<template>
  <el-drawer
    class="task-review"
    :visible="visible"
    :size="drawerSize"
    :with-header="false"
    :wrapper-closable="false"
    @close="$emit('close')"
  >
    <div class="task-review__shell">
      <header class="task-review__header">
        <button type="button" aria-label="关闭" @click="$emit('close')"><i class="el-icon-close" /></button>
        <span class="lab-eyebrow">{{ mode === 'submit' ? 'Result submission' : 'Independent review' }}</span>
        <h2>{{ mode === 'submit' ? '提交任务结果' : '审核任务结果' }}</h2>
        <p>{{ task ? task.title : '任务' }}</p>
      </header>

      <section v-if="task" class="task-review__summary">
        <div><span>计划日期</span><strong>{{ task.planDate || '—' }}</strong></div>
        <div><span>当前状态</span><strong>{{ task.workflowStatus }}</strong></div>
        <div><span>结果</span><strong>{{ task.resultStatus || 'DOING' }}</strong></div>
      </section>

      <el-form v-if="mode === 'submit'" ref="submitForm" class="task-review__form" :model="submitForm" label-position="top">
        <el-form-item label="提交结论" :error="errorFor('requestedResultStatus')">
          <el-radio-group v-model="submitForm.requestedResultStatus">
            <el-radio-button label="">完成（系统按日期判定）</el-radio-button>
            <el-radio-button label="EXCEEDED">超额完成</el-radio-button>
            <el-radio-button label="UNDONE">未完成</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="submitForm.requestedResultStatus !== 'UNDONE'">
          <el-form-item label="实际完成时间" :error="errorFor('actualFinishTime')">
            <el-date-picker v-model="submitForm.actualFinishTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="选择完成时间" />
          </el-form-item>
          <el-form-item label="完成说明" :error="errorFor('resultDesc')">
            <el-input v-model.trim="submitForm.resultDesc" type="textarea" :rows="3" maxlength="2000" show-word-limit />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="未完成原因" :error="errorFor('failReason')"><el-input v-model.trim="submitForm.failReason" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
          <el-form-item label="下一步行动" :error="errorFor('nextAction')"><el-input v-model.trim="submitForm.nextAction" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        </template>

        <div class="task-review__evidence-head">
          <div><h3>当次提交证据</h3><p>完成类结果至少一条；证据会随本次提交进入待审核状态。</p></div>
          <el-button size="mini" icon="el-icon-plus" @click="addEvidenceRow">添加</el-button>
        </div>
        <p v-if="errorFor('evidenceList')" class="task-review__field-error">{{ errorFor('evidenceList') }}</p>
        <div v-for="(item, index) in submitForm.evidenceList" :key="index" class="task-review__evidence-row">
          <el-select v-model="item.evidenceType" size="small"><el-option label="文档" value="DOCUMENT" /><el-option label="链接" value="LINK" /><el-option label="数据" value="DATA" /><el-option label="图片" value="IMAGE" /></el-select>
          <el-input v-model.trim="item.evidenceTitle" size="small" placeholder="证据名称" />
          <file-upload v-model="item.evidenceUrl" :limit="1" :file-size="10" :file-type="fileTypes" :is-show-tip="false" />
          <el-input v-model.trim="item.evidenceUrl" size="small" placeholder="上传后路径或 https:// 链接" />
          <button type="button" aria-label="删除证据" @click="submitForm.evidenceList.splice(index, 1)"><i class="el-icon-delete" /></button>
          <small v-if="evidenceError(index)" class="task-review__evidence-error">{{ evidenceError(index) }}</small>
        </div>
      </el-form>

      <el-form v-else class="task-review__form" :model="reviewForm" label-position="top">
        <section class="task-review__result">
          <h3>提交事实</h3>
          <dl>
            <dt>完成说明</dt><dd>{{ task && task.resultDesc || '—' }}</dd>
            <dt>未完成原因</dt><dd>{{ task && task.failReason || '—' }}</dd>
            <dt>下一步行动</dt><dd>{{ task && task.nextAction || '—' }}</dd>
          </dl>
        </section>
        <el-form-item label="选择通过的待审核证据" :error="errorFor('approvedEvidenceIds')">
          <el-checkbox-group v-model="reviewForm.approvedEvidenceIds" class="task-review__checks">
            <el-checkbox v-for="item in pendingEvidence" :key="item.id" :label="item.id">
              {{ item.evidenceTitle }} <small>{{ item.evidenceType }}</small>
            </el-checkbox>
          </el-checkbox-group>
          <p v-if="!pendingEvidence.length" class="task-review__hint">没有待审核证据；已有通过证据时可直接审核任务。</p>
        </el-form-item>
        <el-form-item label="证据审核意见" :error="errorFor('evidenceAuditComment')"><el-input v-model.trim="reviewForm.evidenceAuditComment" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
        <el-form-item label="任务审核意见" :error="errorFor('reviewerComment')"><el-input v-model.trim="reviewForm.reviewerComment" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item v-if="task && task.resultStatus === 'EXCEEDED'" :error="errorFor('exceededConfirmed')">
          <el-checkbox v-model="reviewForm.exceededConfirmed">我已核对证据并明确确认超额完成</el-checkbox>
        </el-form-item>
      </el-form>

      <footer class="task-review__footer">
        <el-button @click="$emit('close')">取消</el-button>
        <el-button v-if="mode === 'submit'" v-hasPermi="['lab:task:edit']" type="primary" :loading="saving" @click="submitResult">提交审核</el-button>
        <template v-else>
          <el-button v-hasPermi="['lab:task:review']" type="danger" plain :loading="saving" @click="reviewReturn">退回</el-button>
          <el-button v-hasPermi="['lab:task:review']" type="primary" :loading="saving" @click="reviewPass">审核通过</el-button>
        </template>
      </footer>
    </div>
  </el-drawer>
</template>

<script>
import { listTaskEvidence } from '@/api/lab/task'

const newSubmit = () => ({
  requestedResultStatus: '',
  actualFinishTime: '',
  resultDesc: '',
  failReason: '',
  nextAction: '',
  evidenceList: []
})

const newReview = () => ({
  reviewerComment: '',
  evidenceAuditComment: '',
  approvedEvidenceIds: [],
  exceededConfirmed: false
})

export default {
  name: 'TaskReviewPanel',
  props: {
    visible: { type: Boolean, default: false },
    task: { type: Object, default: null },
    mode: { type: String, default: 'submit' },
    saving: { type: Boolean, default: false },
    fieldErrors: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      submitForm: newSubmit(),
      reviewForm: newReview(),
      evidence: [],
      viewportWidth: window.innerWidth,
      fileTypes: ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'pdf', 'png', 'jpg', 'jpeg', 'zip']
    }
  },
  computed: {
    drawerSize() {
      return this.viewportWidth < 720 ? '100%' : '640px'
    },
    pendingEvidence() {
      return this.evidence.filter(item => item.auditStatus === 'PENDING')
    }
  },
  watch: {
    visible(value) {
      if (!value) return
      this.submitForm = newSubmit()
      this.reviewForm = newReview()
      this.loadEvidence()
    }
  },
  mounted() {
    window.addEventListener('resize', this.updateWidth)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateWidth)
  },
  methods: {
    loadEvidence() {
      if (!this.task || !this.task.id) return
      listTaskEvidence(this.task.id).then(response => { this.evidence = response.data || [] })
    },
    updateWidth() {
      this.viewportWidth = window.innerWidth
    },
    addEvidenceRow() {
      this.submitForm.evidenceList.push({ evidenceType: 'DOCUMENT', evidenceTitle: '', evidenceUrl: '' })
    },
    submitResult() {
      const undone = this.submitForm.requestedResultStatus === 'UNDONE'
      if (undone && (!this.submitForm.failReason || !this.submitForm.nextAction)) {
        this.$message.warning('未完成结果必须填写原因和下一步行动')
        return
      }
      if (!undone && (!this.submitForm.actualFinishTime || !this.submitForm.resultDesc || !this.submitForm.evidenceList.length)) {
        this.$message.warning('完成结果必须填写完成时间、说明并添加至少一条证据')
        return
      }
      const missingEvidence = this.submitForm.evidenceList.some(item => !item.evidenceType || !item.evidenceTitle || !item.evidenceUrl)
      if (missingEvidence) {
        this.$message.warning('请补齐每条证据的类型、名称和文件或链接')
        return
      }
      this.$emit('submit', JSON.parse(JSON.stringify(this.submitForm)))
    },
    reviewReturn() {
      if (!this.reviewForm.reviewerComment) {
        this.$message.warning('退回前必须填写审核意见')
        return
      }
      this.$emit('return', JSON.parse(JSON.stringify(this.reviewForm)))
    },
    reviewPass() {
      if (!this.reviewForm.reviewerComment) {
        this.$message.warning('审核通过前必须填写审核意见')
        return
      }
      if (this.task && this.task.resultStatus === 'EXCEEDED' && !this.reviewForm.exceededConfirmed) {
        this.$message.warning('超额完成需要审核人明确确认')
        return
      }
      this.$emit('pass', JSON.parse(JSON.stringify(this.reviewForm)))
    },
    errorFor(field) {
      return this.fieldErrors[field] || ''
    },
    evidenceError(index) {
      const prefix = `evidenceList[${index}]`
      return this.errorFor(prefix) || this.errorFor(`${prefix}.evidenceType`) || this.errorFor(`${prefix}.evidenceTitle`) || this.errorFor(`${prefix}.evidenceUrl`)
    }
  }
}
</script>

<style lang="scss" scoped>
.task-review__shell { min-height: 100%; padding-bottom: 78px; color: var(--lab-ink); background: var(--lab-canvas); }
.task-review__header { position: relative; padding: 27px 28px 22px; color: #fff; background: var(--lab-indigo-deep); }
.task-review__header button { position: absolute; top: 18px; right: 18px; border: 0; color: #fff; background: transparent; font-size: 18px; cursor: pointer; }
.task-review__header h2 { margin: 7px 0 5px; font-size: 21px; }
.task-review__header p { margin: 0; overflow: hidden; color: #c5cee0; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.task-review__summary { display: grid; grid-template-columns: repeat(3, 1fr); margin: 15px 18px 0; border: 1px solid var(--lab-line); background: #fff; }
.task-review__summary div { padding: 13px 14px; border-right: 1px solid var(--lab-line); }
.task-review__summary div:last-child { border-right: 0; }
.task-review__summary span, .task-review__summary strong { display: block; }
.task-review__summary span { color: #5f6b80; font-size: 9px; }
.task-review__summary strong { margin-top: 5px; color: var(--lab-indigo); font-size: 11px; }
.task-review__form { margin: 14px 18px; padding: 18px; border: 1px solid var(--lab-line); background: #fff; }
.task-review__form .el-date-editor { width: 100%; }
.task-review__evidence-head { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 8px; padding-top: 14px; border-top: 1px solid var(--lab-line); }
.task-review__evidence-head h3, .task-review__evidence-head p { margin: 0; }
.task-review__evidence-head h3 { font-size: 13px; }
.task-review__evidence-head p { margin-top: 3px; color: #5f6b80; font-size: 9px; }
.task-review__evidence-row { position: relative; display: grid; grid-template-columns: 120px minmax(0, 1fr); gap: 8px; margin-top: 10px; padding: 12px 38px 12px 12px; border: 1px solid var(--lab-line); background: #f8f9fb; }
.task-review__evidence-row > button { position: absolute; top: 10px; right: 9px; border: 0; color: var(--lab-danger); background: transparent; cursor: pointer; }
.task-review__evidence-error { grid-column: 1 / -1; color: var(--lab-danger); font-size: 10px; }
.task-review__field-error { color: var(--lab-danger); font-size: 10px; }
.task-review__result { margin-bottom: 14px; padding: 14px; border-left: 3px solid var(--lab-teal); background: #f4f9f8; }
.task-review__result h3 { margin: 0 0 8px; font-size: 13px; }
.task-review__result dl { display: grid; grid-template-columns: 88px 1fr; margin: 0; font-size: 10px; line-height: 18px; }
.task-review__result dt { color: #5f6b80; }
.task-review__result dd { margin: 0; }
.task-review__checks { display: flex; flex-direction: column; gap: 8px; }
.task-review__checks small { color: #5f6b80; }
.task-review__hint { color: #5f6b80; font-size: 10px; }
.task-review__footer { position: absolute; right: 0; bottom: 0; left: 0; z-index: 2; display: flex; justify-content: flex-end; gap: 8px; padding: 14px 20px; border-top: 1px solid var(--lab-line); background: #fff; }
@media (max-width: 620px) { .task-review__evidence-row { grid-template-columns: 1fr; } }
</style>
