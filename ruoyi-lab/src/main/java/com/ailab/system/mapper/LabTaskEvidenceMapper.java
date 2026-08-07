package com.ailab.system.mapper;

import com.ailab.system.domain.LabTaskEvidence;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabTaskEvidenceMapper {
    List<LabTaskEvidence> selectEvidenceByTaskId(Long taskId);
    LabTaskEvidence selectEvidenceById(Long id);
    int insertEvidence(LabTaskEvidence evidence);
    int deleteEvidence(@Param("id") Long id, @Param("taskId") Long taskId, @Param("updateBy") String updateBy);
    int approveEvidence(@Param("id") Long id, @Param("taskId") Long taskId,
            @Param("auditorId") Long auditorId, @Param("auditTime") Date auditTime,
            @Param("auditComment") String auditComment, @Param("updateBy") String updateBy);
}
