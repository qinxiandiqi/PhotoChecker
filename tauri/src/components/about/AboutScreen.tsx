import { Info, Code, Heart, Github } from 'lucide-react'
import { Button } from 'react-daisyui'

export const AboutScreen = () => {
  const handleOpenGithub = () => {
    // TODO: 添加GitHub链接
    console.log('打开GitHub页面')
  }

  return (
    <div className="container mx-auto p-4 max-w-2xl">
      <div className="text-center space-y-6">
        {/* 应用图标和名称 */}
        <div className="space-y-2">
          <div className="w-16 h-16 bg-primary rounded-lg mx-auto flex items-center justify-center">
            <Info className="w-8 h-8 text-primary-content" />
          </div>
          <h1 className="text-3xl font-bold">PhotoChecker</h1>
          <p className="text-base-content opacity-70">跨平台EXIF信息查看器</p>
        </div>

        {/* 版本信息 */}
        <div className="bg-base-200 rounded-lg p-4">
          <p className="text-sm">
            <span className="font-semibold">版本:</span> 1.0.0
          </p>
          <p className="text-sm">
            <span className="font-semibold">构建:</span> Tauri 2.0 + React 19
          </p>
        </div>

        {/* 功能介绍 */}
        <div className="text-left space-y-4">
          <h2 className="text-xl font-semibold flex items-center">
            <Code className="w-5 h-5 mr-2" />
            主要功能
          </h2>
          <ul className="space-y-2 text-sm">
            <li className="flex items-start">
              <span className="text-primary mr-2">•</span>
              <span>支持多种图片格式的EXIF信息查看</span>
            </li>
            <li className="flex items-start">
              <span className="text-primary mr-2">•</span>
              <span>显示150+种EXIF标签信息</span>
            </li>
            <li className="flex items-start">
              <span className="text-primary mr-2">•</span>
              <span>响应式设计，适配不同屏幕尺寸</span>
            </li>
            <li className="flex items-start">
              <span className="text-primary mr-2">•</span>
              <span>支持EXIF信息搜索和导出</span>
            </li>
            <li className="flex items-start">
              <span className="text-primary mr-2">•</span>
              <span>跨平台支持 (Windows/macOS/Linux)</span>
            </li>
          </ul>
        </div>

        {/* 支持的格式 */}
        <div className="text-left space-y-4">
          <h2 className="text-xl font-semibold">支持的格式</h2>
          <div className="bg-base-200 rounded-lg p-4">
            <p className="text-sm font-mono">JPEG, PNG, TIFF, RAW, CR2, NEF, ARW, DNG</p>
          </div>
        </div>

        {/* 开发信息 */}
        <div className="text-left space-y-4">
          <h2 className="text-xl font-semibold flex items-center">
            <Heart className="w-5 h-5 mr-2 text-error" />
            开发信息
          </h2>
          <div className="space-y-2 text-sm">
            <p>
              <span className="font-semibold">开发者:</span> QinXianDiQi Team
            </p>
            <p>
              <span className="font-semibold">技术栈:</span> Tauri 2.0, React 19, TypeScript, Rust
            </p>
            <p>
              <span className="font-semibold">开源协议:</span> MIT License
            </p>
          </div>
        </div>

        {/* 操作按钮 */}
        <div className="flex justify-center space-x-4 pt-4">
          <Button onClick={handleOpenGithub} className="btn-primary">
            <Github className="w-4 h-4 mr-2" />
            查看源码
          </Button>
        </div>

        {/* 版权信息 */}
        <div className="text-xs text-base-content opacity-50 pt-4 border-t border-base-300">
          <p>© 2024 QinXianDiQi. All rights reserved.</p>
        </div>
      </div>
    </div>
  )
}
