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
    private Long actionUserId;
    private Long reviewerId;
    private String reviewerComment;
    private boolean exceededConfirmed;

    public String getRequestedResultStatus() { return requestedResultStatus; }
    public void setRequestedResultStatus(String requestedResultStatus) { this.requestedResultStatus = requestedResultStatus; }
    public Date getActualFinishTime() { return actualFinishTime; }
    public void setActualFinishTime(Date actualFinishTime) { this.actualFinishTime = actualFinishTime; }
    public String getResultDesc() { return resultDesc; }
    public void setResultDesc(String resultDesc) { this.resultDesc = resultDesc; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public List<LabTaskEvidence> getEvidenceList() { return evidenceList; }
    public void setEvidenceList(List<LabTaskEvidence> evidenceList) { this.evidenceList = evidenceList == null ? new ArrayList<LabTaskEvidence>() : new ArrayList<LabTaskEvidence>(evidenceList); }
    public Long getActionUserId() { return actionUserId; }
    public void setActionUserId(Long actionUserId) { this.actionUserId = actionUserId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerComment() { return reviewerComment; }
    public void setReviewerComment(String reviewerComment) { this.reviewerComment = reviewerComment; }
    public boolean isExceededConfirmed() { return exceededConfirmed; }
    public void setExceededConfirmed(boolean exceededConfirmed) { this.exceededConfirmed = exceededConfirmed; }
}
