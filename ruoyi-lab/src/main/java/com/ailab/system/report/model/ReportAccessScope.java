package com.ailab.system.report.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;

/** Explicit, immutable data boundary for report projections.  Context is trusted by the caller,
 * but every report query still receives this boundary rather than depending on thread-local security. */
public final class ReportAccessScope {
    public enum Kind { MANAGER, LEAD, MEMBER }
    private final Kind kind; private final String bizLine; private final Long memberId; private final Set<String> permissions;
    private ReportAccessScope(Kind kind, String bizLine, Long memberId, Set<String> permissions) {
        this.kind = kind; this.bizLine = bizLine; this.memberId = memberId;
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<String>(permissions));
    }
    public static ReportAccessScope manager(Collection<String> permissions) { return new ReportAccessScope(Kind.MANAGER, null, null, copy(permissions)); }
    public static ReportAccessScope lead(String bizLine, Collection<String> permissions) { return new ReportAccessScope(Kind.LEAD, required(bizLine, "bizLine"), null, copy(permissions)); }
    public static ReportAccessScope member(String bizLine, Long memberId, Collection<String> permissions) { if (memberId == null) throw new IllegalArgumentException("memberId is required"); return new ReportAccessScope(Kind.MEMBER, required(bizLine, "bizLine"), memberId, copy(permissions)); }
    public Kind getKind() { return kind; } public String getBizLine() { return bizLine; } public Long getMemberId() { return memberId; }
    public boolean hasPermission(String permission) { return permissions.contains(permission); }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof ReportAccessScope)) return false; ReportAccessScope that = (ReportAccessScope) other; return kind == that.kind && Objects.equals(bizLine, that.bizLine) && Objects.equals(memberId, that.memberId) && permissions.equals(that.permissions); }
    @Override public int hashCode() { return Objects.hash(kind, bizLine, memberId, permissions); }
    private static Set<String> copy(Collection<String> values) { Set<String> result = new LinkedHashSet<String>(); if (values != null) for (String value : values) if (value != null) result.add(value); return result; }
    private static String required(String value, String name) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required"); return value; }
}
