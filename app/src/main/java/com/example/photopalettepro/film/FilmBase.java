package com.example.photopalettepro.film;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;
import com.example.photopalettepro.render.FilmBorderRenderer;

/**
 * 胶片「材料」层：台面 + 半透明片基 + 表面磨损。
 *
 * <h3>台面与片基是两件事</h3>
 *
 * <p>早先这里把整张画布都刷成片基色，于是「画布背景」和「胶片」是同一个颜色——
 * 屏幕上只剩下几根描边，胶片不像一块材料，更像一块印了框的纸板。
 *
 * <p>现在分开：
 * <ul>
 *   <li><b>台面</b>（{@link #paintBackdrop}）—— 胶片摆在上面的那一层，
 *       颜色取自 {@link FilmStock#backdrop}，和片基拉开明度差；</li>
 *   <li><b>片基</b>（{@link #paintStrip}）—— 半透明的，只画在每一截胶片自己的范围里，
 *       而且<b>齿孔是真正镂空的</b>（用 {@code Path} 挖掉，不是画几个灰点），
 *       台面从孔里透上来。</li>
 * </ul>
 *
 * <p>片基上仍按四个尺度叠不匀（大面积浓淡 / 涂层条痕 / 银盐团块 / 细颗粒），
 * 再压一层边缘光衰减；这些都画在裁剪区里，不会溢出到台面上。
 *
 * <p>所有纹理都由固定 seed 驱动、尺寸全部相对长边换算，
 * 所以同一张照片每次渲染完全一致，预览（1080）与导出（2000）的颗粒观感也对得上。
 * 每一截胶片用<b>各自的 seed</b>——剪开的几截本来就不是同一段，纹理不该一模一样。
 */
public final class FilmBase {

    /** 颗粒/团块的基准长边：纹理尺寸都以它为单位换算，保证不同输出宽度观感一致。 */
    private static final float REFERENCE_SPAN = 2000f;

    private FilmBase() {
    }

    // ====================================================================
    //  台面
    // ====================================================================

    /**
     * 铺满整张画布的台面。
     *
     * <p>刻意保持"平面扫描感"，不做立体纸厚、不做投影：它只是让胶片有地方待着，
     * 不能抢戏。所以只有一层很轻的中心到边缘衰减。
     */
    public static void paintBackdrop(Canvas canvas, int width, int height,
                                     FilmStock stock, long seed) {
        if (width <= 0 || height <= 0) return;

        canvas.drawColor(stock.backdrop);

        // 四周极淡压暗：让中间的胶片浮起来一点，但不至于读成"暗角滤镜"
        float span = Math.max(width, height);
        float radius = span * 0.78f;
        boolean dark = stock.isDarkBackdrop();

        Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
        vignette.setShader(new RadialGradient(
                width / 2f, height / 2f, radius,
                Color.argb(0, 0, 0, 0),
                dark ? Color.argb(38, 0, 0, 0) : Color.argb(30, 90, 78, 60),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, vignette);

        // 台面本身也有一点颗粒，免得大片纯色显得像色块
        drawNoiseLayer(canvas, width, height, 128, Math.max(1f, span / REFERENCE_SPAN),
                dark ? 10 : 12, seed + 104729L);
    }

    // ====================================================================
    //  片基（半透明，齿孔镂空）
    // ====================================================================

    /**
     * 画一截胶片。
     *
     * <p>{@code stripShape} 已经是「整截矩形挖掉所有齿孔」的形状（{@code EVEN_ODD}），
     * 所以这里只需要把它当作裁剪区，然后照常铺底色与纹理——
     * 孔里什么都不会画，台面自然透上来。
     *
     * @param alpha 片基不透明度；见 {@code FilmBorderRenderer.FILM_ALPHA}
     */
    public static void paintStrip(Canvas canvas, Path stripShape, RectF bounds,
                                  FilmStock stock, long seed, int alpha) {
        if (stripShape == null || bounds == null) return;
        if (bounds.width() <= 0f || bounds.height() <= 0f) return;

        int save = canvas.save();
        canvas.clipPath(stripShape);

        int w = Math.round(bounds.width());
        int h = Math.round(bounds.height());
        float span = Math.max(w, h);

        canvas.drawColor(withAlpha(stock.base, alpha));

        Random random = new Random(seed);
        drawTonalVariation(canvas, bounds, stock, random, span, alpha);
        drawCoatingStreaks(canvas, bounds, stock, random, alpha);
        drawSilverClumps(canvas, bounds, stock, seed, span / REFERENCE_SPAN, alpha);
        drawFineGrain(canvas, bounds, stock, seed, span / REFERENCE_SPAN, alpha);
        drawEdgeFalloff(canvas, bounds, stock, span, alpha);

        canvas.restoreToCount(save);
    }

    // ====================================================================
    //  表面磨损（只落在片基上）
    // ====================================================================

    /**
     * 灰尘与划痕。
     *
     * <p>画在照片<b>之上</b>，但裁掉两部分：所有抽屉之外（{@code stripShapes}）和
     * 所有照片格子之内（{@code photoCells}）——也就是说，只在片基本身上留痕。
     * 照片是用户自己的内容，不能给人家划花；台面也不该有胶片的划痕。
     *
     * @param stripShapes 所有胶片的外形；为 null 时不限制
     * @param photoCells  所有照片的位置；为 null 时不限制
     */
    public static void paintWear(Canvas canvas, Path[] stripShapes, RectF[] photoCells,
                                 RectF bounds, FilmStock stock, long seed) {
        if (bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) return;

        int save = canvas.save();

        // 先裁到胶片外形；多截之间取并集，所以不能简单重复 clipPath（那是交集）
        if (stripShapes != null && stripShapes.length > 0) {
            Path union = new Path();
            for (Path shape : stripShapes) {
                if (shape != null) union.op(shape, Path.Op.UNION);
            }
            canvas.clipPath(union);
        }
        if (photoCells != null) {
            for (RectF cell : photoCells) {
                if (cell != null) canvas.clipOutRect(cell);
            }
        }

        Random random = new Random(seed ^ 0x9E3779B97F4A7C15L);
        float span = Math.max(bounds.width(), bounds.height());
        boolean alongWidth = bounds.width() >= bounds.height();

        drawScratches(canvas, bounds, stock, random, span, alongWidth);
        drawDust(canvas, bounds, stock, random, span);

        canvas.restoreToCount(save);
    }

    // ====================================================================
    //  1. 大面积浓淡
    // ====================================================================

    /**
     * 几团极淡的暗部叠加，让片基有深浅流动感。
     * 暗底片基改用亮部叠加，否则在纯黑上看不见任何层次。
     *
     * <p>每一团都用<b>径向渐变</b>而不是实心圆——实心圆在深色片基上会露出
     * 一圈圈生硬的边界，看起来像画了几个圆，而不是片基本身不匀。
     */
    private static void drawTonalVariation(Canvas canvas, RectF bounds, FilmStock stock,
                                           Random random, float span, int alpha) {
        boolean dark = stock.isDarkBase();
        int tone = dark ? 255 : Color.red(stock.baseDeep);
        int g = dark ? 255 : Color.green(stock.baseDeep);
        int b = dark ? 255 : Color.blue(stock.baseDeep);
        // 渐变让平均不透明度掉到实心圆的三分之一左右，所以核心值要相应提高
        int coreAlpha = dark ? 18 : 24;

        Paint blot = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 7; i++) {
            float cx = bounds.left + random.nextFloat() * bounds.width();
            float cy = bounds.top + random.nextFloat() * bounds.height();
            float radius = (0.30f + random.nextFloat() * 0.42f) * span;
            blot.setShader(new RadialGradient(cx, cy, radius,
                    Color.argb(coreAlpha, tone, g, b),
                    Color.argb(0, tone, g, b),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, blot);
        }
        blot.setShader(null);
    }

    // ====================================================================
    //  2. 涂层条痕
    // ====================================================================

    /**
     * 沿胶片走向的宽条带，极淡。
     *
     * <p>真实片基是涂布出来的，干燥时厚薄不可能完全均匀，扫出来就是这种
     * 顺着走片方向的宽条痕。它比「团块」更线性，是片基「有方向」的来源。
     */
    private static void drawCoatingStreaks(Canvas canvas, RectF bounds, FilmStock stock,
                                           Random random, int alpha) {
        boolean dark = stock.isDarkBase();
        int tone = dark ? 255 : Color.red(stock.baseDeep);
        int g = dark ? 255 : Color.green(stock.baseDeep);
        int b = dark ? 255 : Color.blue(stock.baseDeep);
        int peak = dark ? 8 : 11;

        boolean alongWidth = bounds.width() >= bounds.height();
        float across = alongWidth ? bounds.height() : bounds.width();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            float thickness = across * (0.10f + random.nextFloat() * 0.24f);
            float center = (alongWidth ? bounds.top : bounds.left) + random.nextFloat() * across;
            int a = Math.max(3, Math.round(peak * (0.45f + random.nextFloat() * 0.75f)));

            int clear = Color.argb(0, tone, g, b);
            int solid = Color.argb(a, tone, g, b);
            float lo = center - thickness / 2f;
            float hi = center + thickness / 2f;

            if (alongWidth) {
                paint.setShader(new LinearGradient(0, lo, 0, hi,
                        new int[]{clear, solid, clear}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(bounds.left, lo, bounds.right, hi, paint);
            } else {
                paint.setShader(new LinearGradient(lo, 0, hi, 0,
                        new int[]{clear, solid, clear}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(lo, bounds.top, hi, bounds.bottom, paint);
            }
        }
        paint.setShader(null);
    }

    // ====================================================================
    //  3~4. 银盐团块 + 细颗粒
    // ====================================================================

    /**
     * 银盐团块：在很低的分辨率上生成噪声，再用 shader 的缩放矩阵放大。
     * 双线性插值会把它变成柔和的团块状不匀（而不是硬噪点）。
     *
     * <p>这一层刻意压得比细颗粒更淡、尺度也更小：放大后的噪声本质上是「软团块」，
     * 一旦浓到看得清形状，浅色片基就会读成脏斑而不是颗粒。
     * 它只负责给颗粒一层疏密结构，真正的「胶片感」由下面的细颗粒承担。
     *
     * <p>不用 {@code createScaledBitmap} 放大到整幅画布——那会多出一张
     * 和成品同样大的临时位图（2000×2700 ≈ 21MB），低端机上就是一次 OOM。
     * shader 的局部矩阵能做到同样的放大，显存占用却只有一个几十像素的小图。
     */
    private static void drawSilverClumps(Canvas canvas, RectF bounds, FilmStock stock,
                                         long seed, float scale, int alpha) {
        int peak = Math.max(3, Math.round(stock.grainAlpha * 0.50f));
        drawNoiseLayer(canvas, rectWidth(bounds), rectHeight(bounds),
                64, Math.max(1f, 7f * scale), peak, seed + 7919L);
    }

    /** 单颗银盐的细颗粒。 */
    private static void drawFineGrain(Canvas canvas, RectF bounds, FilmStock stock,
                                      long seed, float scale, int alpha) {
        if (stock.grainAlpha <= 0) return;
        drawNoiseLayer(canvas, rectWidth(bounds), rectHeight(bounds),
                128, Math.max(1f, 1.6f * scale), stock.grainAlpha, seed);
    }

    /**
     * 铺一层可平铺的噪声，用 shader 矩阵放大到指定颗粒直径。
     * 平铺不可避免会重复，但噪声本身无特征、透明度又极低，肉眼看不出来。
     */
    private static void drawNoiseLayer(Canvas canvas, int width, int height,
                                       int tile, float magnify, int maxAlpha, long seed) {
        if (maxAlpha <= 0 || tile <= 0 || width <= 0 || height <= 0) return;

        Bitmap noise = noiseTile(tile, maxAlpha, seed);
        if (noise == null) return;

        BitmapShader shader = new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix matrix = new Matrix();
        matrix.setScale(magnify, magnify);
        shader.setLocalMatrix(matrix);

        Paint paint = new Paint();
        paint.setShader(shader);
        paint.setFilterBitmap(true);
        canvas.drawRect(0, 0, width, height, paint);

        // Shader 已经拷走了像素，这里可以安全回收，避免每次渲染漏一张小图
        if (!noise.isRecycled()) noise.recycle();
    }

    private static Bitmap noiseTile(int size, int maxAlpha, long seed) {
        if (size <= 0) return null;
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[size * size];
        Random random = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            int value = random.nextInt(256);
            pixels[i] = Color.argb(random.nextInt(maxAlpha + 1), value, value, value);
        }
        tile.setPixels(pixels, 0, size, 0, 0, size, size);
        return tile;
    }

    // ====================================================================
    //  5. 边缘光衰减
    // ====================================================================

    /**
     * 一截胶片四周极淡的压暗（暗底则改为极淡的提亮）。
     *
     * <p>和平铺色板相比，这一点点衰减是「这截片基有边界」的关键——
     * 但必须淡到说不出哪里变了，只能感觉到边缘没那么亮。
     */
    private static void drawEdgeFalloff(Canvas canvas, RectF bounds, FilmStock stock,
                                        float span, int alpha) {
        boolean dark = stock.isDarkBase();
        int tone = dark ? 255 : Color.red(stock.baseDeep);
        int g = dark ? 255 : Color.green(stock.baseDeep);
        int b = dark ? 255 : Color.blue(stock.baseDeep);
        int a = dark ? 9 : 15;

        float band = span * 0.045f;
        if (band <= 0.5f) return;

        int clear = Color.argb(0, tone, g, b);
        int solid = Color.argb(a, tone, g, b);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.top + band, solid, clear, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + band, paint);

        paint.setShader(new LinearGradient(0, bounds.bottom, 0, bounds.bottom - band, solid, clear, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds.left, bounds.bottom - band, bounds.right, bounds.bottom, paint);

        paint.setShader(new LinearGradient(bounds.left, 0, bounds.left + band, 0, solid, clear, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds.left, bounds.top, bounds.left + band, bounds.bottom, paint);

        paint.setShader(new LinearGradient(bounds.right, 0, bounds.right - band, 0, solid, clear, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds.right - band, bounds.top, bounds.right, bounds.bottom, paint);

        paint.setShader(null);
    }

    // ====================================================================
    //  磨损：灰尘与划痕
    // ====================================================================

    /**
     * 划痕：沿胶片走向的几条细线。
     *
     * <p>关键是「极淡 + 带斜率」：笔直的、均匀的线会被读成绘制错误，
     * 而略微歪斜、透明度参差的短线才像运输和冲洗蹭出来的。
     */
    private static void drawScratches(Canvas canvas, RectF bounds, FilmStock stock,
                                      Random random, float span, boolean alongWidth) {
        boolean dark = stock.isDarkBase();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeCap(Paint.Cap.ROUND);

        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            int a = 6 + random.nextInt(11);
            paint.setColor(dark
                    ? Color.argb(a, 255, 255, 255)
                    : Color.argb(a, 32, 28, 22));
            paint.setStrokeWidth(Math.max(0.6f, span * (0.00035f + random.nextFloat() * 0.0008f)));

            float run = alongWidth ? bounds.width() : bounds.height();
            float across = alongWidth ? bounds.height() : bounds.width();
            float length = run * (0.08f + random.nextFloat() * 0.30f);
            float start = (alongWidth ? bounds.left : bounds.top) + random.nextFloat() * Math.max(1f, run - length);
            float pos = (alongWidth ? bounds.top : bounds.left) + random.nextFloat() * across;
            float drift = (random.nextFloat() - 0.5f) * span * 0.004f;

            if (alongWidth) {
                canvas.drawLine(start, pos, start + length, pos + drift, paint);
            } else {
                canvas.drawLine(pos, start, pos + drift, start + length, paint);
            }
        }
    }

    /** 灰尘：细小的点，亮底压暗、暗底提亮，大小与浓度都随机。 */
    private static void drawDust(Canvas canvas, RectF bounds, FilmStock stock,
                                 Random random, float span) {
        boolean dark = stock.isDarkBase();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int count = 52;
        for (int i = 0; i < count; i++) {
            float cx = bounds.left + random.nextFloat() * bounds.width();
            float cy = bounds.top + random.nextFloat() * bounds.height();
            float radius = Math.max(0.4f, span * (0.00035f + random.nextFloat() * 0.0011f));
            int alpha = 5 + random.nextInt(dark ? 17 : 15);
            paint.setColor(dark
                    ? Color.argb(alpha, 255, 255, 255)
                    : Color.argb(alpha, 40, 36, 30));
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    // ====================================================================

    private static int rectWidth(RectF bounds) {
        return Math.max(1, Math.round(bounds.width()));
    }

    private static int rectHeight(RectF bounds) {
        return Math.max(1, Math.round(bounds.height()));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
