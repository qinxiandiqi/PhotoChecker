# PhotoChecker - Tauri 版本

基于 Tauri 2.0 + React + Material UI 的跨平台 EXIF 信息查看器。

## 功能特点

- 🖼️ **照片选择**: 支持点击选择和拖拽上传
- 🔍 **EXIF 解析**: 解析照片的详细技术信息
- 📱 **跨平台**: 支持 Windows、macOS、Linux 桌面端
- 🎨 **Material 3 UI**: 使用 Material Design 3 设计语言
- 📐 **响应式**: 适配不同屏幕尺寸
- 🌐 **React Router**: 单页面应用路由管理

## 技术栈

- **前端**: React 19, TypeScript, Vite
- **UI 框架**: Material UI 5 (MUI)
- **路由**: React Router DOM
- **EXIF 解析**: exifr
- **桌面框架**: Tauri 2.0
- **包管理**: pnpm

## 开发环境要求

- Node.js >= 18
- pnpm >= 8
- Rust >= 1.70

## 安装依赖

```bash
cd tauri
pnpm install
```

## 开发命令

### 启动开发服务器

```bash
pnpm tauri:dev
```

### 构建项目

```bash
# 构建桌面端版本
pnpm tauri:build

# 构建移动端版本 (iOS/Android)
pnpm tauri:build:mobile

# 构建特定平台
pnpm tauri build --target x86_64-apple-darwin  # macOS
pnpm tauri build --target x86_64-pc-windows-gnu  # Windows
pnpm tauri build --target x86_64-unknown-linux-gnu  # Linux
```

### 预览构建结果

```bash
pnpm preview
```

## 项目结构

```
tauri/
├── src/                    # React 源代码
│   ├── components/         # 组件
│   ├── pages/              # 页面
│   ├── hooks/              # 自定义 hooks
│   ├── utils/              # 工具函数
│   ├── types/              # TypeScript 类型定义
│   ├── theme.tsx           # 主题配置
│   ├── App.tsx             # 主应用组件
│   └── main.tsx            # 入口文件
├── src-tauri/              # Tauri 后端代码
│   ├── src/                # Rust 源代码
│   ├── tauri.conf.json     # Tauri 配置
│   └── Cargo.toml         # Rust 依赖
├── public/                 # 静态资源
├── package.json            # 项目依赖
├── vite.config.ts          # Vite 配置
└── tsconfig.json           # TypeScript 配置
```

## 主要功能

### 照片选择
- 支持点击选择文件
- 支持拖拽上传
- 支持多种图片格式 (JPG, PNG, WEBP 等)

### EXIF 信息解析
- 相机品牌和型号
- 拍摄时间和日期
- 曝光参数 (光圈、快门、ISO)
- 焦距信息
- GPS 地理位置
- 白平衡设置
- 闪光灯状态

### 响应式设计
- 桌面端: 900x700 窗口
- 移动端: 自适应屏幕尺寸
- Material 3 设计语言

## 开发指南

### 添加新的 EXIF 标签

1. 在 `src/utils/exifParser.ts` 中更新 `commonTags` 映射
2. 在 `formatExifValue` 函数中添加特殊格式化逻辑
3. 在标签顺序数组中添加新的标签

### 修改主题

编辑 `src/theme.tsx` 文件来自定义 Material 3 主题。

### 添加新页面

1. 在 `src/pages/` 目录下创建新页面组件
2. 在 `App.tsx` 中添加新的路由
3. 更新导航组件

## 构建发布版本

```bash
# 构建所有平台
pnpm tauri build

# 构建特定平台
pnpm tauri build --target universal-apple-darwin  # macOS Universal
pnpm tauri build --target x86_64-pc-windows-gnu  # Windows 64-bit
pnpm tauri build --target x86_64-unknown-linux-gnu  # Linux 64-bit
```

## 许可证

本项目与原 Android 版本使用相同的许可证。

## 贡献

欢迎提交 Issues 和 Pull Requests！
