package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class DeploymentContractTest {
    @Test
    void productionSecretsAndAdministrativeSurfacesFailClosedByDefault() throws Exception {
        String application = read("../ruoyi-admin/src/main/resources/application.yml");
        String druid = read("../ruoyi-admin/src/main/resources/application-druid.yml");
        String demo = read("../ruoyi-admin/src/main/resources/application-demo.yml");
        String verifier = read("../scripts/verify-project.ps1");
        assertTrue(application.contains("secret: ${TOKEN_SECRET}"));
        assertTrue(druid.contains("username: ${MYSQL_USERNAME}"));
        assertTrue(druid.contains("password: ${MYSQL_PASSWORD}"));
        assertFalse(druid.contains("${MYSQL_USERNAME:root}"));
        assertFalse(druid.contains("${MYSQL_PASSWORD:}"));
        assertTrue(application.contains("enabled: ${SWAGGER_ENABLED:false}"));
        assertTrue(demo.contains("enabled: ${SWAGGER_ENABLED:false}"));
        assertEquals(2, occurrences(druid, "enabled: ${DRUID_STAT_ENABLED:false}"));
        assertTrue(verifier.contains("@('-pl','ruoyi-lab','-am','clean','test')"));
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
