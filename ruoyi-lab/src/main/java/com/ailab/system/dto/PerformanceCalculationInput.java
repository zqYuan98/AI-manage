package com.ailab.system.dto;

import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Complete immutable-at-call input to the pure performance formula. */
public class PerformanceCalculationInput {
    private Long memberId; private String period; private Date cutoffTime; private boolean closeMode;
    private List<LabTask> tasks=new ArrayList<LabTask>();
    private Map<Long,List<LabTaskEvidence>> evidenceByTask=new HashMap<Long,List<LabTaskEvidence>>();
    private Map<Long,List<LabTaskQualityGate>> qualityGatesByTask=new HashMap<Long,List<LabTaskQualityGate>>();
    private List<LabCollaborationRecord> collaborationRecords=new ArrayList<LabCollaborationRecord>();
    private List<LabCollaborationRecord> quarterCollaborationFacts=new ArrayList<LabCollaborationRecord>();
    private List<PerformanceAssetFact> assetFacts=new ArrayList<PerformanceAssetFact>();
    public Long getMemberId(){return memberId;} public void setMemberId(Long v){memberId=v;} public String getPeriod(){return period;} public void setPeriod(String v){period=v;}
    public Date getCutoffTime(){return cutoffTime==null?null:new Date(cutoffTime.getTime());} public void setCutoffTime(Date v){cutoffTime=v==null?null:new Date(v.getTime());}
    public boolean isCloseMode(){return closeMode;} public void setCloseMode(boolean v){closeMode=v;} public List<LabTask> getTasks(){return tasks;} public void setTasks(List<LabTask> v){tasks=v==null?new ArrayList<LabTask>():v;}
    public Map<Long,List<LabTaskEvidence>> getEvidenceByTask(){return evidenceByTask;} public void setEvidenceByTask(Map<Long,List<LabTaskEvidence>> v){evidenceByTask=v==null?new HashMap<Long,List<LabTaskEvidence>>():v;}
    public Map<Long,List<LabTaskQualityGate>> getQualityGatesByTask(){return qualityGatesByTask;} public void setQualityGatesByTask(Map<Long,List<LabTaskQualityGate>> v){qualityGatesByTask=v==null?new HashMap<Long,List<LabTaskQualityGate>>():v;}
    public List<LabCollaborationRecord> getCollaborationRecords(){return collaborationRecords;} public void setCollaborationRecords(List<LabCollaborationRecord> v){collaborationRecords=v==null?new ArrayList<LabCollaborationRecord>():v;}
    public List<LabCollaborationRecord> getQuarterCollaborationFacts(){return quarterCollaborationFacts;} public void setQuarterCollaborationFacts(List<LabCollaborationRecord> v){quarterCollaborationFacts=v==null?new ArrayList<LabCollaborationRecord>():v;}
    public List<PerformanceAssetFact> getAssetFacts(){return assetFacts;} public void setAssetFacts(List<PerformanceAssetFact> v){assetFacts=v==null?new ArrayList<PerformanceAssetFact>():v;}
}
