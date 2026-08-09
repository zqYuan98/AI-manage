<template>
  <section class="load-matrix lab-panel" aria-labelledby="load-matrix-title">
    <header class="load-matrix__header">
      <div>
        <span class="lab-eyebrow">团队负载</span>
        <h2 id="load-matrix-title">成员负载矩阵</h2>
      </div>
      <el-tooltip content="重点任务权重、在途任务与风险事件的同期快照" placement="top">
        <span class="load-matrix__note">颜色只提示异常</span>
      </el-tooltip>
    </header>

    <div v-if="loading" class="load-matrix__loading lab-skeleton" />
    <div v-else-if="error" class="load-matrix__state">
      <span>负载矩阵暂时不可用</span>
      <button type="button" @click="$emit('retry')">重试</button>
    </div>
    <div v-else-if="!members.length" class="lab-empty">当前范围暂无成员负载数据</div>
    <div v-else class="load-matrix__scroll">
      <table>
        <thead>
          <tr>
            <th scope="col">成员 / 业务线</th>
            <th scope="col">重点权重</th>
            <th scope="col">在途</th>
            <th scope="col">近周</th>
            <th scope="col">逾期</th>
            <th scope="col">阻塞</th>
            <th scope="col">协同</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="member in members"
            :key="member.memberId"
            class="lab-focus-ring"
            tabindex="0"
            role="button"
            :aria-label="`${member.memberName}负载明细`"
            @click="$emit('drill', member)"
            @keydown.enter.prevent="$emit('drill', member)"
            @keydown.space.prevent="$emit('drill', member)"
          >
            <th scope="row">
              <strong>{{ member.memberName }}</strong>
              <small>{{ member.bizLine ? bizLineLabel(member.bizLine) : '未配置业务线' }}</small>
            </th>
            <td><span :class="heatClass(member, 'weight')">{{ formatWeight(member.keyTaskWeight) }}</span></td>
            <td><span :class="heatClass(member, 'active')">{{ value(member.activeTaskCount) }}</span></td>
            <td><span class="load-matrix__cell">{{ value(member.recentWeekTaskCount) }}</span></td>
            <td><span :class="riskClass(member.overdueCount)">{{ value(member.overdueCount) }}</span></td>
            <td><span :class="riskClass(member.blockedCount)">{{ value(member.blockedCount) }}</span></td>
            <td><span class="load-matrix__cell">{{ value(member.coordinationCount) }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script>
import { bizLineLabel } from '@/utils/lab-status'

export default {
  name: 'MemberLoadMatrix',
  props: {
    members: {
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
  methods: {
    bizLineLabel,
    value(value) {
      const number = Number(value)
      return Number.isFinite(number) ? number : 0
    },
    formatWeight(value) {
      const number = this.value(value)
      return `${Number(number.toFixed(1))}%`
    },
    heatClass(member, type) {
      if (type !== 'weight') return 'load-matrix__cell'
      const heat = String(member.heatLevel || '').toUpperCase()
      const value = this.value(member.keyTaskWeight)
      if (/HIGH|RED|OVERLOAD/.test(heat) || value > 100) {
        return 'load-matrix__cell is-high'
      }
      if (/MEDIUM|WARN/.test(heat) || value > 80) {
        return 'load-matrix__cell is-medium'
      }
      return 'load-matrix__cell'
    },
    riskClass(value) {
      return this.value(value) > 0 ? 'load-matrix__cell is-risk' : 'load-matrix__cell'
    }
  }
}
</script>

<style lang="scss" scoped>
.load-matrix { overflow: hidden; }
.load-matrix__header { display: flex; align-items: flex-end; justify-content: space-between; padding: 20px 22px 16px; border-bottom: 1px solid var(--lab-line); }
.load-matrix__header h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 18px; }
.load-matrix__note { color: #5f6b80; font-size: 10px; cursor: help; }
.load-matrix__scroll { overflow-x: auto; }
table { width: 100%; min-width: 720px; border-collapse: collapse; }
thead { background: #f7f8fb; }
th, td { padding: 11px 10px; border-bottom: 1px solid #edf0f5; text-align: center; }
thead th { color: #667288; font-size: 10px; font-weight: 600; letter-spacing: 0.04em; }
thead th:first-child, tbody th { padding-left: 22px; text-align: left; }
tbody tr { transition: background 140ms ease; cursor: pointer; }
tbody tr:hover { background: #f7fbfa; }
tbody th strong, tbody th small { display: block; }
tbody th strong { color: var(--lab-ink); font-size: 12px; }
tbody th small { margin-top: 3px; color: #5f6b80; font-size: 10px; font-weight: 400; }
.load-matrix__cell { display: inline-flex; min-width: 42px; height: 27px; align-items: center; justify-content: center; border-radius: 2px; color: var(--lab-indigo); background: #edf2f6; font-family: 'Arial Narrow', Arial, sans-serif; font-size: 12px; font-weight: 700; }
.load-matrix__cell.is-medium { color: #8a5b10; background: #fff0ce; }
.load-matrix__cell.is-high, .load-matrix__cell.is-risk { color: #a53636; background: #fde1df; }
.load-matrix__loading { height: 286px; }
.load-matrix__state { display: flex; min-height: 220px; align-items: center; justify-content: center; gap: 8px; color: var(--lab-ink-soft); }
.load-matrix__state button { border: 0; color: var(--lab-teal); background: transparent; cursor: pointer; }
</style>
