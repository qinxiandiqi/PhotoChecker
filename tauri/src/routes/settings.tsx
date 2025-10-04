import { useState } from 'react'
import { AppLayout } from '../components/app-layout'

export default function Settings() {
  const [settings, setSettings] = useState({
    notifications: true,
    darkMode: false,
    autoSave: true,
    language: 'zh-CN',
  })

  const handleToggle = (key: keyof typeof settings) => {
    setSettings({
      ...settings,
      [key]: !settings[key],
    })
  }

  const handleSelect = (key: keyof typeof settings, value: string) => {
    setSettings({
      ...settings,
      [key]: value,
    })
  }

  return (
    <AppLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold">设置</h1>
          <p className="mt-2 text-base-content/70">配置您的应用程序偏好设置</p>
        </div>

        <div className="card bg-base-100 shadow-lg">
          <div className="card-body">
            <h3 className="card-title">常规设置</h3>

            <div className="space-y-4">
              <div className="form-control">
                <label className="label cursor-pointer">
                  <span className="label-text">启用通知</span>
                  <input
                    type="checkbox"
                    className="toggle"
                    checked={settings.notifications}
                    onChange={() => handleToggle('notifications')}
                  />
                </label>
              </div>

              <div className="form-control">
                <label className="label cursor-pointer">
                  <span className="label-text">深色模式</span>
                  <input
                    type="checkbox"
                    className="toggle"
                    checked={settings.darkMode}
                    onChange={() => handleToggle('darkMode')}
                  />
                </label>
              </div>

              <div className="form-control">
                <label className="label cursor-pointer">
                  <span className="label-text">自动保存</span>
                  <input
                    type="checkbox"
                    className="toggle"
                    checked={settings.autoSave}
                    onChange={() => handleToggle('autoSave')}
                  />
                </label>
              </div>

              <div className="form-control">
                <label className="label">
                  <span className="label-text">语言</span>
                </label>
                <select
                  className="select select-bordered"
                  value={settings.language}
                  onChange={e => handleSelect('language', e.target.value)}
                >
                  <option value="zh-CN">简体中文</option>
                  <option value="zh-TW">繁体中文</option>
                  <option value="en-US">English</option>
                  <option value="ja-JP">日本語</option>
                </select>
              </div>
            </div>

            <div className="card-actions justify-end mt-6">
              <button className="btn btn-primary">保存设置</button>
            </div>
          </div>
        </div>

        <div className="card bg-base-100 shadow-lg">
          <div className="card-body">
            <h3 className="card-title">关于</h3>
            <div className="space-y-2">
              <p>
                <strong>版本:</strong> 1.0.0
              </p>
              <p>
                <strong>构建:</strong> 2024-01-20
              </p>
              <p>
                <strong>框架:</strong> Tauri 2 + React 19 + TypeScript
              </p>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}
