<template>
  <main class="lab-dashboard">
    <section class="dashboard-hero">
      <div class="dashboard-hero__title">
        <span class="dashboard-hero__sequence">LAB / OPS / {{ selectedPeriod }}</span>
        <h1>实验室管理态势</h1>
        <p>以目标、任务和人才事实为底稿的月度管理作战日志</p>
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
          :loading="overviewLoading || reminderLoading"
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
              <span class="lab-eyebrow">Task composition</span>
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
                  {{ item.name }}
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
              {{ item.name }} <strong>{{ item.count }}</strong>
            </button>
          </div>
        </section>
      </section>

      <section class="dashboard-secondary">
        <member-load-matrix
          :members="overview.memberLoads"
          :loading="overviewLoading"
          :error="overviewError"
          @drill="openDrill($event, 'task')"
          @retry="loadOverview"
        />
        <action-queue
          title="我的提醒"
          eyebrow="Personal inbox"
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

      <section class="dashboard-actions" aria-label="近期管理行动">
        <action-queue
          title="协同待办"
          eyebrow="Coordination"
          :items="overview.coordinationItems"
          :loading="overviewLoading"
          :error="overviewError"
          empty-text="当前没有协同阻塞"
          @drill="openDrill($event, 'task')"
          @retry="loadOverview"
        />
        <action-queue
          title="近期 IPR"
          eyebrow="Intellectual property"
          :items="overview.recentIpr"
          :loading="overviewLoading"
          :error="overviewError"
          empty-text="当前范围暂无近期 IPR"
          @drill="openDrill($event, 'ipr')"
          @retry="loadOverview"
        />
        <action-queue
          title="已定稿报告"
          eyebrow="Report archive"
          :items="overview.recentReports"
          :loading="overviewLoading"
          :error="overviewError"
          empty-text="当前范围暂无已定稿报告"
          @drill="openDrill($event, 'report')"
          @retry="loadOverview"
        />
      </section>
    </div>
  </main>
</template>

<script>
import {
  getDashboardOverview,
  listDashboardReminders,
  markAllDashboardRemindersRead,
  markDashboardReminderRead
} from '@/api/lab/dashboard'
import MetricCard from './components/MetricCard'
import GoalHealthChart from './components/GoalHealthChart'
import MemberLoadMatrix from './components/MemberLoadMatrix'
import ActionQueue from './components/ActionQueue'

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

export default {
  name: 'LabDashboard',
  components: { MetricCard, GoalHealthChart, MemberLoadMatrix, ActionQueue },
  data() {
    return {
      selectedPeriod: this.currentPeriod(),
      overview: emptyOverview(),
      reminders: [],
      overviewLoading: false,
      reminderLoading: false,
      overviewError: false,
      reminderError: false,
      overviewRequest: 0,
      reminderRequest: 0
    }
  },
  computed: {
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
    currentPeriod() {
      const date = new Date()
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    },
    reload() {
      this.loadOverview()
      this.loadReminders()
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
    openDrill(item, fallback) {
      const type = `${item.type || ''} ${item.code || ''} ${fallback || ''}`.toLowerCase()
      let path = '/lab/task'
      if (type.indexOf('goal') >= 0) path = '/lab/goal'
      else if (type.indexOf('asset') >= 0 || type.indexOf('single_point') >= 0) path = '/lab/asset'
      else if (type.indexOf('ipr') >= 0) path = '/lab/ipr'
      else if (type.indexOf('report') >= 0) path = '/lab/report'
      else if (type.indexOf('perf') >= 0) path = '/lab/perf'
      const filters = Object.assign({}, item.drillDownFilters || {})
      if (!filters.period) filters.period = item.period || this.selectedPeriod
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
}
@media (max-width: 980px) {
  .dashboard-hero { padding-bottom: 88px; }
  .dashboard-hero__controls { top: auto; right: 36px; bottom: 42px; left: 36px; }
  .dashboard-hero__meta { bottom: 16px; }
  .dashboard-actions { grid-template-columns: 1fr; }
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
}
</style>
