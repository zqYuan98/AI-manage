package com.ailab.system.service;

import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.WeeklyCommitmentCommand;

public interface LabCommitmentService {
    LabTask create(WeeklyCommitmentCommand command, Long userId);
    void complete(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId);
    void markUndone(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId);
    void correct(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId);
    void cancel(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId);
    LabTask carry(Long taskId, Integer version, WeeklyCommitmentCommand command, Long userId);
}
