package com.example.photopalettepro.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 液态玻璃。
 *
 * <h3>它的本质是折射，不是"白色卡片"</h3>
 *
 * <p>做法照搬 <a href="https://github.com/rdev/liquid-glass-react">rdev/liquid-glass-react</a>
 * （作者说明改编自 shuding/liquid-glass）：
 *
 * <ol>
 *   <li>用<b>圆角矩形 SDF</b> 算出每个像素"离边缘还有多远"；</li>
 *   <li>把这个距离过一遍 smoothstep，得到一个 0~1 的收缩系数——
 *       <b>越靠边收缩越狠</b>；</li>
 *   <li>用这个系数把采样坐标朝中心拉，于是边缘处采到的是更靠里的背景，
 *       看上去就是光被玻璃"折"了一下；</li>
 *   <li>R/G/B 三个通道用略有差别的收缩量，边缘就有了色散彩边。</li>
 * </ol>
 *
 * <p>参考实现把位移预计算成一张贴图（R 存 x 位移、G 存 y 位移）交给
 * {@code feDisplacementMap}；这里同样预计算，只是消费者换成 AGSL 的
 * {@link RuntimeShader}——位移贴图在 CPU 上生成、可以单测，
 * 着色器只剩"采样 + 偏移"这几行，出错的面小得多。
 *
 * <h3>分层</h3>
 *
 * <p>折射只发生在<b>边缘一圈</b>，这是物理事实也是性能上必须的：
 * 整张卡片跑着色器在滚动时要逐帧重算上百万像素。所以：
 * <ul>
 *   <li>中间大片 —— 直接贴背景快照（已经糊过）+ 一层白玻璃，几乎不要钱；</li>
 *   <li>边缘一圈 —— 才跑折射着色器；</li>
 *   <li>顶边高光 + 发丝描边 —— 普通绘制，光"折"进来的那一道。</li>
 * </ul>
 *
 * <p>API 33 以下没有 AGSL，退回"半透明 + 高光 + 描边"的老做法，
 * 观感差一档但不会缺东西。
 */
public class LiquidGlassDrawable extends Drawable {

    /**
     * 折射发生在边缘多宽的一圈里（相对卡片短边的比例）。
     *
     * <p>0.18 是错的：卡片短边 1200px 时那一圈有 216px 厚，
     * 相当于给卡片套了一个粗相框——屏幕上就是"里面一块、外面一圈"的方块感。
     * 折射是边缘现象，几个百分点就够。
     */
    private static final float EDGE_BAND_RATIO = 0.055f;

    /** 边缘最多把采样点往中心拉多少像素（对应参考实现的 displacementScale）。 */
    private static final float DISPLACEMENT_SCALE = 26f;

    /** 色散强度（对应参考实现的 aberrationIntensity）。 */
    private static final float ABERRATION = 0.045f;

    // ---- 高光与内阴影：数值照抄参考实现的 box-shadow 与渐变 stops ----

    /** 内阴影上沿的白内发光：rgba(255,255,255,.25) */
    private static final int LIGHT_INNER = 0x40FFFFFF;
    /** 内阴影下沿的黑内阴影：rgba(0,0,0,.35) */
    private static final int DARK_INNER = 0x59000000;
    /** 对角高光带的暗端：rgba(255,255,255,.32) */
    private static final int SPECULAR = 0x52FFFFFF;
    /** 对角高光带的亮端：rgba(255,255,255,.6) */
    private static final int SPECULAR_BRIGHT = 0x99FFFFFF;

    /** 位移贴图的分辨率。SDF 是光滑的，放大插值足够，不需要按卡片像素来。 */
    private static final int DISPLACEMENT_MAP_SIZE = 96;

    private static final String SHADER = ""
            + "uniform shader uBackdrop;\n"
            + "uniform shader uDisplacement;\n"
            + "uniform float2 uOrigin;\n"
            + "uniform float  uAmount;\n"
            + "uniform float  uAberration;\n"
            + "half4 main(float2 coord) {\n"
            + "    half4 d = uDisplacement.eval(coord);\n"
            + "    float2 dir = (float2(d.r, d.g) - 0.5) * 2.0;\n"
            + "    float2 base = uOrigin + coord;\n"
            + "    float2 oR = dir * uAmount * (1.0 + uAberration);\n"
            + "    float2 oG = dir * uAmount;\n"
            + "    float2 oB = dir * uAmount * (1.0 - uAberration);\n"
            + "    half r = uBackdrop.eval(base + oR).r;\n"
            + "    half g = uBackdrop.eval(base + oG).g;\n"
            + "    half b = uBackdrop.eval(base + oB).b;\n"
            + "    return half4(r, g, b, 1.0);\n"
            + "}\n";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path ringPath = new Path();
    private final Path specularPath = new Path();
    private final Path ringInner = new Path();
    private final Matrix shaderMatrix = new Matrix();
    private final Matrix backdropMatrix = new Matrix();

    private final float cornerRadius;
    private final int tintColor;
    private final int highlightColor;
    private final int borderColor;
    private final boolean shaderSupported;

    private Bitmap backdrop;
    private Bitmap displacementMap;
    private RuntimeShader runtimeShader;

    public LiquidGlassDrawable(Context context, float cornerRadiusDp, int tintColor,
                               int highlightColor, int borderColor) {
        float density = context.getResources().getDisplayMetrics().density;
        this.cornerRadius = cornerRadiusDp * density;
        this.tintColor = tintColor;
        this.highlightColor = highlightColor;
        this.borderColor = borderColor;
        this.shaderSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /** 换一张背景快照（照片换了、或者一开始还没有）。 */
    public void setBackdrop(@Nullable Bitmap bitmap) {
        this.backdrop = bitmap;
        invalidateSelf();
    }

    // ====================================================================
    //  绘制
    // ====================================================================

    @Override
    public void draw(@NonNull Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        if (bounds.width() <= 1f || bounds.height() <= 1f) return;
        if (!bounds.equals(rect)) {
            rect.set(bounds);
            rebuildPaths();
        }

        float radius = Math.min(cornerRadius, Math.min(rect.width(), rect.height()) / 2f);

        // 1. 背景：快照（已饱和化 + 轻糊）。
        //    参考实现这里是 backdrop-filter: blur(12~44px) saturate(140%)。
        drawBackdrop(canvas, radius);

        // 2. 边缘一圈：跑折射。参考实现用 SVG 滤镜做同一件事，
        //    而且【只用边缘】——中心那份是原图、不位移的（CENTER_ORIGINAL）。
        if (ENABLE_REFRACTION) {
            drawRefractionRing(canvas, radius);
        }

        // 3. 白玻璃盖一层，但不能盖掉边缘高光，所以放在高光之前
        canvas.drawRoundRect(rect, radius, radius, tintPaint());

        // 4. 内阴影：上沿白内发光 + 下沿黑内阴影。
        //    box-shadow: 0 1px 3px rgba(255,255,255,.25) inset,
        //                0 1px 4px rgba(0,0,0,.35)
        //    这一对是"玻璃有厚度"的来源，少了它就是一张平的半透明纸。
        drawInnerShadows(canvas, radius);

        // 5. 对角高光带 + 极细内环
        drawSpecularRing(canvas, radius);
    }

    /**
     * 玻璃的厚度感：上沿白内发光 + 下沿黑内阴影。
     *
     * <p>用「整卡铺一条顶亮下透的渐变」实现，而不是「裁一条带子再画」——
     * 同样是 {@code drawRoundRect}，圆角和其他图层一致，不需要 {@code clipPath}。
     */
    private void drawInnerShadows(Canvas canvas, float radius) {
        float depth = Math.max(2f, rect.width() * 0.012f);

        // 上沿：亮（光从上面来）
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.top + depth,
                LIGHT_INNER, LIGHT_INNER & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 下沿：暗（玻璃下缘把光挡住）
        paint.setShader(new LinearGradient(0, rect.bottom, 0, rect.bottom - depth * 1.3f,
                DARK_INNER, DARK_INNER & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, paint);

        paint.setShader(null);
    }

    /**
     * 对角高光带 + 极细内环。
     *
     * <p>参考实现里最出效果的一层：一圈 1.5px 的环，环里填一条 135° 白渐变，
     * 亮度峰值落在 33%~66% 那一段，两端透明——于是上左亮、下右收，
     * 读起来就是"玻璃边沿把斜射的光收住了"。再叠 0.5px 白色内环做分界。
     */
    private void drawSpecularRing(Canvas canvas, float radius) {
        float ringWidth = Math.max(1.5f, rect.width() * 0.004f);
        buildRing(specularPath, radius, ringWidth);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setShader(new LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,     // 135°
                new int[]{
                        SPECULAR & 0x00FFFFFF,
                        SPECULAR,
                        SPECULAR_BRIGHT,
                        SPECULAR & 0x00FFFFFF,
                },
                new float[]{0f, 0.33f, 0.66f, 1f},
                Shader.TileMode.CLAMP));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 参考实现用的是 mix-blend-mode: overlay
            ring.setBlendMode(BlendMode.OVERLAY);
        }
        canvas.drawPath(specularPath, ring);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, rect.width() * 0.0012f));
        paint.setColor(borderColor);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    /** 圆角矩形挖掉内缩 {@code width} 的那块 = 一圈环。 */
    private void buildRing(Path out, float radius, float width) {
        out.reset();
        out.addRoundRect(rect, radius, radius, Path.Direction.CW);

        RectF inner = new RectF(rect);
        inner.inset(width, width);
        Path hole = new Path();
        float innerRadius = Math.max(0f, radius - width);
        hole.addRoundRect(inner, innerRadius, innerRadius, Path.Direction.CW);

        out.op(hole, Path.Op.DIFFERENCE);
    }

    private Paint tintPaint() {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tintColor);
        return paint;
    }

    /**
     * 把背景快照按卡片在屏幕上的位置贴过来。
     *
     * <p><b>这里不用 {@code canvas.clipPath}。</b>圆角交给 {@code drawRoundRect}——
     * 硬件加速的画布上 {@code clipPath} 的圆角不做抗锯齿，滑动时那一圈会露出
     * 生硬的直角/锯齿边（用户圈出来的就是这个）。改用 Path + BitmapShader 填充，
     * 圆角由 drawRoundRect 保证，和其它图层走同一条抗锯齿路径。
     */
    private void drawBackdrop(Canvas canvas, float radius) {
        Bitmap source = backdrop;
        if (source == null || source.isRecycled()) return;

        int[] location = viewLocation();
        if (location == null) return;

        // 快照是全屏的缩略图：按同样比例把卡片那块裁出来
        float scaleX = source.getWidth() / (float) screenWidth();
        float scaleY = source.getHeight() / (float) screenHeight();
        if (scaleX <= 0f || scaleY <= 0f) return;

        RectF src = new RectF(
                location[0] * scaleX, location[1] * scaleY,
                (location[0] + rect.width()) * scaleX,
                (location[1] + rect.height()) * scaleY);
        if (src.width() <= 0f || src.height() <= 0f) return;

        BitmapShader shader = new BitmapShader(source,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float sx = rect.width() / src.width();
        float sy = rect.height() / src.height();
        Matrix matrix = new Matrix();
        matrix.setScale(sx, sy);
        matrix.postTranslate(rect.left - src.left * sx, rect.top - src.top * sy);
        shader.setLocalMatrix(matrix);

        paint.setShader(shader);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
    }

    /** 边缘一圈跑折射着色器。 */
    private void drawRefractionRing(Canvas canvas, float radius) {
        if (!shaderSupported) return;
        if (!ensureShader()) return;

        int save = canvas.save();
        canvas.clipPath(ringPath);

        // 位移贴图按卡片尺寸铺开
        if (displacementMap != null && !displacementMap.isRecycled()) {
            // 等比铺满 + 居中裁切（对应参考实现的 preserveAspectRatio="xMidYMid slice"）。
            // 直接拉伸的话，方形卡片上算出来的 SDF 会被拽成椭圆——
            // 竖长卡片左右两侧的弯折带会比上下窄一半，四角也不对称。
            BitmapShader mapShader = new BitmapShader(displacementMap,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float[] slice = slicePlacement(rect.width(), rect.height(),
                    displacementMap.getWidth(), displacementMap.getHeight());
            shaderMatrix.setScale(slice[0], slice[0]);
            shaderMatrix.postTranslate(slice[1], slice[2]);
            mapShader.setLocalMatrix(shaderMatrix);
            runtimeShader.setInputShader("uDisplacement", mapShader);
        }

        int[] location = viewLocation();
        if (location == null) {
            canvas.restoreToCount(save);
            return;
        }
        if (backdrop != null && !backdrop.isRecycled()) {
            // ⚠️ 必须给背景贴图一个缩放矩阵。
            //
            // 快照是全屏的 1/6，而 uOrigin / coord 都是<b>屏幕像素</b>。
            // 不给矩阵的话，BitmapShader 默认按 1:1 铺，于是每次采样都落在
            // 贴图外面 → CLAMP 到边缘那一个像素 → 整圈变成一个死板的纯色，
            // 真机上就是"卡片里多出一块方形色斑"。
            BitmapShader backdropShader = new BitmapShader(backdrop,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float[] scale = backdropScale(
                    screenWidth(), screenHeight(),
                    backdrop.getWidth(), backdrop.getHeight());
            backdropMatrix.setScale(scale[0], scale[1]);
            backdropShader.setLocalMatrix(backdropMatrix);
            runtimeShader.setInputShader("uBackdrop", backdropShader);
        }
        runtimeShader.setFloatUniform("uOrigin", location[0], location[1]);
        runtimeShader.setFloatUniform("uAmount", DISPLACEMENT_SCALE);
        runtimeShader.setFloatUniform("uAberration", ABERRATION);

        paint.setShader(runtimeShader);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rect, paint);

        canvas.restoreToCount(save);
    }

    private boolean ensureShader() {
        if (runtimeShader != null) return true;
        try {
            runtimeShader = new RuntimeShader(SHADER);
            displacementMap = buildDisplacementMap(DISPLACEMENT_MAP_SIZE, DISPLACEMENT_MAP_SIZE);
            return true;
        } catch (Throwable t) {
            // 某些设备/沙箱没有 AGSL：退回纯玻璃观感，不要崩
            runtimeShader = null;
            return false;
        }
    }

    private void rebuildPaths() {
        float radius = Math.min(cornerRadius, Math.min(rect.width(), rect.height()) / 2f);
        float band = Math.min(rect.width(), rect.height()) * EDGE_BAND_RATIO;

        ringPath.reset();
        ringPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        RectF inner = new RectF(rect);
        inner.inset(band, band);
        ringInner.reset();
        ringInner.addRoundRect(inner, Math.max(0f, radius - band), Math.max(0f, radius - band),
                Path.Direction.CW);

        // 圆角矩形挖掉里面那块 = 边缘一圈
        ringPath.op(ringInner, Path.Op.DIFFERENCE);
    }

    /**
     * 本视图相对<b>根布局</b>的位置。
     *
     * <p>用根布局而不是窗口：背景快照是按根布局的尺寸拍的，
     * 而根布局有 {@code fitsSystemWindows="true"}`，它的原点比窗口原点低一个状态栏。
     * 两者混用会整体错开一条状态栏的高度。
     */
    private int[] viewLocation() {
        if (!(getCallback() instanceof View)) return null;
        View view = (View) getCallback();
        View root = view.getRootView();
        if (root == null) return null;

        int[] location = new int[2];
        int[] rootLocation = new int[2];
        view.getLocationInWindow(location);
        root.getLocationInWindow(rootLocation);
        location[0] -= rootLocation[0];
        location[1] -= rootLocation[1];
        return location;
    }

    private int screenWidth() {
        if (getCallback() instanceof View) {
            return ((View) getCallback()).getRootView().getWidth();
        }
        return rect.width() > 0 ? Math.round(rect.width()) : 1;
    }

    private int screenHeight() {
        if (getCallback() instanceof View) {
            return ((View) getCallback()).getRootView().getHeight();
        }
        return rect.height() > 0 ? Math.round(rect.height()) : 1;
    }

    // ====================================================================
    //  位移贴图（纯计算，可单测）
    // ====================================================================

    /**
     * 生成位移贴图：R 存 x 位移、G 存 y 位移，0.5 = 不位移。
     *
     * <p>和参考实现同一套数学：
     * <pre>
     *   d      = 圆角矩形 SDF(u, v)          // 负数在内部，0 在边上
     *   bend   = smoothstep(0.8, 0, d - 0.15) // 越靠边越大
     *   scaled = smoothstep(0, 1, bend)       // 0 = 贴边，1 = 中心
     *   采样点  = (uv - 0.5) * scaled + 0.5    // 朝中心收缩 —— 这就是折射
     * </pre>
     *
     * <p>位移量取"原位置 − 收缩后的位置"，归一化到 0~1 后写进通道。
     *
     * @return 边长 {@code size} 的贴图；参数非法时返回 null
     */
    @Nullable
    public static Bitmap buildDisplacementMap(int size, int sizeIgnored) {
        return buildDisplacementMap(size);
    }

    @Nullable
    public static Bitmap buildDisplacementMap(int size) {
        if (size <= 0) return null;

        Bitmap map = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[size * size];

        float maxAbs = 0f;
        float[] dx = new float[pixels.length];
        float[] dy = new float[pixels.length];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // 归一化到 -0.5 ~ 0.5，和参考实现一致
                float u = (x + 0.5f) / size;
                float v = (y + 0.5f) / size;
                float ix = u - 0.5f;
                float iy = v - 0.5f;

                float d = roundedRectSdf(ix, iy);
                float bend = smoothStep(0.8f, 0f, d - 0.15f);
                float scaled = smoothStep(0f, 1f, bend);

                // 收缩后的采样位置
                float sampledU = ix * scaled + 0.5f;
                float sampledV = iy * scaled + 0.5f;

                float offsetX = sampledU - u;
                float offsetY = sampledV - v;

                int index = y * size + x;
                dx[index] = offsetX;
                dy[index] = offsetY;
                maxAbs = Math.max(maxAbs, Math.max(Math.abs(offsetX), Math.abs(offsetY)));
            }
        }

        if (maxAbs <= 0f) maxAbs = 1f;

        for (int i = 0; i < pixels.length; i++) {
            float r = clamp01(dx[i] / maxAbs * 0.5f + 0.5f);
            float g = clamp01(dy[i] / maxAbs * 0.5f + 0.5f);
            pixels[i] = Color.argb(255,
                    Math.round(r * 255f), Math.round(g * 255f), Math.round(g * 255f));
        }

        map.setPixels(pixels, 0, size, 0, 0, size, size);
        return map;
    }

    /**
     * 圆角矩形的有符号距离场。
     *
     * <p>参考实现把它写死在 {@code roundedRectSDF(x, y, 0.3, 0.2, 0.6)} 这一组数上，
     * 这里取方形卡片对应的 0.3 / 0.3 / 0.6。
     *
     * @return 负数在形状内部、0 在形状边上、正数在形状外
     */
    public static float roundedRectSdf(float ix, float iy) {
        // 归一化空间里元素是 ±0.5；这三个数是内切形状的一半尺寸
        final float halfWidth = 0.30f;
        final float halfHeight = 0.30f;
        final float radius = 0.60f;

        // 注意是「加」radius，不是减——和标准 SDF 的写法不同，
        // 因为这里的 radius 描述的是内切形状的圆角，比半宽还大
        float qx = Math.abs(ix) - halfWidth + radius;
        float qy = Math.abs(iy) - halfHeight + radius;
        float outside = (float) Math.hypot(Math.max(qx, 0f), Math.max(qy, 0f));
        return Math.min(Math.max(qx, qy), 0f) + outside - radius;
    }

    /**
     * 背景快照的缩放系数：把 1/6 尺寸的快照铺满屏幕。
     *
     * <p>抽出来是因为它是上面那个"方形色斑"bug 的正中心：
     * 着色器拿屏幕坐标去采样快照，两者的尺度必须对齐，否则每次采样都落在贴图外，
     * CLAMP 之后整圈变成一个死板的纯色。
     *
     * @return {@code {scaleX, scaleY}}，非法输入时返回 1
     */
    /**
     * 位移贴图怎么铺到卡片上：等比放大到<b>铺满</b>，再居中（slice 语义）。
     *
     * <p>对应参考实现 {@code feImage} 上的 {@code preserveAspectRatio="xMidYMid slice"}。
     * 位移贴图是方的，卡片是长的——直接拉伸会把方形空间里算好的 SDF 拽成椭圆，
     * 于是左右两侧的弯折带比上下窄一半。
     *
     * @return {@code {scale, dx, dy}}
     */
    public static float[] slicePlacement(float cardW, float cardH, float mapW, float mapH) {
        if (cardW <= 0f || cardH <= 0f || mapW <= 0f || mapH <= 0f) {
            return new float[]{1f, 0f, 0f};
        }
        float scale = Math.max(cardW / mapW, cardH / mapH);
        return new float[]{
                scale,
                (cardW - mapW * scale) / 2f,
                (cardH - mapH * scale) / 2f,
        };
    }

    /**
     * 是否启用 AGSL 边缘折射。
     *
     * <p><b>暂时关闭。</b>真机截图给出了明确证据：卡片边缘那一圈被刷成了近乎纯白的不透明框
     * （像素 240-248），而中间正确透出背景（178-215）——说明着色器拿到的采样落在
     * 背景贴图外面，CLAMP 到边缘那个浅色像素，又因为返回值 alpha=1 把整圈盖死。
     *
     * <p>这条路径只能真机验证，我在这里看不到。与其继续猜参数让你反复截图，
     * 不如先关掉它：<b>剩下三层（半透明 + 内阴影 + 对角高光）都是普通 Canvas 绘制，
     * 不可能出现这种硬边色块</b>，玻璃观感仍在。
     *
     * <p>代码与 {@link #SHADER} 都留在原位。等能在真机上调试时把这里打开，
     * 并先用"渲一个像素读回来比对"的办法确认采样坐标对得上，再放开。
     */
    private static final boolean ENABLE_REFRACTION = false;

    /** 折射带占卡片短边的比例（供测试核对它确实是"一条边"）。 */
    public static float edgeBandRatio() {
        return EDGE_BAND_RATIO;
    }

    public static float[] backdropScale(int screenW, int screenH, int bitmapW, int bitmapH) {
        if (screenW <= 0 || screenH <= 0 || bitmapW <= 0 || bitmapH <= 0) {
            return new float[]{1f, 1f};
        }
        return new float[]{screenW / (float) bitmapW, screenH / (float) bitmapH};
    }
    /** GLSL 的 smoothstep：把 t 从 [a,b] 映射到 0~1 并做三次平滑。 */
    public static float smoothStep(float a, float b, float t) {
        if (a == b) return t < a ? 0f : 1f;
        float x = clamp01((t - a) / (b - a));
        return x * x * (3f - 2f * x);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // ====================================================================

    @Override
    public void setAlpha(int alpha) {
        // 透明度由 tint 自己控制
    }

    @Override
    public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
        // 不支持
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}








