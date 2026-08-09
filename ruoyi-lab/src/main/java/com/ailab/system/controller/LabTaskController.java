package com.ailab.system.controller;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.dto.MonthlyCarryCommand;
import com.ailab.system.dto.WeeklyCommitmentCommand;
import com.ailab.system.service.LabCommitmentService;
import com.ailab.system.service.LabTaskService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final LabCommitmentService commitmentService;
    public LabTaskController(LabTaskService taskService) { this(taskService, null); }
    @Autowired
    public LabTaskController(LabTaskService taskService, LabCommitmentService commitmentService) {
        this.taskService = taskService; this.commitmentService = commitmentService;
    }

    @PreAuthorize("@ss.hasPermi('lab:task:add')")
    @Log(title = "新增本周承诺", businessType = BusinessType.INSERT)
    @PostMapping("/commitment")
    public AjaxResult createCommitment(@RequestBody WeeklyCommitmentCommand command) {
        return success(requireCommitmentService().create(command, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "完成本周承诺", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commitment/complete")
    public AjaxResult completeCommitment(@PathVariable Long id, @RequestParam Integer version,
            @RequestBody WeeklyCommitmentCommand command) {
        requireCommitmentService().complete(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "标记本周未完成", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commitment/undone")
    public AjaxResult undoneCommitment(@PathVariable Long id, @RequestParam Integer version,
            @RequestBody WeeklyCommitmentCommand command) {
        requireCommitmentService().markUndone(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "纠正本周承诺", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commitment/correct")
    public AjaxResult correctCommitment(@PathVariable Long id, @RequestParam Integer version,
            @RequestBody WeeklyCommitmentCommand command) {
        requireCommitmentService().correct(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "取消本周承诺", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commitment/cancel")
    public AjaxResult cancelCommitment(@PathVariable Long id, @RequestParam Integer version,
            @RequestBody WeeklyCommitmentCommand command) {
        requireCommitmentService().cancel(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "转期本周承诺", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/commitment/carry")
    public AjaxResult carryCommitment(@PathVariable Long id, @RequestParam Integer version,
            @RequestBody WeeklyCommitmentCommand command) {
        return success(requireCommitmentService().carry(id, version, command, SecurityUtils.getUserId()));
    }

    private LabCommitmentService requireCommitmentService() {
        if (commitmentService == null) throw new IllegalStateException("周承诺服务未配置"); return commitmentService;
    }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(LabTask query) { startPage(); return getDataTable(taskService.listTasks(query, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) { return success(taskService.getTask(id, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{id}/progress")
    public AjaxResult progress(@PathVariable Long id) { return success(taskService.calculateMonthProgress(id, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:add')")
    @Log(title = "AI lab task", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody LabTask task) { return toAjax(taskService.createTask(task, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody LabTask task) { return toAjax(taskService.updateTask(task, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:remove')")
    @Log(title = "AI lab task", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id, @RequestParam Integer version) { return toAjax(taskService.deleteTask(id, version, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab monthly plan activation", businessType = BusinessType.UPDATE)
    @PutMapping("/plan/activate")
    public AjaxResult activatePlan(@RequestParam Long ownerId, @RequestParam String period) {
        return toAjax(taskService.activateMonthlyPlan(ownerId, period, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab weekly task activation", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/activate")
    public AjaxResult activate(@PathVariable Long id, @RequestParam Integer version) {
        taskService.activateTask(id, version, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task result submission", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/submit")
    public AjaxResult submit(@PathVariable Long id, @RequestParam Integer version, @RequestBody TaskSubmitCommand command) {
        taskService.submitResult(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task result withdrawal", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/withdraw")
    public AjaxResult withdraw(@PathVariable Long id, @RequestParam Integer version) {
        taskService.withdrawResult(id, version, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task result approval", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/review-pass")
    public AjaxResult reviewPass(@PathVariable Long id, @RequestParam Integer version, @RequestBody TaskSubmitCommand command) {
        taskService.reviewPass(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task result return", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/review-return")
    public AjaxResult reviewReturn(@PathVariable Long id, @RequestParam Integer version, @RequestBody TaskSubmitCommand command) {
        taskService.reviewReturn(id, version, command, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task reopen", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/result/reopen")
    public AjaxResult reopen(@PathVariable Long id, @RequestParam Integer version, @RequestParam String reason) {
        taskService.reopenTask(id, version, reason, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:add')")
    @Log(title = "月度结果转入下月", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/result/carry")
    public AjaxResult carryMonthly(@PathVariable Long id, @RequestParam Integer version,
            @RequestBody MonthlyCarryCommand command) {
        return success(taskService.carryMonthlyResult(id, version, command, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:evidence')")
    @GetMapping("/{taskId}/evidence")
    public AjaxResult evidence(@PathVariable Long taskId) { return success(taskService.listEvidence(taskId, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:evidence')")
    @Log(title = "AI lab task evidence", businessType = BusinessType.INSERT)
    @PostMapping("/{taskId}/evidence")
    public AjaxResult addEvidence(@PathVariable Long taskId, @RequestBody LabTaskEvidence evidence) {
        return success(taskService.addEvidence(taskId, evidence, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:evidence')")
    @Log(title = "AI lab task evidence", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskId}/evidence/{evidenceId}")
    public AjaxResult deleteEvidence(@PathVariable Long taskId, @PathVariable Long evidenceId) {
        return toAjax(taskService.deleteEvidence(taskId, evidenceId, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{taskId}/quality-gate")
    public AjaxResult qualityGates(@PathVariable Long taskId) { return success(taskService.listQualityGates(taskId, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task quality gate", businessType = BusinessType.INSERT)
    @PostMapping("/quality-gate")
    public AjaxResult addQualityGate(@RequestBody LabTaskQualityGate gate) { return success(taskService.addQualityGate(gate, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task quality gate", businessType = BusinessType.UPDATE)
    @PutMapping("/quality-gate")
    public AjaxResult updateQualityGate(@RequestBody LabTaskQualityGate gate) { return toAjax(taskService.updateQualityGate(gate, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task quality gate", businessType = BusinessType.DELETE)
    @DeleteMapping("/quality-gate/{id}")
    public AjaxResult deleteQualityGate(@PathVariable Long id) { return toAjax(taskService.deleteQualityGate(id, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:review')")
    @Log(title = "AI lab task quality gate approval", businessType = BusinessType.UPDATE)
    @PutMapping("/quality-gate/{id}/pass")
    public AjaxResult passQualityGate(@PathVariable Long id, @RequestParam Long evidenceId, @RequestParam String result) {
        taskService.passQualityGate(id, evidenceId, result, SecurityUtils.getUserId()); return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:task:list')")
    @GetMapping("/{taskId}/block")
    public AjaxResult blockHistory(@PathVariable Long taskId) { return success(taskService.listBlockEvents(taskId, SecurityUtils.getUserId())); }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task block", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/block")
    public AjaxResult block(@PathVariable Long taskId, @RequestParam Integer version, @RequestParam String type, @RequestParam String reason) {
        return success(taskService.blockTask(taskId, version, type, reason, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title = "AI lab task unblock", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/unblock")
    public AjaxResult unblock(@PathVariable Long taskId, @RequestParam Integer version, @RequestParam String resolution) {
        taskService.unblockTask(taskId, version, resolution, SecurityUtils.getUserId()); return success();
    }
}
