package com.example.photopalettepro;

import com.example.photopalettepro.film.FilmStock;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 胶片边框配置。
 *
 * <p>与 Zine 明信片不同，胶片边框<b>完全不依赖取色逻辑</b>——它只是给照片加一圈
 * 片基，所以本配置里没有任何色板字段。
 *
 * <p>关于文案的规则（与产品确认过）：
 * <ol>
 *   <li>导入照片后用 EXIF 自动预填；</li>
 *   <li>EXIF 读不到、或用户把输入框删空了 —— 该字段一律回落到
 *       {@link FilmStock} 里对应的<b>装饰性文案</b>，绝不显示空白。</li>
 * </ol>
 * 因此 {@link #resolveStock} / {@link #resolveSpec} 是渲染前必须走的一道归一化，
 * 渲染器只认它们的结果。
 */
public class FilmBorderConfig {

    // ---- 边框宽度（弹出菜单选项文本）----

    public static final String WIDTH_NARROW = "窄边框";
    public static final String WIDTH_NORMAL = "标准边框";
    public static final String WIDTH_WIDE = "宽边框";

    public static final String[] WIDTH_OPTIONS = {WIDTH_NARROW, WIDTH_NORMAL, WIDTH_WIDE};

    /** 胶片风格（取值见 {@link FilmStock#STYLE_OPTIONS}） */
    public String style = FilmStock.STYLE_CLASSIC;

    /** 边框宽度（取值见 {@link #WIDTH_OPTIONS}） */
    public String width = WIDTH_NORMAL;

    /** 是否绘制上下（或左右）的胶片齿孔 */
    public boolean sprocketHoles = true;

    /**
     * 是否在胶片下方绘制片边文字（型号 / 帧号 / 机身镜头参数）。
     *
     * <p>关掉之后画布<b>只留胶片本身</b>：下方的文字块连同它占的高度一起去掉，
     * 上下留白改成对称的——否则底边会多出一截没有内容的空白，
     * 整张图看着像"裁歪了"。
     */
    public boolean showCaption = true;

    /** 是否绘制帧号（纯装饰，如 12A） */
    public boolean frameNumber = true;

    /**
     * 是否为多张合成。
     *
     * <p>多张照片的参数往往不是一套（不同时间、不同镜头、甚至不同机器），
     * 硬拼成一行「机身 · 镜头 · 曝光 · 日期」反而处处是错的。所以多张时
     * <b>只保留第一张的机身</b>，其余交给本预设的装饰文案。
     */
    public boolean multiFrame = false;

    /** 胶片型号，例：PORTRA 400 */
    public String stock = "";
    /** 机身，例：SONY A7M4 */
    public String camera = "";
    /** 镜头，例：FE 50MM F1.8 */
    public String lens = "";
    /** 曝光组合，例：1/200s · f/1.8 · ISO100 */
    public String exposure = "";
    /** 拍摄日期，例：2026.09.03 */
    public String date = "";

    public FilmBorderConfig() {
    }

    /** 片基预设。 */
    public FilmStock stockPreset() {
        return FilmStock.of(style);
    }

    /**
     * 胶片型号行的最终文案：输入框有内容就用输入框的，空则用预设的装饰型号。
     */
    public String resolveStock() {
        String trimmed = stock == null ? "" : stock.trim();
        return trimmed.isEmpty() ? stockPreset().decorativeStock : trimmed;
    }

    /**
     * 参数行的最终文案。
     *
     * <p>单张时：四个字段（机身 / 镜头 / 曝光 / 日期）只要有一个填了，
     * 就只显示填了的那些，用 {@code ·} 连起来——因为此时「有什么就说什么」
     * 比凑满一行更诚实。四个全空（含「填过又删空」）时，整行回落到预设的装饰性片幅规格。
     *
     * <p>多张时（见 {@link #multiFrame}）：只认第一张的机身，后面接装饰性片幅规格。
     */
    public String resolveSpec() {
        if (multiFrame) {
            String decorative = stockPreset().decorativeSpec;
            String device = camera == null ? "" : camera.trim();
            return device.isEmpty() ? decorative : device + " · " + decorative;
        }

        StringBuilder sb = new StringBuilder();
        append(sb, camera);
        append(sb, lens);
        append(sb, exposure);
        append(sb, date);
        return sb.length() == 0 ? stockPreset().decorativeSpec : sb.toString();
    }

    /** 帧号：纯装饰，不可编辑。 */
    public String resolveFrameNumber() {
        return stockPreset().decorativeFrame;
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null) return;
        String trimmed = part.trim();
        if (trimmed.isEmpty()) return;
        if (sb.length() > 0) sb.append(" · ");
        sb.append(trimmed);
    }

    // ====================================================================
    //  JSON 读写（用于 Room 历史记录）
    // ====================================================================

    /**
     * 打包成 JSON，供导出历史持久化。
     *
     * <p>放在配置类里而不是 Activity 里，是因为它是纯数据转换、不碰任何 View，
     * 也就能被直接测——「再次导入同一批照片能不能原样恢复」全看这一段。
     */
    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("style", style);
            o.put("width", width);
            o.put("sprocketHoles", sprocketHoles);
            o.put("showCaption", showCaption);
            o.put("frameNumber", frameNumber);
            o.put("stock", stock);
            o.put("camera", camera);
            o.put("lens", lens);
            o.put("exposure", exposure);
            o.put("date", date);
            return o.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * 从历史记录恢复。
     *
     * <p>只覆盖 JSON 里<b>确实存在</b>的字段：老记录没有某个键时保留当前值，
     * 而不是把它清空——「缺字段」和「字段是空串」是两回事。
     *
     * <p>约定：<b>返回 {@code null} 表示「没有可用数据」</b>。
     * 入参为空、解析失败、JSON 不是对象，都会走这一条；不会抛异常出去。
     *
     * @param json     之前 {@link #toJson()} 的结果
     * @param fallback 没有可用数据时返回它（可以为 null）
     */
    public static FilmBorderConfig fromJson(String json, FilmBorderConfig fallback) {
        if (json == null || json.trim().isEmpty()) return fallback;

        FilmBorderConfig cfg = fallback == null ? new FilmBorderConfig() : fallback;
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("style")) cfg.style = o.optString("style", cfg.style);
            if (o.has("width")) cfg.width = o.optString("width", cfg.width);
            if (o.has("sprocketHoles")) cfg.sprocketHoles = o.optBoolean("sprocketHoles", cfg.sprocketHoles);
            if (o.has("showCaption")) cfg.showCaption = o.optBoolean("showCaption", cfg.showCaption);
            if (o.has("frameNumber")) cfg.frameNumber = o.optBoolean("frameNumber", cfg.frameNumber);
            if (o.has("stock")) cfg.stock = o.optString("stock", cfg.stock);
            if (o.has("camera")) cfg.camera = o.optString("camera", cfg.camera);
            if (o.has("lens")) cfg.lens = o.optString("lens", cfg.lens);
            if (o.has("exposure")) cfg.exposure = o.optString("exposure", cfg.exposure);
            if (o.has("date")) cfg.date = o.optString("date", cfg.date);
        } catch (JSONException e) {
            return fallback;
        }
        return cfg;
    }
}

