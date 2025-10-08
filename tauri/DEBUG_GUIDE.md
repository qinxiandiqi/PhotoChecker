# PhotoChecker Tauri应用调试指南

## 概述

本指南提供调试Tauri应用选择照片功能问题的详细步骤和方法。

## 应用架构

### 前端 (React + TypeScript)

- **入口**: `src/App.tsx`
- **主页组件**: `src/components/home/HomeScreen.tsx`
- **选择器组件**: `src/components/home/PhotoSelector.tsx`
- **Hook**: `src/hooks/usePhotoSelector.ts`
- **API服务**: `src/services/api.ts`

### 后端 (Tauri + Rust)

- **主入口**: `src-tauri/src/lib.rs`
- **照片命令**: `src-tauri/src/commands/photo.rs`
- **EXIF命令**: `src-tauri/src/commands/exif.rs`
- **EXIF服务**: `src-tauri/src/services/exif_service.rs`

## 常见问题排查

### 1. 选择照片按钮无响应

**可能原因**:

- Tauri API不可用
- 事件监听器未正确绑定
- 组件状态异常

**调试步骤**:

1. **检查浏览器控制台**:

   ```javascript
   // 在浏览器控制台中检查
   console.log('Tauri API:', window.__TAURI__)
   console.log('当前状态:', document.querySelector('[data-testid="app-state"]'))
   ```

2. **检查按钮元素**:

   ```javascript
   // 查找选择照片按钮
   const buttons = document.querySelectorAll('button')
   buttons.forEach((btn, index) => {
     console.log(`按钮 ${index}:`, btn.textContent, btn.onclick)
   })
   ```

3. **手动触发选择功能**:
   ```javascript
   // 在控制台中手动调用
   if (window.photoCheckerApp && window.photoCheckerApp.selectPhoto) {
     window.photoCheckerApp.selectPhoto()
   }
   ```

### 2. 文件选择对话框不显示

**可能原因**:

- `tauri-plugin-dialog`未正确初始化
- 权限配置问题
- 命令注册错误

**调试步骤**:

1. **检查插件初始化**:

   ```rust
   // 确认在 lib.rs 中有以下代码
   .plugin(tauri_plugin_dialog::init())
   ```

2. **检查命令注册**:

   ```rust
   // 确认 select_photo 命令已注册
   .invoke_handler(tauri::generate_handler![
       select_photo,
       // ... 其他命令
   ])
   ```

3. **检查Tauri配置**:
   ```json
   // 检查 tauri.conf.json 中的权限配置
   "permissions": [
     "core:default",
     "dialog:default"
   ]
   ```

### 3. EXIF数据解析失败

**可能原因**:

- 文件格式不支持
- 文件路径问题
- EXIF库错误

**调试步骤**:

1. **检查文件路径**:

   ```rust
   // 在 photo.rs 中添加调试信息
   println!("选择的文件路径: {:?}", file_path);
   ```

2. **检查文件有效性**:

   ```rust
   // 检查文件是否存在且可读
   if !std::path::Path::new(&path_str).exists() {
       return Err("文件不存在".to_string());
   }
   ```

3. **检查EXIF解析**:
   ```rust
   // 在 exif_service.rs 中添加详细错误信息
   match exif::Reader::new().read_from_file(&file_path) {
       Ok(exif) => {
           println!("成功读取EXIF数据");
           // 继续处理...
       }
       Err(e) => {
           println!("EXIF读取失败: {:?}", e);
           return Err(format!("EXIF解析失败: {}", e));
       }
   }
   ```

## 开发环境调试

### 1. 启动开发模式

```bash
cd tauri
pnpm tauri dev
```

### 2. 检查开发服务器

```bash
# 确认服务器运行在 http://localhost:5173
curl http://localhost:5173
```

### 3. 使用浏览器开发者工具

1. **打开开发者工具**: F12 或右键 > 检查
2. **查看控制台**: 检查JavaScript错误
3. **查看网络**: 检查API调用
4. **查看元素**: 检查DOM结构

### 4. Tauri开发者工具

1. **启用开发者模式**:

   ```json
   // 在 tauri.conf.json 中
   "app": {
     "windows": [{
       "devtools": true
     }]
   }
   ```

2. **打开开发者工具**: 在Tauri应用中按 F12

## 测试工具

### 1. 手动测试页面

使用提供的 `test_manual.html` 文件进行功能测试。

### 2. 单元测试

```bash
# 运行Rust测试
cd src-tauri
cargo test

# 运行前端测试
cd ..
pnpm test
```

### 3. 集成测试

创建端到端测试来验证完整流程。

## 日志调试

### 1. 前端日志

```typescript
// 在组件中添加详细日志
const selectPhoto = useCallback(async () => {
  console.log('开始选择照片...')
  try {
    setIsLoading(true)
    console.log('调用PhotoService.selectPhoto()')
    const path = await PhotoService.selectPhoto()
    console.log('选择的路径:', path)
    // ... 继续处理
  } catch (error) {
    console.error('选择照片失败:', error)
    // ... 错误处理
  }
}, [])
```

### 2. 后端日志

```rust
// 在Rust代码中添加日志
use log::{info, warn, error};

#[command]
pub async fn select_photo(app: AppHandle) -> Result<Option<String>, String> {
    info!("select_photo 命令被调用");

    let file_path = app.dialog()
        .file()
        .add_filter("图片文件", &["jpg", "jpeg", "png"])
        .blocking_pick_file();

    match file_path {
        Some(path) => {
            info!("用户选择了文件: {:?}", path);
            // ... 继续处理
        }
        None => {
            info!("用户取消了文件选择");
            Ok(None)
        }
    }
}
```

## 常见错误及解决方案

### 1. "command not found" 错误

**原因**: 命令未在 `invoke_handler` 中注册
**解决**: 在 `lib.rs` 中添加命令到 `invoke_handler`

### 2. "Permission denied" 错误

**原因**: 权限配置问题
**解决**: 在 `tauri.conf.json` 中添加必要的权限

### 3. "File not found" 错误

**原因**: 文件路径问题或文件不存在
**解决**: 检查文件路径格式和文件存在性

### 4. "EXIF parsing failed" 错误

**原因**: 文件格式不支持或文件损坏
**解决**: 添加文件验证和错误处理

## 性能优化建议

1. **异步处理**: 使用 `async/await` 避免阻塞UI
2. **错误处理**: 添加详细的错误信息
3. **加载状态**: 显示加载指示器
4. **文件验证**: 在处理前验证文件
5. **内存管理**: 及时释放大文件的内存

## 部署检查清单

- [ ] 所有Tauri命令已注册
- [ ] 权限配置正确
- [ ] 错误处理完善
- [ ] 日志记录适当
- [ ] 测试覆盖完整
- [ ] 性能优化完成

## 联系支持

如果问题仍然存在，请提供以下信息：

1. 错误消息和堆栈跟踪
2. 操作系统和版本
3. Tauri版本
4. 重现步骤
5. 相关日志文件
