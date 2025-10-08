# PhotoChecker Tauri 项目结构

## 📁 目录结构

```
tauri/
├── src/                          # React前端代码
│   ├── components/               # React组件
│   │   ├── common/              # 通用组件
│   │   │   ├── LoadingSpinner.tsx
│   │   │   ├── ErrorDisplay.tsx
│   │   │   └── index.ts
│   │   ├── home/                # 主页组件
│   │   │   ├── HomeScreen.tsx
│   │   │   ├── PhotoSelector.tsx
│   │   │   ├── PhotoPreview.tsx
│   │   │   ├── ExifInfoList.tsx
│   │   │   └── index.ts
│   │   ├── about/               # 关于页组件
│   │   │   ├── AboutScreen.tsx
│   │   │   └── index.ts
│   │   └── index.ts
│   ├── hooks/                   # React Hooks
│   │   └── usePhotoSelector.ts
│   ├── services/                # API服务
│   │   └── api.ts
│   ├── types/                   # TypeScript类型定义
│   │   └── index.ts
│   ├── styles/                  # 样式文件
│   ├── assets/                  # 静态资源
│   ├── i18n/                    # 国际化
│   ├── routes/                  # 路由页面
│   ├── App.tsx                  # 主应用组件
│   ├── root.tsx                 # 根组件
│   └── main.tsx                 # 入口文件
├── src-tauri/                   # Rust后端代码
│   ├── src/
│   │   ├── commands/           # Tauri命令
│   │   │   ├── mod.rs
│   │   │   ├── photo.rs        # 照片相关命令
│   │   │   └── exif.rs         # EXIF解析命令
│   │   ├── services/           # 业务逻辑服务
│   │   │   ├── mod.rs
│   │   │   ├── photo_service.rs
│   │   │   └── exif_service.rs
│   │   ├── models/             # 数据模型
│   │   │   ├── mod.rs
│   │   │   ├── photo_info.rs
│   │   │   └── exif_tag.rs
│   │   ├── utils/              # 工具函数
│   │   │   ├── mod.rs
│   │   │   └── file_utils.rs
│   │   ├── lib.rs              # Tauri主入口
│   │   └── main.rs             # 应用入口
│   ├── Cargo.toml              # Rust依赖配置
│   ├── tauri.conf.json         # Tauri配置
│   └── build.rs                # 构建脚本
├── public/                     # 公共静态文件
├── package.json               # Node.js依赖配置
├── tsconfig.json              # TypeScript配置
├── tailwind.config.js         # Tailwind CSS配置
├── vite.config.ts             # Vite构建配置
└── PROJECT_STRUCTURE.md       # 本文档
```

## 🏗️ 架构设计

### 前端架构 (React + TypeScript)

1. **组件化设计**
   - 通用组件 (`components/common/`)
   - 功能模块组件 (`components/home/`, `components/about/`)

2. **状态管理**
   - 使用自定义Hook (`usePhotoSelector`) 管理照片选择和EXIF解析状态
   - 类型安全的UI状态管理

3. **服务层**
   - API服务封装 (`services/api.ts`)
   - 与Tauri后端的类型安全通信

4. **类型系统**
   - 完整的TypeScript类型定义 (`types/index.ts`)
   - 前后端类型一致性保证

### 后端架构 (Rust + Tauri)

1. **命令层** (`commands/`)
   - Tauri命令定义，处理前端调用
   - 输入参数验证和错误处理

2. **服务层** (`services/`)
   - 核心业务逻辑实现
   - 文件处理和EXIF解析服务

3. **模型层** (`models/`)
   - 数据结构定义
   - 序列化/反序列化支持

4. **工具层** (`utils/`)
   - 通用工具函数
   - 文件操作辅助函数

## 🔧 技术栈

### 前端

- **React 19** - 最新版本的React框架
- **TypeScript 5** - 类型安全的JavaScript
- **Tailwind CSS 3** - 实用优先的CSS框架
- **DaisyUI** - 基于Tailwind的组件库
- **React Router 7** - 路由管理
- **Vite** - 快速构建工具

### 后端

- **Rust** - 系统编程语言
- **Tauri 2** - 跨平台应用框架
- **Serde** - 序列化/反序列化框架
- **EXIF库** - EXIF数据解析
- **Image库** - 图片处理

## 🚀 核心功能模块

### 1. 照片选择功能

- 文件对话框选择
- 拖拽上传支持
- 文件类型验证

### 2. EXIF解析功能

- 多格式支持 (JPEG, PNG, TIFF, RAW等)
- 150+ EXIF标签解析
- 数据分组和格式化

### 3. 用户界面

- 响应式设计
- Material Design风格
- 图片预览 + EXIF信息并排显示

### 4. 数据管理

- 搜索和过滤功能
- 导出功能 (JSON格式)
- 复制到剪贴板

## 📝 开发规范

### 命名约定

- **文件名**: PascalCase (组件), camelCase (工具函数)
- **组件名**: PascalCase
- **函数名**: camelCase
- **常量**: UPPER_SNAKE_CASE
- **类型名**: PascalCase

### 代码组织

- 每个组件一个文件
- 导出统一通过 `index.ts` 文件
- 相关功能放在同一目录下

### 错误处理

- 前端使用 ErrorBoundary 和错误状态
- 后端使用 Result<T, E> 类型
- 用户友好的错误提示

## 🎯 下一步开发计划

1. ✅ **Issue #1**: 创建Tauri 2.0项目基础架构
2. ✅ **Issue #2**: 设计和实现项目目录结构
3. 🔄 **Issue #3**: 实现文件选择和读取功能
4. 📋 **Issue #4**: 移植和实现EXIF解析核心功能
5. 📋 **Issue #5**: 实现Tauri命令系统和状态管理

---

_文档更新时间: 2024-10-03_
