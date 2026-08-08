package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.report.config.ReportConfigValidator;
import com.ailab.system.service.impl.LabReportTemplateServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

class LabReportTemplateServiceTest {
    private LabReportMapper mapper;
    private LabAccessService access;
    private LabReportTemplateService service;

    @BeforeEach
    void setUp() {
        mapper = mock(LabReportMapper.class);
        access = mock(LabAccessService.class);
        service = new LabReportTemplateServiceImpl(mapper, access, new ReportConfigValidator());
        doAnswer(call -> { ((LabReportTemplate) call.getArgument(0)).setId(91L); return 1; })
                .when(mapper).insertTemplate(any(LabReportTemplate.class));
        doAnswer(call -> ((java.util.List<?>) call.getArgument(1)).size())
                .when(mapper).insertSections(any(), any());
    }

    @Test
    void savingPublishedTemplateCreatesConsecutiveRevisionInSameFamily() {
        LabReportTemplate source = template(7L, "monthly", 3, 8, "1", "1");
        when(mapper.selectTemplateById(7L)).thenReturn(source);when(mapper.lockTemplateType("MONTH")).thenReturn(Collections.singletonList(source));when(mapper.selectTemplateForUpdate(7L)).thenReturn(source);
        when(mapper.selectMaxTemplateRevisionForUpdate("monthly")).thenReturn(3);

        LabReportTemplate saved = service.saveRevision(7L, template(null, "ignored", 1, 0, "1", "0"),
                Collections.singletonList(section("DELIVERY")), false, 8, 1001L);

        assertEquals("monthly", saved.getTemplateCode());
        assertEquals(4, saved.getRevisionNo());
        assertEquals("1", saved.getLatestFlag());
        assertEquals("1", saved.getDefaultFlag(), "the new latest revision must inherit the family default");
        verify(mapper).clearLatestTemplate("monthly", "1001");
        verify(mapper).clearDefaultTemplate("MONTH",91L,"1001");
        verify(mapper).insertSections(eq(91L), any());
    }

    @Test
    void revisionFromAnOlderSourceTransfersTheFamilyDefaultFromTheCurrentLatest() {
        LabReportTemplate oldSource=template(7L,"monthly",2,8,"0","0");
        LabReportTemplate currentLatest=template(8L,"monthly",3,4,"1","1");
        when(mapper.selectTemplateById(7L)).thenReturn(oldSource);when(mapper.selectTemplateForUpdate(7L)).thenReturn(oldSource);when(mapper.lockTemplateType("MONTH")).thenReturn(java.util.Arrays.asList(oldSource,currentLatest));when(mapper.selectMaxTemplateRevisionForUpdate("monthly")).thenReturn(3);

        LabReportTemplate saved=service.saveRevision(7L,template(null,"monthly",4,0,"1","0"),Collections.singletonList(section("DELIVERY")),false,8,1001L);

        assertEquals("1",saved.getDefaultFlag());verify(mapper).clearDefaultTemplate("MONTH",91L,"1001");
    }

    @Test
    void defaultFamilyCannotPublishADisabledLatestRevision() {
        LabReportTemplate source=template(7L,"monthly",3,8,"1","1");LabReportTemplate disabled=template(null,"monthly",4,0,"1","0");disabled.setStatus("DISABLED");
        when(mapper.selectTemplateById(7L)).thenReturn(source);when(mapper.selectTemplateForUpdate(7L)).thenReturn(source);when(mapper.lockTemplateType("MONTH")).thenReturn(Collections.singletonList(source));when(mapper.selectMaxTemplateRevisionForUpdate("monthly")).thenReturn(3);

        assertThrows(ServiceException.class,()->service.saveRevision(7L,disabled,Collections.singletonList(section("DELIVERY")),false,8,1001L));

        verify(mapper,never()).clearLatestTemplate(any(),any());verify(mapper,never()).insertTemplate(any());
    }

    @Test
    void saveAsNewStartsIndependentFamilyAtRevisionOne() {
        LabReportTemplate source = template(7L, "monthly", 3, 8, "1", "1");
        when(mapper.selectTemplateForUpdate(7L)).thenReturn(source);
        LabReportTemplate draft = template(null, "management-monthly", 9, 0, "1", "0");

        LabReportTemplate saved = service.saveRevision(7L, draft,
                Collections.singletonList(section("RISKS")), true, 8, 1001L);

        assertEquals("management-monthly", saved.getTemplateCode());
        assertNotEquals(source.getTemplateCode(), saved.getTemplateCode());
        assertEquals(1, saved.getRevisionNo());
        verify(mapper, never()).clearLatestTemplate("monthly", "1001");
    }

    @Test
    void staleTemplateVersionIsRejectedBeforeAnyWrite() {
        LabReportTemplate stale=template(7L,"monthly",3,9,"1","1");when(mapper.selectTemplateById(7L)).thenReturn(stale);when(mapper.lockTemplateType("MONTH")).thenReturn(Collections.singletonList(stale));when(mapper.selectTemplateForUpdate(7L)).thenReturn(stale);
        assertThrows(ServiceException.class, () -> service.saveRevision(7L,
                template(null, "monthly", 4, 0, "1", "0"), Collections.singletonList(section("A")),
                false, 8, 1001L));
        verify(mapper, never()).insertTemplate(any());
    }

    @Test
    void defaultSwitchIsAtomicLatestEnabledAndOptimistic() {
        LabReportTemplate candidate = template(9L, "monthly", 4, 2, "1", "0");
        when(mapper.selectTemplateById(9L)).thenReturn(candidate);
        when(mapper.selectTemplateForUpdate(9L)).thenReturn(candidate);
        when(mapper.lockTemplateType("MONTH")).thenReturn(Collections.singletonList(candidate));
        when(mapper.clearDefaultTemplate("MONTH", 9L, "1001")).thenReturn(1);
        when(mapper.markDefaultTemplate(9L, 2, "1001")).thenReturn(1);

        service.setDefault(9L, 2, 1001L);

        verify(mapper).clearDefaultTemplate("MONTH", 9L, "1001");
        verify(mapper).markDefaultTemplate(9L, 2, "1001");
        InOrder ordered=inOrder(mapper);ordered.verify(mapper).selectTemplateById(9L);ordered.verify(mapper).lockTemplateType("MONTH");ordered.verify(mapper).selectTemplateForUpdate(9L);
    }

    @Test
    void defaultSwitchNeverInvalidatesItsOwnOptimisticVersion() {
        LabReportTemplate candidate = template(9L, "monthly", 4, 2, "1", "1");
        when(mapper.selectTemplateById(9L)).thenReturn(candidate);
        when(mapper.selectTemplateForUpdate(9L)).thenReturn(candidate);
        when(mapper.lockTemplateType("MONTH")).thenReturn(Collections.singletonList(candidate));
        when(mapper.markDefaultTemplate(9L, 2, "1001")).thenReturn(1);

        service.setDefault(9L, 2, 1001L);

        verify(mapper).clearDefaultTemplate("MONTH", 9L, "1001");
        verify(mapper).markDefaultTemplate(9L, 2, "1001");
    }

    @Test
    void exportAndImportUseValidatedBoundedJsonAndNeverReuseDatabaseIdentity() {
        LabReportTemplate source = template(7L, "monthly", 3, 8, "1", "1");
        when(mapper.selectTemplateById(7L)).thenReturn(source);
        when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(section("DELIVERY")));
        String json = service.exportJson(7L, 1001L);
        assertTrue(json.contains("\"templateCode\":\"monthly\"") && json.contains("\"sectionCode\":\"DELIVERY\""));

        LabReportTemplate imported = service.importJson(json, "monthly-copy", 1001L);
        assertEquals("monthly-copy", imported.getTemplateCode());
        assertEquals(1, imported.getRevisionNo());
        assertEquals(91L, imported.getId());
        assertThrows(ServiceException.class, () -> service.importJson(repeat('x', 2 * 1024 * 1024 + 1), "too-big", 1001L));
        String unknownNestedField = json.replace("\"templateName\"", "\"clientControlled\":true,\"templateName\"");
        assertThrows(ServiceException.class, () -> service.importJson(unknownNestedField, "bad-copy", 1001L));
        String coercedFlag = json.replace("\"manual\":false", "\"manual\":\"true\"");
        assertThrows(ServiceException.class, () -> service.importJson(coercedFlag, "bad-flag", 1001L));
    }

    @Test
    void duplicateSectionCodesAndSortPositionsAreRejected() {
        LabReportSection first = section("DELIVERY");
        LabReportSection duplicate = section("DELIVERY");
        assertThrows(ServiceException.class, () -> service.saveRevision(null,
                template(null, "new-family", 1, 0, "1", "0"),
                java.util.Arrays.asList(first, duplicate), true, 0, 1001L));
        verify(mapper, never()).insertTemplate(any());
    }

    @Test
    void sameFamilyRevisionCannotDowngradePersistedSensitiveSection() {
        LabReportTemplate source=template(7L,"monthly",3,8,"1","0");when(mapper.selectTemplateById(7L)).thenReturn(source);when(mapper.lockTemplateType("MONTH")).thenReturn(Collections.singletonList(source));when(mapper.selectTemplateForUpdate(7L)).thenReturn(source);when(mapper.selectMaxTemplateRevisionForUpdate("monthly")).thenReturn(3);
        LabReportSection persisted=section("PRIVATE");persisted.setSensitiveFlag("1");persisted.setSensitivePermission("lab:report:sensitive");when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(persisted));
        LabReportSection downgraded=section("PRIVATE");downgraded.setSensitiveFlag("0");downgraded.setSensitivePermission(null);

        assertThrows(IllegalStateException.class,()->service.saveRevision(7L,template(null,"monthly",4,0,"1","0"),Collections.singletonList(downgraded),false,8,1001L));

        verify(mapper,never()).insertTemplate(any());
    }

    @Test
    void previewIsHumanReadableMarkdownAndDoesNotExposeRawConfiguration() {
        when(mapper.selectTemplateById(7L)).thenReturn(template(7L, "monthly", 3, 8, "1", "1"));
        when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(section("DELIVERY")));

        String preview = service.previewMarkdown(7L, 1001L);

        assertTrue(preview.startsWith("# Monthly report"));
        assertTrue(preview.contains("## DELIVERY"));
        assertTrue(!preview.contains("queryConfigJson"));
    }

    @Test
    void jsonImportOwnsARealTransactionDespiteCallingTheRevisionMethodInternally() throws Exception {
        assertTrue(LabReportTemplateServiceImpl.class
                .getMethod("importJson", String.class, String.class, Long.class)
                .isAnnotationPresent(Transactional.class));
    }

    private LabReportTemplate template(Long id, String code, int revision, int version, String latest, String defaultFlag) {
        LabReportTemplate value = new LabReportTemplate(); value.setId(id); value.setTemplateCode(code);
        value.setTemplateName("Monthly report"); value.setPeriodType("MONTH"); value.setRevisionNo(revision);
        value.setVersion(version); value.setLatestFlag(latest); value.setDefaultFlag(defaultFlag); value.setStatus("ENABLED");
        value.setHeaderJson("{}"); value.setStyleJson("{}"); return value;
    }

    private LabReportSection section(String code) {
        LabReportSection value = new LabReportSection(); value.setSectionCode(code); value.setSectionName(code);
        value.setSectionType("TEXT"); value.setSortNo(10); value.setDataSource("GOAL_PROGRESS");
        value.setQueryConfigJson("{}"); value.setRenderConfigJson("{}"); value.setStyleConfigJson("{}");
        value.setManualFlag("0"); value.setVisibleFlag("1"); value.setSensitiveFlag("0"); value.setVersion(0); return value;
    }

    private String repeat(char value, int count) { char[] values = new char[count]; java.util.Arrays.fill(values, value); return new String(values); }
}
