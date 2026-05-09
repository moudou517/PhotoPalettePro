package com.example.photopalettepro;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PosterUtils {

    // HSL 排序逻辑：对应 Python 的 sort_palette
    public static List<Integer> sortPalette(List<Integer> colors) {
        List<HslColor> hslList = new ArrayList<>();
        for (int color : colors) {
            float[] hsl = new float[3];
            // 注意：Android 的 ColorUtils 或 Color.colorToHSV 返回的是 HSV
            // 我们需要转换逻辑以匹配 Python 的 colorsys.rgb_to_hls
            Color.colorToHSV(color, hsl);
            hslList.add(new HslColor(hsl[0], hsl[1], hsl[2], color));
        }
        // 排序规则：Hue 升序，Lightness 降序
        Collections.sort(hslList, (a, b) -> {
            if (a.h != b.h) return Float.compare(a.h, b.h);
            return Float.compare(b.v, a.v);
        });
        List<Integer> sorted = new ArrayList<>();
        for (HslColor c : hslList) sorted.add(c.rawColor);
        return sorted;
    }

    // 空间映射逻辑：对应 Python 的 get_spatial_mapping
    // 空间映射逻辑：对应 Python 的 get_spatial_mapping
    // 空间映射逻辑：修正镜像与坐标反转问题
    public static List<Integer> getSpatialMapping(Bitmap img, List<Integer> palette) {
        int rows = 3, cols = 2;
        int rw = img.getWidth() / cols;
        int rh = img.getHeight() / rows;

        // 判定横竖版以对齐渲染顺序
        boolean isLandscape = img.getWidth() > img.getHeight();

        List<Integer> mapped = new ArrayList<>(Collections.nCopies(6, 0));
        boolean[] used = new boolean[palette.size()];

        for (int i = 0; i < 6; i++) {
            int r, c;
            if (isLandscape) {
                // 对应 PosterRenderer 横版逻辑：i%3是行，i/3是列
                r = i % 3;
                c = i / 3;
            } else {
                // 对应 PosterRenderer 竖版逻辑：i/2是行，i%2是列
                r = i / 2;
                c = i % 2;
            }

            // --- 关键修正：坐标反转逻辑 ---
            // 如果你发现色块显示的颜色和照片位置正好相反：
            // 将 c 替换为 (cols - 1 - c) 实现左右反转
            // 将 r 替换为 (rows - 1 - r) 实现上下反转
            int sampleC = (cols - 1) - c;
            int sampleR = (rows - 1) - r;

            int sampleX = sampleC * rw;
            int sampleY = sampleR * rh;

            // 边界安全检查
            int targetW = Math.max(1, Math.min(rw, img.getWidth() - sampleX));
            int targetH = Math.max(1, Math.min(rh, img.getHeight() - sampleY));

            Bitmap region = Bitmap.createBitmap(img, sampleX, sampleY, targetW, targetH);
            int avgColor = getAverageColor(region);
            if (region != img) region.recycle();

            // 贪婪匹配
            double minOffset = Double.MAX_VALUE;
            int bestIdx = -1;
            for (int j = 0; j < palette.size(); j++) {
                if (used[j]) continue;
                double dist = colorDistance(avgColor, palette.get(j));
                if (dist < minOffset) {
                    minOffset = dist;
                    bestIdx = j;
                }
            }
            if (bestIdx != -1) {
                mapped.set(i, palette.get(bestIdx));
                used[bestIdx] = true;
            }
        }
        return mapped;
    }

    // 适配背景色：对应 Python 的 get_adaptive_bg
    public static int getAdaptiveBg(int brightestColor) {
        int r = Color.red(brightestColor);
        int g = Color.green(brightestColor);
        int b = Color.blue(brightestColor);
        return Color.rgb(
                (int)(r * 0.15 + 255 * 0.85),
                (int)(g * 0.15 + 255 * 0.85),
                (int)(b * 0.15 + 255 * 0.85)
        );
    }

    private static int getAverageColor(Bitmap bitmap) {
        long r = 0, g = 0, b = 0;
        int count = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[count];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int p : pixels) {
            r += Color.red(p); g += Color.green(p); b += Color.blue(p);
        }
        return Color.rgb((int)(r/count), (int)(g/count), (int)(b/count));
    }

    private static double colorDistance(int c1, int c2) {
        return Math.sqrt(Math.pow(Color.red(c1)-Color.red(c2),2) +
                Math.pow(Color.green(c1)-Color.green(c2),2) +
                Math.pow(Color.blue(c1)-Color.blue(c2),2));
    }

    private static class HslColor {
        float h, s, v; int rawColor;
        HslColor(float h, float s, float v, int r) { this.h=h; this.s=s; this.v=v; this.rawColor=r; }
    }
}