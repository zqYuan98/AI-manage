package com.ailab.system;

import com.ailab.system.config.LabProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleSmokeTest
{
    @Test
    void usesPortableDefaultReportSettings()
    {
        LabProperties properties = new LabProperties();

        assertEquals("reports", properties.getOutputDirectory());
        assertEquals("reports/tmp", properties.getTempDirectory());
        assertEquals("soffice", properties.getLibreOfficeExecutable());
        assertEquals(120L, properties.getConversionTimeoutSeconds());
        assertEquals(50L * 1024 * 1024, properties.getMaxUploadSize());
    }
}
