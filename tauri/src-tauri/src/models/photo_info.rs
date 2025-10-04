use serde::{Deserialize, Serialize};

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
        }
    }
}