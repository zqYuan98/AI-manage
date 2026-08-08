package com.ailab.system.report.model;

import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.service.LabAccessService;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Server-side boundary for synchronous and worker report requests. */
@Component
public final class TrustedReportContextFactory {
    private final LabAccessService access;
    public TrustedReportContextFactory(LabAccessService access) { this.access = access; }
    /** Resolves only role/data scope; public callers cannot mint sensitive disclosure. */
    public ReportContext create(Long sysUserId, String period, Instant generatedAt, Map<String, Object> attributes) {
        LabAccessContext actor = access.context(sysUserId); ReportAccessScope scope;
        if ("lab_manager".equals(actor.getRoleKey())) scope = ReportAccessScope.manager(Collections.<String>emptySet());
        else if ("lab_lead".equals(actor.getRoleKey())) scope = ReportAccessScope.lead(actor.getBizLine(), actor.getMemberId(), Collections.<String>emptySet());
        else scope = ReportAccessScope.member(actor.getBizLine(), actor.getMemberId());
        return new ReportContext(period, actor.getBizLine(), actor.getMemberId(), generatedAt, scope, attributes);
    }
    /** For a server-side permission gate that has already checked lab:report:sensitive. */
    ReportContext createSensitiveManager(Long sysUserId, String period, Instant generatedAt, Map<String, Object> attributes) {
        LabAccessContext actor = access.context(sysUserId);
        if (!"lab_manager".equals(actor.getRoleKey())) throw new SecurityException("Sensitive report requires manager role");
        return new ReportContext(period, actor.getBizLine(), actor.getMemberId(), generatedAt,
                ReportAccessScope.manager(Collections.singleton("lab:report:sensitive")), attributes);
    }
}
