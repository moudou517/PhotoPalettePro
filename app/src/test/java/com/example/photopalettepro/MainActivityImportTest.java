package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 走一遍真实的导入流程 —— 这是崩溃高发区，也是唯一能真正验证它的方式。
 *
 * <p>前几轮都是靠读代码猜（「大概是 OOM 吧」），改完还是崩。这个测试把 Activity
 * 真的建起来、喂一张真照片（12MP，正是那条「两张全尺寸解码」老问题的触发尺寸），
 * 把整条导入链路跑完，任何确定性的异常都会在这里炸出来。
 *
 * <p>它当然测不出真机上的 OOM 或 OEM 差异——那部分靠 {@code CrashLogger}
 * 在真机上抓堆栈。但「代码本身有没有错」这件事，这里能给出确切答案。
 *
 * <p>照片用 {@code file://}：Robolectric 里 provider 支撑的 {@code content://} 流
 * 喂不进本地解码器（会抛 "You must use ShadowContentResolver.registerInputStream()"），
 * 那样测试会「通过」但其实根本没解码。{@code file://} 能真实走完
 * {@code BitmapFactory} 那条路，而 App 自己的代码对两者是同一条路径。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityImportTest {

    /** 12MP 手机照的尺寸 */
    private static final int PHOTO_W = 4000;
    private static final int PHOTO_H = 3000;

    // ------------------------------------------------------------------

    @Test
    public void importingAPhotoRunsTheWholePipelineWithoutThrowing() throws Exception {
        MainActivity activity = buildActivity();
        Uri photo = samplePhoto("a.jpg");

        activity.onMainPhotoPicked(photo);
        drainMainLooper();

        assertTrue("Activity 应当还活着", !activity.isFinishing() && !activity.isDestroyed());
    }

    @Test
    public void importingActuallyDecodedThePhoto() throws Exception {
        MainActivity activity = buildActivity();
        Uri photo = samplePhoto("b.jpg");

        // 先确认起点是干净的，否则这个断言会假通过
        assertTrue("导入前不该有预览图", !activity.hasPreviewForTest());

        activity.onMainPhotoPicked(photo);
        drainMainLooper();

        // 真解码了才会把预览贴上去。这一步能挡住「测试通过但其实什么都没跑」
        assertTrue("导入后预览图应当已经解码并贴上", activity.hasPreviewForTest());
    }

    @Test
    public void importingTheSamePhotoTwiceIsSafe() throws Exception {
        MainActivity activity = buildActivity();
        Uri photo = samplePhoto("c.jpg");

        activity.onMainPhotoPicked(photo);
        drainMainLooper();

        // 第二次导入同一张：走「不是换照片」的分支，不能把文案清掉，
        // 也不该因为状态残留而崩
        activity.onMainPhotoPicked(photo);
        drainMainLooper();

        assertTrue(!activity.isFinishing() && !activity.isDestroyed());
    }

    @Test
    public void importingManyPhotosAtOnceIsSafe() throws Exception {
        MainActivity activity = buildActivity();

        // 直接驱动胶片页的多张路径：6 张 = 两行三格
        List<Uri> photos = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            photos.add(samplePhoto("multi" + i + ".jpg"));
        }

        activity.onFilmPhotosPicked(photos);
        drainMainLooper();

        assertTrue(!activity.isFinishing() && !activity.isDestroyed());
        assertTrue("多张时翻页应当被锁住", activity.isPagerLockedForTest());
    }

    /**
     * 真机上「多张胶卷生成好后无法导出」就是这条挂的。
     *
     * <p>多张合成时「主照片」会被刻意放掉（那两页已锁住），{@code currentImageUri} 置空；
     * 而导出按钮的前置判断只看了它，于是明明有 12 张照片，却被判成「还没导入」。
     */
    @Test
    public void afterImportingManyPhotosTheAppStillKnowsItHasPhotos() throws Exception {
        MainActivity activity = buildActivity();

        List<Uri> photos = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            photos.add(samplePhoto("export" + i + ".jpg"));
        }

        activity.onFilmPhotosPicked(photos);
        awaitFilmPhotos(activity, 12);

        assertEquals("12 张都该解出来", 12, activity.filmPhotoCountForTest());
        assertTrue("主照片被放掉不代表没有照片——导出按钮的前置判断必须仍然成立",
                activity.hasPhotoForTest());
    }

    @Test
    public void importingTwelvePhotosIsSafe() throws Exception {
        MainActivity activity = buildActivity();

        List<Uri> photos = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            photos.add(samplePhoto("many" + i + ".jpg"));
        }
        activity.onFilmPhotosPicked(photos);
        drainMainLooper();

        assertTrue(!activity.isFinishing() && !activity.isDestroyed());
    }

    /**
     * 这正是用户报的那个：「点导入多张卡住，再从底部导入就闪退」。
     *
     * <p>第一次还在解十几张，用户又从底部选了一张 —— 两批解码同时占内存，
     * 内存直接翻倍。这条测试要保证的是：<b>第二批一来，第一批立刻停手</b>。
     */
    @Test
    public void aSecondImportCancelsTheFirstInsteadOfDecodingBothAtOnce() throws Exception {
        MainActivity activity = buildActivity();

        List<Uri> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(samplePhoto("overlap" + i + ".jpg"));
        }

        // 第一批：12 张，还没解完
        activity.onFilmPhotosPicked(many);
        // 紧接着第二批：底部「导入」一张 —— 不等第一批结束
        activity.onMainPhotoPicked(samplePhoto("single.jpg"));

        drainMainLooper();

        assertTrue("两批叠在一起也不能崩", !activity.isFinishing() && !activity.isDestroyed());
        assertEquals("最后生效的应当是第二批", 1, activity.filmPhotoCountForTest());
    }

    @Test
    public void tappingTheMultiPickerWhileBusyDoesNotDie() throws Exception {
        MainActivity activity = buildActivity();

        List<Uri> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(samplePhoto("busy" + i + ".jpg"));
        }
        activity.onFilmPhotosPicked(many);

        // 正在解码时再点一次「选择照片（可多选）」：应当给一句提示而不是卡死
        activity.onPickFilmPhotosTappedForTest();

        drainMainLooper();
        assertTrue(!activity.isFinishing() && !activity.isDestroyed());
    }

    @Test
    public void importingFromAnUnreadableUriFailsGracefully() {
        MainActivity activity = buildActivity();

        // 没有这个文件：打开流会失败。要的正是「失败也不能崩」
        activity.onMainPhotoPicked(Uri.parse("content://nobody/here"));
        activity.onMainPhotoPicked(Uri.fromFile(new File("/does/not/exist.jpg")));
        drainMainLooper();

        assertTrue(!activity.isFinishing() && !activity.isDestroyed());
    }

    @Test
    public void importingWithNoPhotoAtAllIsSafe() {
        MainActivity activity = buildActivity();

        activity.onMainPhotoPicked(null);
        activity.onFilmPhotosPicked(null);
        activity.onFilmPhotosPicked(new ArrayList<>());
        drainMainLooper();

        assertTrue(!activity.isFinishing() && !activity.isDestroyed());
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    private static MainActivity buildActivity() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);
        return activity;
    }

    /** 后台的胶片读取会 runOnUiThread 回来，这里把主线程跑空。 */
    private static void drainMainLooper() {
        for (int i = 0; i < 5; i++) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        ShadowLooper.idleMainLooper();
        assertNotNull(Looper.getMainLooper());
    }

    /**
     * 等后台把照片解完，再断言。
     *
     * <p>胶片读取整体在后台线程上跑，回主线程贴界面要等它——十几张 12MP 照片的
     * 「落盘 + 解码」远超 {@link #drainMainLooper()} 那 250ms。
     * 不轮询就会在解完之前断言，测出来永远是 0（第一版就这么挂的）。
     */
    private static void awaitFilmPhotos(MainActivity activity, int expected) {
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            if (activity.filmPhotoCountForTest() == expected) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        ShadowLooper.idleMainLooper();
    }

    /** 造一张有梯度的 12MP 照片并写到缓存目录，返回它的 file:// URI。 */
    private static Uri samplePhoto(String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(PHOTO_W, PHOTO_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 0, PHOTO_H,
                Color.rgb(118, 168, 210), Color.rgb(26, 90, 104), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, PHOTO_W, PHOTO_H, paint);

        Context context = RuntimeEnvironment.getApplication();
        File file = new File(context.getCacheDir(), name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // JPEG：PNG 在 12MP 下有几 MB，写盘很慢，而这里要的只是「能解码」
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
        }
        bitmap.recycle();
        return Uri.fromFile(file);
    }
}

