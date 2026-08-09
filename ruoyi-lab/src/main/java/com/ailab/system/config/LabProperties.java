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

    private long maxUploadSizeBytes = 50L * 1024 * 1024;

    /** Read weekly execution facts instead of the legacy workflow projection. */
    private boolean readNewModel;

    /** Allow members to write the self-close weekly execution workflow. */
    private boolean writeSelfClose;

    /** Share finalized, non-sensitive ALL reports with every active lab role. */
    private boolean shareAllFinalizedNonSensitive = true;

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

    public long getMaxUploadSizeBytes()
    {
        return maxUploadSizeBytes;
    }

    public void setMaxUploadSizeBytes(long maxUploadSizeBytes)
    {
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public boolean isReadNewModel()
    {
        return readNewModel;
    }

    public void setReadNewModel(boolean readNewModel)
    {
        this.readNewModel = readNewModel;
    }

    public boolean isWriteSelfClose()
    {
        return writeSelfClose;
    }

    public void setWriteSelfClose(boolean writeSelfClose)
    {
        this.writeSelfClose = writeSelfClose;
    }

    public boolean isShareAllFinalizedNonSensitive()
    {
        return shareAllFinalizedNonSensitive;
    }

    public void setShareAllFinalizedNonSensitive(boolean value)
    {
        this.shareAllFinalizedNonSensitive = value;
    }
}
