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
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
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
                Map<String,Object> gd=new LinkedHashMap<String,Object>(); gd.put("gateId",gate.getId()); gd.put("gateNo",gate.getGateNo()); gd.put("gateName",gate.getGateName()); gd.put("status",gate.getGateStatus());
                gd.put("evidenceId",gate.getEvidenceId()); gd.put("checkerId",gate.getCheckerId()); gd.put("checkTime",iso(gate.getCheckTime())); gd.put("checkResult",gate.getCheckResult());
                gd.put("approvedEvidence",approvedEvidence); gd.put("included",counted); gd.put("counted",counted);
                gd.put("exclusionReason",counted?null:gateExclusion(gate,approvedEvidence)); gateDetails.add(gd);
            }
            BigDecimal qualityRate=gates.isEmpty()?BigDecimal.ZERO:new BigDecimal(passed).divide(new BigDecimal(gates.size()),6,RoundingMode.HALF_UP);
            weightedQuality=weightedQuality.add(weight.multiply(qualityRate));

            Map<String,Object> td=new LinkedHashMap<String,Object>(); td.put("taskId",task.getId()); td.put("title",task.getTitle()); td.put("perfWeight",weight);
            td.put("originalWorkflowStatus",task.getWorkflowStatus()); td.put("originalResultStatus",task.getResultStatus()); td.put("effectiveResult",effectiveResult);
            if(!LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())&&input.isCloseMode())td.put("treatment","MONTH_CLOSE_UNCONFIRMED_AS_UNDONE");
            td.put("coefficient",coefficient); td.put("deliveryContribution",money(deliveryContribution)); td.put("evidence",evidenceDetail(evidence)); td.put("approvedEvidenceIds",new ArrayList<Long>(approvedIds));
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
            if(same(input.getMemberId(),asset.getPrimaryOwnerId())&&!asset.isActiveBackup()&&!hasQuarterBackupTraining(asset,input)){
                Map<String,Object> trigger=new LinkedHashMap<String,Object>(); trigger.put("code","CRITICAL_ASSET_WITHOUT_BACKUP"); trigger.put("assetId",asset.getAssetId());
                trigger.put("assetName",asset.getAssetName()); trigger.put("activeBackup",false); trigger.put("quarterBackupTraining",false); triggers.add(trigger);
            }
        }
        BigDecimal total=money(delivery.add(quality).add(collaboration.score).max(BigDecimal.ZERO).min(new BigDecimal("100")));

        Map<String,Object> detail=new LinkedHashMap<String,Object>(); detail.put("calculationVersion",LabConstants.PERF_FORMULA_VERSION);
        detail.put("period",input.getPeriod()); detail.put("memberId",input.getMemberId()); detail.put("cutoff",input.getCutoffTime().toInstant().toString()); detail.put("closeMode",input.isCloseMode());
        detail.put("formula","total=min(100,delivery+quality+collaboration); delivery=min(60,sum(weight*coefficient)*0.6); quality=25*sum(weight*rate)/100");
        detail.put("tasks",taskDetails); detail.put("collaboration",collaboration.detail); detail.put("assetFacts",assetDetail(assets,input)); detail.put("redLineTriggers",triggers);
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
        Map<String,BigDecimal> caps=new LinkedHashMap<String,BigDecimal>(); caps.put(LabConstants.COLLAB_CROSS_DEPT,new BigDecimal("6")); caps.put(LabConstants.COLLAB_KNOWLEDGE,new BigDecimal("5")); caps.put(LabConstants.COLLAB_BACKUP,new BigDecimal("4"));
        BigDecimal deductions=BigDecimal.ZERO; List<Map<String,Object>> items=new ArrayList<Map<String,Object>>();
        for(LabCollaborationRecord record:records){
            if(!same(input.getMemberId(),record.getToMemberId())||!same(input.getPeriod(),record.getPeriod()))continue;
            boolean negative=LabConstants.COLLAB_OVERDUE.equals(record.getCategory())||LabConstants.COLLAB_DEDUCTION.equals(record.getCategory());
            BigDecimal points=value(record.getSignedScore());
            boolean included=false; String exclusionReason=null; String capAdjustment=null; BigDecimal rawEligible=BigDecimal.ZERO; BigDecimal applied=BigDecimal.ZERO;
            if(!LabConstants.REVIEW_APPROVED.equals(record.getReviewStatus()))exclusionReason="REVIEW_NOT_APPROVED";
            else if(negative){rawEligible=points.abs().negate();applied=rawEligible;deductions=deductions.add(points.abs());included=true;}
            else if(!positive.containsKey(record.getCategory()))exclusionReason="UNSUPPORTED_CATEGORY";
            else if(!hasText(record.getEvidenceUrl()))exclusionReason="MISSING_EVIDENCE";
            else{
                rawEligible=points.max(BigDecimal.ZERO);BigDecimal remaining=caps.get(record.getCategory()).subtract(positive.get(record.getCategory())).max(BigDecimal.ZERO);
                if(rawEligible.compareTo(BigDecimal.ZERO)<=0)exclusionReason="NON_POSITIVE_SCORE";
                else if(remaining.compareTo(BigDecimal.ZERO)<=0)exclusionReason="CATEGORY_CAP_REACHED";
                else{applied=rawEligible.min(remaining);positive.put(record.getCategory(),positive.get(record.getCategory()).add(applied));included=true;if(applied.compareTo(rawEligible)<0)capAdjustment="PARTIALLY_CAPPED";}
            }
            Map<String,Object> item=new LinkedHashMap<String,Object>(); item.put("id",record.getId()); item.put("recordId",record.getId()); item.put("taskId",record.getTaskId()); item.put("relatedAssetId",record.getRelatedAssetId()); item.put("period",record.getPeriod()); item.put("fromMemberId",record.getFromMemberId()); item.put("toMemberId",record.getToMemberId());
            item.put("category",record.getCategory()); item.put("signedScore",money(points)); item.put("evidenceUrl",record.getEvidenceUrl()); item.put("reviewStatus",record.getReviewStatus());
            item.put("reviewerId",record.getReviewerId()); item.put("reviewTime",iso(record.getReviewTime())); item.put("reviewComment",record.getReviewComment()); item.put("idempotencyKey",record.getIdempotencyKey());
            item.put("version",record.getVersion()); item.put("delFlag",record.getDelFlag()); item.put("createBy",record.getCreateBy()); item.put("createTime",iso(record.getCreateTime())); item.put("updateBy",record.getUpdateBy()); item.put("updateTime",iso(record.getUpdateTime())); item.put("remark",record.getRemark());
            item.put("included",included); item.put("exclusionReason",exclusionReason);
            item.put("rawEligiblePoints",money(rawEligible)); item.put("appliedPoints",money(applied)); item.put("capAdjustment",capAdjustment); items.add(item);
        }
        BigDecimal cross=positive.get(LabConstants.COLLAB_CROSS_DEPT); BigDecimal knowledge=positive.get(LabConstants.COLLAB_KNOWLEDGE); BigDecimal backup=positive.get(LabConstants.COLLAB_BACKUP);
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
    private Set<Long> approvedEvidenceIds(List<LabTaskEvidence> evidence){Set<Long> ids=new LinkedHashSet<Long>(); for(LabTaskEvidence item:evidence)if(LabConstants.EVIDENCE_AUDIT_APPROVED.equals(item.getAuditStatus())&&hasText(item.getEvidenceUrl())&&item.getId()!=null)ids.add(item.getId()); return ids;}
    private List<Map<String,Object>> evidenceDetail(List<LabTaskEvidence> evidence){List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(LabTaskEvidence item:evidence){boolean approved=LabConstants.EVIDENCE_AUDIT_APPROVED.equals(item.getAuditStatus());boolean included=approved&&hasText(item.getEvidenceUrl());Map<String,Object> detail=new LinkedHashMap<String,Object>();detail.put("id",item.getId());detail.put("evidenceId",item.getId());detail.put("type",item.getEvidenceType());detail.put("title",item.getEvidenceTitle());detail.put("url",item.getEvidenceUrl());detail.put("submitterId",item.getSubmitterId());detail.put("submitTime",iso(item.getSubmitTime()));detail.put("auditStatus",item.getAuditStatus());detail.put("auditorId",item.getAuditorId());detail.put("auditTime",iso(item.getAuditTime()));detail.put("auditComment",item.getAuditComment());detail.put("included",included);detail.put("exclusionReason",included?null:(!approved?"AUDIT_NOT_APPROVED":"MISSING_EVIDENCE_URL"));result.add(detail);}return result;}
    private String gateExclusion(LabTaskQualityGate gate,boolean approvedEvidence){if(!"PASSED".equals(gate.getGateStatus()))return "GATE_NOT_PASSED";if(gate.getEvidenceId()==null)return "NO_BOUND_EVIDENCE";if(!approvedEvidence)return "BOUND_EVIDENCE_NOT_APPROVED";return null;}
    private List<LabTaskEvidence> sortedEvidence(List<LabTaskEvidence> source){List<LabTaskEvidence> result=source==null?new ArrayList<LabTaskEvidence>():new ArrayList<LabTaskEvidence>(source); Collections.sort(result,Comparator.comparing(LabTaskEvidence::getId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;}
    private List<LabTaskQualityGate> sortedGates(List<LabTaskQualityGate> source){List<LabTaskQualityGate> result=source==null?new ArrayList<LabTaskQualityGate>():new ArrayList<LabTaskQualityGate>(source); Collections.sort(result,Comparator.comparing(LabTaskQualityGate::getId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;}
    private List<PerformanceAssetFact> sortedAssets(List<PerformanceAssetFact> source){List<PerformanceAssetFact> result=source==null?new ArrayList<PerformanceAssetFact>():new ArrayList<PerformanceAssetFact>(source); Collections.sort(result,Comparator.comparing(PerformanceAssetFact::getAssetId,Comparator.nullsLast(Comparator.naturalOrder()))); return result;}
    private boolean hasQuarterBackupTraining(PerformanceAssetFact asset,PerformanceCalculationInput input){for(LabCollaborationRecord fact:input.getQuarterCollaborationFacts())if(eligibleBackupTraining(asset,input.getPeriod(),fact))return true;return false;}
    private boolean eligibleBackupTraining(PerformanceAssetFact asset,String closePeriod,LabCollaborationRecord fact){if(fact==null||!LabConstants.COLLAB_BACKUP.equals(fact.getCategory())||!LabConstants.REVIEW_APPROVED.equals(fact.getReviewStatus())||!hasText(fact.getEvidenceUrl())||!same(asset.getAssetId(),fact.getRelatedAssetId())||!same(asset.getPrimaryOwnerId(),fact.getToMemberId()))return false;try{YearMonth close=YearMonth.parse(closePeriod);YearMonth month=YearMonth.parse(fact.getPeriod());int first=((close.getMonthValue()-1)/3)*3+1;YearMonth start=YearMonth.of(close.getYear(),first);return !month.isBefore(start)&&!month.isAfter(close);}catch(DateTimeException ex){return false;}}
    private List<Map<String,Object>> assetDetail(List<PerformanceAssetFact> assets,PerformanceCalculationInput input){List<Map<String,Object>> result=new ArrayList<Map<String,Object>>(); for(PerformanceAssetFact asset:assets){Map<String,Object> item=new LinkedHashMap<String,Object>(); item.put("assetId",asset.getAssetId()); item.put("assetName",asset.getAssetName()); item.put("primaryOwnerId",asset.getPrimaryOwnerId()); item.put("activeBackup",asset.isActiveBackup()); item.put("quarterBackupTraining",hasQuarterBackupTraining(asset,input)); result.add(item);} return result;}
    private String triggerReason(List<Map<String,Object>> triggers){StringBuilder value=new StringBuilder(); for(Map<String,Object> trigger:triggers){if(value.length()>0)value.append(';'); value.append(trigger.get("code"));} return value.toString();}
    private void requireInput(PerformanceCalculationInput input){if(input==null||input.getMemberId()==null||!hasText(input.getPeriod())||input.getCutoffTime()==null)throw new ServiceException("Complete performance calculation input is required");}
    private static BigDecimal value(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
    private static String iso(Date value){return value==null?null:value.toInstant().toString();}
    private static boolean same(Object a,Object b){return a==null?b==null:a.equals(b);} private static boolean hasText(String v){return v!=null&&!v.trim().isEmpty();}
    private static final class CollaborationCalculation{private final BigDecimal score; private final Map<String,Object> detail; private CollaborationCalculation(BigDecimal score,Map<String,Object> detail){this.score=score;this.detail=detail;}}
}
