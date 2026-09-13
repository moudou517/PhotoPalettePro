package com.example.photopalettepro.zine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;

/**
 * 主元素合成器。
 *
 * <p>同时遵循两套 skill：
 * <ul>
 *   <li><b>photo-abstract-editorial</b>：主元素先读作克制的抽象——简笔线稿与平涂色块。</li>
 *   <li><b>gathered-scenes-zine · 实景拼贴</b>：在抽象之上补一层
 *       <b>真景锚点</b>（如实保留的照片，不做滤镜），两者以
 *       <b>手撕纤维边</b>相接，并引入
 *       <b>一个高纯度构成色</b>横跨这条接缝。</li>
 * </ul>
 *
 * <p>合成分层（由下至上）：
 * <pre>
 *   1. 插画场 · 平涂色块   —— 提纯后的原图色板，铺满主元素区
 *   2. 插画场 · 简笔线稿   —— 只保留结构，不描摹细节
 *   3. 真景锚点            —— 真实照片 + 手撕边（右、下两边撕裂）
 *   4. 构成色带            —— 从原图地平线延伸出来的一束高纯度色
 *   5. 外缘溶解            —— 整块主元素自然融进纸面
 * </pre>
 */
public final class SceneMotifRenderer {

    /** 提纯增益：k-means 质心天然偏灰，这里把纯度拉回来，解决「寡淡」 */
    private static final float SATURATION_GAIN = 1.55f;
    private static final float VALUE_GAIN = 1.04f;

    /** 平涂色块的不透明度。旧的 126 太淡，是「寡淡」的主因：插画要平，但不能是水洗过 */
    private static final int MASS_ALPHA = 214;

    /** 真景锚点占主元素区的比例。写实压得住，同时给插画留出足够的表现场 */
    private static final float PHOTO_WIDTH_SHARE = 0.70f;
    private static final float PHOTO_HEIGHT_SHARE = 0.74f;

    /** 构成色带的高度与横向起点（相对主元素区） */
    private static final float BAND_HEIGHT_SHARE = 0.15f;
    private static final float BAND_LEFT_SHARE = 0.52f;
    private static final int BAND_PEAK_ALPHA = 214;

    private static final int INK = Color.rgb(58, 54, 48);

    private SceneMotifRenderer() {
    }

    /**
     * @param withPhotoAnchor 是否绘制真景锚点。背面水印传 false，
     *                        只保留线稿与平涂，避免水印出现一块照片。
     */
    public static Bitmap render(Bitmap photo, List<Integer> palette,
                                int outW, int outH, long seed, boolean withPhotoAnchor) {
        if (photo == null || photo.isRecycled() || outW <= 0 || outH <= 0) return null;

        RectF scene = fitInside(photo, outW, outH);
        Bitmap canvas = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);

        List<Integer> boosted = PostcardPalette.boostAll(palette, SATURATION_GAIN, VALUE_GAIN);

        drawFlatMasses(c, photo, boosted, scene);

        int sceneW = Math.max(2, Math.round(scene.width()));
        int sceneH = Math.max(2, Math.round(scene.height()));
        Bitmap lines = SketchLineRenderer.render(photo, sceneW, sceneH, INK, seed);
        if (lines != null) {
            c.drawBitmap(lines, null, scene,
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }

        if (withPhotoAnchor) {
            RectF panel = drawPhotoAnchor(c, photo, scene, seed);
            // 让插画从撕纸接缝处「生长」到照片上，把两半连成一件事
            if (lines != null) drawLinesEmerging(c, lines, scene, panel);
            drawStructuralBand(c, photo, boosted, scene);
        }
        if (lines != null && !lines.isRecycled()) lines.recycle();

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

    /** 把原图压成几块柔和平涂：量化到色板 → 连续降采样再放大。 */
    private static Bitmap buildFlatMasses(Bitmap photo, List<Integer> palette, int w, int h) {
        Bitmap work = Bitmap.createScaledBitmap(photo, w, h, true);
        if (!work.isMutable()) {
            Bitmap mutable = work.copy(Bitmap.Config.ARGB_8888, true);
            if (mutable != null) work = mutable;
        }

        int count = w * h;
        int[] pixels = new int[count];
        work.getPixels(pixels, 0, w, 0, 0, w, h);
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

        Bitmap base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        base.setPixels(flat, 0, w, 0, 0, w, h);

        Bitmap half = Bitmap.createScaledBitmap(base, Math.max(2, w / 2), Math.max(2, h / 2), true);
        if (half != base && !base.isRecycled()) base.recycle();
        Bitmap third = Bitmap.createScaledBitmap(half, Math.max(2, w / 3), Math.max(2, h / 3), true);
        if (third != half && !half.isRecycled()) half.recycle();
        Bitmap soft = Bitmap.createScaledBitmap(third, w, h, true);
        if (soft != third && !third.isRecycled()) third.recycle();
        return soft;
    }

    // ================================================================
    //  2. 插画场 · 简笔线稿
    // ================================================================

    /**
     * 让线稿从撕纸接缝处渐显：接缝左侧不画（保持照片如实），
     * 右侧逐渐显现。对应 skill 的「让插画从选定的撕裂段生长出来」。
     */
    private static void drawLinesEmerging(Canvas c, Bitmap lines, RectF scene, RectF panel) {
        int w = Math.max(2, Math.round(scene.width()));
        int h = Math.max(2, Math.round(scene.height()));

        Bitmap layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        lc.drawBitmap(lines, null, new RectF(0, 0, w, h), null);

        float seamX = panel.right - scene.left;
        Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fade.setShader(new LinearGradient(
                seamX - w * 0.34f, 0f, seamX + w * 0.08f, 0f,
                new int[]{Color.argb(0, 255, 255, 255), Color.argb(210, 255, 255, 255)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        lc.drawRect(0, 0, w, h, fade);

        c.drawBitmap(layer, null, scene, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        if (!layer.isRecycled()) layer.recycle();
    }

    // ================================================================
    //  3. 真景锚点（如实照片 + 手撕纤维边）
    // ================================================================

    private static RectF drawPhotoAnchor(Canvas c, Bitmap photo, RectF scene, long seed) {
        float panelW = scene.width() * PHOTO_WIDTH_SHARE;
        float panelH = scene.height() * PHOTO_HEIGHT_SHARE;
        RectF panel = new RectF(scene.left, scene.top, scene.left + panelW, scene.top + panelH);

        // 纤维毛边会伸出面板外，所以图层要留出余量
        float pad = Math.min(panelW, panelH) * 0.05f;
        int layerW = Math.max(4, Math.round(panelW + pad * 2));
        int layerH = Math.max(4, Math.round(panelH + pad * 2));

        Bitmap layer = Bitmap.createBitmap(layerW, layerH, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);

        RectF inner = new RectF(pad, pad, pad + panelW, pad + panelH);
        // 关键：照片面板取「场景中对应位置的原始像素」，而不是居中裁切。
        // 这样真景与插画共用同一套坐标，地平线/山脊能在撕纸接缝处对齐并延续，
        // 否则会像两张互不相干的图拼在一起。
        Rect source = sceneSubRect(photo, scene, panel);
        lc.drawBitmap(photo, source, inner, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

        Bitmap mask = TornEdgeMask.torn(layerW, layerH, inner, true, true, seed);
        Paint xfer = new Paint(Paint.ANTI_ALIAS_FLAG);
        xfer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        lc.drawBitmap(mask, 0, 0, xfer);
        if (!mask.isRecycled()) mask.recycle();

        c.drawBitmap(layer, panel.left - pad, panel.top - pad, null);
        if (!layer.isRecycled()) layer.recycle();
        return panel;
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
    //  4. 构成色带（色彩成结构）
    // ================================================================

    /**
     * 从原图最强的水平结构（地平线）延伸出一束高纯度色，
     * 横跨「照片 → 纸面」的接缝，承担视觉重心的作用。
     */
    private static void drawStructuralBand(Canvas c, Bitmap photo, List<Integer> palette, RectF scene) {
        int structural = PostcardPalette.structuralColor(palette, Color.rgb(206, 92, 62));

        float ratio = horizonRatio(photo);
        float centerY = scene.top + scene.height() * ratio;
        float bandH = scene.height() * BAND_HEIGHT_SHARE;
        float left = scene.left + scene.width() * BAND_LEFT_SHARE;
        float top = centerY - bandH / 2f;

        int r = Color.red(structural);
        int g = Color.green(structural);
        int b = Color.blue(structural);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, top, 0, top + bandH,
                new int[]{
                        Color.argb(0, r, g, b),
                        Color.argb(BAND_PEAK_ALPHA, r, g, b),
                        Color.argb(0, r, g, b)},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(left, top, scene.right, top + bandH, paint);
    }

    /** 找出原图最强的水平结构所在的相对行（0..1），作为构成色的来源位置。 */
    private static float horizonRatio(Bitmap photo) {
        int dw = 120;
        int dh = Math.max(2, Math.round(dw * (float) photo.getHeight() / photo.getWidth()));

        int[] pixels = new int[dw * dh];
        Bitmap small = Bitmap.createScaledBitmap(photo, dw, dh, true);
        small.getPixels(pixels, 0, dw, 0, 0, dw, dh);
        if (small != photo && !small.isRecycled()) small.recycle();

        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            gray[i] = (int) (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p));
        }

        long best = -1;
        int bestRow = dh / 2;
        for (int y = 1; y < dh - 1; y++) {
            long sum = 0;
            for (int x = 0; x < dw; x++) {
                sum += Math.abs(gray[(y + 1) * dw + x] - gray[(y - 1) * dw + x]);
            }
            if (sum > best) {
                best = sum;
                bestRow = y;
            }
        }
        return (bestRow + 0.5f) / dh;
    }

    // ================================================================
    //  5. 外缘溶解
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
