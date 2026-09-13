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
 * 对应 gathered-scenes-zine 的「纸面会呼吸」：纸张有色调、有轻微明暗晕染、
 * 有极细的纤维颗粒，但整体保持平面扫描感，不做立体纸厚与投影。
 */
public final class PostcardPaper {

    /** 纸面主色 */
    public static final int TONE = Color.rgb(246, 242, 232);
    /** 纸张暗部（用于极淡的大面积晕染） */
    public static final int TONE_DEEP = Color.rgb(212, 200, 176);
    /** 纸纤维缺口色（撕裂边的芯色，比纸面略亮） */
    public static final int TONE_FIBER = Color.rgb(252, 249, 242);

    private PostcardPaper() {
    }

    /** 铺满整张纸：底色 → 明暗晕染 → 纤维颗粒。 */
    public static void paint(Canvas canvas, int width, int height, long seed) {
        canvas.drawColor(TONE);

        Random random = new Random(seed);
        Paint blot = new Paint(Paint.ANTI_ALIAS_FLAG);
        blot.setColor(Color.argb(9, Color.red(TONE_DEEP), Color.green(TONE_DEEP), Color.blue(TONE_DEEP)));
        for (int i = 0; i < 7; i++) {
            float cx = random.nextFloat() * width;
            float cy = random.nextFloat() * height;
            float radius = (0.20f + random.nextFloat() * 0.30f) * width;
            canvas.drawCircle(cx, cy, radius, blot);
        }

        Paint grain = new Paint();
        grain.setShader(new BitmapShader(grainTile(168, seed),
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        canvas.drawRect(0, 0, width, height, grain);
    }

    /** 可平铺的极细颗粒（alpha 0..8，肉眼几乎只见质感不见噪点）。 */
    private static Bitmap grainTile(int size, long seed) {
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Random random = new Random(seed);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            int value = random.nextInt(256);
            pixels[i] = Color.argb(random.nextInt(9), value, value, value);
        }
        tile.setPixels(pixels, 0, size, 0, 0, size, size);
        return tile;
    }
}
