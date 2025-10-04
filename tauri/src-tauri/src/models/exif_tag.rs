use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExifTag {
    pub name: String,
    pub value: String,
    pub group: String,
    pub description: Option<String>,
}

impl ExifTag {
    pub fn new(name: String, value: String, group: String) -> Self {
        Self {
            name,
            value,
            group,
            description: None,
        }
    }

    pub fn with_description(mut self, description: String) -> Self {
        self.description = Some(description);
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ExifGroup {
    Basic,      // 基础信息
    Camera,     // 相机设置
    Exposure,   // 曝光信息
    Gps,        // GPS信息
    DateTime,   // 日期时间
    Other,      // 其他信息
}

impl ExifGroup {
    pub fn as_str(&self) -> &'static str {
        match self {
            ExifGroup::Basic => "基础信息",
            ExifGroup::Camera => "相机设置",
            ExifGroup::Exposure => "曝光信息",
            ExifGroup::Gps => "GPS信息",
            ExifGroup::DateTime => "日期时间",
            ExifGroup::Other => "其他信息",
        }
    }
}