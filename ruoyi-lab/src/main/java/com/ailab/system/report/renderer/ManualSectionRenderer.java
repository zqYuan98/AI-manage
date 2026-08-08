package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class ManualSectionRenderer implements SectionRenderer {
    @Override public String getId() { return "MANUAL"; }
    @Override public boolean supports(String value) { return "MANUAL".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source); Object content = source.getSummary().get("manualText"); if (content == null) content = source.getSummary().get("text"); String text = content == null || String.valueOf(content).trim().isEmpty() ? "暂无人工填写内容" : String.valueOf(content);
        return RendererSupport.result(config, Collections.<Map<String, Object>>emptyList(), RendererSupport.map("text", text));
    }
}
