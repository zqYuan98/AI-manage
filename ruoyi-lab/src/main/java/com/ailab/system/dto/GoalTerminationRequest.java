package com.ailab.system.dto;

/** Optimistic command used to terminate and archive an active goal. */
public class GoalTerminationRequest {
    private Integer version;
    private String reason;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
