package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabAccessMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabAccessServiceTest {
    private MemoryAccessMapper mapper;
    private LabAccessService service;

    @BeforeEach
    void setUp() {
        mapper = new MemoryAccessMapper();
        mapper.put(context(1L, 11L, "lab_manager", "manage", 100L));
        mapper.put(context(2L, 12L, "lab_lead", "algorithm", 101L));
        mapper.put(context(3L, 13L, "lab_member", "algorithm", 101L));
        service = new LabAccessServiceImpl(mapper);
    }

    @Test
    void taskListScopeIsInjectedFromTrustedRoleContext() {
        LabTask managerQuery = new LabTask();
        service.scopeTaskQuery(managerQuery, 1L);
        assertEquals(null, managerQuery.getBizLine());
        assertEquals(null, managerQuery.getOwnerId());

        LabTask leadQuery = new LabTask();
        service.scopeTaskQuery(leadQuery, 2L);
        assertEquals("algorithm", leadQuery.getBizLine());
        assertEquals(null, leadQuery.getOwnerId());

        LabTask memberQuery = new LabTask();
        service.scopeTaskQuery(memberQuery, 3L);
        assertEquals(Long.valueOf(13L), memberQuery.getOwnerId());
    }

    @Test
    void objectAccessRejectsCrossOwnerAndCrossLineWrites() {
        LabTask own = task(13L, "algorithm");
        LabTask otherSameLine = task(14L, "algorithm");
        LabTask otherLine = task(15L, "platform");

        assertDoesNotThrow(() -> service.requireTaskWrite(own, 3L));
        assertThrows(ServiceException.class, () -> service.requireTaskWrite(otherSameLine, 3L));
        assertDoesNotThrow(() -> service.requireTaskWrite(otherSameLine, 2L));
        assertThrows(ServiceException.class, () -> service.requireTaskWrite(otherLine, 2L));
        assertDoesNotThrow(() -> service.requireTaskWrite(otherLine, 1L));
    }

    @Test
    void onlyManagerOrLeadReviewOthersWithinAllowedScope() {
        LabTask leadOwned = task(12L, "algorithm");
        LabTask teammateOwned = task(13L, "algorithm");
        LabTask crossLine = task(15L, "platform");

        assertThrows(ServiceException.class, () -> service.requireTaskReview(leadOwned, 2L));
        assertDoesNotThrow(() -> service.requireTaskReview(teammateOwned, 2L));
        assertThrows(ServiceException.class, () -> service.requireTaskReview(crossLine, 2L));
        assertThrows(ServiceException.class, () -> service.requireTaskReview(teammateOwned, 3L));
        assertDoesNotThrow(() -> service.requireTaskReview(teammateOwned, 1L));
    }

    @Test
    void leadWritesOnlyOwnedQuarterGoalsAndMemberIsReadOnly() {
        LabGoal ownQuarter = goal("QUARTER", 12L);
        LabGoal otherQuarter = goal("QUARTER", 13L);
        LabGoal annual = goal("YEAR", 12L);

        assertDoesNotThrow(() -> service.requireGoalWrite(ownQuarter, 2L));
        assertThrows(ServiceException.class, () -> service.requireGoalWrite(otherQuarter, 2L));
        assertThrows(ServiceException.class, () -> service.requireGoalWrite(annual, 2L));
        assertThrows(ServiceException.class, () -> service.requireGoalWrite(ownQuarter, 3L));
        assertDoesNotThrow(() -> service.requireGoalWrite(annual, 1L));
    }

    @Test
    void inactiveOrUnassignedUserHasNoLabAccess() {
        assertThrows(ServiceException.class, () -> service.context(99L));
    }

    @Test
    void trustedActorQueryRequiresAnEnabledSystemUser() throws Exception {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabAccessMapper.xml"))) cursor = cursor.getParent();
        String xml = new String(Files.readAllBytes(cursor.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabAccessMapper.xml")), StandardCharsets.UTF_8)
                .toLowerCase().replaceAll("\\s+", "");
        assertTrue(xml.contains("u.status='0'"), "disabled system users must not resolve to a trusted lab actor");
        assertTrue(xml.contains("u.del_flag='0'"), "deleted system users must not resolve to a trusted lab actor");
    }

    private static LabAccessContext context(Long userId, Long memberId, String roleKey, String bizLine, Long deptId) {
        LabAccessContext value = new LabAccessContext();
        value.setUserId(userId); value.setMemberId(memberId); value.setRoleKey(roleKey);
        value.setBizLine(bizLine); value.setDeptId(deptId);
        return value;
    }

    private static LabTask task(Long ownerId, String bizLine) {
        LabTask task = new LabTask(); task.setOwnerId(ownerId); task.setBizLine(bizLine); return task;
    }

    private static LabGoal goal(String level, Long ownerId) {
        LabGoal goal = new LabGoal(); goal.setGoalLevel(level); goal.setOwnerId(ownerId); return goal;
    }

    private static final class MemoryAccessMapper implements LabAccessMapper {
        private final Map<Long, LabAccessContext> contexts = new LinkedHashMap<Long, LabAccessContext>();
        void put(LabAccessContext context) { contexts.put(context.getUserId(), context); }
        @Override public LabAccessContext selectAccessContext(Long userId) { return contexts.get(userId); }
    }
}
