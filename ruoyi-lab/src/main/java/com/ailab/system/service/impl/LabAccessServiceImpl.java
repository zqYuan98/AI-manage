package com.ailab.system.service.impl;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.LabReportAccessScope;
import com.ailab.system.mapper.LabAccessMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.config.LabProperties;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysMenuService;
import java.util.Collections;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LabAccessServiceImpl implements LabAccessService {
    public static final String MANAGER = "lab_manager";
    public static final String LEAD = "lab_lead";
    public static final String MEMBER = "lab_member";

    private final LabAccessMapper mapper;
    private final ISysMenuService menus;
    private final LabProperties properties;

    @Autowired
    public LabAccessServiceImpl(LabAccessMapper mapper, ISysMenuService menus, LabProperties properties) {
        this.mapper = mapper; this.menus = menus; this.properties = properties;
    }

    public LabAccessServiceImpl(LabAccessMapper mapper) {
        this(mapper, null, new LabProperties());
    }

    @Override
    public LabAccessContext context(Long userId) {
        if (userId == null) throw new ServiceException("Authenticated actor is required");
        LabAccessContext value = mapper.selectAccessContext(userId);
        if (value == null || value.getMemberId() == null || !knownRole(value.getRoleKey())) {
            throw new ServiceException("Authenticated user has no active lab role");
        }
        return value;
    }

    @Override
    public void scopeTaskQuery(LabTask query, Long userId) {
        if (query == null) throw new ServiceException("Task query is required");
        LabAccessContext actor = context(userId);
        if (LEAD.equals(actor.getRoleKey())) query.setBizLine(actor.getBizLine());
        if (MEMBER.equals(actor.getRoleKey())) query.setOwnerId(actor.getMemberId());
    }

    @Override public void requireTaskRead(LabTask task, Long userId) { requireTaskScope(task, context(userId)); }
    @Override public void requireTaskWrite(LabTask task, Long userId) { requireTaskScope(task, context(userId)); }

    @Override
    public void requireWeeklyWrite(LabTask task, Long userId) {
        LabAccessContext actor=context(userId);
        if (task == null || !"week".equals(task.getTaskLevel())) throw new ServiceException("周承诺不存在");
        if (MANAGER.equals(actor.getRoleKey())) return;
        if ((MEMBER.equals(actor.getRoleKey())||LEAD.equals(actor.getRoleKey()))
                && same(actor.getMemberId(),task.getOwnerId())) return;
        throw new ServiceException("只有周承诺负责人或管理者可以执行该操作");
    }

    @Override
    public void requireMonthlyDefinitionWrite(LabTask task, Long userId) {
        if (task == null || !"month".equals(task.getTaskLevel())) throw new ServiceException("月度结果不存在");
        requireManager(userId);
    }

    @Override
    public void requireTaskReview(LabTask task, Long userId) {
        LabAccessContext actor = context(userId);
        if (task == null) throw new ServiceException("Task does not exist");
        if (same(actor.getMemberId(), task.getOwnerId())) throw new ServiceException("Reviewers cannot review their own task");
        if (MANAGER.equals(actor.getRoleKey())) return;
        if (!LEAD.equals(actor.getRoleKey())) throw new ServiceException("Only managers and line leads may review task results");
        if (!same(actor.getBizLine(), task.getBizLine())) {
            throw new ServiceException("Line lead may review only tasks in the same business line");
        }
    }

    @Override
    public void requireEligibleReviewer(LabTask task) {
        if (task == null || task.getOwnerId() == null || task.getBizLine() == null
                || mapper.countEligibleReviewers(task.getOwnerId(), task.getBizLine()) < 1) {
            throw new ServiceException("月度结果激活前必须存在另一名当前可用的审核人");
        }
    }

    @Override public void requireGoalRead(Long userId) { context(userId); }

    @Override
    public void requireGoalWrite(LabGoal goal, Long userId) {
        LabAccessContext actor = context(userId);
        if (MANAGER.equals(actor.getRoleKey())) return;
        if (!LEAD.equals(actor.getRoleKey()) || goal == null || !"QUARTER".equals(goal.getGoalLevel())
                || !same(actor.getMemberId(), goal.getOwnerId())) {
            throw new ServiceException("Line lead may write only quarterly goals they own; members are read-only");
        }
    }

    @Override
    public void requireManager(Long userId) {
        if (!MANAGER.equals(context(userId).getRoleKey())) throw new ServiceException("Manager role is required");
    }

    @Override
    public LabReportAccessScope reportScope(Long userId) {
        LabAccessContext actor=context(userId);
        boolean manager=MANAGER.equals(actor.getRoleKey());
        Set<String> permissions=menus==null?Collections.<String>emptySet():menus.selectMenuPermsByUserId(userId);
        boolean sensitive=manager&&permissions!=null&&permissions.contains("lab:report:sensitive");
        return new LabReportAccessScope(manager,actor.getBizLine(),sensitive,
                properties.isShareAllFinalizedNonSensitive());
    }

    @Override
    public void requireReportRead(String reportBizLine, boolean sensitive, boolean immutable, Long userId) {
        LabReportAccessScope scope=reportScope(userId);
        if (sensitive && !scope.isSensitiveGranted()) throw new ServiceException("需要敏感报告权限");
        if (scope.isManager()) return;
        if (!immutable) throw new ServiceException("非管理者只能查看已固化报告");
        if (sensitive) throw new ServiceException("非管理者不能查看敏感报告");
        if (same(scope.getBizLine(),reportBizLine)) return;
        if ("ALL".equals(reportBizLine)&&scope.isShareAllFinalizedNonSensitive()) return;
        throw new ServiceException("报告不在当前业务范围内");
    }

    private void requireTaskScope(LabTask task, LabAccessContext actor) {
        if (task == null) throw new ServiceException("Task does not exist");
        if (MANAGER.equals(actor.getRoleKey())) return;
        if (LEAD.equals(actor.getRoleKey()) && same(actor.getBizLine(), task.getBizLine())) return;
        if (MEMBER.equals(actor.getRoleKey()) && same(actor.getMemberId(), task.getOwnerId())) return;
        throw new ServiceException("Task is outside the authenticated actor's lab scope");
    }

    private boolean knownRole(String roleKey) { return MANAGER.equals(roleKey) || LEAD.equals(roleKey) || MEMBER.equals(roleKey); }
    private boolean same(Object left, Object right) { return left == null ? right == null : left.equals(right); }
}
