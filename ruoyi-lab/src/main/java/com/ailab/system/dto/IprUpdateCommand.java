package com.ailab.system.dto;

import com.ailab.system.domain.LabIpr;

public class IprUpdateCommand {
    private LabIpr ipr;
    private String rollbackReason;
    public LabIpr getIpr(){return ipr;} public void setIpr(LabIpr v){ipr=v;}
    public String getRollbackReason(){return rollbackReason;} public void setRollbackReason(String v){rollbackReason=v;}
}
