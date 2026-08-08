package com.ailab.system.report.exporter;

import com.ailab.system.config.LabProperties;
import com.ailab.system.report.model.ReportData;
import java.io.IOException;

/** PDF is deliberately a targeted conversion of an already successful Word artifact. */
public final class PdfReportExporter implements ReportExporter {
    private final WordReportExporter word; private final LibreOfficeProcessRunner runner;
    public PdfReportExporter(WordReportExporter word, LibreOfficeProcessRunner runner) { this.word = word; this.runner = runner; }
    public PdfReportExporter(LabProperties properties) { this(new WordReportExporter(), new LibreOfficeProcessRunner(properties)); }
    @Override public String getId() { return "PDF"; }
    @Override public boolean supports(String value) { return "PDF".equals(value); }
    @Override public byte[] export(ReportData data) throws IOException { byte[] docx = word.export(data); return exportFromWord(docx, "report-" + data.getContext().getPeriod()); }
    public byte[] exportFromWord(byte[] wordBytes, String safeName) throws ReportExportException { return runner.convert(wordBytes, safeName); }
}
