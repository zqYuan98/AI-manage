package com.ailab.system.dto;
import java.math.BigDecimal;
public class CalibrationCommand {
    private BigDecimal score; private String comment; private String resultStatus;
    public CalibrationCommand(){} public CalibrationCommand(BigDecimal score,String comment,String status){this.score=score;this.comment=comment;this.resultStatus=status;}
    public BigDecimal getScore(){return score;} public void setScore(BigDecimal v){score=v;} public String getComment(){return comment;} public void setComment(String v){comment=v;}
    public String getResultStatus(){return resultStatus;} public void setResultStatus(String v){resultStatus=v;}
}
