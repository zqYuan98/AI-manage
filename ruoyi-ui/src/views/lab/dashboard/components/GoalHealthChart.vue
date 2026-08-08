<template>
  <section class="goal-health lab-panel" aria-labelledby="goal-health-title">
    <header class="goal-health__header">
      <div>
        <span class="lab-eyebrow">Goal trajectory</span>
        <h2 id="goal-health-title">年度目标进度轨迹</h2>
      </div>
      <span v-if="activeGoal" class="goal-health__active">{{ activeGoal.title }}</span>
    </header>

    <div v-if="loading" class="goal-health__loading lab-skeleton" />
    <div v-else-if="error" class="goal-health__state">
      <i class="el-icon-warning-outline" />
      <span>目标健康度暂时不可用</span>
      <button type="button" @click="$emit('retry')">重试</button>
    </div>
    <div v-else-if="!health.length && !trend.length" class="lab-empty">本周期暂无目标进度数据</div>
    <div v-else class="goal-health__body">
      <div ref="chart" class="goal-health__chart" role="img" :aria-label="chartLabel" />
      <div class="goal-health__list" aria-label="目标健康度列表">
        <button
          v-for="goal in health"
          :key="goal.goalId"
          type="button"
          class="goal-health__row"
          :class="{ 'is-active': String(goal.goalId) === String(activeGoalId) }"
          @click="selectGoal(goal)"
        >
          <span class="goal-health__status" :class="statusClass(goal.status)" />
          <span class="goal-health__title">{{ goal.title }}</span>
          <strong>{{ number(goal.actualProgress) }}%</strong>
          <small>偏差 {{ signed(goal.lag) }}</small>
        </button>
      </div>
    </div>
  </section>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'GoalHealthChart',
  props: {
    health: {
      type: Array,
      default: () => []
    },
    trend: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    error: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      chart: null,
      activeGoalId: null
    }
  },
  computed: {
    activeGoal() {
      return this.health.find(goal => String(goal.goalId) === String(this.activeGoalId)) || this.health[0]
    },
    activeTrend() {
      const goalId = this.activeGoal ? this.activeGoal.goalId : (this.trend[0] || {}).goalId
      return this.trend
        .filter(point => String(point.goalId) === String(goalId))
        .slice()
        .sort((a, b) => String(a.period).localeCompare(String(b.period)))
    },
    chartLabel() {
      const name = this.activeGoal ? this.activeGoal.title : '目标'
      return `${name}的计划与实际进度折线图`
    }
  },
  watch: {
    health: {
      immediate: true,
      handler(value) {
        if (!value.some(goal => String(goal.goalId) === String(this.activeGoalId))) {
          this.activeGoalId = value.length ? value[0].goalId : null
        }
        this.$nextTick(this.renderChart)
      }
    },
    trend: {
      deep: true,
      handler() {
        this.$nextTick(this.renderChart)
      }
    },
    loading() {
      if (this.loading) this.disposeChart()
      this.$nextTick(this.renderChart)
    },
    error() {
      if (this.error) this.disposeChart()
      this.$nextTick(this.renderChart)
    }
  },
  mounted() {
    window.addEventListener('resize', this.resizeChart)
    this.renderChart()
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart)
    this.disposeChart()
  },
  methods: {
    selectGoal(goal) {
      if (String(goal.goalId) === String(this.activeGoalId)) {
        this.$emit('drill', goal)
        return
      }
      this.activeGoalId = goal.goalId
      this.$nextTick(this.renderChart)
    },
    renderChart() {
      if (this.loading || this.error || !this.$refs.chart) return
      if (this.chart && this.chart.getDom() !== this.$refs.chart) this.disposeChart()
      if (!this.chart) this.chart = echarts.init(this.$refs.chart)
      const points = this.activeTrend
      this.chart.setOption({
        animationDuration: 280,
        color: ['#0b8f87', '#8792a8'],
        tooltip: { trigger: 'axis', valueFormatter: value => `${value}%` },
        legend: { right: 0, top: 0, itemWidth: 16, textStyle: { color: '#526078', fontSize: 11 }, data: ['实际', '计划'] },
        grid: { left: 44, right: 18, top: 44, bottom: 30 },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: points.map(point => point.period),
          axisLine: { lineStyle: { color: '#dfe4ec' }},
          axisTick: { show: false },
          axisLabel: { color: '#68758b', fontSize: 10 }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 100,
          axisLabel: { color: '#68758b', formatter: '{value}%' },
          splitLine: { lineStyle: { color: '#edf0f5' }}
        },
        series: [
          {
            name: '实际',
            type: 'line',
            data: points.map(point => this.number(point.actualProgress)),
            symbol: 'circle',
            symbolSize: 7,
            lineStyle: { width: 3 },
            areaStyle: { color: 'rgba(11,143,135,0.1)' }
          },
          {
            name: '计划',
            type: 'line',
            data: points.map(point => this.number(point.expectedProgress)),
            symbol: 'none',
            lineStyle: { type: 'dashed', width: 2 }
          }
        ]
      }, true)
      this.chart.off('click')
      this.chart.on('click', () => {
        if (this.activeGoal) this.$emit('drill', this.activeGoal)
      })
    },
    resizeChart() {
      if (this.chart) this.chart.resize()
    },
    disposeChart() {
      if (!this.chart) return
      this.chart.dispose()
      this.chart = null
    },
    number(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Number(number.toFixed(2)) : 0
    },
    signed(value) {
      const number = this.number(value)
      return `${number > 0 ? '+' : ''}${number}%`
    },
    statusClass(status) {
      const value = String(status || '').toUpperCase()
      if (/RED|RISK|DANGER|CRITICAL/.test(value)) return 'is-danger'
      if (/WARN|LAG/.test(value)) return 'is-warning'
      return 'is-normal'
    }
  }
}
</script>

<style lang="scss" scoped>
.goal-health { min-height: 430px; padding: 22px 24px 20px; }
.goal-health__header { display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 17px; border-bottom: 1px solid var(--lab-line); }
.goal-health__header h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 19px; font-weight: 700; }
.goal-health__active { max-width: 42%; overflow: hidden; color: var(--lab-ink-soft); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.goal-health__body { display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 22px; padding-top: 18px; }
.goal-health__chart { width: 100%; height: 322px; }
.goal-health__list { max-height: 322px; overflow: auto; border-left: 1px solid var(--lab-line); }
.goal-health__row { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; gap: 8px; width: 100%; padding: 13px 10px 12px 14px; border: 0; border-bottom: 1px solid #edf0f5; color: var(--lab-ink); background: transparent; text-align: left; cursor: pointer; }
.goal-health__row:hover, .goal-health__row.is-active { background: #f2f8f7; }
.goal-health__row.is-active { box-shadow: inset 3px 0 var(--lab-teal); }
.goal-health__status { width: 7px; height: 7px; margin-top: 5px; border-radius: 50%; background: var(--lab-success); }
.goal-health__status.is-warning { background: var(--lab-warning); }
.goal-health__status.is-danger { background: var(--lab-danger); }
.goal-health__title { overflow: hidden; font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.goal-health__row strong { color: var(--lab-indigo); font-size: 13px; }
.goal-health__row small { grid-column: 2 / 4; color: #5f6b80; font-size: 10px; }
.goal-health__loading { height: 322px; margin-top: 18px; }
.goal-health__state { display: flex; min-height: 322px; align-items: center; justify-content: center; gap: 8px; color: var(--lab-ink-soft); }
.goal-health__state button { border: 0; color: var(--lab-teal); background: transparent; cursor: pointer; }
@media (max-width: 1180px) {
  .goal-health__body { grid-template-columns: 1fr; }
  .goal-health__list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid var(--lab-line); border-left: 0; }
}
@media (max-width: 680px) {
  .goal-health { padding: 18px 16px; }
  .goal-health__list { grid-template-columns: 1fr; }
}
</style>
