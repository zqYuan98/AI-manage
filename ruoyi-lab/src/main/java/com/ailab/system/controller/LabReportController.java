package com.ailab.system.controller;

import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.dto.ReportArtifact;
import com.ailab.system.dto.ReportGenerationCommand;
import com.ailab.system.service.LabReportService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.file.FileUtils;
import java.nio.file.Files;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/lab/report")
public class LabReportController extends BaseController {
    private static final long MAX_MARKDOWN=1024L*1024L;
    private final LabReportService service;
    public LabReportController(LabReportService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('lab:report:list')") @GetMapping("/history")
    public TableDataInfo history(@RequestParam(required=false)String period,@RequestParam(required=false)String bizLine,HttpServletResponse response){preventCaching(response);startPage();return getDataTable(service.history(period,bizLine,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:list')") @GetMapping("/{id}/status")
    public AjaxResult status(@PathVariable Long id,HttpServletResponse response){preventCaching(response);return success(service.status(id,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:list')") @GetMapping("/{id}/body")
    public AjaxResult body(@PathVariable Long id,HttpServletResponse response){preventCaching(response);return success(service.body(id,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:list')") @GetMapping("/{id}/jobs")
    public AjaxResult jobs(@PathVariable Long id,HttpServletResponse response){preventCaching(response);return success(service.jobs(id,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:generate')")
    @Log(title="AI lab report generation",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/generate") public AjaxResult generate(@RequestBody ReportGenerationCommand command){if(command==null)throw new ServiceException("Report generation command is required");return success(service.generate(command.getTemplateId(),command.getPeriod(),command.getBizLine(),SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:retry')")
    @Log(title="AI lab report artifact retry",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{id}/retry/{artifact}") public AjaxResult retry(@PathVariable Long id,@PathVariable String artifact){return success(service.retry(id,artifact.toUpperCase(java.util.Locale.ROOT),SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:generate')")
    @Log(title="AI lab report Markdown import",businessType=BusinessType.IMPORT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/{id}/markdown-import") public AjaxResult importMarkdown(@PathVariable Long id,@RequestParam("file")MultipartFile file)throws Exception{if(file==null||file.isEmpty()||file.getSize()>MAX_MARKDOWN)throw new ServiceException("Markdown file is missing or too large");return success(service.importMarkdown(id,file.getOriginalFilename(),file.getBytes(),SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:finalize')")
    @Log(title="AI lab report finalization",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/{id}/finalize") public AjaxResult finalizeReport(@PathVariable Long id,@RequestParam Integer version){if(version==null)throw new ServiceException("Report version is required");return success(service.finalizeReport(id,version,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:download')") @GetMapping("/{id}/artifact/{format}")
    public void artifact(@PathVariable Long id,@PathVariable String format,HttpServletResponse response)throws Exception{ReportArtifact value=service.artifact(id,format.toUpperCase(java.util.Locale.ROOT),SecurityUtils.getUserId());response.setContentType(value.getContentType());preventCaching(response);FileUtils.setAttachmentResponseHeader(response,value.getFileName());Files.copy(value.getPath(),response.getOutputStream());}

    @PreAuthorize("@ss.hasPermi('lab:report:list')") @GetMapping("/summary")
    public AjaxResult summaries(@RequestParam String period,@RequestParam String bizLine,HttpServletResponse response){preventCaching(response);return success(service.summaries(period,bizLine,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:report:list')")
    @Log(title="AI lab report manual summary",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/summary") public AjaxResult saveSummary(@RequestBody LabReportSummary summary){return success(service.saveSummary(summary,SecurityUtils.getUserId()));}

    private void preventCaching(HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");response.setHeader("Pragma","no-cache");response.setHeader("X-Content-Type-Options","nosniff");}
}
