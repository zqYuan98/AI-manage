package com.ailab.system.dto;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.domain.LabOne2One;
import com.ailab.system.domain.LabTask;
import java.util.ArrayList;
import java.util.List;

/** Aggregate tailored for the member detail drawer. */
public class LabMemberDetail {
    private LabMember member;
    private List<LabMemberSkill> skillMatrix = new ArrayList<LabMemberSkill>();
    private List<LabAsset> assets = new ArrayList<LabAsset>();
    private List<LabTask> recentTasks = new ArrayList<LabTask>();
    private List<LabOne2One> oneToOnes = new ArrayList<LabOne2One>();
    public LabMember getMember(){return member;} public void setMember(LabMember v){member=v;}
    public List<LabMemberSkill> getSkillMatrix(){return skillMatrix;} public void setSkillMatrix(List<LabMemberSkill> v){skillMatrix=v;}
    public List<LabAsset> getAssets(){return assets;} public void setAssets(List<LabAsset> v){assets=v;}
    public List<LabTask> getRecentTasks(){return recentTasks;} public void setRecentTasks(List<LabTask> v){recentTasks=v;}
    public List<LabOne2One> getOneToOnes(){return oneToOnes;} public void setOneToOnes(List<LabOne2One> v){oneToOnes=v;}
}
