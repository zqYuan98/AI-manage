<template>
  <section class="markdown-preview" aria-labelledby="preview-title">
    <header><div><span class="lab-eyebrow">实时预览</span><h2 id="preview-title">内容预览</h2></div><span :class="{dirty}">{{ dirty ? '未保存草稿' : '服务端快照' }}</span></header>
    <div v-if="loading" class="markdown-preview__loading lab-skeleton" />
    <pre v-else-if="markdown">{{ markdown }}</pre>
    <div v-else class="lab-empty">配置章节后，将生成不执行任意代码的结构预览。</div>
    <footer><i class="el-icon-lock" /> 变量、字段和操作符全部来自服务端白名单</footer>
  </section>
</template>
<script>export default { name: 'MarkdownPreview', props: { markdown: { type: String, default: '' }, loading: Boolean, dirty: Boolean }}</script>
<style lang="scss" scoped>
.markdown-preview{height:100%;min-height:640px;padding:18px;background:#111a31;color:#dce3ef}.markdown-preview header{display:flex;align-items:flex-end;justify-content:space-between;padding-bottom:14px;border-bottom:1px solid rgba(255,255,255,.12)}h2{margin:5px 0 0;color:#fff;font-size:17px}.markdown-preview header>span{padding:4px 6px;color:#87c6bd;background:rgba(114,206,197,.1);font-size:8px}.markdown-preview header>span.dirty{color:#f0c77a;background:rgba(240,199,122,.1)}pre{max-height:560px;margin:15px 0 0;overflow:auto;color:#dce3ef;font:11px/1.8 Consolas,monospace;white-space:pre-wrap}.markdown-preview__loading{height:430px;margin-top:15px;opacity:.25}.markdown-preview ::v-deep .lab-empty{color:#9da9bf}.markdown-preview footer{margin-top:18px;padding-top:12px;border-top:1px solid rgba(255,255,255,.1);color:#7f8ba3;font-size:8px}.markdown-preview footer i{color:#72cec5}
</style>
