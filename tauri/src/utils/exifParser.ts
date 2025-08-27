import { PhotoInfo, ExifTag } from '../types/photo';

// 使用exifr库进行真正的EXIF解析
export const parseExif = async (file: File): Promise<PhotoInfo> => {
  try {
    // 动态导入exifr库以避免服务端渲染问题
    const exifr = await import('exifr');
    
    // 使用exifr库解析EXIF数据
    const exifData = await exifr.parse(file);
    
    if (!exifData) {
      throw new Error('未找到EXIF数据');
    }

    // 转换为与Android兼容的格式
    const exifInfoList: ExifTag[] = [];
    
    // 常见的EXIF标签映射
    const commonTags: Record<string, string> = {
      'Make': '相机品牌',
      'Model': '相机型号',
      'DateTimeOriginal': '拍摄时间',
      'DateTime': '文件修改时间',
      'ExposureTime': '曝光时间',
      'FNumber': '光圈值',
      'ISO': 'ISO感光度',
      'FocalLength': '焦距',
      'Flash': '闪光灯',
      'WhiteBalance': '白平衡',
      'ExposureProgram': '曝光程序',
      'MeteringMode': '测光模式',
      'ResolutionUnit': '分辨率单位',
      'XResolution': '水平分辨率',
      'YResolution': '垂直分辨率',
      'Software': '软件',
      'Artist': '作者',
      'Copyright': '版权信息',
      'Orientation': '方向',
      'GPSLatitude': '纬度',
      'GPSLongitude': '经度',
      'GPSAltitude': '海拔',
      'GPSDateStamp': 'GPS日期',
    };

    // 遍历EXIF数据
    for (const [key, value] of Object.entries(exifData)) {
      if (value !== undefined && value !== null) {
        const displayName = commonTags[key] || key;
        exifInfoList.push({
          name: displayName,
          value: formatExifValue(key, value),
        });
      }
    }

    // 按照Android版本中的EXIF标签顺序排序
    exifInfoList.sort((a, b) => {
      const tagOrder = [
        'Make', 'Model', 'DateTimeOriginal', 'DateTime', 
        'ExposureTime', 'FNumber', 'ISO', 'FocalLength',
        'Flash', 'WhiteBalance', 'ExposureProgram', 'MeteringMode',
        'ResolutionUnit', 'XResolution', 'YResolution', 'Software',
        'Artist', 'Copyright', 'Orientation', 'GPSLatitude', 'GPSLongitude',
        'GPSAltitude', 'GPSDateStamp'
      ];
      
      const aIndex = tagOrder.indexOf(a.name);
      const bIndex = tagOrder.indexOf(b.name);
      
      if (aIndex === -1 && bIndex === -1) return 0;
      if (aIndex === -1) return 1;
      if (bIndex === -1) return -1;
      return aIndex - bIndex;
    });

    // 创建PhotoInfo对象
    const photoInfo: PhotoInfo = {
      uri: URL.createObjectURL(file),
      readExifInfoList: exifInfoList,
    };

    return photoInfo;
  } catch (error) {
    throw new Error(`解析EXIF数据失败: ${error instanceof Error ? error.message : '未知错误'}`);
  }
};

// 格式化EXIF值
const formatExifValue = (key: string, value: any): string => {
  if (value === undefined || value === null) {
    return '未知';
  }

  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }

  if (typeof value === 'number') {
    // 特殊处理某些数值
    switch (key) {
      case 'ExposureTime':
        return `1/${Math.round(1 / value)}`;
      case 'FNumber':
        return `f/${value}`;
      case 'FocalLength':
        return `${value}mm`;
      case 'ISO':
        return `ISO ${value}`;
      case 'GPSLatitude':
      case 'GPSLongitude':
        if (Array.isArray(value)) {
          return value.map(v => typeof v === 'number' ? v.toFixed(6) : v).join(', ');
        }
        return typeof value === 'number' ? value.toFixed(6) : String(value);
      default:
        return String(value);
    }
  }

  if (Array.isArray(value)) {
    return value.map(v => String(v)).join(', ');
  }

  return String(value);
};

// 从文件对象获取EXIF数据的辅助函数
export const getExifFromFile = async (file: File) => {
  const exifr = await import('exifr');
  return exifr.parse(file);
};