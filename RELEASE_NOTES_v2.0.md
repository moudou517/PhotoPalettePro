# PhotoPalettePro v2.0

> 从「单一海报工具」升级为 **双模式翻页应用**：摄影海报 + Zine 明信片。

---

## ✨ 新增

### 🖼 Zine 明信片模式

- **4:3 横版**画布，成对输出 **正面 + 背面** 两张
- **正面**：左侧紧凑元数据（小序号 / 衬线标题 / 斜体副标题 / `LOCATION` / `DATE`）+ 右下简笔线稿主元素 + **6 个方形色块**
- **背面**：细外框 / 偏右分割线 / 右上虚线邮票框 / 4 条地址线 / 留言区；左半边铺满极淡的原图线条水印
- 卡片可**点按翻面**查看正 / 背面

### ↔ 左右滑动切换模式

- `ViewPager2` 双页 + 底部指示点，顶栏与底栏固定
- 底栏按钮文案随模式自动切换（"保存到相册" / "保存明信片"）

### ✏️ 主元素：程序化简笔线稿

低分辨率提取结构边缘 → **非极大值抑制**细化成 1 像素 → 在输出分辨率上沿切线方向重绘**长短 / 粗细 / 深浅都带抖动**的短笔触 → 程序化 alpha 场做**溶解软边**（不依赖 `BlurMaskFilter`，各版本表现一致）。

底下垫一层取自原图、柔化并压淡的平涂色块，形成"简笔涂色"的观感。

### 🎨 取色与主页面同步

```java
ColorExtractor.getTopWeightedColors(bitmap, mode, 6);
```

先按主页面当前选择的**取色逻辑**（默认渲染 / 取反差色 / 突出原色）拿到色板，再按「颜色实际覆盖的像素数」降序取前 6 色。明信片的方形色块与主元素的涂色层**共用这同一套颜色**。

### 📅 EXIF 自动预填

- 拍摄日期（`TAG_DATETIME_ORIGINAL`）→ 明信片 `DATE`
- GPS 坐标 → 明信片 `LOCATION`（**纯离线，不联网反查地名**）

### 🧊 iOS 玻璃拟态 UI

- 柔和氛围渐变背景 + 半透明磨砂玻璃卡片 / 悬浮栏
- Android 12+（API 31）使用 `RenderEffect` 真实背景模糊，低版本自动降级
- 修复滚动内容露出直角（`ViewOutlineProvider` + `clipToOutline`）

---

## ⚡ 性能与稳定性

- **明信片懒渲染**：导入照片时只渲染海报，**滑到明信片页才渲染**；切换取色逻辑只标记脏位
- 修复位图回收顺序导致 `Canvas: trying to use a recycled bitmap` 闪退
- 修复每次渲染泄漏的临时位图（主元素 / 水印 / 中间缩放图 / 检测缩略图）
- 预览分辨率 1200×900 → **1080×810**；导出 2400×1800 → **2000×1500**
- `onTrimMemory` 时按需释放明信片预览位图

---

## 💾 数据持久化

Room 升级到 **version 3**：

| 版本 | 新增 |
|---|---|
| 2 | 手动修改后的 EXIF（device / lens / shutter / aperture / iso） |
| 3 | `zineJson`：明信片的标题 / 副标题 / 地点 / 日期 / 序号 |

2→3 提供**正式 `Migration`**（`ALTER TABLE ... ADD COLUMN`），**不会清空老用户的历史记录**。导出后会把明信片信息一并落库，再次导入同一张照片即可恢复上次填写的内容。

---

## 🔧 其他

- 版本号 `1.0.1 → 2.0`（versionCode 4 → 5）
- 修复 `.gitignore` 被存为 **UTF-16** 导致 git 无法解析、build 产物一直无法被忽略的问题
- 将 `.continue/` 加入忽略，避免本地 Agent 配置（含 API Key）被提交

---

## 🙏 灵感来源

版式与美术方向参考两个开源的 **Agent Skill（提示词规范）**，本项目按其规范用原生 Java / Canvas 做**离线实现**，未复制其代码或素材：

- [Whiplashzeb/photo-to-zine-postcard](https://github.com/Whiplashzeb/photo-to-zine-postcard) — 明信片固定版式（MIT）
- [kwhi6693-web/photo-abstract-editorial](https://github.com/kwhi6693-web/photo-abstract-editorial) — 抽象编辑美术方向（AGPL-3.0）

> ⚠️ 两个 Skill 都要求主元素**优先手绘二创**。离线安卓端无法本地生成手绘插画，因此本项目实现的是两个 Skill 都允许的**降级路径**——程序化的简笔线稿，而非 AI 手绘。

---

## 📦 安装

| 项 | 值 |
|---|---|
| 文件 | `app-release.apk` |
| 大小 | 33.6 MB |
| minSdk | 26（Android 8.0+） |
| targetSdk | 35（Android 15） |
| 签名 | ✅ 已签名 |

---

## ⚠️ 升级提示

- 从 v1.x 升级后首次启动会执行 **Room 2→3 迁移**，历史记录会保留
- 明信片的 `LOCATION` 预填的是 **GPS 坐标**（如 `31.23°N 121.47°E`），可手动改成地名
- 「取反差色」模式的色板本身可能产生重复色，去重后明信片色块可能少于 6 个

---

## 🐛 已知限制

- 主元素为**程序化线稿**，不是 AI 手绘插画（见上文说明）
- 「取反差色」偶尔少于 6 色
- 单元测试依赖 Robolectric（仅 `testImplementation`，不进 APK）
