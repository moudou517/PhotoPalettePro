package com.example.photopalettepro.helper;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Context;
import android.util.TypedValue;

/**
 * 下拉刷新助手类
 * 职责：处理下拉刷新的配置、NestedScrollView 的阻尼动画
 */
public class PullRefreshHelper {

    private final SwipeRefreshLayout swipeRefreshLayout;
    private final View scrollView;
    private final Context context;
    private final RefreshCallback refreshCallback;

    public interface RefreshCallback {
        void onRefresh();
        boolean canRefresh();
    }

    public PullRefreshHelper(SwipeRefreshLayout swipeRefreshLayout, 
                           View scrollView, 
                           Context context,
                           RefreshCallback callback) {
        this.swipeRefreshLayout = swipeRefreshLayout;
        this.scrollView = scrollView;
        this.context = context;
        this.refreshCallback = callback;
    }

    public void setup() {
        setupColorScheme();
        setupProgressViewOffset();
        setupRefreshListener();
    }

    // ------------------------------------------------------------------
    //  为什么删掉了「阻尼动画」
    //
    //  原来这里给 NestedScrollView 挂了 onTouchListener：下拉时额外
    //  setTranslationY(offset * 0.5f)，松手再用 OvershootInterpolator 弹回。
    //  问题是 SwipeRefreshLayout 自己就会把子视图按手指 1:1 拖着走——
    //  两层位移叠在一起，卡片走得比手指快、松手还要多弹一下，
    //  手感就变成「上面那块占位卡住 + 不跟手」。
    //
    //  交给 SwipeRefreshLayout 全权处理：跟手是它的默认行为，
    //  回弹也是最标准的那条曲线，不需要我们再插一手。
    // ------------------------------------------------------------------

    private void setupColorScheme() {
        swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(context, com.example.photopalettepro.R.color.primary_blue)
        );
    }

    private void setupProgressViewOffset() {
        swipeRefreshLayout.post(() -> {
            int end = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 64, 
                    context.getResources().getDisplayMetrics());
            swipeRefreshLayout.setProgressViewOffset(true, 0, end);
        });
    }

    private void setupDampingAnimation() {
        if (scrollView == null) return;
        
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            private float initialY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (swipeRefreshLayout.isRefreshing()) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!v.canScrollVertically(-1)) {
                            float offset = event.getY() - initialY;
                            if (offset > 0) {
                                scrollView.setTranslationY(offset * 0.5f);
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        scrollView.animate()
                                .translationY(0)
                                .setDuration(300)
                                .setInterpolator(new OvershootInterpolator())
                                .start();
                        initialY = 0;
                        break;
                }
                return false;
            }
        });
    }

    private void setupRefreshListener() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // 真正触发刷新时给一下触感：手指在屏幕上时，震动比转圈更早被感知到
            HapticHelper.refreshTriggered(swipeRefreshLayout);

            if (refreshCallback.canRefresh()) {
                refreshCallback.onRefresh();
                new Handler().postDelayed(() -> {
                    stopRefreshing();
                }, 1000);
            } else {
                Toast.makeText(context, "请先导入照片", Toast.LENGTH_SHORT).show();
                stopRefreshing();
            }
        });
    }

    public void stopRefreshing() {
        swipeRefreshLayout.setRefreshing(false);
    }
}

