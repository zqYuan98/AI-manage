package com.ailab.system.service;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import java.util.List;

public interface LabReportTemplateService {
    List<LabReportTemplate> list(Long actorUserId);
    LabReportTemplate get(Long id, Long actorUserId);
    List<LabReportSection> sections(Long templateId, Long actorUserId);
    LabReportTemplate saveRevision(Long sourceTemplateId, LabReportTemplate draft, List<LabReportSection> sections,
            boolean saveAsNewFamily, int expectedVersion, Long actorUserId);
    void setDefault(Long templateId, int expectedVersion, Long actorUserId);
    String previewMarkdown(Long templateId, Long actorUserId);
    String exportJson(Long templateId, Long actorUserId);
    LabReportTemplate importJson(String json, String newTemplateCode, Long actorUserId);
}
