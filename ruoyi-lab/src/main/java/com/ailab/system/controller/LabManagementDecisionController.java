package com.ailab.system.controller;

import com.ailab.system.domain.LabManagementDecision;
import com.ailab.system.service.LabManagementDecisionService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/decision")
public class LabManagementDecisionController extends BaseController {
    private final LabManagementDecisionService service;
    public LabManagementDecisionController(LabManagementDecisionService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')") @GetMapping("/list")
    public AjaxResult list(@RequestParam String period,@RequestParam(required=false) String status){return success(service.list(period,status,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title="管理决策",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping
    public AjaxResult create(@RequestBody LabManagementDecision decision){return success(service.create(decision,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:task:edit')")
    @Log(title="完成管理决策",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/{id}/complete")
    public AjaxResult complete(@PathVariable Long id,@RequestParam Integer version){service.complete(id,version,SecurityUtils.getUserId());return success();}
}
