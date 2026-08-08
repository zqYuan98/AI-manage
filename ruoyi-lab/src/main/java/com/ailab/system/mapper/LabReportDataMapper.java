package com.ailab.system.mapper;

import com.ailab.system.report.model.ReportQueryCriteria;
import java.util.List;
import java.util.Map;

/** Read-only report projections.  Each statement receives an explicit ReportAccessScope. */
public interface LabReportDataMapper {
    List<Map<String, Object>> selectGoalProgress(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectTasks(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectUndoneTasks(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectNextTasks(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectCoordinationTasks(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectBlockedTasks(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectTaskStats(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectAssets(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectIprs(ReportQueryCriteria criteria);
    List<Map<String, Object>> selectCurrentPerfScores(ReportQueryCriteria criteria);
}
