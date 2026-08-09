<template>
  <main class="goal-page lab-dashboard">
    <header class="goal-page__header">
      <div>
        <span class="lab-eyebrow">战略目标台账</span>
        <h1>目标与里程碑</h1>
        <p>年度目标 → 季度里程碑 → 月度重点任务 → 周度执行事实</p>
      </div>
      <div class="goal-page__filters">
        <el-select v-model="query.year" aria-label="目标年度" @change="loadTree">
          <el-option v-for="year in years" :key="year" :label="`${year} 年`" :value="year" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="全部状态" aria-label="目标状态" @change="loadTree">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已激活" value="ACTIVE" />
        </el-select>
        <el-button icon="el-icon-refresh" :loading="treeLoading" @click="loadTree">刷新</el-button>
        <el-button v-if="isManager" v-hasPermi="['lab:goal:add']" type="primary" icon="el-icon-plus" @click="openCreate('YEAR')">新增年度目标</el-button>
      </div>
    </header>

    <div class="goal-page__body">
      <goal-tree
        :nodes="goals"
        :selected-id="selectedId"
        :loading="treeLoading"
        :can-add-child="canCreateQuarter"
        @select="openDetail"
        @add-child="openCreate('QUARTER', $event)"
      />
      <section class="goal-page__canvas lab-panel">
        <div class="goal-page__blueprint">
          <span class="goal-page__ordinal">01</span>
          <div><span class="lab-eyebrow">选择目标</span><h2>在左侧目标树中保持战略上下文</h2></div>
        </div>
        <div class="goal-page__levels">
          <div><b>年</b><strong>年度目标</strong><span>明确结果与验收标准</span></div>
          <i class="el-icon-right" />
          <div><b>季</b><strong>季度里程碑</strong><span>权重合计 100%</span></div>
          <i class="el-icon-right" />
          <div><b>月</b><strong>重点任务</strong><span>目标权重合计 100%</span></div>
          <i class="el-icon-right" />
          <div><b>周</b><strong>执行事实</strong><span>由确认结果聚合进度</span></div>
        </div>
        <div class="goal-page__formula">
          <span>聚合公式</span>
          <p>年度进度 = Σ（季度里程碑权重 × 里程碑进度）</p>
          <p>里程碑进度 = Σ（月度重点任务目标权重 × 已确认完成比例）</p>
        </div>
      </section>
    </div>

    <goal-detail-drawer
      :visible="detailVisible"
      :goal="selectedGoal"
      :progress="selectedProgress"
      :health="selectedHealth"
      :related-tasks="relatedTasks"
      :owners="owners"
      :readiness="readiness"
      :loading="detailLoading"
      :can-edit="canWriteGoal(selectedGoal)"
      :can-add-child="canAddChild(selectedGoal)"
      :can-activate="canWriteGoal(selectedGoal)"
      :can-delete="canWriteGoal(selectedGoal)"
      @close="detailVisible = false"
      @edit="openEdit"
      @add-child="openCreate('QUARTER', $event)"
      @activate="handleActivate"
      @delete="handleDelete"
    />

    <el-dialog :title="goalForm.id ? '编辑目标' : '新增目标'" :visible.sync="formVisible" width="650px" append-to-body>
      <el-alert v-if="conflictNotice" class="goal-page__conflict" type="warning" :closable="false" show-icon>
        {{ conflictNotice }}
      </el-alert>
      <el-form ref="goalForm" :model="goalForm" :rules="rules" label-position="top">
        <div class="goal-page__form-grid">
          <el-form-item label="目标层级" prop="goalLevel">
            <el-radio-group v-model="goalForm.goalLevel" :disabled="Boolean(goalForm.id)">
              <el-radio-button label="YEAR">年度目标</el-radio-button>
              <el-radio-button label="QUARTER">季度里程碑</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="年度" prop="year">
            <el-select v-model="goalForm.year" :disabled="goalForm.goalLevel === 'QUARTER'">
              <el-option v-for="year in years" :key="year" :label="year" :value="year" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="goalForm.goalLevel === 'QUARTER'" label="所属年度目标" prop="parentId">
            <el-select v-model="goalForm.parentId" filterable @change="syncParentYear">
              <el-option v-for="goal in annualGoals" :key="goal.id" :label="goal.title" :value="goal.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="goalForm.goalLevel === 'QUARTER'" label="季度" prop="period">
            <el-select v-model="goalForm.period">
              <el-option v-for="quarter in quarters" :key="quarter" :label="quarter" :value="quarter" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标编号" prop="goalNo"><el-input v-model.trim="goalForm.goalNo" maxlength="64" /></el-form-item>
          <el-form-item label="负责人" prop="ownerId">
            <el-select v-model="goalForm.ownerId" filterable>
              <el-option v-for="owner in editableOwners" :key="owner.id" :label="ownerLabel(owner)" :value="owner.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="goalForm.goalLevel === 'QUARTER'" label="里程碑权重（%）" prop="weight"><el-input-number v-model="goalForm.weight" :min="0" :max="100" :precision="2" /></el-form-item>
          <el-form-item label="进度模式" prop="progressMode">
            <el-select v-model="goalForm.progressMode" disabled><el-option label="任务聚合（服务端计算）" value="TASK" /></el-select>
          </el-form-item>
        </div>
        <el-form-item label="目标标题" prop="title"><el-input v-model.trim="goalForm.title" maxlength="255" show-word-limit /></el-form-item>
        <el-form-item label="目标值" prop="targetValue"><el-input v-model.trim="goalForm.targetValue" maxlength="1000" /></el-form-item>
        <el-form-item label="验收标准" prop="acceptCriteria"><el-input v-model.trim="goalForm.acceptCriteria" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="进展说明" prop="progressDesc"><el-input v-model.trim="goalForm.progressDesc" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGoal">保存草稿</el-button>
      </span>
    </el-dialog>
  </main>
</template>

<script>
import { getDashboardOverview } from '@/api/lab/dashboard'
import { activateGoal, addGoal, deleteGoal, getGoal, getGoalProgress, getGoalTree, updateGoal } from '@/api/lab/goal'
import { listTaskOwners, listTasks } from '@/api/lab/task'
import GoalTree from './components/GoalTree'
import GoalDetailDrawer from './components/GoalDetailDrawer'

const freshGoal = () => ({
  id: null,
  version: null,
  parentId: 0,
  goalLevel: 'YEAR',
  year: new Date().getFullYear(),
  period: '',
  goalNo: '',
  title: '',
  targetValue: '',
  acceptCriteria: '',
  ownerId: null,
  weight: 0,
  progressMode: 'TASK',
  progressDesc: ''
})

export default {
  name: 'LabGoal',
  components: { GoalTree, GoalDetailDrawer },
  data() {
    const currentYear = new Date().getFullYear()
    return {
      query: { year: Number(this.$route.query.year) || currentYear, status: this.$route.query.status || '' },
      years: [currentYear - 1, currentYear, currentYear + 1, currentYear + 2],
      goals: [],
      goalHealth: [],
      owners: [],
      selectedId: null,
      selectedGoal: null,
      selectedProgress: 0,
      relatedTasks: [],
      treeLoading: false,
      detailLoading: false,
      detailVisible: false,
      formVisible: false,
      saving: false,
      conflictNotice: '',
      detailRequest: 0,
      goalForm: freshGoal(),
      rules: {
        goalLevel: [{ required: true, message: '请选择目标层级', trigger: 'change' }],
        year: [{ required: true, message: '请选择年度', trigger: 'change' }],
        parentId: [{ required: true, message: '请选择年度目标', trigger: 'change' }],
        period: [{ required: true, message: '请选择季度', trigger: 'change' }],
        goalNo: [{ required: true, message: '请输入目标编号', trigger: 'blur' }],
        title: [{ required: true, message: '请输入目标标题', trigger: 'blur' }],
        ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
        weight: [{ required: true, message: '请输入权重', trigger: 'change' }]
      }
    }
  },
  computed: {
    annualGoals() {
      return this.goals.filter(goal => goal.goalLevel === 'YEAR')
    },
    currentMember() {
      const userId = this.$store.state.user.id
      return this.owners.find(owner => String(owner.userId) === String(userId)) || null
    },
    isManager() {
      return (this.$store.state.user.roles || []).indexOf('lab_manager') >= 0
    },
    canCreateQuarter() {
      const roles = this.$store.state.user.roles || []
      return this.isManager || (roles.indexOf('lab_lead') >= 0 && Boolean(this.currentMember))
    },
    editableOwners() {
      return this.isManager ? this.owners : this.currentMember ? [this.currentMember] : []
    },
    quarters() {
      return [1, 2, 3, 4].map(value => `${this.goalForm.year}Q${value}`)
    },
    readiness() {
      if (!this.selectedGoal) return { total: 0, ready: false, hint: '请选择目标。' }
      let rows = []
      let hint = ''
      if (this.selectedGoal.goalLevel === 'YEAR') {
        const node = this.findGoal(this.selectedGoal.id)
        rows = node && node.children ? node.children : []
        hint = '激活前，已配置的季度里程碑权重必须合计 100%。'
      } else {
        rows = this.relatedTasks.filter(task => task.taskLevel === 'month' && task.taskType === 'key')
        hint = '激活前，本季度月度重点任务的目标权重必须合计 100%。'
      }
      const total = rows.reduce((sum, row) => sum + Number(this.selectedGoal.goalLevel === 'YEAR' ? row.weight || 0 : row.goalWeight || 0), 0)
      return { total: Number(total.toFixed(2)), ready: Math.abs(total - 100) < 0.001, hint }
    },
    selectedHealth() {
      if (!this.selectedGoal) return null
      const goalId = this.selectedGoal.goalLevel === 'YEAR' ? this.selectedGoal.id : this.selectedGoal.parentId
      return this.goalHealth.find(item => String(item.goalId) === String(goalId)) || null
    }
  },
  created() {
    this.loadOwners()
    this.loadTree().then(() => {
      const routeId = this.$route.query.goalId || String(this.$route.query.goalIds || '').split(',')[0]
      if (routeId) {
        const node = this.findGoal(routeId)
        if (node) this.openDetail(node)
      }
    })
  },
  methods: {
    loadOwners() {
      return listTaskOwners({ memberStatus: 'ACTIVE', pageNum: 1, pageSize: 500 })
        .then(response => { this.owners = response.rows || [] })
        .catch(() => { this.owners = [] })
    },
    loadTree() {
      this.treeLoading = true
      const params = { year: this.query.year }
      if (this.query.status) params.status = this.query.status
      const goalIds = this.routeList(this.$route.query.goalIds)
      if (String(this.$route.query.goalIdsFilter).toLowerCase() === 'true') {
        params.goalIdsFilter = true
        if (goalIds.length) params.goalIds = goalIds.join(',')
      }
      this.loadHealth()
      return getGoalTree(params)
        .then(response => {
          this.goals = Array.isArray(response.data) ? response.data : []
          let selectionCleared = false
          if (this.selectedId && !this.findGoal(this.selectedId)) {
            this.selectedId = null
            this.selectedGoal = null
            this.detailVisible = false
            selectionCleared = true
          }
          const query = Object.assign({}, this.$route.query, { year: String(this.query.year) })
          if (this.query.status) query.status = this.query.status
          else delete query.status
          if (selectionCleared) delete query.goalId
          const route = this.$router.replace({ query })
          if (route && route.catch) route.catch(() => {})
        })
        .finally(() => { this.treeLoading = false })
    },
    loadHealth() {
      const now = new Date()
      const month = this.query.year === now.getFullYear() ? now.getMonth() + 1 : this.query.year < now.getFullYear() ? 12 : 1
      const period = `${this.query.year}-${String(month).padStart(2, '0')}`
      return getDashboardOverview(period)
        .then(response => { this.goalHealth = response.data && Array.isArray(response.data.goalHealth) ? response.data.goalHealth : [] })
        .catch(() => { this.goalHealth = [] })
    },
    openDetail(node) {
      const requestId = ++this.detailRequest
      this.selectedId = node.id
      this.detailVisible = true
      this.detailLoading = true
      const level = node.goalLevel
      const taskParams = level === 'YEAR'
        ? { goalId: node.id, pageNum: 1, pageSize: 500 }
        : { milestoneId: node.id, pageNum: 1, pageSize: 500 }
      const route = this.$router.replace({ query: Object.assign({}, this.$route.query, { goalId: String(node.id), year: String(this.query.year) }) })
      if (route && route.catch) route.catch(() => {})
      return Promise.all([getGoal(node.id), getGoalProgress(node.id, level), listTasks(taskParams)])
        .then(results => {
          if (requestId !== this.detailRequest) return
          this.selectedGoal = results[0].data
          this.selectedProgress = results[1].data
          this.relatedTasks = results[2].rows || []
        })
        .finally(() => {
          if (requestId === this.detailRequest) this.detailLoading = false
        })
    },
    openCreate(level, parent) {
      if (level === 'YEAR' && !this.isManager) return
      if (level === 'QUARTER' && !this.canCreateQuarter) return
      this.conflictNotice = ''
      this.goalForm = freshGoal()
      this.goalForm.goalLevel = level
      this.goalForm.year = parent ? parent.year : this.query.year
      this.goalForm.parentId = parent ? parent.id : 0
      if (!this.isManager && this.currentMember) this.goalForm.ownerId = this.currentMember.id
      if (level === 'QUARTER') this.goalForm.period = `${this.goalForm.year}Q1`
      this.formVisible = true
      this.$nextTick(() => this.$refs.goalForm && this.$refs.goalForm.clearValidate())
    },
    openEdit(goal) {
      if (!this.canWriteGoal(goal)) return
      this.conflictNotice = ''
      this.goalForm = Object.assign(freshGoal(), JSON.parse(JSON.stringify(goal)))
      this.formVisible = true
      this.$nextTick(() => this.$refs.goalForm && this.$refs.goalForm.clearValidate())
    },
    syncParentYear(parentId) {
      const parent = this.annualGoals.find(goal => String(goal.id) === String(parentId))
      if (!parent) return
      this.goalForm.year = parent.year
      this.goalForm.period = `${parent.year}Q1`
    },
    saveGoal() {
      this.$refs.goalForm.validate(valid => {
        if (!valid) return
        this.saving = true
        const payload = JSON.parse(JSON.stringify(this.goalForm))
        const action = payload.id ? updateGoal(payload) : addGoal(payload)
        action.then(() => {
          this.$message.success('目标草稿已保存')
          this.formVisible = false
          return this.loadTree()
        }).then(() => {
          if (payload.id) {
            const node = this.findGoal(payload.id)
            if (node) this.openDetail(node)
          }
        }).catch(error => {
          if (payload.id && this.isConflict(error)) this.recoverConflict(payload)
        }).finally(() => { this.saving = false })
      })
    },
    recoverConflict(draft) {
      getGoal(draft.id).then(response => {
        const latest = response.data
        this.$confirm('服务器上的目标已更新。可保留当前输入并基于最新版本重试，或载入服务器版本。', '版本冲突', {
          confirmButtonText: '保留输入',
          cancelButtonText: '载入服务器版本',
          type: 'warning'
        }).then(() => {
          this.goalForm.version = latest.version
          this.conflictNotice = `已将本地草稿重新基于服务器 v${latest.version}，请核对后再次保存。`
        }).catch(() => {
          this.goalForm = Object.assign(freshGoal(), latest)
          this.conflictNotice = `已载入服务器 v${latest.version}。`
        })
      })
    },
    handleActivate(goal) {
      if (!this.canWriteGoal(goal)) return
      if (!this.readiness.ready) {
        this.$message.warning(this.readiness.hint)
        return
      }
      this.$confirm('激活后结构和权重将受业务规则约束，确认继续？', '激活目标', { type: 'warning' })
        .then(() => activateGoal(goal.id, goal.version))
        .then(() => this.afterMutation('目标已激活', goal.id))
        .catch(error => { if (this.isConflict(error)) this.openDetail(this.findGoal(goal.id) || goal) })
    },
    handleDelete(goal) {
      if (!this.canWriteGoal(goal)) return
      this.$confirm('仅草稿目标可删除，此操作不可撤销。', '删除目标', { type: 'warning' })
        .then(() => deleteGoal(goal.id, goal.version))
        .then(() => {
          this.detailVisible = false
          this.selectedGoal = null
          return this.afterMutation('目标已删除')
        })
        .catch(error => { if (this.isConflict(error)) this.loadTree() })
    },
    afterMutation(message, id) {
      this.$message.success(message)
      return this.loadTree().then(() => {
        if (!id) return
        const node = this.findGoal(id)
        if (node) return this.openDetail(node)
      })
    },
    findGoal(id, nodes) {
      const source = nodes || this.goals
      for (let index = 0; index < source.length; index++) {
        if (String(source[index].id) === String(id)) return source[index]
        const child = this.findGoal(id, source[index].children || [])
        if (child) return child
      }
      return null
    },
    ownerLabel(owner) {
      return `${owner.nickName || owner.userName || owner.memberNo} · ${owner.bizLine || '未配置业务线'}`
    },
    canWriteGoal(goal) {
      if (!goal) return false
      return this.isManager || Boolean(this.currentMember && goal.goalLevel === 'QUARTER' && String(goal.ownerId) === String(this.currentMember.id))
    },
    canAddChild(goal) {
      return Boolean(goal && goal.goalLevel === 'YEAR' && this.canCreateQuarter)
    },
    routeList(value) {
      const values = Array.isArray(value) ? value : String(value || '').split(',')
      return values.map(item => String(item).trim()).filter(item => /^\d+$/.test(item))
    },
    isConflict(error) {
      return /changed by another user|refresh and retry|版本|更新/.test(String(error && error.message))
    }
  }
}
</script>

<style lang="scss" scoped>
.goal-page { min-height: calc(100vh - 84px); padding: 24px 28px 36px; }
.goal-page__header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 16px; padding: 22px 24px; color: #fff; background: var(--lab-indigo-deep); }
.goal-page__header h1 { margin: 6px 0 5px; font-size: 25px; }
.goal-page__header p { margin: 0; color: #c5cee0; font-size: 11px; }
.goal-page__filters { display: flex; align-items: center; gap: 8px; }
.goal-page__filters .el-select { width: 118px; }
.goal-page__body { display: grid; grid-template-columns: 390px minmax(0, 1fr); gap: 16px; }
.goal-page__canvas { height: calc(100vh - 205px); min-height: 540px; padding: 34px; overflow: hidden; }
.goal-page__blueprint { display: flex; align-items: center; gap: 17px; padding-bottom: 25px; border-bottom: 1px solid var(--lab-line); }
.goal-page__ordinal { color: #cbd1dc; font-family: 'Arial Narrow', Arial, sans-serif; font-size: 44px; }
.goal-page__blueprint h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 20px; }
.goal-page__levels { display: grid; grid-template-columns: repeat(7, auto); align-items: center; justify-content: center; gap: 12px; margin: 80px auto 62px; }
.goal-page__levels div { width: 132px; padding: 20px 14px; border: 1px solid var(--lab-line); text-align: center; background: #fff; }
.goal-page__levels b { display: inline-flex; width: 30px; height: 30px; align-items: center; justify-content: center; margin-bottom: 10px; color: #fff; background: var(--lab-indigo); }
.goal-page__levels strong, .goal-page__levels span { display: block; }
.goal-page__levels strong { font-size: 12px; }
.goal-page__levels span { margin-top: 6px; color: #5f6b80; font-size: 9px; line-height: 15px; }
.goal-page__levels > i { color: var(--lab-teal); }
.goal-page__formula { max-width: 660px; margin: 0 auto; padding: 18px 22px; border-left: 3px solid var(--lab-teal); background: #f3f8f7; }
.goal-page__formula > span { color: var(--lab-teal); font-size: 9px; font-weight: 700; letter-spacing: .12em; }
.goal-page__formula p { margin: 8px 0 0; color: var(--lab-ink-soft); font-size: 11px; }
.goal-page__form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.goal-page__form-grid .el-select, .goal-page__form-grid .el-input-number { width: 100%; }
.goal-page__conflict { margin-bottom: 14px; }
@media (max-width: 1120px) {
  .goal-page__header { align-items: flex-start; flex-direction: column; gap: 18px; }
  .goal-page__body { grid-template-columns: 330px minmax(0, 1fr); }
  .goal-page__levels { grid-template-columns: 1fr; margin: 38px auto; }
  .goal-page__levels > i { transform: rotate(90deg); }
}
@media (max-width: 760px) {
  .goal-page { padding: 14px; }
  .goal-page__filters { flex-wrap: wrap; }
  .goal-page__body { grid-template-columns: 1fr; }
  .goal-page__canvas { display: none; }
  .goal-page__form-grid { grid-template-columns: 1fr; }
}
</style>
