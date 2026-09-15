package com.example.photopalettepro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.example.photopalettepro.film.FilmBase;
import com.example.photopalettepro.film.FilmStock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 胶片边框渲染入口：把一张或多张照片放进胶片里。
 *
 * <p>画面结构：
 * <ol>
 *   <li><b>片基</b> —— 预设底色 + 多尺度不匀 + 边缘光衰减（见 {@link FilmBase}）；</li>
 *   <li><b>照片</b> —— 按原始比例完整嵌入，<b>不裁切、不拉伸</b>；</li>
 *   <li><b>磨损</b> —— 片基上的灰尘与划痕，会把所有格子的区域都裁掉；</li>
 *   <li><b>齿孔</b> —— 每一截胶片的两条长边各一排；</li>
 *   <li><b>片边文字</b> —— 胶片型号 / 机身 · 镜头 · 曝光 · 日期 / 装饰帧号。</li>
 * </ol>
 *
 * <h3>齿孔是按真实规格算出来的，不是画的花纹</h3>
 * <p>135 胶片的 KS 片孔是 <b>1.98 × 2.79mm</b>、节距 <b>4.75mm</b>，
 * 画幅 36×24mm —— 也就是<b>一格 8 个孔</b>。这些数一比就定死了整个齿孔层：
 * <pre>
 *   节距 = 4.75/24 × 帧高 ≈ 0.198 × 帧高
 *   孔长 = 1.98/4.75 × 节距 ≈ 0.417 × 节距
 *   孔宽 = 2.79/1.98 × 孔长 ≈ 1.409 × 孔长
 * </pre>
 * 于是<b>孔的大小自动跟着照片缩放</b>：三格一行时每格 8 个孔，
 * 半格（18×24mm）自然只摊到 4 个孔。片基留边同样是真实比例 ——
 * 135 的片宽 35mm、画幅高 24mm，所以上下各留 (35−24)/2 = 5.5mm ≈ 0.229 × 帧高。
 *
 * <h3>多张：每一行都是一截独立的胶片</h3>
 * <p>多张不是「一整块底板上挖几排洞」，而是<b>一条胶卷剪成几截摊在一起</b>：
 * 每一行都有自己的上下齿孔带，截与截之间留一道缝、缝两侧压一道裁切边。
 *
 * <p>行内排布走<b>半格胶片</b>的路子：所有格子等高（这个高度由最宽的一行反推），
 * 每格宽度 = 帧高 × 该照片宽高比，所以竖幅自然变窄 ——
 * 一张 3:4 竖幅的宽度恰好是 3:2 横幅的一半（18mm 对 36mm）。
 *
 * <p>只有一张照片时保持原有行为：横幅齿孔在上下、竖幅齿孔在左右（竖条胶片）。
 */
public final class FilmBorderRenderer {

    /** 底部片边文字区高度（相对输出宽度） */
    private static final float TEXT_BLOCK_RATIO = 0.150f;

    /** 单张输出的像素上限，防止全景 / 超长竖幅把内存吃穿 */
    private static final long MAX_PIXELS = 26_000_000L;

    /** 片基纹理的种子：固定值，保证同一批照片每次渲染的颗粒完全一致 */
    private static final long FILM_SEED = 20260903L;

    /**
     * 片基的不透明度（0~255）。**刻意不是 255。**
     *
     * <p>照片外面那圈片基是半透明的：台面会从它下面透上来一层，
     * 读起来才像一片塑料底片，而不是一块刷了色的纸板。齿孔处更是完全镂空，
     * 台面原样透出——孔和片基的明度差就是这么来的。
     *
     * <p>0.84 是试出来的：再透一点，浅色片基就会糊进台面；
     * 再实一点，又回到"和背景同色、只剩描边"的老问题。
     */
    private static final int FILM_ALPHA = Math.round(255 * 0.84f);

    /**
     * 一行最多几格。
     *
     * <p>只是<b>安全上限</b>，不是实际列数——列数由「整张稿子要是横版」这个目标反推
     * （见 {@link #chooseRows}）。上限留着是为了兜住极端情况：比如 20 张排成一行，
     * 每格会小到看不出是什么。
     */
    public static final int MAX_COLUMNS = 8;

    /**
     * 整张稿子的目标形态：横着的长方形。
     *
     * <p>高度 / 宽度不超过这个值才算「横版」。取 0.85 而不是 1.0，
     * 是因为 1:0.98 那种近方形读起来并不像横版，用户要的是明显横过来的一张。
     */
    private static final float LANDSCAPE_TARGET_RATIO = 0.85f;

    /** 各行宽度最多差多少倍。差太多（3+1 这种）宁可少排一行。 */
    private static final float MAX_ROW_IMBALANCE = 1.6f;

    // ---- 135 胶片的真实规格（决定了整个齿孔层）----

    /** 片孔节距 / 画幅高 = 4.75mm / 24mm */
    private static final float PERF_PITCH_RATIO = 4.75f / 24f;
    /** 片孔沿片长方向的长度 / 节距 = 1.98mm / 4.75mm */
    private static final float PERF_LENGTH_RATIO = 1.98f / 4.75f;
    /** 片孔横跨片宽方向的长度 / 沿片长方向的长度 = 2.79mm / 1.98mm */
    private static final float PERF_CROSS_RATIO = 2.79f / 1.98f;
    /** 片基留边 / 画幅高 = ((35 − 24) / 2)mm / 24mm —— 也就是「标准边框」档 */
    private static final float REBATE_RATIO = 5.5f / 24f;

    /** 同一截胶片里，帧与帧之间的空档（相对帧高）。真实 135 约占 5~8% */
    private static final float FRAME_GAP_RATIO = 0.08f;

    /**
     * 一个片孔沿片长方向的长度（相对帧高）＝ 节距 4.75/24 × 孔长占比 1.98/4.75 ≈ 0.0825。
     *
     * <p>这是这套版式里的「一个小单位」，三处都用它：截与截之间的缝、
     * 以及每一截两端各留的边。剪口本来就落在片孔之间，一个孔长是最自然的留白。
     */
    private static final float ONE_PERF_RATIO = PERF_PITCH_RATIO * PERF_LENGTH_RATIO;

    /** 截与截之间的缝（相对帧高）：一个片孔长。 */
    private static final float STRIP_GAP_RATIO = ONE_PERF_RATIO;

    /**
     * 每一截两端各留的边（相对帧高）：也是一个片孔长。
     *
     * <p>真实胶片上画幅不会顶着剪口——前面总还有一段片基。
     * 没有这段留白，照片就和切边齐平，看着像"把照片裁成了胶片形状"，
     * 而不是"一条胶片里嵌着照片"。竖条胶片的两端是上下，同理。
     */
    private static final float STRIP_LEAD_RATIO = ONE_PERF_RATIO;

    private FilmBorderRenderer() {
    }

    // ====================================================================
    //  对外接口
    // ====================================================================

    /** 单张渲染（保持原有签名，便于旧调用点与测试继续使用）。 */
    public static Bitmap render(Bitmap photo, FilmBorderConfig cfg, int outW) {
        return render(photo, cfg, outW, 0);
    }

    /** 单张渲染；{@code minFramePx > 0} 时保证照片宽度不低于它。 */
    public static Bitmap render(Bitmap photo, FilmBorderConfig cfg, int outW, int minFramePx) {
        if (photo == null || photo.isRecycled()) return null;
        return render(Collections.singletonList(photo), cfg, outW, minFramePx);
    }

    /**
     * 多张渲染：一行排不下就换行，每一行都是一截带齿孔的独立胶片。
     *
     * @param photos     按用户选择的顺序排列；null / 已回收的项会被跳过
     * @param cfg        边框配置
     * @param outW       期望的输出宽度（仅在需要保清晰度时才会被放大）
     * @param minFramePx 每一格的最小像素宽度；&le;0 表示不保证
     * @return 渲染结果；没有可用照片时返回 {@code null}
     */
    public static Bitmap render(List<Bitmap> photos, FilmBorderConfig cfg, int outW, int minFramePx) {
        if (outW <= 0) return null;
        if (cfg == null) cfg = new FilmBorderConfig();

        List<Bitmap> valid = filterValid(photos);
        if (valid.isEmpty()) return null;

        Plan plan = plan(aspectsOf(valid), cfg);
        if (plan == null) return null;

        Layout l = materialize(plan, resolveOutW(plan, outW, minFramePx));
        if (l == null) return null;

        Bitmap out = Bitmap.createBitmap(l.outW, l.outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        // 一、台面：胶片摆在上面的那一层，和片基拉开明度差
        FilmBase.paintBackdrop(canvas, l.outW, l.outH, l.stock, FILM_SEED);

        // 二、每一截胶片：半透明片基，齿孔真正镂空，台面从孔里透上来。
        //     每截用各自的 seed——剪开的几截本来就不是同一段，纹理不该一模一样。
        for (int i = 0; i < l.strips.length; i++) {
            FilmBase.paintStrip(canvas, l.stripShapes[i], l.strips[i], l.stock,
                    FILM_SEED + i * 7919L, FILM_ALPHA);
        }

        // 三、照片
        for (int i = 0; i < valid.size() && i < l.cells.length; i++) {
            drawPhoto(canvas, valid.get(i), l.cells[i], l.stock);
        }

        // 四、片基表面的灰尘与划痕：压在齿孔与文字之下，
        //     而且只落在片基上——照片是用户的内容，台面也不该有胶片的划痕
        FilmBase.paintWear(canvas, l.stripShapes, l.cells, l.bounds, l.stock, FILM_SEED);

        // 五、孔缘、裁切边，最后是片边文字
        if (cfg.sprocketHoles) {
            drawHoleEdges(canvas, l);
        }
        drawCutEdges(canvas, l);
        if (cfg.showCaption) {
            drawCaption(canvas, l, cfg);
        }

        return out;
    }

    /** 预算输出高度——供调用方在渲染前判断内存是否吃得消。 */
    public static int heightFor(Bitmap photo, FilmBorderConfig cfg, int outW) {
        if (photo == null || photo.isRecycled()) return 0;
        return heightFor(Collections.singletonList(photo), cfg, outW, 0);
    }

    /** 预算多张排布的输出高度。 */
    public static int heightFor(List<Bitmap> photos, FilmBorderConfig cfg, int outW, int minFramePx) {
        if (outW <= 0) return 0;
        if (cfg == null) cfg = new FilmBorderConfig();
        List<Bitmap> valid = filterValid(photos);
        if (valid.isEmpty()) return 0;
        Plan plan = plan(aspectsOf(valid), cfg);
        if (plan == null) return 0;
        Layout l = materialize(plan, resolveOutW(plan, outW, minFramePx));
        return l == null ? 0 : l.outH;
    }

    /**
     * 预算每一格在成品里占的像素尺寸。
     *
     * <p>调用方拿它决定「按多大解码每张照片」——解码太小照片会糊，
     * 一律按原图解码则十几张就是几百 MB。这里给的是精确的格子尺寸，
     * 所以两头都不会浪费。
     *
     * @param sizes 每张照片的 {@code {宽, 高}}（只要尺寸，不需要真的解码）
     * @return 与 sizes 等长的 {@code {格宽, 格高}}；参数非法时返回 {@code null}
     */
    public static int[][] cellSizesFor(List<int[]> sizes, FilmBorderConfig cfg, int outW, int minFramePx) {
        Layout l = layoutFor(sizes, cfg, outW, minFramePx);
        if (l == null) return null;

        int[][] result = new int[l.cells.length][2];
        for (int i = 0; i < l.cells.length; i++) {
            result[i][0] = Math.round(l.cells[i].width());
            result[i][1] = Math.round(l.cells[i].height());
        }
        return result;
    }

    /**
     * 每一条齿孔带的位置（像素）。
     *
     * <p>单独暴露出来，是为了能直接断言「每一截胶片都有自己的上下两排孔、
     * 而且不会压到照片上」——这正是多张合成最容易画错的地方，
     * 只靠看渲染结果很容易漏（尺寸、比例全对，孔却跑到了别的行上）。
     *
     * @return 每条带一个矩形；参数非法时返回 {@code null}
     */
    public static RectF[] perfBandsFor(List<int[]> sizes, FilmBorderConfig cfg, int outW, int minFramePx) {
        Layout l = layoutFor(sizes, cfg, outW, minFramePx);
        if (l == null) return null;
        RectF[] copy = new RectF[l.perfBands.length];
        for (int i = 0; i < copy.length; i++) copy[i] = new RectF(l.perfBands[i]);
        return copy;
    }

    /** 每一格照片的位置（像素）。 */
    public static RectF[] cellsFor(List<int[]> sizes, FilmBorderConfig cfg, int outW, int minFramePx) {
        Layout l = layoutFor(sizes, cfg, outW, minFramePx);
        if (l == null) return null;
        RectF[] copy = new RectF[l.cells.length];
        for (int i = 0; i < copy.length; i++) copy[i] = new RectF(l.cells[i]);
        return copy;
    }

    /**
     * 每一截胶片的完整矩形（像素）。
     *
     * <p>单独暴露是为了能直接断言「照片不贴着剪口、两端各留了一个片孔长度的边」——
     * 这条性质只靠看渲染图很容易漏，而它决定了整张稿子像不像胶片。
     */
    public static RectF[] stripsFor(List<int[]> sizes, FilmBorderConfig cfg, int outW, int minFramePx) {
        Layout l = layoutFor(sizes, cfg, outW, minFramePx);
        if (l == null) return null;
        RectF[] copy = new RectF[l.strips.length];
        for (int i = 0; i < copy.length; i++) copy[i] = new RectF(l.strips[i]);
        return copy;
    }

    /** 按尺寸算出完整版面；上面几个「只读几何」的入口都走这里。 */
    private static Layout layoutFor(List<int[]> sizes, FilmBorderConfig cfg, int outW, int minFramePx) {
        if (sizes == null || sizes.isEmpty() || outW <= 0) return null;
        if (cfg == null) cfg = new FilmBorderConfig();

        float[] aspects = aspectsOfSizes(sizes);
        if (aspects == null || aspects.length == 0) return null;

        Plan plan = plan(aspects, cfg);
        if (plan == null) return null;
        return materialize(plan, resolveOutW(plan, outW, minFramePx));
    }

    /**
     * 分行结果：第 r 行包含哪些照片（下标，按原始顺序）。
     *
     * <p>单独暴露出来，是因为「怎么分行」是这套版式最关键的性质——
     * 它不能只靠肉眼看渲染结果来判断。这里用的就是渲染时的同一套决策
     * （见 {@link #chooseRows}），不是另写一份近似。
     *
     * @return 行数组；参数非法时返回 {@code null}
     */
    public static int[][] rowsFor(List<int[]> sizes) {
        float[] aspects = aspectsOfSizes(sizes);
        if (aspects == null || aspects.length == 0) return null;

        if (aspects.length == 1) {
            return new int[][]{{0}};
        }

        float edgeRatio = margins(FilmBorderConfig.WIDTH_NORMAL)[0];
        float bandFactor = margins(FilmBorderConfig.WIDTH_NORMAL)[1];
        float content = 1f - edgeRatio * 2f;

        int rows = chooseRows(aspects, content, bandFactor, true, edgeRatio);
        List<List<Integer>> balanced = balanceRows(aspects, rows);

        int[][] result = new int[balanced.size()][];
        for (int i = 0; i < balanced.size(); i++) {
            List<Integer> row = balanced.get(i);
            result[i] = new int[row.size()];
            for (int j = 0; j < row.size(); j++) result[i][j] = row.get(j);
        }
        return result;
    }

    // ====================================================================
    //  齿孔规格（对外暴露，便于直接核对真实尺寸）
    // ====================================================================

    /**
     * 片孔节距（像素）。
     *
     * <p>齿孔层的一切都从这里推出来：孔长 = 节距 × {@code 1.98/4.75}，
     * 孔宽 = 孔长 × {@code 2.79/1.98}。
     *
     * @param framePx 帧的「横跨片宽」尺寸：横向走片时是帧高，纵向走片时是帧宽
     */
    public static float perfPitchFor(float framePx) {
        return PERF_PITCH_RATIO * framePx;
    }

    /** 片孔沿片长方向的长度（像素）。 */
    public static float perfLengthFor(float framePx) {
        return perfPitchFor(framePx) * PERF_LENGTH_RATIO;
    }

    /** 片孔横跨片宽方向的长度（像素）。 */
    public static float perfCrossFor(float framePx) {
        return perfLengthFor(framePx) * PERF_CROSS_RATIO;
    }

    /** 从「只读尺寸」里取宽高比，跳过无效项。 */
    private static float[] aspectsOfSizes(List<int[]> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        List<Float> aspects = new ArrayList<>(sizes.size());
        for (int[] size : sizes) {
            if (size == null || size.length < 2 || size[0] <= 0 || size[1] <= 0) continue;
            aspects.add(size[0] / (float) size[1]);
        }
        if (aspects.isEmpty()) return null;

        float[] flat = new float[aspects.size()];
        for (int i = 0; i < flat.length; i++) flat[i] = aspects.get(i);
        return flat;
    }

    // ====================================================================
    //  版面测算
    // ====================================================================

    /** 一次渲染的全部几何量，先算清楚再落笔。 */
    private static final class Layout {
        int outW;
        int outH;
        FilmStock stock;
        /** 每一格（照片）的目标矩形 */
        RectF[] cells;
        /** 每一截胶片的完整矩形（上下齿孔带 + 中间画幅） */
        RectF[] strips;
        /** 每一截胶片的外形：矩形挖掉齿孔，用来裁剪片基 */
        Path[] stripShapes;
        /** 每条齿孔带上的孔（下标与 {@link #perfBands} 对应） */
        RectF[][] holes;
        /** 每一截胶片上的齿孔带（每截两条长边各一条） */
        RectF[] perfBands;
        /** 截与截之间的裁切边（只在真的剪开的地方画） */
        RectF[] cutEdges = new RectF[0];
        /** 齿孔带的厚度（像素） */
        float bandPx;
        /** 帧的「横跨片宽」尺寸（像素）：横向走片时是帧高，纵向走片时是帧宽 */
        float framePx;
        /** 所有格子的并集，片边文字相对它定位 */
        RectF bounds = new RectF();
        float sideMargin;
        /** 最后一截下方到片边文字之间的留白（像素） */
        float bottomMargin;
        float textH;
    }

    /**
     * 与输出宽度无关的版面比例。
     *
     * <p>先算出全部比例，再决定输出宽度（可能要为了保清晰度放大、为了保内存回撤），
     * 最后一次性乘成像素——这样「放大画布」不会把任何一处的相对关系搞乱。
     */
    private static final class Plan {
        FilmStock stock;
        float sideRatio;
        float bandRatio;
        /** 帧的「横跨片宽」尺寸 / outW：齿孔的节距就是按它推出来的 */
        float frameRatio;
        /** 最后一截下方到片边文字之间的留白 / outW */
        float bottomRatio;
        float textRatio = TEXT_BLOCK_RATIO;
        /** 每一格 {左, 上, 宽, 高} */
        float[][] cellRatios;
        /** 每一条齿孔带 {左, 上, 右, 下} */
        float[][] bandRects;
        /** 每一截胶片的完整范围 {左, 上, 右, 下} */
        float[][] stripRects = new float[0][];
        /** 裁切边 {左, 上, 右, 下}，只有上下两条会用到 */
        float[][] cutRects = new float[0][];
        float minCellWRatio;
        float outHRatio;
    }

    /**
     * 边距档位：{胶片两端的留白（相对 outW）, 上下留边（相对帧高）}。
     *
     * <p>上下留边用<b>相对帧高</b>而不是相对画布宽度，是这次修正的关键：
     * 孔的大小由帧高推出，留边也必须跟着帧高走，否则照片一小、
     * 留边还那么厚、孔还那么大，一眼就是假的。
     * 中间那档就是 135 的真实比例 ((35−24)/2) / 24。
     */
    private static float[] margins(String width) {
        if (FilmBorderConfig.WIDTH_NARROW.equals(width)) {
            return new float[]{0.042f, 0.185f};
        }
        if (FilmBorderConfig.WIDTH_WIDE.equals(width)) {
            return new float[]{0.076f, 0.300f};
        }
        return new float[]{0.058f, REBATE_RATIO};
    }

    private static Plan plan(float[] aspects, FilmBorderConfig cfg) {
        if (aspects == null || aspects.length == 0) return null;

        Plan p = new Plan();
        p.stock = cfg.stockPreset();

        float[] m = margins(cfg.width);
        if (aspects.length == 1) {
            return planSingle(p, aspects[0], m[0], m[1], cfg.sprocketHoles, cfg.showCaption);
        }
        return planMulti(p, aspects, m[0], m[1], cfg.sprocketHoles, cfg.showCaption);
    }

    /**
     * 片基留边：开着齿孔时用真实片基比例（相对帧高），
     * 关掉齿孔则收回「胶片两端」那一档窄留白——
     * 没有孔还留一圈厚片基，看起来就是莫名其妙的空白。
     */
    private static float bandRatio(boolean holes, float bandFactor, float frame, float edgeRatio) {
        return holes ? bandFactor * frame : edgeRatio;
    }

    /** 单张：保留原有的方向自适应——竖幅走左右齿孔的竖条胶片。 */
    private static Plan planSingle(Plan p, float aspect, float edgeRatio, float bandFactor,
                                    boolean holes, boolean caption) {
        boolean landscape = aspect >= 1f;

        if (landscape) {
            float content = 1f - edgeRatio * 2f;
            if (content <= 0.05f) return null;

            // 照片两侧各让出一个片孔长度的边：真实胶片上画幅不会顶着剪口。
            // 让出之后照片略小一点，胶片仍是整幅内容宽度——「留白」加在画幅与剪口之间。
            float frame = content / (aspect + 2f * STRIP_LEAD_RATIO);   // 照片高 = 横跨片宽的尺寸
            float lead = STRIP_LEAD_RATIO * frame;
            float photoW = frame * aspect;
            float band = bandRatio(holes, bandFactor, frame, edgeRatio);
            // 上方也要留白：胶片不能顶着画布上沿，否则台面在读图时是不存在的
            float top = edgeRatio;
            float left = edgeRatio;
            float right = edgeRatio + content;

            p.sideRatio = edgeRatio;
            p.frameRatio = frame;
            p.bandRatio = band;
            p.bottomRatio = band;
            p.cellRatios = new float[][]{{left + lead, top + band, photoW, frame}};
            p.bandRects = new float[][]{
                    {left, top, right, top + band},
                    {left, top + band + frame, right, top + band + frame + band}};
            p.stripRects = new float[][]{{left, top, right, top + band + frame + band}};
            p.outHRatio = top + band + frame + band + (caption ? p.textRatio : top);
            p.minCellWRatio = photoW;
            return p;
        }

        // 竖幅：整条胶片竖过来走片，横跨片宽的是「帧宽」。
        //
        // 左右同样要留白。原来这里直接解成 frameW = 1/(1+2×留边系数)，
        // 整截正好占满画布宽度——胶片顶着左右边缘，台面在读图时等于不存在，
        // 又变回"贴在纸边上的一条"。横幅早就留了 edgeRatio，竖幅漏了。
        float content = 1f - 2f * edgeRatio;
        if (content <= 0.05f) return null;

        float band;
        float frameW;
        if (holes) {
            frameW = content / (1f + 2f * bandFactor);
            band = bandFactor * frameW;
        } else {
            band = edgeRatio;
            frameW = content - 2f * band;
        }
        if (frameW <= 0.05f) return null;

        float left = edgeRatio;
        float stripRight = edgeRatio + content;     // = left + band + frameW + band

        // 竖条胶片的两端是上下，同样各让出一个片孔长度的边。
        // 这里的「帧高」已经是横跨片宽的尺寸，所以一个孔长按它算。
        float lead = STRIP_LEAD_RATIO * frameW;
        float photoH = frameW / aspect;
        float frameH = photoH + 2f * lead;          // 整截的纵向长度
        float top = edgeRatio;
        float bottom = edgeRatio + frameH;

        p.sideRatio = band;
        p.frameRatio = frameW;
        p.bandRatio = band;
        // 竖条胶片上，最后一格下方到文字之间是「胶片两端」的那档留白，不是齿孔带
        p.bottomRatio = edgeRatio;
        // 画幅上下各让开 lead，整截才是 frameH
        p.cellRatios = new float[][]{{left + band, top + lead, frameW, photoH}};
        p.bandRects = new float[][]{
                {left, top, left + band, bottom},
                {left + band + frameW, top, stripRight, bottom}};
        p.stripRects = new float[][]{{left, top, stripRight, bottom}};
        p.outHRatio = top + frameH + (caption ? edgeRatio + p.textRatio : top);
        p.minCellWRatio = frameW;
        return p;
    }

    /**
     * 选行数——这套排布的核心决策。
     *
     * <p>目标：整张稿子排成<b>横着的长方形</b>，同时格子尽量大。
     *
     * <p>两个方向是矛盾的：行数越多，每行的格子越少、格子越大，但整张稿子越高。
     * 所以规则是：在「仍然是横版」且「各行宽度不至于差太多」的前提下，
     * 取<b>行数最多</b>的那个方案——它的格子最大。
     *
     * <p>18 张会因此排成 5+4+5+4 四行（宽高比约 1:0.82），
     * 而不是每行三格的六行（1:1.83，一张竖长条）。
     */
    private static int chooseRows(float[] aspects, float content, float bandFactor,
                                  boolean holes, float edgeRatio) {
        int n = aspects.length;

        // 兜底：一个方案都不过关时，退回「每行最多 MAX_COLUMNS 格」
        int fallback = Math.max(1, Math.min(n, (int) Math.ceil(n / (float) MAX_COLUMNS)));
        int best = fallback;
        float bestFrame = -1f;

        for (int k = 1; k <= n; k++) {
            List<List<Integer>> rows = balanceRows(aspects, k);
            if (rows.size() != k) continue;

            boolean tooWide = false;
            for (List<Integer> row : rows) {
                if (row.size() > MAX_COLUMNS) {
                    tooWide = true;
                    break;
                }
            }
            if (tooWide) continue;

            float frame = Float.MAX_VALUE;
            float maxSpan = 0f;
            float minSpan = Float.MAX_VALUE;
            for (List<Integer> row : rows) {
                float span = spanOf(row, aspects);
                if (span <= 0f) continue;
                frame = Math.min(frame, content / (span + 2f * STRIP_LEAD_RATIO));
                maxSpan = Math.max(maxSpan, span);
                minSpan = Math.min(minSpan, span);
            }
            if (frame == Float.MAX_VALUE || frame <= 0f || minSpan <= 0f) continue;

            // 条件一：整张稿子得是横版
            if (layoutHeightRatio(rows.size(), frame, bandFactor, holes, edgeRatio)
                    > LANDSCAPE_TARGET_RATIO) {
                continue;
            }

            // 条件二：各行宽度别差太多
            if (maxSpan / minSpan > MAX_ROW_IMBALANCE) continue;

            if (frame > bestFrame) {
                bestFrame = frame;
                best = k;
            }
        }
        return best;
    }

    /** 一行里所有照片的宽高比之和（含帧间空档）。 */
    private static float spanOf(List<Integer> row, float[] aspects) {
        float span = 0f;
        for (int idx : row) span += aspects[idx];
        return span + FRAME_GAP_RATIO * (row.size() - 1);
    }

    /** 按给定行数与帧高算出来的「高度 / 宽度」，用来判断是不是横版。 */
    private static float layoutHeightRatio(int rows, float frame, float bandFactor,
                                           boolean holes, float edgeRatio) {
        float band = bandRatio(holes, bandFactor, frame, edgeRatio);
        float stripHeight = frame + 2f * band;
        float stripGap = STRIP_GAP_RATIO * frame;
        // 上边留白也要算进来，否则选行数时会高估「横版」的程度，
        // 选完加上留白就超标了
        return edgeRatio
                + rows * stripHeight + (rows - 1) * stripGap
                + TEXT_BLOCK_RATIO;
    }

    /**
     * 多张：半格胶片式排布，每一行是一截独立的胶片。
     *
     * <p>横条永远是横的——多张照片堆在一起时不可能再竖过来走片，
     * 所以齿孔固定在每截的上下长边；竖幅照片靠「变窄」来适应，而不是靠旋转画布。
     */
    private static Plan planMulti(Plan p, float[] aspects, float edgeRatio, float bandFactor,
                                   boolean holes, boolean caption) {
        float content = 1f - edgeRatio * 2f;
        if (content <= 0.05f) return null;

        // 一、分几行：由「整张稿子要是横版」反推，行数越多格子越大
        int rows = chooseRows(aspects, content, bandFactor, holes, edgeRatio);

        // 二、均衡切分，让每行的总宽高比尽量接近（避免 3+3+1 这种失衡）
        List<List<Integer>> rowItems = balanceRows(aspects, rows);

        // 三、帧高由最宽的一行反推——那一行正好占满内容宽度。
        //    横幅的宽高比大，所以实际上「以横幅的高度为标准」，
        //    竖幅等高变窄，宽度自然落到半格附近。
        //
        //    注意分母多了 2×STRIP_LEAD_RATIO：每一截的两端各要留一个片孔长度的边，
        //    「占满内容宽度」的是整截（画幅 + 两端的边），不是画幅本身。
        float frame = Float.MAX_VALUE;
        for (List<Integer> row : rowItems) {
            float span = 0f;
            for (int idx : row) span += aspects[idx];
            span += FRAME_GAP_RATIO * (row.size() - 1);
            if (span <= 0f) continue;
            frame = Math.min(frame, content / (span + 2f * STRIP_LEAD_RATIO));
        }
        if (frame == Float.MAX_VALUE || frame <= 0f) return null;

        float band = bandRatio(holes, bandFactor, frame, edgeRatio);
        float frameGap = FRAME_GAP_RATIO * frame;
        float stripGap = STRIP_GAP_RATIO * frame;
        float lead = STRIP_LEAD_RATIO * frame;
        float stripHeight = frame + 2f * band;

        List<float[]> cells = new ArrayList<>(aspects.length);
        List<float[]> bands = new ArrayList<>();
        List<float[]> strips = new ArrayList<>();
        List<float[]> cuts = new ArrayList<>();
        float minCellW = Float.MAX_VALUE;
        // 上边留白：胶片不能顶着画布上沿。左右已经有 edgeRatio 的留白，
        // 上面不给的话台面在读图时等于不存在，胶片又变回「贴在纸边上的图」
        float y = edgeRatio;

        for (int r = 0; r < rowItems.size(); r++) {
            List<Integer> row = rowItems.get(r);

            float rowSpan = 0f;
            for (int idx : row) rowSpan += aspects[idx];
            float rowW = frame * rowSpan + frameGap * (row.size() - 1);   // 画幅部分
            float stripW = rowW + 2f * lead;                              // 整截：两端各留一个孔长
            // 每行居中；先记下这一截的左边界，下面画格子时 x 会一路往右走
            float rowLeft = edgeRatio + (content - stripW) / 2f;
            float x = rowLeft + lead;      // 画幅从留白之后开始

            // 每一截都有自己的上下齿孔带——这就是「剪成几截」的直观来源。
            // 齿孔带铺满整截（含两端的留白），孔才会一直排到切边。
            bands.add(new float[]{rowLeft, y, rowLeft + stripW, y + band});
            bands.add(new float[]{rowLeft, y + band + frame, rowLeft + stripW, y + band + frame + band});

            for (int idx : row) {
                float w = frame * aspects[idx];
                cells.add(new float[]{x, y + band, w, frame});
                minCellW = Math.min(minCellW, w);
                x += w + frameGap;
            }

            // 裁切边只画在真的剪开的地方，而且只画在这一截自己的宽度里——
            // 行宽不一时（比如 3+2），按内容宽度画会有一条悬在台面上的线。
            float stripBottom = y + stripHeight;
            strips.add(new float[]{rowLeft, y, rowLeft + stripW, stripBottom});

            if (r > 0) {
                cuts.add(new float[]{rowLeft, y, rowLeft + stripW, y});
            }
            if (r < rowItems.size() - 1) {
                cuts.add(new float[]{rowLeft, stripBottom, rowLeft + stripW, stripBottom});
            }

            y = stripBottom;
            if (r < rowItems.size() - 1) y += stripGap;
        }
        if (cells.isEmpty() || minCellW == Float.MAX_VALUE) return null;

        p.sideRatio = edgeRatio;
        p.frameRatio = frame;
        p.bandRatio = band;
        p.bottomRatio = band;
        p.cellRatios = cells.toArray(new float[0][]);
        p.bandRects = bands.toArray(new float[0][]);
        p.stripRects = strips.toArray(new float[0][]);
        p.cutRects = cuts.toArray(new float[0][]);
        p.minCellWRatio = minCellW;
        p.outHRatio = y + (caption ? p.textRatio : edgeRatio);
        return p;
    }

    /**
     * 把照片按顺序切成若干行，并让各行的「总宽高比」尽量接近。
     *
     * <p>简单按每行塞满会得到 3+3+1 这种结果——最后一行只有一格，
     * 整张片子头重脚轻。这里改成按剩余均值切：每行装到「再加一张就偏离均值」为止，
     * 于是 7 张会切成 4+3 而不是 3+3+1，横幅和竖幅混排时也会自动搭配。
     */
    private static List<List<Integer>> balanceRows(float[] aspects, int rows) {
        int n = aspects.length;
        List<List<Integer>> result = new ArrayList<>(rows);

        float remaining = 0f;
        for (float a : aspects) remaining += a;

        int index = 0;
        for (int r = 0; r < rows; r++) {
            int rowsLeft = rows - r;
            List<Integer> row = new ArrayList<>();

            if (rowsLeft == 1) {
                // 最后一行包圆，不再切
                while (index < n) row.add(index++);
                result.add(row);
                break;
            }

            float target = remaining / rowsLeft;
            float acc = 0f;
            while (index < n) {
                // 剩下的张数必须够后面每一行至少分到一张
                if (n - index <= rowsLeft) break;
                float next = aspects[index];
                // 已经装了至少一张、且再加一张会更偏离均值，就收手
                if (!row.isEmpty() && acc + next / 2f > target) break;
                row.add(index);
                acc += next;
                index++;
            }
            if (row.isEmpty()) {   // 兜底：无论如何一行至少要有一张
                row.add(index);
                acc += aspects[index];
                index++;
            }

            remaining -= acc;
            result.add(row);
        }
        return result;
    }

    /**
     * 决定最终输出宽度。
     *
     * <p>两个方向相反的诉求：
     * <ol>
     *   <li><b>保清晰</b>——格子排得越密每格越小，所以当最小的一格窄于
     *       {@code minFramePx} 时，把整张画布按比例放大，而不是让照片糊掉；</li>
     *   <li><b>保内存</b>——极端长宽比下再按像素上限回撤。</li>
     * </ol>
     */
    private static int resolveOutW(Plan plan, int requestedW, int minFramePx) {
        int outW = Math.max(64, requestedW);

        if (minFramePx > 0 && plan.minCellWRatio > 0f) {
            float minCellW = plan.minCellWRatio * outW;
            if (minCellW < minFramePx) {
                outW = (int) Math.ceil(outW * (minFramePx / minCellW));
            }
        }

        double maxByArea = Math.sqrt(MAX_PIXELS / (double) plan.outHRatio);
        if (outW > maxByArea) {
            outW = (int) Math.max(64, Math.floor(maxByArea));
        }
        return outW;
    }

    /** 把比例乘成像素。 */
    private static Layout materialize(Plan plan, int outW) {
        if (plan == null || outW <= 0) return null;

        Layout l = new Layout();
        l.outW = outW;
        l.stock = plan.stock;
        l.sideMargin = outW * plan.sideRatio;
        l.textH = outW * plan.textRatio;
        l.bandPx = outW * plan.bandRatio;
        l.framePx = outW * plan.frameRatio;
        l.bottomMargin = outW * plan.bottomRatio;

        l.cells = new RectF[plan.cellRatios.length];
        boolean first = true;
        for (int i = 0; i < plan.cellRatios.length; i++) {
            RectF rect = toRect(plan.cellRatios[i], outW);
            l.cells[i] = rect;
            if (first) {
                l.bounds.set(rect);
                first = false;
            } else {
                l.bounds.union(rect);
            }
        }

        l.perfBands = new RectF[plan.bandRects.length];
        for (int i = 0; i < plan.bandRects.length; i++) {
            l.perfBands[i] = toRectLtrb(plan.bandRects[i], outW);
        }

        l.cutEdges = new RectF[plan.cutRects.length];
        for (int i = 0; i < plan.cutRects.length; i++) {
            l.cutEdges[i] = toRectLtrb(plan.cutRects[i], outW);
        }

        l.strips = new RectF[plan.stripRects.length];
        for (int i = 0; i < plan.stripRects.length; i++) {
            l.strips[i] = toRectLtrb(plan.stripRects[i], outW);
        }

        // 齿孔与「挖了孔的整截外形」都在这里一次算好：
        // 孔既要用来裁剪片基（真正的镂空），又要用来描边，两处必须是同一组矩形，
        // 否则描边会和镂空错开，看着像重影。
        computeHoles(l);
        l.stripShapes = buildStripShapes(l);

        l.outH = Math.round(outW * plan.outHRatio);
        return l.outH <= 0 ? null : l;
    }

    /** 每条齿孔带上排好一排孔。下标与 {@link Layout#perfBands} 对应。 */
    private static void computeHoles(Layout l) {
        l.holes = new RectF[l.perfBands.length][];

        float pitch = PERF_PITCH_RATIO * l.framePx;
        if (pitch <= 0.5f) {
            for (int i = 0; i < l.holes.length; i++) l.holes[i] = new RectF[0];
            return;
        }

        float along = pitch * PERF_LENGTH_RATIO;      // 沿片长方向
        float across = along * PERF_CROSS_RATIO;      // 横跨片宽方向

        for (int b = 0; b < l.perfBands.length; b++) {
            RectF band = l.perfBands[b];
            if (band == null || band.width() <= 0f || band.height() <= 0f
                    || along <= 0.4f || across <= 0.4f) {
                l.holes[b] = new RectF[0];
                continue;
            }

            boolean vertical = band.height() > band.width();
            float span = vertical ? band.height() : band.width();
            int count = holeCount(span, pitch);
            float total = count * pitch;
            float start = (vertical ? band.top : band.left)
                    + (span - total) / 2f + (pitch - along) / 2f;
            float mid = vertical ? band.centerX() : band.centerY();

            RectF[] holes = new RectF[count];
            for (int i = 0; i < count; i++) {
                float pos = start + i * pitch;
                holes[i] = vertical
                        ? new RectF(mid - across / 2f, pos, mid + across / 2f, pos + along)
                        : new RectF(pos, mid - across / 2f, pos + along, mid + across / 2f);
            }
            l.holes[b] = holes;
        }
    }

    /**
     * 每一截胶片的外形 = 整截矩形 − 它的两排齿孔。
     *
     * <p>用 {@code EVEN_ODD} 填充规则挖孔，而不是"在片基上画几个灰点"——
     * 挖出来的孔是真的透光，台面从孔里露上来，这也是片基读起来像塑料片而不是纸板的原因。
     */
    private static Path[] buildStripShapes(Layout l) {
        Path[] shapes = new Path[l.strips.length];

        for (int s = 0; s < l.strips.length; s++) {
            Path shape = new Path();
            RectF strip = l.strips[s];
            if (strip == null) {
                shapes[s] = shape;
                continue;
            }
            shape.addRect(strip, Path.Direction.CW);

            // 每截两条带（上、下），下标按顺序对应
            for (int k = 0; k < 2; k++) {
                int bandIndex = s * 2 + k;
                if (bandIndex >= l.holes.length) break;
                for (RectF hole : l.holes[bandIndex]) {
                    float radius = Math.min(hole.width(), hole.height()) * 0.28f;
                    shape.addRoundRect(hole, radius, radius, Path.Direction.CW);
                }
            }

            shape.setFillType(Path.FillType.EVEN_ODD);
            shapes[s] = shape;
        }
        return shapes;
    }

    /** {左, 上, 宽, 高} 比例 → 像素矩形 */
    private static RectF toRect(float[] ratio, int outW) {
        return new RectF(
                outW * ratio[0], outW * ratio[1],
                outW * (ratio[0] + ratio[2]), outW * (ratio[1] + ratio[3]));
    }

    /**
     * {左, 上, 右, 下} 比例 → 像素矩形。
     *
     * <p>齿孔带与裁切边用的是这四个绝对边界，不是 {@code {左,上,宽,高}}——
     * 两者混用会把带子画到两倍远的地方（而且因为照片画得对，肉眼看不出规律，
     * 只觉得「孔莫名其妙跑到文字上了」）。
     */
    private static RectF toRectLtrb(float[] ratio, int outW) {
        return new RectF(
                outW * ratio[0], outW * ratio[1],
                outW * ratio[2], outW * ratio[3]);
    }

    private static List<Bitmap> filterValid(List<Bitmap> photos) {
        List<Bitmap> valid = new ArrayList<>();
        if (photos == null) return valid;
        for (Bitmap b : photos) {
            if (b != null && !b.isRecycled() && b.getWidth() > 0 && b.getHeight() > 0) {
                valid.add(b);
            }
        }
        return valid;
    }

    private static float[] aspectsOf(List<Bitmap> photos) {
        float[] aspects = new float[photos.size()];
        for (int i = 0; i < aspects.length; i++) {
            aspects[i] = photos.get(i).getWidth() / (float) photos.get(i).getHeight();
        }
        return aspects;
    }

    // ====================================================================
    //  照片
    // ====================================================================

    private static void drawPhoto(Canvas canvas, Bitmap photo, RectF dst, FilmStock stock) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(photo, null, dst, paint);

        // 极淡发丝线：把照片和片基分开，避免浅色照片与浅色片基糊成一片
        Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
        hairline.setStyle(Paint.Style.STROKE);
        hairline.setStrokeWidth(Math.max(1f, dst.width() * 0.0009f));
        hairline.setColor(withAlpha(stock.ink, 26));
        canvas.drawRect(dst, hairline);
    }

    // ====================================================================
    //  齿孔
    // ====================================================================

    /**
     * 孔缘。
     *
     * <p>孔本身已经在 {@link #buildStripShapes} 里被真正挖掉了，台面从孔里透上来，
     * 所以这里<b>不再填充</b>——只需要一圈极淡的边，把冲压的厚度交代出来。
     * 早先是拿半透明色块去"画"孔，在深色片基上还能读，浅色片基上就是几块灰斑。
     *
     * <p>孔的三个尺寸全部由<b>帧高</b>推出来（见类注释里的三个比例），
     * 所以照片一小、孔跟着小，一整格永远是 8 个孔。
     */
    private static void drawHoleEdges(Canvas canvas, Layout l) {
        if (l.holes == null || l.holes.length == 0) return;

        float pitch = PERF_PITCH_RATIO * l.framePx;
        if (pitch <= 0.5f) return;
        float along = pitch * PERF_LENGTH_RATIO;
        if (along <= 0.4f) return;

        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, along * 0.05f));
        edge.setColor(l.stock.holeEdgeColor());

        for (RectF[] holes : l.holes) {
            for (RectF hole : holes) {
                float radius = Math.min(hole.width(), hole.height()) * 0.28f;
                canvas.drawRoundRect(hole, radius, radius, edge);
            }
        }
    }

    /**
     * 截与截之间的裁切边。
     *
     * <p>只画在真的剪开的地方：几截胶片摊在一起时，段与段的缝需要一条极淡的
     * 边才读得出来——否则相邻两截的片基同色同质，会糊成一整块。
     */
    private static void drawCutEdges(Canvas canvas, Layout l) {
        if (l.cutEdges == null || l.cutEdges.length == 0) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(Math.max(1f, l.outW * 0.0011f));
        paint.setColor(withAlpha(l.stock.ink, 30));

        for (RectF cut : l.cutEdges) {
            if (cut == null) continue;
            canvas.drawLine(cut.left, cut.top, cut.right, cut.top, paint);
        }
    }

    /**
     * 一条带上能排多少个孔。
     *
     * <p>上限放到 160 而不是原来的 30：节距现在只有帧高的 0.198 倍，
     * 一条长带本来就该有几十个孔——压低上限会把节距撑开，正是「孔太大很假」的病根。
     */
    private static int holeCount(float span, float pitch) {
        int count = (int) Math.floor(span / pitch);
        return Math.max(4, Math.min(160, count));
    }

    private static void drawHole(Canvas canvas, float left, float top, float right, float bottom,
                                 Paint fill, Paint edge) {
        float radius = Math.min(right - left, bottom - top) * 0.28f;
        RectF rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, edge);
    }

    // ====================================================================
    //  片边文字
    // ====================================================================

    private static void drawCaption(Canvas canvas, Layout l, FilmBorderConfig cfg) {
        float textTop = l.bounds.bottom + l.bottomMargin;
        float left = l.sideMargin;
        float right = l.outW - l.sideMargin;
        float maxWidth = right - left;
        if (maxWidth <= 1f) return;

        // ---- 帧号（先量宽度，型号行要给它让位）----
        // 片边文字在最后一截胶片「下方」，也就是落在台面上——所以配色按台面走，
        // 不能沿用片基那一套（浅片基配深字，一放到深台面上就看不见了）。
        float frameSize = l.outW * 0.0255f;
        String frameNumber = cfg.frameNumber ? cfg.resolveFrameNumber() : "";
        Paint framePaint = textPaint(l.stock.captionAccent, frameSize, Typeface.BOLD, 0.14f);
        framePaint.setAlpha(170);
        float frameWidth = frameNumber.isEmpty() ? 0f : framePaint.measureText(frameNumber);
        float frameGap = frameWidth > 0f ? maxWidth * 0.06f : 0f;

        // ---- 第一行：胶片型号 ----
        float stockSize = l.outW * 0.030f;
        Paint stockPaint = textPaint(l.stock.captionAccent, stockSize, Typeface.BOLD, 0.14f);
        String stockText = upper(cfg.resolveStock());

        // ---- 第二行：机身 · 镜头 · 曝光 · 日期 ----
        float specSize = l.outW * 0.0195f;
        Paint specPaint = textPaint(l.stock.captionInk, specSize, Typeface.NORMAL, 0.10f);
        specPaint.setAlpha(210);
        String specText = upper(cfg.resolveSpec());

        float[] baselines = captionBaselines(textTop, l.textH, stockPaint, specPaint,
                stockText, specText);

        drawFittedText(canvas, stockText, left, baselines[0],
                maxWidth - frameWidth - frameGap, stockSize, stockPaint);

        if (frameWidth > 0f) {
            canvas.drawText(frameNumber, right - frameWidth, baselines[0], framePaint);
        }

        drawFittedText(canvas, specText, left, baselines[1], maxWidth, specSize, specPaint);
    }

    /**
     * 片边文字两行的基线位置，返回 {@code {型号行, 参数行}}。
     *
     * <p><b>两行之间的空白 = 参数行的高度</b>（按字形量，不是拍一个比例）。
     *
     * <p>早先第二行固定在「文字块高度的 80%」处：`0.36 × 0.15 × 画布宽`，
     * 这个数和字号毫无关系——字号一小，空隙就显得特别大，
     * 而且画布越宽拉得越开。现在按墨迹算：
     *
     * <pre>
     *   型号行墨迹下沿 + 空白(= 参数行墨迹高度) + 参数行墨迹上沿 = 参数行基线
     * </pre>
     *
     * 于是空隙恒等于参数那一行自己的高度，字号怎么变都成立。
     */
    static float[] captionBaselines(float textTop, float textH,
                                    Paint stockPaint, Paint specPaint,
                                    String stockText, String specText) {
        // 第一行仍在文字块的 44% 处：上面留出它的上伸部分
        float stockBaseline = textTop + textH * 0.44f;

        android.graphics.Rect stockInk = new android.graphics.Rect();
        stockPaint.getTextBounds(stockText, 0, stockText.length(), stockInk);

        android.graphics.Rect specInk = new android.graphics.Rect();
        specPaint.getTextBounds(specText, 0, specText.length(), specInk);

        // specInk.top 是负数（在基线之上），所以是「减去」它
        float gap = specInk.height();
        float specBaseline = stockBaseline + stockInk.bottom + gap - specInk.top;

        return new float[]{stockBaseline, specBaseline};
    }

    private static Paint textPaint(int color, float size, int style, float letterSpacing) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        // 胶片片边印刷用的是窄体无衬线，condensed 最接近
        paint.setTypeface(Typeface.create("sans-serif-condensed", style));
        paint.setTextSize(size);
        paint.setLetterSpacing(letterSpacing);
        paint.setColor(color);
        return paint;
    }

    /**
     * 先缩号、再省略地画一行字。
     *
     * <p>片边文字不能换行——真实胶片上就没有第二行。所以行宽不够时先小幅缩号
     * （最多缩到 62%），仍然放不下才截断加省略号，保证文字永远不会压到照片或出血。
     */
    private static void drawFittedText(Canvas canvas, String text, float x, float y,
                                       float maxWidth, float baseSize, Paint paint) {
        if (text == null || text.isEmpty() || maxWidth <= 1f) return;

        float size = baseSize;
        paint.setTextSize(size);
        float floor = baseSize * 0.62f;
        while (size > floor && paint.measureText(text) > maxWidth) {
            size = Math.max(floor, size - baseSize * 0.02f);
            paint.setTextSize(size);
        }

        String out = text;
        if (paint.measureText(out) > maxWidth) {
            out = ellipsize(paint, text, maxWidth);
        }
        canvas.drawText(out, x, y, paint);
    }

    private static String ellipsize(Paint paint, String text, float maxWidth) {
        float ellipsisWidth = paint.measureText("…");
        int end = text.length();
        while (end > 1 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(1, end)) + "…";
    }

    private static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}


