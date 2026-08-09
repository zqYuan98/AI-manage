<template>
  <main class="task-page lab-dashboard">
    <header class="task-page__header">
      <div>
        <span class="lab-eyebrow">执行台账</span>
        <h1>任务事实台账</h1>
        <p>计划、结果、证据、质量门禁与阻塞事件在同一条事实链上推进</p>
      </div>
      <div class="task-page__header-actions">
        <el-button v-hasPermi="['lab:task:add']" icon="el-icon-plus" type="primary" @click="openCreate('month')">新增月任务</el-button>
        <el-button v-hasPermi="['lab:task:add']" icon="el-icon-copy-document" @click="openCreate('week')">新增周任务</el-button>
      </div>
    </header>

    <section class="task-page__filters lab-panel">
      <div class="task-page__scope-tabs">
        <button :class="{ 'is-active': scopeMode === 'all' }" type="button" @click="setScope('all')">授权范围</button>
        <button :class="{ 'is-active': scopeMode === 'mine' }" type="button" :disabled="!currentMember" @click="setScope('mine')">我的任务</button>
      </div>
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item><el-input v-model.trim="query.title" clearable placeholder="任务标题" prefix-icon="el-icon-search" @keyup.enter.native="search" /></el-form-item>
        <el-form-item><el-date-picker v-model="query.period" type="month" value-format="yyyy-MM" format="yyyy-MM" clearable placeholder="月度周期" @change="periodChanged" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.taskLevel" clearable placeholder="层级" @change="search"><el-option label="月任务" value="month" /><el-option label="周任务" value="week" /></el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.workflowStatus" clearable placeholder="工作流状态" @change="workflowChanged">
            <el-option v-for="item in workflowOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.ownerId" clearable filterable placeholder="负责人" :disabled="scopeMode === 'mine'" @change="ownerChanged">
            <el-option v-for="owner in writableOwners" :key="owner.id" :label="ownerLabel(owner)" :value="owner.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.milestoneId" clearable filterable placeholder="季度里程碑" @change="milestoneChanged">
            <el-option v-for="goal in milestoneGoals" :key="goal.id" :label="`${goal.period} · ${goal.title}`" :value="goal.id" />
          </el-select>
        </el-form-item>
        <el-form-item><el-checkbox v-model="blockedOnly" @change="search">仅阻塞</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" icon="el-icon-search" @click="search">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <div v-if="hasDrillFilters" class="task-page__drill-filter" role="status">
        <i class="el-icon-filter" /> 已应用看板下钻条件：{{ drillFilterLabel }}
        <button type="button" @click="clearDrillFilters">清除下钻条件</button>
      </div>
      <div class="task-page__saved">
        <span>保存的筛选</span>
        <button v-for="filter in savedFilters" :key="filter.name" type="button" @click="applySavedFilter(filter)">{{ filter.name }}</button>
        <button type="button" class="is-add" @click="saveCurrentFilter"><i class="el-icon-plus" /> 保存当前</button>
      </div>
    </section>

    <section class="task-readiness lab-panel">
      <div class="task-readiness__title">
        <div><span class="lab-eyebrow">权重就绪度</span><h2>月度双权重就绪度</h2></div>
        <el-button
          v-hasPermi="['lab:task:edit']"
          type="primary"
          size="small"
          :disabled="!performanceReadiness.ready || !query.ownerId || !query.period"
          :loading="activatingPlan"
          @click="activatePlan"
        >
          激活该成员月计划
        </el-button>
      </div>
      <div class="task-readiness__bars">
        <div>
          <p><span>绩效权重 · 成员 / 月</span><strong :class="{ 'is-ready': performanceReadiness.ready }">{{ performanceReadiness.total }} / 100</strong></p>
          <el-progress :percentage="performanceReadiness.percent" :stroke-width="7" :show-text="false" :color="performanceReadiness.ready ? '#087b75' : '#b7791f'" />
          <small>{{ query.ownerId ? '达到 100% 后允许激活月计划' : '先选择负责人' }}</small>
        </div>
        <div>
          <p><span>目标权重 · 里程碑</span><strong :class="{ 'is-ready': goalReadiness.ready }">{{ goalReadiness.total }} / 100</strong></p>
          <el-progress :percentage="goalReadiness.percent" :stroke-width="7" :show-text="false" :color="goalReadiness.ready ? '#087b75' : '#b7791f'" />
          <small>{{ query.milestoneId ? '达到 100% 后可在目标页激活季度里程碑' : '选择季度里程碑查看合同' }}</small>
        </div>
      </div>
    </section>

    <section class="task-table lab-panel">
      <header class="task-table__header">
        <div><span class="lab-eyebrow">事实登记</span><h2>任务列表</h2></div>
        <span>共 {{ total }} 条 · 风险色仅标记阻塞或逾期事实</span>
      </header>
      <el-table v-loading="loading" :data="rows" row-key="id" class="task-table__body" @row-dblclick="handleRowDoubleClick">
        <el-table-column label="任务" min-width="260">
          <template slot-scope="scope">
            <div class="task-table__task">
              <span :class="`is-${scope.row.taskLevel}`">{{ scope.row.taskLevel === 'month' ? '月' : '周' }}</span>
              <div><strong>{{ scope.row.title }}</strong><small>#{{ scope.row.id }} · {{ scope.row.period }} · {{ scope.row.taskType === 'key' ? '重点' : '日常' }}</small></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="145"><template slot-scope="scope">{{ memberName(scope.row.ownerId) }}</template></el-table-column>
        <el-table-column label="计划日期" prop="planDate" width="115" />
        <el-table-column label="双权重" width="135">
          <template slot-scope="scope"><div class="task-table__weights"><span>绩 {{ number(scope.row.perfWeight) }}</span><span>目 {{ number(scope.row.goalWeight) }}</span></div></template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template slot-scope="scope"><span class="task-table__status" :class="statusClass(scope.row.workflowStatus)">{{ statusLabel(scope.row.workflowStatus) }}</span><small v-if="scope.row.resultStatus" class="task-table__result">{{ resultStatusLabel(scope.row.resultStatus) }}</small></template>
        </el-table-column>
        <el-table-column label="风险" width="95" align="center">
          <template slot-scope="scope"><span v-if="riskLabel(scope.row)" class="task-table__risk"><i class="el-icon-warning" /> {{ riskLabel(scope.row) }}</span><span v-else>—</span></template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['lab:task:edit']" type="text" :disabled="isLocked(scope.row)" @click="openEdit(scope.row)">编辑</el-button>
            <el-button v-hasPermi="['lab:task:evidence']" type="text" @click="openEvidence(scope.row)">证据/门禁</el-button>
            <el-button v-if="canSubmit(scope.row)" v-hasPermi="['lab:task:edit']" type="text" @click="openWorkflow(scope.row, 'submit')">提交</el-button>
            <el-button v-if="canReview(scope.row)" v-hasPermi="['lab:task:review']" type="text" @click="openWorkflow(scope.row, 'review')">审核</el-button>
            <el-dropdown trigger="click" @command="handleCommand($event, scope.row)">
              <el-button type="text">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item v-if="scope.row.taskLevel === 'week'" v-hasPermi="['lab:task:add']" command="copy">复制到下周</el-dropdown-item>
                <el-dropdown-item v-else v-hasPermi="['lab:task:add']" command="copy">拆分周任务</el-dropdown-item>
                <el-dropdown-item v-if="canActivateWeek(scope.row)" v-hasPermi="['lab:task:edit']" command="activate">激活周任务</el-dropdown-item>
                <el-dropdown-item v-if="canWithdraw(scope.row)" v-hasPermi="['lab:task:edit']" command="withdraw">撤回结果</el-dropdown-item>
                <el-dropdown-item v-if="canReopen(scope.row)" v-hasPermi="['lab:task:review']" command="reopen">重新打开</el-dropdown-item>
                <el-dropdown-item v-if="canChangeBlock(scope.row) && scope.row.blockFlag === '1'" v-hasPermi="['lab:task:edit']" command="unblock">解除阻塞</el-dropdown-item>
                <el-dropdown-item v-if="canChangeBlock(scope.row) && scope.row.blockFlag !== '1'" v-hasPermi="['lab:task:edit']" command="block">标记阻塞</el-dropdown-item>
                <el-dropdown-item v-if="scope.row.workflowStatus === 'DRAFT'" v-hasPermi="['lab:task:remove']" command="delete" divided>删除草稿</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </template>
        </el-table-column>
        <template slot="empty"><div class="task-table__empty"><i class="el-icon-document" /><span>当前筛选下暂无任务</span></div></template>
      </el-table>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadTasks" />
    </section>

    <task-form-drawer
      :visible="formVisible"
      :task="editingTask"
      :owners="owners"
      :task-owners="writableOwners"
      :goals="flatGoals"
      :month-tasks="monthTasks"
      :saving="saving"
      :field-errors="fieldErrors"
      :conflict-notice="conflictNotice"
      @close="formVisible = false"
      @save="saveTask"
    />
    <evidence-editor :visible="evidenceVisible" :task="activeTask" @close="evidenceVisible = false" />
    <task-review-panel
      :visible="workflowVisible"
      :task="activeTask"
      :mode="workflowMode"
      :saving="saving"
      :field-errors="fieldErrors"
      @close="workflowVisible = false"
      @submit="submitResult"
      @pass="reviewPass"
      @return="reviewReturn"
    />
  </main>
</template>

<script>
import { getGoalTree } from '@/api/lab/goal'
import {
  activateMonthlyPlan,
  activateTask,
  addTask,
  blockTask,
  deleteTask,
  getTask,
  listTaskOwners,
  listTasks,
  reopenTask,
  reviewTaskPass,
  reviewTaskReturn,
  submitTaskResult,
  unblockTask,
  updateTask,
  withdrawTaskResult
} from '@/api/lab/task'
import TaskFormDrawer from './components/TaskFormDrawer'
import EvidenceEditor from './components/EvidenceEditor'
import TaskReviewPanel from './components/TaskReviewPanel'

const FILTER_STORAGE = 'ailab.task.saved-filters.v1'

export default {
  name: 'LabTask',
  components: { TaskFormDrawer, EvidenceEditor, TaskReviewPanel },
  data() {
    return {
      query: {
        pageNum: 1,
        pageSize: 20,
        title: this.$route.query.title || '',
        period: this.$route.query.period || (this.$route.query.id || this.$route.query.taskId || this.$route.query.periodTo ? '' : this.currentPeriod()),
        taskLevel: this.$route.query.taskLevel || '',
        taskType: this.$route.query.taskType || '',
        workflowStatus: this.$route.query.workflowStatus || '',
        workflowStatuses: this.routeList(this.$route.query.workflowStatuses),
        ownerId: this.numberOrNull(this.$route.query.ownerId),
        milestoneId: this.numberOrNull(this.$route.query.milestoneId),
        goalId: this.numberOrNull(this.$route.query.goalId),
        currentBlockFlag: this.$route.query.currentBlockFlag || '',
        id: this.numberOrNull(this.$route.query.id || this.$route.query.taskId),
        periodTo: this.$route.query.periodTo || '',
        overdueOrPending: this.routeBoolean(this.$route.query.overdueOrPending),
        asOf: this.$route.query.asOf || '',
        blockStartBefore: this.$route.query.blockStartBefore || ''
      },
      scopeMode: 'all',
      blockedOnly: this.$route.query.currentBlockFlag === '1',
      rows: [],
      total: 0,
      owners: [],
      goals: [],
      monthTasks: [],
      readinessPerfRows: [],
      readinessGoalRows: [],
      savedFilters: [],
      loading: false,
      saving: false,
      activatingPlan: false,
      formVisible: false,
      evidenceVisible: false,
      workflowVisible: false,
      editingTask: null,
      activeTask: null,
      workflowMode: 'submit',
      fieldErrors: {},
      conflictNotice: '',
      listRequest: 0,
      readinessRequest: 0,
      workflowOptions: [
        { label: '草稿', value: 'DRAFT' },
        { label: '执行中', value: 'ACTIVE' },
        { label: '待审核', value: 'PENDING_REVIEW' },
        { label: '已确认', value: 'CONFIRMED' }
      ]
    }
  },
  computed: {
    currentMember() {
      const userId = this.$store.state.user.id
      return this.owners.find(owner => String(owner.userId) === String(userId)) || null
    },
    flatGoals() {
      const result = []
      const walk = nodes => nodes.forEach(node => { result.push(node); walk(node.children || []) })
      walk(this.goals)
      return result
    },
    milestoneGoals() {
      return this.flatGoals.filter(goal => goal.goalLevel === 'QUARTER')
    },
    writableOwners() {
      const roles = this.$store.state.user.roles || []
      if (roles.indexOf('lab_manager') >= 0) return this.owners
      if (!this.currentMember) return []
      if (roles.indexOf('lab_lead') >= 0) return this.owners.filter(owner => owner.bizLine === this.currentMember.bizLine)
      return [this.currentMember]
    },
    performanceReadiness() {
      const result = this.readiness(this.readinessPerfRows, 'perfWeight', Boolean(this.query.ownerId))
      result.ready = result.ready && this.readinessPerfRows.every(task => task.workflowStatus === 'DRAFT')
      return result
    },
    goalReadiness() {
      return this.readiness(this.readinessGoalRows, 'goalWeight', Boolean(this.query.milestoneId))
    },
    hasDrillFilters() {
      return Boolean(this.query.id || this.query.goalId || this.query.periodTo || this.query.overdueOrPending !== null || this.query.asOf || this.query.blockStartBefore || this.query.workflowStatuses.length)
    },
    drillFilterLabel() {
      const labels = []
      if (this.query.id) labels.push(`任务 #${this.query.id}`)
      if (this.query.goalId) labels.push(`目标 #${this.query.goalId}`)
      if (this.query.workflowStatuses.length) labels.push(`状态 ${this.query.workflowStatuses.join('/')}`)
      if (this.query.periodTo) labels.push(`截止 ${this.query.periodTo}`)
      if (this.query.overdueOrPending !== null) labels.push('逾期或待处理')
      if (this.query.blockStartBefore) labels.push(`阻塞早于 ${this.query.blockStartBefore}`)
      return labels.join(' · ')
    }
  },
  created() {
    this.loadSavedFilters()
    Promise.all([this.loadOwners(), this.loadGoals()]).then(() => {
      if (this.$route.query.my === '1' && this.currentMember) {
        this.scopeMode = 'mine'
        this.query.ownerId = this.currentMember.id
      }
      return this.refreshAll()
    }).then(() => {
      if (this.$route.query.action === 'create-weekly') this.openCreate('week')
    })
  },
  methods: {
    currentPeriod() {
      const date = new Date()
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    },
    numberOrNull(value) {
      const number = Number(value)
      return value !== '' && value != null && Number.isFinite(number) ? number : null
    },
    routeList(value) {
      const values = Array.isArray(value) ? value : String(value || '').split(',')
      return values.map(item => String(item).trim()).filter(Boolean)
    },
    routeBoolean(value) {
      if (value === '' || value === undefined || value === null) return null
      return String(value).toLowerCase() === 'true' || String(value) === '1' ? true : null
    },
    requestParams() {
      const params = {}
      Object.keys(this.query).forEach(key => {
        const value = this.query[key]
        if (Array.isArray(value)) {
          if (value.length) params[key] = value.join(',')
        } else if (value !== '' && value !== null && value !== undefined) params[key] = value
      })
      if (this.blockedOnly) params.currentBlockFlag = '1'
      return params
    },
    loadTasks() {
      const requestId = ++this.listRequest
      this.loading = true
      return listTasks(this.requestParams()).then(response => {
        if (requestId !== this.listRequest) return
        this.rows = response.rows || []
        this.total = Number(response.total || 0)
      }).finally(() => {
        if (requestId === this.listRequest) this.loading = false
      })
    },
    loadOwners() {
      return listTaskOwners({ memberStatus: 'ACTIVE', pageNum: 1, pageSize: 500 })
        .then(response => { this.owners = response.rows || [] })
        .catch(() => { this.owners = [] })
    },
    loadGoals() {
      const year = Number(String(this.query.period || this.currentPeriod()).substring(0, 4))
      return getGoalTree({ year }).then(response => { this.goals = response.data || [] }).catch(() => { this.goals = [] })
    },
    loadMonthTasks(period) {
      const params = { taskLevel: 'month', pageNum: 1, pageSize: 500 }
      const targetPeriod = period || this.query.period
      if (targetPeriod) params.period = targetPeriod
      if (this.query.ownerId) params.ownerId = this.query.ownerId
      return listTasks(params).then(response => { this.monthTasks = response.rows || [] }).catch(() => { this.monthTasks = [] })
    },
    loadReadiness() {
      const requestId = ++this.readinessRequest
      const requests = []
      if (this.query.ownerId && this.query.period) {
        requests.push(listTasks({ ownerId: this.query.ownerId, period: this.query.period, taskLevel: 'month', taskType: 'key', pageNum: 1, pageSize: 500 })
          .then(response => { if (requestId === this.readinessRequest) this.readinessPerfRows = response.rows || [] })
          .catch(() => { if (requestId === this.readinessRequest) this.readinessPerfRows = [] }))
      } else this.readinessPerfRows = []
      if (this.query.milestoneId) {
        requests.push(listTasks({ milestoneId: this.query.milestoneId, taskLevel: 'month', taskType: 'key', pageNum: 1, pageSize: 500 })
          .then(response => { if (requestId === this.readinessRequest) this.readinessGoalRows = response.rows || [] })
          .catch(() => { if (requestId === this.readinessRequest) this.readinessGoalRows = [] }))
      } else this.readinessGoalRows = []
      return Promise.all(requests)
    },
    refreshAll() {
      return Promise.all([this.loadTasks(), this.loadMonthTasks(), this.loadReadiness()])
    },
    search() {
      this.query.pageNum = 1
      this.syncRoute()
      this.refreshAll()
    },
    periodChanged() {
      this.loadGoals()
      this.search()
    },
    ownerChanged() {
      this.scopeMode = this.currentMember && String(this.query.ownerId) === String(this.currentMember.id) ? 'mine' : 'all'
      this.search()
    },
    milestoneChanged() {
      this.search()
    },
    workflowChanged() {
      this.query.workflowStatuses = []
      this.search()
    },
    clearDrillFilters() {
      this.query.id = null
      this.query.goalId = null
      this.query.periodTo = ''
      this.query.overdueOrPending = null
      this.query.asOf = ''
      this.query.blockStartBefore = ''
      this.query.workflowStatuses = []
      this.search()
    },
    resetQuery() {
      this.query = { pageNum: 1, pageSize: 20, title: '', period: this.currentPeriod(), taskLevel: '', taskType: '', workflowStatus: '', workflowStatuses: [], ownerId: null, milestoneId: null, goalId: null, currentBlockFlag: '', id: null, periodTo: '', overdueOrPending: null, asOf: '', blockStartBefore: '' }
      this.blockedOnly = false
      this.scopeMode = 'all'
      this.loadGoals()
      this.search()
    },
    setScope(mode) {
      if (mode === 'mine' && !this.currentMember) return
      this.scopeMode = mode
      this.query.ownerId = mode === 'mine' ? this.currentMember.id : null
      this.search()
    },
    syncRoute() {
      const query = {}
      Object.keys(this.query).forEach(key => {
        const value = this.query[key]
        if (['pageNum', 'pageSize'].indexOf(key) >= 0 || value === '' || value === null) return
        if (Array.isArray(value)) {
          if (value.length) query[key] = value.join(',')
        } else query[key] = String(value)
      })
      if (this.blockedOnly) query.currentBlockFlag = '1'
      if (this.scopeMode === 'mine') query.my = '1'
      const navigation = this.$router.replace({ query })
      if (navigation && navigation.catch) navigation.catch(() => {})
    },
    loadSavedFilters() {
      try { this.savedFilters = JSON.parse(localStorage.getItem(FILTER_STORAGE) || '[]') } catch (error) { this.savedFilters = [] }
    },
    saveCurrentFilter() {
      this.$prompt('为当前筛选命名', '保存筛选', { inputPattern: /^.{1,20}$/, inputErrorMessage: '请输入 1-20 个字符' })
        .then(({ value }) => {
          const filter = { name: value, query: JSON.parse(JSON.stringify(this.query)), blockedOnly: this.blockedOnly, scopeMode: this.scopeMode }
          this.savedFilters = this.savedFilters.filter(item => item.name !== value).concat(filter).slice(-8)
          localStorage.setItem(FILTER_STORAGE, JSON.stringify(this.savedFilters))
        })
        .catch(() => {})
    },
    applySavedFilter(filter) {
      this.query = Object.assign({}, this.query, JSON.parse(JSON.stringify(filter.query)), { pageNum: 1 })
      this.blockedOnly = Boolean(filter.blockedOnly)
      this.scopeMode = filter.scopeMode === 'mine' && this.currentMember ? 'mine' : 'all'
      if (this.scopeMode === 'mine') this.query.ownerId = this.currentMember.id
      this.loadGoals()
      this.search()
    },
    openCreate(level) {
      this.fieldErrors = {}
      this.conflictNotice = ''
      const requestedOwner = this.writableOwners.find(owner => String(owner.id) === String(this.query.ownerId))
      this.editingTask = {
        taskLevel: level,
        taskType: 'key',
        period: level === 'month' ? this.query.period : this.currentIsoWeek(),
        ownerId: requestedOwner ? requestedOwner.id : !this.isManager() && this.currentMember ? this.currentMember.id : null,
        milestoneId: this.query.milestoneId,
        workflowStatus: 'DRAFT',
        coordinationRequired: '0',
        perfWeight: 0,
        goalWeight: 0,
        periodLockFlag: '0'
      }
      const milestone = this.flatGoals.find(goal => String(goal.id) === String(this.query.milestoneId))
      if (milestone) this.editingTask.goalId = milestone.parentId
      const owner = this.owners.find(item => String(item.id) === String(this.editingTask.ownerId))
      if (owner) this.editingTask.bizLine = owner.bizLine
      this.formVisible = true
    },
    openEdit(row) {
      this.fieldErrors = {}
      this.conflictNotice = ''
      getTask(row.id).then(response => {
        this.editingTask = response.data
        this.formVisible = true
      })
    },
    handleRowDoubleClick(row) {
      if (this.hasPermission('lab:task:edit')) this.openEdit(row)
    },
    saveTask(payload) {
      this.fieldErrors = {}
      this.saving = true
      const action = payload.id ? updateTask(payload) : addTask(payload)
      action.then(() => {
        this.$message.success('任务草稿已保存')
        this.formVisible = false
        return this.refreshAll()
      }).catch(error => {
        this.fieldErrors = this.extractFieldErrors(error)
        if (payload.id && this.isConflict(error)) this.recoverConflict(payload)
      }).finally(() => { this.saving = false })
    },
    recoverConflict(draft) {
      getTask(draft.id).then(response => {
        const latest = response.data
        this.$confirm('服务器任务已更新。保留当前输入并基于最新版本重试，或载入服务器版本。', '版本冲突', {
          confirmButtonText: '保留输入', cancelButtonText: '载入服务器版本', type: 'warning'
        }).then(() => {
          this.editingTask = Object.assign({}, draft, { version: latest.version })
          this.conflictNotice = `已将本地输入重新基于服务器 v${latest.version}，请核对后再次保存。`
        }).catch(() => {
          this.editingTask = latest
          this.conflictNotice = `已载入服务器 v${latest.version}。`
        })
      })
    },
    openEvidence(row) {
      getTask(row.id).then(response => { this.activeTask = response.data; this.evidenceVisible = true })
    },
    openWorkflow(row, mode) {
      this.fieldErrors = {}
      getTask(row.id).then(response => { this.activeTask = response.data; this.workflowMode = mode; this.workflowVisible = true })
    },
    submitResult(command) {
      this.executeWorkflow(submitTaskResult(this.activeTask.id, this.activeTask.version, command), '结果已提交审核')
    },
    reviewPass(command) {
      this.executeWorkflow(reviewTaskPass(this.activeTask.id, this.activeTask.version, command), '任务结果已确认')
    },
    reviewReturn(command) {
      this.executeWorkflow(reviewTaskReturn(this.activeTask.id, this.activeTask.version, command), '任务结果已退回')
    },
    executeWorkflow(action, message) {
      this.saving = true
      this.fieldErrors = {}
      action.then(() => {
        this.$message.success(message)
        this.workflowVisible = false
        return this.refreshAll()
      }).catch(error => {
        this.fieldErrors = this.extractFieldErrors(error)
        if (this.isConflict(error)) this.refreshActiveTask()
      }).finally(() => { this.saving = false })
    },
    refreshActiveTask() {
      if (!this.activeTask) return
      getTask(this.activeTask.id).then(response => {
        this.activeTask = response.data
        this.$message.warning('任务已被更新，已载入最新版本，请重新核对。')
      })
    },
    handleCommand(command, row) {
      const handlers = {
        copy: () => this.copyWeekly(row),
        activate: () => this.activateWeek(row),
        withdraw: () => this.withdraw(row),
        reopen: () => this.reopen(row),
        block: () => this.markBlocked(row),
        unblock: () => this.markUnblocked(row),
        delete: () => this.removeTask(row)
      }
      if (handlers[command]) handlers[command]()
    },
    copyWeekly(row) {
      this.fieldErrors = {}
      this.conflictNotice = ''
      const source = JSON.parse(JSON.stringify(row))
      const nextPeriod = row.taskLevel === 'week' ? this.nextContainedIsoWeek(row.period) : this.firstContainedIsoWeek(row.period)
      const targetMonth = row.taskLevel === 'week' ? this.monthForIsoWeek(nextPeriod) : row.period
      const parent = row.taskLevel === 'week' ? this.monthTasks.find(item => String(item.id) === String(row.parentId)) : row
      const inheritedParent = parent && parent.period === targetMonth ? parent.id : row.taskLevel === 'month' ? row.id : null
      this.editingTask = Object.assign(source, {
        id: null,
        version: null,
        parentId: inheritedParent,
        taskLevel: 'week',
        period: nextPeriod,
        perfWeight: 0,
        goalWeight: 0,
        workflowStatus: 'DRAFT',
        resultStatus: null,
        resultDesc: null,
        failReason: null,
        nextAction: null,
        actualFinishTime: null,
        blockFlag: '0',
        periodLockFlag: '0'
      })
      this.loadMonthTasks(targetMonth).then(() => {
        if (!inheritedParent) {
          this.editingTask.goalId = null
          this.editingTask.milestoneId = null
        }
        this.formVisible = true
      })
    },
    activateWeek(row) {
      this.$confirm('确认激活该周任务？', '激活周任务', { type: 'warning' })
        .then(() => activateTask(row.id, row.version))
        .then(() => this.mutationDone('周任务已激活'))
        .catch(error => this.handleMutationError(error))
    },
    withdraw(row) {
      this.$confirm('撤回后任务回到执行中，可重新编辑结果。', '撤回结果', { type: 'warning' })
        .then(() => withdrawTaskResult(row.id, row.version))
        .then(() => this.mutationDone('结果已撤回'))
        .catch(error => this.handleMutationError(error))
    },
    reopen(row) {
      this.$prompt('请输入重新打开原因', '重新打开任务', { inputPattern: /\S+/, inputErrorMessage: '原因不能为空' })
        .then(({ value }) => reopenTask(row.id, row.version, value))
        .then(() => this.mutationDone('任务已重新打开'))
        .catch(error => this.handleMutationError(error))
    },
    markBlocked(row) {
      this.$prompt('请输入阻塞类型', '标记阻塞', { inputPattern: /\S+/, inputErrorMessage: '类型不能为空' })
        .then(({ value: type }) => this.$prompt('请输入阻塞原因', '标记阻塞', { inputPattern: /\S+/, inputErrorMessage: '原因不能为空' }).then(({ value }) => blockTask(row.id, row.version, type, value)))
        .then(() => this.mutationDone('阻塞事件已记录'))
        .catch(error => this.handleMutationError(error))
    },
    markUnblocked(row) {
      this.$prompt('请输入解决说明', '解除阻塞', { inputPattern: /\S+/, inputErrorMessage: '解决说明不能为空' })
        .then(({ value }) => unblockTask(row.id, row.version, value))
        .then(() => this.mutationDone('阻塞已解除'))
        .catch(error => this.handleMutationError(error))
    },
    removeTask(row) {
      this.$confirm('仅草稿任务可删除，确认继续？', '删除任务', { type: 'warning' })
        .then(() => deleteTask(row.id, row.version))
        .then(() => this.mutationDone('任务已删除'))
        .catch(error => this.handleMutationError(error))
    },
    activatePlan() {
      if (!this.query.ownerId || !this.query.period) {
        this.$message.warning('请先选择负责人和月度周期')
        return
      }
      if (!this.performanceReadiness.ready) {
        this.$message.warning('绩效权重必须合计 100%，前端已阻止本次激活请求')
        return
      }
      this.activatingPlan = true
      this.$confirm('确认激活该成员本月全部重点任务计划？', '激活月计划', { type: 'warning' })
        .then(() => activateMonthlyPlan(this.query.ownerId, this.query.period))
        .then(() => this.mutationDone('月度计划已激活'))
        .catch(error => this.handleMutationError(error))
        .finally(() => { this.activatingPlan = false })
    },
    mutationDone(message) {
      this.$message.success(message)
      return this.refreshAll()
    },
    handleMutationError(error) {
      if (this.isConflict(error)) {
        this.$message.warning('记录已被更新，正在刷新最新数据。')
        this.refreshAll()
      }
    },
    extractFieldErrors(error) {
      const rows = error && error.responseData && Array.isArray(error.responseData.fieldErrors) ? error.responseData.fieldErrors : []
      return rows.reduce((result, item) => { result[item.field] = item.message; return result }, {})
    },
    isConflict(error) {
      return /changed|refresh and retry|became read-only|更新|版本/.test(String(error && error.message))
    },
    readiness(rows, field, enabled) {
      const total = rows.reduce((sum, row) => sum + Number(row[field] || 0), 0)
      const normalized = Number(total.toFixed(2))
      return { total: enabled ? normalized : 0, percent: enabled ? Math.min(100, Math.max(0, normalized)) : 0, ready: enabled && Math.abs(total - 100) < 0.001 }
    },
    isLocked(row) {
      return row.periodLockFlag === '1' || ['PENDING_REVIEW', 'CONFIRMED'].indexOf(row.workflowStatus) >= 0
    },
    hasPermission(permission) {
      const permissions = this.$store.state.user.permissions || []
      return permissions.indexOf('*:*:*') >= 0 || permissions.indexOf(permission) >= 0
    },
    isManager() {
      return (this.$store.state.user.roles || []).indexOf('lab_manager') >= 0
    },
    isOwner(row) {
      return Boolean(this.currentMember) && String(row.ownerId) === String(this.currentMember.id)
    },
    canSubmit(row) {
      return row.workflowStatus === 'ACTIVE' && this.isOwner(row) && row.periodLockFlag !== '1'
    },
    canActivateWeek(row) {
      return row.taskLevel === 'week' && row.workflowStatus === 'DRAFT' && this.isOwner(row) && row.periodLockFlag !== '1'
    },
    canReview(row) {
      return row.workflowStatus === 'PENDING_REVIEW' && !this.isOwner(row) && row.periodLockFlag !== '1'
    },
    canWithdraw(row) {
      return row.workflowStatus === 'PENDING_REVIEW' && this.isOwner(row) && row.periodLockFlag !== '1'
    },
    canReopen(row) {
      return row.workflowStatus === 'CONFIRMED' && this.isManager() && row.periodLockFlag !== '1'
    },
    canChangeBlock(row) {
      return ['DRAFT', 'ACTIVE'].indexOf(row.workflowStatus) >= 0 && row.periodLockFlag !== '1'
    },
    riskLabel(row) {
      if (row.blockFlag === '1') return '阻塞'
      if (!row.planDate || row.workflowStatus === 'CONFIRMED') return ''
      const end = new Date(`${row.planDate}T23:59:59`)
      return Number.isNaN(end.getTime()) || end >= new Date() ? '' : '逾期'
    },
    memberName(id) {
      const owner = this.owners.find(item => String(item.id) === String(id))
      return owner ? owner.nickName || owner.userName || owner.memberNo : `#${id}`
    },
    ownerLabel(owner) {
      return `${owner.nickName || owner.userName || owner.memberNo} · ${owner.bizLine || '无业务线'}`
    },
    statusLabel(status) {
      const option = this.workflowOptions.find(item => item.value === status)
      return option ? option.label : '未定义状态'
    },
    resultStatusLabel(status) {
      return ({ DOING: '进行中', EXCEEDED: '超额完成', ONTIME: '按时完成', DELAYED: '延期完成', UNDONE: '未完成' })[status] || '未定义状态'
    },
    statusClass(status) {
      return `is-${String(status || '').toLowerCase().replace('_', '-')}`
    },
    number(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Number(number.toFixed(2)) : 0
    },
    currentIsoWeek() {
      return this.isoWeek(new Date())
    },
    firstContainedIsoWeek(period) {
      const match = /^(\d{4})-(\d{2})$/.exec(period || '')
      if (!match) return this.currentIsoWeek()
      const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1))
      const day = date.getUTCDay() || 7
      if (day !== 1) date.setUTCDate(date.getUTCDate() + (8 - day))
      return this.isoWeek(date)
    },
    nextIsoWeek(period) {
      const match = /^(\d{4})-W(\d{2})$/.exec(period || '')
      if (!match) return this.currentIsoWeek()
      const date = new Date(Date.UTC(Number(match[1]), 0, 4))
      const day = date.getUTCDay() || 7
      date.setUTCDate(date.getUTCDate() - day + 1 + (Number(match[2]) - 1) * 7 + 7)
      return this.isoWeek(date)
    },
    nextContainedIsoWeek(period) {
      let candidate = this.nextIsoWeek(period)
      for (let attempt = 0; attempt < 2; attempt++) {
        const month = this.monthForIsoWeek(candidate)
        if (this.weekWithinMonth(candidate, month)) return candidate
        candidate = this.nextIsoWeek(candidate)
      }
      return candidate
    },
    monthForIsoWeek(period) {
      const monday = this.isoWeekMonday(period)
      if (!monday) return this.currentPeriod()
      monday.setUTCDate(monday.getUTCDate() + 3)
      return `${monday.getUTCFullYear()}-${String(monday.getUTCMonth() + 1).padStart(2, '0')}`
    },
    weekWithinMonth(period, month) {
      const monday = this.isoWeekMonday(period)
      const match = /^(\d{4})-(\d{2})$/.exec(month || '')
      if (!monday || !match) return false
      const sunday = new Date(monday.getTime())
      sunday.setUTCDate(sunday.getUTCDate() + 6)
      const start = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1))
      const end = new Date(Date.UTC(Number(match[1]), Number(match[2]), 0))
      return monday >= start && sunday <= end
    },
    isoWeekMonday(period) {
      const match = /^(\d{4})-W(\d{2})$/.exec(period || '')
      if (!match) return null
      const date = new Date(Date.UTC(Number(match[1]), 0, 4))
      const day = date.getUTCDay() || 7
      date.setUTCDate(date.getUTCDate() - day + 1 + (Number(match[2]) - 1) * 7)
      return date
    },
    isoWeek(input) {
      const date = new Date(Date.UTC(input.getUTCFullYear(), input.getUTCMonth(), input.getUTCDate()))
      const day = date.getUTCDay() || 7
      date.setUTCDate(date.getUTCDate() + 4 - day)
      const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1))
      const week = Math.ceil((((date - yearStart) / 86400000) + 1) / 7)
      return `${date.getUTCFullYear()}-W${String(week).padStart(2, '0')}`
    }
  }
}
</script>

<style lang="scss" scoped>
.task-page { min-height: calc(100vh - 84px); padding: 24px 28px 40px; }
.task-page__header { display: flex; align-items: flex-end; justify-content: space-between; padding: 22px 24px; color: #fff; background: var(--lab-indigo-deep); }
.task-page__header h1 { margin: 6px 0 5px; font-size: 25px; }
.task-page__header p { margin: 0; color: #c5cee0; font-size: 11px; }
.task-page__header-actions { display: flex; gap: 8px; }
.task-page__filters { margin-top: 15px; padding: 17px 18px 10px; }
.task-page__scope-tabs { display: flex; gap: 2px; margin-bottom: 13px; border-bottom: 1px solid var(--lab-line); }
.task-page__scope-tabs button { padding: 8px 14px; border: 0; border-bottom: 3px solid transparent; color: var(--lab-ink-soft); background: transparent; font-size: 11px; cursor: pointer; }
.task-page__scope-tabs button.is-active { border-color: var(--lab-teal); color: var(--lab-indigo); font-weight: 700; }
.task-page__filters .el-form-item { margin-bottom: 8px; }
.task-page__filters .el-input, .task-page__filters .el-select, .task-page__filters .el-date-editor { width: 150px; }
.task-page__saved { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; padding-top: 10px; border-top: 1px solid #edf0f5; }
.task-page__saved > span { margin-right: 4px; color: #5f6b80; font-size: 9px; }
.task-page__saved button { padding: 4px 7px; border: 1px solid var(--lab-line); color: var(--lab-ink-soft); background: #f8f9fb; font-size: 9px; cursor: pointer; }
.task-page__saved button.is-add { border-style: dashed; color: var(--lab-teal); background: #fff; }
.task-page__drill-filter { display: flex; align-items: center; gap: 7px; margin: 2px 0 8px; padding: 8px 10px; color: #244263; background: #eaf3f7; font-size: 10px; }
.task-page__drill-filter button { margin-left: auto; border: 0; color: #075f5a; background: transparent; font-weight: 700; cursor: pointer; }
.task-readiness { margin-top: 15px; padding: 18px 20px; }
.task-readiness__title { display: flex; align-items: flex-end; justify-content: space-between; }
.task-readiness__title h2 { margin: 4px 0 0; font-size: 16px; }
.task-readiness__bars { display: grid; grid-template-columns: repeat(2, 1fr); gap: 35px; margin-top: 15px; }
.task-readiness__bars p { display: flex; justify-content: space-between; margin: 0 0 8px; color: var(--lab-ink-soft); font-size: 10px; }
.task-readiness__bars strong { color: var(--lab-warning); }
.task-readiness__bars strong.is-ready { color: var(--lab-teal); }
.task-readiness__bars small { display: block; margin-top: 7px; color: #5f6b80; font-size: 9px; }
.task-table { margin-top: 15px; overflow: hidden; }
.task-table__header { display: flex; align-items: flex-end; justify-content: space-between; padding: 18px 20px 14px; border-bottom: 1px solid var(--lab-line); }
.task-table__header h2 { margin: 4px 0 0; font-size: 17px; }
.task-table__header > span { color: #5f6b80; font-size: 9px; }
.task-table__task { display: grid; grid-template-columns: 28px minmax(0, 1fr); align-items: center; gap: 9px; }
.task-table__task > span { display: inline-flex; width: 25px; height: 25px; align-items: center; justify-content: center; color: #fff; background: var(--lab-indigo); font-size: 9px; }
.task-table__task > span.is-week { color: #075f5a; background: #d9f0ed; }
.task-table__task strong, .task-table__task small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-table__task strong { color: var(--lab-ink); font-size: 11px; }
.task-table__task small { margin-top: 3px; color: #5f6b80; font-size: 9px; }
.task-table__weights { display: flex; gap: 5px; }
.task-table__weights span { padding: 3px 5px; color: var(--lab-indigo); background: #edf1f6; font-size: 9px; }
.task-table__status { display: inline-block; padding: 3px 6px; color: #5b6576; background: #e9edf3; font-size: 9px; }
.task-table__status.is-active, .task-table__status.is-confirmed { color: #075f5a; background: #d9f0ed; }
.task-table__status.is-pending-review { color: #8a5b10; background: #fff0ce; }
.task-table__result { display: block; margin-top: 4px; color: #5f6b80; font-size: 8px; }
.task-table__risk { color: var(--lab-danger); font-size: 10px; font-weight: 700; }
.task-table__empty { display: flex; min-height: 180px; align-items: center; justify-content: center; flex-direction: column; gap: 7px; color: var(--lab-ink-soft); }
.task-table__empty i { font-size: 23px; }
::v-deep .pagination-container { margin: 0; padding: 14px 20px !important; border-top: 1px solid var(--lab-line); background: #fff; }
@media (max-width: 900px) {
  .task-page__header { align-items: flex-start; flex-direction: column; gap: 17px; }
  .task-readiness__bars { grid-template-columns: 1fr; gap: 18px; }
}
@media (max-width: 680px) { .task-page { padding: 14px; } .task-page__header-actions { flex-wrap: wrap; } }
</style>
