# 🎨 PhotoPalettePro 配色快速参考

## 📊 日间 vs 夜间 模式对比

### 核心背景色
```
日间模式 (Light):
  ├─ app_background:    #F8F9FA (清爽灰白)
  ├─ card_background:   #FFFFFF (纯白)
  └─ input_background:  #F3F4F6 (浅灰)
  
夜间模式 (Dark):
  ├─ app_background:    #0D1117 (深蓝黑)
  ├─ card_background:   #161B22 (深灰蓝)
  └─ input_background:  #21262D (暗灰)
```

### 文字色
```
日间模式:
  ├─ text_main:       #1E293B (深灰黑)
  ├─ text_secondary:  #64748B (中灰)
  └─ hint_text:       #9CA3AF (浅灰)
  
夜间模式:
  ├─ text_main:       #E6EAEF (柔和白)
  ├─ text_secondary:  #8B949E (中灰)
  └─ hint_text:       #6E7681 (深灰)
```

### 边框与分割线
```
日间模式:
  ├─ divider_color:     #E5E7EB
  └─ card_border:       #D1D5DB
  
夜间模式:
  ├─ divider_color:     #30363D
  └─ card_border:       #30363D
```

### 按钮与强调色
```
日间模式:
  ├─ primary_blue:      #2563EB
  ├─ accent_green:      #10B981
  ├─ input_focus:       #2563EB
  └─ button_hover:      #1D4ED8
  
夜间模式:
  ├─ primary_blue:      #1F6FEB (更深的蓝)
  ├─ accent_green:      #238636 (更深的绿)
  ├─ input_focus:       #58A6FF (高亮蓝)
  └─ button_hover:      #1f6feb
```

---

## 🗂️ 文件结构说明

```
res/
├── drawable/                          ☀️ 日间模式drawable
│   ├── bg_card_rounded.xml             # 卡片背景（白）
│   ├── bg_edittext_rounded.xml         # 输入框（浅灰）
│   ├── btn_primary_rounded.xml         # 按钮（蓝色）
│   └── bg_bottom_sheet.xml
│
├── drawable-night/                    🌙 夜间模式drawable (新增)
│   ├── bg_card_rounded.xml             # 卡片背景（深灰）
│   ├── bg_edittext_rounded.xml         # 输入框（暗灰）
│   ├── btn_primary_rounded.xml         # 按钮（深蓝）
│   
├── values/                             ☀️ 日间模式值
│   ├── colors.xml                      # 日间色值定义
│   ├── themes.xml                      # 日间主题（lightStatusBar: true）
│   └── styles.xml
│
├── values-night/                       🌙 夜间模式值 (已优化)
│   ├── colors.xml                      # 夜间色值定义（已升级）
│   ├── themes.xml                      # 夜间主题（lightStatusBar: false）
│   └── styles.xml                      # 夜间样式（新增）
│
└── layout/
    └── activity_main.xml               # 使用语义化色名，自动适应
```

---

## 🚀 快速修改指南

### 修改日间模式背景色
编辑：`res/values/colors.xml`
```xml
<color name="app_background">#您的浅色背景</color>
```

### 修改夜间模式背景色
编辑：`res/values-night/colors.xml`
```xml
<color name="app_background">#您的深色背景</color>
```

### 修改卡片圆角背景
日间：`res/drawable/bg_card_rounded.xml`  
夜间：`res/drawable-night/bg_card_rounded.xml`
```xml
<solid android:color="#您的卡片色" />
```

### 修改按钮颜色
日间：`res/drawable/btn_primary_rounded.xml`  
夜间：`res/drawable-night/btn_primary_rounded.xml`
```xml
<solid android:color="#您的按钮色" />
```

---

## 📱 Android系统适配

### 自动触发条件
- **系统 → 显示 → 深色主题** → 自动加载 `values-night/` 和 `drawable-night/`
- **系统 → 显示 → 夜间灯光** (时间表) → 在指定时间自动切换

### 不需要额外代码
✅ 已在 `activity_main.xml` 中使用语义化色名(`@color/xxx`)  
✅ 系统会自动选择对应模式的资源

---

## 🎯 使用建议

### ✅ 应该这样做
```xml
<!-- 使用语义化色名 -->
<TextView
    android:textColor="@color/text_main"
    android:background="@color/card_background" />
```

### ❌ 不要这样做
```xml
<!-- 硬编码色值 -->
<TextView
    android:textColor="#000000"
    android:background="#FFFFFF" />
```

---

## 🔍 色值验证工具

推荐对所有文字或UI元素检查对比度：
- 🌐 [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/)
- 🎨 [Color Contrast Accessor](https://www.ids.ac.uk/tools-and-resources/#contrastanalyser)

**要求：** WCAG AA 级或以上（对比度 ≥ 4.5:1）

---

## 🎬 测试方法

### Android Studio 内预览
1. Device Configuration → Night / Light
2. 实时查看深色/浅色效果

### 真机测试
1. 设置 → 显示 → 深色主题 (打开/关闭)
2. 查看应用自动切换颜色

### 编程控制（可选）
```java
// 在 MainActivity 或其他位置添加
AppCompatDelegate.setDefaultNightMode(
    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
); // 跟随系统设置
```

---

**最后更新**: 2025-05-07  
**版本**: v2.0 (深色模式完全优化版)

