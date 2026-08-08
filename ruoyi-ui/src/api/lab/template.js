import request from '@/utils/request'

export function listTemplateTree() { return request({ url: '/lab/template/tree', method: 'get' }) }
export function getTemplateMetadata() { return request({ url: '/lab/template/metadata', method: 'get' }) }
export function getTemplateConfig(id) { return request({ url: `/lab/template/${id}/config`, method: 'get' }) }
export function getTemplatePreview(id) { return request({ url: `/lab/template/${id}/preview`, method: 'get' }) }
export function saveTemplateRevision(data) { return request({ url: '/lab/template/revision', method: 'post', data }) }
export function saveTemplateAs(data) { return request({ url: '/lab/template/save-as', method: 'post', data }) }
export function publishTemplateDefault(id, version) { return request({ url: `/lab/template/${id}/default`, method: 'put', params: { version }}) }
export function exportTemplate(id) { return request({ url: `/lab/template/${id}/export`, method: 'get', responseType: 'blob' }) }
export function importTemplate(file, templateCode) {
  const data = new FormData()
  data.append('file', file)
  data.append('templateCode', templateCode)
  return request({ url: '/lab/template/import', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' }})
}
