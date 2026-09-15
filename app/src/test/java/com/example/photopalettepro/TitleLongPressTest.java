package com.example.photopalettepro;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.TextView;

import com.example.photopalettepro.helper.TitleLongPressHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 标题长按彩蛋的时长与取消。
 *
 * <h3>为什么不测「按满 1.2 秒会跳转」</h3>
 *
 * <p>试过两版都靠不住：触发判断埋在 {@code Handler} 每 60ms 一次自我重排里，
 * 而 Robolectric 推进虚拟时间的两种写法都会顺手把<b>延迟任务</b>也跑掉——
 * 于是链条一路跑到底，600ms 就「达成」，测试反而谎报「阈值太短」。
 * 一个会误报的测试比没有测试更糟，所以改成两条：
 *
 * <ul>
 *   <li><b>阈值本身</b>用 {@link TitleLongPressHelper#reachedHold(long)} 直接验
 *       ——它就是一次比较，摊开之后判断完全确定；</li>
 *   <li><b>取消路径</b>在<b>完全不推进时间</b>的前提下验——能不能中途反悔，
 *       和时钟无关。</li>
 * </ul>
 *
 * <p>「按满就跳」这一步由实现保证（一次 elapsed 比较），这里如实说明没覆盖到。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TitleLongPressTest {

    /** 原来的 5 秒已经超出「按一下」的范畴：按到 1 秒没反应人就松手了。 */
    @Test
    public void theHoldThresholdIsShortEnoughToBeDiscoverable() {
        assertTrue("长按阈值应当 <= 1.5 秒，实际 " + TitleLongPressHelper.HOLD_MS + "ms",
                TitleLongPressHelper.HOLD_MS <= 1500);
        assertTrue("太短会和普通点击混淆，实际 " + TitleLongPressHelper.HOLD_MS + "ms",
                TitleLongPressHelper.HOLD_MS >= 600);
    }

    @Test
    public void theThresholdIsExactAndNotEarly() {
        assertFalse("差 1 毫秒不该算达成",
                TitleLongPressHelper.reachedHold(TitleLongPressHelper.HOLD_MS - 1));
        assertFalse("刚开始按当然不算", TitleLongPressHelper.reachedHold(0));
        assertFalse("半秒不算", TitleLongPressHelper.reachedHold(500));
        assertTrue("刚好到点就该达成",
                TitleLongPressHelper.reachedHold(TitleLongPressHelper.HOLD_MS));
        assertTrue("按更久当然算", TitleLongPressHelper.reachedHold(10_000));
    }

    @Test
    public void movingAwayCancelsTheHold() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView title = attach(activity);

        press(title, MotionEvent.ACTION_DOWN, 10, 10);

        // 手指滑走 200px，远超 24dp 的容差——长按要能中途反悔
        press(title, MotionEvent.ACTION_MOVE, 210, 210);
        press(title, MotionEvent.ACTION_UP, 210, 210);

        assertNull("滑走之后不该跳转", shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void aPlainTapDoesNotNavigate() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView title = attach(activity);

        // 按下立刻抬起，中间不推进任何时间
        press(title, MotionEvent.ACTION_DOWN, 10, 10);
        press(title, MotionEvent.ACTION_UP, 10, 10);

        assertNull("点一下不该跳转", shadowOf(activity).getNextStartedActivity());
    }

    // ------------------------------------------------------------------

    private static TextView attach(Activity activity) {
        TextView title = new TextView(activity);
        new TitleLongPressHelper(activity, title).setup();
        return title;
    }

    private static void press(TextView title, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        title.dispatchTouchEvent(MotionEvent.obtain(now, now, action, x, y, 0));
    }
}
