package com.example.photopalettepro.film;

import android.graphics.Color;

/**
 * 胶片边框的「片基」预设。
 *
 * <p>每一个预设 = 一种真实胶片冲洗后片基的样子，外加它<b>摆在什么台面上</b>。
 * 后一半同样重要：照片外面那圈片基是<b>半透明</b>的（见
 * {@link com.example.photopalettepro.FilmBorderRenderer#FILM_ALPHA}），
 * 台面的颜色会透上来。所以台面必须和片基拉开明度差，否则胶片就"消失"在背景里，
 * 只剩下几根描边——这正是这套预设里 {@code backdrop} 每个都挑过的原因。
 *
 * <p>颜色分两组，别混用：
 * <ul>
 *   <li>{@code base} / {@code ink} / {@code accent} —— 印在<b>胶片上</b>的
 *       （照片外面的发丝线、片基纹理）；</li>
 *   <li>{@code backdrop} / {@code captionInk} / {@code captionAccent} ——
 *       用在<b>台面上</b>的。片边文字在最后一截胶片<b>下方</b>，也就是落在台面上，
 *       所以它必须按台面的明度来选色，不能沿用片基那套。</li>
 * </ul>
 */
public final class FilmStock {

    // ---- 预设名（同时作为弹出菜单的选项文本）----

    /** 暖白片基 + 橙字，最常见的彩色负片扫描观感 */
    public static final String STYLE_CLASSIC = "经典白框";
    /** 偏金的纸白 + 棕字，日光型彩色负片 */
    public static final String STYLE_KODAK = "柯达金";
    /** 冷绿白 + 绿字，日系清新 */
    public static final String STYLE_FUJI = "富士绿";
    /** 近黑片基 + 暖金字，电影卷夜戏 */
    public static final String STYLE_NOIR = "暗夜黑";
    /** 中性灰片基 + 黑字，黑白负片 */
    public static final String STYLE_SILVER = "银盐黑白";

    /** 弹出菜单顺序（由浅到深，视觉上有连贯的过渡） */
    public static final String[] STYLE_OPTIONS = {
            STYLE_CLASSIC, STYLE_KODAK, STYLE_FUJI, STYLE_NOIR, STYLE_SILVER
    };

    /** 预设名 */
    public final String style;
    /** 片基主色 */
    public final int base;
    /** 片基暗部（大面积晕染用） */
    public final int baseDeep;
    /** 印在片基上的正文色（照片外的发丝线） */
    public final int ink;
    /** 印在片基上的强调色 */
    public final int accent;
    /** 台面（画布底衬）颜色：胶片摆在上面的那一层 */
    public final int backdrop;
    /** 台面上的正文色（片边文字的下一行） */
    public final int captionInk;
    /** 台面上的强调色（胶片型号 + 帧号） */
    public final int captionAccent;
    /** 装饰性胶片型号（输入框留空时使用） */
    public final String decorativeStock;
    /** 装饰性片幅规格（整组参数都留空时使用） */
    public final String decorativeSpec;
    /** 装饰性帧号 */
    public final String decorativeFrame;
    /** 颗粒强度（alpha，0 = 无颗粒） */
    public final int grainAlpha;

    private FilmStock(String style, int base, int baseDeep, int ink, int accent,
                      int backdrop, int captionInk, int captionAccent,
                      String decorativeStock, String decorativeSpec,
                      String decorativeFrame, int grainAlpha) {
        this.style = style;
        this.base = base;
        this.baseDeep = baseDeep;
        this.ink = ink;
        this.accent = accent;
        this.backdrop = backdrop;
        this.captionInk = captionInk;
        this.captionAccent = captionAccent;
        this.decorativeStock = decorativeStock;
        this.decorativeSpec = decorativeSpec;
        this.decorativeFrame = decorativeFrame;
        this.grainAlpha = grainAlpha;
    }

    private static final FilmStock CLASSIC = new FilmStock(
            STYLE_CLASSIC,
            Color.rgb(245, 242, 235), Color.rgb(216, 209, 194),
            Color.rgb(59, 55, 48), Color.rgb(192, 98, 42),
            // 深暖炭灰台面：奶油色片基压上去最跳，像暗房里摊开的相纸
            Color.rgb(43, 41, 38), Color.rgb(232, 227, 217), Color.rgb(224, 138, 71),
            "PORTRA 400", "35MM · 135 · COLOR NEGATIVE", "12A", 14);

    private static final FilmStock KODAK = new FilmStock(
            STYLE_KODAK,
            Color.rgb(240, 223, 188), Color.rgb(203, 170, 115),
            Color.rgb(74, 55, 24), Color.rgb(156, 91, 18),
            // 偏棕的台面，跟金色片基同色系，观感最"温"
            Color.rgb(51, 44, 34), Color.rgb(236, 223, 200), Color.rgb(217, 163, 74),
            "GOLD 200", "35MM · 135 · COLOR NEGATIVE", "24", 16);

    private static final FilmStock FUJI = new FilmStock(
            STYLE_FUJI,
            Color.rgb(237, 242, 235), Color.rgb(199, 212, 196),
            Color.rgb(44, 58, 48), Color.rgb(27, 122, 69),
            // 冷调深灰，衬日系的清冷
            Color.rgb(35, 40, 42), Color.rgb(220, 228, 222), Color.rgb(63, 169, 106),
            "SUPERIA 400", "35MM · 135 · COLOR NEGATIVE", "07A", 14);

    private static final FilmStock NOIR = new FilmStock(
            STYLE_NOIR,
            Color.rgb(18, 18, 20), Color.rgb(0, 0, 0),
            Color.rgb(239, 239, 239), Color.rgb(217, 174, 98),
            // 唯一一个亮台面：近黑片基只有压在灯箱上才看得见，
            // 这也是暗底负片最经典的观看方式
            Color.rgb(214, 209, 198), Color.rgb(42, 39, 35), Color.rgb(169, 118, 31),
            "CINE 800T", "35MM · 135 · TUNGSTEN", "31", 20);

    private static final FilmStock SILVER = new FilmStock(
            STYLE_SILVER,
            Color.rgb(203, 203, 199), Color.rgb(168, 168, 164),
            Color.rgb(60, 60, 60), Color.rgb(20, 20, 20),
            // 中灰片基配近黑台面：明度差足够，又不至于像暗夜黑那样两极
            Color.rgb(38, 38, 42), Color.rgb(222, 222, 226), Color.rgb(198, 198, 206),
            "HP5 PLUS 400", "35MM · 135 · B&W NEGATIVE", "18A", 22);

    /** 按预设名取片基；名字不认识时回落到经典白框。 */
    public static FilmStock of(String style) {
        if (style == null) return CLASSIC;
        switch (style) {
            case STYLE_KODAK:
                return KODAK;
            case STYLE_FUJI:
                return FUJI;
            case STYLE_NOIR:
                return NOIR;
            case STYLE_SILVER:
                return SILVER;
            case STYLE_CLASSIC:
            default:
                return CLASSIC;
        }
    }

    /** 片基是不是暗底——决定纹理要"提亮"还是"压暗"。 */
    public boolean isDarkBase() {
        return luminance(base) < 0.45;
    }

    /** 台面是不是暗底——决定片边文字用什么色。 */
    public boolean isDarkBackdrop() {
        return luminance(backdrop) < 0.5;
    }

    private static double luminance(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
    }

    /**
     * 齿孔的边缘描边色。
     *
     * <p>孔现在是<b>真正镂空</b>的（透出台面），所以不再需要填充色，
     * 只需要一圈极淡的边把"冲压"的厚度交代出来。
     */
    public int holeEdgeColor() {
        return isDarkBase() ? Color.argb(38, 255, 255, 255) : Color.argb(26, 0, 0, 0);
    }
}
