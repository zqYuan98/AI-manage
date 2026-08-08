import request from '@/utils/request'

export function listTasks(params) {
  return request({ url: '/lab/task/list', method: 'get', params })
}

export function getTask(id) {
  return request({ url: `/lab/task/${id}`, method: 'get' })
}

export function getTaskProgress(id) {
  return request({ url: `/lab/task/${id}/progress`, method: 'get' })
}

export function addTask(data) {
  return request({ url: '/lab/task', method: 'post', data })
}

export function updateTask(data) {
  return request({ url: '/lab/task', method: 'put', data })
}

export function deleteTask(id, version) {
  return request({ url: `/lab/task/${id}`, method: 'delete', params: { version }})
}

export function activateMonthlyPlan(ownerId, period) {
  return request({ url: '/lab/task/plan/activate', method: 'put', params: { ownerId, period }})
}

export function activateTask(id, version) {
  return request({ url: `/lab/task/${id}/activate`, method: 'put', params: { version }})
}

export function submitTaskResult(id, version, data) {
  return request({ url: `/lab/task/${id}/result/submit`, method: 'put', params: { version }, data })
}

export function withdrawTaskResult(id, version) {
  return request({ url: `/lab/task/${id}/result/withdraw`, method: 'put', params: { version }})
}

export function reviewTaskPass(id, version, data) {
  return request({ url: `/lab/task/${id}/result/review-pass`, method: 'put', params: { version }, data })
}

export function reviewTaskReturn(id, version, data) {
  return request({ url: `/lab/task/${id}/result/review-return`, method: 'put', params: { version }, data })
}

export function reopenTask(id, version, reason) {
  return request({ url: `/lab/task/${id}/result/reopen`, method: 'put', params: { version, reason }})
}

export function listTaskEvidence(taskId) {
  return request({ url: `/lab/task/${taskId}/evidence`, method: 'get' })
}

export function addTaskEvidence(taskId, data) {
  return request({ url: `/lab/task/${taskId}/evidence`, method: 'post', data })
}

export function deleteTaskEvidence(taskId, evidenceId) {
  return request({ url: `/lab/task/${taskId}/evidence/${evidenceId}`, method: 'delete' })
}

export function listQualityGates(taskId) {
  return request({ url: `/lab/task/${taskId}/quality-gate`, method: 'get' })
}

export function addQualityGate(data) {
  return request({ url: '/lab/task/quality-gate', method: 'post', data })
}

export function updateQualityGate(data) {
  return request({ url: '/lab/task/quality-gate', method: 'put', data })
}

export function deleteQualityGate(id) {
  return request({ url: `/lab/task/quality-gate/${id}`, method: 'delete' })
}

export function passQualityGate(id, evidenceId, result) {
  return request({ url: `/lab/task/quality-gate/${id}/pass`, method: 'put', params: { evidenceId, result }})
}

export function listTaskBlocks(taskId) {
  return request({ url: `/lab/task/${taskId}/block`, method: 'get' })
}

export function blockTask(taskId, version, type, reason) {
  return request({ url: `/lab/task/${taskId}/block`, method: 'put', params: { version, type, reason }})
}

export function unblockTask(taskId, version, resolution) {
  return request({ url: `/lab/task/${taskId}/unblock`, method: 'put', params: { version, resolution }})
}

export function listTaskOwners(params) {
  return request({ url: '/lab/member/list', method: 'get', params })
}
