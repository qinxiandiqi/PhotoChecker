import { useState, useCallback, useRef } from 'react';
import { HomeUIState, PhotoInfo } from '../types/photo';
import { parseExif } from '../utils/exifParser';

interface UsePhotoSelectorProps {
  initialPhotoState?: HomeUIState;
}

export const usePhotoSelector = ({ initialPhotoState = { type: 'empty' } }: UsePhotoSelectorProps = {}) => {
  const [photoState, setPhotoState] = useState<HomeUIState>(initialPhotoState);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileSelect = useCallback(async (file: File) => {
    setPhotoState({
      type: 'loading',
      photoInfo: {
        uri: URL.createObjectURL(file),
        readExifInfoList: [],
      },
    });

    try {
      const photoInfo = await parseExif(file);
      setPhotoState({
        type: 'success',
        photoInfo,
      });
    } catch (error) {
      setPhotoState({
        type: 'error',
        photoInfo: {
          uri: URL.createObjectURL(file),
          readExifInfoList: [],
        },
        error: error instanceof Error ? error.message : '解析照片时发生错误',
      });
    }
  }, []);

  const handleDrop = useCallback((event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const file = event.dataTransfer.files?.[0];
    if (file && file.type.startsWith('image/')) {
      handleFileSelect(file);
    }
  }, [handleFileSelect]);

  const handleDragOver = useCallback((event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
  }, []);

  const handleFileInputChange = useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && file.type.startsWith('image/')) {
      await handleFileSelect(file);
    }
  }, [handleFileSelect]);

  const triggerFileInput = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const resetPhotoState = useCallback(() => {
    setPhotoState({ type: 'empty' });
  }, []);

  return {
    photoState,
    fileInputRef,
    handleFileSelect,
    handleDrop,
    handleDragOver,
    handleFileInputChange,
    triggerFileInput,
    resetPhotoState,
  };
};

// 响应式图片预览hook
export const useImagePreview = (uri: string | null) => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadPreview = useCallback(async () => {
    if (!uri) return;

    setIsLoading(true);
    setError(null);

    try {
      // 这里可以添加图片预览的加载逻辑
      // 例如：预加载图片，检查格式等
      await new Promise((resolve) => {
        const img = new Image();
        img.onload = resolve;
        img.onerror = () => {
          setError('图片加载失败');
          resolve();
        };
        img.src = uri;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : '图片加载失败');
    } finally {
      setIsLoading(false);
    }
  }, [uri]);

  return {
    isLoading,
    error,
    loadPreview,
  };
};

// 图片尺寸计算hook
export const useImageDimensions = () => {
  const [dimensions, setDimensions] = useState<{ width: number; height: number } | null>(null);
  const [aspectRatio, setAspectRatio] = useState<number>(1);

  const calculateDimensions = useCallback((width: number, height: number) => {
    setDimensions({ width, height });
    setAspectRatio(width / height);
  }, []);

  const calculateResponsiveDimensions = useCallback (
    (containerWidth: number, maxHeight: number = 400) => {
      if (!dimensions) return { width: containerWidth, height: containerWidth };

      const aspectRatio = dimensions.width / dimensions.height;
      let calculatedWidth = containerWidth;
      let calculatedHeight = calculatedWidth / aspectRatio;

      if (calculatedHeight > maxHeight) {
        calculatedHeight = maxHeight;
        calculatedWidth = calculatedHeight * aspectRatio;
      }

      return {
        width: Math.round(calculatedWidth),
        height: Math.round(calculatedHeight),
      };
    },
    [dimensions]
  );

  return {
    dimensions,
    aspectRatio,
    calculateDimensions,
    calculateResponsiveDimensions,
  };
};