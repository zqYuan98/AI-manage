<template>
  <section class="action-queue lab-panel" :aria-labelledby="headingId">
    <header class="action-queue__header">
      <div>
        <span class="lab-eyebrow">{{ eyebrow }}</span>
        <h2 :id="headingId">{{ title }}</h2>
      </div>
      <div class="action-queue__tools">
        <span class="action-queue__count">{{ items.length }}</span>
        <button v-if="markAllVisible" type="button" @click="$emit('mark-all')">全部已读</button>
      </div>
    </header>

    <div v-if="loading" class="action-queue__loading">
      <span v-for="index in 3" :key="index" class="lab-skeleton" />
    </div>
    <div v-else-if="error" class="action-queue__state">
      <span>{{ errorText }}</span>
      <button type="button" @click="$emit('retry')">重试</button>
    </div>
    <div v-else-if="!items.length" class="action-queue__state action-queue__state--empty">
      <i class="el-icon-circle-check" />
      <span>{{ emptyText }}</span>
    </div>
    <ol v-else class="action-queue__list">
      <li v-for="item in items" :key="`${item.type || 'item'}-${item.id}`">
        <div
          class="action-queue__item lab-focus-ring"
          :class="{ 'is-unread': isUnread(item) }"
          role="button"
          tabindex="0"
          @click="$emit('drill', item)"
          @keydown.enter.prevent="$emit('drill', item)"
          @keydown.space.prevent="$emit('drill', item)"
        >
          <span class="action-queue__marker" :class="levelClass(item)" />
          <div class="action-queue__content">
            <strong>{{ item.title || item.reminderContent || '未命名事项' }}</strong>
            <p v-if="item.reminderContent && item.reminderContent !== item.title">{{ item.reminderContent }}</p>
            <span>{{ meta(item) }}</span>
          </div>
          <span v-if="item.status || item.reminderLevel" class="action-queue__status">
            {{ item.status || item.reminderLevel }}
          </span>
        </div>
        <button
          v-if="allowMarkRead && isUnread(item)"
          class="action-queue__read"
          type="button"
          :disabled="item._marking"
          :aria-label="`将${item.title || '提醒'}标为已读`"
          @click="$emit('mark-read', item)"
        >
          {{ item._marking ? '处理中' : '已读' }}
        </button>
      </li>
    </ol>
  </section>
</template>

<script>
export default {
  name: 'ActionQueue',
  props: {
    title: {
      type: String,
      default: '行动队列'
    },
    eyebrow: {
      type: String,
      default: 'Action queue'
    },
    items: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    error: {
      type: Boolean,
      default: false
    },
    errorText: {
      type: String,
      default: '该数据块暂时不可用'
    },
    emptyText: {
      type: String,
      default: '当前没有待处理事项'
    },
    allowMarkRead: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    headingId() {
      return `queue-${this._uid}`
    },
    markAllVisible() {
      return this.allowMarkRead && this.items.some(this.isUnread)
    }
  },
  methods: {
    isUnread(item) {
      return String(item.readFlag) === '0'
    },
    levelClass(item) {
      const level = String(item.reminderLevel || item.status || '').toUpperCase()
      if (/RED|HIGH|CRITICAL|OVERDUE|BLOCK/.test(level)) return 'is-danger'
      if (/YELLOW|WARN|PENDING/.test(level)) return 'is-warning'
      return 'is-normal'
    },
    meta(item) {
      const date = item.dueDate || item.reminderDate || item.lastUpdated || item.sendTime
      const parts = []
      if (item.period) parts.push(item.period)
      if (date) parts.push(this.formatDate(date))
      return parts.join(' · ') || item.type || item.businessType || '当前周期'
    },
    formatDate(value) {
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    }
  }
}
</script>

<style lang="scss" scoped>
.action-queue { overflow: hidden; }
.action-queue__header { display: flex; align-items: flex-end; justify-content: space-between; min-height: 76px; padding: 16px 18px 14px; border-bottom: 1px solid var(--lab-line); }
.action-queue__header h2 { margin: 5px 0 0; color: var(--lab-indigo-deep); font-size: 16px; }
.action-queue__tools { display: flex; align-items: center; gap: 8px; }
.action-queue__tools button { padding: 3px 0; border: 0; color: var(--lab-teal); background: transparent; font-size: 10px; cursor: pointer; }
.action-queue__count { display: inline-flex; min-width: 25px; height: 25px; align-items: center; justify-content: center; border: 1px solid var(--lab-line); border-radius: 50%; color: var(--lab-indigo); font-size: 11px; font-weight: 700; }
.action-queue__list { max-height: 310px; margin: 0; padding: 0; overflow: auto; list-style: none; }
.action-queue__list li { position: relative; border-bottom: 1px solid #edf0f5; }
.action-queue__item { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; gap: 10px; padding: 14px 16px; cursor: pointer; }
.action-queue__item:hover { background: #f7fbfa; }
.action-queue__item.is-unread { background: #f4faf9; }
.action-queue__marker { width: 6px; height: 6px; margin-top: 6px; border-radius: 50%; background: var(--lab-success); }
.action-queue__marker.is-warning { background: var(--lab-warning); }
.action-queue__marker.is-danger { background: var(--lab-danger); }
.action-queue__content { min-width: 0; }
.action-queue__content strong { display: block; overflow: hidden; color: var(--lab-ink); font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.action-queue__content p { display: -webkit-box; margin: 3px 0; overflow: hidden; color: var(--lab-ink-soft); font-size: 11px; line-height: 16px; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.action-queue__content span { color: #5f6b80; font-size: 10px; }
.action-queue__status { align-self: start; padding: 2px 5px; border: 1px solid var(--lab-line); border-radius: 2px; color: var(--lab-ink-soft); font-size: 9px; text-transform: uppercase; }
.action-queue__read { position: absolute; right: 16px; bottom: 8px; padding: 1px 4px; border: 0; color: var(--lab-teal); background: transparent; font-size: 10px; cursor: pointer; }
.action-queue__read:disabled { color: #9aa3b3; cursor: wait; }
.action-queue__loading { padding: 14px 18px; }
.action-queue__loading span { display: block; height: 48px; margin-bottom: 10px; }
.action-queue__state { display: flex; min-height: 145px; align-items: center; justify-content: center; gap: 7px; color: var(--lab-ink-soft); font-size: 12px; }
.action-queue__state button { border: 0; color: var(--lab-teal); background: transparent; cursor: pointer; }
.action-queue__state--empty i { color: var(--lab-teal); font-size: 18px; }
</style>
