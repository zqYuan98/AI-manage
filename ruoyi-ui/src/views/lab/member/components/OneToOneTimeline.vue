<template>
  <section class="one-timeline">
    <header><div><span>Conversation history</span><h3>一对一沟通</h3></div><el-button v-hasPermi="['lab:one2one:add']" size="small" type="primary" @click="openCreate">记录沟通</el-button></header>
    <el-timeline v-if="records.length">
      <el-timeline-item v-for="item in records" :key="item.id" :timestamp="item.meetingDate" placement="top" color="#0a7b74">
        <article>
          <div class="one-timeline__title"><strong>{{ item.topic }}</strong><el-tag size="mini" effect="plain">{{ item.status || 'OPEN' }}</el-tag></div>
          <dl><dt>事实与证据</dt><dd>{{ item.factsEvidence || '—' }}</dd><dt>困难</dt><dd>{{ item.difficulties || '—' }}</dd><dt>下一步</dt><dd>{{ item.nextAction || '—' }}</dd></dl>
          <p v-if="item.managerComment"><i class="el-icon-chat-dot-round" /> {{ item.managerComment }}</p>
          <el-button v-hasPermi="['lab:one2one:edit']" type="text" @click="openEdit(item)">编辑记录</el-button>
        </article>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无一对一沟通记录" :image-size="70" />

    <el-dialog :title="form.id ? '编辑沟通记录' : '记录一对一沟通'" :visible.sync="dialogVisible" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="沟通日期" prop="meetingDate"><el-date-picker v-model="form.meetingDate" type="date" value-format="yyyy-MM-dd" /></el-form-item></el-col><el-col :span="12"><el-form-item label="状态"><el-select v-model="form.status"><el-option label="跟进中" value="OPEN" /><el-option label="已闭环" value="CLOSED" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="主题" prop="topic"><el-input v-model.trim="form.topic" maxlength="120" /></el-form-item>
        <el-form-item label="事实与证据" prop="factsEvidence"><el-input v-model.trim="form.factsEvidence" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="困难"><el-input v-model.trim="form.difficulties" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="下一步行动" prop="nextAction"><el-input v-model.trim="form.nextAction" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="管理者反馈"><el-input v-model.trim="form.managerComment" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import { addOneToOne, updateOneToOne } from '@/api/lab/member'

export default {
  name: 'OneToOneTimeline',
  props: { records: { type: Array, default: () => [] }, member: { type: Object, required: true }},
  data() {
    return {
      dialogVisible: false, saving: false, form: {},
      rules: { meetingDate: [{ required: true, message: '请选择沟通日期', trigger: 'change' }], topic: [{ required: true, message: '请输入主题', trigger: 'blur' }], factsEvidence: [{ required: true, message: '请输入事实与证据', trigger: 'blur' }], nextAction: [{ required: true, message: '请输入下一步行动', trigger: 'blur' }] }
    }
  },
  methods: {
    base() { return { memberId: this.member.id, leaderId: this.member.leaderId, meetingDate: '', topic: '', factsEvidence: '', difficulties: '', nextAction: '', managerComment: '', status: 'OPEN' } },
    openCreate() {
      if (!this.member.leaderId) return this.$message.warning('请先在成员档案中配置负责人')
      this.form = this.base(); this.dialogVisible = true
    },
    openEdit(item) { this.form = { ...item }; this.dialogVisible = true },
    save() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.saving = true
        const action = this.form.id ? updateOneToOne : addOneToOne
        action(this.form).then(() => { this.$message.success('沟通记录已保存'); this.dialogVisible = false; this.$emit('saved') }).finally(() => { this.saving = false })
      })
    }
  }
}
</script>

<style scoped lang="scss">
.one-timeline header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
.one-timeline header span { color:#346c68; font-size:11px; letter-spacing:.14em; text-transform:uppercase; }
.one-timeline h3 { margin:4px 0 0; color:#142a38; }
.one-timeline article { padding:16px 18px; border:1px solid #e2e8e8; border-radius:10px; background:#fbfcfc; }
.one-timeline__title { display:flex; justify-content:space-between; color:#183344; }
.one-timeline dl { display:grid; grid-template-columns:90px 1fr; gap:7px 12px; font-size:13px; }
.one-timeline dt { color:#6f7d89; }.one-timeline dd { margin:0; color:#2f4553; white-space:pre-wrap; }
.one-timeline p { padding:9px 12px; color:#315e5a; background:#edf6f4; border-radius:6px; }
</style>
