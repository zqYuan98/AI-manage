package com.ailab.system.dto;
public class RedLineRevokeCommand {
    private String evidenceUrl; private String reason;
    public RedLineRevokeCommand(){} public RedLineRevokeCommand(String url,String reason){this.evidenceUrl=url;this.reason=reason;}
    public String getEvidenceUrl(){return evidenceUrl;} public void setEvidenceUrl(String v){evidenceUrl=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;}
}
