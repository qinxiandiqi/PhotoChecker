// 中文语言包
import zhCNCommon from './locales/zh-CN/common.json'
import zhCNHome from './locales/zh-CN/home.json'
import zhCNDashboard from './locales/zh-CN/dashboard.json'
import zhCNUsers from './locales/zh-CN/users.json'
import zhCNSettings from './locales/zh-CN/settings.json'
import zhCNValidation from './locales/zh-CN/validation.json'

// 英文语言包
import enUSCommon from './locales/en-US/common.json'
import enUSHome from './locales/en-US/home.json'
import enUSDashboard from './locales/en-US/dashboard.json'
import enUSUsers from './locales/en-US/users.json'
import enUSSettings from './locales/en-US/settings.json'
import enUSValidation from './locales/en-US/validation.json'

export const resources = {
  'zh-CN': {
    common: zhCNCommon,
    home: zhCNHome,
    dashboard: zhCNDashboard,
    users: zhCNUsers,
    settings: zhCNSettings,
    validation: zhCNValidation,
  },
  'en-US': {
    common: enUSCommon,
    home: enUSHome,
    dashboard: enUSDashboard,
    users: enUSUsers,
    settings: enUSSettings,
    validation: enUSValidation,
  },
} as const

export type Language = keyof typeof resources
export type Namespace = keyof (typeof resources)['zh-CN']
