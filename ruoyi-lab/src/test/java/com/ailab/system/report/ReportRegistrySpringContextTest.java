package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.exporter.ReportExporter;
import com.ailab.system.report.exporter.ReportExporterRegistry;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.provider.DataSourceProvider;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.renderer.SectionRenderer;
import com.ailab.system.report.renderer.SectionRendererRegistry;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class ReportRegistrySpringContextTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RegistryScan.class);

    @Test
    void emptyPluginListsStillCreateAllThreeStartupRegistries() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(DataSourceProviderRegistry.class));
            assertNotNull(context.getBean(SectionRendererRegistry.class));
            assertNotNull(context.getBean(ReportExporterRegistry.class));
        });
    }

    @Test
    void duplicateProviderRendererAndExporterCapabilitiesFailRealContextStartup() {
        runner.withBean("providerOne", DataSourceProvider.class, () -> provider("provider-one", "TASK_DETAIL"))
                .withBean("providerTwo", DataSourceProvider.class, () -> provider("provider-two", "TASK_DETAIL"))
                .run(context -> assertStartupFailureContains(context.getStartupFailure(), "Conflicting report provider capability"));
        runner.withBean("rendererOne", SectionRenderer.class, () -> renderer("renderer-one", "TABLE"))
                .withBean("rendererTwo", SectionRenderer.class, () -> renderer("renderer-two", "TABLE"))
                .run(context -> assertStartupFailureContains(context.getStartupFailure(), "Conflicting section renderer capability"));
        runner.withBean("exporterOne", ReportExporter.class, () -> exporter("exporter-one", "JSON"))
                .withBean("exporterTwo", ReportExporter.class, () -> exporter("exporter-two", "JSON"))
                .run(context -> assertStartupFailureContains(context.getStartupFailure(), "Conflicting report exporter capability"));
    }

    private void assertStartupFailureContains(Throwable failure, String message) {
        assertNotNull(failure);
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        assertTrue(current.getMessage().contains(message));
    }

    private DataSourceProvider provider(final String id, final String capability) {
        return new DataSourceProvider() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return id.equals(value) || capability.equals(value); }
            @Override public Set<String> getSupportedIds() { return Collections.singleton(capability); }
            @Override public ReportSectionData load(ReportContext context, ReportSectionConfig section) { return null; }
        };
    }

    private SectionRenderer renderer(final String id, final String capability) {
        return new SectionRenderer() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return id.equals(value) || capability.equals(value); }
            @Override public Set<String> getSupportedIds() { return Collections.singleton(capability); }
            @Override public ReportSectionData render(ReportContext context, ReportSectionConfig section, ReportSectionData source) { return source; }
        };
    }

    private ReportExporter exporter(final String id, final String capability) {
        return new ReportExporter() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return id.equals(value) || capability.equals(value); }
            @Override public Set<String> getSupportedIds() { return Collections.singleton(capability); }
            @Override public byte[] export(ReportData data) { return new byte[0]; }
        };
    }

    @Configuration
    @ComponentScan(basePackages = {
            "com.ailab.system.report.provider",
            "com.ailab.system.report.renderer",
            "com.ailab.system.report.exporter"
    })
    static class RegistryScan { }
}
