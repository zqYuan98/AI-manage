package com.ailab.system.report.renderer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fails fast if two renderers claim the same stable section type. */
public final class SectionRendererRegistry {
    private static final String[] TYPES = { "TABLE", "STAT", "TEXT", "MANUAL", "GROUP_TEXT", "CHART" };
    private final Map<String, SectionRenderer> renderers;
    private final Map<String, SectionRenderer> capabilities;
    public SectionRendererRegistry(Collection<? extends SectionRenderer> values) {
        Map<String, SectionRenderer> result = new LinkedHashMap<String, SectionRenderer>();
        for (SectionRenderer value : values) {
            if (value == null || value.getId() == null || value.getId().trim().isEmpty()) throw new IllegalStateException("Section renderer id is required");
            if (result.put(value.getId(), value) != null) throw new IllegalStateException("Duplicate section renderer id: " + value.getId());
        }
        Map<String, SectionRenderer> capabilityMap = new LinkedHashMap<String, SectionRenderer>();
        for (String type : TYPES) { SectionRenderer owner = null; for (SectionRenderer renderer : result.values()) if (renderer.supports(type)) { if (owner != null) throw new IllegalStateException("Conflicting section renderers for: " + type); owner = renderer; } if (owner != null) capabilityMap.put(type, owner); }
        renderers = Collections.unmodifiableMap(result);
        capabilities = Collections.unmodifiableMap(capabilityMap);
    }
    public SectionRenderer require(String id) { SectionRenderer value = renderers.get(id); if (value == null) value = capabilities.get(id); if (value == null) throw new IllegalArgumentException("Unknown section renderer: " + id); return value; }
    public Map<String, SectionRenderer> asMap() { return renderers; }
}
