use crate::models::PhotoInfo;
use crate::utils::FileUtils;
use std::path::Path;
use std::fs;
use std::time::UNIX_EPOCH;

pub struct PhotoService;

impl PhotoService {
    pub fn new() -> Self {
        Self
    }

    pub async fn get_photo_info(&self, path: &str) -> Result<PhotoInfo, String> {
        let path_obj = Path::new(path);

        if !path_obj.exists() {
            return Err("文件不存在".to_string());
        }

        let metadata = fs::metadata(path_obj)
            .map_err(|e| format!("无法读取文件元数据: {}", e))?;

        let name = path_obj
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("Unknown")
            .to_string();

        let mut photo_info = PhotoInfo::new(path.to_string());
        photo_info.name = name;
        photo_info.size = metadata.len();

        // 检测MIME类型
        photo_info.mime_type = self.detect_mime_type(path);

        // 获取图片尺寸
        if let Ok((width, height)) = self.get_image_dimensions(path) {
            photo_info.width = Some(width);
            photo_info.height = Some(height);
        }

        // 获取文件时间信息
        if let Ok(modified) = metadata.modified() {
            if let Ok(duration) = modified.duration_since(UNIX_EPOCH) {
                photo_info.modified_at = Some(
                    chrono::DateTime::from_timestamp(duration.as_secs() as i64, 0)
                        .map(|dt| dt.format("%Y-%m-%d %H:%M:%S").to_string())
                        .unwrap_or_else(|| "未知时间".to_string())
                );
            }
        }

        if let Ok(created) = metadata.created() {
            if let Ok(duration) = created.duration_since(UNIX_EPOCH) {
                photo_info.created_at = Some(
                    chrono::DateTime::from_timestamp(duration.as_secs() as i64, 0)
                        .map(|dt| dt.format("%Y-%m-%d %H:%M:%S").to_string())
                        .unwrap_or_else(|| "未知时间".to_string())
                );
            }
        }

        Ok(photo_info)
    }

    pub fn is_supported_image(&self, path: &str) -> bool {
        FileUtils::is_image_file(path)
    }

    pub fn get_supported_formats(&self) -> Vec<&'static str> {
        vec![
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif",
            "webp", "raw", "cr2", "nef", "arw", "dng"
        ]
    }

    fn detect_mime_type(&self, path: &str) -> Option<String> {
        let extension = FileUtils::get_file_extension(path)?;

        match extension.as_str() {
            "jpg" | "jpeg" => Some("image/jpeg".to_string()),
            "png" => Some("image/png".to_string()),
            "gif" => Some("image/gif".to_string()),
            "bmp" => Some("image/bmp".to_string()),
            "tiff" | "tif" => Some("image/tiff".to_string()),
            "webp" => Some("image/webp".to_string()),
            "raw" | "cr2" | "nef" | "arw" | "dng" => Some("image/x-raw".to_string()),
            _ => None,
        }
    }

    fn get_image_dimensions(&self, path: &str) -> Result<(u32, u32), Box<dyn std::error::Error>> {
        // 使用image crate获取图片尺寸
        let img = image::open(path)?;
        Ok((img.width(), img.height()))
    }

    pub async fn read_file_bytes(path: &str, max_size: usize) -> Result<Vec<u8>, String> {
        let metadata = fs::metadata(path)
            .map_err(|e| format!("无法读取文件元数据: {}", e))?;

        if metadata.len() > max_size as u64 {
            return Err(format!("文件过大，最大支持 {} MB", max_size / 1024 / 1024));
        }

        fs::read(path)
            .map_err(|e| format!("读取文件失败: {}", e))
    }

    pub fn format_file_size(bytes: u64) -> String {
        FileUtils::format_file_size(bytes)
    }
}