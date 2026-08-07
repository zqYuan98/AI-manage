package com.ailab.system.mapper;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabIpr;
import com.ailab.system.domain.LabOne2One;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabLedgerMapper {
    List<LabAsset> selectAssetList(LabAsset query);
    LabAsset selectAssetById(Long id);
    LabAsset selectAssetForUpdate(Long id);
    List<LabAsset> selectAssetsByMember(Long memberId);
    int insertAsset(LabAsset asset);
    int updateAsset(LabAsset asset);
    int deleteAsset(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);

    List<LabOne2One> selectOne2OneList(LabOne2One query);
    List<LabOne2One> selectOne2OneByMember(Long memberId);
    LabOne2One selectOne2OneById(Long id);
    LabOne2One selectOne2OneForUpdate(Long id);
    int insertOne2One(LabOne2One record);
    int updateOne2One(LabOne2One record);

    List<LabIpr> selectIprList(LabIpr query);
    LabIpr selectIprById(Long id);
    LabIpr selectIprForUpdate(Long id);
    int insertIpr(LabIpr ipr);
    int updateIpr(LabIpr ipr);
    int deleteIpr(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);
}
