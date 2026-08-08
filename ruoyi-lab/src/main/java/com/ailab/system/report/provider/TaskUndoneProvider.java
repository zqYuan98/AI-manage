package com.ailab.system.report.provider;
import com.ailab.system.report.config.*; import com.ailab.system.report.model.*; import java.util.*; import org.springframework.stereotype.Component;
@Component public final class TaskUndoneProvider extends AbstractLabDataSourceProvider { public TaskUndoneProvider(){super(ReportConfigCatalog.TASK_UNDONE,TaskDetailProvider.fields());} protected ReportSectionData loadValidated(ReportQueryCriteria c,ReportSectionConfig s){List<Map<String,Object>> r=copyRows(mapper().selectUndoneTasks(c));return section(c,s,r,summaryCount(r));}}
