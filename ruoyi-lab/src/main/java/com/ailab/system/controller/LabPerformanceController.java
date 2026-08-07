package com.ailab.system.controller;

import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.dto.CalibrationCommand;
import com.ailab.system.dto.CollaborationReviewCommand;
import com.ailab.system.dto.RedLineRevokeCommand;
import com.ailab.system.service.LabPerformanceService;
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
@RequestMapping("/lab/perf")
public class LabPerformanceController extends BaseController {
    private final LabPerformanceService service;
    public LabPerformanceController(LabPerformanceService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('lab:perf:list')")
    @GetMapping("/my")
    public AjaxResult my(@RequestParam String period){return success(service.listMyScores(period,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:perf:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam String period){return success(service.listScores(period,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:perf:close')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam Long memberId,@RequestParam String period){return success(service.preview(memberId,period,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:perf:list')")
    @GetMapping("/collaboration")
    public AjaxResult collaboration(@RequestParam String period){return success(service.listCollaboration(period,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:perf:list')")
    @Log(title="AI lab collaboration fact",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/collaboration")
    public AjaxResult createCollaboration(@RequestBody LabCollaborationRecord record){return success(service.createCollaboration(record,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:perf:close')")
    @Log(title="AI lab collaboration review",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/collaboration/{id}/review")
    public AjaxResult reviewCollaboration(@PathVariable Long id,@RequestBody CollaborationReviewCommand command){service.reviewCollaboration(id,command,SecurityUtils.getUserId());return success();}

    @PreAuthorize("@ss.hasPermi('lab:perf:close')")
    @Log(title="AI lab period close",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/period/{period}/close")
    public AjaxResult close(@PathVariable String period,@RequestParam String reason){return success(service.closePeriod(period,reason,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:perf:reopen')")
    @Log(title="AI lab period reopen",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/period/{period}/reopen")
    public AjaxResult reopen(@PathVariable String period,@RequestParam String reason){service.reopenPeriod(period,reason,SecurityUtils.getUserId());return success();}

    @PreAuthorize("@ss.hasPermi('lab:perf:list')")
    @Log(title="AI lab monthly performance confirmation",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}/confirm")
    public AjaxResult confirm(@PathVariable Long id,@RequestParam Integer version){service.confirmMonthlyScore(id,version,SecurityUtils.getUserId());return success();}

    @PreAuthorize("@ss.hasPermi('lab:perf:revoke')")
    @Log(title="AI lab red-line correction",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/{id}/red-line/revoke")
    public AjaxResult revoke(@PathVariable Long id,@RequestBody RedLineRevokeCommand command){service.revokeRedLine(id,command,SecurityUtils.getUserId());return success();}

    @PreAuthorize("@ss.hasPermi('lab:perf:calibrate')")
    @Log(title="AI lab quarterly calibration",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/quarter/{quarter}/member/{memberId}/calibrate")
    public AjaxResult calibrate(@PathVariable String quarter,@PathVariable Long memberId,@RequestBody CalibrationCommand command){return success(service.calibrateQuarter(quarter,memberId,command,SecurityUtils.getUserId()));}
}
