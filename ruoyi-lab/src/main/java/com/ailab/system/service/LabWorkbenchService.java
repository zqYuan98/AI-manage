package com.ailab.system.service;

import com.ailab.system.dto.ManagerWorkbench;
import com.ailab.system.dto.MemberWorkbench;
import java.util.Date;

public interface LabWorkbenchService {
    ManagerWorkbench manager(String period, Date asOf, Long actorId);
    ManagerWorkbench lead(String period, Date asOf, Long actorId);
    MemberWorkbench member(String period, Date asOf, Long actorId);
}
