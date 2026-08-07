package com.ailab.system.dto;

import com.ailab.system.domain.LabTaskEvidence;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Submission or review data; it deliberately cannot set derived completion labels. */
public class TaskSubmitCommand {
    private String requestedResultStatus;
    private Date actualFinishTime;
    private String resultDesc;
    private String failReason;
    private String nextAction;
    private List<LabTaskEvidence> evidenceList = new ArrayList<LabTaskEvidence>();
    private String reviewerComment;
    private String evidenceAuditComment;
    private List<Long> approvedEvidenceIds = new ArrayList<Long>();
    private boolean exceededConfirmed;

    public String getRequestedResultStatus() { return requestedResultStatus; }
    public void setRequestedResultStatus(String requestedResultStatus) { this.requestedResultStatus = requestedResultStatus; }
    public Date getActualFinishTime() { return copyDate(actualFinishTime); }
    public void setActualFinishTime(Date actualFinishTime) { this.actualFinishTime = copyDate(actualFinishTime); }
    public String getResultDesc() { return resultDesc; }
    public void setResultDesc(String resultDesc) { this.resultDesc = resultDesc; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public List<LabTaskEvidence> getEvidenceList() { return copyEvidenceList(evidenceList); }
    public void setEvidenceList(List<LabTaskEvidence> evidenceList) { this.evidenceList = copyEvidenceList(evidenceList); }
    public String getReviewerComment() { return reviewerComment; }
    public void setReviewerComment(String reviewerComment) { this.reviewerComment = reviewerComment; }
    public String getEvidenceAuditComment() { return evidenceAuditComment; }
    public void setEvidenceAuditComment(String evidenceAuditComment) { this.evidenceAuditComment = evidenceAuditComment; }
    public List<Long> getApprovedEvidenceIds() { return new ArrayList<Long>(approvedEvidenceIds); }
    public void setApprovedEvidenceIds(List<Long> approvedEvidenceIds) { this.approvedEvidenceIds = approvedEvidenceIds == null ? new ArrayList<Long>() : new ArrayList<Long>(approvedEvidenceIds); }
    public boolean isExceededConfirmed() { return exceededConfirmed; }
    public void setExceededConfirmed(boolean exceededConfirmed) { this.exceededConfirmed = exceededConfirmed; }

    private Date copyDate(Date value) {
        return value == null ? null : new Date(value.getTime());
    }

    private List<LabTaskEvidence> copyEvidenceList(List<LabTaskEvidence> source) {
        List<LabTaskEvidence> copy = new ArrayList<LabTaskEvidence>();
        if (source != null) {
            for (LabTaskEvidence evidence : source) {
                copy.add(evidence == null ? null : new LabTaskEvidence(evidence));
            }
        }
        return copy;
    }
}
