package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVerticalJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;
import org.springframework.stereotype.Component;
import org.apache.xmlbeans.XmlCursor;
import javax.xml.namespace.QName;

/** Converts only the renderer's neutral, canonical report model into a self-contained DOCX package. */
@Component
public final class WordReportExporter implements ReportExporter {
    private static final String FONT = "Microsoft YaHei";
    private static final int MAX_SECTIONS = 200, MAX_ROWS = 10000, MAX_CELLS = 100000, MAX_TEXT = 1024 * 1024;
    private static final int MAX_GROUPS = 5000, MAX_PARAGRAPHS = 10000;
    private static final int MAX_IMAGE = 512 * 1024, MAX_OUTPUT = 8 * 1024 * 1024;
    private final ThreadLocal<String> templateFont = new ThreadLocal<String>();
    private final ThreadLocal<Double> templateBodySize = new ThreadLocal<Double>();
    @Override public String getId() { return "WORD"; }
    @Override public boolean supports(String value) { return "WORD".equals(value); }

    @Override public byte[] export(ReportData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("report data is required");
        if (data.getSections().size() > MAX_SECTIONS) throw new ReportExportException("DOCX section limit exceeded", false);
        preflight(data);
        String title=title(data);templateFont.set(font(data));templateBodySize.set(bodySize(data));
        try (XWPFDocument document = new XWPFDocument()) {
            document.getProperties().getCoreProperties().setTitle(title);
            document.getProperties().getCoreProperties().setCreator("AI Lab");
            document.getProperties().getCoreProperties().setCreated(Optional.of(Date.from(data.getContext().getGeneratedAt())));
            document.getProperties().getCoreProperties().setModified(Optional.of(Date.from(data.getContext().getGeneratedAt())));
            styles(document);
            Limits limits = new Limits();
            paragraph(document, title, "ReportTitle", ParagraphAlignment.CENTER, true, limits);
            paragraph(document, data.getContext().getPeriod() + " · " + data.getContext().getBizLine(), "ReportBody", ParagraphAlignment.CENTER, false, limits);
            for (ReportSectionData section : data.getSections()) render(document, section, limits);
            ByteArrayOutputStream raw = new ByteArrayOutputStream(); document.write(new BoundedOutputStream(raw, MAX_OUTPUT));
            byte[] stable = normalizeZip(raw.toByteArray());
            if (stable.length > MAX_OUTPUT) throw new ReportExportException("DOCX output byte limit exceeded", false);
            return stable;
        } finally { templateFont.remove();templateBodySize.remove(); }
    }

    private void styles(XWPFDocument document) {
        addStyle(document, "ReportTitle", 30, true);
        addStyle(document, "ReportSection", 24, true);
        addStyle(document, "ReportBody", (int)Math.round(currentBodySize()*2), false);
        addStyle(document, "ReportTable", 17, false);
    }
    private void addStyle(XWPFDocument document, String id, int halfPoints, boolean bold) {
        CTStyle style = CTStyle.Factory.newInstance(); style.setStyleId(id); style.setType(STStyleType.PARAGRAPH);
        CTFonts fonts = style.addNewRPr().addNewRFonts(); fonts.setAscii(currentFont()); fonts.setHAnsi(currentFont()); fonts.setEastAsia(currentFont());
        CTHpsMeasure size = style.getRPr().addNewSz(); size.setVal(BigInteger.valueOf(halfPoints));
        style.getRPr().addNewSzCs().setVal(BigInteger.valueOf(halfPoints)); if (bold) style.getRPr().addNewB();
        document.createStyles().addStyle(new XWPFStyle(style));
    }
    private XWPFParagraph paragraph(XWPFDocument document, String value, String style,
            ParagraphAlignment alignment, boolean keepNext, Limits limits) throws IOException {
        paragraphs(limits, 1);
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setStyle(style); paragraph.setAlignment(alignment);
        if (keepNext) paragraph.getCTP().addNewPPr().addNewKeepNext();
        run(paragraph, safe(value, limits), style.equals("ReportTable") ? 8.5 : style.equals("ReportBody") ? 10.5 : style.equals("ReportSection") ? 12 : 15, false);
        return paragraph;
    }
    private void render(XWPFDocument document, ReportSectionData section, Limits limits) throws IOException {
        paragraph(document, section.getTitle(), "ReportSection", ParagraphAlignment.LEFT, true, limits);
        String type = section.getSectionType();
        if ("TABLE".equals(type)) table(document, section, limits);
        else if ("STAT".equals(type)) stat(document, section, limits);
        else if ("TEXT".equals(type) || "MANUAL".equals(type)) paragraph(document, text(section.getSummary().get("text")), "ReportBody", ParagraphAlignment.LEFT, false, limits);
        else if ("GROUP_TEXT".equals(type)) groups(document, section.getSummary().get("groups"), limits);
        else if ("CHART".equals(type)) chart(document, section, limits);
        else paragraph(document, "暂无数据", "ReportBody", ParagraphAlignment.LEFT, false, limits);
    }
    private void stat(XWPFDocument document, ReportSectionData section, Limits limits) throws IOException {
        if (section.getSummary().get("text") != null) paragraph(document, text(section.getSummary().get("text")), "ReportBody", ParagraphAlignment.LEFT, false, limits);
        else if (section.getRows().isEmpty()) paragraph(document, "暂无数据", "ReportBody", ParagraphAlignment.LEFT, false, limits); else table(document, section, limits);
    }
    private void table(XWPFDocument document, ReportSectionData section, Limits limits) throws IOException {
        List<String> headers = strings(section.getSummary().get("headers"));
        if (headers.isEmpty() || section.getRows().isEmpty()) { paragraph(document, "暂无数据", "ReportBody", ParagraphAlignment.LEFT, false, limits); return; }
        tableGrid(limits, headers.size(), section.getRows().size());
        List<String> fields = strings(section.getSummary().get("fields"));
        List<String> aligns = strings(section.getSummary().get("alignments")); XWPFTable table = document.createTable();
        XWPFTableRow header = table.getRow(0); header.getCtRow().addNewTrPr().addNewTblHeader();
        for (int i = 0; i < headers.size(); i++) { XWPFTableCell cell = i == 0 ? header.getCell(0) : header.addNewTableCell(); formatCell(cell, "D9EAF7"); writeCell(cell, headers.get(i), alignment(aligns, i), true, limits); }
        for (Map<String, Object> row : section.getRows()) {
            List<Object> values = valuesFor(row, fields, headers.size());
            XWPFTableRow target = table.createRow();
            for (int i = 0; i < headers.size(); i++) { Object value = values.get(i); formatCell(target.getCell(i), null); writeCell(target.getCell(i), text(value), alignment(aligns, i), false, limits); }
        }
    }
    private List<Object> valuesFor(Map<String,Object> row, List<String> fields, int columns) {
        List<Object> positional = new ArrayList<Object>(row.values());
        List<Object> resolved = new ArrayList<Object>(columns);
        for (int i = 0; i < columns; i++) {
            String field = i < fields.size() ? fields.get(i) : null;
            resolved.add(field != null && row.containsKey(field) ? row.get(field)
                    : i < positional.size() ? positional.get(i) : "");
        }
        return resolved;
    }
    private void formatCell(XWPFTableCell cell, String color) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER); if (cell.getCTTc().getTcPr() == null) cell.getCTTc().addNewTcPr();
        margins(cell);
        if (color != null) { CTShd shade = cell.getCTTc().getTcPr().addNewShd(); shade.setFill(color); shade.setVal(STShd.CLEAR); }
    }
    private void writeCell(XWPFTableCell cell, String value, ParagraphAlignment alignment, boolean bold, Limits limits) throws IOException {
        XWPFParagraph paragraph = cell.getParagraphs().get(0); paragraph.setStyle("ReportTable"); paragraph.setAlignment(alignment); run(paragraph, safe(value, limits), 8.5, bold); if (bold) paragraph.getCTP().addNewPPr().addNewKeepNext();
    }
    private void groups(XWPFDocument document, Object raw, Limits limits) throws IOException {
        if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) { paragraph(document, "暂无数据", "ReportBody", ParagraphAlignment.LEFT, false, limits); return; }
        groupCount(limits, ((List<?>) raw).size());
        for (Object entry : (List<?>) raw) if (entry instanceof Map) { Map<?, ?> group = (Map<?, ?>) entry; paragraph(document, text(group.get("title")), "ReportSection", ParagraphAlignment.LEFT, true, limits); paragraph(document, text(group.get("summary")), "ReportBody", ParagraphAlignment.LEFT, false, limits); }
    }
    private void chart(XWPFDocument document, ReportSectionData section, Limits limits) throws IOException {
        byte[] png = png(section.getSummary().get("pngBase64")); if (png == null) { chartTable(document, section, limits); return; }
        paragraphs(limits, 1); XWPFParagraph p = document.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER); XWPFRun run = p.createRun(); fonts(run, 10.5, false);
        try (InputStream image = new ByteArrayInputStream(png)) { run.addPicture(image, Document.PICTURE_TYPE_PNG, "chart.png", Units.toEMU(640), Units.toEMU(320)); }
        catch (Exception ex) { throw new ReportExportException("Cannot embed trusted chart PNG", false, ex); }
    }
    private void chartTable(XWPFDocument document, ReportSectionData section, Limits limits) throws IOException {
        List<String> categories = strings(section.getSummary().get("categories")); List<?> values = section.getSummary().get("values") instanceof List ? (List<?>) section.getSummary().get("values") : Collections.emptyList();
        if (categories.isEmpty()) { paragraph(document, "暂无数据", "ReportBody", ParagraphAlignment.LEFT, false, limits); return; }
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>(); for (int i = 0; i < categories.size(); i++) { java.util.LinkedHashMap<String,Object> row = new java.util.LinkedHashMap<String,Object>(); row.put("分类", categories.get(i)); row.put("数值", i < values.size() ? values.get(i) : "-"); rows.add(row); }
        java.util.LinkedHashMap<String,Object> summary = new java.util.LinkedHashMap<String,Object>(); summary.put("headers", java.util.Arrays.asList("分类", "数值")); summary.put("alignments", java.util.Arrays.asList("left", "right")); table(document, new ReportSectionData(section.getSectionCode(), "TABLE", section.getTitle(), rows, summary), limits);
    }
    private void run(XWPFParagraph paragraph, String value, double points, boolean bold) { XWPFRun run = paragraph.createRun(); fonts(run, points, bold); run.setText(value == null || value.length() == 0 ? "暂无数据" : value); }
    private void fonts(XWPFRun run, double points, boolean bold) { if(points==10.5)points=currentBodySize();org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr props = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr(); CTFonts fonts = props.isSetRFonts() ? props.getRFonts() : props.addNewRFonts(); fonts.setAscii(currentFont()); fonts.setHAnsi(currentFont()); fonts.setEastAsia(currentFont()); CTHpsMeasure size = props.isSetSz() ? props.getSz() : props.addNewSz(); size.setVal(BigInteger.valueOf(Math.round(points * 2))); CTHpsMeasure complex = props.isSetSzCs() ? props.getSzCs() : props.addNewSzCs(); complex.setVal(BigInteger.valueOf(Math.round(points * 2))); if (bold && !props.isSetB()) props.addNewB(); }
    private void margins(XWPFTableCell cell) {
        XmlCursor cursor = cell.getCTTc().getTcPr().newCursor(); try {
            cursor.toEndToken(); cursor.beginElement(new QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tcMar", "w"));
            margin(cursor, "top", 80); margin(cursor, "bottom", 80); margin(cursor, "left", 100); margin(cursor, "right", 100);
        } finally { cursor.dispose(); }
    }
    private void margin(XmlCursor cursor, String edge, int value) { cursor.beginElement(new QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main", edge, "w")); cursor.insertAttributeWithValue(new QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w", "w"), String.valueOf(value)); cursor.insertAttributeWithValue(new QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "type", "w"), "dxa"); cursor.toEndToken(); }
    private ParagraphAlignment alignment(List<String> aligns, int index) { String value = index < aligns.size() ? aligns.get(index) : "left"; return "right".equalsIgnoreCase(value) ? ParagraphAlignment.RIGHT : "center".equalsIgnoreCase(value) ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT; }
    private List<String> strings(Object raw) { if (!(raw instanceof List)) return Collections.emptyList(); List<String> output = new ArrayList<String>(); for (Object value : (List<?>) raw) output.add(text(value)); return output; }
    private String text(Object value) { return value == null ? "暂无数据" : String.valueOf(value); }
    private String safe(String value, Limits limits) throws IOException { String cleaned = value == null ? "暂无数据" : value.replace('\u0000', ' '); limits.text += cleaned.length(); if (limits.text > MAX_TEXT) throw new ReportExportException("DOCX text limit exceeded", false); return cleaned; }
    private static final class Limits { int rows, cells, text, groups, paragraphs; }

    private void preflight(ReportData data) throws IOException {
        Limits structure = new Limits();
        for (ReportSectionData section : data.getSections()) structure(structure, section);
        Limits limits = new Limits(); paragraphs(limits, 2); addText(limits, title(data)); addText(limits, data.getContext().getPeriod()); addText(limits, data.getContext().getBizLine()); for (ReportSectionData section : data.getSections()) { paragraphs(limits, 1); addText(limits, section.getTitle()); grid(limits, section); for (Map<String,Object> row : section.getRows()) { for (Map.Entry<String,Object> entry : row.entrySet()) { addText(limits, entry.getKey()); addText(limits, text(entry.getValue())); } } scan(limits, section.getSummary()); }
    }

    private String title(ReportData data){Object raw=data.getMetadata().get("header");if(raw instanceof Map){Object value=((Map<?,?>)raw).get("title");if(value instanceof String&&!((String)value).trim().isEmpty())return (String)value;}String period=data.getContext().getPeriod();return "人工智能实验室"+(period!=null&&period.contains("-W")?"周报":period!=null&&period.matches("[0-9]{4}Q[1-4]")?"季报":period!=null&&period.matches("[0-9]{4}")?"年报":"月报");}
    private String font(ReportData data){Object raw=data.getMetadata().get("style");if(raw instanceof Map){Object value=((Map<?,?>)raw).get("font");if(value instanceof String&&!((String)value).trim().isEmpty())return (String)value;}return FONT;}
    private double bodySize(ReportData data){Object raw=data.getMetadata().get("style");if(raw instanceof Map){Object value=((Map<?,?>)raw).get("bodyFontSize");if(value instanceof Number){double size=((Number)value).doubleValue();if(size>=6&&size<=72)return size;}}return 10.5;}
    private String currentFont(){String value=templateFont.get();return value==null?FONT:value;}private double currentBodySize(){Double value=templateBodySize.get();return value==null?10.5:value.doubleValue();}
    private void structure(Limits limits, ReportSectionData section) throws IOException {
        String type = section.getSectionType();
        if ("TABLE".equals(type) || ("STAT".equals(type) && section.getSummary().get("text") == null)) {
            int columns = strings(section.getSummary().get("headers")).size();
            if (columns > 0 && !section.getRows().isEmpty()) tableStructure(limits, columns, section.getRows().size());
        } else if ("CHART".equals(type) && png(section.getSummary().get("pngBase64")) == null) {
            int categories = strings(section.getSummary().get("categories")).size();
            if (categories > 0) tableStructure(limits, 2, categories);
        }
    }
    private void tableStructure(Limits limits, int columns, int dataRows) throws IOException {
        int renderedRows = plusOne(dataRows); rows(limits, renderedRows); cells(limits, columns, renderedRows);
    }
    /** Mirrors the only render paths that allocate a Word table. */
    private void grid(Limits limits, ReportSectionData section) throws IOException { String type = section.getSectionType(); if ("TABLE".equals(type)) { tableOrFallback(limits, section); return; } if ("STAT".equals(type)) { if (section.getSummary().get("text") != null || section.getRows().isEmpty()) paragraphs(limits, 1); else tableOrFallback(limits, section); return; } if ("TEXT".equals(type) || "MANUAL".equals(type)) { paragraphs(limits, 1); return; } if ("GROUP_TEXT".equals(type)) { groupGrid(limits, section.getSummary().get("groups")); return; } if ("CHART".equals(type)) { if (png(section.getSummary().get("pngBase64")) != null) { paragraphs(limits, 1); return; } int categories = strings(section.getSummary().get("categories")).size(); if (categories > 0) { int renderedRows = plusOne(categories); rows(limits, renderedRows); cells(limits, 2, renderedRows); paragraphs(limits, 2 * renderedRows); } else paragraphs(limits, 1); return; } paragraphs(limits, 1); }
    private void tableOrFallback(Limits limits, ReportSectionData section) throws IOException { int columns = strings(section.getSummary().get("headers")).size(); if (columns <= 0 || section.getRows().isEmpty()) paragraphs(limits, 1); else tableGrid(limits, columns, section.getRows().size()); }
    private void groupGrid(Limits limits, Object raw) throws IOException { if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) { paragraphs(limits, 1); return; } List<?> values = (List<?>) raw; groupCount(limits, values.size()); for (Object value : values) if (value instanceof Map) paragraphs(limits, 2); }
    private void rows(Limits limits, int count) throws IOException { if ((long) limits.rows + (long) count > MAX_ROWS) throw new ReportExportException("DOCX row limit exceeded", false); limits.rows += count; }
    private void tableGrid(Limits limits, int columns, int dataRows) throws IOException { if (columns > 0 && dataRows > 0) { int renderedRows = plusOne(dataRows); rows(limits, renderedRows); cells(limits, columns, renderedRows); paragraphs(limits, columns * renderedRows); } }
    private int plusOne(int value) throws IOException { if (value == Integer.MAX_VALUE) throw new ReportExportException("DOCX row limit exceeded", false); return value + 1; }
    private void cells(Limits limits, int columns, int rows) throws IOException { long required = (long) columns * (long) rows; if (required > MAX_CELLS - (long) limits.cells) throw new ReportExportException("DOCX cell limit exceeded", false); limits.cells += (int) required; }
    private void groupCount(Limits limits, int count) throws IOException { if ((long) limits.groups + (long) count > MAX_GROUPS) throw new ReportExportException("DOCX group limit exceeded", false); limits.groups += count; }
    private void paragraphs(Limits limits, int count) throws IOException { if ((long) limits.paragraphs + (long) count > MAX_PARAGRAPHS) throw new ReportExportException("DOCX paragraph limit exceeded", false); limits.paragraphs += count; }
    private void scan(Limits limits, Object value) throws IOException { if (value == null) return; if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) { addText(limits, String.valueOf(value)); return; } if (value instanceof Map) { for (Map.Entry<?,?> entry : ((Map<?,?>) value).entrySet()) { addText(limits, String.valueOf(entry.getKey())); scan(limits, entry.getValue()); } return; } if (value instanceof Iterable) for (Object item : (Iterable<?>) value) scan(limits, item); }
    private void addText(Limits limits, String value) throws IOException { int bytes = (value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length); limits.text += bytes; if (limits.text > MAX_TEXT) throw new ReportExportException("DOCX text limit exceeded", false); }
    private static final class BoundedOutputStream extends java.io.OutputStream { private final java.io.OutputStream target; private final int maximum; private int size; BoundedOutputStream(java.io.OutputStream target,int maximum){this.target=target;this.maximum=maximum;} @Override public void write(int value)throws IOException{check(1);target.write(value);} @Override public void write(byte[] value,int offset,int length)throws IOException{check(length);target.write(value,offset,length);} private void check(int count)throws IOException{if(count>maximum-size)throw new ReportExportException("DOCX output byte limit exceeded",false);size+=count;} }

    private byte[] png(Object raw) throws IOException {
        if (!(raw instanceof String) || ((String) raw).length() > 700000) return null; try { byte[] bytes = Base64.getDecoder().decode((String) raw); if (bytes.length > MAX_IMAGE || !pngStructure(bytes)) return null; BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes)); return image != null && image.getWidth() == 640 && image.getHeight() == 320 ? bytes : null; } catch (IllegalArgumentException ex) { return null; }
    }
    private boolean pngStructure(byte[] bytes) { byte[] sig = {(byte)137,80,78,71,13,10,26,10}; if (bytes.length < 33) return false; for (int i=0;i<sig.length;i++) if(bytes[i]!=sig[i]) return false; int offset=8, chunks=0; boolean header=false,end=false; while(offset+12<=bytes.length && ++chunks<=1000) { int length=integer(bytes,offset); if(length<0||length>bytes.length-offset-12)return false; int type=integer(bytes,offset+4), data=offset+8; CRC32 crc=new CRC32();crc.update(bytes,offset+4,length+4);if(crc.getValue()!=unsigned(bytes,data+length))return false; if(!header){if(type!=0x49484452||length!=13||integer(bytes,data)!=640||integer(bytes,data+4)!=320)return false;header=true;} offset+=length+12;if(type==0x49454e44){end=length==0&&offset==bytes.length;break;} } return header&&end; }
    private int integer(byte[] bytes,int offset){return (bytes[offset]&255)<<24|(bytes[offset+1]&255)<<16|(bytes[offset+2]&255)<<8|bytes[offset+3]&255;} private long unsigned(byte[] bytes,int offset){return integer(bytes,offset)&0xffffffffL;}
    private byte[] normalizeZip(byte[] source) throws IOException {
        List<Entry> entries = new ArrayList<Entry>(); try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source))) { for (ZipEntry item; (item = zip.getNextEntry()) != null;) { ByteArrayOutputStream value = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; for (int count; (count=zip.read(buffer)) >= 0;) value.write(buffer,0,count); if (item.getName().contains("..") || item.getName().startsWith("/")) throw new ReportExportException("Unsafe DOCX package entry", false); entries.add(new Entry(item.getName(), value.toByteArray())); } }
        Collections.sort(entries, new Comparator<Entry>() { public int compare(Entry a, Entry b) { return a.name.compareTo(b.name); } }); ByteArrayOutputStream result=new ByteArrayOutputStream(); try(ZipOutputStream zip=new ZipOutputStream(result)){for(Entry item:entries){ZipEntry target=new ZipEntry(item.name);target.setTime(0L);zip.putNextEntry(target);zip.write(item.value);zip.closeEntry();}} return result.toByteArray();
    }
    private static final class Entry { final String name; final byte[] value; Entry(String name, byte[] value){this.name=name;this.value=value;} }
}
