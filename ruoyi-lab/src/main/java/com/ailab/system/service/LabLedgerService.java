package com.ailab.system.service;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabIpr;
import com.ailab.system.domain.LabOne2One;
import java.util.List;

public interface LabLedgerService {
    List<LabAsset> listAssets(LabAsset query, Long actorId);
    List<LabAsset> listAssetRisks(LabAsset query, Long actorId);
    LabAsset getAsset(Long id, Long actorId);
    int createAsset(LabAsset asset, Long actorId);
    int updateAsset(LabAsset asset, Long actorId);
    int deactivateAsset(Long id, Integer version, Long actorId);
    List<LabOne2One> listOne2Ones(LabOne2One query, Long actorId);
    LabOne2One getOne2One(Long id, Long actorId);
    int createOne2One(LabOne2One record, Long actorId);
    int updateOne2One(LabOne2One record, Long actorId);
    List<LabIpr> listIprs(LabIpr query, Long actorId);
    LabIpr getIpr(Long id, Long actorId);
    int createIpr(LabIpr ipr, Long actorId);
    int updateIpr(LabIpr ipr, String rollbackReason, Long actorId);
    int deactivateIpr(Long id, Integer version, Long actorId);
}
