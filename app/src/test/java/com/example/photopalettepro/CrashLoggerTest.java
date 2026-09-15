package com.example.photopalettepro;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.photopalettepro.helper.CrashLogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * 会话记录的判定逻辑。
 *
 * <p>这段代码存在的唯一理由是「上一次真机崩了、但下次进来没有弹窗」——
 * 也就是判定没生效。所以判定本身必须被测到：什么该报、什么不该报。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CrashLoggerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // 每一条用例都从「没有任何记录」开始
        File file = new File(context.getExternalFilesDir(null), "session.log");
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    // ------------------------------------------------------------------
    //  不该报的
    // ------------------------------------------------------------------

    @Test
    public void firstEverLaunchHasNothingToReport() {
        assertNull("第一次启动没有上一场，不应弹窗", CrashLogger.beginSession(context));
    }

    @Test
    public void backgroundedThenReclaimedIsNotReported() throws Exception {
        CrashLogger.beginSession(context);
        CrashLogger.breadcrumb("导入照片");
        CrashLogger.onStopped();

        // 退到后台之后很久才被系统回收：不该打扰用户
        assertNull("隔了很久才被回收，不该弹窗", reportAfterBackgroundGap(10 * 60 * 1000L));
    }

    @Test
    public void dyingWhileTheGalleryIsOnTopIsReported() throws Exception {
        // ★ 用户实际遇到的那个场景，也正是之前被漏掉的：
        //   点导入 → 拉起系统相册（我们退到后台）→ 崩在后台 → 相册被一起收掉
        //   → 用户马上重开 App。日志最后一行就是「退到后台」。
        //   旧规则把它当成「正常回收」，于是表现成「崩了但没有弹窗」。
        CrashLogger.beginSession(context);
        CrashLogger.onResumed();
        CrashLogger.breadcrumb("onMainPhotoPicked");
        CrashLogger.onStopped();          // 系统相册盖住了我们
        // 进程在这里被带走

        String report = reportAfterBackgroundGap(20 * 1000L);
        assertNotNull("刚退到后台就没了，必须上报——这正是「崩了却没弹窗」那一条", report);
        assertTrue("应当带上最后几步", report.contains("onMainPhotoPicked"));
    }

    @Test
    public void backgroundDeathReportIsNotLabelledAsACrash() throws Exception {
        CrashLogger.beginSession(context);
        CrashLogger.breadcrumb("loadFilmPhotos 12 张");
        CrashLogger.onStopped();

        String report = reportAfterBackgroundGap(5_000L);
        assertNotNull(report);
        assertFalse("没有 Java 堆栈时不该谎称是崩溃", report.contains(CrashLogger.MARK_CRASH));
    }

    @Test
    public void cleanExitIsNotReported() {
        CrashLogger.beginSession(context);
        CrashLogger.breadcrumb("导入照片");
        CrashLogger.onStopped();
        CrashLogger.onResumed();
        CrashLogger.breadcrumb("导出海报");
        CrashLogger.onCleanExit();

        assertNull("正常退出不该弹窗", CrashLogger.beginSession(context));
    }

    // ------------------------------------------------------------------
    //  该报的
    // ------------------------------------------------------------------

    @Test
    public void crashingInTheForegroundIsReported() {
        CrashLogger.beginSession(context);
        CrashLogger.onResumed();
        CrashLogger.breadcrumb("onMainPhotoPicked content://photo/1");

        // 模拟一次真正的未捕获异常：走的是安装好的处理器
        Thread thread = new Thread(() -> {
            throw new RuntimeException("模拟导入崩溃");
        }, "film-load");
        thread.setUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler());
        thread.start();
        try {
            thread.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        String report = CrashLogger.beginSession(context);
        assertNotNull("前台崩溃必须上报", report);
        assertTrue("应当带崩溃标记", report.contains(CrashLogger.MARK_CRASH));
        assertTrue("应当带堆栈", report.contains("模拟导入崩溃"));
    }

    @Test
    public void dyingMidwayWithoutAnyExceptionIsStillReported() {
        // 这一条是重点：native 崩溃 / 系统强杀都拿不到 Java 堆栈，
        // 只能靠「最后停在哪一步」来判断。没有这个，用户看到的就是「没有弹窗」。
        CrashLogger.beginSession(context);
        CrashLogger.onResumed();
        CrashLogger.breadcrumb("loadSourceImage 开始");
        CrashLogger.breadcrumb("预览图解码 1600x1200");
        // 进程在这里被带走——没有 onStopped，也没有异常

        String report = CrashLogger.beginSession(context);
        assertNotNull("停在中途必须上报", report);
        assertFalse("没有堆栈时不该谎称是崩溃", report.contains(CrashLogger.MARK_CRASH));
        assertTrue("必须带上最后几步，否则等于没记",
                report.contains("预览图解码 1600x1200"));
        assertTrue("也应当带上机型", report.contains("Android"));
    }

    @Test
    public void dyingAfterReturningFromThePhotoPickerIsReported() {
        // 真实时序：导入 → 拉起系统相册（退到后台）→ 选完回来（回到前台）→ 崩
        CrashLogger.beginSession(context);
        CrashLogger.onResumed();
        CrashLogger.breadcrumb("onMainPhotoPicked");
        CrashLogger.onStopped();          // 系统相册盖住了我们
        CrashLogger.onResumed();          // 选完回来
        CrashLogger.breadcrumb("applyFilmPhotoSet 6 张");
        CrashLogger.onStopped();          // ← 如果只是又退后台
        CrashLogger.onResumed();          // ← 又回来
        CrashLogger.breadcrumb("renderFilmPreview");  // 死在这里

        String report = CrashLogger.beginSession(context);
        assertNotNull("从相册回来之后崩掉必须上报", report);
        assertTrue(report.contains("renderFilmPreview"));
    }

    // ------------------------------------------------------------------

    @Test
    public void reportIsConsumedSoItDoesNotPopEveryLaunch() {
        CrashLogger.beginSession(context);
        CrashLogger.breadcrumb("导入照片");
        // 没退到后台就死了

        assertNotNull("第一次应当上报", CrashLogger.beginSession(context));
        assertNull("报过一次就不该再弹", CrashLogger.beginSession(context));
    }

    @Test
    public void breadcrumbsAreFlushedImmediatelyNotOnCrash() {
        CrashLogger.beginSession(context);
        CrashLogger.breadcrumb("第一步");
        CrashLogger.breadcrumb("第二步");

        // 直接读文件：进程随时可能被系统带走，等到崩溃时再回忆就来不及了
        File file = new File(context.getExternalFilesDir(null), "session.log");
        assertTrue("记录文件应当已经落盘", file.exists());

        String report = CrashLogger.beginSession(context);
        assertNotNull(report);
        assertTrue(report.contains("第一步"));
        assertTrue(report.contains("第二步"));
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    /**
     * 把日志里最后一条「退到后台」的时间戳改成「距今 gapMs 之前」，再开新的一场。
     *
     * <p>用来区分「刚崩掉」和「退到后台很久之后被回收」——判定规则的输入就是这个间隔。
     */
    private String reportAfterBackgroundGap(long gapMs) throws Exception {
        File file = new File(context.getExternalFilesDir(null), "session.log");
        String content = new String(
                java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

        int idx = content.lastIndexOf(CrashLogger.MARK_STOPPED);
        assertTrue("用例应当先写一条「退到后台」", idx >= 0);

        String patched = content.substring(0, idx)
                + CrashLogger.MARK_STOPPED + "  (patched)  "
                + (System.currentTimeMillis() - gapMs) + "\n";
        java.nio.file.Files.write(file.toPath(),
                patched.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return CrashLogger.beginSession(context);
    }
}

