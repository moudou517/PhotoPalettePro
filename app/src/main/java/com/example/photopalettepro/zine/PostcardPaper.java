package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;

import java.util.Random;

/**
 * 暖象牙纸底。
 *
 * <p>对应 gathered-scenes-zine 的「纸面会呼吸」：纸是真的一层材料，
 * 有色调、有云斑、有纤维、有颗粒，但整体保持**平面扫描感**——
 * 不做立体纸厚、不做卷角、不做投影。
 *
 * <p>纹理由四层叠加而成，全部保持极低对比度，避免抢画：
 * <ol>
 *   <li>大面积明暗晕染 —— 纸浆分布不均造成的深浅</li>
 *   <li>低分辨率云斑 —— 放大后得到柔和的团块状不匀</li>
 *   <li>纤维 —— 短而细的浅／深笔触，横七竖八地压在纸里</li>
 *   <li>颗粒 —— 可平铺的细噪点</li>
 * </ol>
 */
public final class PostcardPaper {

    /** 纸面主色 */
    public static final int TONE = Color.rgb(246, 242, 232);
    /** 纸张暗部（用于大面积晕染） */
    public static final int TONE_DEEP = Color.rgb(212, 200, 176);
    /** 纸纤维的亮部（撕裂/受光处的芯色） */
    public static final int TONE_FIBER = Color.rgb(252, 249, 242);

    private PostcardPaper() {
    }

    /** 铺满整张纸。 */
    public static void paint(Canvas canvas, int width, int height, long seed) {
        canvas.drawColor(TONE);

        Random random = new Random(seed);
        drawTonalVariation(canvas, width, height, random);
        drawMottle(canvas, width, height, random);
        drawFibers(canvas, width, height, random);
        drawGrain(canvas, width, height, seed);
    }

    /** 1. 大面积明暗晕染：让纸面有深浅，不是一块死板的纯色。 */
    private static void drawTonalVariation(Canvas canvas, int width, int height, Random random) {
        Paint blot = new Paint(Paint.ANTI_ALIAS_FLAG);
        blot.setColor(Color.argb(13, Color.red(TONE_DEEP), Color.green(TONE_DEEP), Color.blue(TONE_DEEP)));
        for (int i = 0; i < 9; i++) {
            float cx = random.nextFloat() * width;
            float cy = random.nextFloat() * height;
            float radius = (0.20f + random.nextFloat() * 0.32f) * width;
            canvas.drawCircle(cx, cy, radius, blot);
        }
    }

    /**
     * 2. 云斑：在很低的分辨率上生成噪点再放大，
     * 双线性插值会把它变成柔和的团块状不匀（而不是硬噪点）。
     */
    private static void drawMottle(Canvas canvas, int width, int height, Random random) {
        int mw = 30;
        int mh = Math.max(4, Math.round(mw * height / (float) width));

        int[] pixels = new int[mw * mh];
        for (int i = 0; i < pixels.length; i++) {
            int value = 198 + random.nextInt(58);
            pixels[i] = Color.argb(random.nextInt(18), value, value, value);
        }

        Bitmap small = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888);
        small.setPixels(pixels, 0, mw, 0, 0, mw, mh);
        Bitmap scaled = Bitmap.createScaledBitmap(small, width, height, true);
        if (scaled != small && !small.isRecycled()) small.recycle();

        canvas.drawBitmap(scaled, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (!scaled.isRecycled()) scaled.recycle();
    }

    /** 3. 纤维：短细笔触，浅深交替，方向随机——纸的"毛"感来自这一层。 */
    private static void drawFibers(Canvas canvas, int width, int height, Random random) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeCap(Paint.Cap.ROUND);

        int count = Math.max(90, (int) ((long) width * height / 8000));
        for (int i = 0; i < count; i++) {
            float cx = random.nextFloat() * width;
            float cy = random.nextFloat() * height;
            double angle = random.nextDouble() * Math.PI;
            float length = width * (0.004f + random.nextFloat() * 0.013f);
            float dx = (float) Math.cos(angle) * length;
            float dy = (float) Math.sin(angle) * length;

            paint.setStrokeWidth(Math.max(0.6f, width * (0.00035f + random.nextFloat() * 0.0007f)));
            paint.setColor(random.nextBoolean()
                    ? Color.argb(12 + random.nextInt(15), 255, 253, 247)   // 受光纤维
                    : Color.argb(10 + random.nextInt(13), 194, 184, 162)); // 背光纤维
            canvas.drawLine(cx - dx / 2f, cy - dy / 2f, cx + dx / 2f, cy + dy / 2f, paint);
        }
    }

    /** 4. 颗粒：可平铺的细噪点。 */
    private static void drawGrain(Canvas canvas, int width, int height, long seed) {
        Paint grain = new Paint();
        grain.setShader(new BitmapShader(grainTile(168, seed),
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        canvas.drawRect(0, 0, width, height, grain);
    }

    private static Bitmap grainTile(int size, long seed) {
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Random random = new Random(seed);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            int value = random.nextInt(256);
            pixels[i] = Color.argb(random.nextInt(15), value, value, value);
        }
        tile.setPixels(pixels, 0, size, 0, 0, size, size);
        return tile;
    }
}
