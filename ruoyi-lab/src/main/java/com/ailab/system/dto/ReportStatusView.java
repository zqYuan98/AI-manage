package com.ailab.system.dto;

import com.ailab.system.domain.LabReportInstance;
import java.util.Date;

/** Public lifecycle projection. Canonical bodies, storage paths, source pins and internal errors are excluded. */
public final class ReportStatusView {
    private final Long id;private final String reportNo,templateCode,period,bizLine,lifecycleStatus,currentFlag,finalFlag,sensitiveFlag,sourceType;
    private final Integer templateRevision,revisionNo,version;private final String jsonStatus,markdownStatus,wordStatus,pdfStatus;
    private final Date createTime,updateTime;
    private ReportStatusView(LabReportInstance value){id=value.getId();reportNo=value.getReportNo();templateCode=value.getTemplateCode();templateRevision=value.getTemplateRevision();period=value.getPeriod();bizLine=value.getBizLine();revisionNo=value.getRevisionNo();lifecycleStatus=value.getLifecycleStatus();currentFlag=value.getCurrentFlag();finalFlag=value.getFinalFlag();sensitiveFlag=value.getSensitiveFlag();sourceType=value.getSourceType();jsonStatus=value.getJsonStatus();markdownStatus=value.getMarkdownStatus();wordStatus=value.getWordStatus();pdfStatus=value.getPdfStatus();version=value.getVersion();createTime=copy(value.getCreateTime());updateTime=copy(value.getUpdateTime());}
    public static ReportStatusView from(LabReportInstance value){if(value==null)throw new IllegalArgumentException("report is required");return new ReportStatusView(value);}
    public Long getId(){return id;}public String getReportNo(){return reportNo;}public String getTemplateCode(){return templateCode;}public Integer getTemplateRevision(){return templateRevision;}public String getPeriod(){return period;}public String getBizLine(){return bizLine;}public Integer getRevisionNo(){return revisionNo;}public String getLifecycleStatus(){return lifecycleStatus;}public String getCurrentFlag(){return currentFlag;}public String getFinalFlag(){return finalFlag;}public String getSensitiveFlag(){return sensitiveFlag;}public String getSourceType(){return sourceType;}public String getJsonStatus(){return jsonStatus;}public String getMarkdownStatus(){return markdownStatus;}public String getWordStatus(){return wordStatus;}public String getPdfStatus(){return pdfStatus;}public Integer getVersion(){return version;}public Date getCreateTime(){return copy(createTime);}public Date getUpdateTime(){return copy(updateTime);}private static Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
