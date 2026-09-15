package com.example.photopalettepro.helper;

import android.content.Context;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.photopalettepro.R;

/**
 * 页面底色：一个氛围渐变 + 几团很淡的色雾。
 *
 * <h3>它不是"好看的背景"，是玻璃的光源</h3>
 *
 * <p>液态玻璃本身几乎不着色——你看到的颜色全部来自它背后被折射、被模糊、
 * 被提亮的东西。背景是一层纯色渐变，卡片透上去还是纯色，就退化成一块白板。
 * 真机上「玻璃效果全没了」正是这个原因：卡片后面什么都没有。
 *
 * <h3>为什么自己画，不用 layer-list</h3>
 *
 * <p>试过 XML 版本，两个坑：
 * <ul>
 *   <li>layer-list 的 item 内边距<b>只收尺寸</b>，写 {@code 52%} 直接编译失败
 *       （aapt: '52%' is incompatible with attribute bottom），
 *       于是色雾没法按屏幕比例摆；</li>
 *   <li>椭圆 shape 会被 item 的矩形拉伸，渐变半径又够不到边，
 *       屏幕上就是几个<b>能看见轮廓的圆</b>，一眼假。</li>
 * </ul>
 *
 * <p>这里自己画：位置按 {@link Rect} 的比例算，半径按短边算，
 * 而且渐变<b>半径末端 alpha 归零</b>——圆画出去也没有边，怎么摆都是软的。
 * 深色模式不用另写一份：颜色全部从资源取，夜间值自动生效。
 */
public class BackdropDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int baseTop;
    private final int baseMid;
    private final int baseBottom;
    private final int blobA;
    private final int blobB;
    private final int blobC;

    public BackdropDrawable(Context context) {
        baseTop = ContextCompat.getColor(context, R.color.bg_gradient_start);
        baseMid = ContextCompat.getColor(context, R.color.bg_gradient_mid);
        baseBottom = ContextCompat.getColor(context, R.color.bg_gradient_end);
        blobA = ContextCompat.getColor(context, R.color.bg_blob_blue);
        blobB = ContextCompat.getColor(context, R.color.bg_blob_green);
        blobC = ContextCompat.getColor(context, R.color.bg_blob_warm);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        // 底：上到下三段渐变
        paint.setShader(new LinearGradient(
                b.left, b.top, b.left, b.bottom,
                new int[]{baseTop, baseMid, baseBottom},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(b, paint);

        // 色雾：位置按比例、半径按短边——换机型自动跟着走
        float shortSide = Math.min(b.width(), b.height());
        drawBlob(canvas, b.left + b.width() * 0.16f, b.top + b.height() * 0.09f,
                shortSide * 0.62f, blobA);
        drawBlob(canvas, b.left + b.width() * 0.92f, b.top + b.height() * 0.20f,
                shortSide * 0.50f, blobB);
        drawBlob(canvas, b.left + b.width() * 0.22f, b.top + b.height() * 0.82f,
                shortSide * 0.56f, blobC);
    }

    /** 一团色雾。半径末端 alpha 归零，所以圆本身没有边。 */
    private void drawBlob(Canvas canvas, float cx, float cy, float radius, int color) {
        if (radius <= 0f) return;
        int edge = color & 0x00FFFFFF;   // 保留 RGB，alpha 归零
        paint.setShader(new RadialGradient(cx, cy, radius, color, edge,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}

