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
        Method overview = LabDashboardController.class.getMethod("overview", String.class);
        Method list = LabDashboardController.class.getMethod("reminders", Boolean.class);
        Method read = LabDashboardController.class.getMethod("markRead", Long.class, Integer.class);
        Method readAll = LabDashboardController.class.getMethod("markAllRead");

        assertEquals("@ss.hasPermi('lab:dashboard:view')", overview.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('lab:reminder:list')", list.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('lab:reminder:read')", read.getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermi('lab:reminder:read')", readAll.getAnnotation(PreAuthorize.class).value());
        assertFalse(read.getAnnotation(Log.class).isSaveRequestData());
        assertFalse(readAll.getAnnotation(Log.class).isSaveRequestData());
    }
}
