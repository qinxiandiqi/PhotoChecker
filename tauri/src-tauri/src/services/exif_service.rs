use crate::models::{ExifTag, ExifGroup};
use exif::{Tag, Value, Reader};
use std::fs::File;
use std::io::BufReader;

pub struct ExifService;

impl ExifService {
    pub fn new() -> Self {
        Self
    }

    pub async fn parse_exif_data(&self, path: &str) -> Result<Vec<ExifTag>, String> {
        let file = File::open(path)
            .map_err(|e| format!("无法打开文件: {}", e))?;

        let mut bufreader = BufReader::new(file);
        let mut buf = vec![];
        bufreader.read_to_end(&mut buf)
            .map_err(|e| format!("读取文件失败: {}", e))?;

        let reader = Reader::new()
            .read_from_container(&mut std::io::Cursor::new(buf))
            .map_err(|e| format!("解析EXIF数据失败: {}", e))?;

        let mut tags = Vec::new();

        // 获取所有EXIF字段
        for field in reader.fields() {
            let tag_name = self.get_tag_name(field.tag);
            let tag_value = self.format_tag_value(&field.value);
            let group = self.get_tag_group(field.tag);

            if !tag_value.is_empty() {
                tags.push(ExifTag::new(
                    tag_name,
                    tag_value,
                    group,
                ));
            }
        }

        // 如果没有找到EXIF数据，添加基本信息
        if tags.is_empty() {
            tags.push(ExifTag::new(
                "提示".to_string(),
                "此图片不包含EXIF信息".to_string(),
                ExifGroup::Basic.as_str().to_string(),
            ));
        }

        Ok(tags)
    }

    fn get_tag_name(&self, tag: Tag) -> String {
        match tag {
            Tag::Make => "相机制造商".to_string(),
            Tag::Model => "相机型号".to_string(),
            Tag::DateTime => "拍摄时间".to_string(),
            Tag::DateTimeOriginal => "原始拍摄时间".to_string(),
            Tag::DateTimeDigitized => "数字化时间".to_string(),
            Tag::Orientation => "方向".to_string(),
            Tag::XResolution => "X分辨率".to_string(),
            Tag::YResolution => "Y分辨率".to_string(),
            Tag::ResolutionUnit => "分辨率单位".to_string(),
            Tag::Software => "软件".to_string(),
            Tag::ExposureTime => "曝光时间".to_string(),
            Tag::FNumber => "光圈值".to_string(),
            Tag::ExposureProgram => "曝光程序".to_string(),
            Tag::SpectralSensitivity => "光谱感光度".to_string(),
            Tag::ISOSpeed => "ISO感光度".to_string(),
            Tag::OECF => "光电转换函数".to_string(),
            Tag::ExifVersion => "Exif版本".to_string(),
            Tag::DateTimeOriginal => "原始拍摄时间".to_string(),
            Tag::DateTimeDigitized => "数字化时间".to_string(),
            Tag::ComponentsConfiguration => "组件配置".to_string(),
            Tag::CompressedBitsPerPixel => "压缩位数/像素".to_string(),
            Tag::ShutterSpeedValue => "快门速度".to_string(),
            Tag::ApertureValue => "光圈值".to_string(),
            Tag::BrightnessValue => "亮度值".to_string(),
            Tag::ExposureBiasValue => "曝光补偿".to_string(),
            Tag::MaxApertureValue => "最大光圈".to_string(),
            Tag::SubjectDistance => "主体距离".to_string(),
            Tag::MeteringMode => "测光模式".to_string(),
            Tag::LightSource => "光源".to_string(),
            Tag::Flash => "闪光灯".to_string(),
            Tag::FocalLength => "焦距".to_string(),
            Tag::SubjectArea => "主体区域".to_string(),
            Tag::MakerNote => "制造商注释".to_string(),
            Tag::UserComment => "用户评论".to_string(),
            Tag::SubSecTime => "子秒时间".to_string(),
            Tag::SubSecTimeOriginal => "原始子秒时间".to_string(),
            Tag::SubSecTimeDigitized => "数字子秒时间".to_string(),
            Tag::FlashpixVersion => "Flashpix版本".to_string(),
            Tag::ColorSpace => "色彩空间".to_string(),
            Tag::PixelXDimension => "像素X维度".to_string(),
            Tag::PixelYDimension => "像素Y维度".to_string(),
            Tag::RelatedSoundFile => "相关声音文件".to_string(),
            Tag::InteroperabilityIndex => "互操作性标签".to_string(),
            Tag::FlashEnergy => "闪光灯能量".to_string(),
            Tag::SpatialFrequencyResponse => "空间频率响应".to_string(),
            Tag::FocalPlaneXResolution => "焦平面X分辨率".to_string(),
            Tag::FocalPlaneYResolution => "焦平面Y分辨率".to_string(),
            Tag::FocalPlaneResolutionUnit => "焦平面分辨率单位".to_string(),
            Tag::SubjectLocation => "主体位置".to_string(),
            Tag::ExposureIndex => "曝光指数".to_string(),
            Tag::SensingMethod => "传感方法".to_string(),
            Tag::FileSource => "文件源".to_string(),
            Tag::SceneType => "场景类型".to_string(),
            Tag::CFAPattern => "CFA模式".to_string(),
            Tag::CustomRendered => "自定义渲染".to_string(),
            Tag::ExposureMode => "曝光模式".to_string(),
            Tag::WhiteBalance => "白平衡".to_string(),
            Tag::DigitalZoomRatio => "数字变焦比".to_string(),
            Tag::FocalLengthIn35mmFilm => "35mm等效焦距".to_string(),
            Tag::SceneCaptureType => "场景捕获类型".to_string(),
            Tag::GainControl => "增益控制".to_string(),
            Tag::Contrast => "对比度".to_string(),
            Tag::Saturation => "饱和度".to_string(),
            Tag::Sharpness => "锐度".to_string(),
            Tag::DeviceSettingDescription => "设备设置描述".to_string(),
            Tag::SubjectDistanceRange => "主体距离范围".to_string(),
            Tag::ImageUniqueID => "图像唯一ID".to_string(),
            Tag::GPSVersionID => "GPS版本ID".to_string(),
            Tag::GPSLatitudeRef => "GPS纬度参考".to_string(),
            Tag::GPSLatitude => "GPS纬度".to_string(),
            Tag::GPSLongitudeRef => "GPS经度参考".to_string(),
            Tag::GPSLongitude => "GPS经度".to_string(),
            Tag::GPSAltitudeRef => "GPS高度参考".to_string(),
            Tag::GPSAltitude => "GPS高度".to_string(),
            Tag::GPSTimeStamp => "GPS时间戳".to_string(),
            Tag::GPSSatellites => "GPS卫星".to_string(),
            Tag::GPSStatus => "GPS状态".to_string(),
            Tag::GPSMeasureMode => "GPS测量模式".to_string(),
            Tag::GPSDOP => "GPS精度".to_string(),
            Tag::GPSSpeedRef => "GPS速度参考".to_string(),
            Tag::GPSSpeed => "GPS速度".to_string(),
            Tag::GPSTrackRef => "GPS轨迹参考".to_string(),
            Tag::GPSTrack => "GPS轨迹".to_string(),
            Tag::GPSImgDirectionRef => "GPS图像方向参考".to_string(),
            Tag::GPSImgDirection => "GPS图像方向".to_string(),
            Tag::GPSMapDatum => "GPS地图基准".to_string(),
            Tag::GPSDestLatitudeRef => "GPS目标纬度参考".to_string(),
            Tag::GPSDestLatitude => "GPS目标纬度".to_string(),
            Tag::GPSDestLongitudeRef => "GPS目标经度参考".to_string(),
            Tag::GPSDestLongitude => "GPS目标经度".to_string(),
            Tag::GPSDestBearingRef => "GPS目标方位参考".to_string(),
            Tag::GPSDestBearing => "GPS目标方位".to_string(),
            Tag::GPSDestDistanceRef => "GPS目标距离参考".to_string(),
            Tag::GPSDestDistance => "GPS目标距离".to_string(),
            Tag::GPSProcessingMethod => "GPS处理方法".to_string(),
            Tag::GPSAreaInformation => "GPS区域信息".to_string(),
            Tag::GPSDateStamp => "GPS日期戳".to_string(),
            Tag::GPSDifferential => "GPS差分".to_string(),
            Tag::GPSHPositioningError => "GPS水平定位误差".to_string(),
            _ => format!("{:?}", tag),
        }
    }

    fn format_tag_value(&self, value: &Value) -> String {
        match value {
            Value::Ascii(val) => {
                if let Some(s) = val.get(0) {
                    String::from_utf8_lossy(s).trim_matches('\0').to_string()
                } else {
                    String::new()
                }
            }
            Value::Short(val) => {
                if val.len() == 1 {
                    val[0].to_string()
                } else {
                    format!("{:?}", val)
                }
            }
            Value::Long(val) => {
                if val.len() == 1 {
                    val[0].to_string()
                } else {
                    format!("{:?}", val)
                }
            }
            Value::Rational(val) => {
                if val.len() == 1 {
                    format!("{}/{}", val[0].num, val[0].denom)
                } else {
                    format!("{:?}", val)
                }
            }
            Value::SRational(val) => {
                if val.len() == 1 {
                    format!("{}/{}", val[0].num, val[0].denom)
                } else {
                    format!("{:?}", val)
                }
            }
            Value::Undefined(val, _) => {
                if val.len() <= 32 {
                    format!("{:?}", val)
                } else {
                    format!("数据长度: {} bytes", val.len())
                }
            }
            _ => format!("{:?}", value),
        }
    }

    fn get_tag_group(&self, tag: Tag) -> String {
        match tag {
            // 基础信息
            Tag::Make | Tag::Model | Tag::Software | Tag::DateTime |
            Tag::DateTimeOriginal | Tag::DateTimeDigitized | Tag::ExifVersion => {
                ExifGroup::Basic.as_str().to_string()
            }

            // 相机设置
            Tag::FNumber | Tag::FocalLength | Tag::FocalLengthIn35mmFilm |
            Tag::MaxApertureValue | Tag::LensMake | Tag::LensModel |
            Tag::LensSpecification => {
                ExifGroup::Camera.as_str().to_string()
            }

            // 曝光信息
            Tag::ExposureTime | Tag::ShutterSpeedValue | Tag::ApertureValue |
            Tag::BrightnessValue | Tag::ExposureBiasValue | Tag::ExposureProgram |
            Tag::ISOSpeed | Tag::MeteringMode | Tag::Flash => {
                ExifGroup::Exposure.as_str().to_string()
            }

            // GPS信息
            Tag::GPSVersionID | Tag::GPSLatitudeRef | Tag::GPSLatitude |
            Tag::GPSLongitudeRef | Tag::GPSLongitude | Tag::GPSAltitudeRef |
            Tag::GPSAltitude | Tag::GPSTimeStamp | Tag::GPSDateStamp => {
                ExifGroup::Gps.as_str().to_string()
            }

            // 日期时间
            Tag::DateTime | Tag::DateTimeOriginal | Tag::DateTimeDigitized |
            Tag::SubSecTime | Tag::SubSecTimeOriginal | Tag::SubSecTimeDigitized => {
                ExifGroup::DateTime.as_str().to_string()
            }

            // 其他信息
            _ => ExifGroup::Other.as_str().to_string(),
        }
    }

    pub fn get_supported_formats(&self) -> Vec<&'static str> {
        vec![
            "jpg", "jpeg", "tiff", "tif"
        ]
    }
}

// 需要添加io traits到prelude
use std::io::Read;