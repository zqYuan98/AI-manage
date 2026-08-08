package com.ailab.system.report.model;

import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.service.LabAccessService;
import com.ruoyi.system.service.ISysMenuService;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Server-side boundary for synchronous and worker report requests. */
@Component
public final class TrustedReportContextFactory {
    private final LabAccessService access;
    private final ISysMenuService menus;
    public TrustedReportContextFactory(LabAccessService access, ISysMenuService menus) { this.access = access; this.menus = menus; }
    public ReportAccessScope resolve(Long sysUserId) {
        LabAccessContext actor = access.context(sysUserId);
        java.util.Set<String> permissions = menus.selectMenuPermsByUserId(sysUserId);
        if ("lab_manager".equals(actor.getRoleKey())) return ReportAccessScope.manager(permissions);
        if ("lab_lead".equals(actor.getRoleKey())) return ReportAccessScope.lead(actor.getBizLine(), actor.getMemberId(), Collections.<String>emptySet());
        return ReportAccessScope.member(actor.getBizLine(), actor.getMemberId());
    }
    /** Resolves only role/data scope; public callers cannot mint sensitive disclosure. */
    public ReportContext create(Long sysUserId, String period, Instant generatedAt, Map<String, Object> attributes) {
        LabAccessContext actor = access.context(sysUserId); ReportAccessScope scope = resolve(sysUserId);
        return new ReportContext(period, actor.getBizLine(), actor.getMemberId(), generatedAt, scope, attributes);
    }
}
