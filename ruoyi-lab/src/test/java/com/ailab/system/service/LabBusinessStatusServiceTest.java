package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.dto.BusinessStatusDescriptor;
import com.ailab.system.service.impl.LabBusinessStatusService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class LabBusinessStatusServiceTest {
    @Test
    void oneCatalogDefinesChineseBusinessLanguageAndNextAction() {
        LabBusinessStatusService service = new LabBusinessStatusService();
        assertStatus(service.describe("TASK_WORKFLOW", "ACTIVE"), "进行中", "更新执行情况");
        assertStatus(service.describe("TASK_WORKFLOW", "PENDING_REVIEW"), "待验收", "完成验收");
        assertStatus(service.describe("EXECUTION", "SELF_DONE"), "成员已完成", "查看完成事实");
        assertStatus(service.describe("EXECUTION", "SELF_UNDONE"), "本周未完成", "填写下一步");
        assertStatus(service.describe("RESULT", "UNDONE"), "未完成", "确认原因和下一步");
        assertStatus(service.describe("PERFORMANCE", "RED_LINE"), "触发红线", "查看红线原因");
        assertStatus(service.describe("REPORT", "FINALIZED"), "已定稿", "下载归档");
        assertStatus(service.describe("ARTIFACT", "SUCCESS"), "已生成", "下载制品");
        assertStatus(service.describe("REMINDER", "WARNING"), "重要提醒", "立即处理");
        assertStatus(service.describe("BIZ_LINE", "algorithm"), "算法研发", "查看业务线");
    }

    @Test
    void frontendExportContainsTheSameCanonicalLabelsAndNoForbiddenEnglishEyebrows() throws Exception {
        String js = new String(Files.readAllBytes(Paths.get("../ruoyi-ui/src/utils/lab-status.js")), StandardCharsets.UTF_8);
        for (String label : new String[] {"进行中", "待验收", "成员已完成", "本周未完成", "触发红线", "已定稿", "已生成", "算法研发"}) {
            assertTrue(js.contains(label), label);
        }
        String dashboard = new String(Files.readAllBytes(Paths.get("../ruoyi-ui/src/views/lab/dashboard/index.vue")), StandardCharsets.UTF_8)
                + new String(Files.readAllBytes(Paths.get("../ruoyi-ui/src/views/lab/dashboard/components/GoalHealthChart.vue")), StandardCharsets.UTF_8)
                + new String(Files.readAllBytes(Paths.get("../ruoyi-ui/src/views/lab/dashboard/components/MemberLoadMatrix.vue")), StandardCharsets.UTF_8);
        for (String forbidden : new String[] {"Goal trajectory", "Task composition", "Capacity ledger", "Personal inbox"}) {
            assertTrue(!dashboard.contains(forbidden), forbidden);
        }
    }

    private static void assertStatus(BusinessStatusDescriptor value, String label, String action) {
        assertEquals(label, value.getLabel());
        assertEquals(action, value.getNextAction());
        assertTrue(value.getDescription() != null && !value.getDescription().trim().isEmpty());
    }
}
