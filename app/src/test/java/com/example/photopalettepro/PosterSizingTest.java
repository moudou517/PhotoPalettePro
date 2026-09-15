package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 海报里照片的尺寸约束。
 *
 * <p>两条都是"看不见但少了就出事"的规则：
 *
 * <ul>
 *   <li><b>超长画幅要自动缩回来。</b>全景照片按画幅高反推宽度会得到 8900px，
 *       而画布只有 3840——照片横穿出去被裁掉，海报像贴歪了；</li>
 *   <li><b>四周始终留 5%。</b>不管多宽多长，照片都贴不到出血线。</li>
 * </ul>
 *
 * <p>抽成纯函数之后就能把各种画幅直接喂进来验，不用真的渲一张 4K 出来。
 */
public class PosterSizingTest {

    private static final int W = 3840;   // 海报画布宽
    private static final int H = 2160;   // 海报画布高

    /** 安全区占画布的比例。 */
    private static final float MARGIN = 0.05f;

    // ------------------------------------------------------------------
    //  超长画幅
    // ------------------------------------------------------------------

    @Test
    public void ultraWidePanoramaIsScaledBackIntoTheCanvas() {
        int[] size = PosterRenderer.fitPhotoSize(4000, 600, (int) (H * 0.618f), W, H);

        assertTrue("照片宽度 " + size[0] + " 超出了画布 " + W + " 的安全区",
                size[0] <= W * (1f - 2f * MARGIN) + 1);
        assertTrue("照片高度不该超过画布", size[1] <= H);
    }

    @Test
    public void extremePanoramaKeepsItsAspectRatio() {
        int[] size = PosterRenderer.fitPhotoSize(4000, 600, (int) (H * 0.618f), W, H);

        float sourceAspect = 4000 / 600f;
        float fittedAspect = size[0] / (float) size[1];
        assertEquals("缩回来也必须保持原比例，不能被拉伸", sourceAspect, fittedAspect, 0.02f);
    }

    @Test
    public void panoramaGetsWiderButShorterThanTheNormalFrame() {
        int normalH = (int) (H * 0.618f);

        int[] normal = PosterRenderer.fitPhotoSize(3000, 2000, normalH, W, H);
        int[] panorama = PosterRenderer.fitPhotoSize(4000, 600, normalH, W, H);

        assertEquals("3:2 走原画幅高，不受安全区影响", normalH, normal[1]);
        assertTrue("全景要更宽", panorama[0] > normal[0]);
        assertTrue("全景要更矮", panorama[1] < normal[1]);
    }

    // ------------------------------------------------------------------
    //  5% 安全区
    // ------------------------------------------------------------------

    @Test
    public void everyAspectRatioKeepsTheFivePercentMargin() {
        int normalH = (int) (H * 0.618f);

        int[][] aspects = {
                {3000, 2000},   // 3:2
                {1600, 1067},   // 普通横幅
                {3840, 2160},   // 16:9
                {4000, 1000},   // 4:1 超宽
                {5000, 500},    // 10:1 全景
                {1000, 4000},   // 竖幅（走另一条分支）
                {600, 4000},    // 极窄竖条
        };

        for (int[] a : aspects) {
            int[] size = PosterRenderer.fitPhotoSize(a[0], a[1], normalH, W, H);
            float label = a[0] / (float) a[1];

            assertTrue(a[0] + "x" + a[1] + "（比例 " + label + "）宽度超出安全区："
                            + size[0] + " > " + (W * (1f - 2f * MARGIN)),
                    size[0] <= W * (1f - 2f * MARGIN) + 1);
            assertTrue(a[0] + "x" + a[1] + " 高度超出安全区：" + size[1],
                    size[1] <= H * (1f - 2f * MARGIN) + 1);
        }
    }

    @Test
    public void centeringLeavesEqualMarginsOnBothSides() {
        int[] size = PosterRenderer.fitPhotoSize(4000, 1000, (int) (H * 0.618f), W, H);

        int x = PosterRenderer.centeredX(size[0], W);
        int y = PosterRenderer.centeredY(size[1], H);

        assertTrue("左边距应当 >= 5%", x >= W * MARGIN - 1);
        assertEquals("左右边距必须相等", x, W - size[0] - x);
        assertEquals("上下边距必须相等", y, H - size[1] - y);
    }

    @Test
    public void degenerateInputDoesNotBlowUp() {
        assertEquals(1, PosterRenderer.fitPhotoSize(0, 0, 100, W, H)[0]);
        assertEquals(1, PosterRenderer.fitPhotoSize(-5, 100, 100, W, H)[0]);
        assertEquals(1, PosterRenderer.fitPhotoSize(100, 100, 100, 0, 0)[0]);
    }
}
