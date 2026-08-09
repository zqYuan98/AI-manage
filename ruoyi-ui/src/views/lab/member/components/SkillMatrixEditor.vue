<template>
  <section class="skill-matrix">
    <header>
      <div><span>能力矩阵</span><h3>技能矩阵</h3></div>
      <el-button v-if="editable" v-hasPermi="['lab:skill:list']" size="small" type="primary" :loading="saving" @click="save">批量保存</el-button>
    </header>
    <el-table :data="rows" size="small" empty-text="暂无技能记录">
      <el-table-column label="技能" min-width="170">
        <template slot-scope="scope">
          <el-select v-if="scope.row.isNew" v-model="scope.row.skillId" filterable placeholder="选择技能" @change="skillChanged(scope.row)">
            <el-option v-for="item in availableSkills(scope.row)" :key="item.id" :label="`${item.skillCategory || '通用'} · ${item.skillName}`" :value="item.id" />
          </el-select>
          <div v-else><strong>{{ scope.row.skillName }}</strong><small>{{ scope.row.skillCategory || '通用' }}</small></div>
        </template>
      </el-table-column>
      <el-table-column label="熟练度" width="220">
        <template slot-scope="scope"><el-rate v-model="scope.row.level" :max="5" :disabled="!editable" show-score /></template>
      </el-table-column>
      <el-table-column label="最近验证" width="145">
        <template slot-scope="scope"><el-date-picker v-model="scope.row.lastVerifiedDate" :disabled="!editable" type="date" value-format="yyyy-MM-dd" placeholder="验证日期" /></template>
      </el-table-column>
      <el-table-column label="证据" min-width="190">
        <template slot-scope="scope"><el-input v-model.trim="scope.row.evidenceUrl" :disabled="!editable" placeholder="https://…" /></template>
      </el-table-column>
      <el-table-column v-if="editable" width="58" align="center"><template slot-scope="scope"><el-button type="text" class="remove" aria-label="移除技能" @click="remove(scope.$index)"><i class="el-icon-delete" /></el-button></template></el-table-column>
    </el-table>
    <el-button v-if="editable" class="add-skill" size="small" plain icon="el-icon-plus" :disabled="!canAdd" @click="add">添加技能</el-button>
  </section>
</template>

<script>
import { listSkills, saveSkillMatrix } from '@/api/lab/member'

export default {
  name: 'SkillMatrixEditor',
  props: {
    memberId: { type: [Number, String], required: true },
    value: { type: Array, default: () => [] },
    editable: { type: Boolean, default: false }
  },
  data() { return { rows: [], catalog: [], saving: false } },
  computed: { canAdd() { return this.catalog.some(item => !this.rows.some(row => Number(row.skillId) === Number(item.id))) } },
  watch: { value: { immediate: true, deep: true, handler(value) { this.rows = (value || []).map(item => ({ ...item, isNew: false })) } }},
  created() { listSkills({ pageNum: 1, pageSize: 500, status: 'ACTIVE' }).then(res => { this.catalog = res.rows || res.data || [] }) },
  methods: {
    availableSkills(row) { return this.catalog.filter(item => Number(item.id) === Number(row.skillId) || !this.rows.some(existing => Number(existing.skillId) === Number(item.id))) },
    add() { this.rows.push({ skillId: null, level: 1, lastVerifiedDate: '', evidenceUrl: '', isNew: true }) },
    remove(index) { this.rows.splice(index, 1) },
    skillChanged(row) {
      const skill = this.catalog.find(item => Number(item.id) === Number(row.skillId))
      if (skill) Object.assign(row, { skillName: skill.skillName, skillCategory: skill.skillCategory, skillCode: skill.skillCode })
    },
    save() {
      if (this.rows.some(row => !row.skillId || !row.level)) return this.$message.warning('请补齐技能与熟练度')
      this.saving = true
      const payload = this.rows.map(({ isNew, skillName, skillCategory, skillCode, ...row }) => ({ ...row, memberId: Number(this.memberId) }))
      saveSkillMatrix(this.memberId, payload).then(() => {
        this.$message.success('技能矩阵已保存')
        this.$emit('saved')
      }).finally(() => { this.saving = false })
    }
  }
}
</script>

<style scoped lang="scss">
.skill-matrix { padding: 2px; }
.skill-matrix header { display:flex; align-items:center; justify-content:space-between; margin-bottom:16px; }
.skill-matrix header span { color:#346c68; font-size:11px; letter-spacing:.14em; text-transform:uppercase; }
.skill-matrix h3 { margin:4px 0 0; color:#142a38; }
.skill-matrix small { display:block; color:#758394; margin-top:3px; }
.skill-matrix ::v-deep .el-date-editor { width:130px; }
.add-skill { margin-top:14px; }
.remove { color:#a6413b; }
</style>
