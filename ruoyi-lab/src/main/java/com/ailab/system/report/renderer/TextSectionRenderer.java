package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.config.SafeFreemarkerFactory;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class TextSectionRenderer implements SectionRenderer {
    private final SafeFreemarkerFactory templates = new SafeFreemarkerFactory();
    @Override public String getId() { return "TEXT"; }
    @Override public boolean supports(String value) { return "TEXT".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source); Object raw = config.getRenderConfig().get("template"); String template = raw == null ? "" : String.valueOf(raw);
        String value = template.trim().isEmpty() ? RendererSupport.EMPTY : templates.render(template, model(context, source));
        return RendererSupport.result(config, java.util.Collections.<Map<String, Object>>emptyList(), RendererSupport.map("text", value));
    }
    private Map<String, Object> model(ReportContext context, ReportSectionData source) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(); Map<String, Object> safeContext = new LinkedHashMap<String, Object>();
        safeContext.put("period", context.getPeriod()); safeContext.put("bizLine", context.getBizLine()); safeContext.put("requesterId", context.getRequesterId()); safeContext.put("generatedAt", context.getGeneratedAt().toString()); safeContext.put("attributes", context.getAttributes());
        result.put("context", safeContext); result.put("rows", source.getRows()); result.put("summary", source.getSummary()); result.put("metadata", RendererSupport.map("sectionCode", source.getSectionCode(), "title", source.getTitle())); return result;
    }
}
