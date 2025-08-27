export interface ExifTag {
  name: string;
  value: string;
}

export interface PhotoInfo {
  uri: string;
  readExifInfoList: ExifTag[];
}

export interface HomeUIState {
  type: 'empty' | 'loading' | 'success' | 'error';
  photoInfo?: PhotoInfo;
  error?: string;
}

export interface AppState {
  currentRoute: string;
  photoState: HomeUIState;
}