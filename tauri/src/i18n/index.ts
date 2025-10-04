import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { resources } from './resources'

const isDevelopment = process.env.NODE_ENV === 'development'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'zh-CN',
    debug: isDevelopment,

    // 语言检测配置
    detection: {
      order: ['localStorage', 'navigator', 'fallback'],
      lookupLocalStorage: 'tauri-app-language',
      caches: ['localStorage'],
    },

    // 命名空间配置
    ns: ['common', 'home', 'dashboard', 'users', 'settings', 'validation'],
    defaultNS: 'common',

    // 插值配置
    interpolation: {
      escapeValue: false, // React 已经处理了 XSS
    },

    // React相关配置
    react: {
      useSuspense: false, // 避免与React Router冲突
    },
  })

export default i18n
