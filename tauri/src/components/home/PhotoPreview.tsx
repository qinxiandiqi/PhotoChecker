import { useState } from "react";

interface PhotoPreviewProps {
  photoPath: string;
  className?: string;
}

export const PhotoPreview = ({ photoPath, className = "" }: PhotoPreviewProps) => {
  const [imageError, setImageError] = useState(false);

  // 对于Tauri应用，我们需要转换文件路径为可访问的URL
  const imageUrl = `file://${photoPath}`;

  if (imageError) {
    return (
      <div className={`bg-base-200 rounded-lg flex items-center justify-center ${className}`}>
        <div className="text-center p-4">
          <p className="text-error">无法加载图片</p>
          <p className="text-sm text-base-content opacity-70 mt-1">
            {photoPath}
          </p>
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
        onError={() => setImageError(true)}
        onLoad={() => setImageError(false)}
      />
    </div>
  );
};