package com.ailab.system.report.exporter;

import com.ailab.system.config.LabProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Production wiring keeps OS conversion settings in LabProperties rather than exporter code. */
@Configuration
public class LabReportExportConfiguration {
    @Bean
    public LibreOfficeProcessRunner libreOfficeProcessRunner(LabProperties properties) {
        return new LibreOfficeProcessRunner(properties);
    }

    @Bean
    public PdfReportExporter pdfReportExporter(WordReportExporter word, LibreOfficeProcessRunner runner) {
        return new PdfReportExporter(word, runner);
    }
}
