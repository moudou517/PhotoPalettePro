package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

import java.util.Random;

/**
 * 程序化 alpha 遮罩。
 *
 * 刻意不使用 {@code BlurMaskFilter}——它在不同版本/不同渲染管线下表现不一致，
 * 这里改为在低分辨率上直接算 alpha 场，再双线性放大，效果稳定且可控。
 *
 * 提供两种遮罩：
 * <ul>
 *   <li>{@link #dissolve} —— 整体溶解软边，让主元素自然融进纸面；</li>
 *   <li>{@link #torn} —— 手撕纤维边，承担「照片与纸面」之间的材料交接。</li>
 * </ul>
 */
public final class TornEdgeMask {

    private TornEdgeMask() {
    }

    // ================================================================
    //  溶解软边（主元素外缘融进纸面）
    // ================================================================

    /**
     * @param rect         内容矩形
     * @param featherRatio 羽化宽度占内容短边的比例（0.15 ≈ 柔和消散）
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
    //  手撕纤维边
    // ================================================================

    /**
     * 手撕纤维边遮罩：把面板指定的一侧（或两侧）改成不规则的撕裂轮廓，
     * 并在轮廓外侧留一圈「露出的纸纤维」毛边。
     *
     * <p>依据 gathered-scenes-zine 的《Photo–Illustration Edge Transition》：
     * 不规则的手撕轮廓（浅缺口 / 起伏 / 软扇贝 / 偶发长纤维拉扯）、
     * 窄的纤维毛边（约占短边 1–4%）、自然的不对称，
     * 以及边缘处的轻微磨损与断墨——而不是干净的数字剪切路径。
     *
     * @param panel      面板矩形
     * @param tearRight  是否撕裂右边
     * @param tearBottom 是否撕裂下边
     */
    public static Bitmap torn(int outW, int outH, RectF panel,
                              boolean tearRight, boolean tearBottom, long seed) {
        int mw = 240;
        int mh = Math.max(2, Math.round(240f * outH / outW));
        float sx = (float) outW / mw;
        float sy = (float) outH / mh;

        float shortEdge = Math.min(panel.width(), panel.height());
        float tearAmplitude = shortEdge * 0.085f;   // 撕裂起伏幅度
        float fringe = shortEdge * 0.040f;          // 纤维毛边宽度

        Random random = new Random(seed);
        float[] rightInset = tearRight ? tornProfile(mh, tearAmplitude, random) : null;
        float[] bottomInset = tearBottom ? tornProfile(mw, tearAmplitude, random) : null;

        int[] pixels = new int[mw * mh];
        for (int y = 0; y < mh; y++) {
            float fy = (y + 0.5f) * sy;
            float rightBound = tearRight ? panel.right - rightInset[y] : panel.right;

            for (int x = 0; x < mw; x++) {
                float fx = (x + 0.5f) * sx;
                float bottomBound = tearBottom ? panel.bottom - bottomInset[x] : panel.bottom;

                float inside = Math.min(
                        Math.min(fx - panel.left, rightBound - fx),
                        Math.min(fy - panel.top, bottomBound - fy));

                float speck = hashNoise(x, y, seed);
                float alpha;
                if (inside >= 0f) {
                    // 面板内侧：撕裂边缘处轻微磨损/断墨
                    float wear = inside < fringe * 0.6f
                            ? 0.86f + 0.14f * speck
                            : 1f;
                    alpha = wear;
                } else {
                    // 面板外侧：露出的纸纤维，越往外越稀疏
                    float t = PostcardPalette.clamp01(1f + inside / fringe);
                    alpha = t * t * (0.22f + 0.78f * speck);
                }
                pixels[y * mw + x] = Color.argb((int) (PostcardPalette.clamp01(alpha) * 255f), 255, 255, 255);
            }
        }
        return upscale(pixels, mw, mh, outW, outH);
    }

    /**
     * 撕裂轮廓：低频决定大形态（长段偏直），中高频给出软扇贝与毛刺，
     * 再叠加一到两处更深的「纤维拉扯」压力点，保证不对称。
     */
    private static float[] tornProfile(int length, float amplitude, Random random) {
        float[] low = smoothRandom(length, 5, random);
        float[] mid = smoothRandom(length, 13, random);
        float[] high = smoothRandom(length, 31, random);

        float[] profile = new float[length];
        for (int i = 0; i < length; i++) {
            profile[i] = amplitude * (0.55f * low[i] + 0.30f * mid[i] + 0.15f * high[i]);
        }

        int pulls = 1 + random.nextInt(2);
        int width = Math.max(2, length / 18);
        for (int p = 0; p < pulls; p++) {
            int centre = random.nextInt(length);
            for (int k = -width; k <= width; k++) {
                int i = centre + k;
                if (i < 0 || i >= length) continue;
                float falloff = 1f - Math.abs(k) / (float) width;
                profile[i] += amplitude * 0.9f * falloff * falloff;
            }
        }
        return profile;
    }

    /** 由若干控制点插值出的平滑随机曲线（控制点越少，形态越平顺）。 */
    private static float[] smoothRandom(int length, int segments, Random random) {
        float[] knots = new float[segments + 1];
        for (int i = 0; i <= segments; i++) knots[i] = random.nextFloat();

        float[] out = new float[length];
        for (int i = 0; i < length; i++) {
            float t = i * segments / (float) Math.max(1, length - 1);
            int k = Math.min(segments - 1, (int) Math.floor(t));
            float f = t - k;
            f = f * f * (3f - 2f * f);
            out[i] = knots[k] * (1 - f) + knots[k + 1] * f;
        }
        return out;
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

    /** 双线性插值的平滑噪声。 */
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

    /** 逐像素白噪声，放大后成为细纤维颗粒。 */
    private static float hashNoise(int x, int y, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + y * 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        h *= 0x7FEB352D;
        h ^= (h >>> 29);
        return (h & 0xFFFF) / 65535f;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}
