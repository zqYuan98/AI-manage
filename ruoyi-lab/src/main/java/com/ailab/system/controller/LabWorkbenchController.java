package com.ailab.system.controller;

import com.ailab.system.service.LabWorkbenchService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import java.time.Instant;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/workbench")
public class LabWorkbenchController extends BaseController {
    private final LabWorkbenchService service;
    public LabWorkbenchController(LabWorkbenchService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')") @GetMapping("/manager")
    public AjaxResult manager(@RequestParam String period,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant asOf){return success(service.manager(period,Date.from(asOf),SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')") @GetMapping("/lead")
    public AjaxResult lead(@RequestParam String period,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant asOf){return success(service.lead(period,Date.from(asOf),SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')") @GetMapping("/member")
    public AjaxResult member(@RequestParam String period,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant asOf){return success(service.member(period,Date.from(asOf),SecurityUtils.getUserId()));}
}
