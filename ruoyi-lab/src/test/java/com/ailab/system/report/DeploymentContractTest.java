package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DeploymentContractTest {
    @Test
    void productionSecretsAndAdministrativeSurfacesFailClosedByDefault() throws Exception {
        String application = read("../ruoyi-admin/src/main/resources/application.yml");
        String druid = read("../ruoyi-admin/src/main/resources/application-druid.yml");
        String demo = read("../ruoyi-admin/src/main/resources/application-demo.yml");
        String logback = read("../ruoyi-admin/src/main/resources/logback.xml");
        String deployment = read("../docs/deployment.md");
        String verifier = read("../scripts/verify-project.ps1");
        assertTrue(application.contains("secret: ${TOKEN_SECRET}"));
        assertTrue(druid.contains("username: ${MYSQL_USERNAME}"));
        assertTrue(druid.contains("password: ${MYSQL_PASSWORD}"));
        assertFalse(druid.contains("${MYSQL_USERNAME:root}"));
        assertFalse(druid.contains("${MYSQL_PASSWORD:}"));
        assertTrue(application.contains("enabled: ${SWAGGER_ENABLED:false}"));
        assertTrue(demo.contains("enabled: ${SWAGGER_ENABLED:false}"));
        assertTrue(application.contains("read-new-model: ${LAB_COMMITMENT_READ_NEW_MODEL:false}"));
        assertTrue(application.contains("write-self-close: ${LAB_COMMITMENT_WRITE_SELF_CLOSE:false}"));
        assertTrue(demo.contains("read-new-model: ${LAB_COMMITMENT_READ_NEW_MODEL:false}"));
        assertTrue(demo.contains("write-self-close: ${LAB_COMMITMENT_WRITE_SELF_CLOSE:false}"));
        assertTrue(logback.contains("value=\"${LOG_PATH:-/home/ruoyi/logs}\""),
                "a non-root service must be able to place logs in its writable runtime directory");
        assertTrue(deployment.contains("| `LOG_PATH` |"));
        assertTrue(deployment.contains("$env:LOG_PATH ="));
        assertTrue(deployment.contains("export LOG_PATH="),
                "both supported startup examples must externalize the Logback directory");
        assertEquals(2, occurrences(druid, "enabled: ${DRUID_STAT_ENABLED:false}"));
        assertTrue(verifier.contains("@('-pl','ruoyi-lab','-am','clean','test')"));
        assertTrue(verifier.contains("@('-pl','ruoyi-admin','-am','-DskipTests','clean','package')"),
                "the deployable jar must be rebuilt from a clean thin admin jar so stale nested modules cannot survive");
    }

    @Test
    void realMySqlProfileDefinesEveryRequiredDruidPoolSetting() throws Exception {
        String profile = read("../ruoyi-admin/src/test/resources/application-lab-it.yml");
        String mysqlIt = read("../ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java");
        for (String setting : new String[] {
                "timeBetweenEvictionRunsMillis:", "minEvictableIdleTimeMillis:",
                "maxEvictableIdleTimeMillis:", "testWhileIdle:", "testOnBorrow:", "testOnReturn:"
        }) {
            assertTrue(profile.contains(setting), "lab-it profile is missing Druid setting " + setting);
        }
        assertTrue(profile.contains("secret: lab-it-only-token-secret-not-for-production"),
                "the isolated integration profile needs an explicit test-only token secret");
        assertFalse(profile.contains("web-application-type: none"),
                "the full application IT needs MVC mappings even though it does not listen on a port");
        assertTrue(mysqlIt.contains("webEnvironment = SpringBootTest.WebEnvironment.MOCK"),
                "the full application IT must use a mock servlet context for its security configuration");
    }

    @Test
    void reportDownloadsRejectJsonErrorBlobsInsteadOfSavingFakeArtifacts() throws Exception {
        String reportCenter = read("../ruoyi-ui/src/views/lab/report/index.vue");
        assertTrue(reportCenter.contains("import { blobValidate } from '@/utils/ruoyi'"));
        assertTrue(reportCenter.contains("if (!blobValidate(blob))"));
        assertTrue(reportCenter.contains("return blob.text().then"));
        assertTrue(reportCenter.contains("throw new Error(payload.msg"));
    }

    @Test
    void everySeededLabMenuComponentExistsInTheFrontendBundle() throws Exception {
        String sql = read("../sql/ailab.sql");
        Matcher components = Pattern.compile("'(lab/[a-z0-9]+/index)'").matcher(sql);
        int count = 0;
        while (components.find()) {
            String component = components.group(1);
            assertTrue(Files.isRegularFile(Paths.get("../ruoyi-ui/src/views", component + ".vue")),
                    "seeded menu component has no matching Vue file: " + component);
            count++;
        }
        assertEquals(11, count, "all lab page menu components must be covered by this contract");
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
