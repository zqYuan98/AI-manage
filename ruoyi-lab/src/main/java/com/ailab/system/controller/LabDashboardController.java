package com.ailab.system.controller;

import com.ailab.system.service.LabDashboardService;
import com.ailab.system.service.LabReminderService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/dashboard")
public class LabDashboardController extends BaseController {
    private final LabDashboardService dashboardService;
    private final LabReminderService reminderService;

    public LabDashboardController(LabDashboardService dashboardService, LabReminderService reminderService) {
        this.dashboardService = dashboardService; this.reminderService = reminderService;
    }

    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')")
    @GetMapping
    public AjaxResult overview(@RequestParam String period, HttpServletResponse response) {
        preventCaching(response);
        return success(dashboardService.getOverview(period, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:reminder:list')")
    @GetMapping("/reminders")
    public TableDataInfo reminders(@RequestParam(required = false) Boolean unreadOnly) {
        startPage();
        return getDataTable(reminderService.listReminders(unreadOnly, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:reminder:read')")
    @Log(title = "AI lab reminder read", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/reminders/{id}/read")
    public AjaxResult markRead(@PathVariable Long id, @RequestParam Integer version) {
        reminderService.markRead(id, version, SecurityUtils.getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:reminder:read')")
    @Log(title = "AI lab reminders read all", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/reminders/read-all")
    public AjaxResult markAllRead() {
        return success(reminderService.markAllRead(SecurityUtils.getUserId()));
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
