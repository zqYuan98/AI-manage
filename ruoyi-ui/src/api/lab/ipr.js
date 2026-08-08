import request from '@/utils/request'

export function listIpr(params) {
  return request({ url: '/lab/ipr/list', method: 'get', params })
}

export function getIpr(id) {
  return request({ url: `/lab/ipr/${id}`, method: 'get' })
}

export function addIpr(data) {
  return request({ url: '/lab/ipr', method: 'post', data })
}

export function updateIpr(ipr, rollbackReason) {
  return request({ url: '/lab/ipr', method: 'put', data: { ipr, rollbackReason }})
}

export function deactivateIpr(id, version) {
  return request({ url: `/lab/ipr/${id}`, method: 'delete', params: { version }})
}
