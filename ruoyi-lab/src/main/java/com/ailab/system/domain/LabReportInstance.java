package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Report archive. templateCode/templateRevision pin the exact source revision forever. */
public class LabReportInstance extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private String reportNo; private Long templateId; private String templateCode; private Integer templateRevision;
    private String period; private String bizLine; private Integer revisionNo; private String lifecycleStatus; private String currentFlag;
    private String finalFlag; private String sensitiveFlag; private String sourceType; private String sourceDataJson; private Integer sourcePerfRevision;
    private Long sourceCloseRevision; private Long sourceFormalRevision; private Date sourceExecutionCutoff; private String previewOnly;
    private String jsonHash; private String markdownHash; private String wordHash; private String pdfHash;
    private String contentJson; private String contentMarkdown; private String jsonStatus; private String jsonPath; private String jsonError;
    private String markdownStatus; private String markdownPath; private String markdownError; private String wordStatus; private String wordPath;
    private String wordError; private String pdfStatus; private String pdfPath; private String pdfError; private Integer version; private String delFlag;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getReportNo() { return reportNo; } public void setReportNo(String value) { reportNo = value; }
    public Long getTemplateId() { return templateId; } public void setTemplateId(Long value) { templateId = value; }
    public String getTemplateCode() { return templateCode; } public void setTemplateCode(String value) { templateCode = value; }
    public Integer getTemplateRevision() { return templateRevision; } public void setTemplateRevision(Integer value) { templateRevision = value; }
    public String getPeriod() { return period; } public void setPeriod(String value) { period = value; }
    public String getBizLine() { return bizLine; } public void setBizLine(String value) { bizLine = value; }
    public Integer getRevisionNo() { return revisionNo; } public void setRevisionNo(Integer value) { revisionNo = value; }
    public String getLifecycleStatus() { return lifecycleStatus; } public void setLifecycleStatus(String value) { lifecycleStatus = value; }
    public String getCurrentFlag() { return currentFlag; } public void setCurrentFlag(String value) { currentFlag = value; }
    public String getFinalFlag() { return finalFlag; } public void setFinalFlag(String value) { finalFlag = value; }
    public String getSensitiveFlag() { return sensitiveFlag; } public void setSensitiveFlag(String value) { sensitiveFlag = value; }
    public String getSourceType() { return sourceType; } public void setSourceType(String value) { sourceType = value; }
    public String getSourceDataJson() { return sourceDataJson; } public void setSourceDataJson(String value) { sourceDataJson = value; }
    public Integer getSourcePerfRevision() { return sourcePerfRevision; } public void setSourcePerfRevision(Integer value) { sourcePerfRevision = value; }
    public Long getSourceCloseRevision() { return sourceCloseRevision; } public void setSourceCloseRevision(Long value) { sourceCloseRevision = value; }
    public Long getSourceFormalRevision() { return sourceFormalRevision; } public void setSourceFormalRevision(Long value) { sourceFormalRevision = value; }
    public Date getSourceExecutionCutoff() { return copy(sourceExecutionCutoff); } public void setSourceExecutionCutoff(Date value) { sourceExecutionCutoff = copy(value); }
    public String getPreviewOnly() { return previewOnly; } public void setPreviewOnly(String value) { previewOnly = value; }
    public String getJsonHash() { return jsonHash; } public void setJsonHash(String value) { jsonHash = value; }
    public String getMarkdownHash() { return markdownHash; } public void setMarkdownHash(String value) { markdownHash = value; }
    public String getWordHash() { return wordHash; } public void setWordHash(String value) { wordHash = value; }
    public String getPdfHash() { return pdfHash; } public void setPdfHash(String value) { pdfHash = value; }
    public String getContentJson() { return contentJson; } public void setContentJson(String value) { contentJson = value; }
    public String getContentMarkdown() { return contentMarkdown; } public void setContentMarkdown(String value) { contentMarkdown = value; }
    public String getJsonStatus() { return jsonStatus; } public void setJsonStatus(String value) { jsonStatus = value; }
    public String getJsonPath() { return jsonPath; } public void setJsonPath(String value) { jsonPath = value; }
    public String getJsonError() { return jsonError; } public void setJsonError(String value) { jsonError = value; }
    public String getMarkdownStatus() { return markdownStatus; } public void setMarkdownStatus(String value) { markdownStatus = value; }
    public String getMarkdownPath() { return markdownPath; } public void setMarkdownPath(String value) { markdownPath = value; }
    public String getMarkdownError() { return markdownError; } public void setMarkdownError(String value) { markdownError = value; }
    public String getWordStatus() { return wordStatus; } public void setWordStatus(String value) { wordStatus = value; }
    public String getWordPath() { return wordPath; } public void setWordPath(String value) { wordPath = value; }
    public String getWordError() { return wordError; } public void setWordError(String value) { wordError = value; }
    public String getPdfStatus() { return pdfStatus; } public void setPdfStatus(String value) { pdfStatus = value; }
    public String getPdfPath() { return pdfPath; } public void setPdfPath(String value) { pdfPath = value; }
    public String getPdfError() { return pdfError; } public void setPdfError(String value) { pdfError = value; }
    public Integer getVersion() { return version; } public void setVersion(Integer value) { version = value; }
    public String getDelFlag() { return delFlag; } public void setDelFlag(String value) { delFlag = value; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
