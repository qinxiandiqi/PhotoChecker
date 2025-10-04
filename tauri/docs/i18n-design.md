# 国际化方案设计文档

## 概述

本文档详细描述了 Tauri + React 模板项目的国际化（i18n）实施方案。该方案基于 `react-i18next` 技术栈，提供完整的多语言支持解决方案。

## 目录

- [项目现状分析](#项目现状分析)
- [技术选型](#技术选型)
- [目录结构设计](#目录结构设计)
- [语言切换机制](#语言切换机制)
- [组件使用方式](#组件使用方式)
- [路由和格式化](#路由和格式化)
- [实施计划](#实施计划)
- [维护指南](#维护指南)

## 项目现状分析

### 需要国际化的内容

当前项目中需要国际化的内容包括：

#### 用户界面文本

- **导航栏**：品牌名称、首页、仪表板、用户、设置
- **主页内容**：欢迎标题、副标题、功能介绍
- **表单元素**：输入框占位符、按钮文本、标签文本
- **功能卡片**：高性能、安全、现代化等描述文本
- **交互反馈**：成功/错误提示信息、确认对话框

#### 数据格式

- 数字格式（千位分隔符、小数点）
- 日期时间格式
- 货币符号和格式

### 技术栈兼容性分析

项目当前使用的技术栈：

- **React 19.1.1** + TypeScript 5.8.3
- **React Router v7** 文件路由系统
- **DaisyUI** + Tailwind CSS 样式框架
- **Vite** 构建工具
- **Tauri 2.0** 桌面应用框架

所选国际化方案需要与以上技术完全兼容。

## 技术选型

### 核心库选择

**主要依赖：react-i18next + i18next**

选择理由：

1. **成熟稳定**：React 生态中最成熟的国际化解决方案
2. **TypeScript 友好**：提供完整的类型支持
3. **功能完整**：支持命名空间、懒加载、插值、复数处理
4. **生态丰富**：插件生态完善，社区活跃
5. **性能优异**：支持按需加载和缓存机制

### 依赖包配置

```json
{
  "dependencies": {
    "react-i18next": "^14.1.3",
    "i18next": "^24.0.1",
    "i18next-browser-languagedetector": "^8.0.0"
  }
}
```

### 支持的语言

初始支持语言：

- **中文（简体）**：`zh-CN`（默认语言）
- **英语**：`en-US`

扩展计划：

- 框架支持后续添加其他语言（日文、韩文等）
- 通过配置文件轻松添加新语言

## 目录结构设计

### 完整目录结构

```
template/src/
├── i18n/                          # 国际化配置目录
│   ├── index.ts                   # i18next 配置入口文件
│   ├── resources.ts               # 语言资源聚合配置
│   └── locales/                   # 语言文件目录
│       ├── zh-CN/                 # 中文语言包
│       │   ├── common.json        # 公共文案（导航、按钮等）
│       │   ├── home.json          # 首页专用文案
│       │   ├── dashboard.json     # 仪表板页面文案
│       │   ├── users.json         # 用户管理文案
│       │   ├── settings.json      # 设置页面文案
│       │   └── validation.json    # 表单验证信息
│       └── en-US/                 # 英文语言包
│           ├── common.json        # 对应的英文文案
│           ├── home.json
│           ├── dashboard.json
│           ├── users.json
│           ├── settings.json
│           └── validation.json
```

### 语言文件结构示例

#### zh-CN/common.json

```json
{
  "nav": {
    "brand": "Tauri App",
    "home": "首页",
    "dashboard": "仪表板",
    "users": "用户",
    "settings": "设置"
  },
  "actions": {
    "greet": "打招呼",
    "save": "保存",
    "cancel": "取消",
    "confirm": "确认",
    "delete": "删除",
    "edit": "编辑"
  },
  "theme": {
    "toggle": "切换主题",
    "light": "浅色主题",
    "dark": "深色主题"
  },
  "language": {
    "toggle": "切换语言",
    "chinese": "中文",
    "english": "English"
  }
}
```

#### zh-CN/home.json

```json
{
  "title": "欢迎来到 Tauri + React",
  "subtitle": "这是一个现代化的桌面应用模板",
  "demo": {
    "title": "Tauri 命令示例",
    "description": "输入您的名字，然后点击按钮来调用 Rust 命令",
    "placeholder": "输入名字...",
    "success": "调用成功！"
  },
  "features": {
    "performance": {
      "title": "🚀 高性能",
      "description": "Tauri 提供极小的包体积和极快的性能"
    },
    "security": {
      "title": "🛡️ 安全",
      "description": "Rust 提供内存安全和类型安全"
    },
    "modern": {
      "title": "🎨 现代化",
      "description": "React 19 + TypeScript + Tailwind CSS"
    }
  }
}
```

#### zh-CN/validation.json

```json
{
  "required": "此字段为必填项",
  "email": "请输入有效的邮箱地址",
  "minLength": "最少需要 {{count}} 个字符",
  "maxLength": "最多只能输入 {{count}} 个字符",
  "pattern": "格式不正确"
}
```

### 英文语言包示例

#### en-US/common.json

```json
{
  "nav": {
    "brand": "Tauri App",
    "home": "Home",
    "dashboard": "Dashboard",
    "users": "Users",
    "settings": "Settings"
  },
  "actions": {
    "greet": "Greet",
    "save": "Save",
    "cancel": "Cancel",
    "confirm": "Confirm",
    "delete": "Delete",
    "edit": "Edit"
  },
  "theme": {
    "toggle": "Toggle Theme",
    "light": "Light Theme",
    "dark": "Dark Theme"
  },
  "language": {
    "toggle": "Switch Language",
    "chinese": "中文",
    "english": "English"
  }
}
```

## 语言切换机制

### LanguageToggle 组件设计

创建专门的语言切换组件，集成到应用导航栏：

```typescript
// src/components/language-toggle.tsx
import { useTranslation } from 'react-i18next'
import { Globe } from 'lucide-react'

interface LanguageToggleProps {
  className?: string
}

export function LanguageToggle({ className }: LanguageToggleProps) {
  const { i18n, t } = useTranslation('common')

  const languages = [
    { code: 'zh-CN', name: t('language.chinese'), flag: '🇨🇳' },
    { code: 'en-US', name: t('language.english'), flag: '🇺🇸' }
  ]

  const changeLanguage = (langCode: string) => {
    i18n.changeLanguage(langCode)
  }

  return (
    <div className={`dropdown dropdown-end ${className}`}>
      <div tabIndex={0} role="button" className="btn btn-ghost btn-circle" aria-label={t('language.toggle')}>
        <Globe className="h-5 w-5" />
      </div>
      <ul tabIndex={0} className="dropdown-content menu bg-base-100 rounded-box z-[1] w-40 p-2 shadow">
        {languages.map((lang) => (
          <li key={lang.code}>
            <button
              onClick={() => changeLanguage(lang.code)}
              className={i18n.language === lang.code ? 'active' : ''}
            >
              <span>{lang.flag}</span>
              <span>{lang.name}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
```

### 语言持久化策略

#### 存储配置

- **存储方式**：localStorage
- **存储键名**：`tauri-app-language`
- **存储格式**：字符串（如 'zh-CN', 'en-US'）

#### 语言初始化逻辑

1. **优先级1**：检查 localStorage 中的用户设置
2. **优先级2**：检测浏览器语言偏好（navigator.language）
3. **优先级3**：回退到默认语言（zh-CN）

#### i18next 配置示例

```typescript
// src/i18n/index.ts
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { resources } from './resources'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'zh-CN',
    debug: process.env.NODE_ENV === 'development',

    detection: {
      order: ['localStorage', 'navigator', 'fallback'],
      lookupLocalStorage: 'tauri-app-language',
      caches: ['localStorage'],
    },

    interpolation: {
      escapeValue: false,
    },

    // 命名空间配置
    ns: ['common', 'home', 'dashboard', 'users', 'settings', 'validation'],
    defaultNS: 'common',
  })

export default i18n
```

## 组件使用方式

### 基础 Hook 使用

```typescript
import { useTranslation } from 'react-i18next'

// 使用默认命名空间
function MyComponent() {
  const { t } = useTranslation()
  return <button>{t('actions.greet')}</button>
}

// 使用指定命名空间
function HomePage() {
  const { t } = useTranslation('home')
  return <h1>{t('title')}</h1>
}

// 使用多个命名空间
function ComplexComponent() {
  const { t: tCommon } = useTranslation('common')
  const { t: tHome } = useTranslation('home')

  return (
    <div>
      <h1>{tHome('title')}</h1>
      <button>{tCommon('actions.greet')}</button>
    </div>
  )
}
```

### TypeScript 类型支持

```typescript
// src/types/i18n.ts
export type NamespaceKeys = {
  common: 'nav.home' | 'nav.dashboard' | 'actions.greet' | 'actions.save'
  home: 'title' | 'subtitle' | 'demo.title' | 'demo.description'
  validation: 'required' | 'email' | 'minLength' | 'maxLength'
}

// 类型安全的 Hook
export const useTypedTranslation = <T extends keyof NamespaceKeys>(namespace: T) => {
  const { t, ...rest } = useTranslation(namespace)
  return {
    t: (key: NamespaceKeys[T], options?: any) => t(key, options),
    ...rest,
  }
}
```

### 插值和复数处理

```typescript
// 带参数的翻译
const { t } = useTranslation('validation')
const errorMsg = t('minLength', { count: 5 }) // "最少需要 5 个字符"

// 复数处理
const { t } = useTranslation()
const itemCount = t('items', { count: items.length })
// count: 0 -> "没有项目"
// count: 1 -> "1 个项目"
// count: 2+ -> "{{count}} 个项目"
```

### 组件重构示例

#### 重构前的组件

```typescript
// src/routes/_index.tsx (重构前)
export default function Home() {
  return (
    <AppLayout>
      <div className="space-y-6">
        <div className="text-center">
          <h1 className="text-4xl font-bold">欢迎来到 Tauri + React</h1>
          <p className="mt-2 text-lg text-base-content/70">
            这是一个现代化的桌面应用模板
          </p>
        </div>

        <div className="card bg-base-100 shadow-xl">
          <div className="card-body">
            <h2 className="card-title">Tauri 命令示例</h2>
            <p className="text-base-content/70">
              输入您的名字，然后点击按钮来调用 Rust 命令
            </p>

            <input
              type="text"
              className="input input-bordered"
              placeholder="输入名字..."
              value={name}
              onChange={e => setName(e.currentTarget.value)}
            />

            <button className="btn btn-primary" onClick={greet}>
              打招呼
            </button>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}
```

#### 重构后的组件

```typescript
// src/routes/_index.tsx (重构后)
import { useTranslation } from 'react-i18next'

export default function Home() {
  const { t: tCommon } = useTranslation('common')
  const { t: tHome } = useTranslation('home')

  return (
    <AppLayout>
      <div className="space-y-6">
        <div className="text-center">
          <h1 className="text-4xl font-bold">{tHome('title')}</h1>
          <p className="mt-2 text-lg text-base-content/70">
            {tHome('subtitle')}
          </p>
        </div>

        <div className="card bg-base-100 shadow-xl">
          <div className="card-body">
            <h2 className="card-title">{tHome('demo.title')}</h2>
            <p className="text-base-content/70">
              {tHome('demo.description')}
            </p>

            <input
              type="text"
              className="input input-bordered"
              placeholder={tHome('demo.placeholder')}
              value={name}
              onChange={e => setName(e.currentTarget.value)}
            />

            <button className="btn btn-primary" onClick={greet}>
              {tCommon('actions.greet')}
            </button>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}
```

## 路由和格式化

### URL 国际化策略

**设计决策：不实施 URL 国际化**

理由分析：

1. **桌面应用特性**：Tauri 桌面应用的 URL 对用户不可见
2. **技术简化**：避免与 React Router v7 文件路由产生复杂配置
3. **维护成本**：减少路由配置的复杂性和维护成本
4. **用户体验**：桌面应用用户不直接接触 URL

### 数字和日期格式化

#### 国际化格式化工具

```typescript
// src/i18n/formatters.ts
export const formatters = {
  number: (value: number, locale: string, options?: Intl.NumberFormatOptions) => {
    return new Intl.NumberFormat(locale, options).format(value)
  },

  currency: (value: number, locale: string, currency: string) => {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currency,
    }).format(value)
  },

  date: (date: Date, locale: string, options?: Intl.DateTimeFormatOptions) => {
    return new Intl.DateTimeFormat(locale, options).format(date)
  },

  relativeTime: (value: number, unit: Intl.RelativeTimeFormatUnit, locale: string) => {
    return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(value, unit)
  },
}
```

#### i18next 集成格式化

```typescript
// 集成到 i18next 配置中
const i18nConfig = {
  // ...其他配置
  interpolation: {
    formatters: {
      number: (value, format, lng) => {
        return formatters.number(value, lng || 'zh-CN')
      },
      currency: (value, format, lng) => {
        const [currency = 'CNY'] = (format || '').split(':')
        return formatters.currency(value, lng || 'zh-CN', currency)
      },
      date: (value, format, lng) => {
        const options = format
          ? JSON.parse(format)
          : {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            }
        return formatters.date(new Date(value), lng || 'zh-CN', options)
      },
    },
  },
}
```

#### 使用示例

```typescript
// 在组件中使用格式化
const { t } = useTranslation()

// 数字格式化
const price = 1234.56
const formattedPrice = t('price', {
  price,
  formatParams: {
    price: { currency: 'CNY' },
  },
})

// 日期格式化
const date = new Date()
const formattedDate = t('lastUpdated', {
  date,
  formatParams: {
    date: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
  },
})
```

## 实施计划

### Phase 1：基础框架搭建（Week 1）

#### 任务清单

- [ ] 安装必要的依赖包
- [ ] 配置 i18next 基础设置
- [ ] 创建语言文件目录结构
- [ ] 实现语言检测和持久化机制
- [ ] 创建 LanguageToggle 组件

#### 验收标准

- i18next 正确初始化并可以切换语言
- 语言设置能够正确保存到 localStorage
- LanguageToggle 组件功能正常

### Phase 2：内容国际化（Week 2）

#### 任务清单

- [ ] 重构 AppLayout 组件支持多语言
- [ ] 重构主页面（\_index.tsx）内容
- [ ] 创建所有页面的语言文件
- [ ] 完成中文语言包
- [ ] 完成英文语言包翻译

#### 验收标准

- 所有静态文本都通过 t() 函数获取
- 中英文翻译内容准确完整
- 页面切换语言后显示正确

### Phase 3：功能完善（Week 3）

#### 任务清单

- [ ] 添加 TypeScript 类型支持
- [ ] 实现数字和日期格式化
- [ ] 添加表单验证信息国际化
- [ ] 优化组件 Props 和接口
- [ ] 编写单元测试

#### 验收标准

- TypeScript 类型检查通过
- 格式化功能正常工作
- 测试覆盖率达到要求

### Phase 4：优化和文档（Week 4）

#### 任务清单

- [ ] 性能优化和懒加载
- [ ] 完善开发体验工具
- [ ] 编写使用文档
- [ ] 代码审查和重构
- [ ] 用户测试和反馈收集

#### 验收标准

- 应用性能无明显下降
- 文档完整清晰
- 用户反馈良好

## 维护指南

### 添加新语言

#### 步骤说明

1. **创建语言目录**

   ```bash
   mkdir src/i18n/locales/ja-JP  # 日文示例
   ```

2. **复制并翻译语言文件**

   ```bash
   cp src/i18n/locales/zh-CN/*.json src/i18n/locales/ja-JP/
   # 然后翻译所有 JSON 文件中的内容
   ```

3. **更新资源配置**

   ```typescript
   // src/i18n/resources.ts
   import jaJP from './locales/ja-JP'

   export const resources = {
     'zh-CN': zhCN,
     'en-US': enUS,
     'ja-JP': jaJP, // 新增
   }
   ```

4. **更新语言切换组件**
   ```typescript
   const languages = [
     { code: 'zh-CN', name: '中文', flag: '🇨🇳' },
     { code: 'en-US', name: 'English', flag: '🇺🇸' },
     { code: 'ja-JP', name: '日本語', flag: '🇯🇵' }, // 新增
   ]
   ```

### 翻译内容更新

#### 命名规范

- **文件命名**：使用小写字母和连字符，如 `user-management.json`
- **键名命名**：使用点分层级，如 `user.profile.edit`
- **值命名**：简洁明了，避免HTML标签

#### 翻译流程

1. **内容识别**：确定需要翻译的文本
2. **键值设计**：设计合理的键名层级
3. **多语言翻译**：确保所有支持语言都有对应翻译
4. **测试验证**：在各种语言环境下测试显示效果

### 性能优化

#### 懒加载配置

```typescript
// 按需加载语言包
i18n.use(Backend).init({
  backend: {
    loadPath: '/locales/{{lng}}/{{ns}}.json',
  },
  fallbackLng: 'zh-CN',
  preload: ['zh-CN'], // 预加载默认语言
})
```

#### 缓存策略

```typescript
// 配置缓存以提高性能
const cacheConfig = {
  enabled: true,
  expirationTime: 7 * 24 * 60 * 60 * 1000, // 7天过期
}
```

### 故障排查

#### 常见问题

1. **翻译不显示**
   - 检查键名是否正确
   - 验证语言文件是否正确加载
   - 确认命名空间配置

2. **语言切换失效**
   - 检查 localStorage 权限
   - 验证语言代码是否正确
   - 确认组件状态更新

3. **格式化异常**
   - 检查 Intl API 支持
   - 验证格式化参数
   - 确认地区代码正确性

#### 调试工具

```typescript
// 开启调试模式
i18n.init({
  debug: true,
  // 其他配置...
})

// 手动检查翻译
console.log(i18n.t('home:title'))
console.log(i18n.language)
console.log(i18n.options.resources)
```

## 附录

### 相关资源

- [react-i18next 官方文档](https://react.i18next.com/)
- [i18next 官方文档](https://www.i18next.com/)
- [MDN 国际化 API](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl)

### 最佳实践

1. **键名设计**：使用层级结构，便于管理和维护
2. **翻译质量**：确保翻译准确性和文化适应性
3. **性能考虑**：合理使用懒加载和缓存机制
4. **用户体验**：提供流畅的语言切换体验
5. **测试覆盖**：确保所有语言环境都经过测试

---

**版本**：v1.0
**更新时间**：2025-09-26
**作者**：开发团队
**审核状态**：待审核
