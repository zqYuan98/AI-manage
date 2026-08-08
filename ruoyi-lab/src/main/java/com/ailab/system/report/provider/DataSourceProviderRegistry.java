package com.ailab.system.report.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Startup-time registry: ambiguous providers are configuration errors, never runtime guesses. */
public final class DataSourceProviderRegistry {
    private final Map<String, DataSourceProvider> providers;
    private final Map<String, DataSourceProvider> capabilities;
    public DataSourceProviderRegistry(Collection<? extends DataSourceProvider> values) {
        Map<String, DataSourceProvider> result = new LinkedHashMap<String, DataSourceProvider>();
        for (DataSourceProvider value : values) {
            if (value == null || blank(value.getId())) throw new IllegalStateException("Report provider id is required");
            if (result.put(value.getId(), value) != null) throw new IllegalStateException("Duplicate report provider id: " + value.getId());
        }
        Map<String, DataSourceProvider> capabilityMap = new LinkedHashMap<String, DataSourceProvider>();
        for (String supported : ReportConfigValidatorIds.PROVIDER_IDS) {
            DataSourceProvider owner = null;
            for (DataSourceProvider provider : result.values()) if (provider.supports(supported)) {
                if (owner != null) throw new IllegalStateException("Conflicting report providers for: " + supported);
                owner = provider;
            }
            if (owner != null) capabilityMap.put(supported, owner);
        }
        providers = Collections.unmodifiableMap(result);
        capabilities = Collections.unmodifiableMap(capabilityMap);
    }
    public DataSourceProvider require(String id) { DataSourceProvider value = providers.get(id); if (value == null) value = capabilities.get(id); if (value == null) throw new IllegalArgumentException("Unknown report provider: " + id); return value; }
    public Map<String, DataSourceProvider> asMap() { return providers; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
