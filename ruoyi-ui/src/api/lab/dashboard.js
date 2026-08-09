import request from '@/utils/request'

export function getDashboardOverview(period) {
  return request({
    url: '/lab/dashboard',
    method: 'get',
    params: { period }
  })
}

function getWorkbench(role, period, asOf) {
  return request({
    url: `/lab/workbench/${role}`,
    method: 'get',
    params: { period, asOf }
  })
}

export function getManagerWorkbench(period, asOf) {
  return getWorkbench('manager', period, asOf)
}

export function getLeadWorkbench(period, asOf) {
  return getWorkbench('lead', period, asOf)
}

export function getMemberWorkbench(period, asOf) {
  return getWorkbench('member', period, asOf)
}

export function listDashboardReminders(params) {
  return request({
    url: '/lab/dashboard/reminders',
    method: 'get',
    params
  })
}

export function markDashboardReminderRead(id, version) {
  return request({
    url: `/lab/dashboard/reminders/${id}/read`,
    method: 'put',
    params: { version }
  })
}

export function markAllDashboardRemindersRead() {
  return request({
    url: '/lab/dashboard/reminders/read-all',
    method: 'put'
  })
}
