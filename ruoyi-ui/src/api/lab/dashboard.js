import request from '@/utils/request'

export function getDashboardOverview(period) {
  return request({
    url: '/lab/dashboard',
    method: 'get',
    params: { period }
  })
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
