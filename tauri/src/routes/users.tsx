import { useState } from 'react'
import { AppLayout } from '../components/app-layout'

interface User {
  id: number
  name: string
  email: string
  status: 'active' | 'inactive'
}

export default function Users() {
  const [users] = useState<User[]>([
    { id: 1, name: '张三', email: 'zhang@example.com', status: 'active' },
    { id: 2, name: '李四', email: 'li@example.com', status: 'active' },
    { id: 3, name: '王五', email: 'wang@example.com', status: 'inactive' },
    { id: 4, name: '赵六', email: 'zhao@example.com', status: 'active' },
  ])

  return (
    <AppLayout>
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold">用户管理</h1>
            <p className="mt-2 text-base-content/70">管理您的应用程序用户</p>
          </div>
          <button className="btn btn-primary">添加用户</button>
        </div>

        <div className="card bg-base-100 shadow-lg">
          <div className="card-body">
            <div className="overflow-x-auto">
              <table className="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>姓名</th>
                    <th>邮箱</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map(user => (
                    <tr key={user.id}>
                      <td>{user.id}</td>
                      <td>{user.name}</td>
                      <td>{user.email}</td>
                      <td>
                        <span
                          className={`badge ${user.status === 'active' ? 'badge-success' : 'badge-warning'}`}
                        >
                          {user.status === 'active' ? '活跃' : '未激活'}
                        </span>
                      </td>
                      <td>
                        <div className="flex gap-2">
                          <button className="btn btn-sm btn-outline">查看</button>
                          <button className="btn btn-sm btn-outline">编辑</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}
