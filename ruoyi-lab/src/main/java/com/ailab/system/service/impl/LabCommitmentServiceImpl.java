package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskExecutionEvent;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.WeeklyCommitmentCommand;
import com.ailab.system.mapper.LabCommitmentMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabCommitmentService;
import com.ailab.system.util.LabPeriodUtils;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for member-owned weekly execution facts. */
@Service
public class LabCommitmentServiceImpl implements LabCommitmentService {
    private final LabCommitmentMapper commitmentMapper;
    private final LabTaskMapper taskMapper;
    private final LabAccessService accessService;
    private final LabTaskExecutionMigrationService cutover;
    private final Clock clock;

    @Autowired
    public LabCommitmentServiceImpl(LabCommitmentMapper commitmentMapper, LabTaskMapper taskMapper,
            LabAccessService accessService, LabTaskExecutionMigrationService cutover) {
        this(commitmentMapper, taskMapper, accessService, cutover, Clock.systemDefaultZone());
    }

    public LabCommitmentServiceImpl(LabCommitmentMapper commitmentMapper, LabTaskMapper taskMapper,
            LabAccessService accessService, LabTaskExecutionMigrationService cutover, Clock clock) {
        this.commitmentMapper=commitmentMapper; this.taskMapper=taskMapper; this.accessService=accessService;
        this.cutover=cutover; this.clock=clock;
    }

    @Override
    @Transactional
    public LabTask create(WeeklyCommitmentCommand command, Long userId) {
        requireCreate(command);
        LabAccessContext context = context(userId);
        LabTask parent = taskMapper.selectTaskForUpdate(command.getParentTaskId());
        if (parent == null || !LabConstants.TASK_LEVEL_MONTH.equals(parent.getTaskLevel())
                || !LabConstants.WORKFLOW_ACTIVE.equals(parent.getWorkflowStatus())
                || LabConstants.YES.equals(parent.getPeriodLockFlag())) {
            throw new ServiceException("周承诺必须关联进行中且未关期的月度结果");
        }
        accessService.requireTaskRead(parent, userId);
        if (!context.getMemberId().equals(parent.getOwnerId())) {
            throw new ServiceException("成员只能在自己的月度结果下创建周承诺");
        }
        LabPeriodUtils.parseWeek(command.getPeriod());
        LabTask task = new LabTask();
        task.setParentId(parent.getId()); task.setGoalId(parent.getGoalId()); task.setMilestoneId(parent.getMilestoneId());
        task.setTaskLevel(LabConstants.TASK_LEVEL_WEEK); task.setPeriod(command.getPeriod()); task.setBizLine(parent.getBizLine());
        task.setTaskType("daily"); task.setTitle(command.getTitle()); task.setOwnerId(context.getMemberId());
        task.setDeptId(parent.getDeptId()); task.setPlanDate(command.getPlanDate()); task.setDeliverable(command.getDeliverable());
        task.setPerfWeight(BigDecimal.ZERO); task.setGoalWeight(BigDecimal.ZERO);
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); task.setResultStatus(LabConstants.RESULT_DOING);
        task.setExecutionStatus(LabConstants.EXECUTION_ACTIVE); task.setExecutionVersion(0);
        task.setCoordinationRequired(defaultNo(command.getCoordinationRequired()));
        task.setCoordinationOwnerId(command.getCoordinationOwnerId()); task.setCoordinationDeptId(command.getCoordinationDeptId());
        task.setCoordinationContent(command.getCoordinationContent()); task.setCoordinationSupport(command.getCoordinationSupport());
        task.setCurrentBlockFlag(LabConstants.NO); task.setPeriodLockFlag(LabConstants.NO);
        task.setVersion(0); task.setDelFlag(LabConstants.NO); task.setCreateBy(actor(userId));
        if (taskMapper.insertTask(task) != 1) throw new ServiceException("周承诺创建失败，请刷新后重试");
        appendEvent(task, null, LabConstants.EXECUTION_ACTIVE, LabConstants.RESULT_DOING, null,
                context.getMemberId(), "CREATE", "创建周承诺", 0);
        cutover.recordPointOfNoReturn("CREATE");
        return task;
    }

    @Override @Transactional
    public void complete(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId) {
        if (command == null || command.getActualFinishTime() == null || blank(command.getResultDescription())) {
            throw new ServiceException("完成周承诺必须填写实际完成时间和结果说明");
        }
        LabTask task=ownedActive(taskId,version,userId); String result=isAfterPlanDay(command.getActualFinishTime(),task.getPlanDate())
                ? LabConstants.RESULT_DELAYED : LabConstants.RESULT_ONTIME;
        terminal(task, version, LabConstants.EXECUTION_SELF_DONE, result, command.getActualFinishTime(),
                command.getResultDescription(), null, null, "SELF_COMPLETE", "成员确认完成", userId);
    }

    @Override @Transactional
    public void markUndone(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId) {
        if (command == null || blank(command.getFailReason()) || blank(command.getNextAction())) {
            throw new ServiceException("未完成周承诺必须填写原因和下一步");
        }
        LabTask task=ownedActive(taskId,version,userId);
        terminal(task, version, LabConstants.EXECUTION_SELF_UNDONE, LabConstants.RESULT_UNDONE, null,
                null, command.getFailReason(), command.getNextAction(), "SELF_UNDONE", "成员确认本周未完成", userId);
    }

    @Override @Transactional
    public void correct(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId) {
        if (command == null || blank(command.getReason())) throw new ServiceException("纠正周承诺必须填写原因");
        LabTask task=loadVersioned(taskId,version); requireOwnerOrManager(task,userId);
        if (!LabConstants.EXECUTION_SELF_DONE.equals(task.getExecutionStatus())
                && !LabConstants.EXECUTION_SELF_UNDONE.equals(task.getExecutionStatus())) {
            throw new ServiceException("只有已闭环周承诺可以纠正");
        }
        String oldResult=task.getResultStatus(); Date oldFinish=task.getActualFinishTime();
        update(task,version,LabConstants.EXECUTION_ACTIVE,LabConstants.RESULT_DOING,null,null,null,null,userId);
        appendEvent(task, task.getExecutionStatus(), LabConstants.EXECUTION_ACTIVE, oldResult, oldFinish,
                context(userId).getMemberId(), "CORRECT", command.getReason(), version+1);
        cutover.recordPointOfNoReturn("CORRECT");
    }

    @Override @Transactional
    public void cancel(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId) {
        accessService.requireManager(userId);
        if (command == null || blank(command.getReason())) throw new ServiceException("取消周承诺必须填写范围变化原因");
        LabTask task=loadVersioned(taskId,version);
        if (!LabConstants.EXECUTION_ACTIVE.equals(task.getExecutionStatus())
                && !LabConstants.EXECUTION_PLANNED.equals(task.getExecutionStatus())) throw new ServiceException("当前状态不能取消");
        closeOpenBlock(task,userId);
        update(task,version,LabConstants.EXECUTION_CANCELLED,LabConstants.RESULT_DOING,null,null,null,null,userId);
        appendEvent(task,task.getExecutionStatus(),LabConstants.EXECUTION_CANCELLED,task.getResultStatus(),null,
                context(userId).getMemberId(),"CANCEL",command.getReason(),version+1);
        cutover.recordPointOfNoReturn("CANCEL");
    }

    @Override @Transactional
    public LabTask carry(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId) {
        if (command == null || blank(command.getPeriod()) || command.getPlanDate() == null) {
            throw new ServiceException("转期必须填写目标周和截止日期");
        }
        LabPeriodUtils.parseWeek(command.getPeriod());
        LabTask source=loadVersioned(taskId,version); requireOwner(source,userId);
        if (!LabConstants.EXECUTION_SELF_UNDONE.equals(source.getExecutionStatus())) throw new ServiceException("只有本周未完成承诺可以转期");
        LabTask existing=commitmentMapper.selectCarriedCommitment(taskId,command.getPeriod()); if(existing!=null)return existing;
        java.util.List<LabTaskBlockEvent> sourceBlocks=taskMapper.selectBlockEvents(source.getId());
        LabTaskBlockEvent sourceBlock=sourceBlocks==null||sourceBlocks.isEmpty()?null:sourceBlocks.get(0);
        LabTask carried=new LabTask(); carried.setParentId(source.getParentId()); carried.setGoalId(source.getGoalId());
        carried.setMilestoneId(source.getMilestoneId()); carried.setTaskLevel(LabConstants.TASK_LEVEL_WEEK);
        carried.setPeriod(command.getPeriod()); carried.setBizLine(source.getBizLine()); carried.setTaskType("daily");
        carried.setTitle(blank(command.getTitle())?source.getTitle():command.getTitle()); carried.setOwnerId(source.getOwnerId());
        carried.setDeptId(source.getDeptId()); carried.setPlanDate(command.getPlanDate());
        carried.setDeliverable(blank(command.getDeliverable())?source.getDeliverable():command.getDeliverable());
        carried.setPerfWeight(BigDecimal.ZERO); carried.setGoalWeight(BigDecimal.ZERO);
        carried.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); carried.setResultStatus(LabConstants.RESULT_DOING);
        carried.setExecutionStatus(LabConstants.EXECUTION_ACTIVE); carried.setExecutionVersion(0); carried.setCarriedFromId(source.getId());
        Date carriedTime=Date.from(clock.instant());
        carried.setBlockFlag(sourceBlock==null?LabConstants.NO:LabConstants.YES);
        carried.setBlockStartTime(sourceBlock==null?null:carriedTime); carried.setPeriodLockFlag(LabConstants.NO);
        carried.setCoordinationRequired(source.getCoordinationRequired()); carried.setCoordinationOwnerId(source.getCoordinationOwnerId());
        carried.setCoordinationDeptId(source.getCoordinationDeptId()); carried.setCoordinationContent(source.getCoordinationContent());
        carried.setCoordinationSupport(source.getCoordinationSupport()); carried.setVersion(0); carried.setDelFlag(LabConstants.NO);
        carried.setCreateBy(actor(userId)); if(taskMapper.insertTask(carried)!=1)throw new ServiceException("转期承诺创建失败");
        if(sourceBlock!=null){LabTaskBlockEvent linked=new LabTaskBlockEvent();linked.setTaskId(carried.getId());
            linked.setEpisodeNo(taskMapper.selectNextBlockEpisodeNo(carried.getId()));linked.setCarriedFromEventId(sourceBlock.getId());
            linked.setBlockType(sourceBlock.getBlockType());linked.setBlockReason(sourceBlock.getBlockReason());
            linked.setBlockStartTime(carriedTime);linked.setBlockStatus("OPEN");linked.setDelFlag(LabConstants.NO);
            linked.setCreateBy(actor(userId));if(taskMapper.insertBlockEvent(linked)!=1)throw new ServiceException("转期阻塞 episode 创建失败");}
        appendEvent(carried,null,LabConstants.EXECUTION_ACTIVE,LabConstants.RESULT_DOING,null,context(userId).getMemberId(),
                "CARRY_CREATE","由承诺 "+source.getId()+" 转期",0); cutover.recordPointOfNoReturn("CARRY_CREATE"); return carried;
    }

    private void terminal(LabTask task,Integer version,String status,String result,Date finish,String description,
            String failReason,String nextAction,String eventType,String reason,Long userId){
        closeOpenBlock(task,userId); update(task,version,status,result,finish,description,failReason,nextAction,userId);
        appendEvent(task,task.getExecutionStatus(),status,result,finish,context(userId).getMemberId(),eventType,reason,version+1);
        cutover.recordPointOfNoReturn(eventType);
    }
    private void update(LabTask task,Integer version,String status,String result,Date finish,String description,
            String failReason,String nextAction,Long userId){
        if(commitmentMapper.updateExecutionFact(task.getId(),version,task.getExecutionStatus(),status,result,finish,
                description,failReason,nextAction,actor(userId))!=1)throw new ServiceException("周承诺已变化，请刷新后重试");
    }
    private void closeOpenBlock(LabTask task,Long userId){LabTaskBlockEvent block=taskMapper.selectOpenBlockEvent(task.getId());if(block==null)return;
        Date now=Date.from(clock.instant());Long member=context(userId).getMemberId();if(taskMapper.closeBlockEvent(block.getId(),member,now,
                "承诺终态自动关闭阻塞",actor(userId))!=1)throw new ServiceException("阻塞状态已变化，请刷新后重试");}
    private LabTask ownedActive(Long id,Integer version,Long userId){LabTask task=loadVersioned(id,version);requireOwner(task,userId);
        if(!LabConstants.EXECUTION_ACTIVE.equals(task.getExecutionStatus()))throw new ServiceException("只有进行中周承诺可以闭环");return task;}
    private LabTask loadVersioned(Long id,Integer version){LabTask task=taskMapper.selectTaskForUpdate(id);if(task==null||version==null
            ||!version.equals(task.getExecutionVersion()))throw new ServiceException("周承诺已变化，请刷新后重试");
        if(!LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel()))throw new ServiceException("该操作仅适用于周承诺");
        if(LabConstants.YES.equals(task.getPeriodLockFlag()))throw new ServiceException("已关期周承诺不可修改");return task;}
    private void requireOwner(LabTask task,Long userId){if(!context(userId).getMemberId().equals(task.getOwnerId()))
        throw new ServiceException("成员只能维护自己的周承诺");}
    private void requireOwnerOrManager(LabTask task,Long userId){LabAccessContext actor=context(userId);
        if(actor.getMemberId().equals(task.getOwnerId()))return;accessService.requireManager(userId);}
    private LabAccessContext context(Long userId){LabAccessContext context=accessService.context(userId);if(context==null||context.getMemberId()==null)
        throw new ServiceException("当前账号未关联在职成员");return context;}
    private void appendEvent(LabTask task,String from,String to,String result,Date finish,Long actorId,String type,String reason,int version){
        LabTaskExecutionEvent event=new LabTaskExecutionEvent();event.setTaskId(task.getId());event.setFromStatus(from);event.setToStatus(to);
        event.setResultStatus(result);event.setActualFinishTime(finish);event.setActorId(actorId);event.setEventType(type);event.setReason(reason);
        event.setTaskVersion(version);event.setEvidenceVersion(0);event.setIdempotencyKey(task.getId()+":"+type+":"+version);
        event.setEventTime(Date.from(clock.instant()));event.setDelFlag(LabConstants.NO);event.setCreateBy(String.valueOf(actorId));
        if(commitmentMapper.insertExecutionEvent(event)!=1)throw new ServiceException("周承诺事件保存失败");}
    private void requireCreate(WeeklyCommitmentCommand command){if(command==null||command.getParentTaskId()==null||blank(command.getTitle())
            ||blank(command.getDeliverable())||blank(command.getPeriod())||command.getPlanDate()==null)
        throw new ServiceException("父月度结果、承诺、交付物、目标周和截止日期不能为空");}
    private String defaultNo(String value){return LabConstants.YES.equals(value)?LabConstants.YES:LabConstants.NO;}
    private boolean isAfterPlanDay(Date actual,Date planned){return actual.toInstant().atZone(clock.getZone()).toLocalDate()
            .isAfter(planned.toInstant().atZone(clock.getZone()).toLocalDate());}
    private String actor(Long userId){return String.valueOf(userId);}
    private boolean blank(String value){return value==null||value.trim().isEmpty();}
}
