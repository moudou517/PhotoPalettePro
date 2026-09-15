package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.example.photopalettepro.helper.LiquidGlassDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 液态玻璃的折射数学。
 *
 * <h3>为什么测这个而不是测着色器</h3>
 *
 * <p>参考实现（rdev/liquid-glass-react）把折射<b>预计算成一张位移贴图</b>——
 * R 存 x 位移、G 存 y 位移——再交给 {@code feDisplacementMap}。
 * 这里照搬：位移贴图在 CPU 上生成，AGSL 那边只剩"采样 + 偏移"几行。
 *
 * <p>好处正是可测性：<b>真正决定观感的那套数学跑在 Java 里</b>，
 * 而 GPU 那几行几乎不可能单独出错。所以这些断言能挡住绝大多数回归，
 * 剩下的（着色器在具体机型上的编译与表现）必须真机验证，这里如实说明没覆盖。
 *
 * <p>核心是一条式子：离边缘越近，采样点越往中心收——这就是"折射"。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LiquidGlassTest {

    private static final int SIZE = 96;

    // ------------------------------------------------------------------
    //  位移贴图
    // ------------------------------------------------------------------

    @Test
    public void displacementMapHasTheRightShape() {
        Bitmap map = LiquidGlassDrawable.buildDisplacementMap(SIZE);
        assertNotNull(map);
        assertEquals(SIZE, map.getWidth());
        assertEquals(SIZE, map.getHeight());
    }

    @Test
    public void theCenterIsUndisplaced() {
        Bitmap map = LiquidGlassDrawable.buildDisplacementMap(SIZE);
        assertNotNull(map);

        // 0.5 = 不位移（参考实现里把位移归一化后加 0.5 存进通道）
        int center = map.getPixel(SIZE / 2, SIZE / 2);
        assertEquals("中心的红通道应当是 0.5（无位移）",
                128, Color.red(center), 6);
        assertEquals("中心的绿通道应当是 0.5（无位移）",
                128, Color.green(center), 6);
    }

    /**
     * 位移最大的是<b>角</b>，不是边的中点。
     *
     * <p>内切形状的四条直边贴近元素中线，弯折带集中在四个角——
     * 这正是液态玻璃"边缘弯折"的观感来源。第一版我把形状取成元素本身，
     * 结果整张图都是常数，这条断言就是为了钉住那个错误。
     */
    @Test
    public void theCornersAreTheMostDisplaced() {
        Bitmap map = LiquidGlassDrawable.buildDisplacementMap(SIZE);
        assertNotNull(map);

        int corner = Math.max(
                Math.abs(Color.red(map.getPixel(0, 0)) - 128),
                Math.abs(Color.red(map.getPixel(SIZE - 1, 0)) - 128));
        int midEdge = Math.abs(Color.red(map.getPixel(0, SIZE / 2)) - 128);

        assertTrue("角落必须有明显位移，实际偏移 " + corner, corner > 60);
        assertTrue("角落的位移必须大于直边中点：" + corner + " vs " + midEdge,
                corner > midEdge);
    }

    @Test
    public void theWholeMapIsNotConstant() {
        // 第一版的病：整张图几乎是常数，归一化之后只剩噪声。
        // 一条最直接的护栏——位移贴图必须有真实的变化范围。
        Bitmap map = LiquidGlassDrawable.buildDisplacementMap(SIZE);
        assertNotNull(map);

        int min = 255, max = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int r = Color.red(map.getPixel(x, y));
                min = Math.min(min, r);
                max = Math.max(max, r);
            }
        }
        assertTrue("位移贴图的变化范围太小（" + min + "~" + max + "），等于没有折射",
                max - min > 120);
    }

    @Test
    public void displacementGrowsTowardsTheCorner() {
        Bitmap map = LiquidGlassDrawable.buildDisplacementMap(SIZE);
        assertNotNull(map);

        // 沿对角线从中心走向左上角，位移必须单调增强
        int previous = -1;
        for (int i = SIZE / 2; i >= 0; i -= 4) {
            int r = Color.red(map.getPixel(i, i));
            if (previous >= 0) {
                assertTrue("越靠角位移应当越大：i=" + i + " 时 " + r + " < " + previous,
                        r >= previous - 2);
            }
            previous = r;
        }
    }

    @Test
    public void theMapIsSymmetric() {
        Bitmap map = LiquidGlassDrawable.buildDisplacementMap(SIZE);
        assertNotNull(map);

        // 左右镜像后的位移量应当对称（方向相反，所以 R 互为镜像）
        for (int y = 0; y < SIZE; y += 12) {
            int l = Color.red(map.getPixel(0, y));
            int r = Color.red(map.getPixel(SIZE - 1, y));
            assertEquals("第 " + y + " 行左右应当对称", 255, l + r, 8);
        }
    }

    @Test
    public void degenerateSizeIsHandled() {
        assertEquals(null, LiquidGlassDrawable.buildDisplacementMap(0));
        assertEquals(null, LiquidGlassDrawable.buildDisplacementMap(-3));
    }

    // ------------------------------------------------------------------
    //  底层的 SDF 与 smoothstep
    // ------------------------------------------------------------------

    @Test
    public void sdfIsNegativeInsideAndPositiveOutside() {
        // 形状中心：深在内部
        assertTrue("中心应当在形状内部", LiquidGlassDrawable.roundedRectSdf(0f, 0f) < 0f);

        // 形状内切在中间：0.2 还在形状里，0.4 已经在弯折带里（元素半宽是 0.5）
        assertTrue("0.2 还在形状内部", LiquidGlassDrawable.roundedRectSdf(0.2f, 0f) < 0f);
        assertTrue("0.4 应当在形状外的弯折带",
                LiquidGlassDrawable.roundedRectSdf(0.4f, 0f) > 0f);
        // 元素边缘必须落进弯折带，否则折射无从发生
        assertTrue("元素边缘要能触发弯折（d > 0.15）",
                LiquidGlassDrawable.roundedRectSdf(0.5f, 0f) > 0.15f);

        // 四个方向对称
        float a = LiquidGlassDrawable.roundedRectSdf(0.3f, 0f);
        float b = LiquidGlassDrawable.roundedRectSdf(-0.3f, 0f);
        float c = LiquidGlassDrawable.roundedRectSdf(0f, 0.3f);
        float d = LiquidGlassDrawable.roundedRectSdf(0f, -0.3f);
        assertEquals(a, b, 0.0001f);
        assertEquals(a, c, 0.0001f);
        assertEquals(a, d, 0.0001f);
    }

    @Test
    public void smoothStepMatchesTheGlslDefinition() {
        // 边界
        assertEquals(0f, LiquidGlassDrawable.smoothStep(0f, 1f, 0f), 0.0001f);
        assertEquals(1f, LiquidGlassDrawable.smoothStep(0f, 1f, 1f), 0.0001f);
        // 中点：三次平滑后正好 0.5
        assertEquals(0.5f, LiquidGlassDrawable.smoothStep(0f, 1f, 0.5f), 0.0001f);
        // 区间外被夹住
        assertEquals(0f, LiquidGlassDrawable.smoothStep(0f, 1f, -2f), 0.0001f);
        assertEquals(1f, LiquidGlassDrawable.smoothStep(0f, 1f, 2f), 0.0001f);
        // 反向区间（参考实现里用的是 smoothstep(0.8, 0, d)）
        assertEquals(1f, LiquidGlassDrawable.smoothStep(0.8f, 0f, 0f), 0.0001f);
        assertEquals(0f, LiquidGlassDrawable.smoothStep(0.8f, 0f, 0.8f), 0.0001f);
    }

    // ------------------------------------------------------------------
    //  背景快照的尺度对齐（真机上那块"方形色斑"的根因）
    // ------------------------------------------------------------------

    /**
     * 着色器拿<b>屏幕坐标</b>去采样背景快照，而快照只有屏幕的 1/6。
     *
     * <p>不给 BitmapShader 缩放矩阵时它按 1:1 铺，于是每次采样都落在贴图外面，
     * CLAMP 到边缘那一个像素——整圈就变成一个死板的纯色。
     * 真机截图里卡片中那块"方形色斑"就是这么来的：一圈被刷成了
     * {@code 238,240,235}（贴图边缘色），和中间真正透出来的
     * {@code 248,252,255} 形成一条硬边。
     */
    @Test
    public void backdropScaleMapsTheSnapshotOntoTheScreen() {
        // 411×891dp、密度 3 → 屏幕 1233×2673；快照是它的 1/6
        float[] scale = LiquidGlassDrawable.backdropScale(1233, 2673, 206, 446);

        assertEquals("横向要放大 6 倍", 6f, scale[0], 0.05f);
        assertEquals("纵向要放大 6 倍", 6f, scale[1], 0.05f);

        // 关键性质：屏幕右下角换算回贴图坐标，必须正好落在贴图的右下角
        assertEquals(206f, 1233 / scale[0], 1f);
        assertEquals(446f, 2673 / scale[1], 1f);
    }

    @Test
    public void backdropScaleIsOneToOneWhenTheSnapshotIsFullSize() {
        float[] scale = LiquidGlassDrawable.backdropScale(1080, 2400, 1080, 2400);
        assertEquals(1f, scale[0], 0.0001f);
        assertEquals(1f, scale[1], 0.0001f);
    }

    @Test
    public void backdropScaleSurvivesDegenerateInput() {
        float[] scale = LiquidGlassDrawable.backdropScale(0, 0, 0, 0);
        assertEquals(1f, scale[0], 0.0001f);
        assertEquals(1f, scale[1], 0.0001f);
    }

    // ------------------------------------------------------------------
    //  折射带必须是一圈「边」，不是一副粗相框
    // ------------------------------------------------------------------

    /**
     * 折射带太厚的话，卡片会被切成"里面一块、外面一圈"——正是用户看到的方块感。
     * 第一版取了短边的 18%，1200px 的卡片就是 216px 厚，等于给卡片套了个粗相框。
     */
    @Test
    public void theRefractionBandStaysAThinEdge() {
        assertTrue("折射带不该超过卡片短边的 10%，实际 " + LiquidGlassDrawable.edgeBandRatio(),
                LiquidGlassDrawable.edgeBandRatio() <= 0.10f);
        assertTrue("也不能薄到看不见，实际 " + LiquidGlassDrawable.edgeBandRatio(),
                LiquidGlassDrawable.edgeBandRatio() >= 0.02f);
    }
    // ------------------------------------------------------------------
    //  位移贴图的铺法：slice，不是 stretch
    // ------------------------------------------------------------------

    /**
     * 参考实现在 {@code feImage} 上写的是 {@code preserveAspectRatio="xMidYMid slice"}：
     * 等比放大到铺满，再居中裁切。直接拉伸会把方形空间里算好的 SDF 拽成椭圆——
     * 竖长卡片左右两侧的弯折带会比上下窄一半。
     */
    @Test
    public void displacementMapIsSlicedNotStretched() {
        // 卡片 1200×2400，贴图 96×96
        float[] placement = LiquidGlassDrawable.slicePlacement(1200, 2400, 96, 96);

        float scale = placement[0];
        assertEquals("等比：按更长的那一边放大", 2400 / 96f, scale, 0.001f);

        // 放大后横向会超出卡片，两侧各裁掉一半
        float scaledW = 96 * scale;
        assertEquals("左右各裁一半", (1200 - scaledW) / 2f, placement[1], 0.5f);
        assertEquals("纵向正好铺满，不裁", 0f, placement[2], 0.5f);
    }

    @Test
    public void sliceKeepsTheMapSquare() {
        float[] placement = LiquidGlassDrawable.slicePlacement(1200, 2400, 96, 96);
        float scaledW = 96 * placement[0];
        float scaledH = 96 * placement[0];

        assertEquals("等比缩放——宽高必须一致地放大", scaledW, scaledH, 0.001f);
        assertTrue("横向必须铺满（可以超出）", scaledW >= 1200 - 0.5f);
        assertTrue("纵向必须铺满（可以超出）", scaledH >= 2400 - 0.5f);
    }

    @Test
    public void sliceSurvivesDegenerateInput() {
        float[] placement = LiquidGlassDrawable.slicePlacement(0, 0, 0, 0);
        assertEquals(1f, placement[0], 0.0001f);
    }
}
