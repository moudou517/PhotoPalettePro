package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

import java.util.Random;

/**
 * 程序化 alpha 遮罩。
 *
 * 刻意不使用 {@code BlurMaskFilter}——它在不同版本 / 不同渲染管线下表现不一致，
 * 这里改为在低分辨率上直接算 alpha 场，再双线性放大，效果稳定且可控。
 */
public final class TornEdgeMask {

    private TornEdgeMask() {
    }

    /**
     * 柔化羽边：内容向纸面自然消散。
     *
     * <p>用于两处：主元素外缘融进纸面；以及「增强现实」嵌入原照片时的替换边缘——
     * 该边缘必须柔和，所以这里的 {@code featherRatio} 会取得比较大（约 0.26~0.30）。
     *
     * @param rect         内容矩形
     * @param featherRatio 羽化宽度占内容短边的比例
     */
    public static Bitmap dissolve(int outW, int outH, RectF rect, float featherRatio, long seed) {
        int mw = 180;
        int mh = Math.max(2, Math.round(180f * outH / outW));
        float sx = (float) outW / mw;
        float sy = (float) outH / mh;

        float feather = Math.min(rect.width(), rect.height()) * featherRatio;

        Random random = new Random(seed);
        float[][] noise = noiseGrid(9, 9, random);

        int[] pixels = new int[mw * mh];
        for (int y = 0; y < mh; y++) {
            float fy = (y + 0.5f) * sy;
            for (int x = 0; x < mw; x++) {
                float fx = (x + 0.5f) * sx;
                float inside = Math.min(
                        Math.min(fx - rect.left, rect.right - fx),
                        Math.min(fy - rect.top, rect.bottom - fy));
                float n = smoothNoise(noise, x / (float) mw, y / (float) mh);
                float t = PostcardPalette.clamp01(
                        (inside + (n - 0.5f) * feather * 1.20f) / feather);
                pixels[y * mw + x] = Color.argb((int) (smoothstep(t) * 255f), 255, 255, 255);
            }
        }
        return upscale(pixels, mw, mh, outW, outH);
    }

    // ================================================================
    //  工具
    // ================================================================

    private static Bitmap upscale(int[] pixels, int mw, int mh, int outW, int outH) {
        Bitmap small = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888);
        small.setPixels(pixels, 0, mw, 0, 0, mw, mh);
        Bitmap mask = Bitmap.createScaledBitmap(small, outW, outH, true);
        if (mask != small && !small.isRecycled()) small.recycle();
        return mask;
    }

    private static float[][] noiseGrid(int w, int h, Random random) {
        float[][] grid = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid[y][x] = random.nextFloat();
        }
        return grid;
    }

    /** 双线性插值的平滑噪声，让羽化边缘不规则而非几何对称。 */
    private static float smoothNoise(float[][] grid, float u, float v) {
        int nh = grid.length;
        int nw = grid[0].length;
        float gx = PostcardPalette.clamp01(u) * (nw - 1);
        float gy = PostcardPalette.clamp01(v) * (nh - 1);

        int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy);
        int x1 = Math.min(nw - 1, x0 + 1), y1 = Math.min(nh - 1, y0 + 1);
        float tx = gx - x0, ty = gy - y0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);

        float a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx;
        float b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx;
        return a * (1 - ty) + b * ty;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}
