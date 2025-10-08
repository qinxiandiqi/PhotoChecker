import { useState, useEffect } from 'react'
import { usePhotoSelector } from '../../hooks/usePhotoSelector'
import { LoadingSpinner, ErrorDisplay } from '../common'
import { PhotoSelector } from './PhotoSelector'
import { PhotoPreview } from './PhotoPreview'
import { ExifInfoList } from './ExifInfoList'

export const HomeScreen = () => {
  const { uiState, isLoading, selectPhoto, retryPhoto } = usePhotoSelector()
  const [isCompact, setIsCompact] = useState(window.innerWidth < 768)

  // 监听窗口大小变化
  useEffect(() => {
    const handleResize = () => {
      setIsCompact(window.innerWidth < 768)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const renderContent = () => {
    switch (uiState.type) {
      case 'empty':
        return <PhotoSelector onSelect={selectPhoto} />

      case 'loading':
        return (
          <div className={isCompact ? 'space-y-4' : 'flex gap-4'}>
            <PhotoPreview photoPath={uiState.photoInfo.path} />
            <div className="flex-1 flex items-center justify-center">
              <LoadingSpinner size="lg" text="正在解析EXIF数据..." />
            </div>
          </div>
        )

      case 'success':
        return (
          <div className={isCompact ? 'space-y-4' : 'flex gap-4'}>
            <PhotoPreview photoPath={uiState.photoInfo.path} />
            <ExifInfoList
              exifData={uiState.exifResult.photo_info.exif_tags}
              photoInfo={uiState.photoInfo}
            />
          </div>
        )

      case 'error':
        return (
          <div className={isCompact ? 'space-y-4' : 'flex gap-4'}>
            {uiState.photoInfo && <PhotoPreview photoPath={uiState.photoInfo.path} />}
            <div className="flex-1 flex items-center justify-center">
              <ErrorDisplay error={uiState.error} onRetry={retryPhoto} />
            </div>
          </div>
        )

      default:
        return null
    }
  }

  return (
    <div className="container mx-auto p-4 h-screen flex flex-col">
      {/* 头部 */}
      <header className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">PhotoChecker</h1>
        {uiState.type !== 'empty' && (
          <button onClick={selectPhoto} className="btn btn-primary btn-sm">
            选择其他照片
          </button>
        )}
      </header>

      {/* 主要内容 */}
      <main className="flex-1 overflow-hidden">{renderContent()}</main>

      {/* 加载状态 */}
      {isLoading && uiState.type === 'empty' && (
        <div className="fixed inset-0 bg-base-200 bg-opacity-50 flex items-center justify-center z-50">
          <LoadingSpinner size="lg" text="正在选择照片..." />
        </div>
      )}
    </div>
  )
}
