# 📸 PhotoPalettePro v2.0

![Version](https://img.shields.io/badge/Version-2.0-blue)
![Android](https://img.shields.io/badge/Android-API%2026%2B-green)
![License](https://img.shields.io/badge/License-MIT-orange)
![Java](https://img.shields.io/badge/Java-17-red)

> 一个把摄影作品做成**可收藏纸品**的 Android 工具：既能出 4K 摄影海报，也能出 Zine 风格明信片正反面。

**仓库地址**：<https://github.com/moudou517/PhotoPalettePro>

---

## 🆕 v2.0 更新了什么

v2.0 从「单一海报工具」变成了**双模式翻页应用**：

| | 海报模式（原有） | Zine 明信片（v2.0 新增） |
|---|---|---|
| 画布 | 4K 横版 3840×2160 | 4:3 横版，预览 1080×810 / 导出 2000×1500 |
| 取色 | 默认渲染 / 取反差色 / 突出原色 | **跟随上述取色逻辑**，并按画面权重取前 6 色 |
| 输出 | 单张海报 | **正面 + 背面**两张 |
| 切换 | — | 左右滑动翻页（顶部有指示点） |

另外还做了：

- **iOS 玻璃拟态 UI**：柔和氛围渐变背景 + 半透明磨砂玻璃卡片/悬浮栏，API 31+ 使用真实背景模糊（`RenderEffect`），低版本自动降级
- **滑动圆角修复**：用 `ViewOutlineProvider` + `clipToOutline` 保证滚动内容不会露出直角
- **明信片懒加载**：导入照片时只渲染海报，**滑到明信片页才渲染明信片**，省时省内存
- **EXIF 预填**：自动把拍摄日期与 GPS 坐标填进明信片的 `DATE` / `LOCATION`
- **Room 持久化升级到 v3**：导出后把明信片信息一并落库，再次导入同一张照片可恢复上次填写的内容

---

## 🙏 灵感来源 · Skill 致谢

> **这一节请重点阅读。** v2.0 的 Zine 明信片模式，版式与美术方向来自下面两个开源的 **Agent Skill（提示词规范）**。它们是为「AI 出图」写的设计规范，不是代码；本应用是**按其规范用原生 Java / Canvas 做的离线实现**。

### 1. [Whiplashzeb/photo-to-zine-postcard](https://github.com/Whiplashzeb/photo-to-zine-postcard)

提供了明信片的**固定版式**：

- 纵向 2:3、暖象牙纸、细微纸纹
- 原图嵌入上方（保持原始比例、不裁切不拉伸）
- 左侧紧凑元数据块：小序号 + 短横线 / 衬线标题 / 斜体副标题 / `LOCATION` / `DATE`
- 右下：一个主元素 + 可选一个更小的辅助元素
- 取自原图的色块
- 背面：细外框 / 偏右分割线 / 右上邮票框 / 地址线 / 左侧留言区

### 2. [kwhi6693-web/photo-abstract-editorial](https://github.com/kwhi6693-web/photo-abstract-editorial)

提供了**美术方向**（`references/art-direction.md`）：

- 「先读作克制的抽象，再想起原图」——锥形笔触 / 短横带 / 结构轴线
- 保留原图的方向、比例、节奏、重心与不对称
- **不发明对称、不做完整插画、不做可辨识的矢量描摹**
- 暖象牙面板、克制的编辑级留白、高对比衬线标题

### ⚠️ 关于「手绘」的如实说明

两个 Skill 都要求主元素**优先手绘二创**（水彩/墨线/拼贴）。**离线安卓端无法本地生成手绘插画**，因此本应用实现的是两个 Skill 都允许的**降级路径**：

> 低分辨率提取结构边缘 → 非极大值抑制细化 → 在输出分辨率上沿切线方向重绘为**长短/粗细/深浅都带抖动的短笔触**，下面垫一层取自原图的柔和平涂色块。

所以它是「程序化的简笔线稿」，不是真正的 AI 手绘。这一点在 [About 页面](app/src/main/java/com/example/photopalettepro/AboutActivity.java) 里也做了标注。

---

## ✨ 核心功能

### 📷 图片处理
- **高清导入**：支持 50MP+，按需采样（`calculateInSampleSize`）避免 OOM
- **4K 海报渲染**：3840×2160
- **EXIF 提取**：相机/镜头/快门/光圈/ISO + 拍摄日期 + GPS 坐标
- **设备识别**：500+ 相机与手机型号映射库

### 🎨 配色
- **三种取色逻辑**：默认渲染 / 取反差色 / 突出原色（KMeans 聚类 + 高级感调色）
- **权重排序**：`ColorExtractor.getTopWeightedColors()` 按「颜色实际覆盖的像素数」降序，明信片与海报共用同一套取色逻辑
- **自适应背景**：由色板最亮色推导

### 🖼️ 两种成品
- **海报**：水印签名开关、机身与镜头信息、横竖构图自动适配
- **Zine 明信片**：4:3 横版；左侧元数据 + 右下简笔线稿主元素 + 6 个方形色块；背面为可书写的标准明信片版式（左侧铺满极淡的原图线条水印）

### 🌓 主题与交互
- 日间 / 夜间双主题（`values-night` + `drawable-night`），适配系统深色模式
- 玻璃拟态（Glassmorphism）UI
- 按钮按压缩放动画、下拉刷新物理阻尼（Overshoot 插值）
- 长按标题 5 秒跳转 About（分阶段震动反馈）
- 明信片卡片可点按翻面查看正/背面

---

## 🚀 快速开始

### 系统要求
- **minSdk**: 26（Android 8.0+）
- **targetSdk / compileSdk**: 35（Android 15）
- **Java**: 17
- **IDE**: Android Studio Flamingo 或更新

### 构建

```bash
git clone https://github.com/moudou517/PhotoPalettePro.git
cd PhotoPalettePro
# 用 Android Studio 打开，或命令行：
./gradlew assembleDebug
```

> 注意：`local.properties` 里的 `sdk.dir` 需要指向你自己的 Android SDK。

### 使用流程

```
1. 启动 → 同意隐私政策
2. [导入] 选择照片（自动读取 EXIF，并预填明信片的日期/地点）
3. 海报模式：选渲染模式 + 排列方式 → [预览渲染效果] → [保存到相册]
4. 左右滑动 → 明信片模式：填标题/副标题/地点/日期/序号 → [生成 Zine 明信片]
   → [翻面查看背面] → [保存明信片]（正面与背面各存一张）
```

---

## 📁 项目结构

```
app/src/main/java/com/example/photopalettepro/
├── MainActivity.java                  # 双模式编排：翻页、渲染、导出、懒加载
├── AboutActivity.java                 # 关于页（含 Skill 灵感来源）
├── ColorExtractor.java                # 取色算法（含按权重取前 N 色）
├── PosterRenderer.java                # 海报渲染引擎（4K）
├── PosterUtils.java                   # 海报工具（排序 / 空间映射 / 自适应底色）
├── ZinePostcardRenderer.java          # ★ Zine 明信片渲染（正反面 + 简笔线稿）
├── ZinePostcardConfig.java            # 明信片元数据（标题/副标题/地点/日期/序号）
├── ExifUtil.java                      # EXIF 提取（500+ 型号映射 + 日期 + GPS）
│
├── helper/
│   ├── GlassEffectHelper.java         # ★ API 31+ 真实背景模糊
│   ├── UIInteractionHelper.java       # 按压动画 + 圆角裁剪
│   ├── PullRefreshHelper.java         # 下拉刷新（海报页 / 明信片页共用）
│   ├── ImageProcessingHelper.java     # 采样、预览图生成
│   ├── ImageSaveHelper.java           # 异步存相册（MediaStore / 传统 API）
│   ├── ExifInfoManager.java           # EXIF 数据管理
│   ├── PopupMenuHelper.java           # 弹出菜单
│   └── TitleLongPressHelper.java      # 标题长按彩蛋
│
└── data/                              # Room 持久化
    ├── AppDatabase.java               # v3，含 2→3 正式 Migration
    ├── ExportHistory.java             # 导出历史实体（含 zineJson 列）
    ├── ExportHistoryDao.java
    └── ExportHistoryRepository.java

app/src/main/res/
├── layout/
│   ├── activity_main.xml              # 外壳：顶栏 + ViewPager2 + 指示点 + 底栏
│   ├── page_poster.xml                # 海报页
│   ├── page_zine.xml                  # 明信片页
│   ├── activity_about.xml
│   └── dialog_privacy.xml
├── drawable{,-night}/                 # 玻璃材质、圆角、指示点
└── values{,-night}/                   # 颜色、主题、样式

app/src/test/java/.../ZineRenderSmokeTest.java   # ★ Robolectric + 真实 Skia 渲染出 PNG，用于肉眼校验
```

---

## 📊 技术亮点

### 1. 简笔线稿主元素

```java
// 低分辨率做边缘检测 → NMS 细化 → 输出分辨率上重绘抖动短笔触
Bitmap motif = ZinePostcardRenderer.renderFront(photo, palette, config, 2000);
```

关键点：**先做非极大值抑制把边缘细化成 1 像素**，否则一条 2~3px 厚的边缘会被画成好几道平行笔触，看起来毛躁。

### 2. 程序化溶解软边

不依赖 `BlurMaskFilter`（各版本表现不一致），改为在低分辨率上按「到矩形边距离 + 平滑噪声」计算 alpha 场，再双线性放大，得到线条与色块自然消散进纸面的边缘。

### 3. 取色与主页面同步

```java
// 先按当前取色逻辑拿色板，再按"实际覆盖像素数"排序取前 6 色
List<Integer> palette = ColorExtractor.getTopWeightedColors(bitmap, mode, 6);
```

`getTopWeightedColors(bitmap, count)` 只按簇内像素数排序。

### 4. 大图内存管理

```
50MP 原图
  ├─ 分析/导出用: 4000×4000（按需采样）
  └─ UI 预览用:   2000×2000
```

明信片侧还做了：预览 1080×810 / 导出 2000×1500；每次渲染的临时位图全部显式回收；`onTrimMemory` 且不在明信片页时主动释放预览位图。

### 5. 懒渲染

```
导入照片 → 只渲染海报 + zineDirty = true
左右滑动 → 落到明信片页时才真正渲染
切换取色逻辑 → 只标记脏位；若当前不在明信片页则等滑过去再渲染
```

---

## 💾 数据持久化

Room 数据库 `photo_palette_pro_db`，当前 **version 3**。

| 版本 | 变更 |
|---|---|
| 1 | 初始：`originalUri` / `optionsJson` / `sign` / `outputPath` / `timestamp` |
| 2 | 新增手动修改后的 EXIF：`device` / `lens` / `shutter` / `aperture` / `iso` |
| 3 | 新增 `zineJson`：明信片的标题/副标题/地点/日期/序号 |

2→3 提供了**正式 `Migration`** 而不是走破坏性迁移，老用户的导出历史不会丢。再次导入同一张照片时，会自动回填上次的渲染模式、EXIF 与明信片信息。

---

## 🔐 隐私

- **完全离线**：图片处理全在本地，不上传任何数据
- **EXIF**：只读取摄影参数；GPS 只用于预填明信片的地点栏，**不联网反查地名**
- **首次启动**需确认隐私政策（存 SharedPreferences）

---

## 🐛 已知限制

| 限制 | 说明 |
|---|---|
| 主元素是程序化线稿 | 见上文「关于手绘的如实说明」，非 AI 手绘插画 |
| 地点只填坐标 | EXIF 里只有经纬度，离线不做地名反查（填入形如 `31.23°N 121.47°E`，可手动改） |
| 取反差色偶尔少于 6 色 | 该模式的色板本身可能产生重复色，去重后即为 5 色 |
| 单元测试依赖 Robolectric | 仅在 `testImplementation`，不进 APK；不需要可删掉该测试与依赖 |

---

## 📚 文档导航

| 文档 | 描述 |
|------|------|
| **README.md** | 项目总览（本文档） |
| **MODULARIZATION_GUIDE.md** | 模块化设计详解 |
| **NIGHT_MODE_DESIGN.md** | 深色模式 WCAG 标准设计 |
| **COLOR_REFERENCE.md** | 色板参考 |
| **OPTIMIZATION_SUMMARY.md** | 性能优化总结 |
| **MIGRATION_CHECKLIST.md** | 升级迁移清单 |

---

## 🙏 致谢

**Skill / 设计规范**

- [Whiplashzeb/photo-to-zine-postcard](https://github.com/Whiplashzeb/photo-to-zine-postcard) — 明信片版式规范
- [kwhi6693-web/photo-abstract-editorial](https://github.com/kwhi6693-web/photo-abstract-editorial) — 抽象编辑美术方向

**开源库**

- [AndroidX](https://developer.android.com/jetpack) — ViewPager2 / Room / RecyclerView / SwipeRefreshLayout
- [Material Design](https://material.io/) — 组件与配色规范
- [EXIF Interface](https://developer.android.com/jetpack/androidx/releases/exifinterface) — EXIF 解析
- [Palette](https://developer.android.com/jetpack/androidx/releases/palette) — 配色提取
- [Robolectric](https://robolectric.org/) — 本地渲染校验

---

## 📜 许可证

MIT License © 2026 PhotoPalettePro

> 注意：上面两个 Skill 仓库中，`photo-to-zine-postcard` 为 MIT；`photo-abstract-editorial` 为 **AGPL-3.0**。本项目只参考了其**设计规范/美术方向**并用原生代码独立实现，未复制其代码或素材。如果你的使用场景涉及 AGPL 的传染性要求，请自行评估。

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

**Last Updated**: 2026-09
**Current Version**: 2.0（versionCode 5）
