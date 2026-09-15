package com.example.photopalettepro.helper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一个「只旁观、不拦截」的容器：用来发现用户想横向翻页。
 *
 * <p>为什么需要它：多张胶片合成期间要锁死左右翻页，但锁死之后
 * {@code ViewPager2.setUserInputEnabled(false)} 会让翻页组件直接不再处理触摸，
 * 于是「用户试图翻页」这个动作也就无从得知了——而我们需要弹窗告诉他为什么翻不动。
 *
 * <p>{@code ViewPager2} 是 {@code final} 的，没法继承，所以改成在它外面套一层容器，
 * 覆写 {@link #dispatchTouchEvent} 观察手势。这里选 {@code dispatchTouchEvent}
 * 而不是 {@code onInterceptTouchEvent}：前者对本子树里的<b>每一个</b>事件都会被调用，
 * 后者只在有子视图吃掉 ACTION_DOWN 之后才会继续收到 MOVE——
 * 万一哪天 ViewPager2 换实现、DOWN 没人接，旁观就失效了。
 *
 * <p>本类<b>只观察、不消费</b>：始终把事件原样交给 {@code super}，
 * 所以子视图的滚动、点击行为完全不受影响。
 */
public class SwipeWatchLayout extends FrameLayout {

    /** 用户明显想横向翻页时的回调；每次手势最多触发一次。 */
    public interface SwipeAttemptListener {
        void onHorizontalSwipeAttempt();
    }

    /** 横向位移要超过纵向多少倍才算「想翻页」，而不是「想上下滚配置」 */
    private static final float HORIZONTAL_BIAS = 1.5f;

    private final int touchSlop;

    @Nullable
    private SwipeAttemptListener listener;

    private float downX;
    private float downY;
    /** 本次手势是否已经上报过，避免一次滑动弹好几次窗 */
    private boolean reported;

    public SwipeWatchLayout(@NonNull Context context) {
        this(context, null);
    }

    public SwipeWatchLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeWatchLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setSwipeAttemptListener(@Nullable SwipeAttemptListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        trackSwipe(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void trackSwipe(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                reported = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // 多指（捏合之类）不是翻页意图，直接作废本次手势
                reported = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (reported || listener == null) break;
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                if (dx > touchSlop && dx > dy * HORIZONTAL_BIAS) {
                    reported = true;
                    listener.onHorizontalSwipeAttempt();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reported = false;
                break;

            default:
                break;
        }
    }
}
