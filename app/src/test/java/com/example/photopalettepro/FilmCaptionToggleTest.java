package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

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
 * 片边文字开关：关掉之后画布只留胶片。
 *
 * <p>要求来自实际使用：齿孔开关没什么用（孔本来就该有），
 * 而"要不要下面那行文字"是常调的——想要纯胶片效果时就该只剩胶片，
 * 而不是在底下留一块没有内容的空白。
 *
 * <p>所以关掉时要满足三件事：
 * <ul>
 *   <li>画布<b>变矮</b>（文字块连同它占的高度一起去掉）；</li>
 *   <li>上下留白<b>对称</b>——上面留多少，下面就留多少；</li>
 *   <li>是真的<b>没画字</b>，而不只是裁掉了。</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FilmCaptionToggleTest {

    private static final int EXPORT_W = 2000;

    @Test
    public void hidingTheCaptionShrinksTheCanvas() {
        Bitmap photo = samplePhoto(1600, 1067);

        FilmBorderConfig withCaption = new FilmBorderConfig();
        FilmBorderConfig without = new FilmBorderConfig();
        without.showCaption = false;

        int hWith = FilmBorderRenderer.heightFor(photo, withCaption, EXPORT_W);
        int hWithout = FilmBorderRenderer.heightFor(photo, without, EXPORT_W);

        assertTrue("关掉片边文字后画布应当变矮：" + hWithout + " vs " + hWith,
                hWithout < hWith);
    }

    @Test
    public void marginsStaySymmetricWithTheCaptionOff() {
        Bitmap photo = samplePhoto(1600, 1067);

        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.showCaption = false;

        int canvasH = FilmBorderRenderer.heightFor(photo, cfg, EXPORT_W);
        List<int[]> sizes = new ArrayList<>();
        sizes.add(new int[]{photo.getWidth(), photo.getHeight()});
        RectF[] strips = FilmBorderRenderer.stripsFor(sizes, cfg, EXPORT_W, 0);
        assertNotNull(strips);
        assertTrue(strips.length > 0);

        int topMargin = Math.round(strips[0].top);
        int bottomMargin = canvasH - Math.round(strips[strips.length - 1].bottom);

        assertEquals("关掉文字后上下留白必须对称，否则整张图像裁歪了",
                topMargin, bottomMargin, 2f);
    }

    @Test
    public void multiFrameAlsoStaysSymmetricWithTheCaptionOff() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.showCaption = false;

        List<Bitmap> photos = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            photos.add(samplePhoto(1600, 1067));
            sizes.add(new int[]{1600, 1067});
        }

        int canvasH = FilmBorderRenderer.heightFor(photos, cfg, EXPORT_W, 0);
        RectF[] strips = FilmBorderRenderer.stripsFor(sizes, cfg, EXPORT_W, 0);
        assertNotNull(strips);

        int topMargin = Math.round(strips[0].top);
        int bottomMargin = canvasH - Math.round(strips[strips.length - 1].bottom);

        assertEquals("多张时同样要对称", topMargin, bottomMargin, 2f);
    }

    /**
     * 关掉之后是真的没画字，而不只是画布矮了。
     *
     * <p>做法：拿两个只有「胶片型号」不同的配置各渲一张——
     * 文字还画着的话，型号一变就会有像素差异；已经关掉的话，两张必须一模一样。
     *
     * <p>再加一条反向对照：把开关打开，同样两张必须<b>不一样</b>。
     * 没有这个对照，上面那条断言证明不了什么（一个永远渲空白图的实现也能过）。
     */
    @Test
    public void theCaptionIsActuallyGoneNotJustShorter() throws Exception {
        Bitmap photo = samplePhoto(1600, 1067);

        FilmBorderConfig a = new FilmBorderConfig();
        a.stock = "PORTRA 400";
        FilmBorderConfig b = new FilmBorderConfig();
        b.stock = "EKTAR 100";

        // 反向对照：开关打开时必须看得出型号差异
        Bitmap wa = FilmBorderRenderer.render(photo, a, EXPORT_W);
        Bitmap wb = FilmBorderRenderer.render(photo, b, EXPORT_W);
        assertNotNull(wa);
        assertNotNull(wb);
        assertTrue("开着文字时，换型号必须画出不同的内容——否则这条测试没有判别力",
                !wa.sameAs(wb));

        // 正式断言：关掉之后，型号怎么变都不该有像素差异
        a.showCaption = false;
        b.showCaption = false;
        Bitmap ra = FilmBorderRenderer.render(photo, a, EXPORT_W);
        Bitmap rb = FilmBorderRenderer.render(photo, b, EXPORT_W);
        assertNotNull(ra);
        assertNotNull(rb);
        assertTrue("文字已经关掉了，换型号不该画出任何东西", ra.sameAs(rb));

        File dir = new File("build/film-out");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "caption-off.png"))) {
            ra.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    // ------------------------------------------------------------------

    private static Bitmap samplePhoto(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float horizon = 0.62f;
        p.setShader(new LinearGradient(0, 0, 0, h * horizon,
                Color.rgb(118, 168, 210), Color.rgb(228, 216, 192), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * horizon, p);
        p.setShader(new LinearGradient(0, h * horizon, 0, h,
                Color.rgb(62, 158, 160), Color.rgb(26, 90, 104), Shader.TileMode.CLAMP));
        c.drawRect(0, h * horizon, w, h, p);
        p.setShader(null);
        p.setColor(Color.rgb(208, 124, 76));
        c.drawCircle(w * 0.74f, h * horizon * 0.32f, Math.min(w, h) * 0.052f, p);
        return b;
    }
}
