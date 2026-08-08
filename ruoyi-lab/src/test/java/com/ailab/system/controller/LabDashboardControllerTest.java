package com.ailab.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ruoyi.common.annotation.Log;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class LabDashboardControllerTest {
    @Test
    void dashboardAndReminderEndpointsUseExactPermissionsAndSafeAuditLogging() throws Exception {
        Method overview = LabDashboardController.class.getMethod("overview", String.class, javax.servlet.http.HttpServletResponse.class);
        Method list = LabDashboardController.class.getMethod("reminders", Boolean.class);
        Method read = LabDashboardController.class.getMethod("markRead", Long.class, Integer.class);
        Method readAll = LabDashboardController.class.getMethod("markAllRead");

        assertEquals("@ss.hasPermi('lab:dashboard:view')", overview.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('lab:reminder:list')", list.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('lab:reminder:read')", read.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('lab:reminder:read')", readAll.getAnnotation(PreAuthorize.class).value());
        assertFalse(read.getAnnotation(Log.class).isSaveRequestData());
        assertFalse(readAll.getAnnotation(Log.class).isSaveRequestData());
        String source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/controller/LabDashboardController.java")),java.nio.charset.StandardCharsets.UTF_8);
        int start=source.indexOf("public AjaxResult overview");int end=source.indexOf("@PreAuthorize",start+1);
        assertFalse(start<0||!source.substring(start,end).contains("preventCaching(response)"),"dashboard report metadata must not be cached across sensitive permission revocation");
    }
}
