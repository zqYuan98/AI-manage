<template>
  <el-dialog :visible.sync="open" width="860px" append-to-body :close-on-click-modal="false" title="生成前摘要校验" @open="load">
    <div class="summary-intro"><i class="el-icon-collection-tag" /><div><strong>{{ period }} · {{ bizLine }}</strong><span>必填章节完整后才能排队；可选章节可保持空白。</span></div></div>
    <div v-loading="loading" class="summary-list">
      <div v-if="loadError" class="summary-error" role="alert">摘要加载失败。为避免把旧模板内容写入当前报告，已禁止提交；请关闭后重试。</div>
      <article v-for="entry in entries" :key="entry.sectionCode" :class="{ incomplete: incomplete(entry) }">
        <header><div><b>{{ entry.sectionName }}</b><small>{{ entry.sectionCode }}</small></div><el-tag size="mini" :type="entry.required ? 'danger' : 'info'">{{ entry.required ? '必填' : '可选' }}</el-tag></header>
        <el-input v-model="entry.bizLineSummary" type="textarea" :rows="2" maxlength="1200" show-word-limit aria-label="业务线摘要" placeholder="业务线摘要：本期产出、价值与管理判断" />
        <el-input v-model="entry.reasonAnalysis" type="textarea" :rows="2" maxlength="1200" show-word-limit aria-label="原因分析" placeholder="原因分析：事实、偏差与影响" />
        <el-input v-model="entry.nextStep" type="textarea" :rows="2" maxlength="1200" show-word-limit aria-label="下一步策略" placeholder="下一步策略：负责人、时间点与验收条件" />
        <p v-if="incomplete(entry)"><i class="el-icon-warning-outline" /> 一旦填写摘要，必须同时提供业务线摘要、原因分析和下一步策略</p>
      </article>
      <div v-if="!entries.length && !loading && !loadError" class="lab-empty">该模板没有人工摘要章节，可直接生成。</div>
    </div>
    <span slot="footer"><el-button @click="open=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || loadError || hasIncomplete" @click="saveAndContinue">{{ generateAfterSave ? '保存摘要并生成' : '保存摘要' }}</el-button></span>
  </el-dialog>
</template>
<script>
import { getReportSummary, saveReportSummaries } from '@/api/lab/report'
export default {
  name: 'ReportSummaryEditor', props: { visible: Boolean, period: { type: String, default: '' }, bizLine: { type: String, default: '' }, sections: { type: Array, default: () => [] }, generateAfterSave: Boolean },
  data() { return { loading: false, saving: false, loadError: false, entries: [], requestId: 0 } },
  computed: { open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } }, hasIncomplete() { return this.entries.some(item => this.incomplete(item)) } },
  methods: {
    load() { const token = ++this.requestId; this.loading = true; this.loadError = false; this.entries = []; return getReportSummary({ period: this.period, bizLine: this.bizLine }).then(response => { if (token !== this.requestId) return; const saved = Array.isArray(response.data) ? response.data : []; const byCode = saved.reduce((map, item) => { map[item.sectionCode] = item; return map }, {}); this.entries = this.manualSections().map(section => { const item = byCode[section.sectionCode] || {}; let parsed = {}; try { parsed = JSON.parse(item.summaryJson || '{}') } catch (e) { parsed = {} } return { id: item.id, sectionCode: section.sectionCode, sectionName: section.sectionName, required: this.required(section), bizLineSummary: parsed.bizLineSummary || '', reasonAnalysis: parsed.reasonAnalysis || '', nextStep: parsed.nextStep || '', sourceRevision: item.sourceRevision || 0 } }) }).catch(() => { if (token === this.requestId) this.loadError = true }).finally(() => { if (token === this.requestId) this.loading = false }) },
    manualSections() { return this.sections.filter(item => typeof item.required === 'boolean' || (item.visibleFlag !== '0' && (item.manualFlag === '1' || item.sectionType === 'MANUAL'))) },
    required(section) { if (typeof section.required === 'boolean') return section.required; try { return JSON.parse(section.renderConfigJson || '{}').required === true } catch (e) { return false } },
    complete(entry) { return Boolean(String(entry.bizLineSummary || '').trim() && String(entry.reasonAnalysis || '').trim() && String(entry.nextStep || '').trim()) },
    started(entry) { return Boolean(String(entry.bizLineSummary || '').trim() || String(entry.reasonAnalysis || '').trim() || String(entry.nextStep || '').trim()) },
    incomplete(entry) { return (entry.required || this.started(entry)) && !this.complete(entry) },
    saveAndContinue() { if (this.loading || this.loadError || this.hasIncomplete) return; this.saving = true; const writes = this.entries.map(item => ({ id: item.id, period: this.period, bizLine: this.bizLine, sectionCode: item.sectionCode, summaryJson: this.complete(item) ? JSON.stringify({ bizLineSummary: item.bizLineSummary.trim(), reasonAnalysis: item.reasonAnalysis.trim(), nextStep: item.nextStep.trim() }) : '', sourceRevision: item.sourceRevision || 0 })); return saveReportSummaries(writes).then(() => { this.$message.success('摘要已原子保存'); this.open = false; this.$emit('continue') }).finally(() => { this.saving = false }) }
  }
}
</script>
<style lang="scss" scoped>
.summary-intro{display:flex;align-items:center;gap:12px;padding:13px 15px;color:#fff;background:var(--lab-indigo-deep)}.summary-intro>i{font-size:22px;color:#72cec5}.summary-intro strong,.summary-intro span{display:block}.summary-intro span{margin-top:3px;color:#c3cbda;font-size:10px}.summary-list{min-height:160px;max-height:55vh;overflow:auto;padding:10px 2px}.summary-error{margin-top:10px;padding:12px;color:#873f3d;background:#fff0ef;font-size:10px}.summary-list article{margin-top:10px;padding:14px;border:1px solid var(--lab-line);border-left:3px solid var(--lab-teal);background:#fafbfc}.summary-list article.incomplete{border-left-color:var(--lab-danger)}article header{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}article header b,article header small{display:block}article header b{color:var(--lab-indigo-deep);font-size:13px}article header small{margin-top:3px;color:var(--lab-ink-soft);font-size:8px;letter-spacing:.08em}.el-textarea+.el-textarea{margin-top:9px}article p{margin:7px 0 0;color:var(--lab-danger);font-size:10px}
.summary-intro{display:flex;align-items:center;gap:12px;padding:13px 15px;color:#fff;background:var(--lab-indigo-deep)}.summary-intro>i{font-size:22px;color:#72cec5}.summary-intro strong,.summary-intro span{display:block}.summary-intro span{margin-top:3px;color:#c3cbda;font-size:10px}.summary-list{min-height:160px;max-height:55vh;overflow:auto;padding:10px 2px}.summary-error{margin-top:10px;padding:12px;color:#873f3d;background:#fff0ef;font-size:10px}.summary-list article{margin-top:10px;padding:14px;border:1px solid var(--lab-line);border-left:3px solid var(--lab-teal);background:#fafbfc}.summary-list article.incomplete{border-left-color:var(--lab-danger)}article header{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}article header b,article header small{display:block}article header b{color:var(--lab-indigo-deep);font-size:13px}article header small{margin-top:3px;color:var(--lab-ink-soft);font-size:8px;letter-spacing:.08em}.el-textarea+.el-textarea{margin-top:9px}article p{margin:7px 0 0;color:var(--lab-danger);font-size:10px}::v-deep .el-dialog{max-width:calc(100vw - 24px)}
</style>
