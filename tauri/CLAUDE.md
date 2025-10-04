# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Tauri 2 + React + TypeScript template project designed to serve as a foundation for multiple desktop applications. The project features a flat structure for simplicity and includes comprehensive code quality tools and Git commit conventions.

## Project Structure

```
[project-name]/
├── src/                    # React frontend source code
│   ├── components/         # Reusable React components
│   │   └── theme-toggle.tsx # Theme switching component
│   └── assets/            # Static assets
├── src-tauri/              # Tauri backend (Rust)
│   ├── src/               # Rust source code
│   ├── capabilities/       # Tauri capabilities
│   ├── icons/             # Application icons
│   └── target/            # Rust build artifacts
├── public/                 # Static assets
│   ├── tauri.svg          # Tauri icon
│   └── vite.svg           # Vite icon
├── .husky/                 # Git hooks (auto-installed)
├── .vscode/                # VS Code configuration
├── package.json            # Project dependencies and scripts
├── index.html             # HTML entry point
├── README.md              # Project documentation
├── CLAUDE.md              # Claude AI assistance guide
├── COMMIT_GUIDE.md        # Git commit conventions guide
├── .mcp.json              # Claude Code MCP server configuration
├── .gitignore             # Git ignore rules
├── .editorconfig          # Editor configuration
├── .prettierrc            # Prettier code formatting
├── .prettierignore        # Prettier ignore rules
├── eslint.config.js       # ESLint configuration
├── commitlint.config.js   # Commit message validation
├── cz-config.js           # Commitizen configuration
├── tailwind.config.js     # Tailwind CSS configuration
├── postcss.config.js      # PostCSS configuration
├── tsconfig.json          # TypeScript configuration
├── tsconfig.node.json     # TypeScript Node configuration
├── vite.config.ts         # Vite build configuration
└── pnpm-lock.yaml         # Dependency lock file
```

## Development Commands

### Primary Development Workflow

**Direct pnpm commands:**

```bash
# Install dependencies (auto-installs Git hooks)
pnpm install

# Development
pnpm tauri dev

# Build
pnpm tauri build

# Type checking
pnpm typecheck

# Lint and format
pnpm lint
pnpm format

# Commit with conventional format
pnpm commit

# Release new version
pnpm release
```

## Architecture Overview

### Frontend (React + TypeScript)

- **Framework**: React 19.1.1 with TypeScript 5.8.3
- **Build Tool**: Vite 7.0.4
- **Location**: `src/`
- **Entry Point**: `src/main.tsx` → `src/App.tsx`
- **Styling**: Tailwind CSS v3 with daisyUI component library in `src/index.css`
- **UI Components**: daisyUI - pre-built components with semantic class names
- **Theme System**: Built-in dark/light mode with daisyUI theme system

### Backend (Tauri + Rust)

- **Framework**: Tauri 2.0.0
- **Language**: Rust 2021 edition
- **Location**: `src-tauri/`
- **Entry Point**: `src-tauri/src/main.rs` → `src-tauri/src/lib.rs`
- **Commands**: Defined in `lib.rs` with `#[tauri::command]` macro

### Project Configuration

- **Package Manager**: pnpm (required)
- **Node.js**: v22.19.0 LTS (managed via nvm)
- **Frontend Dist**: `dist/`
- **Tauri Config**: `src-tauri/tauri.conf.json`
- **Git Hooks**: Auto-installed via `prepare` script

## Key Development Patterns

### Tauri Command Pattern

Commands are defined in Rust with the `#[tauri::command]` macro and registered in the `invoke_handler`. Example from `template/src-tauri/src/lib.rs`:

```rust
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}

// Register in builder
.invoke_handler(tauri::generate_handler![greet])
```

### Frontend-Backend Communication

React components call Rust commands using the `invoke` function:

```typescript
import { invoke } from '@tauri-apps/api/core'

const result = await invoke('greet', { name: 'World' })
```

## Environment Requirements

### Development Environment

- **Node.js**: v22.19.0 LTS (via nvm)
- **pnpm**: v10.15.1 (package manager)
- **Rust**: 1.89.0 with cargo
- **WSL2**: Required for Windows development with GUI support

### System Dependencies (WSL2/Linux)

- `libwebkit2gtk-4.1-dev`
- `build-essential`
- `libxdo-dev`
- `libssl-dev`
- `libayatana-appindicator3-dev`
- `librsvg2-dev`

## Important Notes

- **Always use pnpm** - this is the mandated package manager for all projects
- **WSL2 GUI support** may be required for Windows development environments

## daisyUI + Tailwind CSS Implementation

### Critical Configuration Requirements

**daisyUI Configuration**:

- Uses `tailwind.config.js` file with daisyUI plugin
- PostCSS configuration uses `tailwindcss` and `autoprefixer` plugins
- CSS structure uses `@tailwind` directives

**Configuration Files**:

- `tailwind.config.js` - Main configuration with daisyUI plugin and themes
- `postcss.config.js` - PostCSS plugin configuration
- `src/index.css` - Custom styles and utilities

**daisyUI Setup in `tailwind.config.js`**:

```javascript
export default {
  plugins: [require('daisyui')],
  daisyui: {
    themes: ['light', 'dark'],
    darkTheme: 'dark',
    themeRoot: ':root',
  },
}
```

### daisyUI Component System

**Built-in Components**:

- **Buttons**: `btn`, `btn-primary`, `btn-secondary`, `btn-outline`, `btn-ghost`
- **Cards**: `card`, `card-body`, `card-title`, `card-actions`
- **Inputs**: `input`, `input-bordered`, `input-primary`
- **Forms**: `form-control`, `label`, `label-text`
- **Alerts**: `alert`, `alert-info`, `alert-success`, `alert-warning`, `alert-error`
- **Layout**: `hero`, `navbar`, `footer`, `divider`

**Key Benefits**:

- Pre-built, accessible components with consistent styling
- No need for custom React component wrappers
- Semantic class names that are easy to understand
- Built-in dark mode support
- Responsive design out of the box

### Theme System

**daisyUI Themes**:

- Light and dark themes built-in
- Theme switching via `data-theme` attribute and CSS `dark` class (DaisyUI v5+)
- Consistent color tokens across themes
- Easy theme customization

**Theme Implementation**:

```javascript
// Theme switching in components (DaisyUI 5.x)
const toggleTheme = () => {
  const html = document.documentElement
  if (isDark) {
    html.classList.add('dark')
    html.setAttribute('data-theme', 'dark')
  } else {
    html.classList.remove('dark')
    html.setAttribute('data-theme', 'light')
  }
  localStorage.setItem('theme', isDark ? 'dark' : 'light')
}
```

**Theme Controller**:

```typescript
// Theme toggle button with DaisyUI 5.x theme-controller
<button className="btn btn-ghost btn-circle theme-controller" aria-label="切换主题">
  {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
</button>
```

**Features**:

- System preference detection via `prefers-color-scheme`
- localStorage persistence
- Automatic theme initialization
- Smooth theme transitions

### Component Usage Patterns

**Button Examples**:

```html
<button className="btn btn-primary">Primary Button</button>
<button className="btn btn-outline">Outline Button</button>
<button className="btn btn-ghost">Ghost Button</button>
```

**Card Examples**:

```html
<div className="card bg-base-100 shadow-xl">
  <div className="card-body">
    <h2 className="card-title">Card Title</h2>
    <p className="text-base-content/70">Card content</p>
    <div className="card-actions">
      <button className="btn btn-primary">Action</button>
    </div>
  </div>
</div>
```

**Form Examples**:

```html
<div className="form-control">
  <label className="label">
    <span className="label-text">Username</span>
  </label>
  <input type="text" className="input input-bordered" />
</div>
```

### Color System

**daisyUI Color Tokens**:

- `primary` - Primary brand color
- `secondary` - Secondary color
- `accent` - Accent color for highlights
- `neutral` - Neutral colors for text and borders
- `base-100` - Background color
- `base-content` - Text color
- `info`, `success`, `warning`, `error` - Status colors

**Usage Examples**:

```html
<div className="bg-primary text-primary-content">Primary background</div>
<div className="text-base-content/70">Muted text</div>
<div className="border-neutral">Neutral border</div>
```

### Layout and Spacing

**daisyUI Layout Classes**:

- `container` - Responsive container
- `grid` - CSS Grid layouts
- `flex` - Flexbox layouts
- `divider` - Content separators
- `mockup-code` - Code display components

**Responsive Design**:

- Built-in responsive utilities
- Mobile-first approach
- Consistent breakpoint system

### Accessibility Features

**Built-in Accessibility**:

- All components follow ARIA guidelines
- Proper focus management
- Keyboard navigation support
- Screen reader compatibility

**Theme Accessibility**:

- Sufficient color contrast ratios
- Clear visual hierarchy
- Consistent focus indicators

### Performance Benefits

**Optimized CSS**:

- Tree-shaking for unused components
- Minimal CSS footprint
- Fast build times
- Optimized runtime performance

**Development Experience**:

- Rapid prototyping with pre-built components
- Consistent design system
- Easy customization and theming
- Excellent documentation

## Tauri 2.0 macOS Compatibility

### objc2 Debug Assertions Configuration

**Critical for macOS compatibility**: Tauri 2.0 requires disabling debug assertions for the `objc2` package to support older macOS versions. Add this configuration to `Cargo.toml`:

```toml
[profile.dev.package.objc2]
debug-assertions = false
```

**Why this is needed**:

- objc2 is the Rust binding to Objective-C runtime used by Tauri on macOS
- Debug assertions in objc2 can cause runtime errors on older macOS versions
- Disabling debug assertions maintains development functionality while ensuring compatibility
- This configuration does not affect production builds (release profile)

**Location**: Add this section in the root of `Cargo.toml`, typically after the `[package]` section and before or after other profile configurations.

## ESLint and Prettier Code Quality Configuration

### ESLint Configuration

The project uses ESLint for code quality and consistency. Configuration is in `template/eslint.config.js`:

**Key Plugins and Rules**:

- **JavaScript**: Base ESLint recommended rules
- **TypeScript**: @typescript-eslint plugin with recommended rules
- **React**: React and React Hooks specific rules
- **Prettier Integration**: eslint-config-prettier to avoid conflicts

**Notable Rules**:

- React JSX best practices (no React in scope, key requirements)
- TypeScript strictness (warn on explicit any, unused vars with \_ prefix)
- Custom globals for browser and Node.js APIs
- Disabled rules that conflict with modern patterns

**Commands**:

```bash
pnpm lint        # Check for issues
pnpm lint:fix    # Auto-fix issues
```

### Prettier Configuration

Prettier ensures consistent code formatting. Configuration is in `template/.prettierrc`:

**Formatting Rules**:

- No semicolons (`semi: false`)
- Single quotes (`singleQuote: true`)
- 2-space indentation (`tabWidth: 2`)
- ES5 trailing commas (`trailingComma: "es5"`)
- 100 character line length (`printWidth: 100`)
- LF line endings (`endOfLine: "lf"`)

**Commands**:

```bash
pnpm format        # Format all files
pnpm format:check  # Check if formatting is needed
```

### Development Workflow

**Recommended Process**:

1. Configure editor for format-on-save
2. Run `pnpm lint` and `pnpm format:check` before commits
3. Use `pnpm lint:fix` and `pnpm format` to fix issues

**VS Code Settings** (in `.vscode/settings.json`):

```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  }
}
```

### Package Scripts

The template includes these quality scripts in `package.json`:

- `"lint": "eslint ."`
- `"lint:fix": "eslint . --fix"`
- `"format": "prettier --write ."`
- `"format:check": "prettier --check ."`
- `"typecheck": "tsc --noEmit"`

## Git Commit Conventions

The project uses conventional commits with automated enforcement:

### Commit Format

The project supports standard conventional commit format:

1. **Basic format**: `feat: 添加新功能`
2. **With scope**: `fix(ui): 修复按钮样式`
3. **Optional emoji**: `✨feat: 添加新功能` (emoji is optional but supported)

### Supported Commit Types

| Type     | Description    |
| -------- | -------------- |
| feat     | 新功能         |
| fix      | 修复 bug       |
| docs     | 文档更新       |
| style    | 代码格式调整   |
| refactor | 重构           |
| test     | 增加测试       |
| build    | 构建相关变动   |
| ci       | CI/CD 配置变动 |
| chore    | 其他修改       |
| revert   | 回滚           |

### Usage

```bash
# Interactive commit (recommended)
pnpm commit

# Manual commit (must follow format)
git commit -m "feat: add new feature"
git commit -m "fix(auth): fix login issue"
git commit -m "docs: update API documentation"

# The prepare script automatically installs Git hooks
pnpm install
```

### Hook Configuration

- **pre-commit**: Runs lint-staged to check and format staged files
- **commit-msg**: Validates commit message format with commitlint
- **Auto-install**: Hooks are automatically installed via `prepare` script
- **Scope support**: Optional scope in parentheses (e.g., `(ui)`, `(auth)`)

### Commitlint Configuration

The project uses standard commitlint configuration:

- Follows conventional commit format: `type(scope): subject`
- Maximum header length: 100 characters
- Enforces proper commit types and formatting

### Commitizen Configuration

Custom commitizen configuration (`cz-config.js`) provides:

- Interactive prompts for commit type, scope, and message
- Optional scope field for better change tracking
- Proper formatting according to conventional commits

## Claude Code MCP Configuration

This project includes project-level MCP (Model Context Protocol) server configuration for enhanced Claude Code capabilities.

### MCP Servers Configuration

The `.mcp.json` file contains project-scoped MCP server configurations:

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["@playwright/mcp@latest"],
      "env": {}
    },
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp"],
      "env": {}
    }
  }
}
```

### Available MCP Servers

**Playwright MCP Server** (`playwright`):

- Provides browser automation and testing capabilities
- Enables web scraping, UI testing, and interaction with web pages
- Tools: `mcp__playwright__browser_*` for browser control

**Context7 MCP Server** (`context7`):

- Provides access to up-to-date library documentation
- Enables retrieving code examples and API references
- Tools: `mcp__context7__*` for documentation queries
- **API Key Required**: To enable Context7, modify `.mcp.json` to add your API key:
  ```json
  "args": ["-y", "@upstash/context7-mcp", "--api-key", "YOUR_API_KEY"]
  ```
- Get API key from: https://context7.com

### Usage

When working with this project in Claude Code, the MCP servers are automatically available:

```bash
# Check MCP server status (within Claude Code)
/mcp

# List available MCP tools
# MCP tools will be available as: mcp__playwright__* and mcp__context7__*
```

### Project-Scoped Configuration

The MCP configuration is project-scoped, meaning:

- Configuration is stored in `.mcp.json` in the project root
- Settings are checked into version control
- Available to all team members working on the project
- Claude Code will prompt for approval before using these servers

### Managing MCP Servers

```bash
# List configured servers
claude mcp list

# Add new project-scoped server
claude mcp add --scope project my-server /path/to/server

# Remove server
claude mcp remove my-server

# Reset project choices
claude mcp reset-project-choices
```

## MCP Usage Requirements and Workflow

When working with Claude Code on this project, follow these MCP usage requirements to ensure high-quality, accurate code implementation.

### Mandatory Documentation Research (Context7 MCP)

**Before implementing any code changes**, you must use the Context7 MCP to research relevant documentation:

**Documentation Research Workflow:**

1. **Identify Dependencies**: Determine which libraries/frameworks are relevant to the task
2. **Query Context7**: Use Context7 MCP tools to get up-to-date documentation
3. **Study Examples**: Review code examples and API references from official documentation
4. **Verify Best Practices**: Ensure implementation follows current best practices
5. **Proceed with Implementation**: Only start coding after thorough documentation research

**Required Research Scenarios:**

- **New Features**: Research all involved libraries before implementation
- **Bug Fixes**: Understand the expected behavior through documentation
- **Refactoring**: Verify new approaches and patterns
- **Library Updates**: Research changes in new versions
- **API Integration**: Study external API documentation

### Mandatory Web-Related Testing (Playwright MCP)

**After making any web-related changes**, you must use the Playwright MCP to verify the implementation:

**Web Testing Workflow:**

1. **Start Development Server**: Ensure the app is running (`pnpm tauri dev`)
2. **Navigate to Relevant Page**: Use browser navigation to reach the affected area
3. **Take Snapshot**: Capture the current state for visual verification
4. **Test Interactions**: Click buttons, fill forms, test functionality
5. **Verify Expected Behavior**: Confirm changes work as intended
6. **Test Edge Cases**: Verify error handling and edge cases
7. **Document Results**: Ensure testing results are documented

**Required Testing Scenarios:**

- **UI Changes**: Test visual appearance and user interactions
- **Form Modifications**: Verify form validation and submission
- **Navigation Updates**: Test routing and page transitions
- **Component Updates**: Verify component rendering and state management
- **API Integration**: Test data fetching and error handling
- **Theme Changes**: Verify dark/light mode functionality
- **Responsive Design**: Test different screen sizes

### Quality Assurance Process

**Complete MCP-Powered Development Cycle:**

1. **Planning Phase**:
   - Use Context7 to research all requirements
   - Document implementation approach
   - Identify potential pitfalls

2. **Implementation Phase**:
   - Code implementation based on documentation research
   - Follow established patterns and best practices
   - Maintain code quality standards

3. **Verification Phase**:
   - Use Playwright to test web-related changes
   - Verify functionality meets requirements
   - Test edge cases and error conditions

4. **Documentation Phase**:
   - Update relevant documentation
   - Add code comments where necessary
   - Document any breaking changes

### MCP Tool Usage Guidelines

**Context7 MCP Best Practices:**

- Always resolve library ID before getting documentation
- Use specific topics to narrow down search results
- Review multiple code examples when available
- Check for version-specific documentation
- Cross-reference information across multiple sources

**Playwright MCP Best Practices:**

- Always start from a clean browser state
- Use descriptive element references
- Take snapshots before and after changes
- Test both successful and failure scenarios
- Verify accessibility where applicable
- Clean up after testing sessions

### Example Workflow: Adding a New Feature

**Step 1: Research with Context7**

- Research React component patterns and best practices
- Study Tauri API documentation for backend integration
- Review Tailwind CSS and DaisyUI documentation for styling

**Step 2: Implementation**

- Write code based on researched documentation
- Follow established patterns
- Maintain code quality standards

**Step 3: Testing with Playwright**

```bash
# Start the development server
pnpm tauri dev

# Test the new feature
# Navigate to the application
# Take snapshots before and after changes
# Test user interactions
# Verify functionality works as expected
```

**Step 4: Verification**

- Compare before/after snapshots
- Verify all functionality works as expected
- Test edge cases and error conditions
- Update documentation as needed
