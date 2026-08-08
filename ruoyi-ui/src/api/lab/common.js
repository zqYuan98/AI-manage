import request from '@/utils/request'

export function getLabDict(dictType) {
  return request({
    url: `/system/dict/data/type/${dictType}`,
    method: 'get'
  })
}

export function getLabConfig(configKey) {
  return request({
    url: `/system/config/configKey/${configKey}`,
    method: 'get'
  })
}
