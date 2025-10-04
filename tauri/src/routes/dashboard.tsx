import { useState } from 'react'
import { AppLayout } from '../components/app-layout'

export default function Dashboard() {
  const [stats] = useState({
    users: 1234,
    projects: 56,
    tasks: 89,
  })

  return (
    <AppLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold">仪表板</h1>
          <p className="mt-2 text-base-content/70">查看您的应用程序统计数据和概览</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="card bg-base-100 shadow-lg">
            <div className="card-body">
              <h2 className="card-title text-primary">用户</h2>
              <div className="text-3xl font-bold">{stats.users.toLocaleString()}</div>
              <div className="text-sm text-base-content/70">总用户数</div>
            </div>
          </div>

          <div className="card bg-base-100 shadow-lg">
            <div className="card-body">
              <h2 className="card-title text-secondary">项目</h2>
              <div className="text-3xl font-bold">{stats.projects}</div>
              <div className="text-sm text-base-content/70">活跃项目</div>
            </div>
          </div>

          <div className="card bg-base-100 shadow-lg">
            <div className="card-body">
              <h2 className="card-title text-accent">任务</h2>
              <div className="text-3xl font-bold">{stats.tasks}</div>
              <div className="text-sm text-base-content/70">待处理任务</div>
            </div>
          </div>
        </div>

        <div className="card bg-base-100 shadow-lg">
          <div className="card-body">
            <h3 className="card-title">最近活动</h3>
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-success rounded-full"></div>
                <span className="text-sm">新用户注册</span>
                <span className="text-xs text-base-content/50">2分钟前</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-warning rounded-full"></div>
                <span className="text-sm">项目更新</span>
                <span className="text-xs text-base-content/50">15分钟前</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-info rounded-full"></div>
                <span className="text-sm">系统维护完成</span>
                <span className="text-xs text-base-content/50">1小时前</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}
