package com.ailab.system.service.impl;

import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.service.LabReportTempFileEligibility;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Fail-closed lifecycle check for temporary directories owned by known report instances. */
@Component
public final class LabReportTempFileEligibilityImpl implements LabReportTempFileEligibility {
    private static final java.util.regex.Pattern OWNER=java.util.regex.Pattern.compile("lo-report-([1-9][0-9]*)-job-([1-9][0-9]*)-run-[A-Za-z0-9_-]{16,128}-[A-Za-z0-9]+$");
    private final LabReportMapper mapper;
    public LabReportTempFileEligibilityImpl(LabReportMapper mapper){this.mapper=mapper;}
    @Override public boolean isDeletionEligible(Path relativePath){
        if(relativePath==null||relativePath.isAbsolute()||relativePath.normalize().startsWith("..")||relativePath.getNameCount()<1)return false;
        java.util.regex.Matcher owner=OWNER.matcher(relativePath.getName(0).toString());if(!owner.matches())return false;
        try{Long reportId=Long.valueOf(owner.group(1));Long jobId=Long.valueOf(owner.group(2));LabReportJob job=mapper.selectReportJobById(jobId);return mapper.selectReportById(reportId)!=null&&job!=null&&reportId.equals(job.getReportId())&&("SUCCESS".equals(job.getJobStatus())||"FAILED".equals(job.getJobStatus()))&&mapper.countActiveReportJobs(reportId)==0;}
        catch(RuntimeException ex){return false;}
    }
}
