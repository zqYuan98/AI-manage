<template>
  <section class="artifact-board lab-panel" aria-labelledby="artifact-title">
    <header><div><span class="lab-eyebrow">制品生成流程</span><h2 id="artifact-title">制品生命周期</h2></div><span class="lifecycle" :class="tone(report.lifecycleStatus)">{{ lifecycleLabel }}</span></header>
    <div v-if="!report.id" class="lab-empty">请从历史列表选择报告</div>
    <div v-else class="artifact-grid">
      <article v-for="item in artifacts" :key="item.code" :class="['artifact-card', tone(item.status)]">
        <div class="artifact-card__mark"><i :class="item.icon" /></div>
        <div class="artifact-card__body"><span>{{ item.name }}</span><strong>{{ label(item.status) }}</strong><small>{{ jobCaption(item.code) }}</small></div>
        <div class="artifact-card__actions">
          <el-button v-if="item.status === 'SUCCESS'" v-hasPermi="['lab:report:download']" type="text" @click="$emit('download', item.code)">下载</el-button>
          <el-button v-if="item.status === 'FAILED'" v-hasPermi="['lab:report:retry']" type="text" @click="$emit('retry', item.code)">单项重试</el-button>
        </div>
      </article>
    </div>
    <footer v-if="report.id">
      <span><i class="el-icon-info" /> 制品独立追踪，上游成功文件会在重试时复用</span>
      <el-button v-if="canFinalize" v-hasPermi="['lab:report:finalize']" type="primary" size="small" @click="$emit('finalize')">定稿并锁定快照</el-button>
    </footer>
  </section>
</template>

<script>
import { statusLabel } from '@/utils/lab-status'

export default {
  name: 'ArtifactStatus',
  props: { report: { type: Object, default: () => ({}) }, jobs: { type: Array, default: () => [] }},
  computed: {
    artifacts() {
      return [
        { code: 'JSON', name: '数据快照', icon: 'el-icon-document', status: this.report.jsonStatus },
        { code: 'MARKDOWN', name: 'Markdown', icon: 'el-icon-edit-outline', status: this.report.markdownStatus },
        { code: 'WORD', name: 'Word', icon: 'el-icon-tickets', status: this.report.wordStatus },
        { code: 'PDF', name: 'PDF', icon: 'el-icon-reading', status: this.report.pdfStatus }
      ]
    },
    canFinalize() { return this.artifacts.every(item => item.status === 'SUCCESS') && this.report.finalFlag !== '1' && this.report.lifecycleStatus !== 'FINALIZED' },
    lifecycleLabel() { return this.report.lifecycleStatus ? statusLabel('REPORT', this.report.lifecycleStatus) : '未创建' }
  },
  methods: {
    label(value) { return value ? statusLabel('ARTIFACT', value) : '未开始' },
    tone(value) { const status = String(value || '').toUpperCase(); if (/FAIL/.test(status)) return 'is-danger'; if (/RUNNING|QUEUED|GENERATING/.test(status)) return 'is-active'; if (/SUCCESS|FINAL|READY/.test(status)) return 'is-success'; return 'is-muted' },
    jobCaption(code) { const jobType = ['JSON', 'MARKDOWN'].includes(code) ? 'DATA' : code; const job = this.jobs.filter(item => String(item.jobType || '').toUpperCase() === jobType).sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0]; if (!job) return '尚无任务'; if (job.jobStatus === 'FAILED') return job.errorSummary || `第 ${job.attemptCount || 1} 次尝试失败`; return `${job.progressRate || 0}% · 第 ${job.attemptCount || 1} 次尝试` }
  }
}
</script>

<style lang="scss" scoped>
.artifact-board { padding: 22px; }
header, footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 19px; }
.lifecycle { padding: 6px 9px; border: 1px solid var(--lab-line); font-size: 10px; font-weight: 700; letter-spacing: .05em; }
.artifact-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 12px; margin-top: 18px; }
.artifact-card { display: grid; grid-template-columns: 38px 1fr; min-height: 112px; padding: 15px; border: 1px solid var(--lab-line); border-top: 3px solid #c7ceda; background: #fafbfc; }
.artifact-card.is-success { border-top-color: var(--lab-success); }.artifact-card.is-active { border-top-color: var(--lab-teal); }.artifact-card.is-danger { border-top-color: var(--lab-danger); }
.artifact-card__mark { display: flex; width: 31px; height: 31px; align-items: center; justify-content: center; color: var(--lab-indigo); background: #edf0f7; }
.artifact-card__body { min-width: 0; }.artifact-card__body span,.artifact-card__body small { display: block; color: var(--lab-ink-soft); font-size: 10px; }.artifact-card__body strong { display: block; margin: 5px 0; color: var(--lab-indigo-deep); font-size: 14px; }.artifact-card__body small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-card__actions { grid-column: 2; }.artifact-card__actions .el-button { padding: 4px 8px 0 0; font-size: 10px; }
footer { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--lab-line); color: var(--lab-ink-soft); font-size: 10px; }
.is-danger { color: var(--lab-danger); }.is-active { color: #08796f; }.is-success { color: #24704c; }.is-muted { color: var(--lab-ink-soft); }
@media(max-width:1000px){.artifact-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:620px){.artifact-grid{grid-template-columns:1fr}}
</style>
