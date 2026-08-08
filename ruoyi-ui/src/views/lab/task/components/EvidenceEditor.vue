<template>
  <el-drawer
    class="evidence-editor"
    :visible="visible"
    :size="drawerSize"
    :with-header="false"
    @close="$emit('close')"
  >
    <div class="evidence-editor__shell">
      <header class="evidence-editor__header">
        <button type="button" aria-label="关闭" @click="$emit('close')"><i class="el-icon-close" /></button>
        <span class="lab-eyebrow">Evidence &amp; gates</span>
        <h2>证据与质量门禁</h2>
        <p>{{ task ? task.title : '任务' }}</p>
      </header>

      <el-tabs v-model="activeTab" class="evidence-editor__tabs">
        <el-tab-pane :label="`证据 ${evidence.length}`" name="evidence">
          <div class="evidence-editor__toolbar">
            <p>证据独立留痕；提交结果时仍需把当次证据随提交命令一并发送。</p>
            <el-button v-if="editable" v-hasPermi="['lab:task:evidence']" size="mini" type="primary" icon="el-icon-plus" @click="showEvidenceForm = true">添加证据</el-button>
          </div>
          <div v-if="loading" class="evidence-editor__loading lab-skeleton" />
          <div v-else-if="!evidence.length" class="lab-empty">尚未添加证据</div>
          <ol v-else class="evidence-editor__list">
            <li v-for="item in evidence" :key="item.id">
              <span class="evidence-editor__file"><i class="el-icon-document" /></span>
              <div>
                <strong>{{ item.evidenceTitle }}</strong>
                <small>{{ item.evidenceType }} · {{ item.auditStatus || 'PENDING' }} · {{ formatTime(item.submitTime) }}</small>
                <p v-if="item.auditComment">审核：{{ item.auditComment }}</p>
              </div>
              <div class="evidence-editor__actions">
                <el-button v-if="safeUrl(item.evidenceUrl)" type="text" @click="openEvidence(item.evidenceUrl)">查看</el-button>
                <el-button v-if="editable && item.auditStatus !== 'APPROVED'" v-hasPermi="['lab:task:evidence']" type="text" class="is-danger" @click="removeEvidence(item)">删除</el-button>
              </div>
            </li>
          </ol>
        </el-tab-pane>

        <el-tab-pane :label="`质量门禁 ${gates.length}`" name="gates">
          <div class="evidence-editor__toolbar">
            <p>门禁必须绑定同任务、已审核通过的明确证据。</p>
            <el-button v-if="editable" v-hasPermi="['lab:task:edit']" size="mini" type="primary" icon="el-icon-plus" @click="openGateForm()">新增门禁</el-button>
          </div>
          <div v-if="loading" class="evidence-editor__loading lab-skeleton" />
          <div v-else-if="!gates.length" class="lab-empty">尚未配置质量门禁</div>
          <ol v-else class="evidence-editor__gates">
            <li v-for="gate in gates" :key="gate.id">
              <span :class="gate.gateStatus === 'PASSED' ? 'is-passed' : 'is-pending'">
                <i :class="gate.gateStatus === 'PASSED' ? 'el-icon-check' : 'el-icon-more'" />
              </span>
              <div><strong>{{ gate.gateNo }} · {{ gate.gateName }}</strong><small>{{ gate.gateStatus || 'PENDING' }}<template v-if="gate.checkResult"> · {{ gate.checkResult }}</template></small></div>
              <div class="evidence-editor__actions">
                <el-button v-if="editable && gate.gateStatus !== 'PASSED'" v-hasPermi="['lab:task:edit']" type="text" @click="openGateForm(gate)">编辑</el-button>
                <el-button v-if="task && task.workflowStatus === 'CONFIRMED' && gate.gateStatus !== 'PASSED'" v-hasPermi="['lab:task:review']" type="text" @click="openPassGate(gate)">验收</el-button>
                <el-button v-if="editable && gate.gateStatus !== 'PASSED'" v-hasPermi="['lab:task:edit']" type="text" class="is-danger" @click="removeGate(gate)">删除</el-button>
              </div>
            </li>
          </ol>
        </el-tab-pane>
      </el-tabs>

      <el-dialog title="添加证据" :visible.sync="showEvidenceForm" width="520px" append-to-body>
        <el-form ref="evidenceForm" :model="evidenceForm" :rules="evidenceRules" label-position="top">
          <el-form-item label="证据类型" prop="evidenceType">
            <el-select v-model="evidenceForm.evidenceType"><el-option label="文档" value="DOCUMENT" /><el-option label="链接" value="LINK" /><el-option label="数据" value="DATA" /><el-option label="图片" value="IMAGE" /></el-select>
          </el-form-item>
          <el-form-item label="证据名称" prop="evidenceTitle"><el-input v-model.trim="evidenceForm.evidenceTitle" maxlength="255" /></el-form-item>
          <el-form-item label="上传文件或填写链接" prop="evidenceUrl">
            <file-upload v-model="evidenceForm.evidenceUrl" :limit="1" :file-size="10" :file-type="fileTypes" />
            <el-input v-model.trim="evidenceForm.evidenceUrl" class="evidence-editor__url" placeholder="也可填写 https:// 或系统内 / 路径" />
          </el-form-item>
        </el-form>
        <span slot="footer"><el-button @click="showEvidenceForm = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEvidence">保存</el-button></span>
      </el-dialog>

      <el-dialog :title="gateForm.id ? '编辑质量门禁' : '新增质量门禁'" :visible.sync="showGateForm" width="500px" append-to-body>
        <el-form ref="gateForm" :model="gateForm" :rules="gateRules" label-position="top">
          <el-form-item label="门禁编号" prop="gateNo"><el-input v-model.trim="gateForm.gateNo" maxlength="64" /></el-form-item>
          <el-form-item label="门禁名称" prop="gateName"><el-input v-model.trim="gateForm.gateName" maxlength="255" /></el-form-item>
        </el-form>
        <span slot="footer"><el-button @click="showGateForm = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveGate">保存</el-button></span>
      </el-dialog>

      <el-dialog title="质量门禁验收" :visible.sync="showPassForm" width="520px" append-to-body>
        <el-form :model="passForm" label-position="top">
          <el-form-item label="绑定已审核证据">
            <el-select v-model="passForm.evidenceId" filterable><el-option v-for="item in approvedEvidence" :key="item.id" :label="item.evidenceTitle" :value="item.id" /></el-select>
          </el-form-item>
          <el-form-item label="验收结果"><el-input v-model.trim="passForm.result" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        </el-form>
        <span slot="footer"><el-button @click="showPassForm = false">取消</el-button><el-button type="primary" :disabled="!passForm.evidenceId || !passForm.result" :loading="saving" @click="confirmPassGate">确认通过</el-button></span>
      </el-dialog>
    </div>
  </el-drawer>
</template>

<script>
import {
  addQualityGate,
  addTaskEvidence,
  deleteQualityGate,
  deleteTaskEvidence,
  listQualityGates,
  listTaskEvidence,
  passQualityGate,
  updateQualityGate
} from '@/api/lab/task'

export default {
  name: 'EvidenceEditor',
  props: {
    visible: { type: Boolean, default: false },
    task: { type: Object, default: null }
  },
  data() {
    return {
      activeTab: 'evidence',
      evidence: [],
      gates: [],
      loading: false,
      saving: false,
      showEvidenceForm: false,
      showGateForm: false,
      showPassForm: false,
      viewportWidth: window.innerWidth,
      evidenceForm: { evidenceType: 'DOCUMENT', evidenceTitle: '', evidenceUrl: '' },
      gateForm: { id: null, taskId: null, gateNo: '', gateName: '', gateStatus: 'PENDING' },
      passForm: { gateId: null, evidenceId: null, result: '' },
      fileTypes: ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'pdf', 'png', 'jpg', 'jpeg', 'zip'],
      evidenceRules: {
        evidenceType: [{ required: true, message: '请选择证据类型', trigger: 'change' }],
        evidenceTitle: [{ required: true, message: '请输入证据名称', trigger: 'blur' }],
        evidenceUrl: [{ required: true, message: '请上传文件或填写链接', trigger: 'blur' }]
      },
      gateRules: {
        gateNo: [{ required: true, message: '请输入门禁编号', trigger: 'blur' }],
        gateName: [{ required: true, message: '请输入门禁名称', trigger: 'blur' }]
      }
    }
  },
  computed: {
    drawerSize() {
      return this.viewportWidth < 720 ? '100%' : '620px'
    },
    editable() {
      return this.task && this.task.periodLockFlag !== '1' && ['PENDING_REVIEW', 'CONFIRMED'].indexOf(this.task.workflowStatus) < 0
    },
    approvedEvidence() {
      return this.evidence.filter(item => item.auditStatus === 'APPROVED')
    }
  },
  watch: {
    visible(value) {
      if (value) this.load()
    }
  },
  mounted() {
    window.addEventListener('resize', this.updateWidth)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateWidth)
  },
  methods: {
    load() {
      if (!this.task || !this.task.id) return Promise.resolve()
      this.loading = true
      return Promise.all([listTaskEvidence(this.task.id), listQualityGates(this.task.id)])
        .then(results => {
          this.evidence = results[0].data || []
          this.gates = results[1].data || []
        })
        .finally(() => { this.loading = false })
    },
    updateWidth() {
      this.viewportWidth = window.innerWidth
    },
    saveEvidence() {
      this.$refs.evidenceForm.validate(valid => {
        if (!valid) return
        this.saving = true
        addTaskEvidence(this.task.id, this.evidenceForm).then(() => {
          this.$message.success('证据已保存')
          this.showEvidenceForm = false
          this.evidenceForm = { evidenceType: 'DOCUMENT', evidenceTitle: '', evidenceUrl: '' }
          return this.load()
        }).finally(() => { this.saving = false })
      })
    },
    removeEvidence(item) {
      this.$confirm('确认删除这条未审核证据？', '删除证据', { type: 'warning' })
        .then(() => deleteTaskEvidence(this.task.id, item.id))
        .then(() => { this.$message.success('证据已删除'); return this.load() })
        .catch(() => {})
    },
    openGateForm(gate) {
      this.gateForm = gate
        ? Object.assign({}, gate)
        : { id: null, taskId: this.task.id, gateNo: '', gateName: '', gateStatus: 'PENDING' }
      this.showGateForm = true
      this.$nextTick(() => this.$refs.gateForm && this.$refs.gateForm.clearValidate())
    },
    saveGate() {
      this.$refs.gateForm.validate(valid => {
        if (!valid) return
        this.saving = true
        const action = this.gateForm.id ? updateQualityGate(this.gateForm) : addQualityGate(this.gateForm)
        action.then(() => {
          this.$message.success('质量门禁已保存')
          this.showGateForm = false
          return this.load()
        }).finally(() => { this.saving = false })
      })
    },
    removeGate(gate) {
      this.$confirm('确认删除这项未通过门禁？', '删除门禁', { type: 'warning' })
        .then(() => deleteQualityGate(gate.id))
        .then(() => { this.$message.success('门禁已删除'); return this.load() })
        .catch(() => {})
    },
    openPassGate(gate) {
      this.passForm = { gateId: gate.id, evidenceId: null, result: '' }
      this.showPassForm = true
    },
    confirmPassGate() {
      this.saving = true
      passQualityGate(this.passForm.gateId, this.passForm.evidenceId, this.passForm.result).then(() => {
        this.$message.success('质量门禁已通过')
        this.showPassForm = false
        return this.load()
      }).finally(() => { this.saving = false })
    },
    safeUrl(value) {
      const url = String(value || '').trim()
      return /^(https?:\/\/|\/)/i.test(url) ? url : ''
    },
    openEvidence(value) {
      const url = this.safeUrl(value)
      if (!url) return
      const resolved = url.charAt(0) === '/' ? `${process.env.VUE_APP_BASE_API}${url}` : url
      window.open(resolved, '_blank', 'noopener,noreferrer')
    },
    formatTime(value) {
      if (!value) return '未记录时间'
      const date = new Date(value)
      return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
    }
  }
}
</script>

<style lang="scss" scoped>
.evidence-editor__shell { min-height: 100%; color: var(--lab-ink); background: var(--lab-canvas); }
.evidence-editor__header { position: relative; padding: 27px 28px 22px; color: #fff; background: var(--lab-indigo-deep); }
.evidence-editor__header button { position: absolute; top: 18px; right: 18px; border: 0; color: #fff; background: transparent; font-size: 18px; cursor: pointer; }
.evidence-editor__header h2 { margin: 7px 0 5px; font-size: 21px; }
.evidence-editor__header p { margin: 0; overflow: hidden; color: #c5cee0; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.evidence-editor__tabs { margin: 16px 18px; padding: 8px 18px 18px; border: 1px solid var(--lab-line); background: #fff; }
.evidence-editor__toolbar { display: flex; min-height: 45px; align-items: center; justify-content: space-between; gap: 14px; }
.evidence-editor__toolbar p { margin: 0; color: #5f6b80; font-size: 10px; line-height: 16px; }
.evidence-editor__list, .evidence-editor__gates { margin: 8px 0 0; padding: 0; list-style: none; }
.evidence-editor__list li, .evidence-editor__gates li { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: start; gap: 10px; padding: 13px 0; border-top: 1px solid #edf0f5; }
.evidence-editor__file, .evidence-editor__gates > li > span { display: inline-flex; width: 31px; height: 31px; align-items: center; justify-content: center; color: var(--lab-teal); background: #e1f2ef; }
.evidence-editor__gates > li > span.is-pending { color: var(--lab-warning); background: #fff0ce; }
.evidence-editor__list strong, .evidence-editor__list small, .evidence-editor__gates strong, .evidence-editor__gates small { display: block; }
.evidence-editor__list strong, .evidence-editor__gates strong { font-size: 11px; line-height: 17px; }
.evidence-editor__list small, .evidence-editor__gates small { margin-top: 3px; color: #5f6b80; font-size: 9px; }
.evidence-editor__list p { margin: 5px 0 0; color: var(--lab-ink-soft); font-size: 10px; }
.evidence-editor__actions { display: flex; gap: 3px; }
.evidence-editor__actions .is-danger { color: var(--lab-danger); }
.evidence-editor__loading { height: 240px; margin-top: 8px; }
.evidence-editor__url { margin-top: 8px; }
</style>
