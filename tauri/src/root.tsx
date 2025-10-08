import { Links, Meta, Outlet, Scripts, ScrollRestoration } from 'react-router'
import type { ReactNode } from 'react'

import './index.css'
import './i18n'

export function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>PhotoChecker - EXIF查看器</title>
        <link rel="icon" type="image/x-icon" href="/favicon.ico" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  )
}

export function HydrateFallback() {
  return (
    <div className="min-h-screen bg-base-200 flex items-center justify-center">
      <div className="loading loading-spinner loading-lg text-primary"></div>
    </div>
  )
}

export default function Root() {
  return <Outlet />
}
