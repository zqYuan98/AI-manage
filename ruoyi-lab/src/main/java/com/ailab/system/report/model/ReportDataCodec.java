package com.ailab.system.report.model;

import com.ailab.system.report.exporter.JsonReportExporter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded codec for the canonical persisted ReportData JSON. */
@org.springframework.stereotype.Component
public final class ReportDataCodec {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 64, MAX_JSON_TOKENS = 100000, MAX_STRING_CHARS = 262144, MAX_TOTAL_STRING_CHARS = 1048576;
    private static final ObjectMapper JSON = new ObjectMapper();

    public String encode(ReportData data) {
        try { return new String(new JsonReportExporter().export(data), StandardCharsets.UTF_8); }
        catch (IOException ex) { throw new IllegalArgumentException("Cannot encode report data", ex); }
    }

    public ReportData decode(String source) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Report data JSON is missing or too large");
        }
        try {
            validateJsonStream(source, "report data");
            JsonNode root = JSON.readTree(source); requiredObject(root, "report data");
            JsonNode contextNode = root.get("context"); requiredObject(contextNode, "report context");
            String period = text(contextNode, "period"); String bizLine = text(contextNode, "bizLine");
            Long requesterId = contextNode.hasNonNull("requesterId") ? contextNode.get("requesterId").longValue() : null;
            if (requesterId == null) throw new IllegalArgumentException("Report requester is required");
            Instant generatedAt = Instant.parse(text(contextNode, "generatedAt"));
            Map<String,Object> attributes = object(contextNode.get("attributes"));
            ReportContext context = new ReportContext(period, bizLine, requesterId, generatedAt,
                    ReportAccessScope.member(bizLine, requesterId), attributes);
            JsonNode rawSections = root.get("sections");
            if (rawSections == null || !rawSections.isArray() || rawSections.size() > 200) throw new IllegalArgumentException("Invalid report sections");
            List<ReportSectionData> sections = new ArrayList<ReportSectionData>();
            for (JsonNode raw : rawSections) {
                List<Map<String,Object>> rows = raw.has("rows")
                        ? JSON.convertValue(raw.get("rows"), new TypeReference<List<Map<String,Object>>>() { })
                        : Collections.<Map<String,Object>>emptyList();
                if (rows.size() > 10000) throw new IllegalArgumentException("Report section row limit exceeded");
                sections.add(new ReportSectionData(text(raw, "sectionCode"), text(raw, "sectionType"),
                        text(raw, "title"), rows, object(raw.get("summary"))));
            }
            return new ReportData(context, text(root, "templateCode"), root.get("templateRevision").intValue(),
                    sections, object(root.get("metadata")));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) throw (IllegalArgumentException) ex;
            throw new IllegalArgumentException("Invalid report data JSON", ex);
        }
    }

    public String encodeSourceSnapshot(List<ReportPerformancePin> pins) {
        return encodeSourceSnapshot(pins, Collections.<String,String>emptyMap());
    }

    public String encodeSourceSnapshot(List<ReportPerformancePin> pins, Map<String,String> manualSummaryTexts) {
        if (pins == null || pins.size() > 5000) throw new IllegalArgumentException("Performance snapshot is too large");
        if(manualSummaryTexts==null||manualSummaryTexts.size()>200)throw new IllegalArgumentException("Manual summary snapshot is too large");
        Map<String,String> summaries=new LinkedHashMap<String,String>();long totalManualBytes=0;
        for(Map.Entry<String,String> item:manualSummaryTexts.entrySet()){
            if(item.getKey()==null||!item.getKey().matches("[A-Za-z0-9_-]{1,64}")||item.getValue()==null)throw new IllegalArgumentException("Invalid manual summary snapshot");byte[] encoded=item.getValue().getBytes(StandardCharsets.UTF_8);totalManualBytes+=encoded.length;
            if(encoded.length>65536||totalManualBytes>ReportDataBudget.manualMarkdownByteLimit())throw new IllegalArgumentException("Manual summary snapshot is too large");
            summaries.put(item.getKey(),item.getValue());
        }
        Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("performancePins",pins);value.put("manualSummaryTexts",summaries);
        try{String encoded=JSON.writeValueAsString(value);if(encoded.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Source snapshot is too large");validateJsonStream(encoded,"source snapshot");return encoded;}catch(IOException ex){throw new IllegalArgumentException("Cannot encode source snapshot",ex);}
    }

    public List<ReportPerformancePin> decodePerformancePins(String source) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Performance snapshot is missing or too large");
        try {
            validateJsonStream(source,"performance snapshot");
            JsonNode root=JSON.readTree(source);requiredObject(root,"performance snapshot");
            if(root.size()==1&&root.has("performanceRevision")&&root.get("performanceRevision").canConvertToInt())return null;
            if((root.size()!=1&&root.size()!=2)||!root.has("performancePins")||!root.get("performancePins").isArray()||root.get("performancePins").size()>5000||(root.size()==2&&!root.has("manualSummaryTexts")))throw new IllegalArgumentException("Invalid performance snapshot");
            List<ReportPerformancePin> result=new ArrayList<ReportPerformancePin>();java.util.Set<Long> members=new java.util.HashSet<Long>();
            for(JsonNode item:root.get("performancePins")){
                if(!item.isObject()||item.size()!=2||!item.has("memberId")||!item.has("revisionNo")||!item.get("memberId").canConvertToLong()||!item.get("revisionNo").canConvertToInt())throw new IllegalArgumentException("Invalid performance pin");
                ReportPerformancePin pin=new ReportPerformancePin(item.get("memberId").longValue(),item.get("revisionNo").intValue());if(!members.add(pin.getMemberId()))throw new IllegalArgumentException("Duplicate performance pin");result.add(pin);
            }
            return Collections.unmodifiableList(result);
        } catch(IOException ex){throw new IllegalArgumentException("Invalid performance snapshot",ex);}
    }

    public Map<String,String> decodeManualSummaryTexts(String source) {
        if(source==null||source.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Source snapshot is missing or too large");
        try{
            validateJsonStream(source,"source snapshot");JsonNode root=JSON.readTree(source);requiredObject(root,"source snapshot");JsonNode values=root.get("manualSummaryTexts");
            if(values==null)return Collections.emptyMap();if(!values.isObject()||values.size()>200)throw new IllegalArgumentException("Invalid manual summary snapshot");
            Map<String,String> result=new LinkedHashMap<String,String>();java.util.Iterator<Map.Entry<String,JsonNode>> fields=values.fields();
            while(fields.hasNext()){Map.Entry<String,JsonNode> item=fields.next();if(!item.getKey().matches("[A-Za-z0-9_-]{1,64}")||!item.getValue().isTextual()||item.getValue().textValue().getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Invalid manual summary snapshot");result.put(item.getKey(),item.getValue().textValue());}
            return Collections.unmodifiableMap(result);
        }catch(IOException ex){throw new IllegalArgumentException("Invalid manual summary snapshot",ex);}
    }

    public Map<String,Object> decodeObject(String source, String name) {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > 32000) throw new IllegalArgumentException(name + " is missing or too large");
        try { validateJsonStream(source,name);JsonNode root=JSON.readTree(source);requiredObject(root,name);return object(root); }
        catch(IOException ex){throw new IllegalArgumentException("Invalid " + name,ex);}
    }

    private static Map<String,Object> object(JsonNode node) {
        if (node == null || node.isNull()) return Collections.emptyMap();
        requiredObject(node, "JSON object");
        return JSON.convertValue(node, new TypeReference<LinkedHashMap<String,Object>>() { });
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().length() > 1000) throw new IllegalArgumentException("Invalid " + field);
        return value.asText();
    }
    private static void requiredObject(JsonNode node, String name) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Invalid " + name);
    }
    private static void validateJsonStream(String source,String name) throws IOException {
        JsonParser parser=JSON.getFactory().createParser(source);int depth=0,tokens=0,totalStrings=0;
        try{JsonToken token;while((token=parser.nextToken())!=null){
            if(++tokens>MAX_JSON_TOKENS)throw new IllegalArgumentException(name+" exceeds JSON token limit");
            if(token==JsonToken.START_OBJECT||token==JsonToken.START_ARRAY){if(++depth>MAX_JSON_DEPTH)throw new IllegalArgumentException(name+" exceeds JSON depth limit");}
            else if(token==JsonToken.END_OBJECT||token==JsonToken.END_ARRAY)depth--;
            if(token==JsonToken.FIELD_NAME||token==JsonToken.VALUE_STRING){int length=parser.getTextLength();if(length>MAX_STRING_CHARS||(totalStrings+=length)>MAX_TOTAL_STRING_CHARS)throw new IllegalArgumentException(name+" exceeds JSON string limit");}
        }}finally{parser.close();}
    }
}
