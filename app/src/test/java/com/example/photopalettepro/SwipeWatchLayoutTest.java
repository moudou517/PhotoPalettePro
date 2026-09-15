package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import com.example.photopalettepro.helper.SwipeWatchLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 翻页旁观器。
 *
 * <p>多张胶片合成期间左右翻页被锁死，这时必须靠它发现「用户想翻页」才能弹窗解释。
 * 两条性质都要守住：
 * <ul>
 *   <li>明显偏横向的拖动要认出来；</li>
 *   <li><b>上下滚配置不能被误判成翻页</b>——否则用户每滚一下配置就弹一次窗。</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SwipeWatchLayoutTest {

    private static final float START_X = 500f;
    private static final float START_Y = 500f;

    /** 远超 touchSlop 的位移 */
    private static final float BIG = 240f;
    /** 远小于 touchSlop 的位移 */
    private static final float TINY = 2f;

    private SwipeWatchLayout layout;
    private final AtomicInteger reports = new AtomicInteger();

    private SwipeWatchLayout watched() {
        layout = new SwipeWatchLayout(RuntimeEnvironment.getApplication());
        reports.set(0);
        layout.setSwipeAttemptListener(reports::incrementAndGet);
        return layout;
    }

    private static void send(SwipeWatchLayout target, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        target.dispatchTouchEvent(event);
        event.recycle();
    }

    /** 一次完整的横向拖动：按下 → 若干次移动 → 抬起。 */
    private static void horizontalDrag(SwipeWatchLayout target, float dx) {
        send(target, MotionEvent.ACTION_DOWN, START_X, START_Y);
        send(target, MotionEvent.ACTION_MOVE, START_X + dx * 0.5f, START_Y);
        send(target, MotionEvent.ACTION_MOVE, START_X + dx, START_Y);
        send(target, MotionEvent.ACTION_UP, START_X + dx, START_Y);
    }

    // ------------------------------------------------------------------

    @Test
    public void horizontalDragIsReported() {
        horizontalDrag(watched(), -BIG);
        assertEquals("向左拖应当被认出来", 1, reports.get());
    }

    @Test
    public void horizontalDragIsReportedOnlyOncePerGesture() {
        SwipeWatchLayout target = watched();
        send(target, MotionEvent.ACTION_DOWN, START_X, START_Y);
        for (int i = 1; i <= 6; i++) {
            send(target, MotionEvent.ACTION_MOVE, START_X - i * 60f, START_Y);
        }
        send(target, MotionEvent.ACTION_UP, START_X - 360f, START_Y);

        assertEquals("一次手势不该弹好几次窗", 1, reports.get());
    }

    @Test
    public void verticalScrollIsNotMistakenForAPageTurn() {
        SwipeWatchLayout target = watched();
        send(target, MotionEvent.ACTION_DOWN, START_X, START_Y);
        send(target, MotionEvent.ACTION_MOVE, START_X, START_Y + BIG);
        send(target, MotionEvent.ACTION_UP, START_X, START_Y + BIG);

        assertEquals("上下滚配置不能触发翻页提示", 0, reports.get());
    }

    @Test
    public void diagonalScrollWithVerticalBiasIsIgnored() {
        SwipeWatchLayout target = watched();
        // 横向 240、纵向 200：虽然横得更多，但没到 1.5 倍，仍算「滚配置」
        send(target, MotionEvent.ACTION_DOWN, START_X, START_Y);
        send(target, MotionEvent.ACTION_MOVE, START_X + BIG, START_Y + 200f);
        send(target, MotionEvent.ACTION_UP, START_X + BIG, START_Y + 200f);

        assertEquals(0, reports.get());
    }

    @Test
    public void tinyMovementsAreIgnored() {
        horizontalDrag(watched(), TINY);
        assertEquals("没超过 touchSlop 不算翻页", 0, reports.get());
    }

    @Test
    public void multiTouchGestureIsIgnored() {
        SwipeWatchLayout target = watched();
        send(target, MotionEvent.ACTION_DOWN, START_X, START_Y);
        send(target, MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), START_X + 120f, START_Y + 120f);
        send(target, MotionEvent.ACTION_MOVE, START_X - BIG, START_Y);
        send(target, MotionEvent.ACTION_UP, START_X - BIG, START_Y);

        assertEquals("捏合之类不算翻页意图", 0, reports.get());
    }

    @Test
    public void aNewGestureCanReportAgain() {
        SwipeWatchLayout target = watched();
        horizontalDrag(target, -BIG);
        horizontalDrag(target, -BIG);
        assertEquals("抬起之后再滑应当能再报一次", 2, reports.get());
    }

    @Test
    public void cancelResetsTheGestureSoTheNextOneReports() {
        SwipeWatchLayout target = watched();

        // 第一次手势被系统取消（例如弹窗抢走了焦点）
        send(target, MotionEvent.ACTION_DOWN, START_X, START_Y);
        send(target, MotionEvent.ACTION_MOVE, START_X - BIG, START_Y);
        send(target, MotionEvent.ACTION_CANCEL, START_X - BIG, START_Y);
        assertEquals(1, reports.get());

        // 取消之后必须能重新计数，否则用户再滑就永远不提示了
        horizontalDrag(target, -BIG);
        assertEquals(2, reports.get());
    }

    @Test
    public void withoutAListenerNothingBlowsUp() {
        SwipeWatchLayout target = new SwipeWatchLayout(RuntimeEnvironment.getApplication());
        target.setSwipeAttemptListener(null);
        horizontalDrag(target, -BIG);
        assertEquals(0, reports.get());
    }
}
