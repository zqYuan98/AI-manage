package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.util.Date;

/** Auditable collaboration fact. Review identity and time are server-owned. */
public class LabCollaborationRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long taskId;
    /** Projection-only task asset used by performance cutoff calculations. */
    private Long relatedAssetId;
    private String period;
    private Long fromMemberId;
    private Long toMemberId;
    private String category;
    private BigDecimal signedScore;
    private String evidenceUrl;
    private Long reviewerId;
    private String reviewStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date reviewTime;
    private String reviewComment;
    private String idempotencyKey;
    private Integer version;
    private String delFlag;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;}
    public Long getRelatedAssetId(){return relatedAssetId;} public void setRelatedAssetId(Long v){relatedAssetId=v;}
    public String getPeriod(){return period;} public void setPeriod(String v){period=v;}
    public Long getFromMemberId(){return fromMemberId;} public void setFromMemberId(Long v){fromMemberId=v;}
    public Long getToMemberId(){return toMemberId;} public void setToMemberId(Long v){toMemberId=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public BigDecimal getSignedScore(){return signedScore;} public void setSignedScore(BigDecimal v){signedScore=v;}
    public String getEvidenceUrl(){return evidenceUrl;} public void setEvidenceUrl(String v){evidenceUrl=v;}
    public Long getReviewerId(){return reviewerId;} public void setReviewerId(Long v){reviewerId=v;}
    public String getReviewStatus(){return reviewStatus;} public void setReviewStatus(String v){reviewStatus=v;}
    public Date getReviewTime(){return copy(reviewTime);} public void setReviewTime(Date v){reviewTime=copy(v);}
    public String getReviewComment(){return reviewComment;} public void setReviewComment(String v){reviewComment=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
