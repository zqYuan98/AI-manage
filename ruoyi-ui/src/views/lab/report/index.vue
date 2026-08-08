<template>
  <main class="report-center">
    <section class="report-hero">
      <div><span>REPORT / CONTROL ROOM / {{ query.period }}</span><h1>月报生成中心</h1><p>从数据快照到定稿制品，每一步可观测、可重试、可追溯</p></div>
      <div class="report-hero__controls">
        <el-date-picker v-model="query.period" type="month" value-format="yyyy-MM" :clearable="false" aria-label="报告周期" @change="reloadHistory" />
        <el-select v-model="query.bizLine" filterable placeholder="选择业务线" aria-label="业务线" @change="reloadHistory"><el-option v-for="line in bizLines" :key="line" :label="line === 'ALL' ? '全实验室' : line" :value="line" /></el-select>
        <el-select v-if="canGenerate" v-model="generation.templateId" filterable placeholder="选择已启用模板" aria-label="生成模板" @change="invalidateGeneration">
          <el-option v-for="item in enabledTemplates" :key="item.id" :label="`${item.templateName} · r${item.revisionNo}`" :value="item.id" />
        </el-select>
        <el-button v-if="canGenerate" v-hasPermi="['lab:report:generate']" type="primary" icon="el-icon-caret-right" :disabled="!generation.templateId || !validBizLine" @click="prepareGenerate">生成新版本</el-button>
        <el-button v-if="canEditSummary" icon="el-icon-edit-outline" :disabled="!summaryScopeReady" @click="editSummary">维护人工小结</el-button>
      </div>
      <div class="report-hero__legend"><span><i class="is-live" /> 运行中</span><span><i class="is-ready" /> 制品就绪</span><span><i class="is-failed" /> 可定向重试</span><span><i class="el-icon-lock" /> 敏感内容按实时权限过滤</span></div>
    </section>

    <div class="report-layout">
      <report-history :items="history" :total="total" :selected-id="selected.id" :loading="historyLoading" :loading-more="loadingMore" :error="historyError" @select="selectReport" @retry="reloadHistory" @more="loadMore" />
      <section class="report-workspace">
        <div class="report-workspace__toolbar lab-panel">
          <div v-if="selected.id"><strong>{{ selected.reportNo || selected.templateCode }}</strong><span>{{ selected.period }} · {{ selected.bizLine }} · 实例修订 {{ selected.revisionNo }}</span></div>
          <div v-else><strong>尚未选择报告</strong><span>从左侧历史打开，或在上方生成新版本</span></div>
          <div class="report-workspace__actions">
            <input ref="markdown" class="sr-only" type="file" accept=".md,text/markdown,text/plain" @change="uploadMarkdown">
            <el-button v-if="selected.id" v-hasPermi="['lab:report:generate']" size="small" icon="el-icon-upload2" @click="$refs.markdown.click()">导入 Markdown 新版</el-button>
            <el-button size="small" icon="el-icon-refresh" :loading="detailLoading" @click="refreshSelected">刷新状态</el-button>
          </div>
        </div>
        <artifact-status :report="selected" :jobs="jobs" @retry="retryArtifact" @download="downloadArtifact" @finalize="finalizeSelected" />
        <section class="report-body lab-panel">
          <header><div><span class="lab-eyebrow">Markdown evidence</span><h2>内容预览</h2></div><span v-if="polling" class="report-body__poll"><i /> {{ pollCaption }}</span></header>
          <div v-if="detailLoading && !body.contentMarkdown" class="report-body__skeleton lab-skeleton" />
          <div v-else-if="detailError" class="report-body__state" role="alert"><span>内容加载失败，状态与制品仍可独立操作。</span><button type="button" @click="refreshSelected">重试</button></div>
          <pre v-else-if="body.contentMarkdown">{{ body.contentMarkdown }}</pre>
          <div v-else class="lab-empty">当 Markdown 制品成功后，将在此显示可读预览。</div>
        </section>
      </section>
    </div>

    <report-summary-editor :visible.sync="summaryVisible" :period="generationContext.period" :biz-line="generationContext.bizLine" :sections="generationContext.sections" :generate-after-save="generationContext.generateAfterSave" @continue="afterSummarySaved" />
  </main>
</template>

<script>
import { saveAs } from 'file-saver'
import { downloadReportArtifact, finalizeReport, generateReport, getReportBody, getReportStatus, getReportSummarySections, importReportMarkdown, listReportBizLines, listReportHistory, listReportJobs, retryReportArtifact } from '@/api/lab/report'
import { getTemplateConfig, listTemplateTree } from '@/api/lab/template'
import ArtifactStatus from './components/ArtifactStatus'
import ReportHistory from './components/ReportHistory'
import ReportSummaryEditor from './components/ReportSummaryEditor'

const emptyReport = () => ({})
export default {
  name: 'LabReportCenter', components: { ArtifactStatus, ReportHistory, ReportSummaryEditor },
  data() { return { query: { period: this.initialPeriod(), bizLine: this.$route.query.bizLine || 'ALL', pageNum: 1, pageSize: 20 }, generation: { templateId: null }, generationContext: { templateId: null, period: '', bizLine: '', templateRevision: 1, sections: [], generateAfterSave: false }, templates: [], bizLines: ['ALL'], history: [], total: 0, selected: emptyReport(), body: {}, jobs: [], historyLoading: false, loadingMore: false, historyError: false, detailLoading: false, detailError: false, summaryVisible: false, polling: false, pollTimer: null, pollDelay: 1500, historyToken: 0, detailToken: 0, generationToken: 0 } },
  computed: {
    effectiveBizLine() { return this.query.bizLine || 'ALL' },
    enabledTemplates() { return this.templates.filter(item => item.status === 'ENABLED' && item.latestFlag !== '0' && item.periodType === 'MONTH') },
    canGenerate() { const values = this.$store.getters.permissions || []; return values.includes('*:*:*') || values.includes('lab:report:generate') },
    canEditSummary() { const roles = (this.$store.getters.roles || []).map(value => String(value).toLowerCase()); return roles.includes('admin') || roles.includes('lab_manager') || roles.includes('manager') || roles.includes('lab_lead') },
    isLineLead() { return (this.$store.getters.roles || []).map(value => String(value).toLowerCase()).includes('lab_lead') },
    validBizLine() { return this.bizLines.includes(this.query.bizLine) },
    summaryScopeReady() { return Boolean(this.validBizLine && (!this.isLineLead || this.query.bizLine !== 'ALL')) },
    selectedTemplate() { return this.templates.find(item => Number(item.id) === Number(this.generation.templateId)) || {} },
    pollCaption() { return this.pollDelay < 5000 ? '实时轮询' : `后台生成中 · ${Math.round(this.pollDelay / 1000)}s 后刷新` }
  },
  created() { this.reloadHistory(); this.loadBizLines(); this.loadTemplates() },
  beforeDestroy() { this.stopPolling(); this.detailToken++ },
  methods: {
    initialPeriod() { if (/^\d{4}-\d{2}$/.test(this.$route.query.period || '')) return this.$route.query.period; const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}` },
    loadTemplates() { if (!this.canGenerate) return Promise.resolve(); return listTemplateTree().then(response => { const data = Array.isArray(response.data) ? response.data : []; this.templates = this.flattenTemplates(data); const routeTemplate = Number(this.$route.query.templateId); this.generation.templateId = this.enabledTemplates.some(item => Number(item.id) === routeTemplate) ? routeTemplate : (this.enabledTemplates[0] || {}).id || null }) },
    loadBizLines() { return listReportBizLines().then(response => { this.bizLines = Array.isArray(response.data) ? response.data : []; if (!this.bizLines.includes(this.query.bizLine)) { this.query.bizLine = this.bizLines[0] || ''; return this.reloadHistory() } }).catch(() => { this.bizLines = [] }) },
    flattenTemplates(items) { const result = []; const walk = values => (values || []).forEach(item => { if (item && item.id) result.push(item); if (item && Array.isArray(item.children)) walk(item.children) }); walk(items); return result },
    reloadHistory() { this.query.pageNum = 1; this.history = []; this.total = 0; this.selected = emptyReport(); this.body = {}; this.jobs = []; this.detailToken++; this.stopPolling(); this.invalidateGeneration(); return this.loadHistory(false) },
    loadMore() { if (this.history.length >= this.total) return; this.query.pageNum += 1; return this.loadHistory(true) },
    loadHistory(append) { const token = ++this.historyToken; this.historyLoading = !append; this.loadingMore = append; this.historyError = false; return listReportHistory({ period: this.query.period, bizLine: this.effectiveBizLine, pageNum: this.query.pageNum, pageSize: this.query.pageSize }).then(response => { if (token !== this.historyToken) return; const rows = Array.isArray(response.rows) ? response.rows : []; this.history = append ? this.history.concat(rows) : rows; this.total = Number(response.total || rows.length); if (!append && rows.length) this.selectReport(rows[0]); else if (!append) { this.selected = emptyReport(); this.body = {}; this.jobs = []; this.stopPolling() } }).catch(() => { if (token === this.historyToken) { this.historyError = true; if (append) this.query.pageNum = Math.max(1, this.query.pageNum - 1) } }).finally(() => { if (token === this.historyToken) { this.historyLoading = false; this.loadingMore = false } }) },
    selectReport(item) { if (!item || !item.id) return; this.selected = Object.assign({}, item); this.body = {}; this.jobs = []; this.pollDelay = 1500; this.refreshSelected() },
    refreshSelected() { if (!this.selected.id) return Promise.resolve(); const id = this.selected.id; const token = ++this.detailToken; const wasPolling = this.polling || this.isActive(); this.detailLoading = true; this.detailError = false; return Promise.all([getReportStatus(id), listReportJobs(id), getReportBody(id).then(response => ({ response })).catch(() => ({ failed: true }))]).then(([status, jobs, body]) => { if (token !== this.detailToken || Number(this.selected.id) !== Number(id)) return; this.selected = Object.assign({}, this.selected, status.data || {}); this.jobs = Array.isArray(jobs.data) ? jobs.data : []; this.detailError = Boolean(body.failed); if (!body.failed) this.body = body.response.data || {}; if (this.isActive()) this.schedulePoll(); else this.stopPolling() }).catch(() => { if (token !== this.detailToken || Number(this.selected.id) !== Number(id)) return; this.detailError = true; if (wasPolling) { this.pollDelay = Math.min(10000, Math.round(this.pollDelay * 1.65)); this.schedulePoll() } else this.stopPolling() }).finally(() => { if (token === this.detailToken) this.detailLoading = false }) },
    isActive() { return ['QUEUED', 'RUNNING', 'PENDING'].some(status => this.jobs.some(job => job.jobStatus === status)) || ['GENERATING', 'QUEUED'].includes(this.selected.lifecycleStatus) },
    schedulePoll() { this.stopPolling(false); this.polling = true; const delay = this.pollDelay; this.pollTimer = setTimeout(() => { this.pollDelay = Math.min(10000, Math.round(this.pollDelay * 1.65)); this.refreshSelected() }, delay) },
    stopPolling(clear = true) { if (this.pollTimer) clearTimeout(this.pollTimer); this.pollTimer = null; if (clear) this.polling = false },
    invalidateGeneration() { this.generationToken++; this.summaryVisible = false; this.generationContext = { templateId: null, period: '', bizLine: '', templateRevision: 1, sections: [], generateAfterSave: false } },
    prepareGenerate() { const templateId = this.generation.templateId; if (!templateId || !this.validBizLine) return; const token = ++this.generationToken; const period = this.query.period; const bizLine = this.effectiveBizLine; const template = this.templates.find(item => Number(item.id) === Number(templateId)) || {}; return getTemplateConfig(templateId).then(response => { if (token !== this.generationToken || Number(this.generation.templateId) !== Number(templateId) || this.query.period !== period || this.effectiveBizLine !== bizLine) return; this.generationContext = { templateId, period, bizLine, templateRevision: template.revisionNo || 1, templateCode: template.templateCode, sections: Array.isArray(response.data && response.data.sections) ? response.data.sections : [], generateAfterSave: true }; this.summaryVisible = true }) },
    editSummary() { if (!this.canEditSummary || !this.summaryScopeReady) return; const token = ++this.generationToken; const period = this.query.period; const bizLine = this.effectiveBizLine; return getReportSummarySections({ period, bizLine }).then(response => { if (token !== this.generationToken || this.query.period !== period || this.effectiveBizLine !== bizLine) return; this.generationContext = { templateId: null, period, bizLine, templateRevision: 1, sections: Array.isArray(response.data) ? response.data : [], generateAfterSave: false }; this.summaryVisible = true }) },
    afterSummarySaved() { if (this.generationContext.generateAfterSave) return this.generatePrepared(); this.invalidateGeneration() },
    generatePrepared() { const context = Object.assign({}, this.generationContext); if (!context.templateId || !context.period || !context.bizLine) return Promise.resolve(); this.invalidateGeneration(); return generateReport({ templateId: context.templateId, period: context.period, bizLine: context.bizLine }).then(response => { this.$message.success('生成任务已持久化排队'); const receipt = response.data || {}; if (receipt.reportId) this.selectReport({ id: receipt.reportId, period: context.period, bizLine: context.bizLine, templateCode: context.templateCode, templateRevision: context.templateRevision, lifecycleStatus: 'GENERATING' }); this.reloadHistory() }) },
    retryArtifact(code) { const report = Object.assign({}, this.selected); return this.$confirm(`仅重试 ${report.reportNo || report.id} 的 ${code} 制品？已成功的上游制品会被复用。`, '定向重试').then(() => retryReportArtifact(report.id, code)).then(() => { this.$message.success('重试任务已排队'); if (Number(this.selected.id) === Number(report.id)) { this.pollDelay = 1500; this.refreshSelected() } }).catch(() => {}) },
    finalizeSelected() { const report = Object.assign({}, this.selected); return this.$confirm(`定稿 ${report.reportNo || report.id} 后，当前内容、模板修订和敏感权限快照将不可变。`, '确认定稿').then(() => finalizeReport(report.id, report.version)).then(response => { this.$message.success('报告已定稿'); if (Number(this.selected.id) === Number(report.id)) this.selected = Object.assign({}, this.selected, response.data || {}); this.reloadHistory() }).catch(() => {}) },
    downloadArtifact(code) { const report = Object.assign({}, this.selected); const extensions = { JSON: 'json', MARKDOWN: 'md', WORD: 'docx', PDF: 'pdf' }; return downloadReportArtifact(report.id, code).then(blob => saveAs(new Blob([blob]), `${report.reportNo || `report-${report.id}`}.${extensions[code] || code.toLowerCase()}`)) },
    uploadMarkdown(event) { const file = event.target.files && event.target.files[0]; const report = Object.assign({}, this.selected); event.target.value = ''; if (!file || !report.id) return; if (!/\.md$/i.test(file.name) || file.size > 1024 * 1024) { this.$message.error('请选择 1 MiB 内的 .md 文件'); return } this.$confirm(`导入会为 ${report.reportNo || report.id} 创建新的报告修订，不会覆盖已定稿历史。`, '导入 Markdown').then(() => importReportMarkdown(report.id, file)).then(response => { this.$message.success('Markdown 新版本已排队'); const receipt = response.data || {}; if (receipt.reportId) this.selectReport({ id: receipt.reportId, period: report.period, bizLine: report.bizLine, lifecycleStatus: 'GENERATING' }); this.reloadHistory() }).catch(() => {}) }
  }
}
</script>

<style lang="scss" scoped>
.report-center{min-height:100%;background:#eef0f4}.report-hero{position:relative;padding:28px 32px 22px;color:#fff;background:linear-gradient(110deg,#111b38 0%,#1d2d55 64%,#173d4b 100%);overflow:hidden}.report-hero:after{position:absolute;right:-70px;bottom:-140px;width:370px;height:370px;border:1px solid rgba(114,206,197,.3);border-radius:50%;content:''}.report-hero>div{position:relative;z-index:1}.report-hero>div:first-child>span{color:#72cec5;font:10px 'Arial Narrow',Arial;letter-spacing:.19em}.report-hero h1{margin:8px 0 4px;font-size:29px}.report-hero p{margin:0;color:#bdc7da;font-size:12px}.report-hero__controls{display:grid;grid-template-columns:155px 180px minmax(220px,1fr) auto;gap:9px;margin-top:21px;max-width:990px}.report-hero__legend{display:flex;gap:18px;margin-top:17px;padding-top:13px;border-top:1px solid rgba(255,255,255,.12);color:#aeb9ce;font-size:9px}.report-hero__legend i:not(.el-icon-lock){display:inline-block;width:6px;height:6px;margin-right:4px;border-radius:50%}.is-live{background:#72cec5}.is-ready{background:#76be8e}.is-failed{background:#e06a62}.report-layout{display:grid;grid-template-columns:310px minmax(0,1fr);gap:16px;padding:20px 24px 38px}.report-workspace{display:flex;min-width:0;flex-direction:column;gap:14px}.report-workspace__toolbar{display:flex;align-items:center;justify-content:space-between;padding:13px 16px}.report-workspace__toolbar strong,.report-workspace__toolbar span{display:block}.report-workspace__toolbar strong{color:var(--lab-indigo-deep);font-size:13px}.report-workspace__toolbar span{margin-top:3px;color:var(--lab-ink-soft);font-size:9px}.report-workspace__actions{display:flex;gap:7px}.report-body{min-height:350px;padding:20px}.report-body header{display:flex;align-items:center;justify-content:space-between;padding-bottom:13px;border-bottom:1px solid var(--lab-line)}.report-body h2{margin:5px 0 0;color:var(--lab-indigo-deep);font-size:18px}.report-body__poll{display:flex;align-items:center;gap:6px;color:#08796f;font-size:9px}.report-body__poll i{width:7px;height:7px;border-radius:50%;background:var(--lab-teal);box-shadow:0 0 0 4px rgba(17,147,136,.1);animation:pulse 1.4s infinite}.report-body pre{max-height:520px;margin:16px 0 0;overflow:auto;padding:20px;color:#27344d;background:#f7f8fa;font:12px/1.8 Consolas,monospace;white-space:pre-wrap}.report-body__skeleton{height:280px;margin-top:16px}.report-body__state{display:flex;min-height:220px;align-items:center;justify-content:center;gap:7px;color:var(--lab-ink-soft);font-size:11px}.report-body__state button{border:0;color:var(--lab-teal);background:transparent;font-weight:700}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}@keyframes pulse{50%{opacity:.4}}@media(max-width:1050px){.report-layout{grid-template-columns:1fr}.report-hero__controls{grid-template-columns:1fr 1fr}.report-hero__legend{flex-wrap:wrap}}@media(max-width:650px){.report-hero{padding:22px 16px}.report-layout{padding:14px}.report-hero__controls{grid-template-columns:1fr}.report-workspace__toolbar{align-items:flex-start;gap:10px;flex-direction:column}}
</style>
