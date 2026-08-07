package com.ailab.system;

import com.ailab.system.config.LabProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleSmokeTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LabPropertiesConfiguration.class);

    @Test
    void usesPortableDefaultReportSettings()
    {
        contextRunner.run(context -> {
            LabProperties properties = context.getBean(LabProperties.class);

            assertEquals("reports", properties.getOutputDirectory());
            assertEquals("reports/tmp", properties.getTempDirectory());
            assertEquals("soffice", properties.getLibreOfficeExecutable());
            assertEquals(120L, properties.getConversionTimeoutSeconds());
            assertEquals(50L * 1024 * 1024, properties.getMaxUploadSizeBytes());
        });
    }

    @Test
    void bindsOverriddenReportSettings()
    {
        contextRunner.withPropertyValues(
                "lab.report.output-directory=custom-reports",
                "lab.report.temp-directory=custom-reports/tmp",
                "lab.report.libre-office-executable=/opt/libreoffice/program/soffice",
                "lab.report.conversion-timeout-seconds=45",
                "lab.report.max-upload-size-bytes=2097152")
                .run(context -> {
                    LabProperties properties = context.getBean(LabProperties.class);

                    assertEquals("custom-reports", properties.getOutputDirectory());
                    assertEquals("custom-reports/tmp", properties.getTempDirectory());
                    assertEquals("/opt/libreoffice/program/soffice", properties.getLibreOfficeExecutable());
                    assertEquals(45L, properties.getConversionTimeoutSeconds());
                    assertEquals(2097152L, properties.getMaxUploadSizeBytes());
                });
    }

    @Configuration
    @EnableConfigurationProperties(LabProperties.class)
    static class LabPropertiesConfiguration
    {
    }
}
