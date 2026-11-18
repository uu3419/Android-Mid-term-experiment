# NotePad 记事本应用（期中实验）

基于 Android 官方早期 NotePad 示例扩展实现，包含以下功能：

## 基本要求
- 笔记列表显示最后修改时间（`SimpleCursorAdapter.ViewBinder` 格式化时间戳）
- 按标题 / 内容实时搜索（顶部搜索框，`title LIKE ? OR note LIKE ?`）

## 附加功能
- 笔记分类：编辑器下拉选择「未分类 / 工作 / 个人 / 灵感」，列表显示彩色分类标签与左侧色条，支持按分类筛选
- 笔记排序：菜单切换「按修改时间 / 按标题」
- UI 美化：Material 主题、Logo、圆角搜索框、卡片式列表、空状态提示、悬浮新建按钮、编辑器胶囊工具条
- 导出与云备份恢复：导出全部笔记为 txt 分享；全部笔记备份为 JSON 到云端目录；一键从备份恢复

## 构建
```
gradlew.bat assembleDebug
```
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 参考
原始示例解析参考 [Android Sample--NotePad解析](https://blog.csdn.net/llfjfz/article/details/67638499)
