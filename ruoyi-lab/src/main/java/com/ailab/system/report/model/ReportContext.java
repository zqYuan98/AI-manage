package com.ailab.system.report.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Trusted report inputs. Values are copied so providers cannot mutate caller state. */
public final class ReportContext {
    private final String period;
    private final String bizLine;
    private final Long requesterId;
    private final Instant generatedAt;
    private final ReportAccessScope accessScope;
    private final Map<String, Object> attributes;

    public ReportContext(String period, String bizLine, Long requesterId, Instant generatedAt, Map<String, Object> attributes) {
        this(period, bizLine, requesterId, generatedAt, ReportAccessScope.member(bizLine, requesterId, java.util.Collections.<String>emptySet()), attributes);
    }
    public ReportContext(String period, String bizLine, Long requesterId, Instant generatedAt, ReportAccessScope accessScope, Map<String, Object> attributes) {
        this.period = require(period, "period"); this.bizLine = require(bizLine, "bizLine"); this.requesterId = requesterId;
        if (accessScope == null) throw new IllegalArgumentException("accessScope is required");
        if (accessScope.getKind() == ReportAccessScope.Kind.MEMBER && !Objects.equals(requesterId, accessScope.getMemberId())) throw new IllegalArgumentException("member scope must match requesterId");
        if (accessScope.getKind() == ReportAccessScope.Kind.LEAD && !bizLine.equals(accessScope.getBizLine())) throw new IllegalArgumentException("lead scope must match bizLine");
        this.accessScope = accessScope;
        this.generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        this.attributes = ImmutableReportValue.map(attributes);
    }
    public String getPeriod() { return period; } public String getBizLine() { return bizLine; } public Long getRequesterId() { return requesterId; }
    public Instant getGeneratedAt() { return generatedAt; } public Map<String, Object> getAttributes() { return attributes; }
    public ReportAccessScope getAccessScope() { return accessScope; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReportContext)) return false;
        ReportContext that = (ReportContext) other;
        return period.equals(that.period) && bizLine.equals(that.bizLine)
                && Objects.equals(requesterId, that.requesterId) && generatedAt.equals(that.generatedAt) && accessScope.equals(that.accessScope)
                && attributes.equals(that.attributes);
    }
    @Override public int hashCode() { return Objects.hash(period, bizLine, requesterId, generatedAt, accessScope, attributes); }
    private static String require(String value, String field) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required"); return value; }
}
