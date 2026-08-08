<template>
  <section class="column-designer">
    <header><div><b>列投影</b><small>稳定字段、标题、对齐与宽度</small></div><el-button type="text" icon="el-icon-plus" :disabled="columns.length>=10" @click="add">列</el-button></header>
    <draggable :value="columns" handle=".column-drag" @input="$emit('input',$event)">
      <div v-for="(column,index) in columns" :key="index" class="column-row">
        <i class="el-icon-rank column-drag" role="button" tabindex="0" aria-label="拖动排序；使用上下方向键移动" @keydown.up.stop.prevent="move(index,-1)" @keydown.down.stop.prevent="move(index,1)" />
        <el-select :value="field(column)" filterable placeholder="字段" @change="update(index,'field',$event)"><el-option v-for="item in fields" :key="item" :label="item" :value="item" /></el-select>
        <el-input :value="object(column).label" placeholder="显示标题" @input="update(index,'label',$event)" />
        <el-select :value="object(column).align || 'LEFT'" @change="update(index,'align',$event)"><el-option label="左" value="LEFT" /><el-option label="中" value="CENTER" /><el-option label="右" value="RIGHT" /></el-select>
        <el-input :value="object(column).width" placeholder="120px" @input="update(index,'width',$event)" />
        <el-button type="text" icon="el-icon-delete" aria-label="删除列" @click="remove(index)" />
      </div>
    </draggable>
    <p v-if="!columns.length">未配置列时，渲染器使用安全的默认投影。</p>
  </section>
</template>
<script>
import draggable from 'vuedraggable'
export default { name: 'ColumnDesigner', components: { draggable }, props: { value: { type: Array, default: () => [] }, fields: { type: Array, default: () => [] }}, computed: { columns() { return this.value || [] } }, methods: { field(item) { return typeof item === 'string' ? item : item.field }, object(item) { return typeof item === 'string' ? { field: item, label: '', align: 'LEFT', width: '' } : item }, add() { this.$emit('input', this.columns.concat({ field: this.fields[0] || '', label: '', align: 'LEFT', width: '' })) }, remove(index) { this.$emit('input', this.columns.filter((item, i) => i !== index)) }, move(index, offset) { const target = index + offset; if (target < 0 || target >= this.columns.length) return; const items = this.columns.slice(); const value = items[index]; items.splice(index, 1); items.splice(target, 0, value); this.$emit('input', items) }, update(index, key, value) { this.$emit('input', this.columns.map((item, i) => { if (i !== index) return item; const result = Object.assign({}, this.object(item), { [key]: value }); Object.keys(result).forEach(name => { if (result[name] === '') delete result[name] }); return result })) } }}
</script>
<style lang="scss" scoped>
.column-designer{padding:13px;border:1px solid var(--lab-line);background:#fafbfc}.column-designer header{display:flex;align-items:center;justify-content:space-between}.column-designer header b,.column-designer header small{display:block}.column-designer header b{color:var(--lab-indigo-deep);font-size:11px}.column-designer header small{margin-top:3px;color:var(--lab-ink-soft);font-size:8px}.column-row{display:grid;grid-template-columns:18px 1fr 1fr 65px 75px 26px;gap:5px;align-items:center;margin-top:7px}.column-drag{color:#98a3b5;cursor:grab}.column-row ::v-deep .el-input__inner{height:30px;padding:0 7px;font-size:8px}.column-designer p{margin:10px 0 0;color:var(--lab-ink-soft);font-size:9px}
</style>
