package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.example.photopalettepro.film.FilmStock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 用 Robolectric 的 NATIVE 图形模式（真实 Skia）渲染胶片边框并导出 PNG，
 * 便于在不启动设备的情况下肉眼校验片基、齿孔与片边排版。
 *
 * <p>产物：app/build/film-out/*.png
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FilmBorderSmokeTest {

    /**
     * 风格列表。
     *
     * <p>故意写成方法而不是 {@code static final} 字段：静态字段会在类加载时
     * 触发 {@link FilmStock} 的静态初始化，而它内部用到了 {@code android.graphics.Color}——
     * 在 Robolectric 的沙箱装载完成之前就把 android 类拉起来会直接炸掉
     * （系统类装载器里只有 android.jar 的 stub，一调用就是 {@code RuntimeException: Stub!}）。
     */
    private static String[] allStyles() {
        return FilmStock.STYLE_OPTIONS.clone();
    }

    // ------------------------------------------------------------------
    //  文案规则：留空必须有装饰文案兜底
    // ------------------------------------------------------------------

    @Test
    public void emptyFieldsFallBackToDecorativeCopy() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.style = FilmStock.STYLE_CLASSIC;

        // 全空 —— 型号行与参数行都回落到预设的装饰文案
        assertEquals("PORTRA 400", cfg.resolveStock());
        assertEquals("35MM · 135 · COLOR NEGATIVE", cfg.resolveSpec());
        assertTrue(cfg.resolveFrameNumber().length() > 0);

        // 只有型号被填 —— 型号用填的，参数行仍是装饰文案
        cfg.stock = "  EKTAR 100  ";
        assertEquals("EKTAR 100", cfg.resolveStock());
        assertEquals("35MM · 135 · COLOR NEGATIVE", cfg.resolveSpec());

        // 填了又被删空（等价于空串）—— 仍然回落到装饰文案
        cfg.stock = "   ";
        assertEquals("PORTRA 400", cfg.resolveStock());

        // 参数行只要有一项就显示那一项，不再拿装饰文案凑数
        cfg.camera = "SONY A7M4";
        assertEquals("SONY A7M4", cfg.resolveSpec());

        cfg.lens = "FE 50MM F1.8";
        cfg.exposure = "1/200s · f/1.8 · ISO400";
        cfg.date = "2026.09.03";
        assertEquals("SONY A7M4 · FE 50MM F1.8 · 1/200s · f/1.8 · ISO400 · 2026.09.03",
                cfg.resolveSpec());
    }

    @Test
    public void everyStyleHasDecorativeCopy() {
        for (String style : allStyles()) {
            FilmStock s = FilmStock.of(style);
            assertTrue(style + " 缺少装饰车型号", !s.decorativeStock.isEmpty());
            assertTrue(style + " 缺少装饰性片幅规格", !s.decorativeSpec.isEmpty());
            assertTrue(style + " 缺少装饰帧号", !s.decorativeFrame.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    //  几何：不裁切、按预算高度落笔
    // ------------------------------------------------------------------

    @Test
    public void renderKeepsPhotoAspectAndMatchesPredictedHeight() {
        Bitmap landscape = makeSamplePhoto(1600, 1067);   // 3:2 横幅
        Bitmap portrait = makeSamplePhoto(1067, 1600);    // 2:3 竖幅

        FilmBorderConfig cfg = new FilmBorderConfig();

        for (Bitmap photo : new Bitmap[]{landscape, portrait}) {
            int predicted = FilmBorderRenderer.heightFor(photo, cfg, 1200);
            Bitmap out = FilmBorderRenderer.render(photo, cfg, 1200);
            assertNotNull(out);
            assertEquals("输出宽度应为请求宽度", 1200, out.getWidth());
            assertEquals("实际高度应等于预算高度", predicted, out.getHeight());

            // 照片必须严格保持原比例：那一格的长宽比应当等于源图
            List<int[]> sizes = new ArrayList<>();
            sizes.add(new int[]{photo.getWidth(), photo.getHeight()});
            int[][] cells = FilmBorderRenderer.cellSizesFor(sizes, cfg, 1200, 0);
            assertNotNull(cells);
            assertEquals("单张只该有一格", 1, cells.length);

            float srcAspect = photo.getWidth() / (float) photo.getHeight();
            float cellAspect = cells[0][0] / (float) cells[0][1];
            assertEquals("照片不能被拉伸", srcAspect, cellAspect, 0.02f);
        }
    }

    // ------------------------------------------------------------------
    //  齿孔规格：全部由 135 胶片的真实尺寸反推
    // ------------------------------------------------------------------

    @Test
    public void perforationsMatchTheRealKsSpec() {
        // 把「帧高」当成 24mm，三个比例就该还原出 135 胶片的真实尺寸
        assertEquals("KS 片孔节距 4.75mm", 4.75f, FilmBorderRenderer.perfPitchFor(24f), 0.02f);
        assertEquals("片孔长 1.98mm", 1.98f, FilmBorderRenderer.perfLengthFor(24f), 0.02f);
        assertEquals("片孔宽 2.79mm", 2.79f, FilmBorderRenderer.perfCrossFor(24f), 0.02f);
    }

    @Test
    public void aFrameSpansEightPerforationsOfFilmAdvance() {
        float pitch = FilmBorderRenderer.perfPitchFor(24f);

        // 36mm 画幅 + 2mm 片间空档 = 8 个节距 —— 这就是「一格 8 个孔」
        assertEquals("一格是 8 个齿孔的走片量", 38f, 8f * pitch, 0.1f);

        // 画幅本身横跨 7.58 个节距：不是 9 个，也不是 6 个
        float across = 36f / pitch;
        assertTrue("画幅应横跨 7~8 个节距，实际 " + across, across > 7f && across < 8f);
    }

    @Test
    public void perforationsShrinkWithTheFrames() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> single = new ArrayList<>();
        single.add(new int[]{3000, 2000});
        int[][] oneRow = FilmBorderRenderer.cellSizesFor(single, cfg, 2000, 0);
        assertNotNull(oneRow);

        // 九张挤在三行里，格子小得多——旧实现里孔的大小跟照片无关，这时就会「一眼假」
        List<int[]> many = new ArrayList<>();
        for (int i = 0; i < 9; i++) many.add(new int[]{3000, 2000});
        int[][] dense = FilmBorderRenderer.cellSizesFor(many, cfg, 2000, 0);
        assertNotNull(dense);

        float singleHole = FilmBorderRenderer.perfCrossFor(oneRow[0][1]);
        float denseHole = FilmBorderRenderer.perfCrossFor(dense[0][1]);
        assertTrue("照片小了孔必须跟着小，实际 " + denseHole + " vs " + singleHole,
                denseHole < singleHole * 0.6f);

        // 而且相对大小恒定：孔宽 / 帧高 恒为 2.79/24
        assertEquals(2.79f / 24f, singleHole / oneRow[0][1], 0.002f);
        assertEquals(2.79f / 24f, denseHole / dense[0][1], 0.002f);
    }

    @Test
    public void holesToggleShrinksTheLongEdgeMargin() {
        Bitmap photo = makeSamplePhoto(1600, 1067);

        FilmBorderConfig withHoles = new FilmBorderConfig();
        withHoles.sprocketHoles = true;

        FilmBorderConfig withoutHoles = new FilmBorderConfig();
        withoutHoles.sprocketHoles = false;

        int h1 = FilmBorderRenderer.heightFor(photo, withHoles, 1200);
        int h2 = FilmBorderRenderer.heightFor(photo, withoutHoles, 1200);
        assertTrue("关掉齿孔后上下边距应变薄，总高度随之变小", h2 < h1);
    }

    @Test
    public void wideNarrowAndStandardDifferInSize() {
        Bitmap photo = makeSamplePhoto(1600, 1067);
        FilmBorderConfig cfg = new FilmBorderConfig();

        cfg.width = FilmBorderConfig.WIDTH_NARROW;
        int narrow = FilmBorderRenderer.heightFor(photo, cfg, 1200);
        cfg.width = FilmBorderConfig.WIDTH_NORMAL;
        int normal = FilmBorderRenderer.heightFor(photo, cfg, 1200);
        cfg.width = FilmBorderConfig.WIDTH_WIDE;
        int wide = FilmBorderRenderer.heightFor(photo, cfg, 1200);

        assertTrue("窄 < 标准 < 宽", narrow < normal && normal < wide);
    }

    @Test
    public void extremePanoramaIsCappedByPixelBudget() {
        Bitmap panorama = makeSamplePhoto(4000, 600);   // 超宽全景
        FilmBorderConfig cfg = new FilmBorderConfig();
        Bitmap out = FilmBorderRenderer.render(panorama, cfg, 3000);
        assertNotNull(out);
        assertTrue("应按像素上限回撤宽度，而不是硬渲染一张巨图",
                (long) out.getWidth() * out.getHeight() <= 26_000_000L);
    }

    @Test
    public void nullInputsReturnNullInsteadOfThrowing() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        assertEquals(null, FilmBorderRenderer.render(null, cfg, 1200));
        assertEquals(0, FilmBorderRenderer.heightFor(null, cfg, 1200));
    }

    // ------------------------------------------------------------------
    //  出图：肉眼校验
    // ------------------------------------------------------------------

    @Test
    public void renderEveryStyleToPng() throws Exception {
        Bitmap landscape = makeSamplePhoto(1600, 1067);
        Bitmap portrait = makeSamplePhoto(1067, 1600);

        File dir = new File("build/film-out");
        assertTrue(dir.exists() || dir.mkdirs());

        // 逐档风格 + 逐档宽度，全部带真实 EXIF 风格文案
        for (String style : allStyles()) {
            FilmBorderConfig cfg = new FilmBorderConfig();
            cfg.style = style;
            cfg.stock = "PORTRA 400";
            cfg.camera = "SONY A7M4";
            cfg.lens = "FE 50MM F1.8";
            cfg.exposure = "1/200s · f/1.8 · ISO400";
            cfg.date = "2026.09.03";

            Bitmap out = FilmBorderRenderer.render(landscape, cfg, 1400);
            assertNotNull(style + " 横幅渲染失败", out);
            writePng(out, new File(dir, "landscape-" + slug(style) + ".png"));
        }

        // 竖幅 + 空文案（走装饰兜底）+ 无齿孔 + 宽边框
        FilmBorderConfig decorative = new FilmBorderConfig();
        decorative.style = FilmStock.STYLE_FUJI;
        Bitmap out = FilmBorderRenderer.render(portrait, decorative, 1400);
        assertNotNull(out);
        writePng(out, new File(dir, "portrait-decorative.png"));

        FilmBorderConfig bare = new FilmBorderConfig();
        bare.style = FilmStock.STYLE_NOIR;
        bare.sprocketHoles = false;
        bare.width = FilmBorderConfig.WIDTH_WIDE;
        bare.camera = "NIKON ZF";
        Bitmap noir = FilmBorderRenderer.render(portrait, bare, 1400);
        assertNotNull(noir);
        writePng(noir, new File(dir, "portrait-noir-noholes-wide.png"));

        System.out.println("胶片边框样例已输出 -> " + dir.getAbsolutePath());
    }

    // ------------------------------------------------------------------
    //  片边文字的排版
    // ------------------------------------------------------------------

    /**
     * 型号行与参数行之间的空白，必须正好是参数行自己的高度。
     *
     * <p>原来是写死在「文字块高度的 80%」——也就是 {@code 0.36 × 0.15 × 画布宽}，
     * 一个和字号毫无关系的比例：字号一小空隙就显得特别大，画布越宽拉得越开。
     */
    @Test
    public void theGapBetweenTheTwoCaptionLinesIsTheSpecLineHeight() {
        float outW = 2000f;
        String stockText = "PORTRA 400";
        String specText = "SONY A7M4 · FE 50MM F1.8 · 1/200S · F/1.8 · ISO400 · 2026.09.03";

        Paint stockPaint = captionPaint(outW * 0.030f, Typeface.BOLD);
        Paint specPaint = captionPaint(outW * 0.0195f, Typeface.NORMAL);

        float[] baselines = FilmBorderRenderer.captionBaselines(
                500f, outW * 0.15f, stockPaint, specPaint, stockText, specText);

        Rect stockInk = new Rect();
        stockPaint.getTextBounds(stockText, 0, stockText.length(), stockInk);
        Rect specInk = new Rect();
        specPaint.getTextBounds(specText, 0, specText.length(), specInk);

        float blank = (baselines[1] + specInk.top) - (baselines[0] + stockInk.bottom);

        assertEquals("两行之间的空白应当等于参数行墨迹的高度",
                specInk.height(), blank, 1f);
    }

    @Test
    public void theCaptionGapScalesWithTheOutputWidth() {
        // 空白是按字形算的，不是固定像素值：字号翻倍，空白也该翻倍
        float small = captionBlank(1000f);
        float large = captionBlank(2000f);

        assertEquals("字号翻倍，空白也该翻倍", small * 2f, large, 2f);
    }

    private static float captionBlank(float outW) {
        String stockText = "PORTRA 400";
        String specText = "SONY A7M4 · FE 50MM F1.8";

        Paint stockPaint = captionPaint(outW * 0.030f, Typeface.BOLD);
        Paint specPaint = captionPaint(outW * 0.0195f, Typeface.NORMAL);

        float[] baselines = FilmBorderRenderer.captionBaselines(
                500f, outW * 0.15f, stockPaint, specPaint, stockText, specText);

        Rect stockInk = new Rect();
        stockPaint.getTextBounds(stockText, 0, stockText.length(), stockInk);
        Rect specInk = new Rect();
        specPaint.getTextBounds(specText, 0, specText.length(), specInk);

        return (baselines[1] + specInk.top) - (baselines[0] + stockInk.bottom);
    }

    private static Paint captionPaint(float size, int style) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-condensed", style));
        paint.setTextSize(size);
        return paint;
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    private static String slug(String style) {
        switch (style) {
            case FilmStock.STYLE_KODAK:
                return "kodak";
            case FilmStock.STYLE_FUJI:
                return "fuji";
            case FilmStock.STYLE_NOIR:
                return "noir";
            case FilmStock.STYLE_SILVER:
                return "silver";
            default:
                return "classic";
        }
    }

    private static void writePng(Bitmap bmp, File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    /** 合成一张有明确主色特征的风景照。 */
    private static Bitmap makeSamplePhoto(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        boolean landscape = w >= h;
        float horizon = landscape ? 0.62f : 0.58f;

        p.setShader(new LinearGradient(0, 0, 0, h * horizon,
                Color.rgb(118, 168, 210), Color.rgb(228, 216, 192), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * horizon, p);
        p.setShader(null);

        p.setColor(Color.rgb(94, 112, 104));
        Path m = new Path();
        m.moveTo(0, h * horizon);
        m.lineTo(w * 0.20f, h * horizon * 0.52f);
        m.lineTo(w * 0.38f, h * horizon * 0.94f);
        m.lineTo(w * 0.56f, h * horizon * 0.45f);
        m.lineTo(w * 0.78f, h * horizon * 0.90f);
        m.lineTo(w, h * horizon * 0.61f);
        m.lineTo(w, h * horizon);
        m.close();
        c.drawPath(m, p);

        p.setShader(new LinearGradient(0, h * horizon, 0, h,
                Color.rgb(62, 158, 160), Color.rgb(26, 90, 104), Shader.TileMode.CLAMP));
        c.drawRect(0, h * horizon, w, h, p);
        p.setShader(null);

        p.setColor(Color.rgb(214, 202, 168));
        c.drawRect(0, h * horizon - h * 0.006f, w, h * horizon + h * 0.024f, p);

        p.setColor(Color.rgb(208, 124, 76));
        c.drawCircle(w * 0.74f, h * horizon * 0.32f, Math.min(w, h) * 0.052f, p);

        return b;
    }
}

