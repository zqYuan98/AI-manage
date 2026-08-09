const entry = (label, description, nextAction, color = '#6b7280', riskLevel = 'NORMAL') =>
  Object.freeze({ label, description, nextAction, color, riskLevel })

export const LAB_STATUS_CATALOG = Object.freeze({
  TASK_WORKFLOW: Object.freeze({
    DRAFT: entry('草稿', '任务尚未进入执行。', '完善并激活'),
    ACTIVE: entry('进行中', '任务已进入执行阶段。', '更新执行情况', '#0b7d75'),
    PENDING_REVIEW: entry('待验收', '成员已提交结果，等待负责人验收。', '完成验收', '#b7791f', 'WARNING'),
    CONFIRMED: entry('已确认', '结果已完成验收并固化。', '查看正式事实', '#2563a8')
  }),
  EXECUTION: Object.freeze({
    PLANNED: entry('待启动', '周承诺尚未开始执行。', '开始执行'),
    ACTIVE: entry('执行中', '周承诺正在执行。', '更新执行事实', '#0b7d75'),
    SELF_DONE: entry('成员已完成', '成员已自报完成，尚未形成正式验收事实。', '查看完成事实', '#0b7d75'),
    SELF_UNDONE: entry('本周未完成', '成员已确认本周未完成。', '填写下一步', '#b45309', 'WARNING'),
    POSTPONED: entry('已顺延', '承诺已顺延到后续周期。', '确认新周期', '#b7791f', 'WARNING'),
    CANCELLED: entry('已取消', '承诺已取消并保留历史。', '查看取消原因')
  }),
  RESULT: Object.freeze({
    DOING: entry('进行中', '结果尚未形成。', '继续执行', '#0b7d75'),
    EXCEEDED: entry('超额完成', '结果超过原定承诺。', '确认成果', '#047857'),
    ONTIME: entry('按时完成', '结果按期完成。', '确认成果', '#0b7d75'),
    DELAYED: entry('延期完成', '结果已完成但晚于计划。', '复盘延期原因', '#b7791f', 'WARNING'),
    UNDONE: entry('未完成', '结果未达到本期承诺。', '确认原因和下一步', '#b42318', 'HIGH')
  }),
  PERFORMANCE: Object.freeze({
    NORMAL: entry('正常', '本期绩效未触发红线。', '查看评分依据', '#0b7d75'),
    PENDING: entry('待确认', '评分等待成员确认。', '确认反馈', '#b7791f', 'WARNING'),
    CONFIRMED: entry('已确认', '评分已由成员确认。', '查看评分明细', '#2563a8'),
    RED_LINE: entry('触发红线', '本期出现必须处理的绩效红线。', '查看红线原因', '#b42318', 'CRITICAL')
  }),
  REPORT: Object.freeze({
    DRAFT: entry('草稿', '报告仍可编辑。', '完善报告'),
    QUEUED: entry('排队中', '报告生成任务已排队。', '等待生成', '#b7791f'),
    GENERATING: entry('生成中', '报告制品正在生成。', '查看生成进度', '#2563a8'),
    FINALIZED: entry('已定稿', '报告已固化为正式归档。', '下载归档', '#0b7d75'),
    SUPERSEDED: entry('历史版本', '报告已被更新版本替代。', '查看版本历史')
  }),
  ARTIFACT: Object.freeze({
    NOT_REQUESTED: entry('未生成', '尚未请求生成该制品。', '开始生成'),
    PENDING: entry('待生成', '制品正在等待处理。', '等待生成', '#b7791f'),
    RUNNING: entry('生成中', '制品正在生成。', '查看进度', '#2563a8'),
    SUCCESS: entry('已生成', '制品已完成并通过校验。', '下载制品', '#0b7d75'),
    FAILED: entry('生成失败', '制品生成未完成。', '查看原因并重试', '#b42318', 'HIGH')
  }),
  REMINDER: Object.freeze({
    INFO: entry('普通提醒', '需要关注但不紧急。', '查看提醒', '#2563a8'),
    WARNING: entry('重要提醒', '存在需要及时处理的事项。', '立即处理', '#b7791f', 'WARNING'),
    CRITICAL: entry('紧急提醒', '存在影响目标或交付的紧急风险。', '立即处理', '#b42318', 'CRITICAL')
  }),
  BIZ_LINE: Object.freeze({
    algorithm: entry('算法研发', '算法与模型方向。', '查看业务线', '#2563a8'),
    platform: entry('平台研发', '平台与模型服务方向。', '查看业务线', '#0b7d75'),
    hardware: entry('硬件研发', '设备与加速器方向。', '查看业务线', '#b7791f'),
    engineering: entry('工程研发', '平台与工程交付方向。', '查看业务线', '#0b7d75'),
    manage: entry('部门管理', '部门经营与综合管理。', '查看业务线'),
    ALL: entry('全部业务线', '当前范围覆盖全部业务线。', '查看全部')
  }),
  ROLE: Object.freeze({
    LAB_MANAGER: entry('部门负责人', '负责部门目标、资源与验收。', '进入管理工作台', '#312e81'),
    LAB_LEAD: entry('业务线负责人', '负责本业务线目标与协同。', '进入业务线工作台', '#2563a8'),
    LAB_MEMBER: entry('成员', '负责个人承诺与执行反馈。', '进入个人工作台', '#0b7d75')
  }),
  PERIOD: Object.freeze({ MONTH: entry('月度', '按自然月管理。', '选择月份'), WEEK: entry('周度', '按 ISO 周管理。', '选择周次'), QUARTER: entry('季度', '按自然季度管理。', '选择季度'), YEAR: entry('年度', '按自然年度管理。', '选择年度') }),
  TEMPLATE: Object.freeze({ ENABLED: entry('启用', '模板可用于生成报告。', '使用模板', '#0b7d75'), DISABLED: entry('停用', '模板不可用于新报告。', '查看历史') })
})

export function statusDescriptor(domain, code) {
  const values = LAB_STATUS_CATALOG[String(domain || '').toUpperCase()] || {}
  const raw = String(code == null ? '' : code).trim()
  const key = String(domain || '').toUpperCase() === 'BIZ_LINE' ? raw : raw.toUpperCase()
  return values[key] || entry('未定义状态', '系统尚未配置该状态的业务含义。', '联系管理员确认')
}

export function statusLabel(domain, code) {
  if (code === null || code === undefined || code === '') return '—'
  return statusDescriptor(domain, code).label
}

export function bizLineLabel(code) {
  return statusLabel('BIZ_LINE', code)
}
