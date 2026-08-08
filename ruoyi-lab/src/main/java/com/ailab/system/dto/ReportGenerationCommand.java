package com.ailab.system.dto;

public class ReportGenerationCommand {
    private Long templateId; private String period; private String bizLine;
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long v){templateId=v;}
    public String getPeriod(){return period;} public void setPeriod(String v){period=v;}
    public String getBizLine(){return bizLine;} public void setBizLine(String v){bizLine=v;}
}
