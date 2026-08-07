package com.ailab.system.controller;

import com.ailab.system.domain.LabOne2One;
import com.ailab.system.service.LabLedgerService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/lab/one2one")
public class LabOne2OneController extends BaseController {
    private final LabLedgerService service; public LabOne2OneController(LabLedgerService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:one2one:list')") @GetMapping("/list") public TableDataInfo list(LabOne2One q){startPage();return getDataTable(service.listOne2Ones(q,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:one2one:list')") @GetMapping("/{id}") public AjaxResult detail(@PathVariable Long id){return success(service.getOne2One(id,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:one2one:add')") @Log(title="AI lab one-to-one",businessType=BusinessType.INSERT) @PostMapping public AjaxResult create(@RequestBody LabOne2One r){return toAjax(service.createOne2One(r,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:one2one:edit')") @Log(title="AI lab one-to-one",businessType=BusinessType.UPDATE) @PutMapping public AjaxResult update(@RequestBody LabOne2One r){return toAjax(service.updateOne2One(r,SecurityUtils.getUserId()));}
}
