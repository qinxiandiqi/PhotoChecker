import { useState, useCallback } from "react";
import { PhotoService } from "../services/api";
import { PhotoInfo, ExifTag, HomeUIState } from "../types";

export const usePhotoSelector = () => {
  const [uiState, setUiState] = useState<HomeUIState>({ type: "empty" });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectPhoto = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);

      const path = await PhotoService.selectPhoto();
      if (!path) {
        setUiState({ type: "empty" });
        return;
      }

      // 创建临时的PhotoInfo用于loading状态
      const tempPhotoInfo: PhotoInfo = {
        path,
        name: "加载中...",
        size: 0,
      };

      setUiState({ type: "loading", photoInfo: tempPhotoInfo });

      // 并行获取照片信息和EXIF数据
      const [photoInfo, exifData] = await Promise.all([
        PhotoService.getPhotoInfo(path),
        PhotoService.parseExifData(path),
      ]);

      setUiState({ type: "success", photoInfo, exifData });
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "未知错误";
      setError(errorMessage);
      // 保持之前的photoInfo以便在错误状态下仍能显示预览
      const currentPhotoInfo = uiState.type === "loading" ? uiState.photoInfo : null;
      setUiState({
        type: "error",
        error: errorMessage,
        photoInfo: currentPhotoInfo
      });
    } finally {
      setIsLoading(false);
    }
  }, []);

  const clearPhoto = useCallback(() => {
    setUiState({ type: "empty" });
    setError(null);
  }, []);

  const retryPhoto = useCallback(() => {
    if (uiState.type === "error" || uiState.type === "success") {
      const photoPath = uiState.photoInfo?.path;
      if (photoPath) {
        selectPhoto();
      }
    }
  }, [uiState, selectPhoto]);

  return {
    uiState,
    isLoading,
    error,
    selectPhoto,
    clearPhoto,
    retryPhoto,
  };
};