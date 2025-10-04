export interface PhotoInfo {
  path: string;
  name: string;
  size: number;
  mime_type?: string;
  width?: number;
  height?: number;
  created_at?: string;
  modified_at?: string;
}

export interface ExifTag {
  name: string;
  value: string;
  group: string;
  description?: string;
}

export enum ExifGroup {
  Basic = "基础信息",
  Camera = "相机设置",
  Exposure = "曝光信息",
  Gps = "GPS信息",
  DateTime = "日期时间",
  Other = "其他信息"
}

export type HomeUIState =
  | { type: "empty" }
  | { type: "loading"; photoInfo: PhotoInfo }
  | { type: "success"; photoInfo: PhotoInfo; exifData: ExifTag[] }
  | { type: "error"; photoInfo?: PhotoInfo; error: string };

export interface AppState {
  currentPhoto: PhotoInfo | null;
  exifData: ExifTag[];
  uiState: HomeUIState;
  isLoading: boolean;
  error: string | null;
}