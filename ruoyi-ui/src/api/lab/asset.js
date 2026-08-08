import request from '@/utils/request'

export function listAssets(params) {
  return request({ url: '/lab/asset/list', method: 'get', params })
}

export function listAssetRisks(params) {
  return request({ url: '/lab/asset/risks', method: 'get', params })
}

export function getAsset(id) {
  return request({ url: `/lab/asset/${id}`, method: 'get' })
}

export function addAsset(data) {
  return request({ url: '/lab/asset', method: 'post', data })
}

export function updateAsset(data) {
  return request({ url: '/lab/asset', method: 'put', data })
}

export function deactivateAsset(id, version) {
  return request({ url: `/lab/asset/${id}`, method: 'delete', params: { version }})
}
