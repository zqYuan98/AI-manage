<template>
  <main class="member-page">
    <header class="page-hero"><div><span>Team directory</span><h1>成员与能力台账</h1><p>以职责、技能、资产主备和近期工作还原团队能力，不展示排名。</p></div><el-button v-hasPermi="['lab:member:add']" type="primary" icon="el-icon-plus" @click="openCreate">新增成员档案</el-button></header>
    <section class="member-summary">
      <article><strong>{{ total }}</strong><span>授权范围成员</span></article><article><strong>{{ activeCount }}</strong><span>当前在岗</span></article><article><strong>{{ bizLines }}</strong><span>业务方向</span></article><article><strong>{{ leadCount }}</strong><span>方向负责人</span></article>
    </section>
    <section class="member-ledger">
      <div class="member-ledger__filters">
        <el-input v-model.trim="query.nickName" clearable prefix-icon="el-icon-search" placeholder="姓名或账号" @keyup.enter.native="search" />
        <el-input v-model.trim="query.bizLine" clearable placeholder="业务方向" @keyup.enter.native="search" />
        <el-select v-model="query.memberStatus" clearable placeholder="全部状态" @change="search"><el-option label="在岗" value="ACTIVE" /><el-option label="已停用" value="INACTIVE" /></el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="成员" min-width="210"><template slot-scope="scope"><div class="member-cell"><span>{{ initials(scope.row) }}</span><div><strong>{{ scope.row.nickName || scope.row.userName || `成员 #${scope.row.id}` }}</strong><small>{{ scope.row.memberNo || '受限档案' }}</small></div></div></template></el-table-column>
        <el-table-column prop="position" label="岗位" min-width="140" /><el-table-column prop="bizLine" label="业务方向" min-width="130" /><el-table-column label="角色" width="120"><template slot-scope="scope">{{ roleLabel(scope.row.roleType) }}</template></el-table-column><el-table-column prop="leaderName" label="负责人" width="130" />
        <el-table-column label="状态" width="95"><template slot-scope="scope"><el-tag :type="statusType(scope.row.memberStatus)" size="mini" effect="plain">{{ statusLabel(scope.row.memberStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="210" fixed="right"><template slot-scope="scope"><el-button type="text" @click.stop="openDetail(scope.row)">查看档案</el-button><el-button v-if="scope.row.version != null" v-hasPermi="['lab:member:edit']" type="text" @click.stop="openEdit(scope.row)">编辑</el-button><el-button v-if="scope.row.memberStatus === 'ACTIVE'" v-hasPermi="['lab:member:remove']" type="text" class="danger" @click.stop="changeStatus(scope.row, false)">停用</el-button><el-button v-else-if="scope.row.memberStatus === 'INACTIVE'" v-hasPermi="['lab:member:edit']" type="text" @click.stop="changeStatus(scope.row, true)">启用</el-button></template></el-table-column>
        <template slot="empty"><el-empty description="当前授权范围暂无成员" /></template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </section>

    <el-dialog :title="form.id ? '编辑成员档案' : '新增成员档案'" :visible.sync="formVisible" width="720px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="关联系统用户" prop="userId"><el-select v-model="form.userId" filterable :disabled="Boolean(form.id)" placeholder="选择未建档用户"><el-option v-for="user in availableUsers" :key="user.userId" :label="`${user.nickName || user.userName} · ${user.userName}`" :value="user.userId" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="成员编号" prop="memberNo"><el-input v-model.trim="form.memberNo" maxlength="32" /></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="岗位" prop="position"><el-input v-model.trim="form.position" /></el-form-item></el-col><el-col :span="8"><el-form-item label="业务方向" prop="bizLine"><el-input v-model.trim="form.bizLine" /></el-form-item></el-col><el-col :span="8"><el-form-item label="角色" prop="roleType"><el-select v-model="form.roleType"><el-option label="成员" value="MEMBER" /><el-option label="方向负责人" value="LINE_LEAD" /><el-option label="实验室负责人" value="MANAGER" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="负责人"><el-select v-model="form.leaderId" clearable filterable><el-option v-for="item in activeMembers" :key="item.id" :disabled="Number(item.id)===Number(form.id)" :label="item.nickName || item.userName" :value="item.id" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="加入日期"><el-date-picker v-model="form.joinDate" type="date" value-format="yyyy-MM-dd" /></el-form-item></el-col></el-row>
        <el-form-item label="主要职责"><el-input v-model.trim="form.primaryResponsibilities" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><el-form-item label="备份职责"><el-input v-model.trim="form.backupResponsibilities" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></span>
    </el-dialog>
    <member-detail-drawer :visible="detailVisible" :member-id="activeId" @close="detailVisible=false" />
  </main>
</template>

<script>
import { addMember, deactivateMember, listAvailableUsers, listMembers, reactivateMember, updateMember } from '@/api/lab/member'
import MemberDetailDrawer from './components/MemberDetailDrawer'

export default {
  name: 'LabMember', components: { MemberDetailDrawer },
  data() { return { query: { pageNum: 1, pageSize: 20, nickName: '', bizLine: '', memberStatus: '' }, rows: [], directory: [], total: 0, loading: false, saving: false, requestToken: 0, formVisible: false, detailVisible: false, activeId: null, form: {}, availableUsers: [], rules: { userId: [{ required: true, message: '请选择系统用户', trigger: 'change' }], memberNo: [{ required: true, message: '请输入成员编号', trigger: 'blur' }], position: [{ required: true, message: '请输入岗位', trigger: 'blur' }], bizLine: [{ required: true, message: '请输入业务方向', trigger: 'blur' }], roleType: [{ required: true, message: '请选择角色', trigger: 'change' }] }} },
  computed: { activeMembers() { return this.directory.filter(item => item.memberStatus === 'ACTIVE') }, activeCount() { return this.directory.filter(item => item.memberStatus === 'ACTIVE').length }, bizLines() { return new Set(this.directory.map(item => item.bizLine).filter(Boolean)).size }, leadCount() { return this.directory.filter(item => String(item.roleType).toUpperCase() === 'LINE_LEAD').length } },
  created() { this.load() },
  methods: {
    load() {
      const token = ++this.requestToken
      this.loading = true
      return Promise.all([listMembers(this.query), listMembers({ pageNum: 1, pageSize: 500 })]).then(([page, directory]) => {
        if (token !== this.requestToken) return
        this.rows = page.rows || []; this.total = Number(page.total || 0); this.directory = directory.rows || []
      }).finally(() => { if (token === this.requestToken) this.loading = false })
    },
    search() { this.query.pageNum = 1; this.load() }, reset() { this.query = { pageNum: 1, pageSize: 20, nickName: '', bizLine: '', memberStatus: '' }; this.load() },
    emptyForm() { return { id: null, userId: null, memberNo: '', position: '', bizLine: '', roleType: 'MEMBER', leaderId: null, joinDate: '', primaryResponsibilities: '', backupResponsibilities: '', version: null } },
    openCreate() { this.form = this.emptyForm(); listAvailableUsers().then(res => { this.availableUsers = res.data || []; this.formVisible = true }) },
    openEdit(row) { this.availableUsers = [row]; this.form = { ...row }; this.formVisible = true },
    openDetail(row) { this.activeId = row.id; this.detailVisible = true },
    save() { this.$refs.form.validate(valid => { if (!valid) return; this.saving = true; (this.form.id ? updateMember(this.form) : addMember(this.form)).then(() => { this.$message.success('成员档案已保存'); this.formVisible = false; this.load() }).finally(() => { this.saving = false }) }) },
    changeStatus(row, active) { this.$confirm(`确认${active ? '启用' : '停用'} ${row.nickName || row.userName || row.memberNo}？`, '成员状态').then(() => (active ? reactivateMember : deactivateMember)(row.id, row.version)).then(() => { this.$message.success('成员状态已更新'); this.load() }).catch(() => {}) },
    initials(row) { return String(row.nickName || row.userName || row.id || 'AI').slice(0, 2).toUpperCase() }, roleLabel(role) { return ({ MANAGER: '实验室负责人', LINE_LEAD: '方向负责人', MEMBER: '成员' })[String(role || '').toUpperCase()] || role || '—' }, statusLabel(status) { return ({ ACTIVE: '在岗', INACTIVE: '停用' })[status] || '受限' }, statusType(status) { return status === 'ACTIVE' ? 'success' : status === 'INACTIVE' ? 'info' : 'warning' }
  }
}
</script>

<style scoped lang="scss">
.member-page { min-height:100%; padding:28px; color:#183442; background:#f2f5f4; }.page-hero { display:flex; justify-content:space-between; align-items:flex-end; padding:26px 30px; color:#fff; border-radius:16px; background:linear-gradient(128deg,#102f3b,#166d64); box-shadow:0 14px 28px rgba(16,47,59,.16); }.page-hero span { font-size:11px; letter-spacing:.18em; text-transform:uppercase; color:#a8ded8; }.page-hero h1 { margin:7px 0; font:600 30px/1.2 Georgia,'Noto Serif SC',serif; }.page-hero p { margin:0; color:#d3e5e2; }
.member-summary { display:grid; grid-template-columns:repeat(4,1fr); gap:12px; margin:16px 0; }.member-summary article { padding:18px 20px; border:1px solid #dce5e3; border-radius:12px; background:#fff; }.member-summary strong { display:block; font:600 25px Georgia,serif; color:#0d6c66; }.member-summary span { color:#667780; font-size:12px; }
.member-ledger { padding:20px; border:1px solid #dce5e3; border-radius:14px; background:#fff; }.member-ledger__filters { display:flex; gap:10px; margin-bottom:18px; }.member-ledger__filters .el-input,.member-ledger__filters .el-select { width:200px; }.member-cell { display:flex; align-items:center; gap:11px; }.member-cell>span { display:grid; place-items:center; width:36px; height:36px; border-radius:10px; color:#0c625d; background:#e4f2ef; font-weight:700; }.member-cell strong,.member-cell small { display:block; }.member-cell small { margin-top:2px; color:#819097; font-size:11px; }.danger { color:#a6413b; }
@media(max-width:900px){.member-page{padding:16px}.member-summary{grid-template-columns:1fr 1fr}.page-hero{align-items:flex-start;gap:20px}.member-ledger__filters{flex-wrap:wrap}}
</style>
