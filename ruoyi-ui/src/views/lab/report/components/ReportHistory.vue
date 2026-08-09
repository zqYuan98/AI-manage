<template>
  <aside class="history lab-panel" aria-labelledby="history-title">
    <header><div><span class="lab-eyebrow">不可变归档</span><h2 id="history-title">版本历史</h2></div><strong>{{ total }}</strong></header>
    <div v-if="loading" class="history__loading lab-skeleton" />
    <div v-else-if="error" class="history__state" role="alert"><span>历史加载失败</span><button type="button" @click="$emit('retry')">重试</button></div>
    <div v-else-if="!items.length" class="lab-empty">该范围暂无报告版本</div>
    <ol v-else>
      <li v-for="item in items" :key="item.id">
        <button type="button" :class="{ active: item.id === selectedId }" @click="$emit('select', item)">
          <span class="history__rail" />
          <span class="history__main"><strong>{{ item.reportNo || `${item.templateCode} #${item.revisionNo}` }}</strong><small>{{ item.period }} · {{ bizLineLabel(item.bizLine) }} · 模板 r{{ item.templateRevision }}</small></span>
          <span class="history__flags"><i v-if="item.sensitiveFlag === '1'" class="el-icon-lock" title="含敏感章节" /><b :class="tone(item.lifecycleStatus)">{{ label(item) }}</b></span>
        </button>
      </li>
    </ol>
    <footer v-if="total > items.length"><el-button type="text" :loading="loadingMore" @click="$emit('more')">加载更多</el-button></footer>
  </aside>
</template>
<script>
import { bizLineLabel, statusLabel } from '@/utils/lab-status'

export default {
  name: 'ReportHistory', props: { items: { type: Array, default: () => [] }, total: { type: Number, default: 0 }, selectedId: { type: [Number, String], default: null }, loading: Boolean, loadingMore: Boolean, error: Boolean },
  methods: { bizLineLabel, label(item) { if (item.currentFlag === '1') return '当前'; if (item.finalFlag === '1') return '已定稿'; return statusLabel('REPORT', item.lifecycleStatus || 'DRAFT') }, tone(value) { return value === 'SUPERSEDED' ? 'muted' : /FINAL|READY/.test(value || '') ? 'success' : /FAIL/.test(value || '') ? 'danger' : 'active' } }
}
</script>
<style lang="scss" scoped>
.history{height:100%;min-height:560px;padding:20px 0}.history header{display:flex;align-items:flex-end;justify-content:space-between;padding:0 18px 15px;border-bottom:1px solid var(--lab-line)}h2{margin:5px 0 0;color:var(--lab-indigo-deep);font-size:18px}.history header>strong{color:var(--lab-indigo);font-size:22px}ol{margin:0;padding:0;list-style:none}li button{position:relative;display:grid;grid-template-columns:4px 1fr auto;gap:11px;width:100%;padding:14px 16px;border:0;border-bottom:1px solid var(--lab-line);color:var(--lab-ink);background:#fff;text-align:left;cursor:pointer}li button:hover,li button.active{background:#f2f4f9}.history__rail{height:100%;min-height:36px;background:#d9dee8}button.active .history__rail{background:var(--lab-teal)}.history__main strong,.history__main small{display:block}.history__main strong{font-size:12px}.history__main small{margin-top:5px;color:var(--lab-ink-soft);font-size:9px}.history__flags{display:flex;align-items:flex-end;gap:5px;flex-direction:column}.history__flags i{color:var(--lab-danger)}.history__flags b{padding:3px 5px;font-size:8px;background:#eef0f4}.history__flags b.success{color:#24704c;background:#eaf5ef}.history__flags b.danger{color:#9b3939;background:#fff0ef}.history__flags b.active{color:#08796f;background:#e5f5f3}.history__flags b.muted{color:#697386}.history__loading{height:420px;margin:15px}.history__state{display:flex;min-height:240px;align-items:center;justify-content:center;gap:7px;color:var(--lab-ink-soft);font-size:11px}.history__state button{border:0;color:var(--lab-teal);background:transparent;font-weight:700}.history footer{text-align:center}
</style>
