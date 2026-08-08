import request from '@/utils/request'

export function listMyPerformance(period) {
  return request({ url: '/lab/perf/my', method: 'get', params: { period }})
}

export function listPerformance(period) {
  return request({ url: '/lab/perf/list', method: 'get', params: { period }})
}

export function listRevisions(memberId, period) {
  return request({ url: `/lab/perf/member/${memberId}/revisions`, method: 'get', params: { period }})
}

export function previewPerformance(memberId, period) {
  return request({ url: '/lab/perf/preview', method: 'get', params: { memberId, period }})
}

export function listCollaboration(period) {
  return request({ url: '/lab/perf/collaboration', method: 'get', params: { period }})
}

export function addCollaboration(data) {
  return request({ url: '/lab/perf/collaboration', method: 'post', data })
}

export function reviewCollaboration(id, data) {
  return request({ url: `/lab/perf/collaboration/${id}/review`, method: 'put', data })
}

export function closePerformancePeriod(period, reason) {
  return request({ url: `/lab/perf/period/${period}/close`, method: 'put', params: { reason }})
}

export function reopenPerformancePeriod(period, reason) {
  return request({ url: `/lab/perf/period/${period}/reopen`, method: 'put', params: { reason }})
}

export function confirmPerformance(id, version) {
  return request({ url: `/lab/perf/${id}/confirm`, method: 'put', params: { version }})
}

export function revokeRedLine(id, data) {
  return request({ url: `/lab/perf/${id}/red-line/revoke`, method: 'put', data })
}

export function calibrateQuarter(quarter, memberId, data) {
  return request({ url: `/lab/perf/quarter/${quarter}/member/${memberId}/calibrate`, method: 'put', data })
}
