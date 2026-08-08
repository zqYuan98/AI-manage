<template>
  <el-drawer
    class="goal-detail"
    :visible="visible"
    :size="drawerSize"
    :wrapper-closable="true"
    :with-header="false"
    @close="$emit('close')"
  >
    <div v-if="goal" class="goal-detail__shell">
      <header class="goal-detail__hero">
        <button type="button" aria-label="关闭" @click="$emit('close')"><i class="el-icon-close" /></button>
        <span class="goal-detail__code">{{ goal.goalNo }} / {{ goal.period || goal.year }}</span>
        <h2>{{ goal.title }}</h2>
        <div class="goal-detail__badges">
          <span>{{ goal.goalLevel === 'YEAR' ? '年度目标' : '季度里程碑' }}</span>
          <span :class="statusClass">{{ goal.status }}</span>
        </div>
      </header>

      <div v-if="loading" class="goal-detail__loading lab-skeleton" />
      <template v-else>
        <section class="goal-detail__metrics">
          <div><span>{{ goal.goalLevel === 'YEAR' ? '季度权重合计' : '自身权重' }}</span><strong>{{ goal.goalLevel === 'YEAR' ? number(readiness.total) : number(goal.weight) }}%</strong></div>
          <div><span>聚合进度</span><strong>{{ number(progressValue) }}%</strong></div>
          <div><span>健康状态</span><strong :class="healthClass">{{ healthLabel }}</strong></div>
          <div><span>版本</span><strong>v{{ goal.version }}</strong></div>
        </section>

        <section class="goal-detail__readiness">
          <div class="goal-detail__section-title">
            <div><span class="lab-eyebrow">Activation contract</span><h3>{{ readinessLabel }}</h3></div>
            <strong :class="{ 'is-ready': readiness.ready }">{{ number(readiness.total) }} / 100</strong>
          </div>
          <el-progress
            :percentage="readinessPercent"
            :stroke-width="8"
            :show-text="false"
            :color="readiness.ready ? '#087b75' : '#b7791f'"
          />
          <p>{{ readiness.ready ? '权重合同已就绪，可进入激活。' : readiness.hint }}</p>
        </section>

        <el-tabs v-model="activeTab" class="goal-detail__tabs">
          <el-tab-pane label="目标定义" name="definition">
            <dl class="goal-detail__definition">
              <dt>目标值</dt><dd>{{ goal.targetValue || '—' }}</dd>
              <dt>验收标准</dt><dd>{{ goal.acceptCriteria || '—' }}</dd>
              <dt>进度模式</dt><dd>{{ goal.progressMode || '任务聚合' }}</dd>
              <dt>进展说明</dt><dd>{{ goal.progressDesc || '暂无补充说明' }}</dd>
              <dt>负责人</dt><dd>{{ ownerName(goal.ownerId) }}</dd>
            </dl>
          </el-tab-pane>
          <el-tab-pane :label="`关联任务 ${relatedTasks.length}`" name="tasks">
            <div v-if="!relatedTasks.length" class="lab-empty">尚未关联月度或周度任务</div>
            <ol v-else class="goal-detail__tasks">
              <li v-for="month in taskHierarchy" :key="month.id">
                <button type="button" class="goal-detail__task-row" @click="openTask(month)">
                  <span :class="`is-${month.taskLevel}`">{{ month.taskLevel === 'week' ? '周' : '月' }}</span>
                  <span><strong>{{ month.title }}</strong><small>{{ month.period }} · {{ month.workflowStatus }}</small></span>
                  <b>{{ number(month.goalWeight) }}%</b>
                  <i class="el-icon-right" />
                </button>
                <ul v-if="month.children.length">
                  <li v-for="week in month.children" :key="week.id">
                    <button type="button" class="goal-detail__task-row is-week-row" @click="openTask(week)">
                      <span class="is-week">周</span>
                      <span><strong>{{ week.title }}</strong><small>{{ week.period }} · {{ week.workflowStatus }}</small></span>
                      <b>{{ week.resultStatus || 'DOING' }}</b>
                      <i class="el-icon-right" />
                    </button>
                  </li>
                </ul>
              </li>
            </ol>
          </el-tab-pane>
        </el-tabs>
      </template>

      <footer class="goal-detail__footer">
        <el-button v-if="canEdit" v-hasPermi="['lab:goal:edit']" :disabled="goal.status !== 'DRAFT'" @click="$emit('edit', goal)">编辑</el-button>
        <el-button v-if="goal.goalLevel === 'YEAR' && canAddChild" v-hasPermi="['lab:goal:add']" :disabled="goal.status !== 'DRAFT'" @click="$emit('add-child', goal)">新增季度</el-button>
        <el-button
          v-if="canActivate"
          v-hasPermi="['lab:goal:activate']"
          type="primary"
          :disabled="goal.status !== 'DRAFT' || !readiness.ready"
          @click="$emit('activate', goal)"
        >
          激活
        </el-button>
        <el-button v-if="canDelete" v-hasPermi="['lab:goal:remove']" type="danger" plain :disabled="goal.status !== 'DRAFT'" @click="$emit('delete', goal)">删除</el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<script>
export default {
  name: 'GoalDetailDrawer',
  props: {
    visible: { type: Boolean, default: false },
    goal: { type: Object, default: null },
    progress: { type: [Number, String, Object], default: 0 },
    health: { type: Object, default: null },
    relatedTasks: { type: Array, default: () => [] },
    owners: { type: Array, default: () => [] },
    readiness: {
      type: Object,
      default: () => ({ total: 0, ready: false, hint: '权重合计必须达到 100。' })
    },
    loading: { type: Boolean, default: false },
    canEdit: { type: Boolean, default: false },
    canAddChild: { type: Boolean, default: false },
    canActivate: { type: Boolean, default: false },
    canDelete: { type: Boolean, default: false }
  },
  data() {
    return { activeTab: 'definition', viewportWidth: window.innerWidth }
  },
  computed: {
    drawerSize() {
      return this.viewportWidth < 760 ? '100%' : '560px'
    },
    statusClass() {
      return this.goal && this.goal.status === 'ACTIVE' ? 'is-active' : 'is-draft'
    },
    progressValue() {
      if (this.progress && typeof this.progress === 'object') {
        return this.progress.progressRate || this.progress.value || this.goal.progressRate
      }
      return this.progress == null ? this.goal.progressRate : this.progress
    },
    healthLabel() {
      if (!this.health) return '待计算'
      return { GREEN: '健康', YELLOW: '关注', RED: '风险' }[this.health.status] || this.health.status || '待计算'
    },
    healthClass() {
      return this.health ? `is-${String(this.health.status || '').toLowerCase()}` : 'is-unknown'
    },
    readinessPercent() {
      return Math.min(100, Math.max(0, this.number(this.readiness.total)))
    },
    readinessLabel() {
      return this.goal && this.goal.goalLevel === 'YEAR' ? '季度里程碑权重' : '月度重点任务目标权重'
    },
    taskHierarchy() {
      const months = this.relatedTasks.filter(task => task.taskLevel === 'month').map(task => Object.assign({}, task, { children: [] }))
      const byId = months.reduce((result, task) => { result[String(task.id)] = task; return result }, {})
      const orphanWeeks = []
      this.relatedTasks.filter(task => task.taskLevel === 'week').forEach(task => {
        const parent = byId[String(task.parentId)]
        if (parent) parent.children.push(task)
        else orphanWeeks.push(Object.assign({}, task, { children: [] }))
      })
      months.forEach(month => month.children.sort((left, right) => String(left.period).localeCompare(String(right.period))))
      return months.concat(orphanWeeks).sort((left, right) => String(left.period).localeCompare(String(right.period)))
    }
  },
  mounted() {
    window.addEventListener('resize', this.updateWidth)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateWidth)
  },
  methods: {
    updateWidth() {
      this.viewportWidth = window.innerWidth
    },
    number(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Number(number.toFixed(2)) : 0
    },
    ownerName(ownerId) {
      const owner = this.owners.find(item => String(item.id) === String(ownerId))
      return owner ? owner.nickName || owner.userName || owner.memberNo : `成员 #${ownerId || '—'}`
    },
    openTask(task) {
      const navigation = this.$router.push({ path: '/lab/task', query: { id: String(task.id), period: task.taskLevel === 'month' ? task.period : undefined }})
      if (navigation && navigation.catch) navigation.catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.goal-detail__shell { min-height: 100%; padding-bottom: 76px; color: var(--lab-ink); background: var(--lab-canvas); }
.goal-detail__hero { position: relative; padding: 29px 30px 24px; color: #fff; background: var(--lab-indigo-deep); }
.goal-detail__hero > button { position: absolute; top: 18px; right: 18px; border: 0; color: #fff; background: transparent; font-size: 18px; cursor: pointer; }
.goal-detail__code { color: #8bd4cc; font-size: 10px; letter-spacing: 0.12em; }
.goal-detail__hero h2 { margin: 10px 34px 14px 0; font-size: 22px; line-height: 32px; }
.goal-detail__badges { display: flex; gap: 8px; }
.goal-detail__badges span { padding: 4px 7px; border: 1px solid rgba(255,255,255,.22); color: #d9e0ef; font-size: 9px; }
.goal-detail__badges .is-active { border-color: #72cec5; color: #bff5ee; }
.goal-detail__metrics { display: grid; grid-template-columns: repeat(4, 1fr); margin: 18px 20px 0; border: 1px solid var(--lab-line); background: #fff; }
.goal-detail__metrics div { padding: 15px 16px; border-right: 1px solid var(--lab-line); }
.goal-detail__metrics div:last-child { border-right: 0; }
.goal-detail__metrics span, .goal-detail__metrics strong { display: block; }
.goal-detail__metrics span { color: #5f6b80; font-size: 10px; }
.goal-detail__metrics strong { margin-top: 6px; color: var(--lab-indigo); font-size: 20px; }
.goal-detail__metrics strong.is-green { color: #087b75; }
.goal-detail__metrics strong.is-yellow { color: #9a640d; }
.goal-detail__metrics strong.is-red { color: #b42318; }
.goal-detail__metrics strong.is-unknown { color: #55627a; font-size: 14px; }
.goal-detail__readiness, .goal-detail__tabs { margin: 14px 20px 0; padding: 18px; border: 1px solid var(--lab-line); background: #fff; }
.goal-detail__section-title { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 12px; }
.goal-detail__section-title h3 { margin: 4px 0 0; font-size: 15px; }
.goal-detail__section-title > strong { color: var(--lab-warning); font-size: 15px; }
.goal-detail__section-title > strong.is-ready { color: var(--lab-teal); }
.goal-detail__readiness p { margin: 9px 0 0; color: #5f6b80; font-size: 10px; }
.goal-detail__definition { display: grid; grid-template-columns: 82px 1fr; margin: 0; font-size: 12px; line-height: 20px; }
.goal-detail__definition dt, .goal-detail__definition dd { margin: 0; padding: 10px 0; border-bottom: 1px solid #edf0f5; }
.goal-detail__definition dt { color: #5f6b80; }
.goal-detail__tasks { margin: 0; padding: 0; list-style: none; }
.goal-detail__tasks > li { border-bottom: 1px solid #edf0f5; }
.goal-detail__tasks ul { margin: 0 0 7px 33px; padding: 0; list-style: none; border-left: 1px solid #d9e1eb; }
.goal-detail__task-row { display: grid; width: 100%; grid-template-columns: 28px 1fr auto 14px; align-items: center; gap: 9px; padding: 10px 2px; border: 0; color: inherit; text-align: left; background: transparent; cursor: pointer; }
.goal-detail__task-row:hover { background: #f1f7f6; }
.goal-detail__task-row > span:first-child { display: inline-flex; width: 24px; height: 24px; align-items: center; justify-content: center; color: #fff; background: var(--lab-indigo); font-size: 9px; }
.goal-detail__task-row > span:first-child.is-week { color: #075f5a; background: #d9f0ed; }
.goal-detail__task-row.is-week-row { padding-left: 9px; }
.goal-detail__tasks strong, .goal-detail__tasks small { display: block; }
.goal-detail__tasks strong { font-size: 11px; }
.goal-detail__tasks small { margin-top: 3px; color: #5f6b80; font-size: 9px; }
.goal-detail__tasks b { color: var(--lab-teal); font-size: 11px; font-weight: 700; }
.goal-detail__task-row > i { color: #7c879b; }
.goal-detail__footer { position: absolute; right: 0; bottom: 0; left: 0; z-index: 2; display: flex; justify-content: flex-end; gap: 7px; padding: 14px 20px; border-top: 1px solid var(--lab-line); background: #fff; }
.goal-detail__loading { height: 360px; margin: 20px; }
@media (max-width: 520px) {
  .goal-detail__metrics { grid-template-columns: repeat(2, 1fr); }
  .goal-detail__metrics div:nth-child(2) { border-right: 0; }
  .goal-detail__metrics div:nth-child(-n+2) { border-bottom: 1px solid var(--lab-line); }
}
</style>
