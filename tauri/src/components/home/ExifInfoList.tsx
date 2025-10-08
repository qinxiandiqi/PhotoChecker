import { useState } from 'react'
import { Copy, Download, FileDown, FileText } from 'lucide-react'
import { ExifTag } from '../../types'
import { Button, Input, Card } from 'react-daisyui'
import { PhotoService } from '../../services/api'

interface ExifInfoListProps {
  exifData: ExifTag[]
  photoInfo: any
}

export const ExifInfoList = ({ exifData, photoInfo }: ExifInfoListProps) => {
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedGroup, setSelectedGroup] = useState<string>('all')

  // 获取所有可用的组
  const groups = ['all', ...Array.from(new Set(exifData.map(tag => tag.group)))]

  // 过滤EXIF数据
  const filteredData = exifData.filter(tag => {
    const matchesSearch =
      tag.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tag.value.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesGroup = selectedGroup === 'all' || tag.group === selectedGroup
    return matchesSearch && matchesGroup
  })

  const handleCopyAll = () => {
    const text = filteredData
      .map(tag => `${tag.name}: ${PhotoService.formatExifValue(tag.value, tag.name)}`)
      .join('\n')

    navigator.clipboard.writeText(text).then(() => {
      // TODO: 显示复制成功提示
      console.log('EXIF信息已复制到剪贴板')
    })
  }

  const handleExport = async (format: 'json' | 'csv') => {
    try {
      if (!photoInfo?.path) {
        console.error('没有文件路径可用于导出')
        return
      }

      const content = await PhotoService.exportExifData(photoInfo.path, format)
      const filename = `${photoInfo.name || 'photo'}_exif.${format}`
      const mimeType = format === 'json' ? 'application/json' : 'text/csv'

      PhotoService.downloadFile(content, filename, mimeType)
    } catch (error) {
      console.error('导出失败:', error)
    }
  }

  return (
    <div className="flex-1 bg-base-100 rounded-lg p-4 overflow-hidden flex flex-col">
      {/* 头部工具栏 */}
      <div className="flex items-center justify-between mb-4 space-x-2">
        <div className="flex items-center space-x-2 flex-1">
          <div className="form-control">
            <Input
              type="text"
              placeholder="搜索EXIF信息..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              className="input input-bordered input-sm w-48"
            />
          </div>

          <select
            value={selectedGroup}
            onChange={e => setSelectedGroup(e.target.value)}
            className="select select-bordered select-sm"
          >
            {groups.map(group => (
              <option key={group} value={group}>
                {group === 'all' ? '全部' : group}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center space-x-1">
          <Button size="sm" onClick={handleCopyAll} className="btn-ghost" title="复制全部">
            <Copy className="w-4 h-4" />
          </Button>

          <div className="dropdown dropdown-end">
            <div tabIndex={0} role="button" className="btn btn-ghost btn-sm m-1" title="导出">
              <Download className="w-4 h-4" />
            </div>
            <ul
              tabIndex={0}
              className="dropdown-content menu p-2 shadow bg-base-100 rounded-box w-52"
            >
              <li>
                <a onClick={() => handleExport('json')} className="flex items-center">
                  <FileText className="w-4 h-4 mr-2" />
                  导出为JSON
                </a>
              </li>
              <li>
                <a onClick={() => handleExport('csv')} className="flex items-center">
                  <FileDown className="w-4 h-4 mr-2" />
                  导出为CSV
                </a>
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* EXIF信息列表 */}
      <div className="flex-1 overflow-y-auto space-y-2">
        {filteredData.length === 0 ? (
          <div className="text-center py-8">
            {searchTerm || selectedGroup !== 'all' ? (
              <div className="text-base-content opacity-50">
                <p>没有找到匹配的EXIF信息</p>
                <button
                  className="btn btn-ghost btn-sm mt-2"
                  onClick={() => {
                    setSearchTerm('')
                    setSelectedGroup('all')
                  }}
                >
                  清除筛选条件
                </button>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="bg-base-200 rounded-lg p-6">
                  <h3 className="text-lg font-semibold mb-2">没有找到EXIF信息</h3>
                  <p className="text-base-content opacity-70 mb-4">
                    这张图片可能不包含EXIF元数据，或者文件格式不支持EXIF。
                  </p>

                  <div className="text-left space-y-2">
                    <div className="collapse collapse-arrow bg-base-300">
                      <input type="checkbox" />
                      <div className="collapse-title text-sm font-medium">为什么没有EXIF信息？</div>
                      <div className="collapse-content text-sm text-base-content opacity-70">
                        <ul className="list-disc list-inside space-y-1">
                          <li>
                            <strong>PNG/GIF文件</strong>: 通常不包含EXIF数据
                          </li>
                          <li>
                            <strong>编辑过的图片</strong>: 保存时可能丢失了EXIF信息
                          </li>
                          <li>
                            <strong>截图文件</strong>: 一般不包含相机EXIF数据
                          </li>
                          <li>
                            <strong>某些应用程序</strong>: 可能出于隐私考虑移除了EXIF
                          </li>
                        </ul>
                      </div>
                    </div>

                    <div className="collapse collapse-arrow bg-base-300">
                      <input type="checkbox" />
                      <div className="collapse-title text-sm font-medium">建议尝试的图片格式</div>
                      <div className="collapse-content text-sm text-base-content opacity-70">
                        <ul className="list-disc list-inside space-y-1">
                          <li>
                            <strong>JPEG/JPG</strong>: 最常见的包含EXIF的格式
                          </li>
                          <li>
                            <strong>TIFF</strong>: 专业摄影格式，通常包含丰富的EXIF
                          </li>
                          <li>
                            <strong>RAW格式</strong>: (CR2, NEF, ARW等) 包含最完整的元数据
                          </li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  <div className="mt-4 p-4 bg-info/10 rounded-lg">
                    <p className="text-sm text-info">
                      <strong>💡 提示</strong>: 要查看EXIF信息，请尝试使用用相机拍摄的JPEG照片。
                    </p>
                  </div>
                </div>

                {photoInfo && (
                  <div className="bg-base-200 rounded-lg p-4">
                    <h4 className="font-medium mb-2">文件信息</h4>
                    <div className="text-sm space-y-1 text-base-content opacity-70">
                      <p>
                        <strong>文件名:</strong> {photoInfo.name || '未知'}
                      </p>
                      <p>
                        <strong>文件路径:</strong> {photoInfo.path || '未知'}
                      </p>
                      <p>
                        <strong>文件大小:</strong>{' '}
                        {photoInfo.size ? PhotoService.formatFileSize(photoInfo.size) : '未知'}
                      </p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        ) : (
          filteredData.map((tag, index) => (
            <Card key={index} className="bg-base-200">
              <div className="card-body p-3">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <h4 className="font-semibold text-sm">{tag.name}</h4>
                    <p className="text-base-content opacity-80 text-sm mt-1 font-mono">
                      {PhotoService.formatExifValue(tag.value, tag.name)}
                    </p>
                    <span className="badge badge-outline badge-xs mt-1">{tag.group}</span>
                  </div>
                </div>
              </div>
            </Card>
          ))
        )}
      </div>

      {/* 底部信息 */}
      <div className="mt-4 pt-2 border-t border-base-300 text-xs text-base-content opacity-50">
        共 {filteredData.length} 条EXIF信息
        {exifData.length !== filteredData.length && (
          <span className="ml-2">(总共 {exifData.length} 条)</span>
        )}
      </div>
    </div>
  )
}
