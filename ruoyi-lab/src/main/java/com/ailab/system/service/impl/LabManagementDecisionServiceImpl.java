package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabManagementDecision;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabManagementDecisionMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabManagementDecisionService;
import com.ruoyi.common.exception.ServiceException;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabManagementDecisionServiceImpl implements LabManagementDecisionService {
    private final LabAccessService access; private final LabManagementDecisionMapper mapper;
    public LabManagementDecisionServiceImpl(LabAccessService access,LabManagementDecisionMapper mapper){this.access=access;this.mapper=mapper;}
    @Override public List<LabManagementDecision> list(String period,String status,Long actorId){
        validatePeriod(period); if(status!=null&&!Arrays.asList("OPEN","DONE").contains(status)) throw new ServiceException("Decision status is invalid");
        List<LabManagementDecision> rows=mapper.selectDecisionList(access.context(actorId),period,status);
        if(rows!=null&&rows.size()>200) throw new ServiceException("Decision list exceeded its safe result bound");
        return rows;
    }
    @Override @Transactional public LabManagementDecision create(LabManagementDecision value,Long actorId){
        LabAccessContext actor=access.context(actorId); validate(value);
        if(LabAccessServiceImpl.MEMBER.equals(actor.getRoleKey())) throw new ServiceException("Members cannot create management decisions");
        if(LabAccessServiceImpl.LEAD.equals(actor.getRoleKey())&&!same(actor.getBizLine(),value.getBizLine())) throw new ServiceException("Line leads may create only same-line decisions");
        if(!LabAccessServiceImpl.MANAGER.equals(actor.getRoleKey())&&!LabAccessServiceImpl.LEAD.equals(actor.getRoleKey())) throw new ServiceException("Decision creator role is invalid");
        String ownerLine=mapper.selectActiveMemberBizLine(value.getOwnerId());
        if(ownerLine==null) throw new ServiceException("Decision owner must be an active member");
        if(LabAccessServiceImpl.LEAD.equals(actor.getRoleKey())&&!same(actor.getBizLine(),ownerLine)) throw new ServiceException("Line leads may assign only same-line members");
        if(value.getRelatedTaskId()!=null){
            String taskLine=mapper.selectTaskBizLine(value.getRelatedTaskId());
            if(taskLine==null) throw new ServiceException("Related task does not exist");
            if(LabAccessServiceImpl.LEAD.equals(actor.getRoleKey())&&!same(actor.getBizLine(),taskLine)) throw new ServiceException("Related task is outside the lead business line");
        }
        if(value.getRelatedGoalId()!=null){
            String goalLine=mapper.selectGoalOwnerBizLine(value.getRelatedGoalId());
            if(goalLine==null) throw new ServiceException("Related goal does not exist");
            if(LabAccessServiceImpl.LEAD.equals(actor.getRoleKey())&&!same(actor.getBizLine(),goalLine)) throw new ServiceException("Related goal is outside the lead business line");
        }
        value.setId(null); value.setDecisionStatus("OPEN"); value.setVersion(0); value.setDelFlag(LabConstants.NO);
        value.setCreateBy(String.valueOf(actorId)); if(mapper.insertDecision(value)!=1) throw new ServiceException("Management decision could not be saved"); return value;
    }
    @Override @Transactional public void complete(Long id,Integer version,Long actorId){
        if(id==null||version==null) throw new ServiceException("Decision id and version are required");
        LabAccessContext actor=access.context(actorId); LabManagementDecision stored=mapper.selectDecisionForUpdate(id);
        if(stored==null||!LabConstants.NO.equals(stored.getDelFlag())) throw new ServiceException("Management decision does not exist");
        boolean allowed=LabAccessServiceImpl.MANAGER.equals(actor.getRoleKey())
                ||(LabAccessServiceImpl.LEAD.equals(actor.getRoleKey())&&same(actor.getBizLine(),stored.getBizLine()))
                ||same(actor.getMemberId(),stored.getOwnerId());
        if(!allowed) throw new ServiceException("Decision is outside the current action scope");
        if(!"OPEN".equals(stored.getDecisionStatus())) throw new ServiceException("Only open decisions can be completed");
        if(!version.equals(stored.getVersion())||mapper.completeDecision(id,version,String.valueOf(actorId))!=1) throw new ServiceException("Decision changed; refresh and retry");
    }
    private void validate(LabManagementDecision value){
        if(value==null||blank(value.getProblem())||blank(value.getDecisionContent())||blank(value.getBizLine())
                ||value.getOwnerId()==null||value.getDueDate()==null) throw new ServiceException("Problem, decision, owner, due date and business line are required");
        validatePeriod(value.getPeriod()); if(value.getProblem().length()>1000||value.getDecisionContent().length()>2000) throw new ServiceException("Decision text is too long");
    }
    private void validatePeriod(String value){try{YearMonth.parse(value);}catch(Exception ex){throw new ServiceException("Decision period must be YYYY-MM");}}
    private boolean blank(String value){return value==null||value.trim().isEmpty();}
    private boolean same(Object a,Object b){return a!=null&&a.equals(b);}
}
