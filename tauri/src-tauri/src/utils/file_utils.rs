use std::path::Path;

pub struct FileUtils;

impl FileUtils {
    pub fn get_file_extension(path: &str) -> Option<String> {
        Path::new(path)
            .extension()
            .and_then(|ext| ext.to_str())
            .map(|s| s.to_lowercase())
    }

    pub fn format_file_size(bytes: u64) -> String {
        const UNITS: &[&str] = &["B", "KB", "MB", "GB"];
        let mut size = bytes as f64;
        let mut unit_index = 0;

        while size >= 1024.0 && unit_index < UNITS.len() - 1 {
            size /= 1024.0;
            unit_index += 1;
        }

        if unit_index == 0 {
            format!("{} {}", bytes, UNITS[unit_index])
        } else {
            format!("{:.2} {}", size, UNITS[unit_index])
        }
    }

    pub fn is_image_file(path: &str) -> bool {
        if let Some(extension) = Self::get_file_extension(path) {
            matches!(extension.as_str(),
                "jpg" | "jpeg" | "png" | "gif" | "bmp" | "tiff" | "tif" |
                "webp" | "raw" | "cr2" | "nef" | "arw" | "dng"
            )
        } else {
            false
        }
    }
}