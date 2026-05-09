package com.example.photopalettepro;

import android.content.Context;
import android.graphics.*;
import android.util.Log;
import java.util.List;
import java.util.Map;

public class PosterRenderer {

    // 预设 4K 分辨率
    private static final int W = 3840;
    private static final int H = 2160;

    public static Bitmap render(Context context, Bitmap photo, List<Integer> rawPalette, Map<String, String> info, String style) {
        // 1. 颜色顺序调度
        List<Integer> finalPalette = style.equals("马赛克化") ?
                PosterUtils.getSpatialMapping(photo, rawPalette) :
                PosterUtils.sortPalette(rawPalette);

        // 2. 背景色生成（基于 HSL 排序后的最亮色）
        int bgColor = PosterUtils.getAdaptiveBg(PosterUtils.sortPalette(rawPalette).get(0));

        // 3. 创建画布（预设 4K 分辨率 W=3840, H=2160）[cite: 19]
        Bitmap canvasBitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawColor(bgColor);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean isLandscape = photo.getWidth() > photo.getHeight();

        // 4. 执行具体的渲染逻辑（注意：内部子方法也同步去掉了 fontSize 参数）[cite: 19]
        if (isLandscape) {
            renderLandscape(context, canvas, photo, finalPalette, info, paint);
        } else {
            renderPortrait(context, canvas, photo, finalPalette, info, paint);
        }

        // --- 5. 新增：绘制 61 像素白色边框 ---
        drawBorder(canvas);
        return canvasBitmap;
    }

    /**
     * 在画布边缘绘制 61 像素的白色边框
     */
    private static void drawBorder(Canvas canvas) {
        int borderWidth = 61;
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.FILL);

        // 顶部边框
        canvas.drawRect(0, 0, W, borderWidth, borderPaint);
        // 底部边框
        canvas.drawRect(0, H - borderWidth, W, H, borderPaint);
        // 左侧边框
        canvas.drawRect(0, 0, borderWidth, H, borderPaint);
        // 右侧边框
        canvas.drawRect(W - borderWidth, 0, W, H, borderPaint);
    }

    private static void renderLandscape(Context context, Canvas canvas, Bitmap img, List<Integer> palette, Map<String, String> info, Paint paint) {
        // --- 1. 确定照片尺寸 (核心基准) ---
        int targetH = (int) (H * 0.618f);
        int targetW = img.getWidth() * targetH / img.getHeight();
        Bitmap resized = Bitmap.createScaledBitmap(img, targetW, targetH, true);
        int imgY = (H - targetH) / 2;

        // --- 2. 确定设备文字规格 (决定全局间距) ---
        String deviceText = getSafeInfo(info, "device", "ILCE-7CM2").toUpperCase();
        String lensText = getSafeInfo(info, "lens", "LENS").toUpperCase();
        String paramText = (getSafeInfo(info, "s", "1/100s") + "  " + getSafeInfo(info, "f", "f/2.8") + "  " + getSafeInfo(info, "iso", "ISO 100")).toUpperCase();

        // 计算设备名缩放与高度
        paint.setTypeface(loadFont(context, "Black"));
        float maxDeviceW = targetW * 0.618f;
        float currentDeviceFontSize = calculateFontSize(paint, deviceText, maxDeviceW, 95, 40);
        paint.setTextSize(currentDeviceFontSize);

        Rect bounds = new Rect();
        paint.getTextBounds(deviceText, 0, deviceText.length(), bounds);
        float deviceTextHeight = bounds.height(); // 这是所有纵向呼吸感的基准
        float finalDeviceW = paint.measureText(deviceText);

        // --- 3. 确定纵向布局与动态半径 R ---
        float lensFontSize = 58f;
        float paramFontSize = 50f;
        int vGap = 20; // 行间距

        // 文字底部对齐照片底部
        float photoBtm = imgY + targetH;
        float paramY = photoBtm - (paramFontSize / 4); // 微调底部参数对齐感

        // 计算镜头和设备名 Y 坐标
        // deviceY 是基准线（Baseline），所以顶边缘是 deviceY - deviceTextHeight
        float lensY = paramY - (paramFontSize / 2 + vGap + lensFontSize / 2);
        float deviceY = lensY - (lensFontSize / 2 + vGap + deviceTextHeight / 2);

        // --- 精确间距修正 ---
        // 规则 1: 横线到文字顶部的距离 = deviceTextHeight
        float deviceTopEdge = deviceY - deviceTextHeight;
        float lineY = deviceTopEdge - deviceTextHeight;

        // 规则 2: 小球底边缘到横线的距离 = deviceTextHeight
        float ballAreaBottom = lineY - deviceTextHeight;

        // 计算 R (公式：顶部偏移 0.618R + 3行直径 6R + 2间距 1.2R = 总高度)
        float totalBallH = ballAreaBottom - imgY;
        float R = totalBallH / 7.818f;
        float rowSpacing = R * 0.6f;
        float colSpacing = R * 0.8f;

        // --- 4. 确定几何水平偏移 (offsetX) ---
        // 按照勾股定理计算咬合距离
        float d = (float) Math.sqrt(Math.pow(2 * R, 2) - Math.pow(R + rowSpacing / 2f, 2));
        float offsetX = d + (R * 0.5f);

        // --- 5. 水平黄金比例布局 (整体居中且偏向 1:0.618) ---
        // 整体宽度 = 照片宽 + 偏移 + 信息区宽（取文字宽和色块区宽的最大值）
        float paletteTotalW = (R * 4 + colSpacing);
        float infoAreaW = Math.max(finalDeviceW, paletteTotalW);
        float totalContentW = targetW + offsetX + infoAreaW;

        // 计算整体左起始点
        int startX = (int) ((W - totalContentW) / 1.618f);
        int infoXStart = (int) (startX + targetW + offsetX);

        // --- 6. 最终渲染 ---

        // A. 绘制照片
        canvas.drawBitmap(resized, startX, imgY, paint);

        // B. 绘制水印 (确保跟随照片)
        String watermarkValue = getSafeInfo(info, "show_watermark", "false");
        if ("true".equalsIgnoreCase(watermarkValue) || "1".equals(watermarkValue)) {
            String signText = getSafeInfo(info, "sign", "").trim();
            if (!signText.isEmpty()) {
                Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                wp.setTypeface(loadFont(context, "Regular"));
                wp.setTextSize(60f);
                wp.setColor(Color.argb(220, 100, 100, 100));
                wp.setShadowLayer(5, 2, 2, Color.argb(180, 200, 200, 200));
                wp.getTextBounds(signText, 0, signText.length(), bounds);
                canvas.drawText(signText, startX + targetW - bounds.width() - 60, imgY + targetH - 60, wp);
            }
        }

        // C. 绘制动态色块 (左边缘切线对齐 infoXStart)
        float originY = imgY + (R * 0.618f) + R;
        for (int i = 0; i < palette.size(); i++) {
            int row = i % 3, col = i / 3;
            float cx = infoXStart + R + (col * (R * 2 + colSpacing));
            float cy = originY + row * (R * 2 + rowSpacing);
            paint.setColor(palette.get(i));
            canvas.drawCircle(cx, cy, R, paint);
        }

        // D. 绘制装饰横线 (宽度与文字/色块对齐)
        paint.setColor(Color.rgb(200, 200, 200));
        paint.setStrokeWidth(3);
        canvas.drawLine(infoXStart, lineY, infoXStart + infoAreaW, lineY, paint);

        // E. 绘制设备型号信息 (对齐 infoXStart)
        paint.setTypeface(loadFont(context, "Black"));
        paint.setTextSize(currentDeviceFontSize);
        paint.setColor(Color.rgb(30, 30, 30));
        canvas.drawText(deviceText, infoXStart, deviceY, paint);

        // F. 绘制镜头信息
        paint.setTypeface(loadFont(context, "Bold"));
        paint.setTextSize(lensFontSize);
        paint.setColor(Color.rgb(80, 80, 80));
        canvas.drawText(lensText, infoXStart, lensY, paint);

        // G. 绘制拍摄参数
        paint.setTypeface(loadFont(context, "Regular"));
        paint.setTextSize(paramFontSize);
        paint.setColor(Color.rgb(140, 140, 140));
        canvas.drawText(paramText, infoXStart, paramY, paint);
    }

    private static float calculateFontSize(Paint paint, String text, float maxWidth, int defaultSize, int minSize) {
        if (text == null || text.isEmpty()) {
            return defaultSize;
        }

        Rect bounds = new Rect();
        paint.setTextSize(defaultSize);
        paint.getTextBounds(text, 0, text.length(), bounds);
        float textWidth = bounds.width();

        if (textWidth <= maxWidth) {
            return defaultSize;
        }

        float scale = maxWidth / textWidth;
        float result;

        // 智能缩放策略[cite: 12]
        if (text.length() <= 12) {
            result = defaultSize * scale * 0.98f;
        } else if (text.length() <= 25) {
            result = defaultSize * scale;
        } else {
            result = defaultSize * (float) Math.pow(scale, 0.85);
        }

        return Math.max(result, (float)minSize);
    }

    private static void renderPortrait(Context context, Canvas canvas, Bitmap img, List<Integer> palette, Map<String, String> info, Paint paint) {
        // --- 1. 图像缩放与基础布局 ---
        int targetH = (int) (H * 0.62);
        int targetW = img.getWidth() * targetH / img.getHeight();
        Bitmap resized = Bitmap.createScaledBitmap(img, targetW, targetH, true);

        int gap = 150;
        int paletteAreaW = targetW;
        int startX = (W - (targetW + gap + paletteAreaW)) / 2;

        // --- 核心修复：文字获取逻辑 ---
        String deviceText = getSafeInfo(info, "device", "ILCE-7CM2").toUpperCase();
        String lensText = getSafeInfo(info, "lens", "LENS").toUpperCase();

        // 在 PosterRenderer 中查找组装 paramText 的位置
        // 核心修复：确保按照以下顺序查找字段
        String f = getSafeInfo(info, "f", getSafeInfo(info, "exif_f", "f/2.8"));
        String s = getSafeInfo(info, "s", getSafeInfo(info, "exif_s", "1/100s"));
        String iso = getSafeInfo(info, "iso", getSafeInfo(info, "exif_iso", "ISO 100"));

        // 重新组合，这样无论是手动改的 "f" 还是 Exif 存的 "f" 都能被正确读取
        String paramText = (s + "  " + f + "  " + iso).toUpperCase();

        // --- 3. 精确测量文字高度 ---
        Rect bounds = new Rect();

        paint.setTypeface(loadFont(context, "Bold"));
        float lensFontSize = 85;
        paint.setTextSize(lensFontSize);
        paint.getTextBounds(lensText, 0, lensText.length(), bounds);
        float lensVisualHeight = bounds.height();

        paint.setTypeface(loadFont(context, "Regular"));
        float paramFontSize = 65;
        paint.setTextSize(paramFontSize);
        paint.getTextBounds(paramText, 0, paramText.length(), bounds);
        float paramVisualHeight = bounds.height(); // 圆直径（偏移量）

        int rightLineGap = 60;
        float rightPartTotalHeight = lensVisualHeight + rightLineGap + paramVisualHeight;

        // --- 4. 核心布局计算：黄金分割与切线偏移 ---
        float offset = paramVisualHeight;

        paint.setTypeface(loadFont(context, "Black"));
        float deviceFontSize = 160;
        float deviceMaxWidth = (targetW * 0.95f) - offset;
        paint.setTextSize(deviceFontSize);
        paint.getTextBounds(deviceText, 0, deviceText.length(), bounds);

        if (bounds.width() > deviceMaxWidth) {
            deviceFontSize = calculateFontSize(paint, deviceText, deviceMaxWidth, 160, 40);
            paint.setTextSize(deviceFontSize);
            paint.getTextBounds(deviceText, 0, deviceText.length(), bounds);
        }
        float deviceVisualHeight = bounds.height();

        float gapBetweenPhotoAndText = lensVisualHeight;
        float textSectionHeight = Math.max(deviceVisualHeight, rightPartTotalHeight);

        float totalContentHeight = targetH + gapBetweenPhotoAndText + textSectionHeight;
        float marginTop = (H - totalContentHeight) / 1.618f;

        int imgY = (int) marginTop;
        float imgBottom = imgY + targetH;
        float textCenterY = imgBottom + gapBetweenPhotoAndText + (textSectionHeight / 2);

        // --- 5. 绘制图像与色块 ---
        canvas.drawBitmap(resized, startX, imgY, paint);

        int itemGap = 20;
        int blockW = (paletteAreaW - itemGap) / 2;
        int blockH = (targetH - 2 * itemGap) / 3;
        int pxStart = startX + targetW + gap;

        for (int i = 0; i < palette.size(); i++) {
            int row = i / 2, col = i % 2;
            int bx = pxStart + col * (blockW + itemGap);
            int by = imgY + row * (blockH + itemGap);

            paint.setColor(palette.get(i));
            canvas.drawRect(bx, by, bx + blockW, by + blockH, paint);

            String hex = String.format("#%06X", (0xFFFFFF & palette.get(i)));
            paint.setTypeface(loadFont(context, "Medium"));
            paint.setTextSize(40);
            paint.setColor(isDark(palette.get(i)) ? Color.WHITE : Color.rgb(60, 60, 60));
            paint.setTextAlign(Paint.Align.CENTER);

            Rect hexBounds = new Rect();
            paint.getTextBounds(hex, 0, hex.length(), hexBounds);
            canvas.drawText(hex, bx + blockW / 2, (by + blockH / 2) + (hexBounds.height() / 2), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        // --- 竖版水印添加 ---
        // --- 水印绘制开始 ---
        String watermarkValue = getSafeInfo(info, "show_watermark", "false");
        boolean showWatermark = "true".equalsIgnoreCase(watermarkValue) || "1".equals(watermarkValue);
        String signText = getSafeInfo(info, "sign", "").trim();

        Log.d("PosterRenderer", "Watermark Debug (Portrait) - showWatermark: " + showWatermark + ", signText: '" + signText + "', watermarkValue: '" + watermarkValue + "'");

        if (showWatermark && !signText.isEmpty()) {
            Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            watermarkPaint.setTypeface(loadFont(context, "Regular"));
            watermarkPaint.setTextSize(60f); // 增加到 60f 以提高可见性
            watermarkPaint.setColor(Color.argb(220, 100, 100, 100)); // 改为深灰色，不透明度220

            // 增大阴影效果以提高对比度
            watermarkPaint.setShadowLayer(5, 2, 2, Color.argb(180, 200, 200, 200));

            // 计算位置：照片区域的右下角，留 60 像素边距
            float margin = 60f;
            Rect textBounds = new Rect();
            watermarkPaint.getTextBounds(signText, 0, signText.length(), textBounds);
            float x = startX + targetW - textBounds.width() - margin;
            float y = imgY + targetH - margin;

            canvas.drawText(signText, x, y, watermarkPaint);
            
            Log.d("PosterRenderer", "Watermark drawn (Portrait) at x: " + x + ", y: " + y);
        }
// --- 水印绘制结束 ---

        // --- 6. 绘制底部文字（应用切线偏移） ---

        paint.setTypeface(loadFont(context, "Black"));
        paint.setTextSize(deviceFontSize);
        paint.setColor(Color.rgb(30, 30, 30));
        canvas.drawText(deviceText, startX + offset, textCenterY + (deviceVisualHeight / 2), paint);

        float lensY = textCenterY - (rightPartTotalHeight / 2) + lensVisualHeight;
        float paramY = textCenterY + (rightPartTotalHeight / 2);

        paint.setTypeface(loadFont(context, "Bold"));
        paint.setTextSize(lensFontSize);
        paint.setColor(Color.rgb(80, 80, 80));
        canvas.drawText(lensText, pxStart + offset, lensY, paint);

        paint.setTypeface(loadFont(context, "Regular"));
        paint.setTextSize(paramFontSize);
        paint.setColor(Color.rgb(120, 120, 120));
        canvas.drawText(paramText, pxStart + offset, paramY, paint);

        // --- 7. 绘制装饰竖线 ---
        paint.setColor(Color.rgb(200, 200, 200));
        paint.setStrokeWidth(3);
        float lineX = startX + targetW + (gap / 2);
        float lineHalfHeight = (textSectionHeight / 2) + 20;
        canvas.drawLine(lineX, textCenterY - lineHalfHeight, lineX, textCenterY + lineHalfHeight, paint);
    }

    private static float calculateFontSizeForHeight(Paint paint, String text, float maxWidth, float targetHeight, int maxSize, int minSize) {
        if (text == null || text.isEmpty()) {
            return minSize;
        }
        
        // 二分查找合适的字体大小
        float low = minSize;
        float high = maxSize;
        float result = minSize;
        
        for (int i = 0; i < 20; i++) { // 迭代20次足以得到精确值
            float mid = (low + high) / 2;
            paint.setTextSize(mid);
            
            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);
            float currentHeight = bounds.height();
            float currentWidth = bounds.width();
            
            // 检查宽度约束
            if (currentWidth > maxWidth) {
                high = mid;
                continue;
            }
            
            // 调整字体大小以匹配目标高度
            if (currentHeight < targetHeight) {
                low = mid;
                result = mid;
            } else if (currentHeight > targetHeight) {
                high = mid;
            } else {
                result = mid;
                break;
            }
        }
        
        return result;
    }

    // --- 工具方法 ---

    private static Typeface loadFont(Context context, String weight) {
        try {
            // 确保你的 assets/fonts 目录下有这些文件[cite: 5]
            return Typeface.createFromAsset(context.getAssets(), "fonts/HarmonyOS_Sans_SC_" + weight + ".ttf");
        } catch (Exception e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    private static String getSafeInfo(Map<String, String> info, String key, String def) {
        String val = info.get(key);
        return (val == null || val.isEmpty()) ? def : val;
    }

    private static boolean isDark(int color) {
        // 基于感知亮度公式：R*0.299 + G*0.587 + B*0.114[cite: 5]
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness > 0.41; // 对应 Python 阈值 150
    }

    /**
     * 加载自定义字体
     */
    // 放在 ColorExtractor.java 中
    private static int[] listToArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

}

