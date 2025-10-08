import { useState, useEffect } from "react";
import { PhotoService } from "../../services/api";

declare global {
  interface Window {
    __TAURI__?: any;
  }
}

interface PhotoPreviewProps {
  photoPath: string;
  className?: string;
}

export const PhotoPreview = ({ photoPath, className = "" }: PhotoPreviewProps) => {
  const [imageError, setImageError] = useState(false);
  const [imageUrl, setImageUrl] = useState<string>("");
  const [isLoading, setIsLoading] = useState(true);
  const [isBlobUrl, setIsBlobUrl] = useState(false);

  useEffect(() => {
    const loadImagePreview = async () => {
      try {
        setIsLoading(true);
        setImageError(false);
        setIsBlobUrl(false);

        console.log("正在加载图片预览:", photoPath);

        // 使用Tauri API获取图片预览
        const previewUrl = await PhotoService.getFilePreview(photoPath);
        console.log("获取到预览URL:", previewUrl);
        setImageUrl(previewUrl);
        setIsBlobUrl(false);
      } catch (error) {
        console.error("加载图片预览失败:", error);
        console.log("Tauri环境:", !!window.__TAURI__);

        // 如果API失败，尝试读取文件为blob
        if (typeof window !== 'undefined' && window.__TAURI__) {
          try {
            const { readFile } = await import("@tauri-apps/plugin-fs");
            const contents = await readFile(photoPath);
            const blob = new Blob([contents]);
            const blobUrl = URL.createObjectURL(blob);
            setImageUrl(blobUrl);
            setIsBlobUrl(true);
            console.log("使用blob URL成功:", blobUrl);
          } catch (blobError) {
            console.error("读取文件为blob失败:", blobError);
            setImageError(true);
          }
        } else {
          console.log("在浏览器环境中，无法加载本地文件");
          setImageError(true);
        }
      } finally {
        setIsLoading(false);
      }
    };

    if (photoPath) {
      loadImagePreview();
    }

    // 清理函数，当组件卸载或photoPath改变时清理blob URL
    return () => {
      if (isBlobUrl && imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [photoPath]);

  if (imageError) {
    return (
      <div className={`bg-base-200 rounded-lg flex items-center justify-center ${className}`}>
        <div className="text-center p-4">
          <p className="text-error">无法加载图片</p>
          <p className="text-sm text-base-content opacity-70 mt-1 break-all">
            {photoPath}
          </p>
          <p className="text-xs text-base-content opacity-50 mt-2">
            这可能是因为图片格式不支持或文件已损坏
          </p>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className={`bg-base-200 rounded-lg flex items-center justify-center ${className}`}>
        <div className="text-center p-4">
          <div className="loading loading-spinner loading-md"></div>
          <p className="text-sm text-base-content opacity-70 mt-2">正在加载图片...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`bg-base-200 rounded-lg overflow-hidden ${className}`}>
      <img
        src={imageUrl}
        alt="预览图片"
        className="w-full h-full object-cover"
        onError={() => {
          console.error("图片加载失败:", imageUrl);
          setImageError(true);
        }}
        onLoad={() => {
          setImageError(false);
        }}
      />
    </div>
  );
};