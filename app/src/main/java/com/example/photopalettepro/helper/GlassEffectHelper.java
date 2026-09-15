package com.example.photopalettepro.helper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.drawable.Drawable;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * 玻璃效果助手类
 * 职责：为背景视图应用真实的背景模糊（RenderEffect，仅 Android 12+），
 * 从而实现 iOS 风格的磨砂玻璃质感。低版本系统自动降级为半透明表面。
 */
public final class GlassEffectHelper {

    private GlassEffectHelper() {
    }

    /**
     * 给视图应用磨砂模糊效果（Android 12 / API 31 及以上）。
     * 在低版本上静默跳过，由半透明的玻璃表面兜底。
     *
     * @param view   需要模糊的视图（通常是铺满全屏的背景图）
     * @param radius 模糊半径（像素）
     */
    public static void applyBackdropBlur(View view, float radius) {
        if (view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {
                // 某些设备可能不支持，忽略并回退到半透明表面
            }
        }
    }

    /**
     * 移除视图上的模糊效果
     */
    public static void clearBlur(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null);
        }
    }

    /**
     * 给背景图提一档饱和度。
     *
     * <p>参考实现（rdev/liquid-glass-react）里 saturation 默认 140——
     * 这不是调色偏好，是<b>液态玻璃的物理前提</b>：
     * 玻璃本身几乎不着色，你看到的颜色全部来自它背后被折射的东西。
     * 背景一灰，玻璃就退化成一块白板；背景有颜色，卡片才"活"。
     *
     * <p>用 {@link ColorMatrixColorFilter} 而不是 RenderEffect：
     * 它在所有 API 上都能用，而且和模糊效果互不干扰（两个 RenderEffect
     * 只能有一个，颜色矩阵则不受这个限制）。
     *
     * @param saturation 1.0 = 不变；1.35 左右是"看得出彩但不假"的档
     * @param brighten   整体加多少亮度（0~255），补一点被模糊吃掉的通透感
     */
    public static void applyBackdropSaturation(View view, float saturation, float brighten) {
        if (view == null || view instanceof android.widget.ImageView == false) return;

        // 亮度权重取 Rec.709——和 Android 自身灰度化用的系数一致
        final float lumR = 0.213f;
        final float lumG = 0.715f;
        final float lumB = 0.072f;
        float inv = 1f - saturation;
        float a = inv * lumR;
        float b = inv * lumG;
        float c = inv * lumB;

        float[] matrix = {
                a + saturation, b, c, 0, brighten,
                a, b + saturation, c, 0, brighten,
                a, b, c + saturation, 0, brighten,
                0, 0, 0, 1, 0,
        };

        try {
            ((android.widget.ImageView) view).setColorFilter(
                    new android.graphics.ColorMatrixColorFilter(matrix));
        } catch (Throwable ignored) {
            // 调色失败不影响别的
        }
    }

    // ====================================================================
    //  背景快照：液态玻璃"折"的就是它
    // ====================================================================

    /**
     * 拍一张背景快照，供 {@link LiquidGlassDrawable} 折射。
     *
     * <p>玻璃要折的就是这个东西，所以它必须<b>盖住整个屏幕</b>——
     * 卡片滚动时按自己的位置从里面取对应的一块。
     *
     * <p>尺寸压得很小（调用方传屏幕的 1/6 即可）：一是省内存，
     * 二是放大回来自带一层柔化，正好是玻璃该有的"糊"。
     *
     * @param background 底色（渐变 + 色雾）
     * @param photo      已导入的照片；没有就传 null
     * @param photoAlpha 照片叠在底色上的不透明度
     */
    public static Bitmap buildBackdropSnapshot(int width, int height, Drawable background,
                                               Bitmap photo, float photoAlpha) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        Bitmap snapshot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(snapshot);

        if (background != null) {
            background.setBounds(0, 0, w, h);
            background.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }

        if (photo != null && !photo.isRecycled()) {
            drawCenterCrop(canvas, photo, w, h, photoAlpha);
        }

        // 玻璃背后不该有清晰的东西。
        //
        // 半径取"短边/90"：快照是屏幕的 1/6，折回全尺寸约等于 14px 的模糊——
        // 参考实现是 blur(12 + blurAmount*32) = 14px。之前用了 1/12，
        // 折回全尺寸超过 100px，背景被糊成一片灰，玻璃自然就"一点都不通透"了。
        boxBlur(snapshot, Math.max(2, Math.min(w, h) / 90));

        // saturate(140%)：参考实现里背景滤镜的另一半。
        // 玻璃本身不着色，饱和度全靠这一步把底色"提"出来。
        saturatePixels(snapshot, 1.4f);
        return snapshot;
    }

    /**
     * 就地提饱和度（Rec.709 亮度权重，和 {@link #applyBackdropSaturation} 同一套系数）。
     *
     * <p>快照是几十像素的小图，逐像素算完全无所谓；
     * 也正因为小，这一步比再套一层 ColorFilter 省事得多。
     */
    public static void saturatePixels(Bitmap bitmap, float saturation) {
        if (bitmap == null || bitmap.isRecycled()) return;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        final float lumR = 0.213f;
        final float lumG = 0.715f;
        final float lumB = 0.072f;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            float lum = lumR * r + lumG * g + lumB * b;

            int nr = clampChannel(lum + (r - lum) * saturation);
            int ng = clampChannel(lum + (g - lum) * saturation);
            int nb = clampChannel(lum + (b - lum) * saturation);

            pixels[i] = (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static int clampChannel(float value) {
        if (value <= 0f) return 0;
        if (value >= 255f) return 255;
        return Math.round(value);
    }

    /** 按 centerCrop 把照片铺满，带整体透明度。 */
    private static void drawCenterCrop(Canvas canvas, Bitmap photo, int w, int h, float alpha) {
        float scale = Math.max(w / (float) photo.getWidth(), h / (float) photo.getHeight());
        float dw = photo.getWidth() * scale;
        float dh = photo.getHeight() * scale;
        float left = (w - dw) / 2f;
        float top = (h - dh) / 2f;

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
        canvas.drawBitmap(photo, null, new RectF(left, top, left + dw, top + dh), paint);
    }

    /**
     * 盒式模糊，就地改。
     *
     * <p>用可分离的两趟（先横后竖）而不是二维卷积：半径 r 时复杂度从
     * O(n·r²) 降到 O(n·r)，在 1/6 尺寸的快照上基本感觉不到。
     */
    public static void boxBlur(Bitmap bitmap, int radius) {
        if (bitmap == null || bitmap.isRecycled() || radius < 1) return;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w < 2 || h < 2) return;

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] temp = new int[w * h];

        blurPass(pixels, temp, w, h, radius, true);
        blurPass(temp, pixels, w, h, radius, false);

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static void blurPass(int[] src, int[] dst, int w, int h, int radius,
                                 boolean horizontal) {
        int outer = horizontal ? h : w;
        int inner = horizontal ? w : h;
        int window = radius * 2 + 1;

        for (int o = 0; o < outer; o++) {
            long a = 0, r = 0, g = 0, b = 0;

            // 先把窗口铺满
            for (int i = -radius; i <= radius; i++) {
                int p = pixelAt(src, w, o, clamp(i, inner), horizontal);
                a += (p >>> 24) & 0xFF;
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
            }

            for (int i = 0; i < inner; i++) {
                int value = ((int) (a / window) << 24) | ((int) (r / window) << 16)
                        | ((int) (g / window) << 8) | (int) (b / window);
                if (horizontal) {
                    dst[o * w + i] = value;
                } else {
                    dst[i * w + o] = value;
                }

                // 滑动窗口：去掉最左、加上最右
                int out = pixelAt(src, w, o, clamp(i - radius, inner), horizontal);
                int in = pixelAt(src, w, o, clamp(i + radius + 1, inner), horizontal);

                a += ((in >>> 24) & 0xFF) - ((out >>> 24) & 0xFF);
                r += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    private static int pixelAt(int[] pixels, int w, int outer, int inner, boolean horizontal) {
        return horizontal ? pixels[outer * w + inner] : pixels[inner * w + outer];
    }

    private static int clamp(int value, int max) {
        if (value < 0) return 0;
        if (value >= max) return max - 1;
        return value;
    }
}




