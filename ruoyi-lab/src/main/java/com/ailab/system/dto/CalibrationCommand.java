package com.ailab.system.dto;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalibrationCommand {
    private BigDecimal score; private String comment;
    public CalibrationCommand(){} public CalibrationCommand(BigDecimal score,String comment){this.score=score;this.comment=comment;}
    public CalibrationCommand(BigDecimal score,String comment,String ignoredResultStatus){this(score,comment);}
    public BigDecimal getScore(){return score;} public void setScore(BigDecimal v){score=v;} public String getComment(){return comment;} public void setComment(String v){comment=v;}
}
