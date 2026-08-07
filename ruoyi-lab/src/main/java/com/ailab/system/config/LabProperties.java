package com.ailab.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable defaults for laboratory report generation.
 */
@Component
@ConfigurationProperties(prefix = "lab.report")
public class LabProperties
{
    private String outputDirectory = "reports";

    private String tempDirectory = "reports/tmp";

    private String libreOfficeExecutable = "soffice";

    private long conversionTimeoutSeconds = 120L;

    private long maxUploadSize = 50L * 1024 * 1024;

    public String getOutputDirectory()
    {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }

    public String getTempDirectory()
    {
        return tempDirectory;
    }

    public void setTempDirectory(String tempDirectory)
    {
        this.tempDirectory = tempDirectory;
    }

    public String getLibreOfficeExecutable()
    {
        return libreOfficeExecutable;
    }

    public void setLibreOfficeExecutable(String libreOfficeExecutable)
    {
        this.libreOfficeExecutable = libreOfficeExecutable;
    }

    public long getConversionTimeoutSeconds()
    {
        return conversionTimeoutSeconds;
    }

    public void setConversionTimeoutSeconds(long conversionTimeoutSeconds)
    {
        this.conversionTimeoutSeconds = conversionTimeoutSeconds;
    }

    public long getMaxUploadSize()
    {
        return maxUploadSize;
    }

    public void setMaxUploadSize(long maxUploadSize)
    {
        this.maxUploadSize = maxUploadSize;
    }
}
