package com.ailab.system.service;

import com.ailab.system.domain.LabAsset;
import java.util.List;
import org.springframework.stereotype.Component;

/** Single server-side definition of asset single-point ownership risk. */
@Component
public class LabAssetRiskPolicy {
    public void apply(LabAsset asset) {
        boolean inUse = "ACTIVE".equals(asset.getStatus())
                && ("DEPLOYED".equals(asset.getAssetStage()) || "ACCEPTED".equals(asset.getAssetStage()));
        boolean riskRelevant = "1".equals(asset.getCriticalFlag()) || inUse;
        asset.setSinglePointRisk(riskRelevant
                && (asset.getBackupOwnerId() == null || !"ACTIVE".equals(asset.getBackupOwnerStatus())));
    }

    public void applyAll(List<LabAsset> assets) {
        for (LabAsset asset : assets) apply(asset);
    }
}
