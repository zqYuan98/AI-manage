import request from '@/utils/request'

export function listMembers(params) {
  return request({ url: '/lab/member/list', method: 'get', params })
}

export function listAvailableUsers() {
  return request({ url: '/lab/member/available-users', method: 'get' })
}

export function getMember(id) {
  return request({ url: `/lab/member/${id}`, method: 'get' })
}

export function addMember(data) {
  return request({ url: '/lab/member', method: 'post', data })
}

export function updateMember(data) {
  return request({ url: '/lab/member', method: 'put', data })
}

export function deactivateMember(id, version) {
  return request({ url: `/lab/member/${id}/deactivate`, method: 'put', params: { version }})
}

export function reactivateMember(id, version) {
  return request({ url: `/lab/member/${id}/reactivate`, method: 'put', params: { version }})
}

export function getSkillMatrix(id) {
  return request({ url: `/lab/member/${id}/skills`, method: 'get' })
}

export function saveSkillMatrix(id, data) {
  return request({ url: `/lab/member/${id}/skills`, method: 'put', data })
}

export function listSkills(params) {
  return request({ url: '/lab/skill/list', method: 'get', params })
}

export function listOneToOnes(params) {
  return request({ url: '/lab/one2one/list', method: 'get', params })
}

export function addOneToOne(data) {
  return request({ url: '/lab/one2one', method: 'post', data })
}

export function updateOneToOne(data) {
  return request({ url: '/lab/one2one', method: 'put', data })
}
