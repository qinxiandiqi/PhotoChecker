import { Upload, Image as ImageIcon } from "lucide-react";
import { Button } from "react-daisyui";

interface PhotoSelectorProps {
  onSelect: () => void;
}

export const PhotoSelector = ({ onSelect }: PhotoSelectorProps) => {
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();

    const files = Array.from(e.dataTransfer.files);
    const imageFile = files.find(file =>
      file.type.startsWith('image/')
    );

    if (imageFile) {
      // TODO: 处理拖拽的文件
      console.log("拖拽的文件:", imageFile);
      onSelect();
    }
  };

  return (
    <div className="flex-1 flex items-center justify-center">
      <div
        className="max-w-md w-full text-center space-y-6 p-8 border-2 border-dashed border-base-300 rounded-lg"
        onDragOver={handleDragOver}
        onDrop={handleDrop}
      >
        <div className="flex justify-center">
          <ImageIcon className="w-16 h-16 text-base-content opacity-50" />
        </div>

        <div className="space-y-2">
          <h2 className="text-xl font-semibold">
            选择照片查看EXIF信息
          </h2>
          <p className="text-base-content opacity-70">
            支持JPG、PNG、TIFF、RAW等格式
          </p>
        </div>

        <div className="space-y-3">
          <Button
            onClick={onSelect}
            className="btn btn-primary w-full"
          >
            <Upload className="w-4 h-4 mr-2" />
            选择照片
          </Button>

          <p className="text-sm text-base-content opacity-50">
            或将照片拖拽到此处
          </p>
        </div>

        <div className="text-xs text-base-content opacity-40">
          <p>支持的格式: JPEG, PNG, TIFF, RAW</p>
          <p>最大文件大小: 100MB</p>
        </div>
      </div>
    </div>
  );
};