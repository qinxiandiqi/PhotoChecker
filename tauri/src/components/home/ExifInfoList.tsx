import { useState } from "react";
import { Search, Copy, Download, FileDown, FileText } from "lucide-react";
import { ExifTag } from "../../types";
import { Button, Input, Card, Dropdown } from "daisyui";
import { PhotoService } from "../../services/api";

interface ExifInfoListProps {
  exifData: ExifTag[];
  photoInfo: any;
}

export const ExifInfoList = ({ exifData, photoInfo }: ExifInfoListProps) => {
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedGroup, setSelectedGroup] = useState<string>("all");

  // 获取所有可用的组
  const groups = ["all", ...Array.from(new Set(exifData.map(tag => tag.group)))];

  // 过滤EXIF数据
  const filteredData = exifData.filter(tag => {
    const matchesSearch = tag.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         tag.value.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesGroup = selectedGroup === "all" || tag.group === selectedGroup;
    return matchesSearch && matchesGroup;
  });

  const handleCopyAll = () => {
    const text = filteredData
      .map(tag => `${tag.name}: ${PhotoService.formatExifValue(tag.value, tag.name)}`)
      .join('\n');

    navigator.clipboard.writeText(text).then(() => {
      // TODO: 显示复制成功提示
      console.log("EXIF信息已复制到剪贴板");
    });
  };

  const handleExport = async (format: 'json' | 'csv') => {
    try {
      if (!photoInfo?.path) {
        console.error("没有文件路径可用于导出");
        return;
      }

      const content = await PhotoService.exportExifData(photoInfo.path, format);
      const filename = `${photoInfo.name || 'photo'}_exif.${format}`;
      const mimeType = format === 'json' ? 'application/json' : 'text/csv';

      PhotoService.downloadFile(content, filename, mimeType);
    } catch (error) {
      console.error("导出失败:", error);
    }
  };

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
              onChange={(e) => setSearchTerm(e.target.value)}
              className="input input-bordered input-sm w-48"
            />
          </div>

          <select
            value={selectedGroup}
            onChange={(e) => setSelectedGroup(e.target.value)}
            className="select select-bordered select-sm"
          >
            {groups.map(group => (
              <option key={group} value={group}>
                {group === "all" ? "全部" : group}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center space-x-1">
          <Button
            size="sm"
            onClick={handleCopyAll}
            className="btn-ghost"
            title="复制全部"
          >
            <Copy className="w-4 h-4" />
          </Button>

          <Dropdown>
            <Dropdown.Button size="sm" className="btn-ghost" title="导出">
              <Download className="w-4 h-4" />
            </Dropdown.Button>
            <Dropdown.Menu className="dropdown-content-right">
              <Dropdown.Item onClick={() => handleExport('json')}>
                <FileText className="w-4 h-4 mr-2" />
                导出为JSON
              </Dropdown.Item>
              <Dropdown.Item onClick={() => handleExport('csv')}>
                <FileDown className="w-4 h-4 mr-2" />
                导出为CSV
              </Dropdown.Item>
            </Dropdown.Menu>
          </Dropdown>
        </div>
      </div>

      {/* EXIF信息列表 */}
      <div className="flex-1 overflow-y-auto space-y-2">
        {filteredData.length === 0 ? (
          <div className="text-center py-8 text-base-content opacity-50">
            {searchTerm || selectedGroup !== "all"
              ? "没有找到匹配的EXIF信息"
              : "没有EXIF信息"}
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
                    <span className="badge badge-outline badge-xs mt-1">
                      {tag.group}
                    </span>
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
          <span className="ml-2">
            (总共 {exifData.length} 条)
          </span>
        )}
      </div>
    </div>
  );
};