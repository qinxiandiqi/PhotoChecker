mod commands;
mod models;
mod services;
mod utils;

use commands::{
    select_photo,
    read_photo_info,
    validate_image_file,
    get_supported_formats,
    get_file_preview,
    parse_exif_data,
    export_exif_data,
    validate_exif_data
};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .invoke_handler(tauri::generate_handler![
            select_photo,
            read_photo_info,
            validate_image_file,
            get_supported_formats,
            get_file_preview,
            parse_exif_data,
            export_exif_data,
            validate_exif_data
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
