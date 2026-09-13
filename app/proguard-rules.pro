# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 如果你的项目以后用到了 WebView 和 JS 交互，可以取消下方注释
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# 建议取消下方两行的注释：保留行号信息，这样即使混淆了，崩溃日志也能看到具体的报错行号
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ==========================================================================
# PhotoPalettePro - Room 数据库专用的代码混淆与裁剪保护规则
# ==========================================================================

# 1. 保护所有 Room 相关的基础组件和自动生成的底层实现类名称（防止反射实例化失败）
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.Entity { *; }
-keep class * implements androidx.room.Dao { *; }
-keep class *_Impl { *; }

# 2. 核心保活：保护你的历史记录实体数据类（Entity）
# 必须保证 originalUri、optionsJson、sign、outputPath 等字段名不被混淆成 a, b, c
# 否则 Room 在匹配 SQLite 数据库表列名时会发生严重不匹配导致 Crash
-keep class com.example.photopalettepro.data.ExportHistory {
    *;
}

# 3. 忽略部分不影响编译期和运行期的第三方库库警告
-dontwarn androidx.room.**