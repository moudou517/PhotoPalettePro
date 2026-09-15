package com.example.photopalettepro.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.example.photopalettepro.zine.PostcardPaper;
import com.example.photopalettepro.zine.SceneMotifRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.example.photopalettepro.render.ColorExtractor;
import com.example.photopalettepro.config.ZinePostcardConfig;
import com.example.photopalettepro.render.ZinePostcardRenderer;

/**
 * Zine 明信片渲染入口：4:3 横版，暖象牙纸。
 *
 * <p>画面结构（版式本身保持稳定，不随输入变化）：
 * <ul>
 *   <li>左侧：紧凑元数据块——小序号 + 短横线 / 字距拉开的衬线标题 /
 *       斜体副标题 / {@code LOCATION} 与 {@code DATE} 标签 + 书写下划线</li>
 *   <li>右下：主元素（见 {@link SceneMotifRenderer}）</li>
 *   <li>左下：取自原图的方形色块</li>
 *   <li>背面：细外框 / 偏右分割线 / 邮票框 / 地址线 / 留言区</li>
 * </ul>
 *
 * <p>本类只负责<b>版式与排版</b>；主元素的绘制拆到
 * {@link SceneMotifRenderer}，纸面拆到 {@link PostcardPaper}。
 *
 * <p>设计规范来源（两套 Skill 叠加，均为离线实现）：
 * <ul>
 *   <li>photo-to-zine-postcard —— 明信片正反面版式</li>
 *   <li>photo-abstract-editorial —— 克制的抽象美术方向</li>
 *   <li>gathered-scenes-zine —— 真景为锚 / 色彩成结构 / 撕纸成界</li>
 * </ul>
 */
public final class ZinePostcardRenderer {

    /** 4:3 横版 */
    private static final float CARD_H_OVER_W = 0.75f;

    /** 色块数量上限：主页面色板最多 6 色，全部保留 */
    private static final int MAX_SWATCHES = 6;

    private static final int INK = Color.rgb(58, 54, 48);
    private static final int RULE = Color.rgb(178, 170, 154);
    private static final int LABEL_COLOR = Color.rgb(124, 116, 103);

    // 主元素区域（占整张 4:3 画布的右下部分）
    private static final float MOTIF_LEFT = 0.355f;
    private static final float MOTIF_TOP = 0.085f;
    private static final float MOTIF_RIGHT = 0.975f;
    private static final float MOTIF_BOTTOM = 0.955f;

    private ZinePostcardRenderer() {
    }

    public static int heightFor(int w) {
        return Math.round(w * CARD_H_OVER_W);
    }

    // ====================================================================
    //  正面
    // ====================================================================

    public static Bitmap renderFront(Bitmap photo, List<Integer> palette,
                                     ZinePostcardConfig cfg, int W) {
        if (cfg == null) cfg = new ZinePostcardConfig();

        int H = heightFor(W);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        PostcardPaper.paint(canvas, W, H, 20240517L);

        drawMotif(canvas, photo, palette, W, H, 11L, cfg.realityAnchor);
        drawMetadata(canvas, cfg, W, H);
        drawSwatches(canvas, photo, palette, W, H);

        return out;
    }

    // ====================================================================
    //  背面
    // ====================================================================

    public static Bitmap renderBack(Bitmap photo, List<Integer> palette,
                                    ZinePostcardConfig cfg, int W) {
        if (cfg == null) cfg = new ZinePostcardConfig();

        int H = heightFor(W);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        PostcardPaper.paint(canvas, W, H, 7744201L);

        float m = W * 0.052f;
        float left = m * 1.05f;
        float right = W - m * 1.05f;
        float top = m * 1.35f;
        float dividerX = W * 0.555f;
        float contentTop = top - W * 0.012f;
        float contentBottom = H - m * 1.15f;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1.2f, W * 0.0011f));
        line.setColor(RULE);

        // 细外框
        canvas.drawRect(new RectF(m * 0.62f, m * 0.62f, W - m * 0.62f, H - m * 0.62f), line);

        // 原图衍生的极淡线稿：铺满左半边（留言区）
        drawWatermark(canvas, photo, palette, left, contentTop, dividerX, contentBottom);

        canvas.drawLine(dividerX, contentTop, dividerX, contentBottom, line);

        drawStampBox(canvas, right, top, W);
        drawAddressLines(canvas, dividerX, right, top, W);
        drawBackFooter(canvas, cfg, left, H, m);

        // POST CARD 最后画，压在左侧水印之上
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setTextSize(W * 0.0205f);
        label.setLetterSpacing(0.32f);
        label.setColor(INK);
        canvas.drawText("POST CARD", left, top + W * 0.0205f, label);

        return out;
    }

    /** 左半边的极淡线稿水印（不含真景锚点，避免水印出现一块照片）。 */
    private static void drawWatermark(Canvas canvas, Bitmap photo, List<Integer> palette,
                                      float left, float top, float right, float bottom) {
        if (photo == null || photo.isRecycled()) return;

        int w = Math.round(right - left);
        int h = Math.round(bottom - top);
        if (w <= 0 || h <= 0) return;

        Bitmap watermark = SceneMotifRenderer.render(photo, palette, w, h, 53L, false);
        if (watermark == null) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(26);
        canvas.drawBitmap(watermark, null, new RectF(left, top, right, bottom), paint);
        if (!watermark.isRecycled()) watermark.recycle();
    }

    private static void drawStampBox(Canvas canvas, float right, float top, int W) {
        float stampW = W * 0.135f;
        float stampH = stampW * 1.18f;
        RectF stamp = new RectF(right - stampW, top - W * 0.006f,
                right, top - W * 0.006f + stampH);

        Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(Math.max(1.0f, W * 0.0009f));
        dashed.setColor(Color.argb(150, 176, 168, 152));
        dashed.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{W * 0.008f, W * 0.006f}, 0));
        canvas.drawRect(stamp, dashed);
    }

    private static void drawAddressLines(Canvas canvas, float dividerX, float right, float top, int W) {
        float stampW = W * 0.135f;
        float stampH = stampW * 1.18f;
        float addressLeft = dividerX + W * 0.048f;
        float addressRight = right - W * 0.010f;
        float addressTop = top - W * 0.006f + stampH + W * 0.060f;
        float gap = W * 0.052f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(Math.max(1.0f, W * 0.0009f));
        paint.setColor(Color.argb(120, 176, 168, 152));
        for (int i = 0; i < 4; i++) {
            float y = addressTop + i * gap;
            canvas.drawLine(addressLeft, y, addressRight, y, paint);
        }
    }

    private static void drawBackFooter(Canvas canvas, ZinePostcardConfig cfg,
                                       float left, int H, float m) {
        StringBuilder text = new StringBuilder();
        if (notBlank(cfg.index)) text.append("NO. ").append(cfg.index);
        if (notBlank(cfg.location)) {
            if (text.length() > 0) text.append("   ");
            text.append(cfg.location.toUpperCase(Locale.getDefault()));
        }
        if (text.length() == 0) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        paint.setTextSize(H * 0.0207f);
        paint.setLetterSpacing(0.18f);
        paint.setColor(Color.argb(190, 90, 84, 74));
        canvas.drawText(text.toString(), left, H - m * 1.05f, paint);
    }

    // ====================================================================
    //  主元素
    // ====================================================================

    private static void drawMotif(Canvas canvas, Bitmap photo, List<Integer> palette,
                                  int W, int H, long seed, boolean realityAnchor) {
        if (photo == null || photo.isRecycled()) return;

        float left = W * MOTIF_LEFT;
        float top = H * MOTIF_TOP;
        float right = W * MOTIF_RIGHT;
        float bottom = H * MOTIF_BOTTOM;

        int w = Math.round(right - left);
        int h = Math.round(bottom - top);
        if (w <= 0 || h <= 0) return;

        Bitmap motif = SceneMotifRenderer.render(photo, palette, w, h, seed, realityAnchor);
        if (motif == null) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(motif, null, new RectF(left, top, right, bottom), paint);
        // 画完立即回收，否则每渲染一次就泄漏一张主元素位图
        if (!motif.isRecycled()) motif.recycle();
    }

    // ====================================================================
    //  元数据与色块
    // ====================================================================

    private static void drawMetadata(Canvas canvas, ZinePostcardConfig cfg, int W, int H) {
        float x = W * 0.055f;
        float columnWidth = W * 0.235f;

        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setStrokeWidth(Math.max(1.0f, W * 0.0009f));
        rule.setColor(Color.argb(150, 176, 168, 152));

        // 小序号 + 短横线
        Paint index = new Paint(Paint.ANTI_ALIAS_FLAG);
        index.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        index.setTextSize(W * 0.0135f);
        index.setLetterSpacing(0.20f);
        index.setColor(Color.argb(200, 96, 90, 79));
        canvas.drawText(notBlank(cfg.index) ? cfg.index : "01", x, H * 0.395f, index);
        canvas.drawLine(x, H * 0.448f, x + columnWidth * 0.21f, H * 0.448f, rule);

        // 标题：衬线 + 拉开字距
        if (notBlank(cfg.title)) {
            Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
            title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            title.setLetterSpacing(0.18f);
            title.setColor(INK);
            String text = cfg.title.toUpperCase(Locale.getDefault());
            title.setTextSize(fitSize(title, text, W * 0.0295f, W * 0.290f));
            canvas.drawText(text, x, H * 0.525f, title);
        }

        // 副标题：斜体衬线
        if (notBlank(cfg.subtitle)) {
            Paint subtitle = new Paint(Paint.ANTI_ALIAS_FLAG);
            subtitle.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            subtitle.setColor(Color.argb(215, 92, 86, 76));
            subtitle.setTextSize(fitSize(subtitle, cfg.subtitle, W * 0.0190f, W * 0.265f));
            canvas.drawText(cfg.subtitle, x, H * 0.583f, subtitle);
        }

        drawMetaField(canvas, "LOCATION", cfg.location, x, columnWidth, H * 0.690f, H * 0.727f, W, rule);
        drawMetaField(canvas, "DATE", cfg.date, x, columnWidth, H * 0.785f, H * 0.822f, W, rule);
    }

    private static void drawMetaField(Canvas canvas, String label, String value,
                                      float x, float columnWidth, float labelBaseline,
                                      float ruleY, int W, Paint rule) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(W * 0.0122f);
        paint.setLetterSpacing(0.30f);
        paint.setColor(LABEL_COLOR);
        canvas.drawText(label, x, labelBaseline, paint);

        canvas.drawLine(x, ruleY, x + columnWidth, ruleY, rule);

        if (notBlank(value)) {
            Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valuePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            valuePaint.setTextSize(W * 0.0142f);
            valuePaint.setColor(Color.argb(220, 70, 66, 58));
            canvas.drawText(value, x, ruleY - W * 0.0065f, valuePaint);
        }
    }

    /** 超过可用宽度就等比缩小字号。 */
    private static float fitSize(Paint paint, String text, float size, float maxWidth) {
        paint.setTextSize(size);
        float measured = paint.measureText(text);
        return measured > maxWidth ? size * (maxWidth / measured) : size;
    }

    /** 色块：按数量自适应宽度，整排与左侧元数据列对齐。 */
    private static void drawSwatches(Canvas canvas, Bitmap photo, List<Integer> palette, int W, int H) {
        List<Integer> colors = resolveSwatches(photo, palette);
        int count = colors.size();
        if (count == 0) return;

        float x = W * 0.055f;
        float rowWidth = W * 0.255f;
        float gapRatio = 0.45f;
        float size = rowWidth / (count + (count - 1) * gapRatio);
        float gap = size * gapRatio;
        float y = H * 0.862f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < count; i++) {
            paint.setColor(colors.get(i));
            float left = x + i * (size + gap);
            canvas.drawRect(left, y, left + size, y + size, paint);
        }
    }

    /**
     * 色块使用主页面那套完整色板（最多 6 个，顺序即权重降序），
     * 不自行挑选 dominant / dark / pale，也不砍到 3 个。
     */
    private static List<Integer> resolveSwatches(Bitmap photo, List<Integer> palette) {
        List<Integer> out = new ArrayList<>();
        if (palette != null) {
            for (int color : palette) {
                if (!out.contains(color)) out.add(color);
                if (out.size() == MAX_SWATCHES) break;
            }
        }
        if (out.size() >= 2) return out;

        if (photo != null) {
            for (int color : ColorExtractor.getTopWeightedColors(photo, MAX_SWATCHES)) {
                if (!out.contains(color)) out.add(color);
                if (out.size() == MAX_SWATCHES) break;
            }
        }
        while (out.size() < 3) out.add(Color.rgb(150, 142, 128));
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
