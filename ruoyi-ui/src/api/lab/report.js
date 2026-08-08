import request from '@/utils/request'

export function listReportBizLines() { return request({ url: '/lab/report/biz-lines', method: 'get' }) }
export function listReportHistory(params) { return request({ url: '/lab/report/history', method: 'get', params }) }
export function getReportStatus(id) { return request({ url: `/lab/report/${id}/status`, method: 'get' }) }
export function getReportBody(id) { return request({ url: `/lab/report/${id}/body`, method: 'get' }) }
export function listReportJobs(id) { return request({ url: `/lab/report/${id}/jobs`, method: 'get' }) }
export function generateReport(data) { return request({ url: '/lab/report/generate', method: 'post', data }) }
export function retryReportArtifact(id, artifact) { return request({ url: `/lab/report/${id}/retry/${artifact}`, method: 'post' }) }
export function finalizeReport(id, version) { return request({ url: `/lab/report/${id}/finalize`, method: 'put', params: { version }}) }
export function getReportSummary(params) { return request({ url: '/lab/report/summary', method: 'get', params }) }
export function getReportSummarySections(params) { return request({ url: '/lab/report/summary-sections', method: 'get', params }) }
export function saveReportSummary(data) { return request({ url: '/lab/report/summary', method: 'put', data }) }
export function saveReportSummaries(data) { return request({ url: '/lab/report/summaries', method: 'put', data }) }
export function downloadReportArtifact(id, format) { return request({ url: `/lab/report/${id}/artifact/${format}`, method: 'get', responseType: 'blob' }) }
export function importReportMarkdown(id, file) {
  const data = new FormData()
  data.append('file', file)
  return request({ url: `/lab/report/${id}/markdown-import`, method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' }})
}
