<template>
  <article
    class="metric-card lab-panel lab-focus-ring"
    :class="[`metric-card--${tone}`, { 'metric-card--clickable': clickable }]"
    :tabindex="clickable ? 0 : -1"
    :role="clickable ? 'button' : undefined"
    :aria-label="clickable ? `${metric.name || '指标'}，${formattedValue}${metric.unit || ''}。${metric.definition || ''}。查看明细` : undefined"
    @click="openDetail"
    @keydown.enter.prevent="openDetail"
    @keydown.space.prevent="openDetail"
  >
    <template v-if="loading">
      <span class="metric-card__skeleton lab-skeleton" />
      <span class="metric-card__skeleton metric-card__skeleton--value lab-skeleton" />
    </template>
    <template v-else>
      <div class="metric-card__rule" />
      <div class="metric-card__head">
        <span class="metric-card__index">{{ indexLabel }}</span>
        <el-tooltip v-if="metric.definition" :content="metric.definition" placement="top">
          <span class="metric-card__definition">口径</span>
        </el-tooltip>
      </div>
      <p class="metric-card__name">{{ metric.name || '未命名指标' }}</p>
      <p class="metric-card__value">
        <strong>{{ formattedValue }}</strong>
        <span v-if="metric.unit">{{ metric.unit }}</span>
      </p>
      <div class="metric-card__foot">
        <span>{{ metric.period || '当前周期' }}</span>
        <span v-if="metric.lastUpdated">更新 {{ formatClock(metric.lastUpdated) }}</span>
      </div>
      <i v-if="clickable" class="el-icon-right metric-card__arrow" aria-hidden="true" />
    </template>
  </article>
</template>

<script>
export default {
  name: 'MetricCard',
  props: {
    metric: {
      type: Object,
      default: () => ({})
    },
    loading: {
      type: Boolean,
      default: false
    },
    index: {
      type: Number,
      default: 0
    }
  },
  computed: {
    clickable() {
      return !this.loading && this.metric.drillDownFilters &&
        Object.keys(this.metric.drillDownFilters).length > 0
    },
    indexLabel() {
      return String(this.index + 1).padStart(2, '0')
    },
    formattedValue() {
      const value = this.metric.value
      if (value === null || value === undefined || value === '') return '—'
      if (typeof value === 'number') {
        return value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
      }
      return String(value)
    },
    tone() {
      const code = String(this.metric.code || '').toUpperCase()
      const value = Number(this.metric.value)
      const riskMetric = /ANNUALGOALHEALTH|GOALHEALTHRISK|OVERDUE|MISSING|BLOCK|SINGLE_POINT|RISK/.test(code)
      if (riskMetric) return value > 0 ? 'danger' : 'normal'
      if (/HEALTH|PROGRESS|COMPLETE/.test(code) && Number.isFinite(value) && value < 80) {
        return value < 60 ? 'danger' : 'warning'
      }
      return 'normal'
    }
  },
  methods: {
    openDetail() {
      if (this.clickable) this.$emit('drill', this.metric)
    },
    formatClock(value) {
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return '—'
      return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  }
}
</script>

<style lang="scss" scoped>
.metric-card {
  position: relative;
  min-height: 168px;
  padding: 18px 18px 16px;
  overflow: hidden;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.metric-card--clickable { cursor: pointer; }
.metric-card--clickable:hover { transform: translateY(-2px); box-shadow: 0 16px 36px rgba(28, 43, 78, 0.13); }
.metric-card__rule { position: absolute; top: 0; left: 0; width: 52px; height: 4px; background: var(--lab-teal); }
.metric-card--warning .metric-card__rule { background: var(--lab-warning); }
.metric-card--danger .metric-card__rule { background: var(--lab-danger); }
.metric-card__head, .metric-card__foot { display: flex; align-items: center; justify-content: space-between; }
.metric-card__index { color: #59657a; font-family: 'Arial Narrow', Arial, sans-serif; font-size: 12px; letter-spacing: 0.12em; }
.metric-card__definition { padding: 2px 5px; border: 1px solid var(--lab-line); border-radius: 2px; color: var(--lab-ink-soft); font-size: 11px; cursor: help; }
.metric-card__name { min-height: 38px; margin: 14px 0 3px; color: var(--lab-ink-soft); font-size: 13px; line-height: 19px; }
.metric-card__value { margin: 0; color: var(--lab-ink); white-space: nowrap; }
.metric-card__value strong { font-family: 'Arial Narrow', 'DIN Alternate', Arial, sans-serif; font-size: 34px; font-weight: 700; letter-spacing: -0.025em; }
.metric-card__value span { margin-left: 5px; color: var(--lab-ink-soft); font-size: 12px; }
.metric-card__foot { margin-top: 12px; padding-top: 9px; border-top: 1px solid var(--lab-line); color: #5f6b80; font-size: 10px; }
.metric-card__arrow { position: absolute; right: 14px; bottom: 47px; color: var(--lab-teal); font-size: 16px; }
.metric-card__skeleton { display: block; width: 64%; height: 14px; margin-top: 14px; border-radius: 2px; }
.metric-card__skeleton--value { width: 46%; height: 38px; margin-top: 26px; }
</style>
