package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖幅胶片的四周留白。
 *
 * <p>横幅从一开始就留了 edgeRatio，竖幅漏了：那边直接把帧宽解成
 * {@code 1/(1+2×留边系数)}，整截正好占满画布宽度——胶片顶着左右边缘，
 * 台面在读图时等于不存在，又变回"贴在纸边上的一条"。
 *
 * <p>这条是用户报的第二次同类问题（第一次是上边没留白）。
 * 所以这里不光断言"有留白"，还断言<b>四周那档留白是同一个值</b>——
 * 只要有一条边漏了，比例就对不上。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FilmVerticalMarginTest {

    private static final int EXPORT_W = 2000;

    /** 竖幅单张：胶片左右必须留出和上边一样宽的白。 */
    @Test
    public void verticalStripLeavesMarginsOnBothSides() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> sizes = new ArrayList<>();
        sizes.add(new int[]{2000, 3000});          // 2:3 竖幅
        RectF[] strips = FilmBorderRenderer.stripsFor(sizes, cfg, EXPORT_W, 0);
        assertNotNull(strips);
        assertEquals(1, strips.length);

        RectF strip = strips[0];
        int canvasH = FilmBorderRenderer.heightFor(bitmap(2000, 3000), cfg, EXPORT_W);

        float leftMargin = strip.left;
        float rightMargin = EXPORT_W - strip.right;
        float topMargin = strip.top;

        assertTrue("左右必须留白，实际左 " + leftMargin + " 右 " + rightMargin,
                leftMargin > 1f && rightMargin > 1f);
        assertEquals("左右留白必须相等", leftMargin, rightMargin, 2f);
        assertEquals("左右留白要和上边一档，否则四周不均匀",
                topMargin, leftMargin, 2f);
        assertTrue("整截不能占满宽度，实际占 " + strip.width() + " / " + EXPORT_W,
                strip.width() < EXPORT_W * 0.95f);
        assertTrue("画布高度也要合理", canvasH > strip.bottom);
    }

    /** 极窄的竖条同样要留在画布里。 */
    @Test
    public void veryNarrowPortraitStillFitsWithMargins() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> sizes = new ArrayList<>();
        sizes.add(new int[]{900, 4000});           // 比 9:16 还窄
        RectF[] strips = FilmBorderRenderer.stripsFor(sizes, cfg, EXPORT_W, 0);
        assertNotNull(strips);

        RectF strip = strips[0];
        assertEquals("左右留白必须相等", strip.left, EXPORT_W - strip.right, 2f);
        assertTrue("不能贴到左边", strip.left > 1f);
        assertTrue("不能贴到右边", strip.right < EXPORT_W - 1f);
    }

    /** 关掉片边文字之后，四周留白仍然一致。 */
    @Test
    public void captionOffKeepsTheSameSideMargins() {
        FilmBorderConfig withCaption = new FilmBorderConfig();
        FilmBorderConfig without = new FilmBorderConfig();
        without.showCaption = false;

        List<int[]> sizes = new ArrayList<>();
        sizes.add(new int[]{2000, 3000});

        RectF[] a = FilmBorderRenderer.stripsFor(sizes, withCaption, EXPORT_W, 0);
        RectF[] b = FilmBorderRenderer.stripsFor(sizes, without, EXPORT_W, 0);
        assertNotNull(a);
        assertNotNull(b);

        assertEquals("开关文字不该改变横向排版", a[0].left, b[0].left, 1f);
        assertEquals("开关文字不该改变横向排版", a[0].right, b[0].right, 1f);
    }

    private static Bitmap bitmap(int w, int h) {
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }
}
