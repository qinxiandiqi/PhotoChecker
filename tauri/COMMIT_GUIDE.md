# Git 提交规范指南

## 概述

项目使用 Commitizen + commitlint 来确保 Git 提交信息的规范性和一致性。这有助于：

- 自动生成清晰的变更日志（Changelog）
- 快速定位问题提交
- 便于版本管理和发布
- 提高团队协作效率

## 提交类型

提交信息必须遵循以下类型之一：

| 类型       | 描述           | 示例                                           |
| ---------- | -------------- | ---------------------------------------------- |
| `feat`     | 新功能         | feat(auth): add OAuth2 login                   |
| `fix`      | 修复 bug       | fix(button): prevent double click              |
| `docs`     | 文档更新       | docs(readme): update installation steps        |
| `style`    | 代码格式调整   | style(button): adjust padding                  |
| `refactor` | 重构代码       | refactor(utils): simplify validation logic     |
| `test`     | 添加或修改测试 | test(auth): add login unit tests               |
| `build`    | 构建相关       | build(deps): update react version              |
| `ci`       | CI/CD 配置     | ci(github): add workflow for PR                |
| `chore`    | 其他修改       | chore: remove unused files                     |
| `revert`   | 回滚提交       | revert: feat(api) remove experimental endpoint |

## 提交格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 各部分说明

1. **type**: 必填，提交类型
2. **scope**: 可选，影响范围（如组件名、文件名）
3. **subject**: 必填，简短描述，使用命令式语气
4. **body**: 可选，详细描述
5. **footer**: 可选， Breaking Changes 或 Issue 引用

### 示例

```
feat(auth): add OAuth2 authentication

Implemented OAuth2 authentication flow with Google and GitHub providers.
Added login button, auth context, and protected routes.

BREAKING CHANGE: Removed old session-based authentication.
Closes #123, #125
```

## 使用方法

### 1. 使用 Commitizen（推荐）

```bash
# 交互式提交
pnpm commit

# 或使用 npx
npx cz
```

### 2. VS Code 中使用

1. 安装推荐的 VS Code 扩展
2. 使用 GitLens 的 "Commitizen Commit" 按钮
3. 或按 `Ctrl+Shift+P` 执行 "Commitizen: Commit"

### 3. 命令行直接提交（不推荐）

```bash
git commit -m "feat(auth): add login functionality"
```

## Git Hooks

项目配置了以下 Git Hooks：

### pre-commit

提交前自动执行：

- ESLint 检查并修复代码问题
- Prettier 格式化代码

### commit-msg

校验提交信息格式是否符合规范，不符合规范的提交将被拒绝。

## 版本发布

使用 standard-version 自动管理版本和生成 changelog：

```bash
# 自动升级版本（根据提交类型）
pnpm release

# 指定版本类型
pnpm release:patch   # 0.1.0 → 0.1.1
pnpm release:minor  # 0.1.0 → 0.2.0
pnpm release:major  # 0.1.0 → 1.0.0

# 预览（不实际修改）
pnpm release --dry-run
```

## 最佳实践

1. **及时提交**：小而频繁的提交，每个提交只做一件事
2. **清晰的描述**：使用命令式语气，如 "add feature" 而不是 "added feature"
3. **详细说明**：复杂的变更在 body 中提供详细说明
4. **破坏性变更**：明确标记 BREAKING CHANGE
5. **关联 Issue**：使用 `Closes #123` 或 `Fixes #123` 关闭相关 Issue

## 常见错误

### 1. 提交信息格式错误

```
ERROR: subject may not be empty
ERROR: type may not be empty
ERROR: scope must be lowercase
```

**解决**：使用 `pnpm commit` 确保格式正确

### 2. pre-commit 检查失败

```
ERROR: ESLint found errors
ERROR: Prettier found unformatted files
```

**解决**：

```bash
# 修复 ESLint 问题
pnpm lint:fix

# 格式化代码
pnpm format
```

### 3. commit-msg 校验失败

```
ERROR: must not be longer than 72 characters
ERROR: type must be one of [feat, fix, docs, ...]
```

**解决**：修改提交信息符合规范格式

## 相关资源

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Commitizen 文档](https://commitizen-tools.github.io/commitizen/)
- [commitlint 文档](https://commitlint.js.org/)
- [standard-version 文档](https://github.com/conventional-changelog/standard-version)
