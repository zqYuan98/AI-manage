package com.ailab.system.report.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Startup-time registry: every declared capability has one supporting, non-shadowed owner. */
@Component
public final class DataSourceProviderRegistry {
    private final Map<String, DataSourceProvider> providers; private final Map<String, DataSourceProvider> capabilities;
    public DataSourceProviderRegistry(List<DataSourceProvider> values) {
        Map<String, DataSourceProvider> ids = new LinkedHashMap<String, DataSourceProvider>();
        Map<String, DataSourceProvider> claimed = new LinkedHashMap<String, DataSourceProvider>();
        for (DataSourceProvider value : values) {
            if (value == null || blank(value.getId()) || !value.supports(value.getId())) throw new IllegalStateException("Report provider must support its own id");
            if (ids.put(value.getId(), value) != null) throw new IllegalStateException("Duplicate report provider id: " + value.getId());
        }
        for (DataSourceProvider value : ids.values()) index(ids, claimed, value, value.getSupportedIds());
        providers = Collections.unmodifiableMap(ids); capabilities = Collections.unmodifiableMap(claimed);
    }
    private static void index(Map<String, DataSourceProvider> ids, Map<String, DataSourceProvider> claimed, DataSourceProvider value, Set<String> declared) {
        if (declared == null) throw new IllegalStateException("Report provider capabilities are required");
        for (String capability : declared) {
            if (blank(capability) || !value.supports(capability)) throw new IllegalStateException("Provider does not support declared capability");
            DataSourceProvider idOwner = ids.get(capability); DataSourceProvider old = claimed.put(capability, value);
            if ((idOwner != null && idOwner != value) || (old != null && old != value)) throw new IllegalStateException("Conflicting report provider capability: " + capability);
        }
    }
    public DataSourceProvider require(String id) { DataSourceProvider value = providers.containsKey(id) ? providers.get(id) : capabilities.get(id); if (value == null || !value.supports(id)) throw new IllegalArgumentException("Unknown report provider: " + id); return value; }
    public Map<String, DataSourceProvider> asMap() { return providers; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
