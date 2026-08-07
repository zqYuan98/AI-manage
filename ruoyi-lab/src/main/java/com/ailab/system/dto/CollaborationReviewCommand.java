package com.ailab.system.dto;

import java.math.BigDecimal;
public class CollaborationReviewCommand {
    private BigDecimal approvedScore; private String comment;
    public CollaborationReviewCommand(){} public CollaborationReviewCommand(BigDecimal score,String comment){this.approvedScore=score;this.comment=comment;}
    public BigDecimal getApprovedScore(){return approvedScore;} public void setApprovedScore(BigDecimal v){approvedScore=v;} public String getComment(){return comment;} public void setComment(String v){comment=v;}
}
