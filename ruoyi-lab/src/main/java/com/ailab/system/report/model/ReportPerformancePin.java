package com.ailab.system.report.model;

/** Exact immutable performance revision selected for one member. */
public final class ReportPerformancePin {
    private Long memberId;
    private Integer revisionNo;
    public ReportPerformancePin() { }
    public ReportPerformancePin(Long memberId, Integer revisionNo) {
        if (memberId == null || memberId.longValue() <= 0 || revisionNo == null || revisionNo.intValue() <= 0) {
            throw new IllegalArgumentException("Performance pin is invalid");
        }
        this.memberId=memberId; this.revisionNo=revisionNo;
    }
    public Long getMemberId(){return memberId;} public void setMemberId(Long value){memberId=value;}
    public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer value){revisionNo=value;}
}
