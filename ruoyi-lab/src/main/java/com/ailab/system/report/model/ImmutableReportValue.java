package com.ailab.system.report.model;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/** Defensive copier for report DTO values; report data must not retain mutable caller collections. */
final class ImmutableReportValue {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 100000;
    private ImmutableReportValue() { }
    static Map<String, Object> map(Map<String, Object> source) {
        return map(source, new CopyState(), 0);
    }
    private static Map<String, Object> map(Map<String, Object> source, CopyState state, int depth) {
        state.node(depth); state.enter(source);
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        try { if (source != null) for (Map.Entry<String, Object> entry : source.entrySet()) copy.put(entry.getKey(), value(entry.getValue(), state, depth + 1)); return Collections.unmodifiableMap(copy); }
        finally { state.leave(source); }
    }
    @SuppressWarnings("unchecked")
    static Object value(Object source) {
        return value(source, new CopyState(), 0);
    }
    @SuppressWarnings("unchecked")
    private static Object value(Object source, CopyState state, int depth) {
        if (source instanceof Map) return map((Map<String, Object>) source, state, depth);
        state.node(depth);
        if (source instanceof Collection) { state.enter(source); try { List<Object> copy = new ArrayList<Object>(); for (Object item : (Collection<?>) source) copy.add(value(item, state, depth + 1)); return Collections.unmodifiableList(copy); } finally { state.leave(source); } }
        if (source != null && source.getClass().isArray()) { state.enter(source); try { List<Object> copy = new ArrayList<Object>(); for (int i = 0; i < Array.getLength(source); i++) copy.add(value(Array.get(source, i), state, depth + 1)); return Collections.unmodifiableList(copy); } finally { state.leave(source); } }
        if (source instanceof Enum) return ((Enum<?>) source).name();
        if (source == null || source instanceof String || source instanceof Boolean || source instanceof Character
                || source instanceof Byte || source instanceof Short || source instanceof Integer || source instanceof Long
                || source instanceof Float || source instanceof Double || source instanceof java.math.BigInteger
                || source instanceof java.math.BigDecimal) return source;
        throw new IllegalArgumentException("Report values must be immutable scalar values, maps, collections, or arrays");
    }
    private static final class CopyState {
        private int nodes; private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<Object, Boolean>();
        void node(int depth) { if (depth > MAX_DEPTH) throw new IllegalArgumentException("Report values exceed depth limit"); if (++nodes > MAX_NODES) throw new IllegalArgumentException("Report values exceed node limit"); }
        void enter(Object source) { if (source != null && active.put(source, Boolean.TRUE) != null) throw new IllegalArgumentException("Report values must not contain cycles"); }
        void leave(Object source) { if (source != null) active.remove(source); }
    }
}
