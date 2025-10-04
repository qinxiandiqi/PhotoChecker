import { ReactNode } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { ThemeToggle } from './theme-toggle'
import { LanguageToggle } from './language-toggle'

interface AppLayoutProps {
  children: ReactNode
}

export function AppLayout({ children }: AppLayoutProps) {
  const { t } = useTranslation('common')

  return (
    <div className="min-h-screen bg-base-200">
      <nav className="navbar bg-base-100 shadow">
        <div className="container mx-auto">
          <div className="flex-1">
            <Link to="/" className="btn btn-ghost text-xl">
              {t('nav.brand')}
            </Link>
          </div>
          <div className="flex-none gap-2">
            <Link to="/" className="btn btn-ghost">
              {t('nav.home')}
            </Link>
            <Link to="/dashboard" className="btn btn-ghost">
              {t('nav.dashboard')}
            </Link>
            <Link to="/users" className="btn btn-ghost">
              {t('nav.users')}
            </Link>
            <Link to="/settings" className="btn btn-ghost">
              {t('nav.settings')}
            </Link>
            <LanguageToggle />
            <ThemeToggle />
          </div>
        </div>
      </nav>

      <main className="container mx-auto p-4">{children}</main>
    </div>
  )
}
