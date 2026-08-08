package com.ailab.system.service.impl;

import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.service.LabReportTempFileEligibility;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Fail-closed lifecycle check for temporary directories owned by known report instances. */
@Component
public final class LabReportTempFileEligibilityImpl implements LabReportTempFileEligibility {
    private final LabReportMapper mapper;
    public LabReportTempFileEligibilityImpl(LabReportMapper mapper){this.mapper=mapper;}
    @Override public boolean isDeletionEligible(Path relativePath){
        if(relativePath==null||relativePath.isAbsolute()||relativePath.normalize().startsWith("..")||relativePath.getNameCount()<2)return false;
        String owner=relativePath.getName(0).toString();if(!owner.matches("report-[1-9][0-9]*"))return false;
        try{Long reportId=Long.valueOf(owner.substring(7));return mapper.selectReportById(reportId)!=null&&mapper.countActiveReportJobs(reportId)==0&&mapper.countTerminalReportJobs(reportId)>0;}
        catch(RuntimeException ex){return false;}
    }
}
