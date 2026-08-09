<template>
  <main class="lab-dashboard">
    <section class="dashboard-hero">
      <div class="dashboard-hero__title">
        <span class="dashboard-hero__sequence">实验室 / 管理态势 / {{ selectedPeriod }}</span>
        <h1>{{ workbenchTitle }}</h1>
        <p>{{ workbenchDescription }}</p>
      </div>
      <div class="dashboard-hero__controls">
        <div class="dashboard-hero__scope">
          <i class="el-icon-lock" aria-hidden="true" />
          <span>权限范围 · 服务端裁剪</span>
        </div>
        <el-date-picker
          v-model="selectedPeriod"
          class="dashboard-hero__period"
          type="month"
          value-format="yyyy-MM"
          format="yyyy 年 MM 月"
          :clearable="false"
          aria-label="看板月份"
          @change="reload"
        />
        <el-button
          class="dashboard-hero__refresh"
          icon="el-icon-refresh"
          :loading="overviewLoading || workbenchLoading || reminderLoading"
          @click="reload"
        >
          刷新
        </el-button>
      </div>
      <div class="dashboard-hero__meta">
        <span><i class="el-icon-time" /> 最近更新 {{ latestUpdate }}</span>
        <span>数据口径随卡片可查 · 所有数字均可下钻</span>
      </div>
    </section>

    <div class="dashboard-body">
      <div v-if="workbenchError" class="dashboard-alert" role="alert">
        <span><i class="el-icon-warning-outline" /> 角色工作台加载失败，未用空数据掩盖异常。</span>
        <button type="button" @click="loadWorkbench">重新加载</button>
      </div>

      <section v-if="isManagementWorkbench" class="workbench-section" data-workbench-section="actions" aria-labelledby="today-actions-title">
        <header class="workbench-section__header">
          <div><span class="lab-eyebrow">行动优先</span><h2 id="today-actions-title">今日需要处理</h2></div>
          <span>{{ workbenchRole === 'manager' ? '部门范围' : '本业务线范围' }} · {{ todayActions.length }} 项</span>
        </header>
        <div class="workbench-action-grid">
          <action-queue title="待验收结果" eyebrow="验收" :items="workbench.pendingAcceptance" :loading="workbenchLoading" :error="workbenchError" empty-text="暂无待验收结果" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="新增阻塞" eyebrow="风险" :items="workbench.newBlocks" :loading="workbenchLoading" :error="workbenchError" empty-text="近七天没有新增阻塞" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="预计延期" eyebrow="预警" :items="workbench.forecastDelays" :loading="workbenchLoading" :error="workbenchError" empty-text="暂无预计延期承诺" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="待跟进决策" eyebrow="决策" :items="meetingDecisions" :loading="workbenchLoading" :error="workbenchError" empty-text="暂无待跟进决策" @drill="openDrill($event, 'decision')" @retry="loadWorkbench" />
        </div>
      </section>

      <section v-if="isMemberWorkbench" class="member-workbench" aria-labelledby="member-workbench-title">
        <header class="workbench-section__header">
          <div><span class="lab-eyebrow">个人闭环</span><h2 id="member-workbench-title">我的结果与承诺</h2></div>
          <span>只显示本人事实</span>
        </header>
        <div class="member-primary-actions" aria-label="个人主要操作">
          <button data-member-action="create-weekly" type="button" @click="openMemberAction('create-weekly')" @keydown.enter.prevent="openMemberAction('create-weekly')" @keydown.space.prevent="openMemberAction('create-weekly')"><i class="el-icon-plus" /><strong>新增本周承诺</strong><span>从月度结果拆出本周交付</span></button>
          <button data-member-action="report-block" type="button" @click="openMemberAction('report-block')" @keydown.enter.prevent="openMemberAction('report-block')" @keydown.space.prevent="openMemberAction('report-block')"><i class="el-icon-warning-outline" /><strong>报告阻塞</strong><span>为执行中的承诺登记阻塞</span></button>
          <button data-member-action="submit-result" type="button" @click="openMemberAction('submit-result')" @keydown.enter.prevent="openMemberAction('submit-result')" @keydown.space.prevent="openMemberAction('submit-result')"><i class="el-icon-finished" /><strong>提交交付结果</strong><span>完成或未完成都如实反馈</span></button>
        </div>
        <div class="member-workbench__grid">
          <action-queue title="本月结果" eyebrow="月度" :items="workbench.monthlyResults" :loading="workbenchLoading" :error="workbenchError" empty-text="本月尚无结果项" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="本周承诺" eyebrow="周度" :items="workbench.weeklyCommitments" :loading="workbenchLoading" :error="workbenchError" empty-text="本周尚无承诺" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="临期待办" eyebrow="截止日期" :items="workbench.dueItems" :loading="workbenchLoading" :error="workbenchError" empty-text="暂无临期待办" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="我的阻塞" eyebrow="风险" :items="workbench.blocks" :loading="workbenchLoading" :error="workbenchError" empty-text="当前没有阻塞" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue title="缺失证据" eyebrow="交付质量" :items="workbench.missingEvidence" :loading="workbenchLoading" :error="workbenchError" empty-text="当前没有缺失证据" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
          <action-queue
            title="我的提醒"
            eyebrow="消息"
            :items="reminders"
            :loading="reminderLoading"
            :error="reminderError"
            error-text="提醒列表暂时不可用"
            empty-text="没有未处理提醒"
            allow-mark-read
            @drill="openReminder"
            @mark-read="markReminderRead"
            @mark-all="markAllRemindersRead"
            @retry="loadReminders"
          />
        </div>
      </section>

      <template v-if="isManagementWorkbench">
        <section class="workbench-section" data-workbench-section="goals" aria-labelledby="goal-situation-title">
          <header class="workbench-section__header">
            <div><span class="lab-eyebrow">目标与事实</span><h2 id="goal-situation-title">本月目标态势</h2></div>
            <span>每项指标均可下钻到事实列表</span>
          </header>
          <section class="dashboard-kpis" aria-label="五项核心管理指标">
            <div v-if="overviewError" class="dashboard-kpis__error lab-panel">
              <i class="el-icon-warning-outline" />
              <span>核心指标暂时不可用</span>
              <button type="button" @click="loadOverview">重试</button>
            </div>
            <metric-card
              v-for="(metric, index) in overviewError ? [] : metricCards"
              :key="metric.code || index"
              :metric="metric"
              :index="index"
              :loading="overviewLoading"
              @drill="openDrill($event, 'task')"
            />
          </section>

          <div v-if="overviewError" class="dashboard-alert" role="alert">
            <span><i class="el-icon-warning-outline" /> 看板主体加载失败，各独立数据块仍可重试。</span>
            <button type="button" @click="loadOverview">重新加载</button>
          </div>

          <section class="dashboard-primary">
            <goal-health-chart
              :health="overview.goalHealth"
              :trend="overview.goalTrend"
              :loading="overviewLoading"
              :error="overviewError"
              @drill="openDrill($event, 'goal')"
              @retry="loadOverview"
            />

            <section class="status-ledger lab-panel" aria-labelledby="status-ledger-title">
              <header class="status-ledger__header">
                <div>
                  <span class="lab-eyebrow">任务全景</span>
                  <h2 id="status-ledger-title">任务状态分布</h2>
                </div>
                <strong>{{ taskTotal }}</strong>
              </header>
              <div v-if="overviewLoading" class="status-ledger__loading lab-skeleton" />
              <div v-else-if="overviewError" class="status-ledger__state">
                <span>任务状态暂时不可用</span>
                <button type="button" @click="loadOverview">重试</button>
              </div>
              <div v-else-if="!overview.taskStatusDistribution.length" class="lab-empty">暂无任务状态数据</div>
              <ol v-else class="status-ledger__list">
                <li v-for="item in overview.taskStatusDistribution" :key="item.code">
                  <button type="button" @click="openDrill(item, 'task')">
                    <span class="status-ledger__label">
                      <i :class="statusTone(item.code)" />
                      {{ statusLabel('TASK_WORKFLOW', item.code) }}
                    </span>
                    <strong>{{ item.count }}</strong>
                    <span class="status-ledger__track">
                      <i :style="{ width: statusWidth(item.count) }" :class="statusTone(item.code)" />
                    </span>
                  </button>
                </li>
              </ol>
              <div v-if="overview.performanceSummary.length" class="status-ledger__performance">
                <span>绩效快照</span>
                <button
                  v-for="item in overview.performanceSummary"
                  :key="item.code"
                  type="button"
                  @click="openDrill(item, 'perf')"
                >
                  {{ statusLabel('PERFORMANCE', item.code) }} <strong>{{ item.count }}</strong>
                </button>
              </div>
            </section>
          </section>
        </section>

        <section class="workbench-section" data-workbench-section="commitments" aria-labelledby="commitment-load-title">
          <header class="workbench-section__header">
            <div><span class="lab-eyebrow">团队执行</span><h2 id="commitment-load-title">团队承诺与负载</h2></div>
            <span>{{ teamCommitments.length }} 名成员</span>
          </header>
          <section class="dashboard-secondary">
            <member-load-matrix
              :members="teamCommitments"
              :loading="workbenchLoading"
              :error="workbenchError"
              @drill="openDrill($event, 'task')"
              @retry="loadWorkbench"
            />
            <action-queue
              title="我的提醒"
              eyebrow="我的待办"
              :items="reminders"
              :loading="reminderLoading"
              :error="reminderError"
              error-text="提醒列表暂时不可用"
              empty-text="没有未处理提醒"
              allow-mark-read
              @drill="openReminder"
              @mark-read="markReminderRead"
              @mark-all="markAllRemindersRead"
              @retry="loadReminders"
            />
          </section>
        </section>

        <section class="workbench-section" data-workbench-section="meeting" aria-labelledby="meeting-workspace-title">
          <header class="workbench-section__header">
            <div><span class="lab-eyebrow">闭环记录</span><h2 id="meeting-workspace-title">周会工作区</h2></div>
            <span>问题、决策、负责人、截止时间</span>
          </header>
          <section class="dashboard-actions" aria-label="周会行动">
            <action-queue title="待跟进决策" eyebrow="决策清单" :items="meetingDecisions" :loading="workbenchLoading" :error="workbenchError" empty-text="暂无待跟进决策" @drill="openDrill($event, 'decision')" @retry="loadWorkbench" />
            <action-queue title="长期未更新结果" eyebrow="结果复盘" :items="workbench.staleKeyResults" :loading="workbenchLoading" :error="workbenchError" empty-text="暂无长期未更新结果" @drill="openDrill($event, 'task')" @retry="loadWorkbench" />
            <action-queue
              title="协同待办"
              eyebrow="协同事项"
              :items="overview.coordinationItems"
              :loading="overviewLoading"
              :error="overviewError"
              empty-text="当前没有协同阻塞"
              @drill="openDrill($event, 'task')"
              @retry="loadOverview"
            />
          </section>
          <section class="dashboard-actions" aria-label="近期管理资料">
            <action-queue
              title="近期 IPR"
              eyebrow="知识产权"
              :items="overview.recentIpr"
              :loading="overviewLoading"
              :error="overviewError"
              empty-text="当前范围暂无近期 IPR"
              @drill="openDrill($event, 'ipr')"
              @retry="loadOverview"
            />
            <action-queue
              title="已定稿报告"
              eyebrow="报告归档"
              :items="overview.recentReports"
              :loading="overviewLoading"
              :error="overviewError"
              empty-text="当前范围暂无已定稿报告"
              @drill="openDrill($event, 'report')"
              @retry="loadOverview"
            />
          </section>
        </section>
      </template>
    </div>
  </main>
</template>

<script>
import {
  getDashboardOverview,
  getLeadWorkbench,
  getManagerWorkbench,
  getMemberWorkbench,
  listDashboardReminders,
  markAllDashboardRemindersRead,
  markDashboardReminderRead
} from '@/api/lab/dashboard'
import MetricCard from './components/MetricCard'
import GoalHealthChart from './components/GoalHealthChart'
import MemberLoadMatrix from './components/MemberLoadMatrix'
import ActionQueue from './components/ActionQueue'
import { statusLabel } from '@/utils/lab-status'

const emptyOverview = () => ({
  kpis: [],
  goalHealth: [],
  goalTrend: [],
  taskStatusDistribution: [],
  memberLoads: [],
  coordinationItems: [],
  recentIpr: [],
  recentReports: [],
  latestReport: null,
  performanceSummary: []
})

const emptyManagementWorkbench = () => ({
  pendingDecisions: [],
  newBlocks: [],
  forecastDelays: [],
  pendingAcceptance: [],
  staleKeyResults: [],
  teamCommitments: []
})

const emptyMemberWorkbench = () => ({
  monthlyResults: [],
  weeklyCommitments: [],
  dueItems: [],
  blocks: [],
  missingEvidence: []
})

export default {
  name: 'LabDashboard',
  components: { MetricCard, GoalHealthChart, MemberLoadMatrix, ActionQueue },
  data() {
    return {
      selectedPeriod: this.currentPeriod(),
      overview: emptyOverview(),
      workbench: emptyManagementWorkbench(),
      reminders: [],
      overviewLoading: false,
      workbenchLoading: false,
      reminderLoading: false,
      overviewError: false,
      workbenchError: false,
      reminderError: false,
      overviewRequest: 0,
      workbenchRequest: 0,
      reminderRequest: 0
    }
  },
  computed: {
    workbenchRole() {
      const roles = (this.$store.getters.roles || []).map(role => String(role).toLowerCase())
      if (roles.includes('admin') || roles.includes('lab_manager') || roles.includes('manager')) return 'manager'
      if (roles.includes('lab_lead')) return 'lead'
      return 'member'
    },
    isManagementWorkbench() {
      return this.workbenchRole !== 'member'
    },
    isMemberWorkbench() {
      return this.workbenchRole === 'member'
    },
    workbenchTitle() {
      if (this.workbenchRole === 'manager') return '部门负责人工作台'
      if (this.workbenchRole === 'lead') return '业务线负责人工作台'
      return '个人承诺工作台'
    },
    workbenchDescription() {
      if (this.workbenchRole === 'manager') return '先处理异常和决策，再看目标、承诺负载与周会闭环。'
      if (this.workbenchRole === 'lead') return '聚焦本业务线承诺、阻塞与待跟进决策，不展示部门负责人专属操作。'
      return '用最少动作维护本月结果、本周承诺、阻塞和交付事实。'
    },
    todayActions() {
      if (!this.isManagementWorkbench) return []
      return []
        .concat(this.workbench.pendingAcceptance || [])
        .concat(this.workbench.newBlocks || [])
        .concat(this.workbench.forecastDelays || [])
        .concat((this.workbench.pendingDecisions || []).map(this.decisionAction))
    },
    meetingDecisions() {
      if (!this.isManagementWorkbench) return []
      return (this.workbench.pendingDecisions || []).map(this.decisionAction)
    },
    teamCommitments() {
      return this.isManagementWorkbench && Array.isArray(this.workbench.teamCommitments)
        ? this.workbench.teamCommitments
        : []
    },
    metricCards() {
      if (this.overviewLoading) {
        return Array.from({ length: 5 }, (item, index) => ({ code: `loading-${index}` }))
      }
      return this.overview.kpis.slice(0, 5)
    },
    taskTotal() {
      return this.overview.taskStatusDistribution.reduce((sum, item) => sum + Number(item.count || 0), 0)
    },
    latestUpdate() {
      const values = []
      const collect = items => items.forEach(item => {
        if (item && item.lastUpdated) values.push(new Date(item.lastUpdated).getTime())
      })
      collect(this.overview.kpis)
      collect(this.overview.goalHealth)
      collect(this.overview.taskStatusDistribution)
      collect(this.overview.memberLoads)
      if (this.isManagementWorkbench) {
        collect(this.workbench.newBlocks || [])
        collect(this.workbench.forecastDelays || [])
        collect(this.workbench.pendingAcceptance || [])
      } else {
        collect(this.workbench.weeklyCommitments || [])
        collect(this.workbench.blocks || [])
      }
      const latest = values.filter(Number.isFinite).sort((a, b) => b - a)[0]
      if (!latest) return '等待数据'
      const date = new Date(latest)
      return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  },
  created() {
    this.reload()
  },
  methods: {
    statusLabel,
    currentPeriod() {
      const date = new Date()
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    },
    reload() {
      if (this.isManagementWorkbench) this.loadOverview()
      else {
        this.overview = emptyOverview()
        this.overviewError = false
        this.overviewLoading = false
      }
      this.loadWorkbench()
      this.loadReminders()
    },
    loadWorkbench() {
      const requestId = ++this.workbenchRequest
      const period = this.selectedPeriod
      const role = this.workbenchRole
      const asOf = new Date().toISOString()
      const loaders = { manager: getManagerWorkbench, lead: getLeadWorkbench, member: getMemberWorkbench }
      this.workbenchLoading = true
      this.workbenchError = false
      this.workbench = role === 'member' ? emptyMemberWorkbench() : emptyManagementWorkbench()
      return loaders[role](period, asOf)
        .then(response => {
          if (requestId !== this.workbenchRequest || role !== this.workbenchRole || period !== this.selectedPeriod) return
          const data = response && response.data ? response.data : {}
          const normalized = role === 'member' ? emptyMemberWorkbench() : emptyManagementWorkbench()
          Object.keys(normalized).forEach(key => { normalized[key] = Array.isArray(data[key]) ? data[key] : [] })
          this.workbench = normalized
        })
        .catch(() => {
          if (requestId !== this.workbenchRequest) return
          this.workbench = role === 'member' ? emptyMemberWorkbench() : emptyManagementWorkbench()
          this.workbenchError = true
        })
        .finally(() => {
          if (requestId === this.workbenchRequest) this.workbenchLoading = false
        })
    },
    loadOverview() {
      const requestId = ++this.overviewRequest
      this.overviewLoading = true
      this.overviewError = false
      return getDashboardOverview(this.selectedPeriod)
        .then(response => {
          if (requestId !== this.overviewRequest) return
          const data = response && response.data ? response.data : {}
          const normalized = emptyOverview()
          Object.keys(normalized).forEach(key => {
            if (Array.isArray(normalized[key])) normalized[key] = Array.isArray(data[key]) ? data[key] : []
            else normalized[key] = data[key] || null
          })
          this.overview = normalized
        })
        .catch(() => {
          if (requestId !== this.overviewRequest) return
          this.overview = emptyOverview()
          this.overviewError = true
        })
        .finally(() => {
          if (requestId === this.overviewRequest) this.overviewLoading = false
        })
    },
    loadReminders() {
      const requestId = ++this.reminderRequest
      this.reminderLoading = true
      this.reminderError = false
      return listDashboardReminders({ unreadOnly: false, pageNum: 1, pageSize: 8 })
        .then(response => {
          if (requestId === this.reminderRequest) this.reminders = Array.isArray(response.rows) ? response.rows : []
        })
        .catch(() => {
          if (requestId !== this.reminderRequest) return
          this.reminders = []
          this.reminderError = true
        })
        .finally(() => {
          if (requestId === this.reminderRequest) this.reminderLoading = false
        })
    },
    markReminderRead(item) {
      if (item._marking) return Promise.resolve()
      this.$set(item, '_marking', true)
      return markDashboardReminderRead(item.id, item.version)
        .then(() => {
          item.readFlag = '1'
          item.version = Number(item.version || 0) + 1
        })
        .finally(() => this.$set(item, '_marking', false))
    },
    markAllRemindersRead() {
      return markAllDashboardRemindersRead()
        .then(() => {
          this.reminders = this.reminders.map(item => Object.assign({}, item, { readFlag: '1' }))
        })
    },
    openReminder(item) {
      const type = String(item.businessType || item.reminderType || '').toLowerCase()
      this.openDrill({
        type,
        drillDownFilters: {
          id: item.businessId,
          taskId: item.taskId,
          period: this.selectedPeriod
        }
      }, type.indexOf('report') >= 0 ? 'report' : 'task')
    },
    decisionAction(decision) {
      const relatedTask = decision.relatedTaskId
      const relatedGoal = decision.relatedGoalId
      return {
        id: decision.id,
        type: relatedTask ? 'task' : relatedGoal ? 'goal' : 'decision',
        title: decision.problem || decision.decisionContent || '待跟进管理决策',
        status: decision.decisionStatus,
        dueDate: decision.dueDate,
        period: decision.period || this.selectedPeriod,
        definition: decision.decisionContent,
        drillDownFilters: relatedTask ? { id: relatedTask } : relatedGoal ? { goalId: relatedGoal } : { period: decision.period || this.selectedPeriod }
      }
    },
    openMemberAction(action) {
      const query = { my: '1', period: this.selectedPeriod }
      if (action === 'create-weekly') Object.assign(query, { action: 'create-weekly', taskLevel: 'week' })
      if (action === 'report-block') Object.assign(query, { workflowStatus: 'ACTIVE' })
      if (action === 'submit-result') Object.assign(query, { workflowStatus: 'ACTIVE', overdueOrPending: 'true' })
      const navigation = this.$router.push({ path: '/lab/task', query })
      if (navigation && navigation.catch) navigation.catch(() => {})
    },
    openDrill(item, fallback) {
      const type = `${item.type || ''} ${item.code || ''} ${fallback || ''}`.toLowerCase()
      let path = '/lab/task'
      if (type.indexOf('goal') >= 0) path = '/lab/goal'
      else if (type.indexOf('asset') >= 0 || type.indexOf('single_point') >= 0) path = '/lab/asset'
      else if (type.indexOf('ipr') >= 0) path = '/lab/ipr'
      else if (type.indexOf('report') >= 0) path = '/lab/report'
      else if (type.indexOf('perf') >= 0) path = '/lab/perf'
      const filters = Object.assign({}, item.drillDownFilters || {})
      if (!filters.period && !filters.periodTo) filters.period = item.period || this.selectedPeriod
      const query = {}
      Object.keys(filters).forEach(key => {
        const value = filters[key]
        if (value === null || value === undefined) return
        if (Array.isArray(value)) {
          if (value.length) query[key] = value.map(item => String(item)).join(',')
          return
        }
        if (typeof value !== 'object') query[key] = String(value)
      })
      const navigation = this.$router.push({ path, query })
      if (navigation && navigation.catch) navigation.catch(() => {})
    },
    statusWidth(count) {
      if (!this.taskTotal || Number(count || 0) === 0) return '0%'
      return `${Math.max(3, Math.round(Number(count || 0) / this.taskTotal * 100))}%`
    },
    statusTone(code) {
      const value = String(code || '').toUpperCase()
      if (/BLOCK|OVERDUE|REJECT|FAIL/.test(value)) return 'is-danger'
      if (/PENDING|REVIEW|DRAFT/.test(value)) return 'is-warning'
      return 'is-normal'
    }
  }
}
</script>

<style lang="scss" scoped>
.dashboard-hero { position: relative; min-height: 188px; padding: 30px 36px 28px; overflow: hidden; color: #fff; background: var(--lab-indigo-deep); }
.dashboard-hero::before { position: absolute; top: -90px; right: 8%; width: 360px; height: 360px; border: 1px solid rgba(121, 205, 197, 0.22); border-radius: 50%; content: ''; }
.dashboard-hero::after { position: absolute; top: 0; right: 0; width: 43%; height: 100%; background: repeating-linear-gradient(120deg, transparent 0, transparent 19px, rgba(255,255,255,0.035) 20px); content: ''; }
.dashboard-hero__title, .dashboard-hero__controls, .dashboard-hero__meta { position: relative; z-index: 1; }
.dashboard-hero__sequence { color: #72cec5; font-family: 'Arial Narrow', Arial, sans-serif; font-size: 10px; letter-spacing: 0.2em; }
.dashboard-hero h1 { margin: 9px 0 6px; font-size: 30px; font-weight: 700; letter-spacing: 0.04em; }
.dashboard-hero p { margin: 0; color: #bfc9dc; font-size: 13px; }
.dashboard-hero__controls { position: absolute; top: 34px; right: 36px; display: flex; align-items: center; gap: 10px; }
.dashboard-hero__scope { display: flex; height: 36px; align-items: center; gap: 6px; padding: 0 11px; border: 1px solid rgba(255,255,255,0.18); color: #c4cde0; font-size: 11px; }
.dashboard-hero__scope i { color: #72cec5; }
.dashboard-hero__period { width: 164px; }
.dashboard-hero__refresh { border-color: #72cec5; color: #e7fffc; background: transparent; }
.dashboard-hero__meta { position: absolute; right: 36px; bottom: 25px; left: 36px; display: flex; justify-content: space-between; padding-top: 13px; border-top: 1px solid rgba(255,255,255,0.13); color: #8f9db9; font-size: 10px; }
.dashboard-body { padding: 24px 28px 42px; }
.workbench-section, .member-workbench { margin-top: 18px; }
.workbench-section:first-child, .member-workbench:first-child { margin-top: 0; }
.workbench-section__header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 12px; padding: 0 2px; }
.workbench-section__header h2 { margin: 4px 0 0; color: var(--lab-indigo-deep); font-size: 19px; }
.workbench-section__header > span { color: #5f6b80; font-size: 10px; }
.workbench-action-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.member-primary-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.member-primary-actions button { display: grid; grid-template-columns: 36px 1fr; grid-template-rows: auto auto; gap: 2px 10px; padding: 17px 18px; border: 1px solid var(--lab-line); color: var(--lab-ink); background: #fff; text-align: left; cursor: pointer; }
.member-primary-actions button:hover, .member-primary-actions button:focus { border-color: var(--lab-teal); box-shadow: 0 6px 18px rgba(14, 52, 74, 0.08); outline: none; }
.member-primary-actions i { grid-row: 1 / 3; align-self: center; color: var(--lab-teal); font-size: 25px; }
.member-primary-actions strong { font-size: 13px; }
.member-primary-actions span { color: #5f6b80; font-size: 10px; }
.member-workbench__grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.dashboard-kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; margin-top: -1px; }
.dashboard-kpis__error { grid-column: 1 / -1; display: flex; min-height: 120px; align-items: center; justify-content: center; gap: 8px; color: #874040; }
.dashboard-kpis__error button, .status-ledger__state button { border: 0; color: var(--lab-teal); background: transparent; font-weight: 700; cursor: pointer; }
.dashboard-alert { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; padding: 11px 14px; border-left: 3px solid var(--lab-danger); color: #874040; background: #fff1f0; font-size: 12px; }
.dashboard-alert button { border: 0; color: #9b3939; background: transparent; font-weight: 700; cursor: pointer; }
.dashboard-primary { display: grid; grid-template-columns: minmax(0, 2.1fr) minmax(280px, 0.9fr); gap: 16px; margin-top: 16px; }
.status-ledger { min-height: 430px; padding: 22px 20px 18px; }
.status-ledger__header { display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--lab-line); }
.status-ledger__header h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 18px; }
.status-ledger__header > strong { color: var(--lab-indigo); font-family: 'Arial Narrow', Arial, sans-serif; font-size: 28px; }
.status-ledger__list { margin: 12px 0 0; padding: 0; list-style: none; }
.status-ledger__list button { display: grid; grid-template-columns: 1fr auto; gap: 7px; width: 100%; padding: 9px 2px; border: 0; color: var(--lab-ink); background: transparent; text-align: left; cursor: pointer; }
.status-ledger__label { display: flex; align-items: center; gap: 7px; font-size: 11px; }
.status-ledger__label i { width: 6px; height: 6px; border-radius: 50%; background: var(--lab-success); }
.status-ledger__label i.is-warning { background: var(--lab-warning); }
.status-ledger__label i.is-danger { background: var(--lab-danger); }
.status-ledger__list strong { font-size: 12px; }
.status-ledger__track { grid-column: 1 / 3; height: 3px; overflow: hidden; background: #edf0f5; }
.status-ledger__track i { display: block; height: 100%; background: var(--lab-teal); }
.status-ledger__track i.is-warning { background: var(--lab-warning); }
.status-ledger__track i.is-danger { background: var(--lab-danger); }
.status-ledger__performance { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 14px; padding-top: 13px; border-top: 1px solid var(--lab-line); }
.status-ledger__performance > span { width: 100%; margin-bottom: 2px; color: #5f6b80; font-size: 9px; letter-spacing: 0.08em; }
.status-ledger__performance button { padding: 5px 7px; border: 1px solid var(--lab-line); color: var(--lab-ink-soft); background: #f8f9fb; font-size: 9px; cursor: pointer; }
.status-ledger__performance strong { margin-left: 3px; color: var(--lab-indigo); }
.status-ledger__loading { height: 280px; margin-top: 15px; }
.status-ledger__state { display: flex; min-height: 235px; align-items: center; justify-content: center; gap: 7px; color: var(--lab-ink-soft); font-size: 12px; }
.dashboard-secondary { display: grid; grid-template-columns: minmax(0, 2fr) minmax(300px, 0.8fr); gap: 16px; margin-top: 16px; }
.dashboard-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 16px; }
@media (max-width: 1380px) {
  .dashboard-kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dashboard-primary, .dashboard-secondary { grid-template-columns: 1fr; }
  .workbench-action-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .member-workbench__grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 980px) {
  .dashboard-hero { padding-bottom: 88px; }
  .dashboard-hero__controls { top: auto; right: 36px; bottom: 42px; left: 36px; }
  .dashboard-hero__meta { bottom: 16px; }
  .dashboard-actions { grid-template-columns: 1fr; }
  .member-primary-actions { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .dashboard-hero { padding: 24px 18px 120px; }
  .dashboard-hero h1 { font-size: 24px; }
  .dashboard-hero__controls { right: 18px; bottom: 47px; left: 18px; flex-wrap: wrap; }
  .dashboard-hero__scope { display: none; }
  .dashboard-hero__meta { right: 18px; bottom: 12px; left: 18px; }
  .dashboard-hero__meta span:last-child { display: none; }
  .dashboard-body { padding: 16px 14px 30px; }
  .dashboard-kpis { grid-template-columns: 1fr; }
  .workbench-action-grid, .member-workbench__grid { grid-template-columns: 1fr; }
  .workbench-section__header { align-items: flex-start; flex-direction: column; gap: 4px; }
}
</style>
