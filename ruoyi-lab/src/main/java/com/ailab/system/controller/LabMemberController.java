package com.ailab.system.controller;

import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.service.LabMemberService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import java.util.List;
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
@RequestMapping("/lab/member")
public class LabMemberController extends BaseController {
    private final LabMemberService service;
    public LabMemberController(LabMemberService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('lab:member:list')") @GetMapping("/list")
    public TableDataInfo list(LabMember query){startPage();return getDataTable(service.listMembers(query,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:member:add')") @GetMapping("/available-users")
    public AjaxResult availableUsers(){return success(service.listAvailableSystemUsers(SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:member:list')") @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id){return success(service.getMemberDetail(id,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:member:add')") @Log(title="AI lab member",businessType=BusinessType.INSERT) @PostMapping
    public AjaxResult create(@RequestBody LabMember member){return toAjax(service.createMember(member,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:member:edit')") @Log(title="AI lab member",businessType=BusinessType.UPDATE) @PutMapping
    public AjaxResult update(@RequestBody LabMember member){return toAjax(service.updateMember(member,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:member:remove')") @Log(title="AI lab member deactivation",businessType=BusinessType.UPDATE) @PutMapping("/{id}/deactivate")
    public AjaxResult deactivate(@PathVariable Long id,@RequestParam Integer version){return toAjax(service.deactivateMember(id,version,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:member:edit')") @Log(title="AI lab member reactivation",businessType=BusinessType.UPDATE) @PutMapping("/{id}/reactivate")
    public AjaxResult reactivate(@PathVariable Long id,@RequestParam Integer version){return toAjax(service.reactivateMember(id,version,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:skill:list')") @GetMapping("/{id}/skills")
    public AjaxResult matrix(@PathVariable Long id){return success(service.getSkillMatrix(id,SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasAnyPermi('lab:skill:config,lab:skill:list')") @Log(title="AI lab skill matrix",businessType=BusinessType.UPDATE) @PutMapping("/{id}/skills")
    public AjaxResult saveMatrix(@PathVariable Long id,@RequestBody List<LabMemberSkill> rows){return success(service.saveSkillMatrix(id,rows,SecurityUtils.getUserId()));}
}
