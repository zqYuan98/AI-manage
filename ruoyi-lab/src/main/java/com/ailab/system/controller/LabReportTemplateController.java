package com.ailab.system.controller;

import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.dto.TemplateRevisionCommand;
import com.ailab.system.service.LabReportTemplateService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.file.FileUtils;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
@RequestMapping("/lab/template")
public class LabReportTemplateController extends BaseController {
    private static final long MAX_JSON = 2L * 1024L * 1024L;
    private final LabReportTemplateService service;
    public LabReportTemplateController(LabReportTemplateService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('lab:template:list')") @GetMapping("/tree")
    public AjaxResult tree(){return success(service.list(SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:template:list')") @GetMapping("/{id}/config")
    public AjaxResult config(@PathVariable Long id){Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("template",service.get(id,SecurityUtils.getUserId()));value.put("sections",service.sections(id,SecurityUtils.getUserId()));return success(value);}

    @PreAuthorize("@ss.hasPermi('lab:template:list')") @GetMapping("/{id}/preview")
    public AjaxResult preview(@PathVariable Long id){return success(service.previewMarkdown(id,SecurityUtils.getUserId()));}

    @PreAuthorize("@ss.hasPermi('lab:template:config')")
    @Log(title="AI lab report template revision",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/revision") public AjaxResult revision(@RequestBody TemplateRevisionCommand command){return success(save(command,false));}

    @PreAuthorize("@ss.hasPermi('lab:template:config')")
    @Log(title="AI lab report template save as",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/save-as") public AjaxResult saveAs(@RequestBody TemplateRevisionCommand command){return success(save(command,true));}

    @PreAuthorize("@ss.hasPermi('lab:template:config')")
    @Log(title="AI lab report template default",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    @PutMapping("/{id}/default") public AjaxResult setDefault(@PathVariable Long id,@RequestParam Integer version){if(version==null)throw new ServiceException("Template version is required");service.setDefault(id,version,SecurityUtils.getUserId());return success();}

    @PreAuthorize("@ss.hasPermi('lab:template:export')") @GetMapping("/{id}/export")
    public void export(@PathVariable Long id,HttpServletResponse response) throws Exception {String json=service.exportJson(id,SecurityUtils.getUserId());response.setContentType("application/json;charset=UTF-8");FileUtils.setAttachmentResponseHeader(response,"template-"+id+".json");response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));}

    @PreAuthorize("@ss.hasPermi('lab:template:import')")
    @Log(title="AI lab report template import",businessType=BusinessType.IMPORT,isSaveRequestData=false,isSaveResponseData=false)
    @PostMapping("/import") public AjaxResult importJson(@RequestParam("file") MultipartFile file,@RequestParam String templateCode) throws Exception {String name=file.getOriginalFilename();if(name==null||name.length()>128||name.contains("/")||name.contains("\\")||!name.toLowerCase(java.util.Locale.ROOT).endsWith(".json")||file.isEmpty()||file.getSize()>MAX_JSON)throw new ServiceException("A safe JSON template file up to 2 MiB is required");return success(service.importJson(utf8(file.getBytes()),templateCode,SecurityUtils.getUserId()));}

    private LabReportTemplate save(TemplateRevisionCommand command,boolean saveAs){if(command==null||command.getExpectedVersion()==null)throw new ServiceException("Template command and expected version are required");return service.saveRevision(command.getSourceTemplateId(),command.getTemplate(),command.getSections(),saveAs,command.getExpectedVersion(),SecurityUtils.getUserId());}
    private String utf8(byte[] bytes){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException ex){throw new ServiceException("Template JSON must be valid UTF-8");}}
}
