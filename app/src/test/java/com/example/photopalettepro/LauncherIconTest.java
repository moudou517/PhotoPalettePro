package com.example.photopalettepro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.core.content.ContextCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 把应用图标画出来看。
 *
 * <p>图标是矢量的，pathData 和渐变都是<b>运行期</b>解析的——编译通过不等于能画出来。
 * 这一路上吃过三次亏（整片空白、黑块），都是靠出图才发现的。
 *
 * <p>产物：app/build/ui-out/icon.png（方形）与 icon-squircle.png（圆角方形裁切）。
 *
 * <h3>⚠️ 不要给前景加 66/108 的内缩</h3>
 *
 * <p>真实的自适应图标是把【前景铺满 108×108】，由桌面按自己的形状做遮罩。
 * 66×66 只是"重要内容别出这个框"的<b>建议</b>，不是系统施加的内缩。
 * 第一版这里加了内缩，预览比真机小了一大圈，害我误判成"图标没铺满"。
 *
 * <p>这个测试只出图、不做断言——它是给人看的，不是给 CI 卡门的。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherIconTest {

    private static final int SIZE = 432;

    @Test
    public void iconRenders() throws Exception {
        Drawable bg = ContextCompat.getDrawable(
                RuntimeEnvironment.getApplication(), R.drawable.ic_launcher_background);
        Drawable fg = ContextCompat.getDrawable(
                RuntimeEnvironment.getApplication(), R.drawable.ic_launcher_foreground);

        File dir = new File("build/ui-out");
        if (!dir.exists() && !dir.mkdirs()) return;

        LayerDrawable layered = new LayerDrawable(new Drawable[]{bg, fg});
        layered.setBounds(0, 0, SIZE, SIZE);

        Bitmap icon = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        layered.draw(new Canvas(icon));
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "icon.png"))) {
            icon.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }

        // 圆角方形遮罩：和 MIUI / 原生桌面的实际裁切最接近
        Bitmap squircle = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(squircle);
        Path mask = new Path();
        mask.addRoundRect(new RectF(0, 0, SIZE, SIZE), SIZE * 0.28f, SIZE * 0.28f,
                Path.Direction.CW);
        sc.clipPath(mask);
        sc.drawBitmap(icon, 0, 0, null);
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "icon-squircle.png"))) {
            squircle.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }

        System.out.println("图标样例已输出 -> " + dir.getAbsolutePath());
    }
}


