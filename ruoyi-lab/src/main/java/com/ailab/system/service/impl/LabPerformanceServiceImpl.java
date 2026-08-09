package com.ailab.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabPerfScore;
import com.ailab.system.domain.LabPeriodClose;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.CalibrationCommand;
import com.ailab.system.dto.CollaborationReviewCommand;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.PerformanceAssetFact;
import com.ailab.system.dto.PerformanceCalculationInput;
import com.ailab.system.dto.PerformanceCalculationResult;
import com.ailab.system.dto.RedLineRevokeCommand;
import com.ailab.system.mapper.LabPerformanceMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabPerformanceCalculator;
import com.ailab.system.service.LabPerformanceService;
import com.ailab.system.util.LabPeriodUtils;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabPerformanceServiceImpl implements LabPerformanceService {
    private final LabPerformanceMapper mapper; private final LabAccessService access; private final LabPerformanceCalculator calculator; private final Clock clock;
    @Autowired public LabPerformanceServiceImpl(LabPerformanceMapper mapper,LabAccessService access,LabPerformanceCalculator calculator){this(mapper,access,calculator,Clock.systemDefaultZone());}
    public LabPerformanceServiceImpl(LabPerformanceMapper mapper,LabAccessService access,LabPerformanceCalculator calculator,Clock clock){this.mapper=mapper;this.access=access;this.calculator=calculator;this.clock=clock;}

    @Override public List<LabPerfScore> listMyScores(String period,Long actorUserId){requireAssessmentPeriod(period);LabAccessContext actor=access.context(actorUserId);return mapper.selectScoresForMember(actor.getMemberId(),period);}
    @Override public List<LabPerfScore> listScores(String period,Long actorUserId){requireAssessmentPeriod(period);access.requireManager(actorUserId);return mapper.selectCurrentScores(period);}
    @Override public List<LabPerfScore> listScoreRevisions(Long memberId,String period,Long actorUserId){if(memberId==null)throw new ServiceException("Member is required");requireAssessmentPeriod(period);LabAccessContext actor=access.context(actorUserId);requireManager(actor);return mapper.selectScoreRevisions(memberId,period);}
    @Override public List<LabCollaborationRecord> listCollaboration(String period,Long actorUserId){requireMonth(period);LabAccessContext actor=access.context(actorUserId);return mapper.selectCollaborationList(period,actor.getMemberId(),actor.getBizLine(),actor.getRoleKey());}

    @Override @Transactional(readOnly=true)
    public PerformanceCalculationResult preview(Long memberId,String period,Long actorUserId){requireMonth(period);access.requireManager(actorUserId);if(memberId==null)throw new ServiceException("Member is required");return calculator.calculate(loadInput(memberId,period,false,Date.from(clock.instant())));}

    @Override @Transactional
    public LabCollaborationRecord createCollaboration(LabCollaborationRecord record,Long actorUserId){
        if(record==null||record.getToMemberId()==null||!hasText(record.getPeriod())||!hasText(record.getCategory())||record.getSignedScore()==null)throw new ServiceException("Period, target member, category and proposed score are required");
        requireMonth(record.getPeriod()); LabAccessContext actor=access.context(actorUserId); requireCreateCategory(record.getCategory(),actor);
        if(isPositive(record.getCategory())&&!hasText(record.getEvidenceUrl()))throw new ServiceException("Positive collaboration requires evidence URL");
        validateScoreSign(record.getCategory(),record.getSignedScore());lockOpenPeriod(record.getPeriod(),actorUserId); record.setFromMemberId(actor.getMemberId()); record.setReviewerId(null); record.setReviewTime(null); record.setReviewComment(null);
        record.setReviewStatus(LabConstants.REVIEW_PENDING); record.setIdempotencyKey(null); record.setVersion(0); record.setDelFlag(LabConstants.NO); record.setCreateBy(actor(actorUserId));
        requireAffected(mapper.insertCollaboration(record),"Collaboration record was not created"); return record;
    }

    @Override @Transactional
    public void reviewCollaboration(Long id,CollaborationReviewCommand command,Long actorUserId){
        if(id==null||command==null||command.getApprovedScore()==null||!hasText(command.getComment()))throw new ServiceException("Approved score and review comment are required");
        LabAccessContext actor=access.context(actorUserId); requireManager(actor);LabCollaborationRecord located=requireCollaboration(mapper.selectCollaborationById(id));requireMonth(located.getPeriod());lockOpenPeriod(located.getPeriod(),actorUserId);LabCollaborationRecord record=requireCollaboration(mapper.selectCollaborationForUpdate(id));
        if(!same(located.getPeriod(),record.getPeriod()))throw new ServiceException("Collaboration period changed concurrently");
        if(!LabConstants.REVIEW_PENDING.equals(record.getReviewStatus()))throw new ServiceException("Only pending collaboration may be reviewed");
        if(same(actor.getMemberId(),record.getFromMemberId()))throw new ServiceException("Creators cannot review their own collaboration fact");
        if(isPositive(record.getCategory())&&!hasText(record.getEvidenceUrl()))throw new ServiceException("Positive collaboration requires evidence URL");
        validateScoreSign(record.getCategory(),command.getApprovedScore()); BigDecimal approved=money(command.getApprovedScore());
        requireAffected(mapper.reviewCollaboration(id,approved,actor.getMemberId(),Date.from(clock.instant()),command.getComment().trim(),actor(actorUserId)),"Collaboration review changed concurrently");
    }

    @Override @Transactional
    public List<LabPerfScore> closePeriod(String period,String reason,Long actorUserId){
        requireMonth(period); if(!hasText(reason))throw new ServiceException("Close reason is required"); LabAccessContext actor=access.context(actorUserId);requireManager(actor);
        mapper.ensureOpenPeriod(period,actor(actorUserId));
        LabPeriodClose close=mapper.selectPeriodForUpdate(period);
        if(close==null)throw new ServiceException("Period row could not be locked");
        if(LabConstants.PERIOD_CLOSED.equals(close.getCloseStatus()))return mapper.selectCurrentScoresForUpdate(period);
        if(!LabConstants.PERIOD_OPEN.equals(close.getCloseStatus()))throw new ServiceException("Unsupported period close state");
        List<LabTask> tasks=safe(mapper.selectPeriodTasksForUpdate(period)); List<LabMember> members=safe(mapper.selectActiveMembersForUpdate());
        assertStable(tasks,members); List<Long> taskIds=new ArrayList<Long>();for(LabTask task:tasks)taskIds.add(task.getId());
        List<LabTaskEvidence> evidence=safe(mapper.selectEvidenceForTaskIds(taskIds)); List<LabTaskQualityGate> gates=safe(mapper.selectQualityGatesForTaskIds(taskIds));validateQualityGateContract(null,tasks,gates);
        String[] quarter=quarterRange(period);List<LabCollaborationRecord> quarterCollaboration=safe(mapper.selectQuarterCollaborationFactsForUpdate(quarter[0],period));
        List<LabCollaborationRecord> collaboration=new ArrayList<LabCollaborationRecord>(safe(mapper.selectCollaborationsForPeriodForUpdate(period)));
        List<PerformanceAssetFact> assetFacts=safe(mapper.selectCriticalAssetFactsForUpdate(quarter[0],period)); Date cutoff=Date.from(clock.instant());
        for(LabTask task:tasks)if(isKeyMonth(task)&&!LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())){
            LabCollaborationRecord overdue=overdue(task,period,actor,actorUserId,cutoff); int inserted=mapper.insertOverdueRecord(overdue); if(inserted<0||inserted>1)throw new ServiceException("Invalid overdue idempotency result"); if(inserted==1)collaboration.add(overdue);
        }
        Map<Long,List<LabTaskEvidence>> evidenceMap=evidenceMap(evidence); Map<Long,List<LabTaskQualityGate>> gateMap=gateMap(gates); List<LabPerfScore> scores=new ArrayList<LabPerfScore>();
        for(LabMember member:members){
            PerformanceCalculationInput input=input(member.getId(),period,true,cutoff,tasks,evidenceMap,gateMap,collaboration,quarterCollaboration,assetFacts); PerformanceCalculationResult result=calculator.calculate(input);
            Integer max=mapper.selectMaxRevision(member.getId(),period); int history=mapper.markCurrentScoresHistorical(period,member.getId(),actor(actorUserId)); if(history<0||history>1)throw new ServiceException("Current performance revision invariant is broken");
            LabPerfScore score=score(member.getId(),period,max==null?1:max+1,result,cutoff,actorUserId); requireAffected(mapper.insertPerfScore(score),"Performance revision was not inserted");scores.add(score);
        }
        int locked=mapper.lockTasksForPeriod(period,LabConstants.YES);if(locked!=tasks.size())throw new ServiceException("Period task lock changed concurrently");
        requireAffected(mapper.closePeriod(close.getId(),close.getVersion(),actor(actorUserId),cutoff,reason.trim()),"Period close changed concurrently"); return scores;
    }

    @Override @Transactional
    public void reopenPeriod(String period,String reason,Long actorUserId){
        requireMonth(period);if(!hasText(reason))throw new ServiceException("Reopen reason is required");LabAccessContext actor=access.context(actorUserId);requireManager(actor);
        LabPeriodClose close=mapper.selectPeriodForUpdate(period);if(close==null||!LabConstants.PERIOD_CLOSED.equals(close.getCloseStatus()))throw new ServiceException("Only a closed period may be reopened");
        List<LabTask> tasks=safe(mapper.selectPeriodTasksForUpdate(period));assertStable(tasks,Collections.<LabMember>emptyList());List<LabPerfScore> currentScores=safe(mapper.selectCurrentScoresForUpdate(period));
        int history=mapper.markPeriodScoresHistorical(period,actor(actorUserId));if(history!=currentScores.size())throw new ServiceException("Period score revisions changed concurrently");
        int unlocked=mapper.lockTasksForPeriod(period,LabConstants.NO);if(unlocked!=tasks.size())throw new ServiceException("Period task unlock changed concurrently");
        requireAffected(mapper.reopenPeriod(close.getId(),close.getVersion(),actor(actorUserId),Date.from(clock.instant()),reason.trim()),"Period reopen changed concurrently");
    }

    @Override @Transactional
    public void confirmMonthlyScore(Long id,Integer version,Long actorUserId){
        if(id==null||version==null)throw new ServiceException("Score id and version are required");LabAccessContext actor=access.context(actorUserId);LabPerfScore score=requireScore(mapper.selectScoreForUpdate(id));requireMonth(score.getPeriod());
        if(!LabConstants.YES.equals(score.getCurrentFlag()))throw new ServiceException("Only current monthly revision may be confirmed");
        if(LabConstants.YES.equals(score.getRedLineFlag())&&!LabConstants.YES.equals(score.getRevokedFlag())&&!isManager(actor))throw new ServiceException("Only managers may confirm an active red-line score");
        if(!isManager(actor)&&!same(actor.getMemberId(),score.getMemberId()))throw new ServiceException("Members may confirm only their own score");
        requireAffected(mapper.confirmScore(id,version,actor.getMemberId(),Date.from(clock.instant()),actor(actorUserId)),"Score confirmation changed concurrently");
    }

    @Override @Transactional
    public void revokeRedLine(Long id,RedLineRevokeCommand command,Long actorUserId){
        if(id==null||command==null||!validEvidenceUrl(command.getEvidenceUrl())||!hasText(command.getReason()))throw new ServiceException("Corrective evidence URL and reason are required");
        LabAccessContext actor=access.context(actorUserId);requireManager(actor);LabPerfScore score=requireScore(mapper.selectScoreForUpdate(id));
        if(!LabConstants.YES.equals(score.getCurrentFlag())||!LabConstants.YES.equals(score.getRedLineFlag())||LabConstants.YES.equals(score.getRevokedFlag()))throw new ServiceException("Only an active current red line may be revoked");
        requireAffected(mapper.revokeRedLine(id,score.getVersion(),command.getEvidenceUrl().trim(),command.getReason().trim(),actor.getMemberId(),Date.from(clock.instant()),actor(actorUserId)),"Red-line correction changed concurrently");
    }

    @Override @Transactional
    public LabPerfScore calibrateQuarter(String quarter,Long memberId,CalibrationCommand command,Long actorUserId){
        String[] range=quarterRangeFromQuarter(quarter);if(memberId==null||command==null||command.getScore()==null||command.getScore().compareTo(BigDecimal.ZERO)<0||command.getScore().compareTo(new BigDecimal("100"))>0||!hasText(command.getComment()))throw new ServiceException("Quarter calibration requires member, score 0-100 and comment");
        LabAccessContext actor=access.context(actorUserId);requireManager(actor);List<LabPerfScore> months=safe(mapper.selectCurrentMonthlyScoresForUpdate(memberId,range[0],range[1]));
        if(months.size()!=3)throw new ServiceException("Quarter calibration requires three current monthly details");
        boolean activeMonthlyRedLine=false;for(LabPerfScore month:months)if(LabConstants.YES.equals(month.getRedLineFlag())&&!LabConstants.YES.equals(month.getRevokedFlag()))activeMonthlyRedLine=true;
        if(activeMonthlyRedLine)throw new ServiceException("Active red line must be revoked before quarter calibration");
        Map<String,Object> detail=new LinkedHashMap<String,Object>();detail.put("calculationVersion","AILAB_QUARTER_CALIBRATION_V1");detail.put("quarter",quarter);detail.put("sourceMonths",monthlyRefs(months));detail.put("manualScore",money(command.getScore()));detail.put("comment",command.getComment().trim());detail.put("activeMonthlyRedLine",activeMonthlyRedLine);
        Integer max=mapper.selectMaxRevision(memberId,quarter);int history=mapper.markCurrentScoresHistorical(quarter,memberId,actor(actorUserId));if(history<0||history>1)throw new ServiceException("Current calibration revision invariant is broken");
        LabPerfScore score=new LabPerfScore();score.setMemberId(memberId);score.setPeriod(quarter);score.setRevisionNo(max==null?1:max+1);score.setCurrentFlag(LabConstants.YES);score.setScore(money(command.getScore()));score.setCalibrateScore(money(command.getScore()));
        score.setDetailJson(JSON.toJSONString(detail));score.setCalculationVersion("AILAB_QUARTER_CALIBRATION_V1");score.setCutoffTime(Date.from(clock.instant()));score.setResultStatus(LabConstants.PERF_RESULT_NORMAL);score.setRedLineFlag(LabConstants.NO);score.setRedLineReason(null);score.setRevokedFlag(LabConstants.NO);
        score.setCalibrationStatus("CALIBRATED");score.setCalibratorId(actor.getMemberId());score.setCalibrationNote(command.getComment().trim());score.setCalibrationTime(Date.from(clock.instant()));score.setVersion(0);score.setDelFlag(LabConstants.NO);score.setCreateBy(actor(actorUserId));
        requireAffected(mapper.insertPerfScore(score),"Quarter calibration revision was not inserted");return score;
    }

    private PerformanceCalculationInput loadInput(Long memberId,String period,boolean close,Date cutoff){List<LabTask> tasks=safe(mapper.selectPeriodTasks(period));List<Long> ids=taskIds(tasks);List<LabTaskQualityGate> gates=safe(mapper.selectQualityGatesForTaskIds(ids));validateQualityGateContract(null,tasks,gates);String[] q=quarterRange(period);return input(memberId,period,close,cutoff,tasks,evidenceMap(safe(mapper.selectEvidenceForTaskIds(ids))),gateMap(gates),safe(mapper.selectCollaborationForPeriod(period)),safe(mapper.selectQuarterCollaborationFacts(q[0],period)),safe(mapper.selectCriticalAssetFacts(q[0],period)));}
    private PerformanceCalculationInput input(Long memberId,String period,boolean close,Date cutoff,List<LabTask> tasks,Map<Long,List<LabTaskEvidence>> evidence,Map<Long,List<LabTaskQualityGate>> gates,List<LabCollaborationRecord> collaboration,List<LabCollaborationRecord> quarterCollaboration,List<PerformanceAssetFact> assets){PerformanceCalculationInput in=new PerformanceCalculationInput();in.setMemberId(memberId);in.setPeriod(period);in.setCloseMode(close);in.setCutoffTime(cutoff);in.setTasks(tasks);in.setEvidenceByTask(evidence);in.setQualityGatesByTask(gates);in.setCollaborationRecords(collaboration);in.setQuarterCollaborationFacts(quarterCollaboration);in.setAssetFacts(assets);return in;}
    private LabPerfScore score(Long memberId,String period,int revision,PerformanceCalculationResult result,Date cutoff,Long actor){LabPerfScore score=new LabPerfScore();score.setMemberId(memberId);score.setPeriod(period);score.setRevisionNo(revision);score.setCurrentFlag(LabConstants.YES);score.setDeliveryScore(result.getDeliveryScore());score.setQualityScore(result.getQualityScore());score.setCollaborationScore(result.getCollaborationScore());score.setScore(result.getTotalScore());score.setDetailJson(result.getDetailJson());score.setCalculationVersion(LabConstants.PERF_FORMULA_VERSION);score.setCutoffTime(cutoff);score.setResultStatus(result.getResultStatus());score.setRedLineFlag(result.isRedLine()?LabConstants.YES:LabConstants.NO);score.setRedLineReason(result.getRedLineReason());score.setRevokedFlag(LabConstants.NO);score.setConfirmationStatus("PENDING");score.setCalibrationStatus("PENDING");score.setVersion(0);score.setDelFlag(LabConstants.NO);score.setCreateBy(actor(actor));return score;}
    private LabCollaborationRecord overdue(LabTask task,String period,LabAccessContext actor,Long actorUserId,Date cutoff){LabCollaborationRecord r=new LabCollaborationRecord();r.setTaskId(task.getId());r.setPeriod(period);r.setFromMemberId(actor.getMemberId());r.setToMemberId(task.getOwnerId());r.setCategory(LabConstants.COLLAB_OVERDUE);r.setSignedScore(new BigDecimal("-1.00"));r.setEvidenceUrl("system://period-close/"+period+"/task/"+task.getId());r.setReviewerId(actor.getMemberId());r.setReviewStatus(LabConstants.REVIEW_APPROVED);r.setReviewTime(cutoff);r.setReviewComment("Unconfirmed monthly key task at cutoff");r.setIdempotencyKey("PERIOD_OVERDUE:"+period+":"+task.getId());r.setVersion(0);r.setDelFlag(LabConstants.NO);r.setCreateBy(actor(actorUserId));return r;}
    private Map<Long,List<LabTaskEvidence>> evidenceMap(List<LabTaskEvidence> rows){Map<Long,List<LabTaskEvidence>> map=new HashMap<Long,List<LabTaskEvidence>>();for(LabTaskEvidence row:rows){List<LabTaskEvidence> list=map.get(row.getTaskId());if(list==null){list=new ArrayList<LabTaskEvidence>();map.put(row.getTaskId(),list);}list.add(row);}return map;}
    private Map<Long,List<LabTaskQualityGate>> gateMap(List<LabTaskQualityGate> rows){Map<Long,List<LabTaskQualityGate>> map=new HashMap<Long,List<LabTaskQualityGate>>();for(LabTaskQualityGate row:rows){List<LabTaskQualityGate> list=map.get(row.getTaskId());if(list==null){list=new ArrayList<LabTaskQualityGate>();map.put(row.getTaskId(),list);}list.add(row);}return map;}
    private List<Long> taskIds(List<LabTask> tasks){List<Long> ids=new ArrayList<Long>();for(LabTask task:tasks)ids.add(task.getId());return ids;}
    private void validateQualityGateContract(Long memberId,List<LabTask> tasks,List<LabTaskQualityGate> gates){Map<Long,Integer> counts=new HashMap<Long,Integer>();for(LabTaskQualityGate gate:gates)if(gate!=null&&gate.getTaskId()!=null)counts.put(gate.getTaskId(),counts.containsKey(gate.getTaskId())?counts.get(gate.getTaskId())+1:1);for(LabTask task:tasks)if(isKeyMonth(task)&&(memberId==null||same(memberId,task.getOwnerId()))&&!counts.containsKey(task.getId()))throw new ServiceException("Monthly key task "+task.getId()+" ("+(hasText(task.getTitle())?task.getTitle():"untitled")+") requires at least one applicable quality gate");}
    private List<Map<String,Object>> monthlyRefs(List<LabPerfScore> rows){Collections.sort(rows,Comparator.comparing(LabPerfScore::getPeriod));List<Map<String,Object>> refs=new ArrayList<Map<String,Object>>();for(LabPerfScore row:rows){Map<String,Object> ref=new LinkedHashMap<String,Object>();ref.put("scoreId",row.getId());ref.put("period",row.getPeriod());ref.put("revisionNo",row.getRevisionNo());ref.put("score",row.getScore());ref.put("resultStatus",row.getResultStatus());ref.put("activeRedLine",LabConstants.YES.equals(row.getRedLineFlag())&&!LabConstants.YES.equals(row.getRevokedFlag()));refs.add(ref);}return refs;}
    private String[] quarterRange(String month){YearMonth ym=YearMonth.parse(month);int first=((ym.getMonthValue()-1)/3)*3+1;return new String[]{String.format("%04d-%02d",ym.getYear(),first),String.format("%04d-%02d",ym.getYear(),first+2)};}
    private String[] quarterRangeFromQuarter(String quarter){if(quarter==null||!quarter.matches("^\\d{4}-Q[1-4]$"))throw new ServiceException("Quarter format must be YYYY-Qn");int year=Integer.parseInt(quarter.substring(0,4));int q=Integer.parseInt(quarter.substring(6));int first=(q-1)*3+1;return new String[]{String.format("%04d-%02d",year,first),String.format("%04d-%02d",year,first+2)};}
    private void requireMonth(String period){try{LabPeriodUtils.parseMonth(period);}catch(IllegalArgumentException e){throw new ServiceException(e.getMessage());}}
    private void requireAssessmentPeriod(String period){if(period!=null&&period.matches("^\\d{4}-Q[1-4]$"))return;requireMonth(period);}
    private void requireCreateCategory(String category,LabAccessContext actor){if(isPositive(category))return;if(LabConstants.COLLAB_DEDUCTION.equals(category)&&isManager(actor))return;throw new ServiceException("Only managers may create manual deductions; overdue records are system-owned");}
    private void validateScoreSign(String category,BigDecimal score){if(isPositive(category)&&score.compareTo(BigDecimal.ZERO)<0)throw new ServiceException("Positive collaboration score cannot be negative");if(!isPositive(category)&&score.compareTo(BigDecimal.ZERO)>0)throw new ServiceException("Deduction score cannot be positive");}
    private boolean isPositive(String category){return LabConstants.COLLAB_CROSS_DEPT.equals(category)||LabConstants.COLLAB_KNOWLEDGE.equals(category)||LabConstants.COLLAB_BACKUP.equals(category);}
    private boolean validEvidenceUrl(String value){return hasText(value)&&(value.startsWith("https://")||value.startsWith("http://"));}
    private LabPeriodClose lockOpenPeriod(String period,Long actorUserId){mapper.ensureOpenPeriod(period,actor(actorUserId));LabPeriodClose close=mapper.selectPeriodForUpdate(period);if(close==null)throw new ServiceException("Period row could not be locked");if(!LabConstants.PERIOD_OPEN.equals(close.getCloseStatus()))throw new ServiceException("Collaboration changes require an open period");return close;}
    private boolean isKeyMonth(LabTask t){return LabConstants.TASK_LEVEL_MONTH.equals(t.getTaskLevel())&&LabConstants.TASK_TYPE_KEY.equals(t.getTaskType());}
    private void assertStable(List<LabTask> tasks,List<LabMember> members){Long last=null;for(LabTask t:tasks){if(t.getId()==null||(last!=null&&t.getId()<last))throw new ServiceException("Tasks must be locked in stable id order");last=t.getId();}last=null;for(LabMember m:members){if(m.getId()==null||(last!=null&&m.getId()<last))throw new ServiceException("Members must be locked in stable id order");last=m.getId();}}
    private <T> List<T> safe(List<T> rows){return rows==null?new ArrayList<T>():rows;} private void requireManager(LabAccessContext c){if(!isManager(c))throw new ServiceException("Manager role is required");}
    private boolean isManager(LabAccessContext c){return LabAccessServiceImpl.MANAGER.equals(c.getRoleKey());} private LabCollaborationRecord requireCollaboration(LabCollaborationRecord v){if(v==null)throw new ServiceException("Collaboration record does not exist");return v;}
    private LabPerfScore requireScore(LabPerfScore v){if(v==null)throw new ServiceException("Performance score does not exist");return v;} private int requireAffected(int count,String message){if(count!=1)throw new ServiceException(message);return count;}
    private static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);} private String actor(Long id){return String.valueOf(id);} private boolean same(Object a,Object b){return a==null?b==null:a.equals(b);} private boolean hasText(String v){return v!=null&&!v.trim().isEmpty();}
}
