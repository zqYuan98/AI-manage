<template>
  <section class="goal-tree lab-panel" aria-labelledby="goal-tree-title">
    <header class="goal-tree__header">
      <div>
        <span class="lab-eyebrow">Goal map</span>
        <h2 id="goal-tree-title">年度目标树</h2>
      </div>
      <span>{{ count }} 项</span>
    </header>
    <div v-if="loading" class="goal-tree__loading">
      <span v-for="index in 6" :key="index" class="lab-skeleton" />
    </div>
    <div v-else-if="!nodes.length" class="lab-empty">当前筛选下暂无目标</div>
    <el-tree
      v-else
      ref="tree"
      class="goal-tree__body"
      node-key="id"
      :data="nodes"
      :props="treeProps"
      :expand-on-click-node="false"
      :highlight-current="true"
      :default-expand-all="true"
      @node-click="selectNode"
    >
      <div slot-scope="{ data }" class="goal-tree__node">
        <span class="goal-tree__level" :class="`is-${String(data.goalLevel).toLowerCase()}`">
          {{ levelLabel(data.goalLevel) }}
        </span>
        <span class="goal-tree__name">
          <strong>{{ data.title }}</strong>
          <small v-if="data.goalLevel === 'YEAR'">{{ data.goalNo }} · 季度权重 {{ childWeight(data) }}%</small>
          <small v-else>{{ data.goalNo }} · 自身权重 {{ number(data.weight) }}%</small>
        </span>
        <span class="goal-tree__progress">{{ number(data.progressRate) }}%</span>
        <button
          v-if="data.goalLevel === 'YEAR' && data.status === 'DRAFT' && canAddChild"
          v-hasPermi="['lab:goal:add']"
          type="button"
          title="新增季度里程碑"
          aria-label="新增季度里程碑"
          @click.stop="$emit('add-child', data)"
        >
          <i class="el-icon-plus" />
        </button>
      </div>
    </el-tree>
  </section>
</template>

<script>
export default {
  name: 'GoalTree',
  props: {
    nodes: {
      type: Array,
      default: () => []
    },
    selectedId: {
      type: [Number, String],
      default: null
    },
    loading: {
      type: Boolean,
      default: false
    },
    canAddChild: { type: Boolean, default: false }
  },
  data() {
    return { treeProps: { children: 'children', label: 'title' }}
  },
  computed: {
    count() {
      const walk = nodes => nodes.reduce((sum, node) => sum + 1 + walk(node.children || []), 0)
      return walk(this.nodes)
    }
  },
  watch: {
    selectedId: {
      immediate: true,
      handler(value) {
        this.$nextTick(() => {
          if (this.$refs.tree && value != null) this.$refs.tree.setCurrentKey(value)
        })
      }
    }
  },
  methods: {
    selectNode(data) {
      this.$emit('select', data)
    },
    levelLabel(level) {
      return level === 'YEAR' ? '年' : '季'
    },
    number(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Number(number.toFixed(2)) : 0
    },
    childWeight(goal) {
      return this.number((goal.children || []).reduce((sum, child) => sum + Number(child.weight || 0), 0))
    }
  }
}
</script>

<style lang="scss" scoped>
.goal-tree { height: calc(100vh - 205px); min-height: 540px; overflow: hidden; }
.goal-tree__header { display: flex; align-items: flex-end; justify-content: space-between; padding: 20px 18px 15px; border-bottom: 1px solid var(--lab-line); }
.goal-tree__header h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 17px; }
.goal-tree__header > span { color: var(--lab-ink-soft); font-size: 11px; }
.goal-tree__body { height: calc(100% - 77px); padding: 10px 8px 18px; overflow: auto; background: transparent; }
.goal-tree__node { display: grid; width: 100%; grid-template-columns: 30px minmax(0, 1fr) auto 25px; align-items: center; gap: 7px; padding-right: 6px; }
.goal-tree__level { display: inline-flex; width: 26px; height: 22px; align-items: center; justify-content: center; border-radius: 2px; color: #fff; background: var(--lab-indigo); font-size: 10px; font-weight: 700; }
.goal-tree__level.is-quarter { color: #075f5a; background: #d9f0ed; }
.goal-tree__name { min-width: 0; }
.goal-tree__name strong, .goal-tree__name small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.goal-tree__name strong { color: var(--lab-ink); font-size: 12px; font-weight: 600; }
.goal-tree__name small { margin-top: 2px; color: #5f6b80; font-size: 9px; }
.goal-tree__progress { color: var(--lab-teal); font-family: 'Arial Narrow', Arial, sans-serif; font-size: 11px; font-weight: 700; }
.goal-tree__node button { width: 23px; height: 23px; padding: 0; border: 1px solid var(--lab-line); color: var(--lab-teal); background: #fff; cursor: pointer; }
.goal-tree__loading { padding: 18px; }
.goal-tree__loading span { display: block; height: 45px; margin-bottom: 10px; }
::v-deep .el-tree-node__content { height: 55px; margin-bottom: 3px; border-radius: 2px; }
::v-deep .el-tree-node__content:hover { background: #f1f7f6; }
::v-deep .el-tree-node.is-current > .el-tree-node__content { background: #e8f4f2; box-shadow: inset 3px 0 var(--lab-teal); }
</style>
