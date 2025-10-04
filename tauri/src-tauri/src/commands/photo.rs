use crate::{models::PhotoInfo, services::PhotoService};
use tauri::{command, AppHandle};

#[command]
pub async fn select_photo(app: AppHandle) -> Result<Option<String>, String> {
    use tauri_plugin_dialog::{DialogExt, MessageDialogKind};

    // 获取支持的文件格式
    let photo_service = PhotoService::new();
    let formats = photo_service.get_supported_formats();
    let filters = vec![
        ("图片文件".to_string(), formats.iter().map(|f| format!("*.{}", f)).collect()),
        ("所有文件".to_string(), vec!["*.*".to_string()]),
    ];

    // 打开文件选择对话框
    let file_path = app
        .dialog()
        .file()
        .add_filter("图片文件", &["jpg", "jpeg", "png", "tiff", "tif", "raw", "cr2", "nef", "arw", "dng"])
        .add_filter("所有文件", &["*"])
        .blocking_pick_file();

    match file_path {
        Some(path) => {
            // 验证是否为支持的图片文件
            let photo_service = PhotoService::new();
            let path_str = path.to_string();
            if photo_service.is_supported_image(&path_str) {
                Ok(Some(path_str.to_string()))
            } else {
                // 显示错误消息
                app.dialog()
                    .message("不支持的文件格式")
                    .kind(MessageDialogKind::Error)
                    .blocking_show();
                Err("不支持的文件格式".to_string())
            }
        }
        None => Ok(None), // 用户取消了选择
    }
}

#[command]
pub async fn read_photo_info(path: String) -> Result<PhotoInfo, String> {
    let photo_service = PhotoService::new();
    photo_service.get_photo_info(&path).await
}

#[command]
pub async fn validate_image_file(path: String) -> Result<bool, String> {
    let photo_service = PhotoService::new();
    Ok(photo_service.is_supported_image(&path))
}

#[command]
pub async fn get_file_preview(path: String) -> Result<String, String> {
    // 对于本地文件，我们直接返回文件路径，让前端处理
    if std::path::Path::new(&path).exists() {
        Ok(format!("file://{}", path))
    } else {
        Err("文件不存在".to_string())
    }
}