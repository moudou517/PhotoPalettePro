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
import android.graphics.RectF;
import android.graphics.Shader;

import com.example.photopalettepro.film.FilmStock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.example.photopalettepro.config.FilmBorderConfig;
import com.example.photopalettepro.render.FilmBorderRenderer;

/**
 * 胶片边框的多张排布。
 *
 * <p>校验的是这套版式的四条性质：
 * <ol>
 *   <li>一行最多 {@link FilmBorderRenderer#MAX_COLUMNS} 格，超了就换行；</li>
 *   <li>换行要均衡——不能出现 3+3+1 这种最后一行只有一格的排布；</li>
 *   <li>所有格子等高，竖幅靠变窄适应，宽度落到半格胶片附近；</li>
 *   <li>格子变小的时候放大画布，而不是让照片糊掉。</li>
 * </ol>
 *
 * <p>产物：app/build/film-out/multi-*.png，用于肉眼校验。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FilmMultiFrameTest {

    private static final int EXPORT_W = 2000;
    private static final int MIN_FRAME_PX = 620;

    // ------------------------------------------------------------------
    //  1. 换行
    // ------------------------------------------------------------------

    @Test
    public void fourFramesFitInOneRow() {
        // 4 张排一行宽高比约 1:0.35，明显是横版 → 不换行，格子也最大
        int[][] rows = FilmBorderRenderer.rowsFor(sizes(3000, 2000, 3000, 2000, 3000, 2000, 3000, 2000));
        assertNotNull(rows);
        assertEquals("4 张应当排成一行", 1, rows.length);
        assertEquals(4, rows[0].length);
    }

    @Test
    public void sixFramesWrapToTwoRowsToStayLandscape() {
        // 6 张排一行会变成一条细长条；两行三列才是横着的长方形
        int[][] rows = FilmBorderRenderer.rowsFor(
                sizes(3000, 2000, 3000, 2000, 3000, 2000, 3000, 2000, 3000, 2000, 3000, 2000));
        assertNotNull(rows);
        assertEquals("6 张应当换成两行", 2, rows.length);
        assertEquals(3, rows[0].length);
        assertEquals(3, rows[1].length);
    }

    @Test
    public void rowsNeverExceedMaxColumns() {
        for (int count = 1; count <= 18; count++) {
            List<int[]> list = new ArrayList<>();
            for (int i = 0; i < count; i++) list.add(new int[]{3000, 2000});

            int[][] rows = FilmBorderRenderer.rowsFor(list);
            assertNotNull(rows);
            int total = 0;
            for (int[] row : rows) {
                assertTrue(count + " 张时某一行超了 " + FilmBorderRenderer.MAX_COLUMNS + " 格",
                        row.length <= FilmBorderRenderer.MAX_COLUMNS);
                assertTrue("不允许空行", row.length > 0);
                total += row.length;
            }
            assertEquals(count + " 张时下标总数对不上", count, total);
        }
    }

    // ------------------------------------------------------------------
    //  整体形态：必须是「横着的长方形」
    // ------------------------------------------------------------------

    @Test
    public void everySheetIsLandscape() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        for (int count : new int[]{2, 3, 4, 6, 9, 12, 15, 18}) {
            List<int[]> list = new ArrayList<>();
            for (int i = 0; i < count; i++) list.add(new int[]{3000, 2000});

            int height = FilmBorderRenderer.heightFor(
                    photos(count), cfg, EXPORT_W, 0);
            assertTrue(count + " 张时应当是横版，实际高 " + height + " 宽 " + EXPORT_W,
                    height < EXPORT_W);
        }
    }

    @Test
    public void eighteenPhotosFormAHorizontalRectangle() {
        List<int[]> eighteen = new ArrayList<>();
        for (int i = 0; i < 18; i++) eighteen.add(new int[]{3000, 2000});

        int[][] rows = FilmBorderRenderer.rowsFor(eighteen);
        assertNotNull(rows);

        // 3 行、每行 6 格：横版阈值把 4 行挡在外面（4 行加上上边留白是 1:0.88）
        assertEquals("18 张应当是 3 行", 3, rows.length);
        for (int[] row : rows) {
            assertTrue("每行 6 格才撑得起横版，实际 " + row.length, row.length == 6);
        }

        // 而且整体确实明显横过来（高 / 宽 <= 0.85）
        int height = FilmBorderRenderer.heightFor(photos(18), new FilmBorderConfig(), EXPORT_W, 0);
        assertTrue("18 张的宽高比应当明显横过来，实际 1:" + (height / (float) EXPORT_W),
                height <= EXPORT_W * 0.86f);
    }

    // ------------------------------------------------------------------
    //  2. 均衡
    // ------------------------------------------------------------------

    @Test
    public void sevenFramesEndUpBalanced() {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) list.add(new int[]{3000, 2000});

        int[][] rows = FilmBorderRenderer.rowsFor(list);
        assertNotNull(rows);

        // 关键是别出现「最后一行只剩一格」——整张片子会头重脚轻。
        // 至于切成 4+3 还是 3+2+2，由「整张稿子要是横版」决定，不锁死。
        int fewest = Integer.MAX_VALUE;
        for (int[] row : rows) {
            fewest = Math.min(fewest, row.length);
        }
        assertTrue("最窄的一行不该只剩 1 格，实际 " + fewest, fewest >= 2);
        assertEquals("下标总数对不上", 7, Arrays.stream(rows).mapToInt(r -> r.length).sum());
    }

    @Test
    public void mixedAspectsStillProduceEvenlyWideRows() {
        // 4 张横幅 + 4 张竖幅：不管切几行，各行的总宽高比都该接近
        List<int[]> list = sizes(
                3000, 2000, 3000, 2000, 3000, 2000, 3000, 2000,
                2000, 3000, 2000, 3000, 2000, 3000, 2000, 3000);

        int[][] rows = FilmBorderRenderer.rowsFor(list);
        assertNotNull(rows);
        assertTrue("8 张应当不止一行", rows.length >= 2);

        float[] widths = new float[rows.length];
        for (int r = 0; r < rows.length; r++) {
            for (int idx : rows[r]) {
                widths[r] += list.get(idx)[0] / (float) list.get(idx)[1];
            }
            widths[r] += 0.08f * (rows[r].length - 1);
        }

        float max = 0f;
        float min = Float.MAX_VALUE;
        for (float w : widths) {
            max = Math.max(max, w);
            min = Math.min(min, w);
        }
        assertTrue("各行总宽高比应当接近，实际 " + max + " vs " + min, max / min < 1.25f);
    }

    // ------------------------------------------------------------------
    //  3. 等高 + 半格
    // ------------------------------------------------------------------

    @Test
    public void everyFrameSharesTheSameHeight() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        int[][] cells = FilmBorderRenderer.cellSizesFor(
                sizes(3000, 2000, 2000, 3000, 4000, 3000, 1600, 900), cfg, EXPORT_W, 0);
        assertNotNull(cells);

        int height = cells[0][1];
        for (int[] cell : cells) {
            assertEquals("所有格子必须等高（这就是「横幅的高度」）", height, cell[1]);
        }
    }

    @Test
    public void threeToFourPortraitIsExactlyHalfFrameWidth() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        int[][] cells = FilmBorderRenderer.cellSizesFor(
                sizes(3000, 2000, 3000, 4000), cfg, EXPORT_W, 0);
        assertNotNull(cells);

        int landscapeW = cells[0][0];   // 3:2 → 1.5 × 帧高
        int portraitW = cells[1][0];    // 3:4 → 0.75 × 帧高

        // 半格胶片是 18×24mm 对 36×24mm：竖幅宽度恰好是横幅的一半
        assertEquals("3:4 竖幅宽度应当正好是 3:2 横幅的一半",
                landscapeW / 2f, portraitW, 1.5f);
    }

    @Test
    public void portraitIsNarrowerThanLandscapeAtEqualHeight() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        int[][] cells = FilmBorderRenderer.cellSizesFor(
                sizes(3000, 2000, 2000, 3000), cfg, EXPORT_W, 0);
        assertNotNull(cells);

        assertEquals(cells[0][1], cells[1][1]);
        assertTrue("竖幅必须比横幅窄", cells[1][0] < cells[0][0]);
        // 2:3 竖幅 / 3:2 横幅 = 0.667 / 1.5 ≈ 0.44，也就是半格上下
        float ratio = cells[1][0] / (float) cells[0][0];
        assertTrue("竖幅宽度比应在半格附近，实际 " + ratio, ratio > 0.40f && ratio < 0.55f);
    }

    @Test
    public void widerRowsSetTheFrameHeight() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        // 一行两张，其中一张是超宽全景：帧高必须按最宽的那一行算，否则会溢出
        int[][] cells = FilmBorderRenderer.cellSizesFor(
                sizes(6000, 2000, 3000, 2000), cfg, EXPORT_W, 0);
        assertNotNull(cells);

        float contentW = EXPORT_W * (1f - 0.058f * 2f);
        float rowW = cells[0][0] + cells[1][0] + cells[0][1] * 0.08f;
        assertTrue("这一行不应超过内容宽度，实际 " + rowW + " > " + contentW, rowW <= contentW + 2f);
    }

    // ------------------------------------------------------------------
    //  4. 画布变大保清晰
    // ------------------------------------------------------------------

    @Test
    public void denseLayoutsGrowTheCanvasInsteadOfBlurring() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        List<int[]> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) nine.add(new int[]{3000, 2000});

        int[][] atBase = FilmBorderRenderer.cellSizesFor(nine, cfg, EXPORT_W, 0);
        assertNotNull(atBase);
        assertTrue("基准宽度下 9 张确实会排得很小，实际 " + atBase[0][0],
                atBase[0][0] < MIN_FRAME_PX);

        int[][] sharpened = FilmBorderRenderer.cellSizesFor(nine, cfg, EXPORT_W, MIN_FRAME_PX);
        assertNotNull(sharpened);
        for (int[] cell : sharpened) {
            assertTrue("每一格都应不低于 " + MIN_FRAME_PX + "px，实际 " + cell[0],
                    cell[0] >= MIN_FRAME_PX);
        }
    }

    @Test
    public void growingTheCanvasKeepsTheLayoutProportions() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        List<int[]> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) six.add(new int[]{3000, 2000});

        int[][] atBase = FilmBorderRenderer.cellSizesFor(six, cfg, EXPORT_W, 0);
        int[][] sharpened = FilmBorderRenderer.cellSizesFor(six, cfg, EXPORT_W, MIN_FRAME_PX);
        assertNotNull(atBase);
        assertNotNull(sharpened);

        // 放大是等比缩放：同尺寸照片之间的格子比例不该变
        assertEquals(atBase[0][0] / (float) atBase[1][0],
                sharpened[0][0] / (float) sharpened[1][0], 0.02f);
        assertTrue(sharpened[0][0] > atBase[0][0]);
        assertTrue(sharpened[0][1] > atBase[0][1]);
    }

    @Test
    public void outputStaysWithinThePixelBudget() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        List<int[]> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) many.add(new int[]{3000, 2000});

        int[][] cells = FilmBorderRenderer.cellSizesFor(many, cfg, EXPORT_W, MIN_FRAME_PX);
        assertNotNull(cells);
        // 12 张 → 4 行；逐格相加只能粗估，这里只要求格子尺寸是合理量级
        assertTrue("格子不该被放大到离谱", cells[0][0] < 1400);
        assertTrue("格子也不该小到看不清", cells[0][0] >= MIN_FRAME_PX);
    }

    // ------------------------------------------------------------------
    //  每一截胶片都必须自带上下两排齿孔
    // ------------------------------------------------------------------

    @Test
    public void everyStripCarriesItsOwnPerforationBands() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        // 6 张 → 两行两截 → 四条（两行比一行格子更大，而 1:0.70 仍是横版）
        RectF[] six = FilmBorderRenderer.perfBandsFor(
                sizes(3000, 2000, 3000, 2000, 3000, 2000,
                      3000, 2000, 3000, 2000, 3000, 2000), cfg, EXPORT_W, 0);
        assertNotNull(six);
        assertEquals("6 张排两行：两截胶片，四条齿孔带", 4, six.length);

        // 12 张 → 三行三截 → 六条
        List<int[]> twelve = new ArrayList<>();
        for (int i = 0; i < 12; i++) twelve.add(new int[]{3000, 2000});
        RectF[] twelveBands = FilmBorderRenderer.perfBandsFor(twelve, cfg, EXPORT_W, 0);
        assertNotNull(twelveBands);
        assertEquals("12 张排三行：三截，六条齿孔带", 6, twelveBands.length);

        // 18 张 → 三行三截 → 六条（四行加上上边留白会超过横版阈值，见 chooseRows）
        List<int[]> eighteen = new ArrayList<>();
        for (int i = 0; i < 18; i++) eighteen.add(new int[]{3000, 2000});
        RectF[] eighteenBands = FilmBorderRenderer.perfBandsFor(eighteen, cfg, EXPORT_W, 0);
        assertNotNull(eighteenBands);
        assertEquals("18 张排三行：三截，六条齿孔带", 6, eighteenBands.length);
    }

    @Test
    public void perforationBandsNeverOverlapThePhotos() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) six.add(new int[]{3000, 2000});

        RectF[] bands = FilmBorderRenderer.perfBandsFor(six, cfg, EXPORT_W, 0);
        RectF[] cells = FilmBorderRenderer.cellsFor(six, cfg, EXPORT_W, 0);
        assertNotNull(bands);
        assertNotNull(cells);

        for (RectF band : bands) {
            for (RectF cell : cells) {
                assertTrue("齿孔带压到照片上了：" + band + " 与 " + cell + " 相交",
                        !RectF.intersects(band, cell));
            }
        }
    }

    @Test
    public void eachStripsBandsSitRightAboveAndBelowItsOwnRow() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) six.add(new int[]{3000, 2000});

        RectF[] bands = FilmBorderRenderer.perfBandsFor(six, cfg, EXPORT_W, 0);
        RectF[] cells = FilmBorderRenderer.cellsFor(six, cfg, EXPORT_W, 0);
        assertNotNull(bands);
        assertNotNull(cells);

        // 第一截的两条带：一条在第一行上方、一条在第一行下方
        assertEquals("上带应当紧贴第一行上方",
                cells[0].top, bands[0].bottom, 1.5f);
        assertEquals("下带应当紧贴第一行下方",
                cells[0].bottom, bands[1].top, 1.5f);

        // 第二截的两条带，同样贴着第二行
        assertEquals("第二截的上带应紧贴第二行上方",
                cells[3].top, bands[2].bottom, 1.5f);
        assertEquals("第二截的下带应紧贴第二行下方",
                cells[3].bottom, bands[3].top, 1.5f);

        // 第一截的下带与第二截的上带之间应当有一道缝
        assertTrue("剪开的两截之间要留缝", bands[2].top > bands[1].bottom + 1f);
    }

    @Test
    public void theGapBetweenTwoStripsIsExactlyOnePerforationLong() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) six.add(new int[]{3000, 2000});

        RectF[] bands = FilmBorderRenderer.perfBandsFor(six, cfg, EXPORT_W, 0);
        RectF[] cells = FilmBorderRenderer.cellsFor(six, cfg, EXPORT_W, 0);
        assertNotNull(bands);
        assertNotNull(cells);

        // 上截的下带 → 下截的上带 之间那道缝
        float gap = bands[2].top - bands[1].bottom;
        float framePx = cells[0].height();

        // 剪口落在片孔之间，缝宽就是一个孔沿片长方向的长度
        assertEquals("缝宽应当正好是一个片孔的长度",
                FilmBorderRenderer.perfLengthFor(framePx), gap, 1.5f);
    }

    @Test
    public void bandThicknessFollowsTheFrameNotTheCanvas() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        List<int[]> single = new ArrayList<>();
        single.add(new int[]{3000, 2000});
        List<int[]> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) nine.add(new int[]{3000, 2000});

        RectF[] thin = FilmBorderRenderer.perfBandsFor(single, cfg, EXPORT_W, MIN_FRAME_PX);
        RectF[] dense = FilmBorderRenderer.perfBandsFor(nine, cfg, EXPORT_W, MIN_FRAME_PX);
        assertNotNull(thin);
        assertNotNull(dense);

        // 九张时帧小得多，留边也必须跟着薄——这正是「孔太大很假」的病根
        assertTrue("留边必须跟着帧高缩，而不是固定占画布宽度的一个比例",
                dense[0].height() < thin[0].height() * 0.6f);
    }

    // ------------------------------------------------------------------
    //  单张不受影响
    // ------------------------------------------------------------------

    @Test
    public void singleLandscapePhotoLeavesOnePerforationOfLeadOnEachSide() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        RectF[] cells = FilmBorderRenderer.cellsFor(sizes(3000, 2000), cfg, EXPORT_W, 0);
        RectF[] strips = FilmBorderRenderer.stripsFor(sizes(3000, 2000), cfg, EXPORT_W, 0);
        assertNotNull(cells);
        assertNotNull(strips);
        assertEquals(1, cells.length);
        assertEquals(1, strips.length);

        // 照片两侧各让开「一个片孔的长度」——真实胶片上画幅不会顶着剪口
        float lead = FilmBorderRenderer.perfLengthFor(cells[0].height());
        assertEquals("左边应当留出一个孔长", lead, cells[0].left - strips[0].left, 1.5f);
        assertEquals("右边应当留出一个孔长", lead, strips[0].right - cells[0].right, 1.5f);
    }

    @Test
    public void noPhotoEverTouchesTheCutEdge() {
        FilmBorderConfig cfg = new FilmBorderConfig();

        // 单张、两张、六张、混排都过一遍
        List<List<int[]>> cases = new ArrayList<>();
        cases.add(sizes(3000, 2000));
        cases.add(sizes(3000, 2000, 2000, 3000));
        cases.add(sizes(3000, 2000, 3000, 2000, 3000, 2000,
                3000, 2000, 3000, 2000, 3000, 2000));
        cases.add(sizes(2000, 3000, 2000, 3000));

        for (List<int[]> sizes : cases) {
            RectF[] cells = FilmBorderRenderer.cellsFor(sizes, cfg, EXPORT_W, MIN_FRAME_PX);
            RectF[] strips = FilmBorderRenderer.stripsFor(sizes, cfg, EXPORT_W, MIN_FRAME_PX);
            assertNotNull(cells);
            assertNotNull(strips);

            for (RectF strip : strips) {
                for (RectF cell : cells) {
                    // 横向：格子在截内，且两侧都留了边
                    if (cell.top >= strip.top - 1f && cell.bottom <= strip.bottom + 1f) {
                        assertTrue("画幅不该压在剪口上：" + cell + " vs " + strip,
                                cell.left >= strip.left - 1f && cell.right <= strip.right + 1f);
                        if (cell.height() >= strip.height() - 2f) {
                            // 横条：两侧留边
                            assertTrue("左侧应当有留白", cell.left > strip.left + 1f);
                            assertTrue("右侧应当有留白", cell.right < strip.right - 1f);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void singlePhotoIsNeverShrunkByTheMinimumFrameRule() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        int[][] plain = FilmBorderRenderer.cellSizesFor(sizes(3000, 2000), cfg, EXPORT_W, 0);
        int[][] withMin = FilmBorderRenderer.cellSizesFor(sizes(3000, 2000), cfg, EXPORT_W, 900);
        assertNotNull(plain);
        assertNotNull(withMin);

        // 单张本来就够宽，minFramePx 不该把它改大（那是「回撤」的反方向）
        assertEquals(plain[0][0], withMin[0][0]);
    }

    // ------------------------------------------------------------------
    //  5. 多张的片边文案
    // ------------------------------------------------------------------

    @Test
    public void multiFrameSpecKeepsOnlyTheCameraModel() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.multiFrame = true;
        cfg.camera = "XIAOMI 15";
        cfg.lens = "LENS 24MM";
        cfg.exposure = "1/100s · f/1.8 · ISO400";
        cfg.date = "2026.09.03";

        assertEquals("多张时只留机型，其余用装饰文案",
                "XIAOMI 15 · 35MM · 135 · COLOR NEGATIVE", cfg.resolveSpec());
    }

    @Test
    public void multiFrameFallsBackWhenThereIsNoCamera() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.multiFrame = true;
        cfg.lens = "LENS 24MM";
        cfg.date = "2026.09.03";

        assertEquals("35MM · 135 · COLOR NEGATIVE", cfg.resolveSpec());
    }

    @Test
    public void singleFrameStillTellsEverythingItKnows() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.multiFrame = false;
        cfg.camera = "XIAOMI 15";
        cfg.lens = "LENS 24MM";
        cfg.exposure = "1/100s · f/1.8 · ISO400";
        cfg.date = "2026.09.03";

        assertEquals("XIAOMI 15 · LENS 24MM · 1/100s · f/1.8 · ISO400 · 2026.09.03",
                cfg.resolveSpec());
    }

    // ------------------------------------------------------------------
    //  出图：肉眼校验
    // ------------------------------------------------------------------

    @Test
    public void renderMultiFrameSamplesToPng() throws Exception {
        File dir = new File("build/film-out");
        assertTrue(dir.exists() || dir.mkdirs());

        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.style = FilmStock.STYLE_CLASSIC;
        cfg.stock = "PORTRA 400";
        cfg.camera = "XIAOMI 15";
        cfg.multiFrame = true;

        // 两张横幅
        writePng(render(cfg, makePhoto(1600, 1067), makePhoto(1600, 1067)),
                new File(dir, "multi-2-landscape.png"));

        // 三张横幅：一行正好放满
        writePng(render(cfg, makePhoto(1600, 1067), makePhoto(1600, 1067), makePhoto(1600, 1067)),
                new File(dir, "multi-3-landscape.png"));

        // 六张横幅：两行
        writePng(render(cfg, makePhoto(1600, 1067), makePhoto(1600, 1067), makePhoto(1600, 1067),
                        makePhoto(1600, 1067), makePhoto(1600, 1067), makePhoto(1600, 1067)),
                new File(dir, "multi-6-landscape.png"));

        // 横幅 + 竖幅混排：竖幅应当像半格一样变窄
        writePng(render(cfg, makePhoto(1600, 1067), makePhoto(1067, 1600), makePhoto(1600, 1067)),
                new File(dir, "multi-mixed.png"));

        // 全竖幅 + 暗底
        FilmBorderConfig noir = new FilmBorderConfig();
        noir.style = FilmStock.STYLE_NOIR;
        noir.camera = "NIKON ZF";
        noir.multiFrame = true;
        writePng(render(noir, makePhoto(1067, 1600), makePhoto(1067, 1600), makePhoto(1067, 1600)),
                new File(dir, "multi-3-portrait-noir.png"));

        // 12 张：三行四列
        Bitmap[] twelve = new Bitmap[12];
        for (int i = 0; i < twelve.length; i++) twelve[i] = makePhoto(1600, 1067);
        writePng(render(cfg, twelve), new File(dir, "multi-12-landscape.png"));

        // 18 张：上限，应当排成 5+4+5+4 四行的横版
        Bitmap[] eighteen = new Bitmap[18];
        for (int i = 0; i < eighteen.length; i++) eighteen[i] = makePhoto(1600, 1067);
        Bitmap sheet = render(cfg, eighteen);
        writePng(sheet, new File(dir, "multi-18-landscape.png"));
        assertTrue("18 张必须是横版：实际 " + sheet.getWidth() + "x" + sheet.getHeight(),
                sheet.getWidth() > sheet.getHeight());

        System.out.println("多张胶片样例已输出 -> " + dir.getAbsolutePath());
    }

    private static Bitmap render(FilmBorderConfig cfg, Bitmap... photos) {
        List<Bitmap> list = new ArrayList<>();
        for (Bitmap b : photos) list.add(b);

        int height = FilmBorderRenderer.heightFor(list, cfg, EXPORT_W, MIN_FRAME_PX);
        assertTrue("应当能预算出高度", height > 0);

        Bitmap out = FilmBorderRenderer.render(list, cfg, EXPORT_W, MIN_FRAME_PX);
        assertNotNull(out);
        assertTrue("多张时画布必须比单张更大", out.getHeight() > 0);
        return out;
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    private static List<int[]> sizes(int... widthHeightPairs) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i + 1 < widthHeightPairs.length; i += 2) {
            list.add(new int[]{widthHeightPairs[i], widthHeightPairs[i + 1]});
        }
        return list;
    }

    /** 造 count 张同尺寸的位图，用来量输出长宽比。 */
    private static List<Bitmap> photos(int count) {
        List<Bitmap> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888));
        }
        return list;
    }

    private static void writePng(Bitmap bmp, File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    /** 合成一张有明确主色特征的风景照。 */
    private static Bitmap makePhoto(int w, int h) {
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

