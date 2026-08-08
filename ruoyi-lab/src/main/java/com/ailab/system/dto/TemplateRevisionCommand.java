package com.ailab.system.dto;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import java.util.List;

public class TemplateRevisionCommand {
    private Long sourceTemplateId; private LabReportTemplate template; private List<LabReportSection> sections;
    private Boolean saveAsNewFamily; private Integer expectedVersion;
    public Long getSourceTemplateId(){return sourceTemplateId;} public void setSourceTemplateId(Long v){sourceTemplateId=v;}
    public LabReportTemplate getTemplate(){return template;} public void setTemplate(LabReportTemplate v){template=v;}
    public List<LabReportSection> getSections(){return sections;} public void setSections(List<LabReportSection> v){sections=v;}
    public Boolean getSaveAsNewFamily(){return saveAsNewFamily;} public void setSaveAsNewFamily(Boolean v){saveAsNewFamily=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
