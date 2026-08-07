package com.ailab.system.controller;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.LabTaskService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
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
@RequestMapping("/lab/task")
public class LabTaskController extends BaseController {
    private final LabTaskService taskService;
    public LabTaskController(LabTaskService taskService) { this.taskService = taskService; }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(LabTask query) { startPage(); return getDataTable(taskService.listTasks(query)); }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) { requireVisible(id); return success(taskService.getTask(id)); }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{id}/progress")
    public AjaxResult progress(@PathVariable Long id) { requireVisible(id); return success(taskService.calculateMonthProgress(id)); }

    @PreAuthorize("@ss.hasPermi('lab:task:add')")
    @Log(title = "AI lab task", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody LabTask task) { return toAjax(taskService.createTask(task, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody LabTask task) { requireVisible(task.getId()); return toAjax(taskService.updateTask(task, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:remove')")
    @Log(title = "AI lab task", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id, @RequestParam Integer version) { requireVisible(id); return toAjax(taskService.deleteTask(id, version, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab monthly plan activation", businessType = BusinessType.UPDATE)
    @PutMapping("/plan/activate")
    public AjaxResult activatePlan(@RequestParam Long ownerId, @RequestParam String period) {
        LabTask query = new LabTask(); query.setOwnerId(ownerId); query.setPeriod(period);
        if (taskService.listTasks(query).isEmpty()) throw new ServiceException("Plan is outside the current data scope");
        return toAjax(taskService.activateMonthlyPlan(ownerId, period, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab weekly task activation", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/activate")
    public AjaxResult activate(@PathVariable Long id, @RequestParam Integer version) {
        requireVisible(id);
        taskService.activateTask(id, version, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task result submission", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/submit")
    public AjaxResult submit(@PathVariable Long id, @RequestParam Integer version, @RequestBody TaskSubmitCommand command) {
        requireVisible(id);
        taskService.submitResult(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task result withdrawal", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/withdraw")
    public AjaxResult withdraw(@PathVariable Long id, @RequestParam Integer version) {
        requireVisible(id);
        taskService.withdrawResult(id, version, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task result approval", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/review-pass")
    public AjaxResult reviewPass(@PathVariable Long id, @RequestParam Integer version, @RequestBody TaskSubmitCommand command) {
        requireVisible(id);
        taskService.reviewPass(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task result return", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/review-return")
    public AjaxResult reviewReturn(@PathVariable Long id, @RequestParam Integer version, @RequestBody TaskSubmitCommand command) {
        requireVisible(id);
        taskService.reviewReturn(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task reopen", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/reopen")
    public AjaxResult reopen(@PathVariable Long id, @RequestParam Integer version, @RequestParam String reason) {
        requireVisible(id);
        taskService.reopenTask(id, version, reason, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:evidence')")
    @GetMapping("/{taskId}/evidence")
    public AjaxResult evidence(@PathVariable Long taskId) { requireVisible(taskId); return success(taskService.listEvidence(taskId)); }

    @PreAuthorize("@ss.hasPermi('lab:task:evidence')")
    @Log(title = "AI lab task evidence", businessType = BusinessType.INSERT)
    @PostMapping("/{taskId}/evidence")
    public AjaxResult addEvidence(@PathVariable Long taskId, @RequestBody LabTaskEvidence evidence) {
        requireVisible(taskId);
        return success(taskService.addEvidence(taskId, evidence, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:evidence')")
    @Log(title = "AI lab task evidence", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskId}/evidence/{evidenceId}")
    public AjaxResult deleteEvidence(@PathVariable Long taskId, @PathVariable Long evidenceId) {
        requireVisible(taskId);
        return toAjax(taskService.deleteEvidence(taskId, evidenceId, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{taskId}/quality-gate")
    public AjaxResult qualityGates(@PathVariable Long taskId) { requireVisible(taskId); return success(taskService.listQualityGates(taskId)); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task quality gate", businessType = BusinessType.INSERT)
    @PostMapping("/quality-gate")
    public AjaxResult addQualityGate(@RequestBody LabTaskQualityGate gate) { requireVisible(gate.getTaskId()); return success(taskService.addQualityGate(gate, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task quality gate", businessType = BusinessType.UPDATE)
    @PutMapping("/quality-gate")
    public AjaxResult updateQualityGate(@RequestBody LabTaskQualityGate gate) { requireGateVisible(gate.getId()); return toAjax(taskService.updateQualityGate(gate, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task quality gate", businessType = BusinessType.DELETE)
    @DeleteMapping("/quality-gate/{id}")
    public AjaxResult deleteQualityGate(@PathVariable Long id) { requireGateVisible(id); return toAjax(taskService.deleteQualityGate(id, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task quality gate approval", businessType = BusinessType.UPDATE)
    @PutMapping("/quality-gate/{id}/pass")
    public AjaxResult passQualityGate(@PathVariable Long id, @RequestParam Long evidenceId, @RequestParam String result) {
        requireGateVisible(id);
        taskService.passQualityGate(id, evidenceId, result, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{taskId}/block")
    public AjaxResult blockHistory(@PathVariable Long taskId) { requireVisible(taskId); return success(taskService.listBlockEvents(taskId)); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task block", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/block")
    public AjaxResult block(@PathVariable Long taskId, @RequestParam Integer version, @RequestParam String type, @RequestParam String reason) {
        requireVisible(taskId);
        return success(taskService.blockTask(taskId, version, type, reason, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task unblock", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/unblock")
    public AjaxResult unblock(@PathVariable Long taskId, @RequestParam Integer version, @RequestParam String resolution) {
        requireVisible(taskId);
        taskService.unblockTask(taskId, version, resolution, SecurityUtils.getUserId()); return success();
    }

    private void requireVisible(Long id) {
        LabTask query = new LabTask(); query.setId(id);
        if (taskService.listTasks(query).isEmpty()) throw new ServiceException("Task is outside the current data scope");
    }

    private void requireGateVisible(Long id) { requireVisible(taskService.getQualityGate(id).getTaskId()); }
}
