package com.ailab.system.dto;

import java.math.BigDecimal;

public class PerformanceCalculationResult {
    private BigDecimal deliveryScore; private BigDecimal qualityScore; private BigDecimal collaborationScore; private BigDecimal totalScore;
    private boolean redLine; private String redLineReason; private String resultStatus; private String detailJson;
    public BigDecimal getDeliveryScore(){return deliveryScore;} public void setDeliveryScore(BigDecimal v){deliveryScore=v;} public BigDecimal getQualityScore(){return qualityScore;} public void setQualityScore(BigDecimal v){qualityScore=v;}
    public BigDecimal getCollaborationScore(){return collaborationScore;} public void setCollaborationScore(BigDecimal v){collaborationScore=v;} public BigDecimal getTotalScore(){return totalScore;} public void setTotalScore(BigDecimal v){totalScore=v;}
    public boolean isRedLine(){return redLine;} public void setRedLine(boolean v){redLine=v;} public String getRedLineReason(){return redLineReason;} public void setRedLineReason(String v){redLineReason=v;}
    public String getResultStatus(){return resultStatus;} public void setResultStatus(String v){resultStatus=v;} public String getDetailJson(){return detailJson;} public void setDetailJson(String v){detailJson=v;}
}
