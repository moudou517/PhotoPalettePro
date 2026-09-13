package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

/**
 * 用拉普拉斯算子估算「信息密度」，找出细节最密集的区域。
 *
 * <p>拉普拉斯是二阶微分：平坦区域响应接近 0，边缘与纹理处响应强烈。
 * 取绝对值后分块累加，能量最高的格子就是画面里信息量最大的地方——
 * 也就是开启「增强现实」时，最值得把原始照片嵌回去的位置。
 */
public final class InformationDensity {

    /** 计算用的工作宽度（低分辨率足够判断密度分布） */
    private static final int WORK_WIDTH = 128;
    /** 分块数（GRID × GRID） */
    private static final int GRID = 4;

    private InformationDensity() {
    }

    /**
     * @param widthShare  嵌入区域占整幅的宽度比例
     * @param heightShare 嵌入区域占整幅的高度比例
     * @return 以能量最高格子为中心、限制在 0..1 内的<b>相对</b>矩形
     */
    public static RectF densestRegion(Bitmap photo, float widthShare, float heightShare) {
        float[] center = densestCenter(photo);
        float left = PostcardPalette.clamp(center[0] - widthShare / 2f, 0f, Math.max(0f, 1f - widthShare));
        float top = PostcardPalette.clamp(center[1] - heightShare / 2f, 0f, Math.max(0f, 1f - heightShare));
        return new RectF(left, top, left + widthShare, top + heightShare);
    }

    /** @return 信息密度最高格子的相对中心 {x, y}，取值 0..1 */
    private static float[] densestCenter(Bitmap photo) {
        int w = WORK_WIDTH;
        int h = Math.max(2, Math.round(WORK_WIDTH * (float) photo.getHeight() / photo.getWidth()));

        int[] pixels = new int[w * h];
        Bitmap small = Bitmap.createScaledBitmap(photo, w, h, true);
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        if (small != photo && !small.isRecycled()) small.recycle();

        int[] gray = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            gray[i] = (int) (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p));
        }

        long[][] energy = new long[GRID][GRID];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int laplacian = 4 * gray[i]
                        - gray[i - 1] - gray[i + 1]
                        - gray[i - w] - gray[i + w];
                int cellY = Math.min(GRID - 1, y * GRID / h);
                int cellX = Math.min(GRID - 1, x * GRID / w);
                energy[cellY][cellX] += Math.abs(laplacian);
            }
        }

        int bestY = GRID / 2;
        int bestX = GRID / 2;
        long best = -1;
        for (int cellY = 0; cellY < GRID; cellY++) {
            for (int cellX = 0; cellX < GRID; cellX++) {
                if (energy[cellY][cellX] > best) {
                    best = energy[cellY][cellX];
                    bestY = cellY;
                    bestX = cellX;
                }
            }
        }
        return new float[]{(bestX + 0.5f) / GRID, (bestY + 0.5f) / GRID};
    }
}
