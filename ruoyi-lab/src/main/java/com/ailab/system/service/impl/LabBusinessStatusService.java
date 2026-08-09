package com.ailab.system.service.impl;

import com.ailab.system.dto.BusinessStatusDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Central Chinese business language catalog. Internal codes remain stable. */
@Service
public class LabBusinessStatusService {
    private static final Map<String, Map<String, BusinessStatusDescriptor>> CATALOG = buildCatalog();

    public BusinessStatusDescriptor describe(String domain, String code) {
        String safeDomain = normalize(domain);
        String safeCode = code == null ? "" : code.trim();
        Map<String, BusinessStatusDescriptor> values = CATALOG.get(safeDomain);
        BusinessStatusDescriptor value = values == null ? null : values.get(key(safeDomain, safeCode));
        if (value != null) return value;
        return descriptor(safeCode, "未定义状态", "系统尚未配置该状态的业务含义。", "联系管理员确认", "#6b7280", "UNKNOWN");
    }

    public Map<String, Map<String, BusinessStatusDescriptor>> catalog() {
        return CATALOG;
    }

    private static Map<String, Map<String, BusinessStatusDescriptor>> buildCatalog() {
        Map<String, Map<String, BusinessStatusDescriptor>> catalog = new LinkedHashMap<>();
        add(catalog, "TASK_WORKFLOW", "DRAFT", "草稿", "任务尚未进入执行。", "完善并激活", "#6b7280", "NORMAL");
        add(catalog, "TASK_WORKFLOW", "ACTIVE", "进行中", "任务已进入执行阶段。", "更新执行情况", "#0b7d75", "NORMAL");
        add(catalog, "TASK_WORKFLOW", "PENDING_REVIEW", "待验收", "成员已提交结果，等待负责人验收。", "完成验收", "#b7791f", "WARNING");
        add(catalog, "TASK_WORKFLOW", "CONFIRMED", "已确认", "结果已完成验收并固化。", "查看正式事实", "#2563a8", "NORMAL");

        add(catalog, "EXECUTION", "PLANNED", "待启动", "周承诺尚未开始执行。", "开始执行", "#6b7280", "NORMAL");
        add(catalog, "EXECUTION", "ACTIVE", "执行中", "周承诺正在执行。", "更新执行事实", "#0b7d75", "NORMAL");
        add(catalog, "EXECUTION", "SELF_DONE", "成员已完成", "成员已自报完成，尚未形成正式验收事实。", "查看完成事实", "#0b7d75", "NORMAL");
        add(catalog, "EXECUTION", "SELF_UNDONE", "本周未完成", "成员已确认本周未完成。", "填写下一步", "#b45309", "WARNING");
        add(catalog, "EXECUTION", "POSTPONED", "已顺延", "承诺已顺延到后续周期。", "确认新周期", "#b7791f", "WARNING");
        add(catalog, "EXECUTION", "CANCELLED", "已取消", "承诺已取消并保留历史。", "查看取消原因", "#6b7280", "NORMAL");

        add(catalog, "RESULT", "DOING", "进行中", "结果尚未形成。", "继续执行", "#0b7d75", "NORMAL");
        add(catalog, "RESULT", "EXCEEDED", "超额完成", "结果超过原定承诺。", "确认成果", "#047857", "NORMAL");
        add(catalog, "RESULT", "ONTIME", "按时完成", "结果按期完成。", "确认成果", "#0b7d75", "NORMAL");
        add(catalog, "RESULT", "DELAYED", "延期完成", "结果已完成但晚于计划。", "复盘延期原因", "#b7791f", "WARNING");
        add(catalog, "RESULT", "UNDONE", "未完成", "结果未达到本期承诺。", "确认原因和下一步", "#b42318", "HIGH");

        add(catalog, "PERFORMANCE", "NORMAL", "正常", "本期绩效未触发红线。", "查看评分依据", "#0b7d75", "NORMAL");
        add(catalog, "PERFORMANCE", "PENDING", "待确认", "评分等待成员确认。", "确认反馈", "#b7791f", "WARNING");
        add(catalog, "PERFORMANCE", "CONFIRMED", "已确认", "评分已由成员确认。", "查看评分明细", "#2563a8", "NORMAL");
        add(catalog, "PERFORMANCE", "RED_LINE", "触发红线", "本期出现必须处理的绩效红线。", "查看红线原因", "#b42318", "CRITICAL");

        add(catalog, "REPORT", "DRAFT", "草稿", "报告仍可编辑。", "完善报告", "#6b7280", "NORMAL");
        add(catalog, "REPORT", "QUEUED", "排队中", "报告生成任务已排队。", "等待生成", "#b7791f", "NORMAL");
        add(catalog, "REPORT", "GENERATING", "生成中", "报告制品正在生成。", "查看生成进度", "#2563a8", "NORMAL");
        add(catalog, "REPORT", "FINALIZED", "已定稿", "报告已固化为正式归档。", "下载归档", "#0b7d75", "NORMAL");
        add(catalog, "REPORT", "SUPERSEDED", "历史版本", "报告已被更新版本替代。", "查看版本历史", "#6b7280", "NORMAL");

        add(catalog, "ARTIFACT", "NOT_REQUESTED", "未生成", "尚未请求生成该制品。", "开始生成", "#6b7280", "NORMAL");
        add(catalog, "ARTIFACT", "PENDING", "待生成", "制品正在等待处理。", "等待生成", "#b7791f", "NORMAL");
        add(catalog, "ARTIFACT", "RUNNING", "生成中", "制品正在生成。", "查看进度", "#2563a8", "NORMAL");
        add(catalog, "ARTIFACT", "SUCCESS", "已生成", "制品已完成并通过校验。", "下载制品", "#0b7d75", "NORMAL");
        add(catalog, "ARTIFACT", "FAILED", "生成失败", "制品生成未完成。", "查看原因并重试", "#b42318", "HIGH");

        add(catalog, "REMINDER", "INFO", "普通提醒", "需要关注但不紧急。", "查看提醒", "#2563a8", "NORMAL");
        add(catalog, "REMINDER", "WARNING", "重要提醒", "存在需要及时处理的事项。", "立即处理", "#b7791f", "WARNING");
        add(catalog, "REMINDER", "CRITICAL", "紧急提醒", "存在影响目标或交付的紧急风险。", "立即处理", "#b42318", "CRITICAL");

        add(catalog, "BIZ_LINE", "algorithm", "算法研发", "算法与模型方向。", "查看业务线", "#2563a8", "NORMAL");
        add(catalog, "BIZ_LINE", "platform", "平台研发", "平台与模型服务方向。", "查看业务线", "#0b7d75", "NORMAL");
        add(catalog, "BIZ_LINE", "hardware", "硬件研发", "设备与加速器方向。", "查看业务线", "#b7791f", "NORMAL");
        add(catalog, "BIZ_LINE", "engineering", "工程研发", "平台与工程交付方向。", "查看业务线", "#0b7d75", "NORMAL");
        add(catalog, "BIZ_LINE", "manage", "部门管理", "部门经营与综合管理。", "查看业务线", "#6b7280", "NORMAL");
        add(catalog, "BIZ_LINE", "ALL", "全部业务线", "当前范围覆盖全部业务线。", "查看全部", "#6b7280", "NORMAL");

        add(catalog, "ROLE", "lab_manager", "部门负责人", "负责部门目标、资源与验收。", "进入管理工作台", "#312e81", "NORMAL");
        add(catalog, "ROLE", "lab_lead", "业务线负责人", "负责本业务线目标与协同。", "进入业务线工作台", "#2563a8", "NORMAL");
        add(catalog, "ROLE", "lab_member", "成员", "负责个人承诺与执行反馈。", "进入个人工作台", "#0b7d75", "NORMAL");

        add(catalog, "PERIOD", "MONTH", "月度", "按自然月管理。", "选择月份", "#2563a8", "NORMAL");
        add(catalog, "PERIOD", "WEEK", "周度", "按 ISO 周管理。", "选择周次", "#0b7d75", "NORMAL");
        add(catalog, "PERIOD", "QUARTER", "季度", "按自然季度管理。", "选择季度", "#b7791f", "NORMAL");
        add(catalog, "PERIOD", "YEAR", "年度", "按自然年度管理。", "选择年度", "#312e81", "NORMAL");
        add(catalog, "TEMPLATE", "ENABLED", "启用", "模板可用于生成报告。", "使用模板", "#0b7d75", "NORMAL");
        add(catalog, "TEMPLATE", "DISABLED", "停用", "模板不可用于新报告。", "查看历史", "#6b7280", "NORMAL");

        Map<String, Map<String, BusinessStatusDescriptor>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, BusinessStatusDescriptor>> entry : catalog.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Map<String, BusinessStatusDescriptor>> catalog, String domain, String code,
                            String label, String description, String nextAction, String color, String riskLevel) {
        catalog.computeIfAbsent(domain, ignored -> new LinkedHashMap<>())
                .put(key(domain, code), descriptor(code, label, description, nextAction, color, riskLevel));
    }

    private static BusinessStatusDescriptor descriptor(String code, String label, String description,
                                                       String nextAction, String color, String riskLevel) {
        return new BusinessStatusDescriptor(code, label, description, nextAction, color, riskLevel);
    }

    private static String key(String domain, String code) {
        return "BIZ_LINE".equals(domain) ? code : normalize(code);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
