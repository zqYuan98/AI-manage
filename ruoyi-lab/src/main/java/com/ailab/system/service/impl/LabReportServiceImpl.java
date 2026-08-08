package com.ailab.system.service.impl;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.ReportArtifact;
import com.ailab.system.dto.ReportQueueReceipt;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.report.ReportGenerationOrchestrator;
import com.ailab.system.report.ReportJobDispatcher;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysMenuService;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabReportServiceImpl implements LabReportService {
    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabReportMapper mapper; private final LabAccessService access; private final ISysMenuService menus;
    private final ReportGenerationOrchestrator orchestrator; private final ReportJobDispatcher dispatcher;
    public LabReportServiceImpl(LabReportMapper mapper, LabAccessService access, ISysMenuService menus,
            ReportGenerationOrchestrator orchestrator, ReportJobDispatcher dispatcher) {
        this.mapper=mapper;this.access=access;this.menus=menus;this.orchestrator=orchestrator;this.dispatcher=dispatcher;
    }
    @Override @Transactional public ReportQueueReceipt generate(Long templateId,String period,String bizLine,Long actorUserId){LabReportInstance value=orchestrator.createGeneration(templateId,period,bizLine,actorUserId);return dispatcher.queue(value.getId(),"DATA",actor(actorUserId));}
    @Override public LabReportInstance status(Long reportId,Long actorUserId){return orchestrator.authorizeView(reportId,actorUserId);}
    @Override public List<LabReportInstance> history(String period,String bizLine,Long actorUserId){LabAccessContext actor=access.context(actorUserId);Set<String> permissions=menus.selectMenuPermsByUserId(actorUserId);boolean manager="lab_manager".equals(actor.getRoleKey());boolean sensitive=permissions!=null&&permissions.contains("lab:report:sensitive");return safe(mapper.selectReportHistory(period,bizLine,manager,sensitive));}
    @Override public List<LabReportJob> jobs(Long reportId,Long actorUserId){orchestrator.authorizeView(reportId,actorUserId);return safe(mapper.selectReportJobs(reportId));}
    @Override public ReportQueueReceipt retry(Long reportId,String artifact,Long actorUserId){access.requireManager(actorUserId);LabReportInstance value=orchestrator.authorizeView(reportId,actorUserId);return dispatcher.queue(reportId,orchestrator.retryStep(value,artifact),actor(actorUserId));}
    @Override @Transactional public ReportQueueReceipt importMarkdown(Long sourceReportId,String fileName,byte[] bytes,Long actorUserId){access.requireManager(actorUserId);if(fileName==null||fileName.length()>128||fileName.contains("/")||fileName.contains("\\")||!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".md"))throw new ServiceException("A safe .md file name is required");String markdown=utf8(bytes);LabReportInstance value=orchestrator.importMarkdown(sourceReportId,markdown,actorUserId);return dispatcher.queue(value.getId(),"DATA",actor(actorUserId));}
    @Override public LabReportInstance finalizeReport(Long reportId,int expectedVersion,Long actorUserId){return orchestrator.finalizeReport(reportId,expectedVersion,actorUserId);}
    @Override public ReportArtifact artifact(Long reportId,String format,Long actorUserId){return orchestrator.authorizeArtifact(reportId,format,actorUserId);}
    @Override public List<LabReportSummary> summaries(String period,String bizLine,Long actorUserId){requireSummaryScope(period,bizLine,actorUserId,false);return safe(mapper.selectSummaries(period,bizLine));}
    @Override @Transactional public LabReportSummary saveSummary(LabReportSummary summary,Long actorUserId){if(summary==null)throw new ServiceException("Report summary is required");requireSummaryScope(summary.getPeriod(),summary.getBizLine(),actorUserId,true);if(summary.getSectionCode()==null||!summary.getSectionCode().matches("[A-Za-z0-9_-]{1,64}"))throw new ServiceException("Invalid report section code");if(summary.getSummaryText()!=null&&summary.getSummaryText().getBytes(StandardCharsets.UTF_8).length>65536)throw new ServiceException("Report summary text is too large");if(summary.getSummaryJson()!=null){if(summary.getSummaryJson().getBytes(StandardCharsets.UTF_8).length>65536)throw new ServiceException("Report summary JSON is too large");try{if(!JSON.readTree(summary.getSummaryJson()).isObject())throw new ServiceException("Report summary JSON must be an object");}catch(ServiceException ex){throw ex;}catch(Exception ex){throw new ServiceException("Invalid report summary JSON");}}summary.setId(null);summary.setSourceRevision(1);summary.setDelFlag("0");summary.setCreateBy(actor(actorUserId));int rows=mapper.upsertSummary(summary);if(rows<1||rows>2)throw new ServiceException("Report summary was not saved");return summary;}
    private void requireSummaryScope(String period,String bizLine,Long actorUserId,boolean write){if(period==null||!period.matches("[0-9]{4}-(0[1-9]|1[0-2])")||bizLine==null||!bizLine.matches("[A-Za-z0-9_-]{1,32}"))throw new ServiceException("Invalid report summary period or business line");LabAccessContext actor=access.context(actorUserId);if("lab_manager".equals(actor.getRoleKey()))return;if("lab_lead".equals(actor.getRoleKey())&&bizLine.equals(actor.getBizLine()))return;throw new ServiceException(write?"Only managers and the matching line lead may edit summaries":"Report summaries are outside the actor scope");}
    private String utf8(byte[] bytes){if(bytes==null||bytes.length==0||bytes.length>MAX_TEXT_BYTES)throw new ServiceException("Markdown file is missing or too large");try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException ex){throw new ServiceException("Markdown must be valid UTF-8");}}
    private String actor(Long value){return String.valueOf(value);} private <T> List<T> safe(List<T> value){return value==null?Collections.<T>emptyList():value;}
}
