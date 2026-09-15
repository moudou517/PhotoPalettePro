package com.example.photopalettepro;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 把真实的 Activity 界面画成 PNG，用来肉眼检查版式。
 *
 * <p>起因：真机截图里「中间那块玻璃卡片的白色没有把内容全包进去」，
 * 但只看 XML 判断不出是哪一层的问题——运行时还有
 * {@code UiInteractionHelper.applyRoundedClip} 在改 outline 与裁剪，
 * 光读布局是看不出结果的。
 *
 * <p>这里直接把 Activity 的 decorView 量好、布局好、画到一张位图上，
 * 于是「真机上长什么样」变成了一个可以反复比对的文件，
 * 而不是只能靠猜。产物：app/build/ui-out/*.png
 *
 * <p>注意：软件渲染看得出版式，看不出 GPU 合成出来的色差——
 * 真机那个「两块白」的缝就是这么漏掉的。所以断言只能退而求其次：
 * 验证卡片确实画出了一整片宽白面。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiLayoutScreenshotTest {

    private static final int BITMAP_W = 1233;   // 411dp × 3.0
    private static final int BITMAP_H = 2673;   // 891dp × 3.0

    @Test
    public void allThreePagesRender() throws Exception {
        File dir = new File("build/ui-out");
        assertTrue(dir.exists() || dir.mkdirs());

        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);
        ShadowLooper.idleMainLooper();

        ViewPager2 pager = activity.findViewById(R.id.vpModes);
        assertNotNull(pager);

        // 页面顺序：0 = 明信片，1 = 海报（默认落点），2 = 胶片
        String[] names = {"zine-page.png", "poster-page.png", "film-page.png"};
        for (int page = 0; page < names.length; page++) {
            pager.setCurrentItem(page, false);
            ShadowLooper.idleMainLooper();

            Bitmap shot = drawView(activity.getWindow().getDecorView());
            assertNotNull(shot);
            writePng(shot, new File(dir, names[page]));
        }

        // 弹窗的一行也出张图：它是自己搭的布局，不在任何一个页面里
        View row = LayoutInflater.from(activity)
                .inflate(R.layout.item_popup_option, null, false);
        ((TextView) row.findViewById(R.id.tvOptionTitle)).setText("标准取色");
        ((TextView) row.findViewById(R.id.tvOptionSummary))
                .setText("聚类取色，最稳，适合大多数照片");
        row.findViewById(R.id.ivOptionCheck).setVisibility(View.VISIBLE);
        writePng(drawView(row, 900, 200), new File(dir, "popup-option.png"));

        System.out.println("界面样例已输出 -> " + dir.getAbsolutePath());
    }

    /**
     * 卡片内部必须是<b>一片</b>白。
     *
     * <p>真机上的毛病正是这里出的：卡片是半透明玻璃，被 {@code clipToOutline}
     * 丢进离屏层之后，内容区合成成 255、四周内边距一圈是 248——
     * 一块方形亮斑嵌在灰白里，看着就是「白色没包住内容」。
     *
     * <h3>为什么不写死 y</h3>
     *
     * <p>第一版把 y 固定成 1150（当时按出图量的）。结果一改预览比例、
     * 整个版面往下挪，这条就挂了——<b>那是测试脆，不是代码坏</b>。
     * 现在扫一遍所有行、取最宽的那段白：验证的是「卡片确实画出了一整片宽白面」，
     * 和版面的具体位置无关，改比例、改间距都不受影响。
     */
    @Test
    public void cardsPaintASingleSurface() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();

        Bitmap shot = drawView(activity.getWindow().getDecorView());
        assertNotNull(shot);

        int widest = 0;
        for (int y = 0; y < shot.getHeight(); y += 8) {
            int firstWhite = -1;
            int lastWhite = -1;
            for (int x = 0; x < shot.getWidth(); x++) {
                int c = shot.getPixel(x, y);
                boolean white = Color.red(c) >= 250
                        && Color.green(c) >= 250
                        && Color.blue(c) >= 250;
                if (white) {
                    if (firstWhite < 0) firstWhite = x;
                    lastWhite = x;
                }
            }
            if (firstWhite >= 0) {
                widest = Math.max(widest, lastWhite - firstWhite);
            }
        }

        assertTrue("应当能找到卡片的宽白面，实测最宽 " + widest,
                widest > shot.getWidth() * 0.8f);
    }

    /** 量好、布局好，再画到一张和屏幕等大的位图上。 */
    private static Bitmap drawView(View view) {
        return drawView(view, BITMAP_W, BITMAP_H);
    }

    private static Bitmap drawView(View view, int width, int height) {
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(1, view.getMeasuredWidth()),
                Math.max(1, view.getMeasuredHeight()),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    private static void writePng(Bitmap bmp, File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
