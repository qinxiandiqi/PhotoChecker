use crate::services::ExifService;
use tauri::command;

#[command]
pub async fn parse_exif_data(path: String) -> Result<Vec<crate::models::ExifTag>, String> {
    let exif_service = ExifService::new();
    exif_service.parse_exif_data(&path).await
}

#[command]
pub async fn get_supported_formats() -> Result<Vec<String>, String> {
    let exif_service = ExifService::new();
    let formats = exif_service.get_supported_formats();
    Ok(formats.into_iter().map(|s| s.to_string()).collect())
}

#[command]
pub async fn export_exif_data(path: String, format: String) -> Result<String, String> {
    let exif_service = ExifService::new();
    let exif_data = exif_service.parse_exif_data(&path).await?;

    match format.as_str() {
        "json" => {
            let json = serde_json::to_string_pretty(&exif_data)
                .map_err(|e| format!("序列化JSON失败: {}", e))?;
            Ok(json)
        }
        "csv" => {
            let mut csv = "Tag Name,Value,Group\n".to_string();
            for tag in exif_data {
                csv.push_str(&format!("{},{},{}\n", tag.name, tag.value, tag.group));
            }
            Ok(csv)
        }
        _ => Err("不支持的导出格式".to_string()),
    }
}

#[command]
pub async fn validate_exif_data(path: String) -> Result<bool, String> {
    let exif_service = ExifService::new();
    match exif_service.parse_exif_data(&path).await {
        Ok(tags) => {
            // 检查是否有有效的EXIF数据（除了提示信息）
            let has_valid_exif = tags.iter().any(|tag| tag.name != "提示");
            Ok(has_valid_exif)
        }
        Err(_) => Ok(false),
    }
}