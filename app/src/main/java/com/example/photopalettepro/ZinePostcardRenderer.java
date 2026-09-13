package com.example.photopalettepro;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Photo → Zine Postcard / Abstract Editorial 本地渲染器。
 *
 * 画布：4:3 横版，暖象牙纸。
 * 左侧：紧凑元数据块（小序号 + 短横线 / 字距拉开的衬线标题 / 斜体副标题 /
 *       LOCATION 与 DATE 标签 + 书写下划线）
 * 右下：主元素
 * 左下：3 个方形色块
 *
 * 主元素遵循 photo-abstract-editorial 的美术方向（Art Direction）：
 *   「先读作克制的抽象，再想起原图」——锥形笔触 / 短横带 / 结构轴线，
 *   保留原图的方向、比例、节奏、重心与不对称；不发明对称、不做完整插画、
 *   不做可辨识的矢量描摹。
 *
 * 实现：低分辨率提取结构边缘 → 在输出分辨率上用带抖动、粗细不一的短笔触
 * 重绘为「简笔线稿」，底下垫一层取自原图、柔化并压淡的平涂色块，
 * 最后用程序化 alpha 场做溶解软边。
 */
public class ZinePostcardRenderer {

    /** 4:3 横版画布 */
    private static final float CARD_H_OVER_W = 0.75f;

    /** 色块数量上限：主页面色板最多 6 色，这里全部保留 */
    private static final int MAX_SWATCHES = 6;

    private static final int PAPER      = Color.rgb(246, 242, 232);
    private static final int PAPER_DEEP = Color.rgb(212, 200, 176);
    private static final int INK        = Color.rgb(58, 54, 48);
    private static final int RULE       = Color.rgb(178, 170, 154);
    private static final int LABEL_COL  = Color.rgb(124, 116, 103);

    public static int heightFor(int w) {
        return Math.round(w * CARD_H_OVER_W);
    }

    // ====================================================================
    //  正面（4:3 横版 · 编辑部分）
    // ====================================================================

    public static Bitmap renderFront(Bitmap photo, List<Integer> palette,
                                     ZinePostcardConfig cfg, int W) {
        if (cfg == null) cfg = new ZinePostcardConfig();

        int H = heightFor(W);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        drawPaper(c, W, H, 20240517L);

        // ---------- 右下：简笔线稿主元素 ----------
        float motifL = W * 0.355f;
        float motifT = H * 0.085f;
        float motifR = W * 0.975f;
        float motifB = H * 0.955f;

        if (photo != null && !photo.isRecycled()) {
            Bitmap motif = renderSketchScene(photo, palette,
                    Math.round(motifR - motifL), Math.round(motifB - motifT), 11L);
            if (motif != null) {
                Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                c.drawBitmap(motif, null, new RectF(motifL, motifT, motifR, motifB), img);
                // 画完立即回收：否则每渲染一次就泄漏一张主元素位图
                if (!motif.isRecycled()) motif.recycle();
            }
        }

        // ---------- 左侧：元数据块 ----------
        drawMetadata(c, cfg, W, H);

        // ---------- 左下：3 个方形色块 ----------
        drawSwatches(c, photo, palette, W, H);

        return out;
    }

    // ====================================================================
    //  背面（4:3 横版统一明信片背面）
    // ====================================================================

    public static Bitmap renderBack(Bitmap photo, List<Integer> palette,
                                    ZinePostcardConfig cfg, int W) {
        if (cfg == null) cfg = new ZinePostcardConfig();

        int H = heightFor(W);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        drawPaper(c, W, H, 7744201L);

        float m = W * 0.052f;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1.2f, W * 0.0011f));
        line.setColor(RULE);

        c.drawRect(new RectF(m * 0.62f, m * 0.62f, W - m * 0.62f, H - m * 0.62f), line);

        float left = m * 1.05f;
        float right = W - m * 1.05f;
        float top = m * 1.35f;

        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setTextSize(W * 0.0205f);
        label.setLetterSpacing(0.32f);
        label.setColor(INK);
        c.drawText("POST CARD", left, top + W * 0.0205f, label);

        // 极淡的原图衍生水印（左侧留言区）
        // 分割线位置（后面画图、画线都要用，先算出来）
        float divX = W * 0.555f;
        float contentTop = top - W * 0.012f;
        float contentBottom = H - m * 1.15f;

        // 原图衍生水印：铺满整个左半边（与外框内缘对齐），
        // 分割线、邮票框、地址线仍在右侧保持明信片的功能性。
        if (photo != null && !photo.isRecycled()) {
            float wmLeft = left;
            float wmRight = divX;
            Bitmap wm = renderSketchScene(photo, palette,
                    Math.round(wmRight - wmLeft),
                    Math.round(contentBottom - contentTop), 53L);
            if (wm != null) {
                Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                wp.setAlpha(52);
                c.drawBitmap(wm, null,
                        new RectF(wmLeft, contentTop, wmRight, contentBottom), wp);
                if (!wm.isRecycled()) wm.recycle();
            }
        }

        c.drawLine(divX, contentTop, divX, contentBottom, line);

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
        c.drawRect(stamp, dashed);

        float addrLeft = divX + W * 0.048f;
        float addrRight = right - W * 0.010f;
        float addrTop = stamp.bottom + W * 0.060f;
        float addrGap = W * 0.052f;
        Paint addrLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        addrLine.setStrokeWidth(Math.max(1.0f, W * 0.0009f));
        addrLine.setColor(Color.argb(120, 176, 168, 152));
        for (int i = 0; i < 4; i++) {
            float y = addrTop + i * addrGap;
            c.drawLine(addrLeft, y, addrRight, y, addrLine);
        }

        Paint tiny = new Paint(Paint.ANTI_ALIAS_FLAG);
        tiny.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        tiny.setTextSize(W * 0.0155f);
        tiny.setLetterSpacing(0.18f);
        tiny.setColor(Color.argb(190, 90, 84, 74));
        StringBuilder sb = new StringBuilder();
        if (notBlank(cfg.index)) sb.append("NO. ").append(cfg.index);
        if (notBlank(cfg.location)) {
            if (sb.length() > 0) sb.append("   ");
            sb.append(cfg.location.toUpperCase(Locale.getDefault()));
        }
        if (sb.length() > 0) {
            c.drawText(sb.toString(), left, H - m * 1.05f, tiny);
        }

        return out;
    }

    // ====================================================================
    //  元数据 + 色块
    // ====================================================================

    private static void drawMetadata(Canvas c, ZinePostcardConfig cfg, int W, int H) {
        float x = W * 0.055f;
        float colW = W * 0.235f;

        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setStrokeWidth(Math.max(1.0f, W * 0.0009f));
        rule.setColor(Color.argb(150, 176, 168, 152));

        Paint idx = new Paint(Paint.ANTI_ALIAS_FLAG);
        idx.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        idx.setTextSize(W * 0.0135f);
        idx.setLetterSpacing(0.20f);
        idx.setColor(Color.argb(200, 96, 90, 79));
        String index = notBlank(cfg.index) ? cfg.index : "01";
        c.drawText(index, x, H * 0.395f, idx);
        c.drawLine(x, H * 0.448f, x + colW * 0.21f, H * 0.448f, rule);

        if (notBlank(cfg.title)) {
            Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
            title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            title.setLetterSpacing(0.18f);
            title.setColor(INK);
            float size = W * 0.0295f;
            title.setTextSize(size);
            String text = cfg.title.toUpperCase(Locale.getDefault());
            float maxW = W * 0.290f;
            if (title.measureText(text) > maxW) {
                size = size * (maxW / title.measureText(text));
                title.setTextSize(size);
            }
            c.drawText(text, x, H * 0.525f, title);
        }

        if (notBlank(cfg.subtitle)) {
            Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
            sub.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            sub.setColor(Color.argb(215, 92, 86, 76));
            float size = W * 0.0190f;
            sub.setTextSize(size);
            float maxW = W * 0.265f;
            if (sub.measureText(cfg.subtitle) > maxW) {
                size = size * (maxW / sub.measureText(cfg.subtitle));
                sub.setTextSize(size);
            }
            c.drawText(cfg.subtitle, x, H * 0.583f, sub);
        }

        drawMetaField(c, "LOCATION", cfg.location, x, colW, H * 0.690f, H * 0.727f, W, rule);
        drawMetaField(c, "DATE", cfg.date, x, colW, H * 0.785f, H * 0.822f, W, rule);
    }

    private static void drawMetaField(Canvas c, String label, String value,
                                      float x, float colW, float labelBaseline,
                                      float ruleY, int W, Paint rule) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextSize(W * 0.0122f);
        p.setLetterSpacing(0.30f);
        p.setColor(LABEL_COL);
        c.drawText(label, x, labelBaseline, p);

        c.drawLine(x, ruleY, x + colW, ruleY, rule);

        if (notBlank(value)) {
            Paint vp = new Paint(Paint.ANTI_ALIAS_FLAG);
            vp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            vp.setTextSize(W * 0.0142f);
            vp.setColor(Color.argb(220, 70, 66, 58));
            c.drawText(value, x, ruleY - W * 0.0065f, vp);
        }
    }

    /** 色块：按数量自适应宽度，整排与左侧元数据列对齐 */
    private static void drawSwatches(Canvas c, Bitmap photo, List<Integer> palette, int W, int H) {
        List<Integer> colors = resolveSwatches(photo, palette);
        int n = colors.size();
        if (n == 0) return;

        float x = W * 0.055f;
        float rowW = W * 0.255f;
        float gapRatio = 0.45f;
        float size = rowW / (n + (n - 1) * gapRatio);
        float gap = size * gapRatio;
        float y = H * 0.862f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < n; i++) {
            p.setColor(colors.get(i));
            float left = x + i * (size + gap);
            c.drawRect(left, y, left + size, y + size, p);
        }
    }

    /**
     * 色块使用主页面那套完整色板（最多 6 个，顺序即权重降序），
     * 不再自行挑选 dominant / dark / pale，也不砍到 3 个。
     */
    private static List<Integer> resolveSwatches(Bitmap photo, List<Integer> palette) {
        List<Integer> out = new ArrayList<>();
        if (palette != null) {
            for (int c : palette) {
                if (!out.contains(c)) out.add(c);
                if (out.size() == MAX_SWATCHES) break;
            }
        }
        if (out.size() >= 2) return out;

        // 兜底：色板不足时自行聚色补齐
        if (photo != null) {
            List<Integer> own = ColorExtractor.getTopWeightedColors(photo, MAX_SWATCHES);
            for (int c : own) {
                if (!out.contains(c)) out.add(c);
                if (out.size() == MAX_SWATCHES) break;
            }
        }
        while (out.size() < 3) out.add(Color.rgb(150, 142, 128));
        return out;
    }

    // ====================================================================
    //  主元素：简笔线稿 + 淡色块
    // ====================================================================

    private static Bitmap renderSketchScene(Bitmap photo, List<Integer> palette,
                                            int outW, int outH, long seed) {
        if (photo == null || photo.isRecycled() || outW <= 0 || outH <= 0) return null;

        float scale = Math.min((float) outW / photo.getWidth(), (float) outH / photo.getHeight());
        int sw = Math.max(1, Math.round(photo.getWidth() * scale));
        int sh = Math.max(1, Math.round(photo.getHeight() * scale));

        Bitmap masses = buildColorMasses(photo, palette, sw, sh);
        Bitmap lines = buildSketchLines(photo, sw, sh, seed);

        Bitmap canvas = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);
        float dx = (outW - sw) / 2f;
        float dy = (outH - sh) / 2f;
        RectF dst = new RectF(dx, dy, dx + sw, dy + sh);

        // 色块压得很淡：像简笔画里淡淡涂的一层
        if (masses != null) {
            Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            mp.setAlpha(126);
            c.drawBitmap(masses, null, dst, mp);
        }
        if (lines != null) {
            c.drawBitmap(lines, null, dst,
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }

        Bitmap mask = makeDissolveMask(outW, outH, dx, dy, sw, sh, seed);
        Paint xfer = new Paint(Paint.ANTI_ALIAS_FLAG);
        xfer.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask, 0, 0, xfer);
        if (!mask.isRecycled()) mask.recycle();

        if (masses != null && !masses.isRecycled()) masses.recycle();
        if (lines != null && !lines.isRecycled()) lines.recycle();
        return canvas;
    }

    /** 取自原图的柔和平涂色块（简笔画的"涂色"层） */
    private static Bitmap buildColorMasses(Bitmap photo, List<Integer> palette, int w, int h) {
        Bitmap work = Bitmap.createScaledBitmap(photo, w, h, true);
        if (!work.isMutable()) {
            Bitmap m = work.copy(Bitmap.Config.ARGB_8888, true);
            if (m != null) work = m;
        }
        int n = w * h;
        int[] px = new int[n];
        work.getPixels(px, 0, w, 0, 0, w, h);
        // work 只是取像素用的临时图，取完就该释放
        if (work != photo && !work.isRecycled()) work.recycle();

        // 与主页面统一：优先只用传入的那套色板来平涂
        List<Integer> q = new ArrayList<>();
        if (palette != null) {
            for (int color : palette) {
                if (!q.contains(color)) q.add(color);
            }
        }
        // 外部色板不足时才自行聚色兜底
        if (q.size() < 2) {
            List<Integer> own = buildQuantPalette(px, 5);
            for (int color : own) {
                if (!q.contains(color)) q.add(color);
            }
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = nearest(q, px[i]);

        Bitmap flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        flat.setPixels(out, 0, w, 0, 0, w, h);

        // 连续降采样再放大 → 柔和平涂的块面（每一步都回收上一张，避免堆积）
        Bitmap s1 = Bitmap.createScaledBitmap(flat,
                Math.max(2, w / 2), Math.max(2, h / 2), true);
        if (s1 != flat && !flat.isRecycled()) flat.recycle();

        Bitmap s2 = Bitmap.createScaledBitmap(s1,
                Math.max(2, w / 3), Math.max(2, h / 3), true);
        if (s2 != s1 && !s1.isRecycled()) s1.recycle();

        Bitmap soft = Bitmap.createScaledBitmap(s2, w, h, true);
        if (soft != s2 && !s2.isRecycled()) s2.recycle();
        return soft;
    }

    /**
     * 简笔线稿：低分辨率提取结构边缘，再在输出分辨率上以粗细/长短/角度都带抖动的
     * 短笔触重绘，得到手绘感线条，而不是生硬的矢量描摹。
     */
    private static Bitmap buildSketchLines(Bitmap photo, int outW, int outH, long seed) {
        int dw = 300;
        int dh = Math.max(2, Math.round(dw * (float) photo.getHeight() / photo.getWidth()));
        Bitmap det = Bitmap.createScaledBitmap(photo, dw, dh, true);

        int n = dw * dh;
        int[] px = new int[n];
        det.getPixels(px, 0, dw, 0, 0, dw, dh);
        // 检测用的缩略图只在这一步用得到，取完像素立即释放
        if (det != photo && !det.isRecycled()) det.recycle();

        int[] gray = new int[n];
        for (int i = 0; i < n; i++) {
            gray[i] = (int) (0.299f * Color.red(px[i])
                    + 0.587f * Color.green(px[i])
                    + 0.114f * Color.blue(px[i]));
        }
        gray = boxBlur(gray, dw, dh);

        int[] gx = new int[n];
        int[] gy = new int[n];
        int[] mag = new int[n];
        for (int y = 1; y < dh - 1; y++) {
            for (int x = 1; x < dw - 1; x++) {
                int i = y * dw + x;
                int ix = gray[i + 1] - gray[i - 1];
                int iy = gray[i + dw] - gray[i - dw];
                gx[i] = ix;
                gy[i] = iy;
                mag[i] = (int) Math.sqrt((double) ix * ix + (double) iy * iy);
            }
        }

        // 自适应阈值：只保留最强的结构边缘，保证"简"
        int thr = percentile(mag, 0.90f);
        if (thr < 18) thr = 18;

        Bitmap layer = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(layer);
        Random r = new Random(seed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);

        float kx = (float) outW / dw;
        float ky = (float) outH / dh;
        float unit = (kx + ky) * 0.5f;

        int inkR = Color.red(INK), inkG = Color.green(INK), inkB = Color.blue(INK);

        // 非极大值抑制：沿梯度方向细化边缘，让每条轮廓只留一条线，
        // 否则一个 2~3px 厚的边缘会被画成好几道平行笔触，看起来毛躁。
        boolean[] thin = new boolean[n];
        for (int y = 1; y < dh - 1; y++) {
            for (int x = 1; x < dw - 1; x++) {
                int i = y * dw + x;
                if (mag[i] < thr) continue;
                float nrm = (float) Math.hypot(gx[i], gy[i]);
                if (nrm < 1e-3f) continue;
                int ax = Math.round(gx[i] / nrm);
                int ay = Math.round(gy[i] / nrm);
                if (ax == 0 && ay == 0) continue;
                int x1 = x + ax, y1 = y + ay, x2 = x - ax, y2 = y - ay;
                if (x1 < 1 || x1 >= dw - 1 || y1 < 1 || y1 >= dh - 1) continue;
                if (x2 < 1 || x2 >= dw - 1 || y2 < 1 || y2 >= dh - 1) continue;
                if (mag[i] >= mag[y1 * dw + x1] && mag[i] >= mag[y2 * dw + x2]) {
                    thin[i] = true;
                }
            }
        }
        // 补缝：让线条连续，但阈值取高一些，避免重新变粗
        boolean[] draw = new boolean[n];
        for (int y = 1; y < dh - 1; y++) {
            for (int x = 1; x < dw - 1; x++) {
                int i = y * dw + x;
                if (thin[i]) {
                    draw[i] = true;
                    continue;
                }
                int kn = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (thin[i + dy * dw + dx]) kn++;
                    }
                }
                if (kn >= 4) draw[i] = true;
            }
        }

        for (int y = 1; y < dh - 1; y++) {
            for (int x = 1; x < dw - 1; x++) {
                int i = y * dw + x;
                if (!draw[i]) continue;

                // 切线方向 = 梯度方向的垂线
                float tx = -gy[i], ty = gx[i];
                float norm = (float) Math.hypot(tx, ty);
                if (norm < 1e-3f) continue;
                tx /= norm;
                ty /= norm;

                // 轻微垂直抖动，像手画的，但不要抖成毛边
                float jitter = (r.nextFloat() - 0.5f) * unit * 0.12f;
                float X = (x + 0.5f) * kx - ty * jitter;
                float Y = (y + 0.5f) * ky + tx * jitter;

                float len = (2.4f + r.nextFloat() * 1.2f) * unit * 0.52f;
                float wid = (1.05f + r.nextFloat() * 0.5f) * unit * 0.30f;

                int alpha = 178 + r.nextInt(54);
                p.setStrokeWidth(Math.max(0.8f, wid));
                p.setColor(Color.argb(Math.min(236, alpha), inkR, inkG, inkB));
                c.drawLine(X - tx * len, Y - ty * len, X + tx * len, Y + ty * len, p);
            }
        }
        return layer;
    }

    private static int[] boxBlur(int[] src, int w, int h) {
        int[] out = new int[src.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int sum = src[i]
                        + src[i - 1] + src[i + 1]
                        + src[i - w] + src[i + w];
                out[i] = sum / 5;
            }
        }
        // 边界直接复制
        for (int x = 0; x < w; x++) {
            out[x] = src[x];
            out[(h - 1) * w + x] = src[(h - 1) * w + x];
        }
        for (int y = 0; y < h; y++) {
            out[y * w] = src[y * w];
            out[y * w + w - 1] = src[y * w + w - 1];
        }
        return out;
    }

    private static int percentile(int[] values, float p) {
        int max = 0;
        for (int v : values) if (v > max) max = v;
        if (max <= 0) return 0;
        int buckets = Math.max(2, Math.min(max + 1, 1024));
        int[] hist = new int[buckets];
        for (int v : values) {
            int b = (int) ((long) v * (buckets - 1) / max);
            hist[b]++;
        }
        int target = (int) (values.length * p);
        int acc = 0;
        for (int b = 0; b < buckets; b++) {
            acc += hist[b];
            if (acc >= target) return (int) ((long) b * max / (buckets - 1));
        }
        return max;
    }

    /**
     * 溶解软边遮罩：程序化 alpha 场（不依赖 BlurMaskFilter，保证各版本一致），
     * 低分辨率生成后双线性放大，得到线条与色块自然消散进纸面的边缘。
     */
    private static Bitmap makeDissolveMask(int outW, int outH,
                                          float dx, float dy, float sw, float sh,
                                          long seed) {
        int mw = 180;
        int mh = Math.max(2, Math.round(180f * outH / outW));
        float sx = (float) outW / mw;
        float sy = (float) outH / mh;

        float rectL = dx + sw * 0.02f;
        float rectT = dy + sh * 0.02f;
        float rectR = dx + sw * 0.98f;
        float rectB = dy + sh * 0.98f;
        float feather = Math.min(rectR - rectL, rectB - rectT) * 0.15f;

        Random r = new Random(seed);
        int nw = 9, nh = 9;
        float[][] noise = new float[nh][nw];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) noise[y][x] = r.nextFloat();
        }

        int[] px = new int[mw * mh];
        for (int y = 0; y < mh; y++) {
            float fy = (y + 0.5f) * sy;
            for (int x = 0; x < mw; x++) {
                float fx = (x + 0.5f) * sx;
                float d = Math.min(Math.min(fx - rectL, rectR - fx),
                        Math.min(fy - rectT, rectB - fy));
                float nz = smoothNoise(noise, x / (float) mw, y / (float) mh);
                float t = clamp01((d + (nz - 0.5f) * feather * 1.20f) / feather);
                float a = t * t * (3f - 2f * t);
                px[y * mw + x] = Color.argb((int) (a * 255f), 255, 255, 255);
            }
        }

        Bitmap small = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888);
        small.setPixels(px, 0, mw, 0, 0, mw, mh);
        Bitmap mask = Bitmap.createScaledBitmap(small, outW, outH, true);
        if (mask != small && !small.isRecycled()) small.recycle();
        return mask;
    }

    private static float smoothNoise(float[][] grid, float u, float v) {
        int nh = grid.length;
        int nw = grid[0].length;
        float gx = clamp01(u) * (nw - 1);
        float gy = clamp01(v) * (nh - 1);
        int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy);
        int x1 = Math.min(nw - 1, x0 + 1), y1 = Math.min(nh - 1, y0 + 1);
        float tx = gx - x0, ty = gy - y0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx;
        float b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx;
        return a * (1 - ty) + b * ty;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // ====================================================================
    //  纸张与工具
    // ====================================================================

    private static void drawPaper(Canvas c, int W, int H, long seed) {
        c.drawColor(PAPER);

        Random r = new Random(seed);
        Paint blot = new Paint(Paint.ANTI_ALIAS_FLAG);
        blot.setColor(Color.argb(9, Color.red(PAPER_DEEP), Color.green(PAPER_DEEP), Color.blue(PAPER_DEEP)));
        for (int i = 0; i < 7; i++) {
            float cx = r.nextFloat() * W;
            float cy = r.nextFloat() * H;
            float rr = (0.20f + r.nextFloat() * 0.30f) * W;
            c.drawCircle(cx, cy, rr, blot);
        }

        Paint grain = new Paint();
        grain.setShader(new BitmapShader(makeGrainTile(168, seed),
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, W, H, grain);
    }

    private static Bitmap makeGrainTile(int size, long seed) {
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Random r = new Random(seed);
        int[] px = new int[size * size];
        for (int i = 0; i < px.length; i++) {
            int v = r.nextInt(256);
            int a = r.nextInt(9);
            px[i] = Color.argb(a, v, v, v);
        }
        tile.setPixels(px, 0, size, 0, 0, size, size);
        return tile;
    }

    /** 图像自身聚色（采样 + k-means），保证色块/涂色忠于原图 */
    private static List<Integer> buildQuantPalette(int[] px, int k) {
        int step = Math.max(1, px.length / 3000);
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < px.length; i += step) samples.add(px[i]);
        if (samples.isEmpty()) samples.add(Color.GRAY);

        int n = Math.max(1, Math.min(k, samples.size()));
        Random rnd = new Random(7);
        int[][] cen = new int[n][3];
        for (int i = 0; i < n; i++) {
            int c = samples.get(rnd.nextInt(samples.size()));
            cen[i] = new int[]{Color.red(c), Color.green(c), Color.blue(c)};
        }

        for (int iter = 0; iter < 8; iter++) {
            long[] sr = new long[n], sg = new long[n], sb = new long[n];
            int[] cnt = new int[n];
            for (int c : samples) {
                int bi = 0;
                double bd = Double.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    double d = sq(Color.red(c) - cen[j][0])
                            + sq(Color.green(c) - cen[j][1])
                            + sq(Color.blue(c) - cen[j][2]);
                    if (d < bd) { bd = d; bi = j; }
                }
                sr[bi] += Color.red(c);
                sg[bi] += Color.green(c);
                sb[bi] += Color.blue(c);
                cnt[bi]++;
            }
            for (int j = 0; j < n; j++) {
                if (cnt[j] == 0) continue;
                cen[j] = new int[]{(int) (sr[j] / cnt[j]), (int) (sg[j] / cnt[j]), (int) (sb[j] / cnt[j])};
            }
        }

        List<Integer> out = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            out.add(Color.rgb(cen[j][0], cen[j][1], cen[j][2]));
        }
        return out;
    }

    private static int nearest(List<Integer> colors, int target) {
        int best = colors.get(0);
        double min = Double.MAX_VALUE;
        for (int c : colors) {
            double d = colorDistance(c, target);
            if (d < min) { min = d; best = c; }
        }
        return best;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double colorDistance(int c1, int c2) {
        return Math.sqrt(sq(Color.red(c1) - Color.red(c2))
                + sq(Color.green(c1) - Color.green(c2))
                + sq(Color.blue(c1) - Color.blue(c2)));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
