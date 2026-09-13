package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

/**
 * 简笔线稿引擎。
 *
 * 对应 photo-abstract-editorial 的「先读作克制的抽象」，
 * 也对应 gathered-scenes-zine 的「插画成场」——插画只保留结构，
 * 不去描摹照片的每一处细节。
 *
 * 流程：
 * <pre>
 *   低分辨率灰度 → 5 点平滑 → Sobel 梯度
 *   → 分位数自适应阈值（只留最强结构边缘，保证「简」）
 *   → 沿梯度做非极大值抑制，把 2~3px 厚的边缘细化成 1px
 *   → 补缝，避免线条断成珠子
 *   → 在输出分辨率上沿切线方向重绘「长短/粗细/深浅都带抖动」的短笔触
 * </pre>
 *
 * 注意：必须在低分辨率上细化再做笔触重绘。若省略非极大值抑制，
 * 一条 2~3px 厚的边缘会被画成好几道平行笔触，看起来毛躁。
 */
public final class SketchLineRenderer {

    /** 边缘检测用的工作宽度 */
    private static final int DETECT_WIDTH = 300;
    /** 只保留梯度强度前 18% 的像素。放宽比例＝保留更多高频结构，线稿更「有信息」 */
    private static final float MAGNITUDE_PERCENTILE = 0.82f;
    /** 梯度强度的绝对下限，避免平坦图片产生满屏噪点 */
    private static final int MIN_THRESHOLD = 18;

    private SketchLineRenderer() {
    }

    /**
     * @param inkColor 线条颜色
     * @return 输出分辨率下的线稿层（透明底，只有笔触）
     */
    public static Bitmap render(Bitmap photo, int outW, int outH, int inkColor, long seed) {
        int dw = DETECT_WIDTH;
        int dh = Math.max(2, Math.round(dw * (float) photo.getHeight() / photo.getWidth()));

        int count = dw * dh;
        int[] pixels = new int[count];
        Bitmap detect = Bitmap.createScaledBitmap(photo, dw, dh, true);
        detect.getPixels(pixels, 0, dw, 0, 0, dw, dh);
        if (detect != photo && !detect.isRecycled()) detect.recycle();

        int[] gray = toGray(pixels);
        gray = boxBlur(gray, dw, dh);

        int[] gx = new int[count];
        int[] gy = new int[count];
        int[] magnitude = new int[count];
        sobel(gray, dw, dh, gx, gy, magnitude);

        int threshold = Math.max(MIN_THRESHOLD,
                percentile(magnitude, MAGNITUDE_PERCENTILE));

        boolean[] ridge = thinRidges(magnitude, gx, gy, dw, dh, threshold);
        boolean[] draw = fillGaps(ridge, dw, dh);

        return strokeOver(draw, gx, gy, dw, dh, outW, outH, inkColor, seed);
    }

    // ---------------------------------------------------------------- 检测

    private static int[] toGray(int[] pixels) {
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            gray[i] = (int) (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p));
        }
        return gray;
    }

    /** 5 点均值平滑，压掉高频噪点，避免边缘检测抓到颗粒。 */
    private static int[] boxBlur(int[] src, int w, int h) {
        int[] out = new int[src.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                out[i] = (src[i] + src[i - 1] + src[i + 1] + src[i - w] + src[i + w]) / 5;
            }
        }
        for (int x = 0; x < w; x++) {
            out[x] = src[x];
            out[(h - 1) * w + x] = src[(h - 1) * w + x];
        }
        for (int y = 0; y < h; y++) {
            out[y * w] = src[y * w];
            out[y * w + w - 1] = src[y * w + w - 1];
        }
        return out;
    }

    private static void sobel(int[] gray, int w, int h, int[] gx, int[] gy, int[] magnitude) {
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int dx = gray[i + 1] - gray[i - 1];
                int dy = gray[i + w] - gray[i - w];
                gx[i] = dx;
                gy[i] = dy;
                magnitude[i] = (int) Math.sqrt((double) dx * dx + (double) dy * dy);
            }
        }
    }

    /**
     * 非极大值抑制：沿梯度方向只保留局部最大的那个像素。
     * 这一步让每条轮廓只剩一条线，是线稿「干净」的关键。
     */
    private static boolean[] thinRidges(int[] magnitude, int[] gx, int[] gy,
                                        int w, int h, int threshold) {
        boolean[] ridge = new boolean[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if (magnitude[i] < threshold) continue;

                double norm = Math.hypot(gx[i], gy[i]);
                if (norm < 1e-3) continue;
                int ax = (int) Math.round(gx[i] / norm);
                int ay = (int) Math.round(gy[i] / norm);
                if (ax == 0 && ay == 0) continue;

                int x1 = x + ax, y1 = y + ay, x2 = x - ax, y2 = y - ay;
                if (x1 < 1 || x1 >= w - 1 || y1 < 1 || y1 >= h - 1) continue;
                if (x2 < 1 || x2 >= w - 1 || y2 < 1 || y2 >= h - 1) continue;

                if (magnitude[i] >= magnitude[y1 * w + x1]
                        && magnitude[i] >= magnitude[y2 * w + x2]) {
                    ridge[i] = true;
                }
            }
        }
        return ridge;
    }

    /** 补缝：邻域里线状像素够多就补上，让线条连续但不会重新变粗。 */
    private static boolean[] fillGaps(boolean[] ridge, int w, int h) {
        boolean[] draw = new boolean[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if (ridge[i]) {
                    draw[i] = true;
                    continue;
                }
                int neighbours = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (ridge[i + dy * w + dx]) neighbours++;
                    }
                }
                if (neighbours >= 4) draw[i] = true;
            }
        }
        return draw;
    }

    // ---------------------------------------------------------------- 重绘

    private static Bitmap strokeOver(boolean[] draw, int[] gx, int[] gy,
                                     int dw, int dh, int outW, int outH,
                                     int inkColor, long seed) {
        Bitmap layer = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(layer);
        Random random = new Random(seed);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float kx = (float) outW / dw;
        float ky = (float) outH / dh;
        float unit = (kx + ky) * 0.5f;

        int inkR = Color.red(inkColor);
        int inkG = Color.green(inkColor);
        int inkB = Color.blue(inkColor);

        for (int y = 1; y < dh - 1; y++) {
            for (int x = 1; x < dw - 1; x++) {
                int i = y * dw + x;
                if (!draw[i]) continue;

                // 切线方向 = 梯度方向的垂线
                float tx = -gy[i];
                float ty = gx[i];
                double norm = Math.hypot(tx, ty);
                if (norm < 1e-3) continue;
                tx /= norm;
                ty /= norm;

                // 轻微垂直抖动：像手画的，但不抖成毛边
                float jitter = (random.nextFloat() - 0.5f) * unit * 0.12f;
                float cx = (x + 0.5f) * kx - ty * jitter;
                float cy = (y + 0.5f) * ky + tx * jitter;

                float half = (2.4f + random.nextFloat() * 1.2f) * unit * 0.52f;
                float width = Math.max(0.8f, (1.05f + random.nextFloat() * 0.5f) * unit * 0.30f);
                int alpha = Math.min(236, 178 + random.nextInt(54));

                paint.setStrokeWidth(width);
                paint.setColor(Color.argb(alpha, inkR, inkG, inkB));
                canvas.drawLine(cx - tx * half, cy - ty * half,
                        cx + tx * half, cy + ty * half, paint);
            }
        }
        return layer;
    }

    // ---------------------------------------------------------------- 工具

    /** 取分位数对应的梯度值（直方图近似，不排序）。 */
    private static int percentile(int[] values, float fraction) {
        int max = 0;
        for (int v : values) {
            if (v > max) max = v;
        }
        if (max <= 0) return 0;

        int buckets = Math.max(2, Math.min(max + 1, 1024));
        int[] histogram = new int[buckets];
        for (int v : values) {
            histogram[(int) ((long) v * (buckets - 1) / max)]++;
        }

        int target = (int) (values.length * fraction);
        int acc = 0;
        for (int b = 0; b < buckets; b++) {
            acc += histogram[b];
            if (acc >= target) return (int) ((long) b * max / (buckets - 1));
        }
        return max;
    }
}
