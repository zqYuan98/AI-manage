package com.ailab.system.controller;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.dto.GoalTerminationRequest;
import com.ailab.system.service.LabGoalService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import java.util.List;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/goal")
public class LabGoalController extends BaseController {
    private final LabGoalService goalService;

    public LabGoalController(LabGoalService goalService) { this.goalService = goalService; }

    @PreAuthorize("@ss.hasPermi('lab:goal:list')")
    @GetMapping("/list")
    public TableDataInfo list(LabGoal query) {
        startPage();
        List<LabGoal> rows = goalService.listGoals(query, SecurityUtils.getUserId());
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:list')")
    @GetMapping("/tree")
    public AjaxResult tree(LabGoal query) { return success(goalService.goalTree(query, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:goal:list')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) { return success(goalService.getGoal(id, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:goal:list')")
    @GetMapping("/{id}/progress")
    public AjaxResult progress(@PathVariable Long id, @RequestParam String level) {
        Long actorId = SecurityUtils.getUserId();
        if ("YEAR".equals(level)) return success(goalService.calculateAnnualProgress(id, actorId));
        if ("QUARTER".equals(level)) return success(goalService.calculateMilestoneProgress(id, actorId));
        throw new com.ruoyi.common.exception.ServiceException("Goal progress level must be YEAR or QUARTER");
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:list')")
    @GetMapping("/{id}/progress-comparison")
    public AjaxResult progressComparison(@PathVariable Long id, @RequestParam String level,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date asOf) {
        Long actorId = SecurityUtils.getUserId();
        if ("YEAR".equals(level)) return success(goalService.compareAnnualProgress(id, asOf, actorId));
        if ("QUARTER".equals(level)) return success(goalService.compareMilestoneProgress(id, asOf, actorId));
        throw new com.ruoyi.common.exception.ServiceException("Goal progress level must be YEAR or QUARTER");
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:add')")
    @Log(title = "AI lab goal", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody LabGoal goal) {
        return toAjax(goalService.createGoal(goal, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:edit')")
    @Log(title = "AI lab goal", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody LabGoal goal) {
        return toAjax(goalService.updateGoal(goal, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:activate')")
    @Log(title = "AI lab goal activation", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/activate")
    public AjaxResult activate(@PathVariable Long id, @RequestParam Integer version) {
        goalService.activateGoal(id, version, SecurityUtils.getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:terminate')")
    @Log(title = "AI lab goal termination", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/terminate")
    public AjaxResult terminate(@PathVariable Long id, @RequestBody GoalTerminationRequest request) {
        if (request == null) throw new com.ruoyi.common.exception.ServiceException("终止请求不能为空");
        goalService.terminateGoal(id, request.getVersion(), request.getReason(), SecurityUtils.getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:goal:remove')")
    @Log(title = "AI lab goal", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id, @RequestParam Integer version) {
        return toAjax(goalService.deleteGoal(id, version, SecurityUtils.getUserId()));
    }
}
