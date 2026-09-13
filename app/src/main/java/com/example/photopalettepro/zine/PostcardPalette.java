package com.example.photopalettepro.zine;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 颜色工具。
 *
 * 对应 gathered-scenes-zine 的「色彩成结构」：
 * k-means 聚出的质心天然偏灰，直接用会显得寡淡，
 * 所以这里提供「提纯」与「选构成色」两类操作——
 * 前者把已有色彩的纯度拉回来，后者挑出一个真正的高纯度色做构成骨架。
 */
public final class PostcardPalette {

    private PostcardPalette() {
    }

    /** 纯度低于此值时视为中性色，不做提纯（避免把灰调成彩色）。 */
    private static final float NEUTRAL_SATURATION = 0.10f;

    /**
     * 提纯：提高饱和度（必要时略微提亮）。
     * 只对本来就有色彩的颜色生效，中性色保持原样。
     */
    public static int boostChroma(int color, float saturationGain, float valueGain) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] >= NEUTRAL_SATURATION) {
            hsv[1] = clamp01(hsv[1] * saturationGain);
            hsv[2] = clamp01(hsv[2] * valueGain);
        }
        return Color.HSVToColor(hsv);
    }

    /** 整条色板统一提纯。 */
    public static List<Integer> boostAll(List<Integer> palette, float saturationGain, float valueGain) {
        List<Integer> out = new ArrayList<>();
        if (palette == null) return out;
        for (int c : palette) {
            out.add(boostChroma(c, saturationGain, valueGain));
        }
        return out;
    }

    /**
     * 选出「构成色」：色板里最鲜艳的一个，并把纯度推到一个明确的高位。
     *
     * <p>当前渲染流程没有直接使用它——「色彩成结构」是通过
     * {@link #boostAll} 对整条色板做适度提纯来承担的。
     * 保留此方法是为了后续需要「一个明确的结构色」时可以取用。
     */
    public static int structuralColor(List<Integer> palette, int fallback) {
        if (palette == null || palette.isEmpty()) return fallback;

        int best = palette.get(0);
        float bestScore = -1f;
        for (int c : palette) {
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            float score = hsv[1] * (0.35f + 0.65f * hsv[2]);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }

        float[] hsv = new float[3];
        Color.colorToHSV(best, hsv);
        // 推到明确的高纯度；亮度保持在能压得住纸面的区间
        hsv[1] = Math.max(hsv[1], 0.72f);
        hsv[2] = clamp(hsv[2], 0.45f, 0.88f);
        return Color.HSVToColor(hsv);
    }

    /** 感知亮度 0..255 */
    public static float luminance(int color) {
        return 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color);
    }

    public static int nearest(List<Integer> colors, int target) {
        int best = colors.get(0);
        double min = Double.MAX_VALUE;
        for (int c : colors) {
            double d = distance(c, target);
            if (d < min) {
                min = d;
                best = c;
            }
        }
        return best;
    }

    public static double distance(int c1, int c2) {
        double dr = Color.red(c1) - Color.red(c2);
        double dg = Color.green(c1) - Color.green(c2);
        double db = Color.blue(c1) - Color.blue(c2);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** 线性混色，t=0 取 base，t=1 取 over。 */
    public static int blend(int base, int over, float t) {
        t = clamp01(t);
        return Color.rgb(
                (int) (Color.red(base) * (1 - t) + Color.red(over) * t),
                (int) (Color.green(base) * (1 - t) + Color.green(over) * t),
                (int) (Color.blue(base) * (1 - t) + Color.blue(over) * t));
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * 从像素里聚出 k 个颜色（采样 + k-means）。
     * 只在外部没有提供色板时作为兜底，保证平涂用的颜色一定忠于原图。
     */
    public static List<Integer> quantize(int[] pixels, int k) {
        int step = Math.max(1, pixels.length / 3000);
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < pixels.length; i += step) samples.add(pixels[i]);
        if (samples.isEmpty()) samples.add(Color.GRAY);

        int n = Math.max(1, Math.min(k, samples.size()));
        java.util.Random random = new java.util.Random(7);
        int[][] centroids = new int[n][3];
        for (int i = 0; i < n; i++) {
            int c = samples.get(random.nextInt(samples.size()));
            centroids[i] = new int[]{Color.red(c), Color.green(c), Color.blue(c)};
        }

        for (int iteration = 0; iteration < 8; iteration++) {
            long[] sumR = new long[n], sumG = new long[n], sumB = new long[n];
            int[] count = new int[n];
            for (int c : samples) {
                int best = 0;
                double bestDistance = Double.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    int dr = Color.red(c) - centroids[j][0];
                    int dg = Color.green(c) - centroids[j][1];
                    int db = Color.blue(c) - centroids[j][2];
                    double d = dr * dr + dg * dg + db * db;
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = j;
                    }
                }
                sumR[best] += Color.red(c);
                sumG[best] += Color.green(c);
                sumB[best] += Color.blue(c);
                count[best]++;
            }
            for (int j = 0; j < n; j++) {
                if (count[j] == 0) continue;
                centroids[j] = new int[]{
                        (int) (sumR[j] / count[j]),
                        (int) (sumG[j] / count[j]),
                        (int) (sumB[j] / count[j])};
            }
        }

        List<Integer> result = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            result.add(Color.rgb(centroids[j][0], centroids[j][1], centroids[j][2]));
        }
        return result;
    }
}
