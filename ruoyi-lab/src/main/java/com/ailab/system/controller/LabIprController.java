package com.ailab.system.controller;

import com.ailab.system.domain.LabIpr;
import com.ailab.system.dto.IprUpdateCommand;
import com.ailab.system.service.LabLedgerService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
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

@RestController @RequestMapping("/lab/ipr")
public class LabIprController extends BaseController {
    private final LabLedgerService service; public LabIprController(LabLedgerService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:ipr:list')") @GetMapping("/list") public TableDataInfo list(LabIpr q){startPage();return getDataTable(service.listIprs(q,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:ipr:list')") @GetMapping("/{id}") public AjaxResult detail(@PathVariable Long id){return success(service.getIpr(id,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:ipr:add')") @Log(title="AI lab IPR",businessType=BusinessType.INSERT) @PostMapping public AjaxResult create(@RequestBody LabIpr i){return toAjax(service.createIpr(i,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:ipr:edit')") @Log(title="AI lab IPR",businessType=BusinessType.UPDATE) @PutMapping public AjaxResult update(@RequestBody IprUpdateCommand c){return toAjax(service.updateIpr(c.getIpr(),c.getRollbackReason(),SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:ipr:remove')") @Log(title="AI lab IPR deactivation",businessType=BusinessType.UPDATE) @DeleteMapping("/{id}") public AjaxResult deactivate(@PathVariable Long id,@RequestParam Integer version){return toAjax(service.deactivateIpr(id,version,SecurityUtils.getUserId()));}
}
