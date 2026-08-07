package com.ailab.system.controller;

import com.ailab.system.domain.LabAsset;
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

@RestController @RequestMapping("/lab/asset")
public class LabAssetController extends BaseController {
    private final LabLedgerService service; public LabAssetController(LabLedgerService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:asset:list')") @GetMapping("/list") public TableDataInfo list(LabAsset q){startPage();return getDataTable(service.listAssets(q,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:asset:list')") @GetMapping("/risks") public AjaxResult risks(LabAsset q){return success(service.listAssetRisks(q,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:asset:list')") @GetMapping("/{id}") public AjaxResult detail(@PathVariable Long id){return success(service.getAsset(id,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:asset:add')") @Log(title="AI lab asset",businessType=BusinessType.INSERT) @PostMapping public AjaxResult create(@RequestBody LabAsset a){return toAjax(service.createAsset(a,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:asset:edit')") @Log(title="AI lab asset",businessType=BusinessType.UPDATE) @PutMapping public AjaxResult update(@RequestBody LabAsset a){return toAjax(service.updateAsset(a,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:asset:remove')") @Log(title="AI lab asset deactivation",businessType=BusinessType.UPDATE) @DeleteMapping("/{id}") public AjaxResult deactivate(@PathVariable Long id,@RequestParam Integer version){return toAjax(service.deactivateAsset(id,version,SecurityUtils.getUserId()));}
}
