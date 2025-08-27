import { PhotoInfo, ExifTag } from '../types/photo';

// 使用exifr库进行真正的EXIF解析
export const parseExif = async (file: File): Promise<PhotoInfo> => {
  try {
    // 动态导入exifr库以避免服务端渲染问题
    const exifr = await import('exifr');
    
    // 使用exifr库解析EXIF数据，包含更多标签
    const exifData = await exifr.parse(file, {
      tiff: true,
      exif: true,
      gps: true,
      interop: true,
      ifd1: true,
      icc: true,
      xmp: true,
      jfif: true,
      iptc: true,
      sanitize: false, // 不过滤字段，获取更多原始数据
    });
    
    if (!exifData) {
      throw new Error('未找到EXIF数据');
    }

    // 转换为与Android兼容的格式
    const exifInfoList: ExifTag[] = [];
    
    // 更全面的EXIF标签映射，与Android版本保持一致
    const commonTags: Record<string, string> = {
      // 基本图像信息
      'ImageWidth': '图像宽度',
      'ImageLength': '图像高度',
      'BitsPerSample': '每样本位数',
      'Compression': '压缩方式',
      'PhotometricInterpretation': '像素组成',
      'Orientation': '方向',
      'SamplesPerPixel': '每像素样本数',
      'PlanarConfiguration': '平面配置',
      'YCbCrSubSampling': 'YCbCr子采样',
      'YCbCrPositioning': 'YCbCr位置',
      'XResolution': '水平分辨率',
      'YResolution': '垂直分辨率',
      'ResolutionUnit': '分辨率单位',
      'TransferFunction': '传输函数',
      'WhitePoint': '白点',
      'PrimaryChromaticities': '主要色度',
      'YCbCrCoefficients': 'YCbCr系数',
      'ReferenceBlackWhite': '参考黑白值',
      
      // 相机信息
      'Make': '相机品牌',
      'Model': '相机型号',
      'Software': '软件',
      'Artist': '作者',
      'Copyright': '版权信息',
      
      // EXIF版本信息
      'ExifVersion': 'EXIF版本',
      'FlashpixVersion': 'Flashpix版本',
      'ColorSpace': '色彩空间',
      'Gamma': '伽马值',
      'PixelXDimension': '像素宽度',
      'PixelYDimension': '像素高度',
      'ComponentsConfiguration': '组件配置',
      'CompressedBitsPerPixel': '每像素压缩位数',
      
      // 拍摄参数
      'DateTime': '文件修改时间',
      'DateTimeOriginal': '拍摄时间',
      'DateTimeDigitized': '数字化时间',
      'OffsetTime': '时区偏移',
      'OffsetTimeOriginal': '原始时区偏移',
      'OffsetTimeDigitized': '数字化时区偏移',
      'SubsecTime': '亚秒时间',
      'SubsecTimeOriginal': '原始亚秒时间',
      'SubsecTimeDigitized': '数字化亚秒时间',
      
      // 图像描述
      'ImageDescription': '图像描述',
      'UserComment': '用户评论',
      
      // 相机设置
      'ExposureTime': '曝光时间',
      'FNumber': '光圈值',
      'ExposureProgram': '曝光程序',
      'SpectralSensitivity': '光谱灵敏度',
      'PhotographicSensitivity': '摄影灵敏度',
      'SensitivityType': '灵敏度类型',
      'StandardOutputSensitivity': '标准输出灵敏度',
      'RecommendedExposureIndex': '推荐曝光指数',
      'ISOSpeed': 'ISO速度',
      'ISOSpeedLatitudeyyy': 'ISO速度纬度yyy',
      'ISOSpeedLatitudezzz': 'ISO速度纬度zzz',
      'ShutterSpeedValue': '快门速度',
      'ApertureValue': '光圈值',
      'BrightnessValue': '亮度值',
      'ExposureBiasValue': '曝光补偿',
      'MaxApertureValue': '最大光圈',
      'SubjectDistance': '主体距离',
      'MeteringMode': '测光模式',
      'LightSource': '光源',
      'Flash': '闪光灯',
      'FocalLength': '焦距',
      'SubjectArea': '主体区域',
      'FlashEnergy': '闪光能量',
      'SpatialFrequencyResponse': '空间频率响应',
      'FocalPlaneXResolution': '焦平面X分辨率',
      'FocalPlaneYResolution': '焦平面Y分辨率',
      'FocalPlaneResolutionUnit': '焦平面分辨率单位',
      'SubjectLocation': '主体位置',
      'ExposureIndex': '曝光指数',
      'SensingMethod': '感应方式',
      'FileSource': '文件源',
      'SceneType': '场景类型',
      'CFAPattern': 'CFA模式',
      'CustomRendered': '自定义渲染',
      'ExposureMode': '曝光模式',
      'WhiteBalance': '白平衡',
      'DigitalZoomRatio': '数码变焦比率',
      'FocalLengthIn35mmFilm': '35mm胶片等效焦距',
      'SceneCaptureType': '场景捕获类型',
      'GainControl': '增益控制',
      'Contrast': '对比度',
      'Saturation': '饱和度',
      'Sharpness': '锐度',
      'DeviceSettingDescription': '设备设置描述',
      'SubjectDistanceRange': '主体距离范围',
      
      // 其他信息
      'ImageUniqueID': '图像唯一ID',
      'CameraOwnerName': '相机所有者',
      'BodySerialNumber': '机身序列号',
      'LensSpecification': '镜头规格',
      'LensMake': '镜头品牌',
      'LensModel': '镜头型号',
      'LensSerialNumber': '镜头序列号',
      
      // GPS信息
      'GPSVersionID': 'GPS版本ID',
      'GPSLatitudeRef': '纬度参考',
      'GPSLatitude': '纬度',
      'GPSLongitudeRef': '经度参考',
      'GPSLongitude': '经度',
      'GPSAltitudeRef': '海拔参考',
      'GPSAltitude': '海拔',
      'GPSTimeStamp': 'GPS时间戳',
      'GPSSatellites': 'GPS卫星',
      'GPSStatus': 'GPS状态',
      'GPSMeasureMode': 'GPS测量模式',
      'GPSDOP': 'GPS精度因子',
      'GPSSpeedRef': 'GPS速度参考',
      'GPSSpeed': 'GPS速度',
      'GPSTrackRef': 'GPS轨迹参考',
      'GPSTrack': 'GPS轨迹',
      'GPSImgDirectionRef': '图像方向参考',
      'GPSImgDirection': '图像方向',
      'GPSMapDatum': 'GPS地图基准',
      'GPSDestLatitudeRef': '目标纬度参考',
      'GPSDestLatitude': '目标纬度',
      'GPSDestLongitudeRef': '目标经度参考',
      'GPSDestLongitude': '目标经度',
      'GPSDestBearingRef': '目标方位参考',
      'GPSDestBearing': '目标方位',
      'GPSDestDistanceRef': '目标距离参考',
      'GPSDestDistance': '目标距离',
      'GPSProcessingMethod': 'GPS处理方法',
      'GPSAreaInformation': 'GPS区域信息',
      'GPSDateStamp': 'GPS日期',
      'GPSDifferential': 'GPS差分',
      'GPSHPositioningError': 'GPS水平定位误差',
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
        // 基本图像信息
        'ImageWidth', 'ImageLength', 'BitsPerSample', 'Compression', 
        'PhotometricInterpretation', 'Orientation', 'SamplesPerPixel', 'PlanarConfiguration',
        'YCbCrSubSampling', 'YCbCrPositioning', 'XResolution', 'YResolution', 'ResolutionUnit',
        
        // 相机信息
        'Make', 'Model', 'Software', 'Artist', 'Copyright',
        
        // EXIF版本信息
        'ExifVersion', 'FlashpixVersion', 'ColorSpace', 'Gamma',
        'PixelXDimension', 'PixelYDimension',
        
        // 时间信息
        'DateTime', 'DateTimeOriginal', 'DateTimeDigitized',
        'SubsecTime', 'SubsecTimeOriginal', 'SubsecTimeDigitized',
        
        // 拍摄参数
        'ExposureTime', 'FNumber', 'ExposureProgram', 'PhotographicSensitivity', 'ISO',
        'ShutterSpeedValue', 'ApertureValue', 'BrightnessValue', 'ExposureBiasValue',
        'MaxApertureValue', 'SubjectDistance', 'MeteringMode', 'LightSource', 'Flash',
        'FocalLength', 'FlashEnergy', 'FocalPlaneXResolution', 'FocalPlaneYResolution',
        'FocalPlaneResolutionUnit', 'ExposureIndex', 'SensingMethod', 'FileSource',
        'SceneType', 'CustomRendered', 'ExposureMode', 'WhiteBalance', 'DigitalZoomRatio',
        'FocalLengthIn35mmFilm', 'SceneCaptureType', 'GainControl', 'Contrast', 'Saturation',
        'Sharpness', 'SubjectDistanceRange',
        
        // GPS信息
        'GPSVersionID', 'GPSLatitudeRef', 'GPSLatitude', 'GPSLongitudeRef', 'GPSLongitude',
        'GPSAltitudeRef', 'GPSAltitude', 'GPSTimeStamp', 'GPSSatellites', 'GPSStatus',
        'GPSMeasureMode', 'GPSDOP', 'GPSSpeedRef', 'GPSSpeed', 'GPSTrackRef', 'GPSTrack',
        'GPSImgDirectionRef', 'GPSImgDirection', 'GPSMapDatum', 'GPSDestLatitudeRef',
        'GPSDestLatitude', 'GPSDestLongitudeRef', 'GPSDestLongitude', 'GPSDestBearingRef',
        'GPSDestBearing', 'GPSDestDistanceRef', 'GPSDestDistance', 'GPSProcessingMethod',
        'GPSAreaInformation', 'GPSDateStamp', 'GPSDifferential', 'GPSHPositioningError',
        
        // 其他信息
        'ImageUniqueID', 'CameraOwnerName', 'BodySerialNumber', 'LensSpecification',
        'LensMake', 'LensModel', 'LensSerialNumber',
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

// 将GPS坐标转换为十进制格式
const convertGpsToDecimal = (gps: number[], ref: string): number => {
  if (!gps || gps.length < 3) return 0;
  
  // GPS坐标格式：[度, 分, 秒]
  const degrees = gps[0] || 0;
  const minutes = gps[1] || 0;
  const seconds = gps[2] || 0;
  
  // 转换为十进制
  let decimal = degrees + minutes / 60 + seconds / 3600;
  
  // 根据参考方向调整符号
  if (ref === 'S' || ref === 'W') {
    decimal = -decimal;
  }
  
  return decimal;
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
        // 处理曝光时间
        if (value < 1) {
          return `1/${Math.round(1 / value)}`;
        } else {
          return `${value.toFixed(1)}秒`;
        }
      case 'FNumber':
        return `f/${value.toFixed(1)}`;
      case 'FocalLength':
      case 'FocalLengthIn35mmFilm':
        return `${value}mm`;
      case 'PhotographicSensitivity':
      case 'ISO':
        return `ISO ${value}`;
      case 'GPSLatitude':
      case 'GPSLongitude':
        return value.toFixed(6);
      case 'ShutterSpeedValue':
      case 'ApertureValue':
      case 'BrightnessValue':
      case 'ExposureBiasValue':
        // 这些值是APEX值，需要转换
        return value.toFixed(2);
      case 'DigitalZoomRatio':
        return value.toFixed(2) + 'x';
      case 'GPSAltitude':
        return `${value}m`;
      default:
        return String(value);
    }
  }

  if (Array.isArray(value)) {
    // 特殊处理数组值
    switch (key) {
      case 'GPSLatitude':
      case 'GPSLongitude':
        // GPS坐标格式化为十进制
        return value.map(v => typeof v === 'number' ? v.toFixed(6) : String(v)).join(', ');
      case 'SubjectArea':
      case 'SubjectLocation':
        // 坐标或区域信息
        return value.join(', ');
      default:
        return value.map(v => String(v)).join(', ');
    }
  }

  // 特殊处理字符串值
  switch (key) {
    case 'DateTime':
    case 'DateTimeOriginal':
    case 'DateTimeDigitized':
      // 格式化日期时间
      if (typeof value === 'string' && value.includes(':')) {
        // 将 YYYY:MM:DD HH:MM:SS 格式转换为更易读的格式
        return value.replace(/^(\d{4}):(\d{2}):(\d{2})\s+(\d{2}):(\d{2}):(\d{2})$/, '$1年$2月$3日 $4:$5:$6');
      }
      return String(value);
    case 'Orientation':
      // 方向值映射
      const orientationMap: Record<string, string> = {
        '1': '正常',
        '2': '水平翻转',
        '3': '旋转180度',
        '4': '垂直翻转',
        '5': '顺时针旋转90度并水平翻转',
        '6': '顺时针旋转90度',
        '7': '顺时针旋转90度并垂直翻转',
        '8': '逆时针旋转90度'
      };
      return orientationMap[String(value)] || String(value);
    case 'Flash':
      // 闪光灯状态解析
      if (typeof value === 'number') {
        const flashMap: Record<number, string> = {
          0: '未触发',
          1: '触发',
          5: '触发但无闪光检测',
          7: '触发并有闪光检测',
          8: '未触发（强制不闪光）',
          9: '触发（强制闪光）',
          13: '触发（强制闪光）但无闪光检测',
          15: '触发（强制闪光）并有闪光检测',
          16: '未触发（自动模式）',
          20: '未触发（自动模式）',
          24: '未触发（自动模式）',
          25: '触发（自动模式）',
          29: '触发（自动模式）但无闪光检测',
          31: '触发（自动模式）并有闪光检测',
          32: '无闪光功能',
          65: '无闪光功能'
        };
        return flashMap[value] || `未知状态 (${value})`;
      }
      return String(value);
    case 'WhiteBalance':
      // 白平衡映射
      if (typeof value === 'number') {
        return value === 0 ? '自动' : value === 1 ? '手动' : `未知 (${value})`;
      }
      return String(value);
    case 'MeteringMode':
      // 测光模式映射
      if (typeof value === 'number') {
        const meteringMap: Record<number, string> = {
          0: '未知',
          1: '平均测光',
          2: '中央重点测光',
          3: '点测光',
          4: '多点测光',
          5: '模式测光',
          6: '局部测光',
          255: '其他'
        };
        return meteringMap[value] || `未知 (${value})`;
      }
      return String(value);
    case 'ColorSpace':
      // 色彩空间映射
      if (typeof value === 'number') {
        return value === 1 ? 'sRGB' : value === 65535 ? '未校准' : `未知 (${value})`;
      }
      return String(value);
    case 'ExposureProgram':
      // 曝光程序映射
      if (typeof value === 'number') {
        const programMap: Record<number, string> = {
          0: '未知',
          1: '手动',
          2: '标准程序',
          3: '光圈优先',
          4: '快门优先',
          5: '创意程序（偏向景深）',
          6: '运动程序（偏向快门速度）',
          7: '肖像模式',
          8: '风景模式'
        };
        return programMap[value] || `未知 (${value})`;
      }
      return String(value);
    default:
      return String(value);
  }
};

// 从文件对象获取EXIF数据的辅助函数
export const getExifFromFile = async (file: File) => {
  const exifr = await import('exifr');
  return exifr.parse(file, {
    tiff: true,
    exif: true,
    gps: true,
    interop: true,
    ifd1: true,
    icc: true,
    xmp: true,
    jfif: true,
    iptc: true,
    sanitize: false,
  });
};