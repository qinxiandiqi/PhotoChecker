import { invoke } from "@tauri-apps/api/core";
import { PhotoInfo, ExifTag } from "../types";

export class PhotoService {
  static async selectPhoto(): Promise<string | null> {
    try {
      // 注意：select_photo命令现在需要AppHandle参数，但Tauri会自动处理
      return await invoke<string>("select_photo");
    } catch (error) {
      console.error("选择照片失败:", error);
      throw error;
    }
  }

  static async getPhotoInfo(path: string): Promise<PhotoInfo> {
    try {
      return await invoke<PhotoInfo>("read_photo_info", { path });
    } catch (error) {
      console.error("读取照片信息失败:", error);
      throw error;
    }
  }

  static async parseExifData(path: string): Promise<ExifTag[]> {
    try {
      return await invoke<ExifTag[]>("parse_exif_data", { path });
    } catch (error) {
      console.error("解析EXIF数据失败:", error);
      throw error;
    }
  }

  static async getSupportedFormats(): Promise<string[]> {
    try {
      return await invoke<string[]>("get_supported_formats");
    } catch (error) {
      console.error("获取支持的格式失败:", error);
      throw error;
    }
  }

  static async validateImageFile(path: string): Promise<boolean> {
    try {
      return await invoke<boolean>("validate_image_file", { path });
    } catch (error) {
      console.error("验证图片文件失败:", error);
      return false;
    }
  }

  static async getFilePreview(path: string): Promise<string> {
    try {
      return await invoke<string>("get_file_preview", { path });
    } catch (error) {
      console.error("获取文件预览失败:", error);
      throw error;
    }
  }

  static async handleFileDrop(files: File[]): Promise<string | null> {
    try {
      for (const file of files) {
        if (file.type.startsWith('image/')) {
          // 对于拖拽的文件，我们需要获取其路径
          // 在Tauri中，拖拽的文件可能需要特殊处理
          // 这里先用一个简单的方法
          return file.name; // 临时返回文件名，后续需要改进
        }
      }
      return null;
    } catch (error) {
      console.error("处理拖拽文件失败:", error);
      return null;
    }
  }

  static formatFileSize(bytes: number): string {
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    if (unitIndex === 0) {
      return `${bytes} ${units[unitIndex]}`;
    } else {
      return `${size.toFixed(2)} ${units[unitIndex]}`;
    }
  }

  static async exportExifData(path: string, format: 'json' | 'csv'): Promise<string> {
    try {
      return await invoke<string>("export_exif_data", { path, format });
    } catch (error) {
      console.error("导出EXIF数据失败:", error);
      throw error;
    }
  }

  static async validateExifData(path: string): Promise<boolean> {
    try {
      return await invoke<boolean>("validate_exif_data", { path });
    } catch (error) {
      console.error("验证EXIF数据失败:", error);
      return false;
    }
  }

  static downloadFile(content: string, filename: string, mimeType: string = 'text/plain') {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  static formatExifValue(value: string, tagName: string): string {
    // 格式化特定的EXIF值
    switch (tagName) {
      case '曝光时间':
        // 将分数格式化为更易读的形式
        if (value.includes('/')) {
          const [num, den] = value.split('/').map(Number);
          if (den && num < den) {
            return `1/${Math.round(den / num)}`;
          }
        }
        return value;

      case '光圈值':
      case 'F值':
        // 格式化光圈值
        if (value.includes('/')) {
          const [num, den] = value.split('/').map(Number);
          if (den && num / den > 0) {
            return `f/${(num / den).toFixed(1)}`;
          }
        }
        return value.startsWith('f/') ? value : `f/${value}`;

      case '焦距':
        // 格式化焦距
        if (!value.endsWith('mm')) {
          return `${value}mm`;
        }
        return value;

      case 'ISO感光度':
        return `ISO ${value}`;

      case '拍摄时间':
      case '原始拍摄时间':
      case '数字化时间':
        // 尝试格式化日期时间
        try {
          const date = new Date(value.replace(/:/g, '-', 2));
          if (!isNaN(date.getTime())) {
            return date.toLocaleString('zh-CN');
          }
        } catch {
          // 如果格式化失败，返回原始值
        }
        return value;

      case 'GPS纬度':
      case 'GPS经度':
        // 格式化GPS坐标
        try {
          if (value.includes(',')) {
            const [degrees, minutes, seconds] = value.split(',').map(Number);
            const decimal = degrees + minutes / 60 + seconds / 3600;
            return `${decimal.toFixed(6)}°`;
          }
        } catch {
          // 如果格式化失败，返回原始值
        }
        return value;

      default:
        return value;
    }
  }
}