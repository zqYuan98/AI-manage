<template>
  <section class="section-properties">
    <div v-if="!section" class="lab-empty">选择一个章节后编辑安全属性。</div>
    <template v-else>
      <header><div><span class="lab-eyebrow">强类型配置</span><h2>章节属性</h2></div><el-button v-hasPermi="['lab:template:config']" type="text" class="danger" @click="$emit('remove')">删除</el-button></header>
      <el-form label-position="top" size="small">
        <div class="form-grid"><el-form-item label="章节标识"><el-input :value="section.sectionCode" maxlength="64" @input="change('sectionCode',$event)" /></el-form-item><el-form-item label="显示名称"><el-input :value="section.sectionName" maxlength="200" @input="change('sectionName',$event)" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="章节类型"><el-select :value="section.sectionType" @change="$emit('type-change',$event)"><el-option v-for="item in metadata.sectionTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="数据源"><el-select :value="section.dataSource" clearable :disabled="section.sectionType==='MANUAL'" @change="$emit('source-change',$event)"><el-option v-for="item in sources" :key="item" :label="item" :value="item" /></el-select></el-form-item></div>
        <div class="flags"><el-checkbox :value="section.visibleFlag!=='0'" @change="change('visibleFlag',$event?'1':'0')">在报告中显示</el-checkbox><el-checkbox :value="section.sensitiveFlag==='1'" @change="change('sensitiveFlag',$event?'1':'0')">敏感章节</el-checkbox><el-checkbox v-if="section.sectionType==='MANUAL'" :value="render.required===true" @change="renderChange('required',$event)">必填摘要</el-checkbox></div>
        <el-form-item v-if="section.sensitiveFlag==='1'" label="敏感权限快照"><el-input :value="section.sensitivePermission || 'lab:report:sensitive'" disabled><i slot="prefix" class="el-icon-lock" /></el-input></el-form-item>
        <filter-builder :value="query.filters || []" :field-specs="fieldSpecs" @input="queryChange('filters',$event)" />
        <column-designer v-if="section.sectionType==='TABLE'" class="property-block" :value="render.columns || []" :fields="fields" @input="renderChange('columns',$event)" />
        <div class="property-block form-grid">
          <el-form-item v-if="['TEXT','GROUP_TEXT'].includes(section.sectionType)" label="安全模板片段"><el-input :value="render.template" type="textarea" :rows="3" maxlength="500" placeholder="例：${context.period} 目标进度" @input="renderChange('template',$event)" /></el-form-item>
          <el-form-item v-if="section.sectionType==='MANUAL'" label="空值提示"><el-input :value="render.placeholder" maxlength="500" @input="renderChange('placeholder',$event)" /></el-form-item>
          <el-form-item v-if="section.sectionType==='CHART'" label="图表类型"><el-select :value="render.chart || 'bar'" @change="renderChange('chart',$event)"><el-option label="柱状图" value="bar" /></el-select></el-form-item>
          <el-form-item v-if="['GROUP_TEXT'].includes(section.sectionType)" label="分组字段"><el-select :value="render.groupBy" filterable @change="renderChange('groupBy',$event)"><el-option v-for="item in fields" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        </div>
        <div class="variables"><b>可用 FreeMarker 变量</b><button v-for="item in metadata.freemarkerVariables || []" :key="item" type="button" @click="$emit('insert-variable',item)">{{ variableExpression(item) }}</button></div>
      </el-form>
    </template>
  </section>
</template>
<script>
import FilterBuilder from './FilterBuilder'; import ColumnDesigner from './ColumnDesigner'
export default { name: 'SectionProperties', components: { FilterBuilder, ColumnDesigner }, props: { section: { type: Object, default: null }, metadata: { type: Object, default: () => ({}) }, periodType: { type: String, default: '' }, query: { type: Object, default: () => ({}) }, render: { type: Object, default: () => ({}) }}, computed: { sources() { const compatible = (this.metadata.compatibleProviders && this.metadata.compatibleProviders[this.section.sectionType]) || []; return compatible.filter(provider => { const periods = (this.metadata.providerPeriods || {})[provider] || []; return !this.periodType || periods.includes(this.periodType) }) }, fieldSpecs() { return ((this.metadata.providerFields || {})[this.section.dataSource] || []) }, fields() { return this.fieldSpecs.map(item => item.name) } }, methods: { change(key, value) { this.$emit('change', Object.assign({}, this.section, { [key]: value })) }, queryChange(key, value) { this.$emit('query-change', Object.assign({}, this.query, { [key]: value })) }, renderChange(key, value) { this.$emit('render-change', Object.assign({}, this.render, { [key]: value })) }, variableExpression(item) { return '${' + item + '}' } }}
</script>
<style lang="scss" scoped>
.section-properties{height:100%;min-height:640px;overflow:auto;padding:18px;background:#fff}.section-properties>header{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:14px;padding-bottom:13px;border-bottom:1px solid var(--lab-line)}h2{margin:5px 0 0;color:var(--lab-indigo-deep);font-size:17px}.danger{color:var(--lab-danger)}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px}.section-properties ::v-deep .el-form-item{margin-bottom:12px}.section-properties ::v-deep .el-form-item__label{padding:0 0 4px;color:#68748a;font-size:9px;line-height:18px}.section-properties ::v-deep .el-select{width:100%}.flags{display:flex;flex-wrap:wrap;gap:12px;margin:0 0 13px;padding:9px;border:1px solid var(--lab-line);background:#fafbfc}.flags ::v-deep .el-checkbox__label{font-size:9px}.property-block{margin-top:10px}.variables{margin-top:11px;padding:11px;border-left:3px solid var(--lab-teal);background:#edf6f5}.variables b{display:block;margin-bottom:6px;color:var(--lab-indigo-deep);font-size:9px}.variables button{margin:2px 4px 2px 0;padding:3px 5px;border:1px solid #bfd9d5;color:#176e67;background:#fff;font:8px Consolas;cursor:pointer}
</style>
