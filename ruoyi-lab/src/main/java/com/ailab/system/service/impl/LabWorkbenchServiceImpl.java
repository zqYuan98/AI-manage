package com.ailab.system.service.impl;

import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.ManagerWorkbench;
import com.ailab.system.dto.MemberWorkbench;
import com.ailab.system.mapper.LabWorkbenchMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabWorkbenchService;
import com.ruoyi.common.exception.ServiceException;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LabWorkbenchServiceImpl implements LabWorkbenchService {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private final LabAccessService access;
    private final LabWorkbenchMapper mapper;
    public LabWorkbenchServiceImpl(LabAccessService access, LabWorkbenchMapper mapper){this.access=access;this.mapper=mapper;}

    @Override public ManagerWorkbench manager(String period, Date asOf, Long actorId) {
        LabAccessContext scope=scope(period,asOf,actorId);
        if(!LabAccessServiceImpl.MANAGER.equals(scope.getRoleKey())) throw new ServiceException("Manager role is required");
        return management(period,asOf,scope,true);
    }
    @Override public ManagerWorkbench lead(String period, Date asOf, Long actorId) {
        LabAccessContext scope=scope(period,asOf,actorId);
        if(!LabAccessServiceImpl.LEAD.equals(scope.getRoleKey())||blank(scope.getBizLine())) throw new ServiceException("Line lead role is required");
        return management(period,asOf,scope,false);
    }
    @Override public MemberWorkbench member(String period, Date asOf, Long actorId) {
        LabAccessContext scope=scope(period,asOf,actorId);
        if(!LabAccessServiceImpl.MEMBER.equals(scope.getRoleKey())) throw new ServiceException("Member role is required");
        MemberWorkbench result=new MemberWorkbench(); result.setPeriod(period); result.setMemberId(scope.getMemberId()); result.setAsOf(asOf);
        result.setMonthlyResults(bounded(mapper.selectOwnMonthlyResults(scope,period),"monthly results"));
        result.setWeeklyCommitments(bounded(mapper.selectOwnWeeklyCommitments(scope,period),"weekly commitments"));
        result.setDueItems(bounded(mapper.selectOwnDueItems(scope,asOf),"due items"));
        result.setBlocks(bounded(mapper.selectOwnBlocks(scope),"blocks"));
        result.setMissingEvidence(bounded(mapper.selectOwnMissingEvidence(scope,period),"missing evidence")); return result;
    }
    private ManagerWorkbench management(String period,Date asOf,LabAccessContext scope,boolean manager) {
        ManagerWorkbench result=new ManagerWorkbench(); result.setPeriod(period); result.setAsOf(asOf);
        result.setScopeType(manager?"MANAGER":"BIZ_LINE"); result.setBizLine(manager?null:scope.getBizLine());
        result.setManagerActionsAllowed(manager); result.setPendingDecisions(bounded(mapper.selectPendingDecisions(scope,period),"pending decisions"));
        result.setNewBlocks(bounded(mapper.selectNewBlocks(scope,new Date(asOf.getTime()-7L*DAY)),"new blocks"));
        result.setForecastDelays(bounded(mapper.selectForecastDelays(scope,asOf),"forecast delays"));
        result.setPendingAcceptance(bounded(mapper.selectPendingAcceptance(scope),"pending acceptance"));
        result.setStaleKeyResults(bounded(mapper.selectStaleKeyResults(scope,new Date(asOf.getTime()-14L*DAY)),"stale key results"));
        result.setTeamCommitments(bounded(mapper.selectTeamCommitmentCounts(scope,period,asOf),"team commitments")); return result;
    }
    private LabAccessContext scope(String period,Date asOf,Long actorId){
        if(asOf==null) throw new ServiceException("Workbench cutoff is required");
        try{YearMonth.parse(period);}catch(Exception ex){throw new ServiceException("Workbench period must be YYYY-MM");}
        return access.context(actorId);
    }
    private boolean blank(String value){return value==null||value.trim().isEmpty();}
    private <T> List<T> bounded(List<T> values,String bucket){
        if(values!=null&&values.size()>200) throw new ServiceException("Workbench " + bucket + " exceeded its safe result bound");
        return values;
    }
}
