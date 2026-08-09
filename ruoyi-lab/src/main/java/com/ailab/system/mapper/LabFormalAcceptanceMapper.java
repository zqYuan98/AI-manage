package com.ailab.system.mapper;

import com.ailab.system.domain.LabFormalAcceptanceFact;
import com.ailab.system.domain.LabFormalAcceptanceRevision;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 正式验收修订及事实持久化接口。 */
public interface LabFormalAcceptanceMapper {
    int ensurePeriodLock(@Param("period") String period, @Param("actor") String actor);
    Long lockPeriod(String period);
    Integer selectMaxRevision(@Param("period") String period, @Param("bizLine") String bizLine);
    int insertRevision(LabFormalAcceptanceRevision revision);
    int insertFact(LabFormalAcceptanceFact fact);
    LabFormalAcceptanceRevision selectRevision(Long id);
    List<LabFormalAcceptanceFact> selectFactsByRevision(Long formalRevisionId);
    LabFormalAcceptanceFact selectLatestFactForTask(Long taskId);
}
