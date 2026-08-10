import request from '@/utils/request'

export function listGoals(params) {
  return request({ url: '/lab/goal/list', method: 'get', params })
}

export function getGoalTree(params) {
  return request({ url: '/lab/goal/tree', method: 'get', params })
}

export function getGoal(id) {
  return request({ url: `/lab/goal/${id}`, method: 'get' })
}

export function getGoalProgress(id, level) {
  return request({ url: `/lab/goal/${id}/progress`, method: 'get', params: { level }})
}

export function addGoal(data) {
  return request({ url: '/lab/goal', method: 'post', data })
}

export function updateGoal(data) {
  return request({ url: '/lab/goal', method: 'put', data })
}

export function activateGoal(id, version) {
  return request({ url: `/lab/goal/${id}/activate`, method: 'put', params: { version }})
}

export function terminateGoal(id, version, reason) {
  return request({ url: `/lab/goal/${id}/terminate`, method: 'put', data: { version, reason }})
}

export function deleteGoal(id, version) {
  return request({ url: `/lab/goal/${id}`, method: 'delete', params: { version }})
}
