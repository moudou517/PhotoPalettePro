package com.example.photopalettepro.helper;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 会话记录 / 崩溃记录。
 *
 * <p><b>为什么不是简单的 UncaughtExceptionHandler。</b>
 * 「导入照片卡住然后闪退、但下次进来没有弹窗」只有两种可能：
 * 崩溃根本没走到 Java 的未捕获异常处理器，或者处理器自己也没写成。
 * 而没走到处理器的情况恰恰很常见：
 * <ul>
 *   <li>系统低内存直接把进程杀掉（没有任何异常）；</li>
 *   <li>ANR 被系统清理；</li>
 *   <li>RenderThread / GPU 驱动里的 native 崩溃（tombstone，不是 Java 堆栈）；</li>
 *   <li>处理器里再撞一次 OOM，写文件失败。</li>
 * </ul>
 *
 * <p>所以这里的做法是<b>边做边记</b>：每一步操作立刻追加进文件，而不是等崩溃时再回忆。
 * 进程无论怎么死，文件都已经落在盘上了。下次启动时判断「上一次是不是正常结束」：
 * <ol>
 *   <li>文件里有「崩溃」行 → 当作崩溃上报，带堆栈；</li>
 *   <li>最后一行是「退到后台」→ 正常退后台后被系统回收，不上报；</li>
 *   <li>其余情况（最后停在某个操作中间）→ 上报「上次没有正常结束」，带完整轨迹。</li>
 * </ol>
 * 第 3 条正好覆盖 native 崩溃与系统强杀：没有堆栈，但知道它死在哪一步。
 *
 * <p>文件写在 app 自己的外部目录（{@code getExternalFilesDir}），不需要任何权限。
 */
public final class CrashLogger {

    private static final String TAG = "CrashLogger";
    private static final String FILE_NAME = "session.log";

    /**
     * 「退到后台」之后多久内重启，才算「不太像正常回收」。
     *
     * <p>正常流程是退到后台很久之后被系统悄悄回收——那种不该打扰用户。
     * 而「点导入 → 相册盖住 → 崩在后台 → 相册被一起收掉 → 用户马上重开」
     * 只隔几秒到几十秒。三分钟足够把两者分开。
     */
    private static final long BACKGROUND_GRACE_MS = 3 * 60 * 1000L;

    static final String MARK_START = "== 会话开始 ==";
    static final String MARK_RESUMED = "-- 回到前台 --";
    /** 公开：测试要靠它改写时间戳，来区分「刚崩掉」和「退后台很久后被回收」 */
    public static final String MARK_STOPPED = "-- 退到后台 --";
    static final String MARK_CLEAN_EXIT = "== 正常退出 ==";
    /** 公开：界面要靠它区分「崩溃」和「没有正常结束」两种标题 */
    public static final String MARK_CRASH = "== 崩溃 ==";

    private static final Object LOCK = new Object();

    /**
     * 应用级 Context。
     *
     * <p>存成静态是因为 {@link #breadcrumb(String)} 这类调用点分布在主流程各处，
     * 让每一步都带着 Context 走会把代码搞脏；而 Application Context 本身
     * 与进程同生命周期，不存在泄漏。
     */
    private static Context appContext;

    private CrashLogger() {
    }

    // ====================================================================
    //  生命周期
    // ====================================================================

    /**
     * 开始一次会话：先把上一场的记录读出来，再开新的一场。
     *
     * @return 上一场需要上报的记录；上一次是正常结束（或无记录）时返回 {@code null}
     */
    public static String beginSession(final Context context) {
        // 整个开场都包住：诊断代码绝不能成为「App 打不开」的原因
        try {
            if (context == null) return null;

            appContext = context.getApplicationContext();
            String report;
            String previous;

            synchronized (LOCK) {
                previous = readAll();
                if (shouldReport(previous, System.currentTimeMillis())) {
                    report = previous;
                } else {
                    report = null;
                }
                // 覆盖写：新的一场从干净的记录开始
                writeAll(MARK_START + "  " + stamp() + "  " + System.currentTimeMillis()
                        + "  " + describeDevice());
            }

            installHandler();
            return report;
        } catch (Throwable t) {
            Log.w(TAG, "会话记录初始化失败，跳过", t);
            return null;
        }
    }

    /**
     * 会话记录文件（应用私有的外部目录）。
     *
     * <p><b>刻意不往「下载」目录或媒体库里写。</b>
     * 早先的版本会把日志通过 MediaStore 发布到公共下载目录，好让文件管理器能看到；
     * 但那意味着<b>每次启动都要同步访问系统媒体库</b>——媒体库是全系统共用的，
     * 一旦它忙或者状态异常，App 就会卡在启动、而相册和截图也会跟着不正常。
     * 一个诊断功能付不起这个代价，所以改成只写自己目录 + FileProvider 分享出去。
     */
    public static File sessionFile(Context context) {
        if (context == null) return null;
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /**
     * 上一场该不该上报。
     *
     * <p>只有「停在半路」才报：
     * <ul>
     *   <li>正常退出 —— 不是 bug；</li>
     *   <li>记录里只有一行「会话开始」—— 上一场刚起来就没了，没有可分析的信息；</li>
     *   <li>最后停在「退到后台」<b>且已经过了很久</b> —— 那是退到后台之后被系统回收，
     *       很常见，报到用户面前只会天天弹窗。</li>
     * </ul>
     *
     * <p>但最后停在「退到后台」<b>且马上就重启了</b>，多半不是正常回收：典型场景是
     * 「点导入 → 拉起系统相册（我们退到后台）→ 崩在后台 → 相册被一起收掉」。
     * 这条正是之前漏掉的——用户看到的「崩了却没有弹窗」就是它。
     */
    private static boolean shouldReport(String log, long now) {
        if (log == null || log.trim().isEmpty()) return false;
        if (log.contains(MARK_CRASH)) return true;

        String last = lastMeaningfulLine(log);
        if (last == null) return false;
        if (last.startsWith(MARK_START)) return false;
        if (last.startsWith(MARK_CLEAN_EXIT)) return false;

        if (last.startsWith(MARK_STOPPED)) {
            long stoppedAt = trailingEpoch(last);
            // 时间戳读不出来就保守上报：宁可多问一次，也不要又一次「没有弹窗」
            if (stoppedAt <= 0) return true;
            return now - stoppedAt <= BACKGROUND_GRACE_MS;
        }
        return true;
    }

    /** 从「退到后台」那一行末尾取出时间戳（毫秒）。 */
    private static long trailingEpoch(String line) {
        int idx = line.lastIndexOf(' ');
        if (idx < 0) return -1L;
        try {
            return Long.parseLong(line.substring(idx + 1).trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static String lastMeaningfulLine(String log) {
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) return line;
        }
        return null;
    }

    /** 安装未捕获异常处理器；写完之后仍交还给系统原本的处理器。 */
    private static void installHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                appendCrash(thread, throwable);
            } catch (Throwable ignored) {
                // 记录崩溃的过程本身再崩，就没什么可做的了
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    // ====================================================================
    //  记录
    // ====================================================================

    /** 记一步操作。立刻落盘——进程随时可能被系统带走，等崩溃时再回忆就来不及了。 */
    public static void breadcrumb(String message) {
        if (message == null) return;
        append(stamp() + "  " + message);
    }

    public static void onResumed() {
        append(MARK_RESUMED + "  " + stamp());
    }

    public static void onStopped() {
        // 末尾带上毫秒时间戳：下次启动靠它判断「是刚崩掉，还是退后台很久后被回收」
        append(MARK_STOPPED + "  " + stamp() + "  " + System.currentTimeMillis());
    }

    /** 用户真的退出应用（Back / 关掉任务），才算这一场正常结束。 */
    public static void onCleanExit() {
        append(MARK_CLEAN_EXIT + "  " + stamp());
    }

    private static void appendCrash(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(MARK_CRASH + "  " + stamp() + "  thread=" + thread.getName());
        throwable.printStackTrace(pw);
        pw.flush();
        append(sw.toString());
    }

    private static void append(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (LOCK) {
            File file = sessionFile(appContext);
            if (file == null) return;
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write((text.endsWith("\n") ? text : text + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable t) {
                // 记录失败不能反过来影响 App —— 尤其这里可能就是 OOM
                Log.w(TAG, "写会话记录失败", t);
            }
        }
    }

    // ====================================================================
    //  文件
    // ====================================================================

    private static String readAll() {
        File file = sessionFile(appContext);
        if (file == null || !file.exists() || file.length() <= 0) return null;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Throwable t) {
            Log.w(TAG, "读会话记录失败", t);
            return null;
        }
        return sb.toString();
    }

    private static void writeAll(String text) {
        File file = sessionFile(appContext);
        if (file == null) return;
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.w(TAG, "写会话记录失败", t);
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String describeDevice() {
        return Build.MANUFACTURER + " " + Build.MODEL
                + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }
}
