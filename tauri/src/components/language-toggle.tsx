import { useTranslation } from 'react-i18next'
import { Globe } from 'lucide-react'

interface LanguageToggleProps {
  className?: string
}

export function LanguageToggle({ className }: LanguageToggleProps) {
  const { i18n, t } = useTranslation('common')

  const languages = [
    { code: 'zh-CN', name: t('language.chinese'), flag: '🇨🇳' },
    { code: 'en-US', name: t('language.english'), flag: '🇺🇸' },
  ]

  const changeLanguage = (langCode: string) => {
    i18n.changeLanguage(langCode)
  }

  return (
    <div className={`dropdown dropdown-end ${className}`}>
      <div
        tabIndex={0}
        role="button"
        className="btn btn-ghost btn-circle"
        aria-label={t('language.toggle')}
        title={t('language.toggle')}
      >
        <Globe className="h-5 w-5" />
      </div>
      <ul
        tabIndex={0}
        className="dropdown-content menu bg-base-100 rounded-box z-[1] w-40 p-2 shadow"
      >
        {languages.map(lang => (
          <li key={lang.code}>
            <button
              onClick={() => changeLanguage(lang.code)}
              className={`flex items-center gap-2 ${i18n.language === lang.code ? 'active' : ''}`}
            >
              <span className="text-lg">{lang.flag}</span>
              <span className="flex-1">{lang.name}</span>
              {i18n.language === lang.code && <span className="text-primary">✓</span>}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
