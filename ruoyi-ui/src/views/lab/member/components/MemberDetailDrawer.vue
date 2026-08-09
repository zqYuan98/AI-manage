<template>
  <el-drawer class="member-drawer" :visible="visible" size="78%" :with-header="false" append-to-body @close="$emit('close')">
    <div v-loading="loading" class="member-drawer__body">
      <header v-if="member.id" class="member-hero">
        <div class="member-hero__avatar">{{ initials(member) }}</div>
        <div class="member-hero__identity">
          <span>{{ member.bizLine ? bizLineLabel(member.bizLine) : '实验室成员' }} · {{ member.memberNo || '受限档案' }}</span>
          <h2>{{ member.nickName || member.userName || `成员 #${member.id}` }}</h2>
          <p>{{ member.position || '岗位未填写' }} · {{ roleLabel(member.roleType) }} · 负责人 {{ member.leaderName || '未配置' }}</p>
        </div>
        <div class="member-hero__actions"><el-tag :type="statusType(member.memberStatus)" effect="plain">{{ statusLabel(member.memberStatus) }}</el-tag><el-button icon="el-icon-close" circle aria-label="关闭" @click="$emit('close')" /></div>
      </header>

      <el-tabs v-if="member.id" v-model="tab" class="member-tabs">
        <el-tab-pane label="成员档案" name="profile">
          <div class="profile-grid">
            <section class="profile-card"><span>主要职责</span><h3>主要职责</h3><p>{{ member.primaryResponsibilities || '暂未填写' }}</p></section>
            <section class="profile-card"><span>备份职责</span><h3>备份职责</h3><p>{{ member.backupResponsibilities || '暂未填写' }}</p></section>
          </div>
          <section class="detail-section"><header><div><span>当前工作</span><h3>当前任务</h3></div></header><el-table :data="detail.recentTasks || []" size="small" empty-text="暂无当前任务"><el-table-column prop="title" label="任务" min-width="210" /><el-table-column prop="period" label="周期" width="95" /><el-table-column label="状态" width="130"><template slot-scope="scope">{{ taskStatusLabel(scope.row.workflowStatus) }}</template></el-table-column><el-table-column prop="planDate" label="计划日期" width="115" /></el-table></section>
          <section class="detail-section"><header><div><span>主备关系</span><h3>资产主备关系</h3></div></header><el-table :data="detail.assets || []" size="small" empty-text="暂无资产关系"><el-table-column prop="assetName" label="资产" min-width="200" /><el-table-column prop="assetStage" label="阶段" width="110" /><el-table-column label="角色" width="100"><template slot-scope="scope">{{ Number(scope.row.primaryOwnerId) === Number(member.id) ? '主负责人' : '备份人' }}</template></el-table-column><el-table-column label="风险" width="110"><template slot-scope="scope"><span v-if="scope.row.singlePointRisk" class="risk"><i class="el-icon-warning" /> 单点风险</span><span v-else>—</span></template></el-table-column></el-table></section>
        </el-tab-pane>
        <el-tab-pane label="技能矩阵" name="skills"><skill-matrix-editor :key="skillKey" :member-id="member.id" :value="detail.skillMatrix || []" :editable="member.memberStatus === 'ACTIVE' && canEditSkills" @saved="reload" /></el-tab-pane>
        <el-tab-pane label="一对一沟通" name="one2one"><one-to-one-timeline :records="detail.oneToOnes || []" :member="member" @saved="reload" /></el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script>
import { bizLineLabel, statusLabel } from '@/utils/lab-status'
import { getMember } from '@/api/lab/member'
import SkillMatrixEditor from './SkillMatrixEditor'
import OneToOneTimeline from './OneToOneTimeline'

export default {
  name: 'MemberDetailDrawer',
  components: { SkillMatrixEditor, OneToOneTimeline },
  props: { visible: Boolean, memberId: { type: [Number, String], default: null }},
  data() { return { loading: false, requestToken: 0, detail: { member: {}, skillMatrix: [], assets: [], recentTasks: [], oneToOnes: [] }, tab: 'profile', skillKey: 0 } },
  computed: {
    member() { return this.detail.member || {} },
    canEditSkills() {
      const perms = this.$store.getters.permissions || []
      const roles = (this.$store.getters.roles || []).map(item => String(item).toLowerCase())
      const permitted = perms.includes('*:*:*') || perms.includes('lab:skill:config') || perms.includes('lab:skill:list')
      const manager = roles.includes('admin') || roles.includes('lab_manager') || roles.includes('manager')
      return permitted && (manager || Number(this.member.userId) === Number(this.$store.state.user.id))
    }
  },
  watch: {
    visible(value) { if (value && this.memberId) { this.tab = 'profile'; this.reload() } },
    memberId(value, previous) { if (this.visible && value && Number(value) !== Number(previous)) { this.tab = 'profile'; this.reload() } }
  },
  methods: {
    bizLineLabel,
    taskStatusLabel(status) { return statusLabel('TASK_WORKFLOW', status) },
    reload() {
      const token = ++this.requestToken
      const memberId = this.memberId
      this.loading = true
      return getMember(memberId).then(res => { if (token === this.requestToken && Number(memberId) === Number(this.memberId)) { this.detail = res.data || {}; this.skillKey += 1 } }).finally(() => { if (token === this.requestToken) this.loading = false })
    },
    initials(member) { return String(member.nickName || member.userName || member.id || 'AI').slice(0, 2).toUpperCase() },
    roleLabel(role) { return ({ MANAGER: '实验室负责人', LINE_LEAD: '方向负责人', MEMBER: '成员' })[String(role || '').toUpperCase()] || role || '成员' },
    statusLabel(status) { return ({ ACTIVE: '在岗', INACTIVE: '已停用' })[status] || '受限档案' },
    statusType(status) { return status === 'ACTIVE' ? 'success' : status === 'INACTIVE' ? 'info' : 'warning' }
  }
}
</script>

<style scoped lang="scss">
.member-drawer__body { min-height:100%; background:#f4f7f6; }
.member-hero { display:flex; align-items:center; gap:18px; padding:28px 34px; color:#fff; background:linear-gradient(128deg,#102f3b,#17665f); }
.member-hero__avatar { display:grid; place-items:center; width:64px; height:64px; border:1px solid rgba(255,255,255,.5); border-radius:18px; font:600 22px/1 Georgia,serif; background:rgba(255,255,255,.1); }
.member-hero__identity { flex:1; }.member-hero__identity span { font-size:11px; letter-spacing:.14em; text-transform:uppercase; opacity:.75; }.member-hero h2 { margin:6px 0; font:600 28px/1.2 Georgia,'Noto Serif SC',serif; }.member-hero p { margin:0; opacity:.82; }
.member-hero__actions { display:flex; gap:12px; align-items:center; }.member-tabs { padding:8px 34px 34px; }
.member-tabs ::v-deep .el-tabs__header { margin-bottom:24px; }.member-tabs ::v-deep .el-tabs__item { height:52px; line-height:52px; }.member-tabs ::v-deep .el-tabs__active-bar { background:#0a7b74; }.member-tabs ::v-deep .el-tabs__item.is-active { color:#0a6c66; }
.profile-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }.profile-card,.detail-section { padding:20px; border:1px solid #dde6e4; border-radius:12px; background:#fff; box-shadow:0 8px 22px rgba(24,52,63,.04); }
.profile-card span,.detail-section header span { color:#39726d; font-size:10px; letter-spacing:.14em; text-transform:uppercase; }.profile-card h3,.detail-section h3 { margin:5px 0 12px; color:#183442; }.profile-card p { margin:0; min-height:48px; color:#51636d; line-height:1.75; white-space:pre-wrap; }
.detail-section { margin-top:16px; }.detail-section header { margin-bottom:12px; }.risk { color:#a34438; font-weight:600; }
@media(max-width:900px){.profile-grid{grid-template-columns:1fr}.member-tabs{padding:8px 16px 24px}.member-hero{padding:22px 18px}.member-hero__identity h2{font-size:23px}}
</style>
