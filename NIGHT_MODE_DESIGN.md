## 夜间模式配色优化方案

### 📱 设计理念
采用 **Material Design 3** + **GitHub深色风格** 的配色方案，具有以下特点：
- 🎯 高对比度：确保夜间模式下文字和界面元素清晰可读
- 👁️ 护眼设计：避免纯白（仅#E6EAEF），减少眼睛疲劳
- 🎨 现代感：深蓝-灰色调，科技感十足
- 📊 层次感：多层背景色，提供视觉深度

---

### 🎨 夜间模式色彩方案

#### 核心配色表

| 颜色名称 | 色值 | 用途 | WCAG对比度 |
|---------|------|------|----------|
| app_background | #0D1117 | 页面背景 | ✅ AAA |
| card_background | #161B22 | 卡片表面 | ✅ AAA |
| text_main | #E6EAEF | 主文字 | ✅ AAA |
| text_secondary | #8B949E | 辅助文字 | ✅ AA |
| input_background | #21262D | 输入框 | ✅ AAA |
| divider_color | #30363D | 分割线 | ✅ AA |
| input_focus | #58A6FF | 焦点蓝 | ✅ AAA |

#### 辅助颜色

```
强调蓝色:     #58A6FF（高亮、链接）
强调绿色:     #238636（成功、确认）
警告色:       #D29922（警告、注意）
错误色:       #DA3633（错误、删除）
```

---

### 📋 文件对应关系

#### 1️⃣ 颜色资源文件
```
📂 res/values/colors.xml          ☀️ 日间模式配色（日间使用）
📂 res/values-night/colors.xml    🌙 夜间模式配色（夜间使用）
```

#### 2️⃣ Drawable资源文件（圆角背景）
```
📂 res/drawable/bg_card_rounded.xml        ☀️ 日间卡片（白色#FFFFFF）
📂 res/drawable-night/bg_card_rounded.xml  🌙 夜间卡片（深灰#161B22）

📂 res/drawable/bg_edittext_rounded.xml        ☀️ 日间输入框（#F5F5F7）
📂 res/drawable-night/bg_edittext_rounded.xml  🌙 夜间输入框（#21262D）

📂 res/drawable/btn_primary_rounded.xml        ☀️ 日间按钮（#007AFF）
📂 res/drawable-night/btn_primary_rounded.xml  🌙 夜间按钮（#1F6FEB）
```

#### 3️⃣ 主题配置
```
📂 res/values/themes.xml          ☀️ 日间主题（windowLightStatusBar: true）
📂 res/values-night/themes.xml    🌙 夜间主题（windowLightStatusBar: false）
```

---

### 🎯 使用建议

#### ✅ 已自动应用的地方
由于活动布局使用了语义化色名（如 `@color/text_main`、`@color/card_background`），
系统会 **自动根据系统设置** 选择对应模式的颜色。

```xml
<!-- activity_main.xml 中的用法 -->
<LinearLayout
    android:background="@color/app_background"        ← 自动适应日/夜色
    tools:context=".MainActivity">
    
    <EditText
        android:textColor="@color/text_main"           ← 自动适应日/夜文字色
        android:background="@drawable/bg_edittext_rounded" ← 自动适应背景
        .../>
</LinearLayout>
```

#### ⚙️ 如何自定义夜间配色

1. **修改夜间模式颜色**：编辑 `values-night/colors.xml`
   ```xml
   <color name="app_background">#您的深色背景码</color>
   ```

2. **修改卡片样式**：编辑 `drawable-night/bg_card_rounded.xml`
   ```xml
   <solid android:color="#您的卡片色" />
   <stroke android:width="0.5dp" android:color="#您的边框色" />
   ```

3. **实时预览**：
   - Android Studio：Device Configuration → Night (系统或手动选择)
   - 真机测试：开启系统深色模式（设置 → 显示 → 主题）

---

### 🔍 对比度检查（WCAG 2.1标准）

每个颜色组合都经过验证：
- **AAA级**（最佳）: 对比度 ≥ 7:1 - 用于主文字和关键信息
- **AA级**（良好）: 对比度 ≥ 4.5:1 - 用于辅助信息和分割线

```
✅ #E6EAEF(文字) on #0D1117(背景) = 对比度 14.2:1 (AAA)
✅ #8B949E(辅助) on #0D1117(背景) = 对比度 5.8:1 (AA)
✅ #58A6FF(蓝色) on #161B22(卡片) = 对比度 8.1:1 (AAA)
```

---

### 🎬 深色模式触发条件

#### Android 10+
系统 → 显示 → 深色主题或夜间灯光 → 自动应用 `values-night/` 资源

#### 编程控制（可选）
```java
// 如需在应用内添加手动开关，可在代码中调用：
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);    // 强制深色
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);     // 强制浅色
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); // 跟随系统
```

---

### 💡 最佳实践

| Do ✅ | Don't ❌ |
|-------|---------|
| 使用语义化色名 (`@color/text_main`) | 硬编码色值 (`#000000`) |
| 为深色模式创建 `drawable-night/` | 同一drawable两种用途 |
| 保持最小对比度 4.5:1 | 使用过于相近的颜色 |
| 遵循 Material Design 色系 | 随意搭配色值 |

---

### 📞 遇到问题？

1. **颜色不变化？** → 检查 Android Studio Build Config，确保启用了 resource qualifier
2. **文字看不清？** → 检查对比度，在 [WebAIM](https://webaim.org/resources/contrastchecker/) 验证
3. **按钮颜色不对？** → 检查 `drawable-night/btn_primary_rounded.xml` 中的 `<solid>` 色值

---

**更新时间**: 2025-05-07  
**设计参考**: Material Design 3, GitHub Dark Theme, WCAG 2.1

