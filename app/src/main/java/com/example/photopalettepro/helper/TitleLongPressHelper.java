package com.example.photopalettepro.helper;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.example.photopalettepro.AboutActivity;

/**
 * 标题长按彩蛋：按住标题不放，进入「关于」。
 *
 * <h3>两个真问题</h3>
 *
 * <p><b>一、时间太长。</b>原本要按满 <b>5 秒</b>。长按的通用心理预期是 0.5~1 秒，
 * 5 秒已经超出「按一下」的范畴——用户按到 1 秒没反应就松手了，
 * 于是这个入口等于不存在。现在改成 {@value #HOLD_MS} 毫秒。
 *
 * <p><b>二、震动节奏根本不会响。</b>原来的进度震动写在 {@code ACTION_MOVE} 里：
 * 手指只要不动，系统就不投递 MOVE 事件，那几个 1 秒 / 2 秒 / 3 秒的节点
 * 一个都不会命中。现在改成<b>自己按时间轮询</b>，手指一动不动也照常给反馈。
 *
 * <p>另外，原来用的是裸 {@link android.os.Vibrator}，绕开了系统的
 * 「触感反馈」开关——用户在设置里关掉了也照震。现在统一走
 * {@link HapticHelper}（内部是 {@code performHapticFeedback}），系统关了就安静。
 *
 * <h3>反馈设计</h3>
 *
 * <p>按住的过程是一次「充能」：标题缓缓缩小（缩到 0.92 就是蓄满），
 * 同时四段渐强的震动（25% / 50% / 75% / 100%），达成时一记最重的手感再跳转。
 * 只按一下就走的话给一句提示，每个会话最多说一次。
 */
public class TitleLongPressHelper {

    /** 按住多久算达成。1.2 秒：够「刻意」，又不至于让人以为没反应。 */
    public static final long HOLD_MS = 1200;

    /**
     * 按住了这么久，算不算达成。
     *
     * <p>抽成一个纯函数，是为了让「阈值」这件事能被直接验证：
     * 触发判断原本埋在 {@code Handler} 的自我重排里，靠推进虚拟时间去够它
     * 又和 Robolectric 的时钟语义纠缠不清（试了两版都不可靠，反而写出会误报的测试）。
     * 判断本身就是一次比较，把它摊开，测试就稳定了。
     */
    public static boolean reachedHold(long heldMillis) {
        return heldMillis >= HOLD_MS;
    }

    /** 进度震动的节点。手指不动也要响，所以由时间轮询驱动，不依赖触摸事件。 */
    private static final float[] PROGRESS_STOPS = {0.25f, 0.50f, 0.75f, 1.00f};

    /** 手指移动超过这么多像素就取消——长按要能中途反悔。 */
    private static final float TOUCH_SLOP_DP = 24f;

    private final Context context;
    private final TextView tvAppTitle;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private ValueAnimator holdAnimator;

    private long pressStartTime;
    private float downX;
    private float downY;
    private int nextStop;
    private boolean holding;
    private boolean completed;
    /** 每个会话只提示一次，避免每次轻点都弹一句话。 */
    private boolean hinted;

    public TitleLongPressHelper(Context context, TextView tvAppTitle) {
        this.context = context;
        this.tvAppTitle = tvAppTitle;
    }

    public void setup() {
        if (tvAppTitle == null) return;
        tvAppTitle.setOnTouchListener(this::handleTouch);
    }

    private boolean handleTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startHold(v, event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (holding && movedTooFar(event)) cancelHold(v);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishHold(v);
                break;
            default:
                break;
        }
        return true;
    }

    // ====================================================================
    //  按住
    // ====================================================================

    private void startHold(View v, MotionEvent event) {
        holding = true;
        completed = false;
        pressStartTime = android.os.SystemClock.uptimeMillis();
        downX = event.getX();
        downY = event.getY();
        nextStop = 0;

        long slop = (long) (TOUCH_SLOP_DP * v.getResources().getDisplayMetrics().density);

        // 蓄力：标题缓缓缩小，缩到 0.92 正好是达成的时刻。
        // 这条曲线本身就是进度条——不然用户根本不知道要按多久。
        if (holdAnimator != null) holdAnimator.cancel();
        holdAnimator = ValueAnimator.ofFloat(1f, 0.92f);
        holdAnimator.setDuration(HOLD_MS);
        holdAnimator.setInterpolator(new DecelerateInterpolator());
        holdAnimator.addUpdateListener(
                a -> {
                    float value = (float) a.getAnimatedValue();
                    tvAppTitle.setScaleX(value);
                    tvAppTitle.setScaleY(value);
                });
        holdAnimator.start();

        // 进度震动：按时间轮询，不依赖 MOVE 事件
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!holding) return;

                long elapsed = android.os.SystemClock.uptimeMillis() - pressStartTime;
                if (reachedHold(elapsed)) {
                    completeHold(v, slop);
                    return;
                }

                float progress = elapsed / (float) HOLD_MS;
                if (nextStop < PROGRESS_STOPS.length
                        && progress >= PROGRESS_STOPS[nextStop]) {
                    HapticHelper.holdProgress(v, progress);
                    nextStop++;
                }
                handler.postDelayed(this, 60);
            }
        };
        handler.postDelayed(progressRunnable, 60);
    }

    private boolean movedTooFar(MotionEvent event) {
        float slop = TOUCH_SLOP_DP * context.getResources().getDisplayMetrics().density;
        return Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop;
    }

    /** 中途松手或移开：收回动画，什么也不发生。 */
    private void cancelHold(View v) {
        if (!holding) return;
        holding = false;
        stopProgress();
        releaseScale(v);
    }

    private void finishHold(View v) {
        long held = android.os.SystemClock.uptimeMillis() - pressStartTime;

        if (holding && !completed) {
            holding = false;
            stopProgress();
            releaseScale(v);

            // 只按了一下：给一次提示就够了，不必每次轻点都弹
            if (!hinted && held < HOLD_MS) {
                hinted = true;
                Toast.makeText(context, "按住标题不放有惊喜", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ====================================================================
    //  达成
    // ====================================================================

    private void completeHold(View v, long slop) {
        if (completed) return;
        completed = true;
        holding = false;
        stopProgress();

        if (holdAnimator != null) holdAnimator.cancel();

        // 一记最重的手感，然后弹一下再走——触感在前，视觉在后
        HapticHelper.holdCompleted(v);

        v.animate()
                .scaleX(1.06f).scaleY(1.06f)
                .setDuration(90)
                .withEndAction(() -> v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setInterpolator(new OvershootInterpolator())
                        .setDuration(160)
                        .withEndAction(() -> jumpToAbout(v))
                        .start())
                .start();
    }

    private void releaseScale(View v) {
        if (holdAnimator != null) holdAnimator.cancel();
        v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
    }

    private void stopProgress() {
        if (progressRunnable != null) handler.removeCallbacks(progressRunnable);
    }

    private void jumpToAbout(View v) {
        try {
            context.startActivity(new Intent(context, AboutActivity.class));
        } catch (Exception e) {
            Toast.makeText(context, "打不开关于页", Toast.LENGTH_SHORT).show();
        }
    }

    public void cleanup() {
        holding = false;
        stopProgress();
        if (holdAnimator != null) holdAnimator.cancel();
    }
}




