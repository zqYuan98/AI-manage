package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.LabGoalService;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabGoalServiceImpl implements LabGoalService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final LabGoalMapper goalMapper;
    private final LabTaskMapper taskMapper;

    public LabGoalServiceImpl(LabGoalMapper goalMapper, LabTaskMapper taskMapper) {
        this.goalMapper = goalMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    @DataScope(deptAlias = "u", userAlias = "u", permission = "lab:goal:list")
    public List<LabGoal> listGoals(LabGoal query) {
        return goalMapper.selectGoalList(query == null ? new LabGoal() : query);
    }

    @Override
    @DataScope(deptAlias = "u", userAlias = "u", permission = "lab:goal:list")
    public List<LabGoal> goalTree(LabGoal query) {
        List<LabGoal> flat = goalMapper.selectGoalList(query == null ? new LabGoal() : query);
        Map<Long, LabGoal> index = new LinkedHashMap<Long, LabGoal>();
        for (LabGoal goal : flat) {
            goal.setChildren(new ArrayList<LabGoal>());
            index.put(goal.getId(), goal);
        }
        List<LabGoal> roots = new ArrayList<LabGoal>();
        for (LabGoal goal : flat) {
            LabGoal parent = index.get(goal.getParentId());
            if (parent == null) {
                roots.add(goal);
            } else {
                parent.getChildren().add(goal);
            }
        }
        return roots;
    }

    @Override
    public LabGoal getGoal(Long id) {
        LabGoal goal = goalMapper.selectGoalById(id);
        if (goal == null) throw new ServiceException("Goal does not exist");
        return goal;
    }

    @Override
    @Transactional
    public int createGoal(LabGoal goal, Long actorId) {
        validateGoal(goal);
        goal.setId(null);
        goal.setStatus("DRAFT");
        goal.setProgressRate(BigDecimal.ZERO);
        goal.setVersion(0);
        goal.setDelFlag(LabConstants.NO);
        goal.setCreateBy(actor(actorId));
        return goalMapper.insertGoal(goal);
    }

    @Override
    @Transactional
    public int updateGoal(LabGoal goal, Long actorId) {
        if (goal == null || goal.getId() == null || goal.getVersion() == null) {
            throw new ServiceException("Goal id and version are required");
        }
        LabGoal stored = getGoal(goal.getId());
        validateGoal(goal);
        goal.setStatus(stored.getStatus());
        goal.setProgressRate(stored.getProgressRate());
        goal.setDelFlag(stored.getDelFlag());
        goal.setUpdateBy(actor(actorId));
        if (goalMapper.updateGoal(goal) != 1) throw optimisticConflict();
        return 1;
    }

    @Override
    @Transactional
    public int deleteGoal(Long id, Integer version, Long actorId) {
        if (!goalMapper.selectChildrenByParentId(id).isEmpty()) {
            throw new ServiceException("Delete child milestones before deleting the goal");
        }
        if (goalMapper.deleteGoal(id, version, actor(actorId)) != 1) throw optimisticConflict();
        return 1;
    }

    @Override
    @Transactional
    public void activateGoal(Long id, Integer version, Long actorId) {
        LabGoal goal = getGoal(id);
        if (!goal.getVersion().equals(version)) throw optimisticConflict();
        if ("YEAR".equals(goal.getGoalLevel())) {
            requireWeightTotal(goalMapper.selectChildrenByParentId(id), "Quarter milestone weights must total 100 before annual goal activation");
        } else if ("QUARTER".equals(goal.getGoalLevel())) {
            requireTaskWeightTotal(taskMapper.selectKeyMonthTasksByMilestoneId(id), "Monthly key-task goal weights must total 100 before milestone activation");
        } else {
            throw new ServiceException("Unsupported goal level");
        }
        goal.setStatus("ACTIVE");
        goal.setUpdateBy(actor(actorId));
        if (goalMapper.updateGoal(goal) != 1) throw optimisticConflict();
    }

    @Override
    public BigDecimal calculateMilestoneProgress(Long milestoneId) {
        getGoal(milestoneId);
        BigDecimal total = BigDecimal.ZERO;
        for (LabTask task : taskMapper.selectKeyMonthTasksByMilestoneId(milestoneId)) {
            BigDecimal weight = zero(task.getGoalWeight());
            BigDecimal coefficient;
            if (LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())) {
                coefficient = completionCoefficient(task.getResultStatus());
            } else {
                coefficient = calculateMonthProgress(task.getId()).divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
            }
            total = total.add(weight.multiply(coefficient));
        }
        return percent(total);
    }

    @Override
    public BigDecimal calculateAnnualProgress(Long annualGoalId) {
        LabGoal annual = getGoal(annualGoalId);
        if (!"YEAR".equals(annual.getGoalLevel())) throw new ServiceException("Annual progress requires a YEAR goal");
        BigDecimal total = BigDecimal.ZERO;
        for (LabGoal milestone : goalMapper.selectChildrenByParentId(annualGoalId)) {
            total = total.add(zero(milestone.getWeight()).multiply(calculateMilestoneProgress(milestone.getId()))
                    .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
        }
        return percent(total);
    }

    private BigDecimal calculateMonthProgress(Long monthId) {
        List<LabTask> weeks = taskMapper.selectTasksByParentId(monthId);
        if (weeks.isEmpty()) return BigDecimal.ZERO.setScale(2);
        int completed = 0;
        for (LabTask week : weeks) {
            if (LabConstants.WORKFLOW_CONFIRMED.equals(week.getWorkflowStatus())
                    && completionCoefficient(week.getResultStatus()).compareTo(BigDecimal.ONE) == 0) completed++;
        }
        return new BigDecimal(completed).multiply(ONE_HUNDRED)
                .divide(new BigDecimal(weeks.size()), 2, RoundingMode.HALF_UP);
    }

    private void validateGoal(LabGoal goal) {
        if (goal == null || blank(goal.getGoalLevel()) || goal.getYear() == null || blank(goal.getGoalNo())
                || blank(goal.getTitle()) || goal.getOwnerId() == null) {
            throw new ServiceException("Goal level, year, number, title and owner are required");
        }
        if (goal.getWeight() != null && (goal.getWeight().signum() < 0 || goal.getWeight().compareTo(ONE_HUNDRED) > 0)) {
            throw new ServiceException("Goal weight must be between 0 and 100");
        }
        if ("YEAR".equals(goal.getGoalLevel())) {
            if (goal.getParentId() != null && goal.getParentId() != 0L) throw new ServiceException("Annual goal cannot have a parent");
            if (!blank(goal.getPeriod())) throw new ServiceException("Annual goal must not have a quarter period");
            return;
        }
        if (!"QUARTER".equals(goal.getGoalLevel()) || goal.getParentId() == null || goal.getParentId() == 0L) {
            throw new ServiceException("Quarter milestone must have an annual parent");
        }
        LabGoal parent = goalMapper.selectGoalById(goal.getParentId());
        if (parent == null || !"YEAR".equals(parent.getGoalLevel())) throw new ServiceException("Quarter milestone parent must be an annual goal");
        if (!parent.getYear().equals(goal.getYear())) throw new ServiceException("Quarter milestone must use the parent annual goal year");
        if (!isQuarter(goal.getPeriod(), goal.getYear())) throw new ServiceException("Quarter milestone period must use YYYYQ1 through YYYYQ4");
    }

    private void requireWeightTotal(List<LabGoal> children, String message) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LabGoal child : children) sum = sum.add(zero(child.getWeight()));
        if (children.isEmpty() || sum.compareTo(ONE_HUNDRED) != 0) throw new ServiceException(message);
    }

    private void requireTaskWeightTotal(List<LabTask> tasks, String message) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LabTask task : tasks) sum = sum.add(zero(task.getGoalWeight()));
        if (tasks.isEmpty() || sum.compareTo(ONE_HUNDRED) != 0) throw new ServiceException(message);
    }

    private BigDecimal completionCoefficient(String result) {
        return LabConstants.RESULT_EXCEEDED.equals(result) || LabConstants.RESULT_ONTIME.equals(result)
                || LabConstants.RESULT_DELAYED.equals(result) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private boolean isQuarter(String period, Integer year) {
        return period != null && period.matches("\\d{4}Q[1-4]") && period.startsWith(String.valueOf(year));
    }

    private String actor(Long actorId) {
        if (actorId == null) throw new ServiceException("Authenticated actor is required");
        return String.valueOf(actorId);
    }

    private ServiceException optimisticConflict() { return new ServiceException("Record was changed by another user; refresh and retry"); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal percent(BigDecimal value) { return value.max(BigDecimal.ZERO).min(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
