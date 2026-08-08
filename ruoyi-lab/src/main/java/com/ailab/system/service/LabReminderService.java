package com.ailab.system.service;

import com.ailab.system.domain.LabReminder;
import java.util.List;

public interface LabReminderService {
    int scanBlocks();
    int scanPendingTasks();
    List<LabReminder> listReminders(Boolean unreadOnly, Long actorUserId);
    void markRead(Long id, Integer version, Long actorUserId);
    int markAllRead(Long actorUserId);
}
