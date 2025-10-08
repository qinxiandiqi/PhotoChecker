import { useState, useCallback } from 'react'
import { PhotoService } from '../services/api'
import { PhotoInfo, HomeUIState } from '../types'

export const usePhotoSelector = () => {
  const [uiState, setUiState] = useState<HomeUIState>({ type: 'empty' })
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selectPhoto = useCallback(async () => {
    try {
      setIsLoading(true)
      setError(null)

      const path = await PhotoService.selectPhoto()
      if (!path) {
        setUiState({ type: 'empty' })
        return
      }

      // 创建临时的PhotoInfo用于loading状态
      const tempPhotoInfo: PhotoInfo = {
        path,
        name: '加载中...',
        size: 0,
        exif_tags: [],
        file_info: {},
        format_supported: false,
        exif_available: false,
      }

      setUiState({ type: 'loading', photoInfo: tempPhotoInfo })

      // 获取照片信息（已包含EXIF数据）
      const exifResult = await PhotoService.getPhotoInfo(path)

      setUiState({ type: 'success', photoInfo: exifResult.photo_info, exifResult })
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '未知错误'
      setError(errorMessage)
      // 保持之前的photoInfo以便在错误状态下仍能显示预览
      const currentPhotoInfo = uiState.type === 'loading' ? uiState.photoInfo : undefined
      setUiState({
        type: 'error',
        error: errorMessage,
        photoInfo: currentPhotoInfo,
      })
    } finally {
      setIsLoading(false)
    }
  }, [])

  const clearPhoto = useCallback(() => {
    setUiState({ type: 'empty' })
    setError(null)
  }, [])

  const retryPhoto = useCallback(() => {
    if (uiState.type === 'error' || uiState.type === 'success') {
      const photoPath = uiState.photoInfo?.path
      if (photoPath) {
        selectPhoto()
      }
    }
  }, [uiState, selectPhoto])

  return {
    uiState,
    isLoading,
    error,
    selectPhoto,
    clearPhoto,
    retryPhoto,
  }
}
