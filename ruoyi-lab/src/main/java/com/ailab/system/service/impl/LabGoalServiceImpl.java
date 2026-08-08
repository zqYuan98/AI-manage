package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.LabGoalService;
import com.ailab.system.service.LabAccessService;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
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
    private final LabAccessService accessService;

    public LabGoalServiceImpl(LabGoalMapper goalMapper, LabTaskMapper taskMapper, LabAccessService accessService) {
        this.goalMapper = goalMapper;
        this.taskMapper = taskMapper;
        this.accessService = accessService;
    }

    @Override
    public List<LabGoal> listGoals(LabGoal query, Long actorId) {
        accessService.requireGoalRead(actorId);
        return goalMapper.selectGoalList(safeGoalQuery(query));
    }

    @Override
    public List<LabGoal> goalTree(LabGoal query, Long actorId) {
        accessService.requireGoalRead(actorId);
        List<LabGoal> flat = goalMapper.selectGoalList(safeGoalQuery(query));
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
    public LabGoal getGoal(Long id, Long actorId) {
        accessService.requireGoalRead(actorId);
        return loadGoal(id);
    }

    private LabGoal loadGoal(Long id) {
        LabGoal goal = goalMapper.selectGoalById(id);
        if (goal == null) throw new ServiceException("Goal does not exist");
        return goal;
    }

    @Override
    @Transactional
    public int createGoal(LabGoal goal, Long actorId) {
        LabGoal lockedParent = lockNewGoalParent(goal);
        lockGoalOwner(goal == null ? null : goal.getOwnerId());
        validateGoal(goal, lockedParent);
        if (lockedParent != null && !"DRAFT".equals(lockedParent.getStatus())) {
            throw new ServiceException("Quarter membership is frozen after annual goal activation");
        }
        accessService.requireGoalWrite(goal, actorId);
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
        LabGoal snapshot = loadGoal(goal.getId());
        accessService.requireGoalWrite(snapshot, actorId);
        prevalidateGoalUpdate(snapshot, goal);
        Map<Long, LabGoal> lockedParents = lockGoalParents(snapshot.getParentId(), goal.getParentId());
        LabGoal stored = goalMapper.selectGoalForUpdate(goal.getId());
        if (stored == null || !stored.getVersion().equals(goal.getVersion())) throw optimisticConflict();
        accessService.requireGoalWrite(stored, actorId);
        accessService.requireGoalWrite(goal, actorId);
        lockGoalOwner(goal.getOwnerId());
        requireStableStructure(stored, goal, lockStructureDependents(stored, goal), lockedParents);
        requireStableWeight(stored, goal, lockedParents);
        validateGoal(goal, lockedParents.get(goal.getParentId()));
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
        LabGoal snapshot = loadGoal(id);
        Map<Long, LabGoal> lockedParents = lockGoalParents(snapshot.getParentId());
        LabGoal stored = goalMapper.selectGoalForUpdate(id);
        if (stored == null || !same(stored.getVersion(), version)) throw optimisticConflict();
        accessService.requireGoalWrite(stored, actorId);
        if (!"DRAFT".equals(stored.getStatus())) throw new ServiceException("Only draft goals can be deleted");
        LabGoal parent = lockedParents.get(stored.getParentId());
        if (parent != null && !"DRAFT".equals(parent.getStatus())) {
            throw new ServiceException("Quarter membership is frozen after annual goal activation");
        }
        if (!goalMapper.selectChildrenByParentIdForUpdate(id).isEmpty()) {
            throw new ServiceException("Delete child milestones before deleting the goal");
        }
        if (!taskMapper.selectTasksByGoalOrMilestoneForUpdate(id).isEmpty()) {
            throw new ServiceException("Delete connected tasks before deleting the goal");
        }
        if (goalMapper.deleteGoal(id, version, actor(actorId)) != 1) throw optimisticConflict();
        return 1;
    }

    @Override
    @Transactional
    public void activateGoal(Long id, Integer version, Long actorId) {
        LabGoal snapshot = loadGoal(id);
        lockGoalParents(snapshot.getParentId());
        LabGoal goal = goalMapper.selectGoalForUpdate(id);
        if (goal == null) throw new ServiceException("Goal does not exist");
        accessService.requireManager(actorId);
        if (!"DRAFT".equals(goal.getStatus())) throw new ServiceException("Only draft goals can be activated");
        if (!goal.getVersion().equals(version)) throw optimisticConflict();
        if ("YEAR".equals(goal.getGoalLevel())) {
            requireWeightTotal(goalMapper.selectChildrenByParentIdForUpdate(id), "Quarter milestone weights must total 100 before annual goal activation");
        } else if ("QUARTER".equals(goal.getGoalLevel())) {
            requireTaskWeightTotal(taskMapper.selectKeyMonthTasksByMilestoneIdForUpdate(id), "Monthly key-task goal weights must total 100 before milestone activation");
        } else {
            throw new ServiceException("Unsupported goal level");
        }
        goal.setStatus("ACTIVE");
        goal.setUpdateBy(actor(actorId));
        if (goalMapper.updateGoal(goal) != 1) throw optimisticConflict();
    }

    @Override
    public BigDecimal calculateMilestoneProgress(Long milestoneId, Long actorId) {
        accessService.requireGoalRead(actorId);
        LabGoal milestone = loadGoal(milestoneId);
        if (!"QUARTER".equals(milestone.getGoalLevel())) {
            throw new ServiceException("Milestone progress requires a QUARTER goal");
        }
        return calculateMilestoneProgressInternal(milestoneId);
    }

    private BigDecimal calculateMilestoneProgressInternal(Long milestoneId) {
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
    public BigDecimal calculateAnnualProgress(Long annualGoalId, Long actorId) {
        accessService.requireGoalRead(actorId);
        LabGoal annual = loadGoal(annualGoalId);
        if (!"YEAR".equals(annual.getGoalLevel())) throw new ServiceException("Annual progress requires a YEAR goal");
        BigDecimal total = BigDecimal.ZERO;
        for (LabGoal milestone : goalMapper.selectChildrenByParentId(annualGoalId)) {
            total = total.add(zero(milestone.getWeight()).multiply(calculateMilestoneProgressInternal(milestone.getId()))
                    .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
        }
        return percent(total);
    }

    private BigDecimal calculateMonthProgress(Long monthId) {
        List<LabTask> weeks = taskMapper.selectTasksByParentId(monthId);
        if (weeks.isEmpty()) return BigDecimal.ZERO.setScale(2);
        int confirmed = 0, completed = 0;
        for (LabTask week : weeks) {
            if (LabConstants.WORKFLOW_CONFIRMED.equals(week.getWorkflowStatus())) {
                confirmed++;
                if (completionCoefficient(week.getResultStatus()).compareTo(BigDecimal.ONE) == 0) completed++;
            }
        }
        if (confirmed == 0) return BigDecimal.ZERO.setScale(2);
        return new BigDecimal(completed).multiply(ONE_HUNDRED)
                .divide(new BigDecimal(confirmed), 2, RoundingMode.HALF_UP);
    }

    private void validateGoal(LabGoal goal, LabGoal lockedParent) {
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
        LabGoal parent = lockedParent;
        if (parent == null || !"YEAR".equals(parent.getGoalLevel())) throw new ServiceException("Quarter milestone parent must be an annual goal");
        if (!parent.getYear().equals(goal.getYear())) throw new ServiceException("Quarter milestone must use the parent annual goal year");
        if (!isQuarter(goal.getPeriod(), goal.getYear())) throw new ServiceException("Quarter milestone period must use YYYYQ1 through YYYYQ4");
    }

    private void prevalidateGoalUpdate(LabGoal stored, LabGoal proposed) {
        if (!same(stored.getGoalLevel(), proposed.getGoalLevel())) {
            throw new ServiceException("Goal level is immutable after creation");
        }
        LabGoal parent = proposed.getParentId() == null || proposed.getParentId() == 0L
                ? null : goalMapper.selectGoalById(proposed.getParentId());
        validateGoal(proposed, parent);
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

    private void requireStableWeight(LabGoal stored, LabGoal proposed, Map<Long, LabGoal> lockedParents) {
        if (same(stored.getWeight(), proposed.getWeight())) return;
        LabGoal parent = lockedParents.get(stored.getParentId());
        if (frozen(stored) || frozen(parent)) {
            throw new ServiceException("Goal weight is immutable after the goal or annual parent is activated");
        }
    }

    private void requireStableStructure(LabGoal stored, LabGoal proposed, boolean hasDependents,
            Map<Long, LabGoal> lockedParents) {
        if (!structureChanged(stored, proposed)) return;
        if (hasDependents) throw new ServiceException("Goal structure is immutable after dependent records exist");
        if (frozen(stored) || frozen(lockedParents.get(stored.getParentId()))
                || frozen(lockedParents.get(proposed.getParentId()))) {
            throw new ServiceException("Activated goal hierarchy, period, number and owner are immutable");
        }
    }

    private boolean lockStructureDependents(LabGoal stored, LabGoal proposed) {
        if (!structureChanged(stored, proposed)) return false;
        if ("YEAR".equals(stored.getGoalLevel())) {
            return !goalMapper.selectChildrenByParentIdForUpdate(stored.getId()).isEmpty();
        }
        if ("QUARTER".equals(stored.getGoalLevel())) {
            return !taskMapper.selectTasksByMilestoneIdForUpdate(stored.getId()).isEmpty();
        }
        return false;
    }

    private boolean structureChanged(LabGoal stored, LabGoal proposed) {
        return !same(stored.getParentId(), proposed.getParentId()) || !same(stored.getGoalLevel(), proposed.getGoalLevel())
                || !same(stored.getYear(), proposed.getYear()) || !same(stored.getPeriod(), proposed.getPeriod())
                || !same(stored.getGoalNo(), proposed.getGoalNo()) || !same(stored.getOwnerId(), proposed.getOwnerId());
    }

    private boolean frozen(LabGoal goal) { return goal != null && !"DRAFT".equals(goal.getStatus()); }

    private LabGoal lockNewGoalParent(LabGoal goal) {
        if (goal == null || !"QUARTER".equals(goal.getGoalLevel()) || goal.getParentId() == null || goal.getParentId() == 0L) {
            return null;
        }
        return goalMapper.selectGoalForUpdate(goal.getParentId());
    }

    private Map<Long, LabGoal> lockGoalParents(Long... parentIds) {
        List<Long> ids = new ArrayList<Long>();
        if (parentIds != null) {
            for (Long parentId : parentIds) {
                if (parentId != null && parentId != 0L && !ids.contains(parentId)) ids.add(parentId);
            }
        }
        Collections.sort(ids);
        Map<Long, LabGoal> locked = new LinkedHashMap<Long, LabGoal>();
        for (Long parentId : ids) locked.put(parentId, goalMapper.selectGoalForUpdate(parentId));
        return locked;
    }

    private void lockGoalOwner(Long ownerId) {
        if (ownerId == null || taskMapper.lockMemberForUpdate(ownerId) == null) {
            throw new ServiceException("Goal owner is not an active lab member");
        }
    }

    private BigDecimal completionCoefficient(String result) {
        return LabConstants.RESULT_EXCEEDED.equals(result) || LabConstants.RESULT_ONTIME.equals(result)
                || LabConstants.RESULT_DELAYED.equals(result) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private boolean isQuarter(String period, Integer year) {
        return period != null && period.matches("\\d{4}Q[1-4]") && period.startsWith(String.valueOf(year));
    }

    private boolean same(Object left, Object right) { return left == null ? right == null : left.equals(right); }

    private LabGoal safeGoalQuery(LabGoal query) {
        LabGoal safe = query == null ? new LabGoal() : query;
        safe.getParams().remove("dataScope");
        if (Boolean.TRUE.equals(safe.getGoalIdsFilter())) {
            for (Long goalId : safe.getGoalIds()) {
                if (goalId == null || goalId.longValue() <= 0L) {
                    throw new ServiceException("Goal id filters must be positive integers");
                }
            }
        }
        return safe;
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
