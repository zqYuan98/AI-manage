package com.ailab.system.service.impl;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabIpr;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabOne2One;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabLedgerMapper;
import com.ailab.system.mapper.LabMemberMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabLedgerService;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabLedgerServiceImpl implements LabLedgerService {
    private static final Map<String,Integer> IPR_STAGE_ORDER = stageOrder();
    private final LabLedgerMapper ledgerMapper;
    private final LabMemberMapper memberMapper;
    private final LabAccessService accessService;
    private final Clock clock;

    public LabLedgerServiceImpl(LabLedgerMapper ledgerMapper, LabMemberMapper memberMapper,
            LabAccessService accessService) {
        this(ledgerMapper, memberMapper, accessService, Clock.systemDefaultZone());
    }

    public LabLedgerServiceImpl(LabLedgerMapper ledgerMapper, LabMemberMapper memberMapper,
            LabAccessService accessService, Clock clock) {
        this.ledgerMapper=ledgerMapper; this.memberMapper=memberMapper; this.accessService=accessService; this.clock=clock;
    }

    @Override
    public List<LabAsset> listAssets(LabAsset query, Long actorId) {
        accessService.context(actorId);
        List<LabAsset> rows=ledgerMapper.selectAssetList(query==null?new LabAsset():query);
        for(LabAsset row:rows) deriveRisk(row);
        return rows;
    }

    @Override
    public List<LabAsset> listAssetRisks(LabAsset query, Long actorId) {
        List<LabAsset> risks=new ArrayList<LabAsset>();
        for(LabAsset row:listAssets(query,actorId)) if(row.isSinglePointRisk()) risks.add(row);
        return risks;
    }

    @Override
    public LabAsset getAsset(Long id, Long actorId) {
        accessService.context(actorId); LabAsset asset=requireAsset(ledgerMapper.selectAssetById(id)); deriveRisk(asset); return asset;
    }

    @Override
    @Transactional
    public int createAsset(LabAsset asset, Long actorId) {
        requireAssetInput(asset);
        applyAssetDefaults(asset);
        if(same(asset.getPrimaryOwnerId(),asset.getBackupOwnerId()))throw new ServiceException("Primary and backup owners must differ");
        LabAccessContext actor=accessService.context(actorId);
        List<LabMember> owners=lockActiveMembers(asset.getPrimaryOwnerId(),asset.getBackupOwnerId());
        LabMember primary=find(owners,asset.getPrimaryOwnerId());
        requireAssetWrite(actor,primary);
        if(asset.getBackupOwnerId()!=null)asset.setBackupOwnerStatus(find(owners,asset.getBackupOwnerId()).getMemberStatus());
        deriveRisk(asset); asset.setVersion(0); asset.setDelFlag("0"); asset.setCreateBy(actor(actorId));
        return requireAffected(ledgerMapper.insertAsset(asset),"Asset was not created");
    }

    @Override
    @Transactional
    public int updateAsset(LabAsset asset, Long actorId) {
        requireAssetInput(asset); requireIdVersion(asset.getId(),asset.getVersion());
        applyAssetDefaults(asset);
        if(same(asset.getPrimaryOwnerId(),asset.getBackupOwnerId()))throw new ServiceException("Primary and backup owners must differ");
        LabAccessContext actor=accessService.context(actorId);
        LabAsset current=requireAsset(ledgerMapper.selectAssetForUpdate(asset.getId()));
        if(!same(current.getVersion(),asset.getVersion()))throw new ServiceException("Asset changed concurrently");
        List<LabMember> owners=lockMembers(current.getPrimaryOwnerId(),asset.getPrimaryOwnerId(),asset.getBackupOwnerId());
        LabMember currentOwner=find(owners,current.getPrimaryOwnerId());
        requireAssetWrite(actor,currentOwner);
        requireAssetWrite(actor,requireActive(find(owners,asset.getPrimaryOwnerId())));
        if(asset.getBackupOwnerId()!=null)asset.setBackupOwnerStatus(requireActive(find(owners,asset.getBackupOwnerId())).getMemberStatus());
        deriveRisk(asset); asset.setUpdateBy(actor(actorId));
        return requireAffected(ledgerMapper.updateAsset(asset),"Asset changed concurrently");
    }

    @Override
    @Transactional
    public int deactivateAsset(Long id,Integer version,Long actorId){
        requireIdVersion(id,version); LabAccessContext actor=accessService.context(actorId);
        LabAsset current=requireAsset(ledgerMapper.selectAssetForUpdate(id));
        if(!same(current.getVersion(),version))throw new ServiceException("Asset changed concurrently");
        requireAssetWrite(actor,requireMember(memberMapper.selectMemberForUpdate(current.getPrimaryOwnerId())));
        return requireAffected(ledgerMapper.deleteAsset(id,version,actor(actorId)),"Asset changed concurrently");
    }

    @Override
    public List<LabOne2One> listOne2Ones(LabOne2One query,Long actorId){
        LabAccessContext actor=accessService.context(actorId); LabOne2One safe=query==null?new LabOne2One():query;
        if(!isManager(actor))safe.setMemberId(actor.getMemberId());
        return ledgerMapper.selectOne2OneList(safe);
    }

    @Override
    public LabOne2One getOne2One(Long id,Long actorId){
        LabAccessContext actor=accessService.context(actorId); LabOne2One record=requireOne2One(ledgerMapper.selectOne2OneById(id));
        if(!isManager(actor)&&!same(actor.getMemberId(),record.getMemberId()))throw new ServiceException("One-to-one record is private to its talk subject");
        return record;
    }

    @Override
    @Transactional
    public int createOne2One(LabOne2One record,Long actorId){
        LabAccessContext actor=accessService.context(actorId); requireManager(actor); requireOne2OneInput(record);
        lockActiveMembers(record.getMemberId(),record.getLeaderId());
        if(blank(record.getStatus()))record.setStatus("OPEN");
        record.setVersion(0);record.setDelFlag("0");record.setCreateBy(actor(actorId));
        return requireAffected(ledgerMapper.insertOne2One(record),"One-to-one record was not created");
    }

    @Override
    @Transactional
    public int updateOne2One(LabOne2One record,Long actorId){
        LabAccessContext actor=accessService.context(actorId); requireManager(actor); requireOne2OneInput(record); requireIdVersion(record.getId(),record.getVersion());
        LabOne2One current=requireOne2One(ledgerMapper.selectOne2OneForUpdate(record.getId()));
        if(!same(current.getVersion(),record.getVersion()))throw new ServiceException("One-to-one record changed concurrently");
        List<LabMember> participants=lockMembers(current.getMemberId(),current.getLeaderId(),record.getMemberId(),record.getLeaderId());
        requireActive(find(participants,record.getMemberId()));requireActive(find(participants,record.getLeaderId()));
        if(blank(record.getStatus()))record.setStatus(current.getStatus()); record.setUpdateBy(actor(actorId));
        return requireAffected(ledgerMapper.updateOne2One(record),"One-to-one record changed concurrently");
    }

    @Override
    public List<LabIpr> listIprs(LabIpr query,Long actorId){accessService.context(actorId);return ledgerMapper.selectIprList(query==null?new LabIpr():query);}

    @Override
    public LabIpr getIpr(Long id,Long actorId){accessService.context(actorId);return requireIpr(ledgerMapper.selectIprById(id));}

    @Override
    @Transactional
    public int createIpr(LabIpr ipr,Long actorId){
        requireIprInput(ipr); LabAccessContext actor=accessService.context(actorId);
        LabMember owner=find(lockActiveMembers(ipr.getOwnerId()),ipr.getOwnerId()); requireOwnerWrite(actor,owner);
        validateIpr(ipr); ipr.setStageChangeReason(null);ipr.setVersion(0);ipr.setDelFlag("0");ipr.setStatus(blank(ipr.getStatus())?"ACTIVE":ipr.getStatus());ipr.setCreateBy(actor(actorId));
        return requireAffected(ledgerMapper.insertIpr(ipr),"IPR record was not created");
    }

    @Override
    @Transactional
    public int updateIpr(LabIpr ipr,String rollbackReason,Long actorId){
        requireIprInput(ipr);requireIdVersion(ipr.getId(),ipr.getVersion());LabAccessContext actor=accessService.context(actorId);
        LabIpr current=requireIpr(ledgerMapper.selectIprForUpdate(ipr.getId()));
        if(!same(current.getVersion(),ipr.getVersion()))throw new ServiceException("IPR record changed concurrently");
        List<LabMember> owners=lockMembers(current.getOwnerId(),ipr.getOwnerId());
        requireOwnerWrite(actor,find(owners,current.getOwnerId()));
        requireOwnerWrite(actor,requireActive(find(owners,ipr.getOwnerId())));
        if(stage(ipr.getIprStage())<stage(current.getIprStage())){
            if(!isManager(actor)||blank(rollbackReason))throw new ServiceException("IPR stage rollback requires a manager and a reason");
            ipr.setStageChangeReason(rollbackReason.trim());
        } else ipr.setStageChangeReason(current.getStageChangeReason());
        if(blank(ipr.getStatus()))ipr.setStatus(current.getStatus());
        validateIpr(ipr);ipr.setUpdateBy(actor(actorId));
        return requireAffected(ledgerMapper.updateIpr(ipr),"IPR record changed concurrently");
    }

    @Override
    @Transactional
    public int deactivateIpr(Long id,Integer version,Long actorId){
        requireIdVersion(id,version);LabAccessContext actor=accessService.context(actorId);LabIpr current=requireIpr(ledgerMapper.selectIprForUpdate(id));
        if(!same(current.getVersion(),version))throw new ServiceException("IPR record changed concurrently");
        requireOwnerWrite(actor,requireMember(memberMapper.selectMemberForUpdate(current.getOwnerId())));
        return requireAffected(ledgerMapper.deleteIpr(id,version,actor(actorId)),"IPR record changed concurrently");
    }

    private void validateIpr(LabIpr ipr){
        int stage=stage(ipr.getIprStage());
        if(stage>=1&&ipr.getActualSubmitDate()==null)throw new ServiceException("Submitted or later IPR requires actual submit date");
        if(ipr.getActualSubmitDate()!=null&&toLocalDate(ipr.getActualSubmitDate()).isAfter(LocalDate.now(clock)))throw new ServiceException("Actual submit date cannot be in the future");
        if(stage>=2&&blank(ipr.getAcceptanceNo()))throw new ServiceException("Accepted or later IPR requires an acceptance number");
        if(stage>=3&&blank(ipr.getCertificateNo()))throw new ServiceException("Authorized or completed IPR requires a certificate number");
    }

    private List<LabMember> lockActiveMembers(Long...ids){
        List<LabMember> rows=lockMembers(ids);
        for(LabMember row:rows)requireActive(row);
        return rows;
    }
    private List<LabMember> lockMembers(Long...ids){
        List<Long> sorted=new ArrayList<Long>();for(Long id:ids)if(id!=null&&!sorted.contains(id))sorted.add(id);Collections.sort(sorted);
        if(sorted.isEmpty())throw new ServiceException("At least one owner is required");
        List<LabMember> rows=memberMapper.lockMembersForUpdate(sorted);
        if(rows==null||rows.size()!=sorted.size())throw new ServiceException("Every referenced member must exist");
        return rows;
    }
    private LabMember requireActive(LabMember member){if(!"ACTIVE".equals(member.getMemberStatus()))throw new ServiceException("Every referenced member must be active");return member;}
    private LabMember find(List<LabMember> rows,Long id){for(LabMember row:rows)if(same(row.getId(),id))return row;throw new ServiceException("Referenced member does not exist");}
    private void requireAssetWrite(LabAccessContext actor,LabMember owner){
        if(isManager(actor))return;
        if(same(actor.getMemberId(),owner.getId()))return;
        if(isLead(actor)&&same(actor.getBizLine(),owner.getBizLine()))return;
        throw new ServiceException("Asset is outside the authenticated actor's write scope");
    }
    private void requireOwnerWrite(LabAccessContext actor,LabMember owner){requireAssetWrite(actor,owner);}
    private void deriveRisk(LabAsset a){boolean inUse="ACTIVE".equals(a.getStatus())&&("DEPLOYED".equals(a.getAssetStage())||"ACCEPTED".equals(a.getAssetStage()));boolean important="1".equals(a.getCriticalFlag())||inUse;a.setSinglePointRisk(important&&(a.getBackupOwnerId()==null||!"ACTIVE".equals(a.getBackupOwnerStatus())));}
    private void applyAssetDefaults(LabAsset a){if(blank(a.getAssetVersion()))a.setAssetVersion("");if(blank(a.getAssetStage()))a.setAssetStage("VERIFYING");if(blank(a.getStatus()))a.setStatus("ACTIVE");if(blank(a.getCriticalFlag()))a.setCriticalFlag("0");if(a.getReuseCount()==null)a.setReuseCount(0);}
    private void requireManager(LabAccessContext actor){if(!isManager(actor))throw new ServiceException("Manager role is required");}
    private boolean isManager(LabAccessContext c){return LabAccessServiceImpl.MANAGER.equals(c.getRoleKey());}
    private boolean isLead(LabAccessContext c){return LabAccessServiceImpl.LEAD.equals(c.getRoleKey());}
    private void requireAssetInput(LabAsset a){if(a==null||blank(a.getAssetNo())||blank(a.getAssetName())||blank(a.getAssetType())||a.getPrimaryOwnerId()==null)throw new ServiceException("Asset number, name, type and primary owner are required");}
    private void requireOne2OneInput(LabOne2One r){if(r==null||r.getMemberId()==null||r.getLeaderId()==null||r.getMeetingDate()==null)throw new ServiceException("Member, manager and meeting date are required");if(same(r.getMemberId(),r.getLeaderId()))throw new ServiceException("Talk subject and manager must differ");}
    private void requireIprInput(LabIpr i){if(i==null||blank(i.getIprNo())||blank(i.getIprName())||blank(i.getIprType())||blank(i.getIprStage())||i.getOwnerId()==null)throw new ServiceException("IPR number, name, type, stage and owner are required");stage(i.getIprStage());}
    private int stage(String name){Integer value=IPR_STAGE_ORDER.get(name);if(value==null)throw new ServiceException("Unsupported IPR stage");return value;}
    private static Map<String,Integer> stageOrder(){Map<String,Integer> m=new HashMap<String,Integer>();m.put("DRAFTING",0);m.put("SUBMITTED",1);m.put("ACCEPTED",2);m.put("AUTHORIZED",3);m.put("COMPLETED",3);return m;}
    private LocalDate toLocalDate(Date date){return date.toInstant().atZone(clock.getZone()).toLocalDate();}
    private LabAsset requireAsset(LabAsset v){if(v==null)throw new ServiceException("Asset does not exist");return v;}
    private LabMember requireMember(LabMember v){if(v==null)throw new ServiceException("Member does not exist");return v;}
    private LabOne2One requireOne2One(LabOne2One v){if(v==null)throw new ServiceException("One-to-one record does not exist");return v;}
    private LabIpr requireIpr(LabIpr v){if(v==null)throw new ServiceException("IPR record does not exist");return v;}
    private void requireIdVersion(Long id,Integer version){if(id==null||version==null)throw new ServiceException("Id and version are required");}
    private int requireAffected(int count,String message){if(count!=1)throw new ServiceException(message);return count;}
    private String actor(Long id){return String.valueOf(id);}
    private boolean same(Object a,Object b){return a==null?b==null:a.equals(b);}
    private boolean blank(String v){return v==null||v.trim().isEmpty();}
}
