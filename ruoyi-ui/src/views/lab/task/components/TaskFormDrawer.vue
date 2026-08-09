<template>
  <el-drawer
    class="task-form"
    :visible="visible"
    :size="drawerSize"
    :wrapper-closable="false"
    :with-header="false"
    @close="$emit('close')"
  >
    <div class="task-form__shell">
      <header class="task-form__header">
        <button type="button" aria-label="关闭" @click="$emit('close')"><i class="el-icon-close" /></button>
        <span class="lab-eyebrow">任务事实卡</span>
        <h2>{{ form.id ? (isWeekly ? '编辑周承诺' : '编辑月任务事实') : (isWeekly ? '新增本周承诺' : '新增月任务') }}</h2>
        <p>{{ isWeekly ? '周承诺只维护所属月结果、交付物、截止日期和必要协同。' : '先维护计划字段，再按实际发生逐步补充协同、结果、证据和阻塞事实。' }}</p>
      </header>

      <el-alert v-if="conflictNotice" class="task-form__notice" type="warning" :closable="false" show-icon>
        {{ conflictNotice }}
      </el-alert>
      <el-alert v-if="locked" class="task-form__notice" type="info" :closable="false" show-icon>
        当前任务已进入审核、确认或关期状态，计划内容只读。
      </el-alert>

      <el-form ref="form" class="task-form__body" :model="form" :rules="rules" label-position="top">
        <section class="task-form__section">
          <div class="task-form__section-title"><span>01</span><div><h3>任务身份</h3><p>月任务承接目标，周任务承接月任务。</p></div></div>
          <div class="task-form__grid">
            <el-form-item v-if="!isWeekly" label="任务层级" prop="taskLevel" :error="errorFor('taskLevel')">
              <el-radio-group v-model="form.taskLevel" :disabled="structureLocked" @change="handleLevelChange">
                <el-radio-button label="month">月度</el-radio-button>
                <el-radio-button label="week">周度</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="!isWeekly" label="任务类型" prop="taskType" :error="errorFor('taskType')">
              <el-select v-model="form.taskType" :disabled="structureLocked" @change="normalizeWeights">
                <el-option label="重点任务" value="key" />
                <el-option label="日常任务" value="daily" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!isWeekly" label="月度周期" prop="period" :error="errorFor('period')">
              <el-date-picker v-model="form.period" type="month" value-format="yyyy-MM" format="yyyy 年 MM 月" :disabled="structureLocked" />
            </el-form-item>
            <el-form-item v-else label="ISO 周期" prop="period" :error="errorFor('period')">
              <el-input v-model.trim="form.period" placeholder="例如 2026-W32" :disabled="structureLocked" />
            </el-form-item>
            <el-form-item v-if="isWeekly" label="所属月结果" prop="parentId" :error="errorFor('parentId')">
              <el-select v-model="form.parentId" filterable :disabled="structureLocked" @change="inheritMonthTask">
                <el-option v-for="monthTask in monthTasks" :key="monthTask.id" :label="`${monthTask.period} · ${monthTask.title}`" :value="monthTask.id" />
              </el-select>
            </el-form-item>
            <template v-if="!isWeekly">
              <el-form-item label="年度目标" prop="goalId" :error="errorFor('goalId')">
                <el-select v-model="form.goalId" filterable :disabled="structureLocked" @change="form.milestoneId = null">
                  <el-option v-for="goal in annualGoals" :key="goal.id" :label="goal.title" :value="goal.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="季度里程碑" prop="milestoneId" :error="errorFor('milestoneId')">
                <el-select v-model="form.milestoneId" filterable :disabled="structureLocked">
                  <el-option v-for="goal in milestoneOptions" :key="goal.id" :label="`${goal.period} · ${goal.title}`" :value="goal.id" />
                </el-select>
              </el-form-item>
            </template>
          </div>
        </section>

        <section class="task-form__section">
          <div class="task-form__section-title"><span>02</span><div><h3>计划合同</h3><p>标题、负责人、日期和交付物是激活前置条件。</p></div></div>
          <el-form-item label="任务标题" prop="title" :error="errorFor('title')"><el-input v-model.trim="form.title" maxlength="255" show-word-limit :disabled="locked" /></el-form-item>
          <div class="task-form__grid">
            <el-form-item v-if="!isWeekly" label="负责人" prop="ownerId" :error="errorFor('ownerId')">
              <el-select v-model="form.ownerId" filterable :disabled="structureLocked" @change="syncOwner">
                <el-option v-for="owner in taskOwners" :key="owner.id" :label="ownerLabel(owner)" :value="owner.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!isWeekly" label="业务线" prop="bizLine" :error="errorFor('bizLine')"><el-input v-model="form.bizLine" disabled /></el-form-item>
            <el-form-item v-if="!isWeekly" label="部门 ID" prop="deptId" :error="errorFor('deptId')"><el-input-number v-model="form.deptId" :min="1" :disabled="structureLocked" /></el-form-item>
            <el-form-item :label="isWeekly ? '承诺完成日期' : '计划完成日期'" prop="planDate" :error="errorFor('planDate')"><el-date-picker v-model="form.planDate" type="date" value-format="yyyy-MM-dd" :disabled="structureLocked" /></el-form-item>
          </div>
          <el-form-item label="交付物" prop="deliverable" :error="errorFor('deliverable')"><el-input v-model.trim="form.deliverable" type="textarea" :rows="2" maxlength="1000" show-word-limit :disabled="locked" /></el-form-item>
        </section>

        <section v-if="!isWeekly" class="task-form__section">
          <div class="task-form__section-title"><span>03</span><div><h3>双权重</h3><p>仅月度重点任务参与；绩效权重与目标权重各自形成 100% 合同。</p></div></div>
          <div class="task-form__grid">
            <el-form-item label="绩效权重（%）" prop="perfWeight" :error="errorFor('perfWeight')">
              <el-input-number v-model="form.perfWeight" :min="0" :max="100" :precision="2" :disabled="!isKeyMonth || structureLocked" />
            </el-form-item>
            <el-form-item label="目标权重（%）" prop="goalWeight" :error="errorFor('goalWeight')">
              <el-input-number v-model="form.goalWeight" :min="0" :max="100" :precision="2" :disabled="!isKeyMonth || structureLocked" />
            </el-form-item>
          </div>
        </section>

        <section class="task-form__section">
          <div class="task-form__section-title"><span>04</span><div><h3>协同需求</h3><p>只有勾选需要协同时才展开必填字段。</p></div></div>
          <el-form-item label="是否需要协同" prop="coordinationRequired">
            <el-switch v-model="form.coordinationRequired" active-value="1" inactive-value="0" :disabled="locked" />
          </el-form-item>
          <div v-if="form.coordinationRequired === '1'" class="task-form__coordination">
            <div class="task-form__grid">
              <el-form-item label="协同负责人" prop="coordinationOwnerId" :error="errorFor('coordinationOwnerId')">
                <el-select v-model="form.coordinationOwnerId" filterable :disabled="locked">
                  <el-option v-for="owner in owners" :key="owner.id" :label="ownerLabel(owner)" :value="owner.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="协同部门 ID" prop="coordinationDeptId" :error="errorFor('coordinationDeptId')">
                <el-input-number v-model="form.coordinationDeptId" :min="1" :disabled="locked" />
              </el-form-item>
            </div>
            <el-form-item label="协同内容" prop="coordinationContent" :error="errorFor('coordinationContent')"><el-input v-model.trim="form.coordinationContent" :disabled="locked" /></el-form-item>
            <el-form-item label="所需支持" prop="coordinationSupport" :error="errorFor('coordinationSupport')"><el-input v-model.trim="form.coordinationSupport" type="textarea" :rows="2" :disabled="locked" /></el-form-item>
          </div>
        </section>
      </el-form>

      <footer class="task-form__footer">
        <el-button @click="$emit('close')">取消</el-button>
        <el-button v-if="!locked" v-hasPermi="['lab:task:add', 'lab:task:edit']" type="primary" :loading="saving" @click="submit">保存草稿</el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<script>
const newTask = () => ({
  id: null,
  version: null,
  parentId: null,
  goalId: null,
  milestoneId: null,
  taskLevel: 'month',
  period: '',
  bizLine: '',
  taskType: 'key',
  title: '',
  ownerId: null,
  deptId: null,
  planDate: '',
  deliverable: '',
  perfWeight: 0,
  goalWeight: 0,
  coordinationRequired: '0',
  coordinationOwnerId: null,
  coordinationDeptId: null,
  coordinationContent: '',
  coordinationSupport: '',
  workflowStatus: 'DRAFT',
  periodLockFlag: '0'
})

export default {
  name: 'TaskFormDrawer',
  props: {
    visible: { type: Boolean, default: false },
    task: { type: Object, default: null },
    owners: { type: Array, default: () => [] },
    taskOwners: { type: Array, default: () => [] },
    goals: { type: Array, default: () => [] },
    monthTasks: { type: Array, default: () => [] },
    saving: { type: Boolean, default: false },
    fieldErrors: { type: Object, default: () => ({}) },
    conflictNotice: { type: String, default: '' }
  },
  data() {
    return {
      form: newTask(),
      viewportWidth: window.innerWidth,
      rules: {
        taskLevel: [{ required: true, message: '请选择任务层级', trigger: 'change' }],
        taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
        period: [{ required: true, message: '请输入任务周期', trigger: 'blur' }],
        parentId: [{ required: true, message: '请选择所属月任务', trigger: 'change' }],
        goalId: [{ required: true, message: '请选择年度目标', trigger: 'change' }],
        milestoneId: [{ required: true, message: '请选择季度里程碑', trigger: 'change' }],
        title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
        ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
        bizLine: [{ required: true, message: '负责人必须配置业务线', trigger: 'change' }],
        planDate: [{ required: true, message: '请选择计划完成日期', trigger: 'change' }],
        deliverable: [{ required: true, message: '请输入交付物', trigger: 'blur' }],
        coordinationOwnerId: [{ required: true, message: '请选择协同负责人', trigger: 'change' }],
        coordinationDeptId: [{ required: true, message: '请输入协同部门', trigger: 'change' }],
        coordinationContent: [{ required: true, message: '请输入协同内容', trigger: 'blur' }],
        coordinationSupport: [{ required: true, message: '请输入所需支持', trigger: 'blur' }]
      }
    }
  },
  computed: {
    drawerSize() {
      return this.viewportWidth < 780 ? '100%' : '720px'
    },
    locked() {
      return this.form.periodLockFlag === '1' || ['PENDING_REVIEW', 'CONFIRMED'].indexOf(this.form.workflowStatus) >= 0
    },
    structureLocked() {
      return this.locked || (this.form.id && this.form.workflowStatus !== 'DRAFT')
    },
    isKeyMonth() {
      return this.form.taskLevel === 'month' && this.form.taskType === 'key'
    },
    isWeekly() {
      return this.form.taskLevel === 'week'
    },
    annualGoals() {
      return this.goals.filter(goal => goal.goalLevel === 'YEAR')
    },
    milestoneOptions() {
      return this.goals.filter(goal => goal.goalLevel === 'QUARTER' && String(goal.parentId) === String(this.form.goalId))
    }
  },
  watch: {
    visible(value) {
      if (value) this.reset()
    },
    task: {
      deep: true,
      handler() {
        if (this.visible) this.reset()
      }
    }
  },
  mounted() {
    window.addEventListener('resize', this.updateWidth)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateWidth)
  },
  methods: {
    reset() {
      this.form = Object.assign(newTask(), this.task ? JSON.parse(JSON.stringify(this.task)) : {})
      this.normalizeWeights()
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    updateWidth() {
      this.viewportWidth = window.innerWidth
    },
    handleLevelChange() {
      this.form.parentId = null
      this.form.goalId = null
      this.form.milestoneId = null
      this.normalizeWeights()
    },
    normalizeWeights() {
      if (this.form.taskLevel !== 'month' || this.form.taskType !== 'key') {
        this.form.perfWeight = 0
        this.form.goalWeight = 0
      }
    },
    inheritMonthTask(id) {
      const parent = this.monthTasks.find(task => String(task.id) === String(id))
      if (!parent) return
      this.form.goalId = parent.goalId
      this.form.milestoneId = parent.milestoneId
      this.form.ownerId = parent.ownerId
      this.form.bizLine = parent.bizLine
      this.form.deptId = parent.deptId
      this.form.taskType = parent.taskType
      this.form.period = this.firstContainedIsoWeek(parent.period)
      this.normalizeWeights()
    },
    firstContainedIsoWeek(period) {
      const match = /^(\d{4})-(\d{2})$/.exec(period || '')
      if (!match) return this.form.period
      const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1))
      const day = date.getUTCDay() || 7
      if (day !== 1) date.setUTCDate(date.getUTCDate() + (8 - day))
      date.setUTCDate(date.getUTCDate() + 3)
      const year = date.getUTCFullYear()
      const yearStart = new Date(Date.UTC(year, 0, 1))
      const week = Math.ceil((((date - yearStart) / 86400000) + 1) / 7)
      return `${year}-W${String(week).padStart(2, '0')}`
    },
    syncOwner(id) {
      const owner = this.owners.find(item => String(item.id) === String(id))
      if (owner) this.form.bizLine = owner.bizLine || ''
    },
    ownerLabel(owner) {
      return `${owner.nickName || owner.userName || owner.memberNo} · ${owner.bizLine || '无业务线'}`
    },
    errorFor(field) {
      return this.fieldErrors[field] || ''
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.$emit('save', JSON.parse(JSON.stringify(this.form)))
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.task-form__shell { min-height: 100%; padding-bottom: 76px; background: var(--lab-canvas); }
.task-form__header { position: relative; padding: 27px 30px 22px; color: #fff; background: var(--lab-indigo-deep); }
.task-form__header > button { position: absolute; top: 18px; right: 18px; border: 0; color: #fff; background: transparent; font-size: 18px; cursor: pointer; }
.task-form__header h2 { margin: 7px 0 5px; font-size: 22px; }
.task-form__header p { margin: 0; color: #c5cee0; font-size: 10px; }
.task-form__notice { width: auto; margin: 14px 20px 0; }
.task-form__body { padding: 5px 20px 22px; }
.task-form__section { margin-top: 14px; padding: 18px 19px 8px; border: 1px solid var(--lab-line); background: #fff; }
.task-form__section-title { display: flex; align-items: center; gap: 11px; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #edf0f5; }
.task-form__section-title > span { color: #bcc5d3; font-family: 'Arial Narrow', Arial, sans-serif; font-size: 25px; }
.task-form__section-title h3, .task-form__section-title p { margin: 0; }
.task-form__section-title h3 { font-size: 14px; }
.task-form__section-title p { margin-top: 3px; color: #5f6b80; font-size: 9px; }
.task-form__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.task-form__grid .el-select, .task-form__grid .el-date-editor, .task-form__grid .el-input-number { width: 100%; }
.task-form__coordination { padding: 13px 14px 1px; border-left: 3px solid var(--lab-teal); background: #f4f9f8; }
.task-form__footer { position: absolute; right: 0; bottom: 0; left: 0; z-index: 2; display: flex; justify-content: flex-end; gap: 8px; padding: 14px 20px; border-top: 1px solid var(--lab-line); background: #fff; }
@media (max-width: 620px) { .task-form__grid { grid-template-columns: 1fr; } }
</style>
