package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * 主元素合成器。
 *
 * <p>提供两种呈现，由 {@code ZinePostcardConfig.realityAnchor} 切换：
 *
 * <h3>默认 · 纯插画</h3>
 * 遵循 photo-abstract-editorial 的「先读作克制的抽象」：
 * 简笔线稿压在一层取自原图的柔和平涂色块上，外缘溶解进纸面。
 *
 * <h3>增强现实（可选开关）</h3>
 * 额外把<b>原始照片</b>嵌回插画里，用来补上插画必然丢失的高频细节：
 * <ol>
 *   <li>用<b>拉普拉斯算子</b>估算信息密度，找出细节最密集的区域（{@link InformationDensity}）；</li>
 *   <li>把该区域对应的原始像素铺进去，保持与插画同一套坐标；</li>
 *   <li>替换边缘用<b>大比例羽化</b>柔和过渡，不做生硬的硬切或撕纸边；</li>
 *   <li>再加一束从地平线延伸出来的高纯度构成色，横跨这块嵌入区。</li>
 * </ol>
 */
public final class SceneMotifRenderer {

    /**
     * 提纯增益：k-means 质心天然偏灰，需要把纯度拉回来，但**不能拉过头**——
     * 1.55 会让画面偏浓郁，1.25 左右刚好「不发灰也不刺眼」。
     */
    private static final float SATURATION_GAIN = 1.25f;
    private static final float VALUE_GAIN = 1.00f;

    /**
     * 平涂色块的不透明度。保持接近上一版那种「纸面透得出来」的轻盈感，
     * 颜色不够艳靠 {@link #SATURATION_GAIN} 提纯来解决，而不是靠堆不透明度。
     */
    private static final int MASS_ALPHA = 140;

    /** 平涂的降采样除数：越大越"简"，细节被合并成越少的大形 */
    private static final int FLATTEN_DIVISOR = 6;

    /** 增强现实：嵌入区域占整幅场景的比例 */
    private static final float REALITY_WIDTH_SHARE = 0.60f;
    private static final float REALITY_HEIGHT_SHARE = 0.56f;

    /**
     * 增强现实：替换边缘的羽化比例（占嵌入区短边）。
     * 取得很大是刻意的——边缘必须柔化，不能生硬。
     */
    private static final float REALITY_EDGE_FEATHER = 0.28f;

    private static final int INK = Color.rgb(58, 54, 48);

    private SceneMotifRenderer() {
    }

    /**
     * @param realityAnchor 是否启用「增强现实」——把原照片嵌入插画。
     *                      背面水印固定传 false，只保留线稿与平涂。
     */
    public static Bitmap render(Bitmap photo, List<Integer> palette,
                                int outW, int outH, long seed, boolean realityAnchor) {
        if (photo == null || photo.isRecycled() || outW <= 0 || outH <= 0) return null;

        RectF scene = fitInside(photo, outW, outH);
        Bitmap canvas = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);

        List<Integer> boosted = PostcardPalette.boostAll(palette, SATURATION_GAIN, VALUE_GAIN);

        drawFlatMasses(c, photo, boosted, scene);
        drawSketchLines(c, photo, scene, seed);

        if (realityAnchor) {
            drawRealityAnchor(c, photo, scene, seed);
        }

        applyOuterDissolve(c, scene, outW, outH, seed);
        return canvas;
    }

    // ================================================================
    //  1. 插画场 · 平涂色块
    // ================================================================

    private static void drawFlatMasses(Canvas c, Bitmap photo, List<Integer> palette, RectF scene) {
        int w = Math.max(2, Math.round(scene.width()));
        int h = Math.max(2, Math.round(scene.height()));

        Bitmap masses = buildFlatMasses(photo, palette, w, h);
        if (masses == null) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(MASS_ALPHA);
        c.drawBitmap(masses, null, scene, paint);
        if (!masses.isRecycled()) masses.recycle();
    }

    /**
     * 把原图压成几块柔和平涂。
     *
     * <p><b>顺序很重要</b>：先降采样把细节合并成大形，<b>再</b>量化到色板，
     * 最后放大回来。如果反过来（先量化再缩放），缩放会在相邻色块之间插值出
     * 大量中间色，结果是斑驳的色块而不是干净的平涂。
     */
    private static Bitmap buildFlatMasses(Bitmap photo, List<Integer> palette, int w, int h) {
        // 降采样要足够狠：把树、人群这类高频细节真正合并成"少量大形"，
        // 否则量化后仍是一堆碎块，看起来是斑驳的色块而不是平涂
        int cw = Math.max(2, w / FLATTEN_DIVISOR);
        int ch = Math.max(2, h / FLATTEN_DIVISOR);

        Bitmap work = Bitmap.createScaledBitmap(photo, cw, ch, true);
        if (!work.isMutable()) {
            Bitmap mutable = work.copy(Bitmap.Config.ARGB_8888, true);
            if (mutable != null) work = mutable;
        }

        int count = cw * ch;
        int[] pixels = new int[count];
        work.getPixels(pixels, 0, cw, 0, 0, cw, ch);
        if (work != photo && !work.isRecycled()) work.recycle();

        List<Integer> colors = new ArrayList<>();
        if (palette != null) {
            for (int color : palette) {
                if (!colors.contains(color)) colors.add(color);
            }
        }
        if (colors.size() < 2) {
            for (int color : PostcardPalette.quantize(pixels, 5)) {
                if (!colors.contains(color)) colors.add(color);
            }
        }

        int[] flat = new int[count];
        for (int i = 0; i < count; i++) flat[i] = PostcardPalette.nearest(colors, pixels[i]);

        Bitmap base = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        base.setPixels(flat, 0, cw, 0, 0, cw, ch);

        Bitmap soft = Bitmap.createScaledBitmap(base, w, h, true);
        if (soft != base && !base.isRecycled()) base.recycle();
        return soft;
    }

    // ================================================================
    //  2. 插画场 · 简笔线稿
    // ================================================================

    private static void drawSketchLines(Canvas c, Bitmap photo, RectF scene, long seed) {
        int w = Math.max(2, Math.round(scene.width()));
        int h = Math.max(2, Math.round(scene.height()));

        Bitmap lines = SketchLineRenderer.render(photo, w, h, INK, seed);
        if (lines == null) return;

        c.drawBitmap(lines, null, scene, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        if (!lines.isRecycled()) lines.recycle();
    }

    // ================================================================
    //  3. 增强现实：按信息密度嵌入原照片（柔边）
    // ================================================================

    private static void drawRealityAnchor(Canvas c, Bitmap photo, RectF scene, long seed) {
        RectF relative = InformationDensity.densestRegion(
                photo, REALITY_WIDTH_SHARE, REALITY_HEIGHT_SHARE);
        RectF panel = new RectF(
                scene.left + relative.left * scene.width(),
                scene.top + relative.top * scene.height(),
                scene.left + relative.right * scene.width(),
                scene.top + relative.bottom * scene.height());

        // 羽化会伸出面板之外，图层要留足余量
        float pad = Math.min(panel.width(), panel.height()) * REALITY_EDGE_FEATHER;
        int layerW = Math.max(4, Math.round(panel.width() + pad * 2));
        int layerH = Math.max(4, Math.round(panel.height() + pad * 2));

        Bitmap layer = Bitmap.createBitmap(layerW, layerH, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);

        RectF inner = new RectF(pad, pad, pad + panel.width(), pad + panel.height());
        // 取场景中对应位置的原始像素：与插画共用同一套坐标，内容自然衔接
        Rect source = sceneSubRect(photo, scene, panel);
        lc.drawBitmap(photo, source, inner,
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

        Bitmap mask = TornEdgeMask.dissolve(layerW, layerH, inner, REALITY_EDGE_FEATHER, seed);
        Paint xfer = new Paint(Paint.ANTI_ALIAS_FLAG);
        xfer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        lc.drawBitmap(mask, 0, 0, xfer);
        if (!mask.isRecycled()) mask.recycle();

        c.drawBitmap(layer, panel.left - pad, panel.top - pad, null);
        if (!layer.isRecycled()) layer.recycle();
    }

    /** 面板在整幅场景中对应的原始像素区域（保持场景坐标，不做裁切重定位）。 */
    private static Rect sceneSubRect(Bitmap photo, RectF scene, RectF panel) {
        float relLeft = (panel.left - scene.left) / scene.width();
        float relTop = (panel.top - scene.top) / scene.height();
        float relRight = (panel.right - scene.left) / scene.width();
        float relBottom = (panel.bottom - scene.top) / scene.height();

        int pw = photo.getWidth();
        int ph = photo.getHeight();
        int left = clampInt(Math.round(relLeft * pw), 0, pw - 1);
        int top = clampInt(Math.round(relTop * ph), 0, ph - 1);
        int right = clampInt(Math.round(relRight * pw), left + 1, pw);
        int bottom = clampInt(Math.round(relBottom * ph), top + 1, ph);
        return new Rect(left, top, right, bottom);
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    // ================================================================
    //  4. 外缘溶解
    // ================================================================

    private static void applyOuterDissolve(Canvas c, RectF scene, int outW, int outH, long seed) {
        Bitmap mask = TornEdgeMask.dissolve(outW, outH, scene, 0.15f, seed);
        Paint xfer = new Paint(Paint.ANTI_ALIAS_FLAG);
        xfer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask, 0, 0, xfer);
        if (!mask.isRecycled()) mask.recycle();
    }

    // ================================================================
    //  工具
    // ================================================================

    /** 把照片等比放进 outW × outH，居中，返回它在画布上的矩形。 */
    private static RectF fitInside(Bitmap photo, int outW, int outH) {
        float scale = Math.min((float) outW / photo.getWidth(), (float) outH / photo.getHeight());
        float w = photo.getWidth() * scale;
        float h = photo.getHeight() * scale;
        float left = (outW - w) / 2f;
        float top = (outH - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }
}
