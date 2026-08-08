package com.ailab.system.report.renderer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Startup-time registry for exact renderer capabilities. */
@Component
public final class SectionRendererRegistry {
    private final Map<String, SectionRenderer> renderers; private final Map<String, SectionRenderer> capabilities;
    public SectionRendererRegistry(List<SectionRenderer> values) {
        Map<String, SectionRenderer> ids = new LinkedHashMap<String, SectionRenderer>(); Map<String, SectionRenderer> claimed = new LinkedHashMap<String, SectionRenderer>();
        for (SectionRenderer value : values) { if (value == null || blank(value.getId()) || !value.supports(value.getId())) throw new IllegalStateException("Section renderer must support its own id"); if (ids.put(value.getId(), value) != null) throw new IllegalStateException("Duplicate section renderer id: " + value.getId()); }
        for (SectionRenderer value : ids.values()) index(ids, claimed, value, value.getSupportedIds());
        renderers = Collections.unmodifiableMap(ids); capabilities = Collections.unmodifiableMap(claimed);
    }
    private static void index(Map<String, SectionRenderer> ids, Map<String, SectionRenderer> claimed, SectionRenderer value, Set<String> declared) { if (declared == null) throw new IllegalStateException("Section renderer capabilities are required"); for (String capability : declared) { if (blank(capability) || !value.supports(capability)) throw new IllegalStateException("Renderer does not support declared capability"); SectionRenderer idOwner = ids.get(capability); SectionRenderer old = claimed.put(capability, value); if ((idOwner != null && idOwner != value) || (old != null && old != value)) throw new IllegalStateException("Conflicting section renderer capability: " + capability); } }
    public SectionRenderer require(String id) { SectionRenderer value = renderers.containsKey(id) ? renderers.get(id) : capabilities.get(id); if (value == null || !value.supports(id)) throw new IllegalArgumentException("Unknown section renderer: " + id); return value; }
    public Map<String, SectionRenderer> asMap() { return renderers; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
