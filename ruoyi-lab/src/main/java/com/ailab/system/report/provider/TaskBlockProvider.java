package com.ailab.system.report.provider;
import com.ailab.system.report.config.*; import com.ailab.system.report.model.*; import java.util.*; import org.springframework.stereotype.Component;
@Component public final class TaskBlockProvider extends AbstractLabDataSourceProvider { public TaskBlockProvider(){super(ReportConfigCatalog.TASK_BLOCK,TaskDetailProvider.fields());} protected ReportSectionData loadValidated(ReportQueryCriteria c,ReportSectionConfig s){List<Map<String,Object>> r=copyRows(mapper().selectBlockedTasks(c));return section(c,s,r,summaryCount(r));}}
