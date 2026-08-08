package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public final class ChartSectionRenderer implements SectionRenderer {
    private static final int WIDTH = 640, HEIGHT = 320, MAX_IMAGE_BYTES = 512 * 1024;
    @Override public String getId() { return "CHART"; }
    @Override public boolean supports(String value) { return "CHART".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source); Object chartConfig = config.getRenderConfig().get("chart"); if (chartConfig != null && !"bar".equals(chartConfig)) throw new IllegalArgumentException("Only bar charts are supported"); List<String> categories = new ArrayList<String>(); List<Number> values = new ArrayList<Number>();
        for (int i = 0; i < source.getRows().size(); i++) { Map<String, Object> row = source.getRows().get(i); categories.add(RendererSupport.text(first(row, "label", "goalTitle", "owner", "resultStatus", "memberId"))); Object raw = first(row, "value", "progressRate", "score", "total"); values.add(raw instanceof Number ? finite((Number) raw) : Integer.valueOf(0)); }
        List<Map<String, Object>> series = Collections.<Map<String, Object>>singletonList(RendererSupport.map("name", "数值", "values", values));
        String png = Base64.getEncoder().encodeToString(image(values));
        return RendererSupport.result(config, Collections.<Map<String, Object>>emptyList(), RendererSupport.map("categories", categories, "series", series, "labels", categories, "values", values, "config", "bar", "pngBase64", png, "empty", categories.isEmpty() ? RendererSupport.EMPTY : ""));
    }
    private Object first(Map<String, Object> row, String... fields) { for (String field : fields) if (row.get(field) != null) return row.get(field); return null; }
    private Number finite(Number value) { if ((value instanceof Double && !Double.isFinite(value.doubleValue())) || (value instanceof Float && !Float.isFinite(value.floatValue()))) throw new IllegalArgumentException("Chart values must be finite"); return value; }
    private byte[] image(List<Number> values) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB); fill(image, 0, 0, WIDTH, HEIGHT, 0xFFFFFF); double max = 1D; for (Number value : values) max = Math.max(max, value.doubleValue()); int count = Math.max(1, values.size()); for (int i = 0; i < values.size(); i++) { int height = (int) Math.round(220D * Math.max(0D, values.get(i).doubleValue()) / max); int x = 50 + (i * 550 / count); int next = 50 + ((i + 1) * 550 / count); fill(image, x, 260 - height, Math.max(1, next - x - 1), height, 0x2D5CA0); } fill(image, 50, 260, 551, 1, 0x808080);
        try { ByteArrayOutputStream output = new ByteArrayOutputStream(); if (!ImageIO.write(image, "png", output) || output.size() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Chart image exceeds limit"); return output.toByteArray(); } catch (IOException ex) { throw new IllegalStateException("Cannot encode deterministic chart", ex); }
    }
    private void fill(BufferedImage image, int x, int y, int width, int height, int rgb) { for (int row = Math.max(0, y); row < Math.min(HEIGHT, y + height); row++) for (int column = Math.max(0, x); column < Math.min(WIDTH, x + width); column++) image.setRGB(column, row, rgb); }
}
