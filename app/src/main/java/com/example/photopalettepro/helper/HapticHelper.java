package com.example.photopalettepro.helper;

import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * 交互震动反馈。
 *
 * <p>一套界面里的触感应该是一套<b>词汇</b>，而不是到处都用同一下。这里按力度分档：
 *
 * <table>
 *   <tr><td>{@link #pageChanged}</td><td>换页停稳</td><td>最轻，一下短促的「到位了」</td></tr>
 *   <tr><td>{@link #popupOpened}</td><td>选择弹窗弹出</td><td>轻，确认「有东西出现」</td></tr>
 *   <tr><td>{@link #selectionCommitted}</td><td>选中某一项</td><td>中，像机械开关落到底</td></tr>
 *   <tr><td>{@link #refreshTriggered}</td><td>下拉刷新触发</td><td>中，同上</td></tr>
 *   <tr><td>{@link #holdProgress}</td><td>长按进度</td><td>渐强，越接近触发越重</td></tr>
 *   <tr><td>{@link #holdCompleted}</td><td>长按达成</td><td>最重，一个明确的「成了」</td></tr>
 * </table>
 *
 * <p>刻意<b>不</b>传 {@code FLAG_IGNORE_GLOBAL_SETTING}：
 * 用户在系统里关掉触感反馈就该是关掉的，App 不该越过这个设置。系统设置本身
 * 是对 {@code performHapticFeedback} 生效的，所以这里什么都不用判断。
 * ——这一点也是长按标题原先的问题：它绕开这里直接用了 {@code Vibrator}，
 * 于是「关了触感反馈也照震」。
 *
 * <p>整体包一层 try/catch：某些 ROM 在特定状态下会抛，而震动反馈失败
 * 绝不该让界面操作跟着挂掉。
 */
public final class HapticHelper {

    private HapticHelper() {
    }

    /** 换页停稳时的轻点。 */
    public static void pageChanged(View view) {
        perform(view, HapticFeedbackConstants.CLOCK_TICK);
    }

    /** 下拉刷新真正触发时的一下。 */
    public static void refreshTriggered(View view) {
        perform(view, HapticFeedbackConstants.LONG_PRESS);
    }

    /** 选择弹窗弹出：轻，只是确认「有东西出现了」。 */
    public static void popupOpened(View view) {
        perform(view, HapticFeedbackConstants.CLOCK_TICK);
    }

    /** 选中某一项：中，比弹出重一档，表示「决定了」。 */
    public static void selectionCommitted(View view) {
        perform(view, HapticFeedbackConstants.LONG_PRESS);
    }

    /**
     * 长按进度。
     *
     * @param progress 0~1，越接近触发力道越重
     */
    public static void holdProgress(View view, float progress) {
        if (progress < 0.35f) {
            perform(view, HapticFeedbackConstants.CLOCK_TICK);
        } else if (progress < 0.75f) {
            perform(view, HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        } else {
            perform(view, HapticFeedbackConstants.LONG_PRESS);
        }
    }

    /**
     * 翻页被挡住：急促有力的一下。
     *
     * <p>三连击而不是一记长震——长震听上去像"正在处理"，
     * 连击才像"不行"。用户手指还停在屏幕上，触感比弹窗更早被感知到。
     */
    public static void blockedByLock(View view) {
        if (view == null) return;
        view.postDelayed(() -> perform(view, HapticFeedbackConstants.REJECT), 0);
        view.postDelayed(() -> perform(view, HapticFeedbackConstants.REJECT), 55);
        view.postDelayed(() -> perform(view, HapticFeedbackConstants.LONG_PRESS), 110);
    }

    /** 长按达成：最重的一下，然后才跳转。 */
    public static void holdCompleted(View view) {
        perform(view, HapticFeedbackConstants.CONTEXT_CLICK);
        perform(view, HapticFeedbackConstants.LONG_PRESS);
    }

    private static void perform(View view, int feedbackConstant) {
        if (view == null) return;
        try {
            view.performHapticFeedback(feedbackConstant);
        } catch (Throwable ignored) {
            // 触感反馈是锦上添花，失败就算了
        }
    }
}

