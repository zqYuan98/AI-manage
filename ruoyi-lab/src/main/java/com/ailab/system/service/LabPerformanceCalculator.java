package com.ailab.system.service;

import com.alibaba.fastjson2.JSON;
import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.PerformanceAssetFact;
import com.ailab.system.dto.PerformanceCalculationInput;
import com.ailab.system.dto.PerformanceCalculationResult;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Pure deterministic formula; persistence and clocks are deliberately outside this type. */
@Component
public class LabPerformanceCalculator {
    private static final BigDecimal ZERO=money(BigDecimal.ZERO);
    private static final BigDecimal DELIVERY_FACTOR=new BigDecimal("0.6");
    private static final BigDecimal QUALITY_TOTAL=new BigDecimal("25");
    private static final BigDecimal HUNDRED=new BigDecimal("100");

    public PerformanceCalculationResult calculate(PerformanceCalculationInput input) {
        requireInput(input);
        List<LabTask> tasks=scoredTasks(input);
        List<Map<String,Object>> taskDetails=new ArrayList<Map<String,Object>>();
        List<Map<String,Object>> triggers=new ArrayList<Map<String,Object>>();
        BigDecimal deliveryRaw=BigDecimal.ZERO;
        BigDecimal weightedQuality=BigDecimal.ZERO;
        for(LabTask task:tasks){
            String effectiveResult=effectiveResult(task,input.isCloseMode());
            BigDecimal coefficient=coefficient(effectiveResult);
            BigDecimal weight=value(task.getPerfWeight());
            BigDecimal deliveryContribution=weight.multiply(coefficient).multiply(DELIVERY_FACTOR);
            deliveryRaw=deliveryRaw.add(deliveryContribution);

            List<LabTaskEvidence> evidence=sortedEvidence(input.getEvidenceByTask().get(task.getId()));
            Set<Long> approvedIds=approvedEvidenceIds(evidence);
            List<LabTaskQualityGate> gates=sortedGates(input.getQualityGatesByTask().get(task.getId()));
            int passed=0;
            List<Map<String,Object>> gateDetails=new ArrayList<Map<String,Object>>();
            for(LabTaskQualityGate gate:gates){
                boolean approvedEvidence=gate.getEvidenceId()!=null&&approvedIds.contains(gate.getEvidenceId());
                boolean counted="PASSED".equals(gate.getGateStatus())&&approvedEvidence;
                if(counted)passed++;
                Map<String,Object> gd=new LinkedHashMap<String,Object>(); gd.put("gateId",gate.getId()); gd.put("status",gate.getGateStatus());
                gd.put("evidenceId",gate.getEvidenceId()); gd.put("approvedEvidence",approvedEvidence); gd.put("counted",counted); gateDetails.add(gd);
            }
            BigDecimal qualityRate=gates.isEmpty()?BigDecimal.ZERO:new BigDecimal(passed).divide(new BigDecimal(gates.size()),6,RoundingMode.HALF_UP);
            weightedQuality=weightedQuality.add(weight.multiply(qualityRate));

            Map<String,Object> td=new LinkedHashMap<String,Object>(); td.put("taskId",task.getId()); td.put("title",task.getTitle()); td.put("perfWeight",weight);
            td.put("originalWorkflowStatus",task.getWorkflowStatus()); td.put("originalResultStatus",task.getResultStatus()); td.put("effectiveResult",effectiveResult);
            if(!LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())&&input.isCloseMode())td.put("treatment","MONTH_CLOSE_UNCONFIRMED_AS_UNDONE");
            td.put("coefficient",coefficient); td.put("deliveryContribution",money(deliveryContribution)); td.put("approvedEvidenceIds",new ArrayList<Long>(approvedIds));
            td.put("qualityGates",gateDetails); td.put("qualityRate",qualityRate.setScale(6,RoundingMode.HALF_UP));
            if(gates.isEmpty())td.put("qualityExplanation","NO_APPLICABLE_GATE");
            taskDetails.add(td);

            if(input.isCloseMode()&&approvedIds.isEmpty()){
                Map<String,Object> trigger=new LinkedHashMap<String,Object>(); trigger.put("code","MISSING_APPROVED_DELIVERY_EVIDENCE"); trigger.put("taskId",task.getId());
                trigger.put("title",task.getTitle()); trigger.put("workflowStatusAtCutoff",task.getWorkflowStatus()); trigger.put("resultStatusAtCutoff",task.getResultStatus()); triggers.add(trigger);
            }
        }

        BigDecimal delivery=money(deliveryRaw.min(new BigDecimal("60")));
        BigDecimal quality=money(QUALITY_TOTAL.multiply(weightedQuality).divide(HUNDRED,8,RoundingMode.HALF_UP).min(new BigDecimal("25")));
        CollaborationCalculation collaboration=collaboration(input);
        List<PerformanceAssetFact> assets=sortedAssets(input.getAssetFacts());
        if(input.isCloseMode())for(PerformanceAssetFact asset:assets){
            if(same(input.getMemberId(),asset.getPrimaryOwnerId())&&!asset.isActiveBackup()&&!asset.isQuarterBackupTraining()){
                Map<String,Object> trigger=new LinkedHashMap<String,Object>(); trigger.put("code","CRITICAL_ASSET_WITHOUT_BACKUP"); trigger.put("assetId",asset.getAssetId());
                trigger.put("assetName",asset.getAssetName()); trigger.put("activeBackup",false); trigger.put("quarterBackupTraining",false); triggers.add(trigger);
            }
        }
        BigDecimal total=money(delivery.add(quality).add(collaboration.score).max(BigDecimal.ZERO).min(new BigDecimal("100")));

        Map<String,Object> detail=new LinkedHashMap<String,Object>(); detail.put("calculationVersion",LabConstants.PERF_FORMULA_VERSION);
        detail.put("period",input.getPeriod()); detail.put("memberId",input.getMemberId()); detail.put("cutoff",input.getCutoffTime().toInstant().toString()); detail.put("closeMode",input.isCloseMode());
        detail.put("formula","total=min(100,delivery+quality+collaboration); delivery=min(60,sum(weight*coefficient)*0.6); quality=25*sum(weight*rate)/100");
        detail.put("tasks",taskDetails); detail.put("collaboration",collaboration.detail); detail.put("assetFacts",assetDetail(assets)); detail.put("redLineTriggers",triggers);
        Map<String,Object> totals=new LinkedHashMap<String,Object>(); totals.put("delivery",delivery); totals.put("quality",quality); totals.put("collaboration",collaboration.score); totals.put("total",total); detail.put("totals",totals);

        PerformanceCalculationResult result=new PerformanceCalculationResult(); result.setDeliveryScore(delivery); result.setQualityScore(quality);
        result.setCollaborationScore(collaboration.score); result.setTotalScore(total); result.setRedLine(!triggers.isEmpty());
        result.setResultStatus(triggers.isEmpty()?LabConstants.PERF_RESULT_NORMAL:LabConstants.PERF_RESULT_RED_LINE);
        result.setRedLineReason(triggerReason(triggers)); result.setDetailJson(JSON.toJSONString(detail)); return result;
    }

    private CollaborationCalculation collaboration(PerformanceCalculationInput input){
        List<LabCollaborationRecord> records=new ArrayList<LabCollaborationRecord>(input.getCollaborationRecords());
        Collections.sort(records,Comparator.comparing(LabCollaborationRecord::getId,Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String,BigDecimal> positive=new LinkedHashMap<String,BigDecimal>(); positive.put(LabConstants.COLLAB_CROSS_DEPT,BigDecimal.ZERO); positive.put(LabConstants.COLLAB_KNOWLEDGE,BigDecimal.ZERO); positive.put(LabConstants.COLLAB_BACKUP,BigDecimal.ZERO);
        BigDecimal deductions=BigDecimal.ZERO; List<Map<String,Object>> items=new ArrayList<Map<String,Object>>();
        for(LabCollaborationRecord record:records){
            if(!same(input.getMemberId(),record.getToMemberId())||!same(input.getPeriod(),record.getPeriod())||!LabConstants.REVIEW_APPROVED.equals(record.getReviewStatus()))continue;
            boolean negative=LabConstants.COLLAB_OVERDUE.equals(record.getCategory())||LabConstants.COLLAB_DEDUCTION.equals(record.getCategory());
            boolean evidenceBacked=hasText(record.getEvidenceUrl());
            boolean counted=negative||(positive.containsKey(record.getCategory())&&evidenceBacked);
            if(!counted)continue;
            BigDecimal points=value(record.getSignedScore());
            if(negative)deductions=deductions.add(points.abs()); else positive.put(record.getCategory(),positive.get(record.getCategory()).add(points.max(BigDecimal.ZERO)));
            Map<String,Object> item=new LinkedHashMap<String,Object>(); item.put("recordId",record.getId()); item.put("category",record.getCategory()); item.put("signedScore",points);
            item.put("evidenceUrl",record.getEvidenceUrl()); item.put("reviewerId",record.getReviewerId()); item.put("reviewTime",record.getReviewTime()); items.add(item);
        }
        BigDecimal cross=positive.get(LabConstants.COLLAB_CROSS_DEPT).min(new BigDecimal("6")); BigDecimal knowledge=positive.get(LabConstants.COLLAB_KNOWLEDGE).min(new BigDecimal("5")); BigDecimal backup=positive.get(LabConstants.COLLAB_BACKUP).min(new BigDecimal("4"));
        BigDecimal before=cross.add(knowledge).add(backup); BigDecimal score=money(before.subtract(deductions).max(BigDecimal.ZERO).min(new BigDecimal("15")));
        Map<String,Object> detail=new LinkedHashMap<String,Object>(); detail.put("items",items); detail.put("crossDepartmentCapped",money(cross)); detail.put("knowledgeCapped",money(knowledge));
        detail.put("backupCapped",money(backup)); detail.put("positiveBeforeDeduction",money(before)); detail.put("deductions",money(deductions)); detail.put("score",score);
        return new CollaborationCalculation(score,detail);
    }

    private List<LabTask> scoredTasks(PerformanceCalculationInput input){
        List<LabTask> result=new ArrayList<LabTask>();
        for(LabTask task:input.getTasks())if(task!=null&&same(input.getMemberId(),task.getOwnerId())&&same(input.getPeriod(),task.getPeriod())
                &&LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())&&LabConstants.TASK_TYPE_KEY.equals(task.getTaskType())
                &&(input.isCloseMode()||LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())))result.add(task);
        Collections.sort(result,Comparator.comparing(LabTask::getId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;
    }
    private String effectiveResult(LabTask task,boolean closeMode){return LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())?task.getResultStatus():(closeMode?LabConstants.RESULT_UNDONE:null);}
    private BigDecimal coefficient(String result){if(LabConstants.RESULT_EXCEEDED.equals(result))return new BigDecimal("1.2"); if(LabConstants.RESULT_ONTIME.equals(result))return BigDecimal.ONE; if(LabConstants.RESULT_DELAYED.equals(result))return new BigDecimal("0.7"); return BigDecimal.ZERO;}
    private Set<Long> approvedEvidenceIds(List<LabTaskEvidence> evidence){Set<Long> ids=new HashSet<Long>(); for(LabTaskEvidence item:evidence)if(LabConstants.EVIDENCE_AUDIT_APPROVED.equals(item.getAuditStatus())&&hasText(item.getEvidenceUrl())&&item.getId()!=null)ids.add(item.getId()); return ids;}
    private List<LabTaskEvidence> sortedEvidence(List<LabTaskEvidence> source){List<LabTaskEvidence> result=source==null?new ArrayList<LabTaskEvidence>():new ArrayList<LabTaskEvidence>(source); Collections.sort(result,Comparator.comparing(LabTaskEvidence::getId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;}
    private List<LabTaskQualityGate> sortedGates(List<LabTaskQualityGate> source){List<LabTaskQualityGate> result=source==null?new ArrayList<LabTaskQualityGate>():new ArrayList<LabTaskQualityGate>(source); Collections.sort(result,Comparator.comparing(LabTaskQualityGate::getId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;}
    private List<PerformanceAssetFact> sortedAssets(List<PerformanceAssetFact> source){List<PerformanceAssetFact> result=source==null?new ArrayList<PerformanceAssetFact>():new ArrayList<PerformanceAssetFact>(source); Collections.sort(result,Comparator.comparing(PerformanceAssetFact::getAssetId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;}
    private List<Map<String,Object>> assetDetail(List<PerformanceAssetFact> assets){List<Map<String,Object>> result=new ArrayList<Map<String,Object>>(); for(PerformanceAssetFact asset:assets){Map<String,Object> item=new LinkedHashMap<String,Object>(); item.put("assetId",asset.getAssetId()); item.put("assetName",asset.getAssetName()); item.put("primaryOwnerId",asset.getPrimaryOwnerId()); item.put("activeBackup",asset.isActiveBackup()); item.put("quarterBackupTraining",asset.isQuarterBackupTraining()); result.add(item);} return result;}
    private String triggerReason(List<Map<String,Object>> triggers){StringBuilder value=new StringBuilder(); for(Map<String,Object> trigger:triggers){if(value.length()>0)value.append(';'); value.append(trigger.get("code"));} return value.toString();}
    private void requireInput(PerformanceCalculationInput input){if(input==null||input.getMemberId()==null||!hasText(input.getPeriod())||input.getCutoffTime()==null)throw new ServiceException("Complete performance calculation input is required");}
    private static BigDecimal value(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
    private static boolean same(Object a,Object b){return a==null?b==null:a.equals(b);} private static boolean hasText(String v){return v!=null&&!v.trim().isEmpty();}
    private static final class CollaborationCalculation{private final BigDecimal score; private final Map<String,Object> detail; private CollaborationCalculation(BigDecimal score,Map<String,Object> detail){this.score=score;this.detail=detail;}}
}
