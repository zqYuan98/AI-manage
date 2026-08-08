<template>
  <section class="section-tree" aria-labelledby="section-tree-title">
    <header><div><span class="lab-eyebrow">Document anatomy</span><h2 id="section-tree-title">章节结构</h2></div><el-button v-hasPermi="['lab:template:config']" type="text" icon="el-icon-plus" @click="$emit('add')">新增</el-button></header>
    <el-tree :data="treeSections" node-key="_key" draggable :allow-drop="allowDrop" :expand-on-click-node="false" @node-drop="drop" @node-click="select">
      <div slot-scope="{ data }" role="button" tabindex="0" :class="['section-node',{active:data.sectionCode===selectedCode}]" @keydown.enter.prevent="select(data)" @keydown.space.prevent="select(data)">
        <i class="el-icon-rank drag" role="button" tabindex="0" aria-label="拖动排序；使用上下方向键移动" @click.stop @keydown.up.stop.prevent="move(data,-1)" @keydown.down.stop.prevent="move(data,1)" />
        <span><b>{{ data.sectionName || '未命名章节' }}</b><small>{{ String(treeSections.indexOf(data)+1).padStart(2,'0') }} · {{ data.sectionType }} · {{ data.dataSource || 'MANUAL' }}</small></span>
        <em v-if="data.sensitiveFlag==='1'" class="el-icon-lock" title="敏感章节" />
        <em v-else-if="data.visibleFlag==='0'" class="el-icon-view" title="已隐藏" />
      </div>
    </el-tree>
    <div v-if="!sections.length" class="section-tree__empty">暂无章节，从一个有明确用途的章节开始。</div>
  </section>
</template>
<script>
export default { name: 'SectionTree', props: { sections: { type: Array, default: () => [] }, selectedCode: { type: String, default: '' }}, data() { return { treeSections: [] } }, watch: { sections: { immediate: true, deep: true, handler(value) { this.treeSections = (value || []).map(item => Object.assign({}, item)) } }}, methods: { select(section) { this.$emit('select', section.sectionCode) }, allowDrop(dragging, dropping, type) { return type !== 'inner' }, drop() { this.$nextTick(() => this.reorder(this.treeSections)) }, reorder(items) { this.$emit('reorder', items.map((item, index) => Object.assign({}, item, { sortNo: (index + 1) * 10 }))) }, move(section, offset) { const index = this.treeSections.indexOf(section); const target = index + offset; if (index < 0 || target < 0 || target >= this.treeSections.length) return; const items = this.treeSections.slice(); const value = items[index]; items.splice(index, 1); items.splice(target, 0, value); this.treeSections = items; this.reorder(items) } }}
</script>
<style lang="scss" scoped>
.section-tree{height:100%;min-height:640px;padding:18px 0;background:#fff}.section-tree header{display:flex;align-items:flex-end;justify-content:space-between;padding:0 16px 14px;border-bottom:1px solid var(--lab-line)}h2{margin:5px 0 0;color:var(--lab-indigo-deep);font-size:17px}.section-tree ::v-deep .el-tree-node__content{height:auto;padding:0!important}.section-tree ::v-deep .el-tree-node__expand-icon{display:none}.section-node{display:grid;grid-template-columns:20px 1fr 18px;gap:7px;width:100%;padding:13px 14px;border:0;border-bottom:1px solid var(--lab-line);color:var(--lab-ink);background:#fff;text-align:left;cursor:pointer}.section-node:hover,.section-node.active{background:#f0f4f7}.section-node.active{box-shadow:inset 3px 0 var(--lab-teal)}.section-node .drag{align-self:center;color:#9ba5b7;cursor:grab}.section-node span b,.section-node span small{display:block}.section-node span b{font-size:11px}.section-node span small{margin-top:4px;color:var(--lab-ink-soft);font:8px 'Arial Narrow',Arial;letter-spacing:.04em}.section-node em{align-self:center;color:var(--lab-danger);font-style:normal}.section-tree__empty{padding:70px 24px;color:var(--lab-ink-soft);font-size:11px;line-height:1.8;text-align:center}
</style>
