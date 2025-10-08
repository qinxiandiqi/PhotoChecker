use crate::{models::{PhotoInfo, ExifParseResult}, services::ExifService};
use tauri::{command, AppHandle};
use std::path::Path;

#[command]
pub async fn select_photo(app: AppHandle) -> Result<Option<String>, String> {
    use tauri_plugin_dialog::DialogExt;

    // 基于Android标准支持所有格式
    let file_path = app
        .dialog()
        .file()
        .add_filter("所有支持的图片", &["jpg", "jpeg", "tif", "tiff", "png", "webp", "heif", "heic", "dng", "cr2", "nef", "nrw", "arw", "rw2", "orf", "pef", "srw", "raf"])
        .add_filter("常见图片格式", &["jpg", "jpeg", "png", "webp", "tif", "tiff"])
        .add_filter("RAW格式", &["dng", "cr2", "nef", "nrw", "arw", "rw2", "orf", "pef", "srw", "raf"])
        .add_filter("现代格式", &["heif", "heic", "webp", "png"])
        .add_filter("传统格式", &["jpg", "jpeg", "tif", "tiff"])
        .add_filter("所有文件", &["*"])
        .blocking_pick_file();

    match file_path {
        Some(path) => {
            let path_str = path.to_string();
            Ok(Some(path_str))
        }
        None => Ok(None), // 用户取消了选择
    }
}

#[command]
pub async fn read_photo_info(path: String) -> Result<ExifParseResult, String> {
    let exif_service = ExifService::new();

    // 创建基本的文件信息
    let mut file_info = std::collections::HashMap::new();
    if let Some(filename) = std::path::Path::new(&path).file_name() {
        if let Some(name_str) = filename.to_str() {
            file_info.insert("文件名".to_string(), name_str.to_string());
        }
    }

    // 解析EXIF数据
    match exif_service.parse_exif_data(&path).await {
        Ok(exif_tags) => {
            let photo_info = PhotoInfo::new_with_exif(path.clone(), exif_tags.clone(), file_info);

            // 判断解析状态
            if exif_tags.is_empty() {
                Ok(ExifParseResult::no_exif_data(
                    photo_info,
                    "文件已成功读取，但未发现EXIF数据。这可能是因为该文件格式本身不支持EXIF（如某些PNG），或者图片在编辑过程中丢失了元数据。".to_string()
                ))
            } else {
                Ok(ExifParseResult::success(photo_info))
            }
        }
        Err(e) => {
            // 分析错误类型
            if e.contains("不支持的文件格式") {
                Ok(ExifParseResult::unsupported_format(
                    path,
                    e
                ))
            } else if e.contains("文件不存在") || e.contains("无法打开") || e.contains("无法读取") {
                Ok(ExifParseResult::file_error(
                    path,
                    e
                ))
            } else {
                Ok(ExifParseResult::parse_error(
                    path,
                    e
                ))
            }
        }
    }
}

#[command]
pub async fn validate_image_file(path: String) -> Result<bool, String> {
    let exif_service = ExifService::new();
    let path_obj = Path::new(&path);

    if !path_obj.exists() {
        return Ok(false);
    }

    // 检查扩展名
    if let Some(extension) = path_obj.extension().and_then(|ext| ext.to_str()) {
        let supported_formats = exif_service.get_supported_formats();
        Ok(supported_formats.contains(&extension))
    } else {
        Ok(false)
    }
}


#[command]
pub async fn get_file_preview(path: String) -> Result<String, String> {
    if Path::new(&path).exists() {
        // 使用 asset 协议来访问本地文件
        // 将绝对路径转换为 asset URL
        let normalized_path = std::fs::canonicalize(&path)
            .map_err(|e| format!("无法规范化路径: {}", e))?;

        Ok(format!("asset://localhost/{}", normalized_path.to_string_lossy()))
    } else {
        Err("文件不存在".to_string())
    }
}

#[command]
pub async fn get_supported_formats() -> Result<Vec<String>, String> {
    let exif_service = ExifService::new();
    let formats = exif_service.get_supported_formats();
    Ok(formats.iter().map(|s| s.to_string()).collect())
}