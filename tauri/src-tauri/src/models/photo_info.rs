use serde::{Deserialize, Serialize};
use crate::models::ExifTag;
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhotoInfo {
    pub path: String,
    pub name: String,
    pub size: u64,
    pub mime_type: Option<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub created_at: Option<String>,
    pub modified_at: Option<String>,
    pub exif_tags: Vec<ExifTag>,
    pub file_info: HashMap<String, String>,
    pub format_supported: bool,
    pub exif_available: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExifParseResult {
    pub photo_info: PhotoInfo,
    pub parse_status: ExifParseStatus,
    pub message: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ExifParseStatus {
    Success,
    NoExifData,
    UnsupportedFormat,
    FileError,
    ParseError,
}

impl PhotoInfo {
    pub fn new(path: String) -> Self {
        Self {
            path,
            name: "Unknown".to_string(),
            size: 0,
            mime_type: None,
            width: None,
            height: None,
            created_at: None,
            modified_at: None,
            exif_tags: Vec::new(),
            file_info: HashMap::new(),
            format_supported: false,
            exif_available: false,
        }
    }

    pub fn new_with_exif(path: String, exif_tags: Vec<ExifTag>, file_info: HashMap<String, String>) -> Self {
        let mut photo_info = Self::new(path);
        photo_info.exif_tags = exif_tags.clone();
        photo_info.file_info = file_info.clone();

        // 从file_info中提取基本信息
        if let Some(name) = file_info.get("文件名") {
            photo_info.name = name.clone();
        }

        if let Some(size_str) = file_info.get("文件大小") {
            if let Some(size_num) = size_str.split_whitespace().next() {
                if let Ok(size) = size_num.parse::<u64>() {
                    photo_info.size = size;
                }
            }
        }

        if let Some(modified) = file_info.get("修改时间") {
            photo_info.modified_at = Some(modified.clone());
        }

        // 从file_info中解析图像尺寸
        if let Some(dimensions) = file_info.get("图像尺寸") {
            if let Some((width, height)) = dimensions.split_once('x') {
                if let Ok(w) = width.trim().parse::<u32>() {
                    if let Some(h) = height.trim().split_whitespace().next() {
                        if let Ok(h_num) = h.parse::<u32>() {
                            photo_info.width = Some(w);
                            photo_info.height = Some(h_num);
                        }
                    }
                }
            }
        }

        // 判断格式是否支持EXIF
        photo_info.format_supported = true;

        // 判断是否有EXIF数据
        photo_info.exif_available = !exif_tags.is_empty();

        photo_info
    }
}

impl ExifParseResult {
    pub fn success(photo_info: PhotoInfo) -> Self {
        Self {
            photo_info,
            parse_status: ExifParseStatus::Success,
            message: None,
        }
    }

    pub fn no_exif_data(photo_info: PhotoInfo, message: String) -> Self {
        Self {
            photo_info,
            parse_status: ExifParseStatus::NoExifData,
            message: Some(message),
        }
    }

    pub fn unsupported_format(path: String, message: String) -> Self {
        let photo_info = PhotoInfo::new(path);
        Self {
            photo_info,
            parse_status: ExifParseStatus::UnsupportedFormat,
            message: Some(message),
        }
    }

    pub fn file_error(path: String, message: String) -> Self {
        let photo_info = PhotoInfo::new(path);
        Self {
            photo_info,
            parse_status: ExifParseStatus::FileError,
            message: Some(message),
        }
    }

    pub fn parse_error(path: String, message: String) -> Self {
        let photo_info = PhotoInfo::new(path);
        Self {
            photo_info,
            parse_status: ExifParseStatus::ParseError,
            message: Some(message),
        }
    }
}