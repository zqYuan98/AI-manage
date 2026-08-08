package com.ailab.system.report.model;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Defensive copier for report DTO values; report data must not retain mutable caller collections. */
final class ImmutableReportValue {
    private ImmutableReportValue() { }
    static Map<String, Object> map(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source != null) for (Map.Entry<String, Object> entry : source.entrySet()) copy.put(entry.getKey(), value(entry.getValue()));
        return Collections.unmodifiableMap(copy);
    }
    @SuppressWarnings("unchecked")
    static Object value(Object source) {
        if (source instanceof Map) return map((Map<String, Object>) source);
        if (source instanceof Collection) { List<Object> copy = new ArrayList<Object>(); for (Object item : (Collection<?>) source) copy.add(value(item)); return Collections.unmodifiableList(copy); }
        if (source != null && source.getClass().isArray()) { List<Object> copy = new ArrayList<Object>(); for (int i = 0; i < Array.getLength(source); i++) copy.add(value(Array.get(source, i))); return Collections.unmodifiableList(copy); }
        if (source == null || source instanceof String || source instanceof Boolean || source instanceof Character
                || source instanceof Enum || source instanceof java.time.temporal.TemporalAccessor
                || source instanceof Byte || source instanceof Short || source instanceof Integer || source instanceof Long
                || source instanceof Float || source instanceof Double || source instanceof java.math.BigInteger
                || source instanceof java.math.BigDecimal) return source;
        throw new IllegalArgumentException("Report values must be immutable scalar values, maps, collections, or arrays");
    }
}
