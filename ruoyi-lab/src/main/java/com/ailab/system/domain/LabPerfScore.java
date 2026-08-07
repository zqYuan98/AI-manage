package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.util.Date;

/** Immutable monthly calculation revision plus separately audited feedback fields. */
public class LabPerfScore extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private Long memberId; private String period; private Integer revisionNo; private String currentFlag;
    private BigDecimal deliveryScore; private BigDecimal qualityScore; private BigDecimal collaborationScore; private BigDecimal score;
    private String detailJson; private String calculationVersion; @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date cutoffTime;
    private String resultStatus; private String redLineFlag; private String redLineReason; private String revokedFlag;
    private String revokeReason; private String redLineCorrectionJson; private String confirmationStatus; private Long confirmedBy;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date confirmedTime; private String calibrationStatus; private BigDecimal calibrateScore;
    private Long calibratorId; private String calibrationNote; @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date calibrationTime;
    private Integer version; private String delFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getMemberId(){return memberId;} public void setMemberId(Long v){memberId=v;}
    public String getPeriod(){return period;} public void setPeriod(String v){period=v;} public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer v){revisionNo=v;}
    public String getCurrentFlag(){return currentFlag;} public void setCurrentFlag(String v){currentFlag=v;} public BigDecimal getDeliveryScore(){return deliveryScore;} public void setDeliveryScore(BigDecimal v){deliveryScore=v;}
    public BigDecimal getQualityScore(){return qualityScore;} public void setQualityScore(BigDecimal v){qualityScore=v;} public BigDecimal getCollaborationScore(){return collaborationScore;} public void setCollaborationScore(BigDecimal v){collaborationScore=v;}
    public BigDecimal getScore(){return score;} public void setScore(BigDecimal v){score=v;} public String getDetailJson(){return detailJson;} public void setDetailJson(String v){detailJson=v;}
    public String getCalculationVersion(){return calculationVersion;} public void setCalculationVersion(String v){calculationVersion=v;} public Date getCutoffTime(){return copy(cutoffTime);} public void setCutoffTime(Date v){cutoffTime=copy(v);}
    public String getResultStatus(){return resultStatus;} public void setResultStatus(String v){resultStatus=v;} public String getRedLineFlag(){return redLineFlag;} public void setRedLineFlag(String v){redLineFlag=v;}
    public String getRedLineReason(){return redLineReason;} public void setRedLineReason(String v){redLineReason=v;} public String getRevokedFlag(){return revokedFlag;} public void setRevokedFlag(String v){revokedFlag=v;}
    public String getRevokeReason(){return revokeReason;} public void setRevokeReason(String v){revokeReason=v;} public String getRedLineCorrectionJson(){return redLineCorrectionJson;} public void setRedLineCorrectionJson(String v){redLineCorrectionJson=v;}
    public String getConfirmationStatus(){return confirmationStatus;} public void setConfirmationStatus(String v){confirmationStatus=v;} public Long getConfirmedBy(){return confirmedBy;} public void setConfirmedBy(Long v){confirmedBy=v;}
    public Date getConfirmedTime(){return copy(confirmedTime);} public void setConfirmedTime(Date v){confirmedTime=copy(v);} public String getCalibrationStatus(){return calibrationStatus;} public void setCalibrationStatus(String v){calibrationStatus=v;}
    public BigDecimal getCalibrateScore(){return calibrateScore;} public void setCalibrateScore(BigDecimal v){calibrateScore=v;} public Long getCalibratorId(){return calibratorId;} public void setCalibratorId(Long v){calibratorId=v;}
    public String getCalibrationNote(){return calibrationNote;} public void setCalibrationNote(String v){calibrationNote=v;} public Date getCalibrationTime(){return copy(calibrationTime);} public void setCalibrationTime(Date v){calibrationTime=copy(v);}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
