package com.ailab.system.controller;

import com.ailab.system.domain.LabSkill;
import com.ailab.system.service.LabMemberService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/lab/skill")
public class LabSkillController extends BaseController {
    private final LabMemberService service; public LabSkillController(LabMemberService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:skill:list')") @GetMapping("/list")
    public TableDataInfo list(LabSkill query){startPage();return getDataTable(service.listSkills(query,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:skill:config')") @Log(title="AI lab skill",businessType=BusinessType.INSERT) @PostMapping
    public AjaxResult create(@RequestBody LabSkill skill){return toAjax(service.createSkill(skill,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:skill:config')") @Log(title="AI lab skill",businessType=BusinessType.UPDATE) @PutMapping
    public AjaxResult update(@RequestBody LabSkill skill){return toAjax(service.updateSkill(skill,SecurityUtils.getUserId()));}
}
