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
        setupDampingAnimation();
        setupRefreshListener();
    }

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

