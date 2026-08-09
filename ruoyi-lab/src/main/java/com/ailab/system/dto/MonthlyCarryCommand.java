package com.ailab.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** 将已确认未完成的月度结果转入下月。 */
public class MonthlyCarryCommand {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date planDate;
    private String reason;
    public Date getPlanDate() { return planDate == null ? null : new Date(planDate.getTime()); }
    public void setPlanDate(Date planDate) { this.planDate = planDate == null ? null : new Date(planDate.getTime()); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
