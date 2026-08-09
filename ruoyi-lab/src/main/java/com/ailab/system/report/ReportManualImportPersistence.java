package com.ailab.system.report;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.mapper.LabReportMapper;
import com.ruoyi.common.exception.ServiceException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the durable manual-import revision in a short transaction, before any file work starts. */
@Component
public class ReportManualImportPersistence {
    private final LabReportMapper mapper;

    public ReportManualImportPersistence(LabReportMapper mapper) { this.mapper = mapper; }

    @Transactional
    public CreatedDraft create(Long sourceReportId, String markdown, String contentJson, Long actorUserId) {
        LabReportInstance source = required(mapper.selectReportById(sourceReportId));
        mapper.lockReportFamily(source.getTemplateCode(), source.getPeriod(), source.getBizLine());
        source = required(mapper.selectReportById(sourceReportId));
        if (!"SUCCESS".equals(source.getJsonStatus()) || !hasText(source.getContentJson())) {
            throw new ServiceException("Only a report with successful persisted data can be imported");
        }
        Integer maximum = mapper.selectMaxReportRevisionForUpdate(source.getTemplateCode(), source.getPeriod(), source.getBizLine());
        LabReportInstance target = new LabReportInstance();
        target.setReportNo(reportNo(source.getPeriod(), source.getBizLine()));
        target.setTemplateId(source.getTemplateId()); target.setTemplateCode(source.getTemplateCode());
        target.setTemplateRevision(source.getTemplateRevision()); target.setPeriod(source.getPeriod());
        target.setBizLine(source.getBizLine()); target.setRevisionNo(maximum == null ? 1 : maximum + 1);
        target.setLifecycleStatus("DRAFT"); target.setCurrentFlag("0"); target.setFinalFlag("0");
        target.setSensitiveFlag(source.getSensitiveFlag()); target.setSourceType("MANUAL_IMPORT");
        target.setSourcePerfRevision(source.getSourcePerfRevision()); target.setSourceDataJson(source.getSourceDataJson());
        target.setSourceCloseRevision(source.getSourceCloseRevision());target.setSourceFormalRevision(source.getSourceFormalRevision());
        target.setSourceExecutionCutoff(source.getSourceExecutionCutoff());target.setPreviewOnly("0");
        target.setContentJson(contentJson); target.setContentMarkdown(markdown);
        target.setJsonStatus("SUCCESS"); target.setMarkdownStatus("SUCCESS");
        target.setWordStatus("PENDING"); target.setPdfStatus("NOT_REQUESTED");
        target.setVersion(0); target.setDelFlag("0"); target.setCreateBy(String.valueOf(actorUserId));
        if (mapper.insertReportInstance(target) != 1 || target.getId() == null) {
            throw new ServiceException("Markdown import version was not created");
        }
        return new CreatedDraft(source, target);
    }

    private String reportNo(String period, String bizLine) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "RPT-" + period.replaceAll("[^A-Za-z0-9]", "") + "-" + bizLine + "-" + suffix;
    }
    private LabReportInstance required(LabReportInstance value) {
        if (value == null) throw new ServiceException("Report does not exist");
        return value;
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    public static final class CreatedDraft {
        private final LabReportInstance source;
        private final LabReportInstance target;
        CreatedDraft(LabReportInstance source, LabReportInstance target) { this.source = source; this.target = target; }
        public LabReportInstance getSource() { return source; }
        public LabReportInstance getTarget() { return target; }
    }
}
