package com.example.photopalettepro.helper;

import android.annotation.SuppressLint;
import android.graphics.Outline;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;

/**
 * UI 交互助手类
 * 职责：处理按钮按下动画等通用 UI 交互反馈
 */
public class UiInteractionHelper {

    /**
     * 应用按压缩放动画
     * @param view 目标视图
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void applyPressAnimation(View view) {
        if (view == null) return;
        
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(100)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator())
                            .start();
                    break;
            }
            return false;
        });
    }

    /**
     * 批量应用按压缩放动画
     * @param views 视图列表
     */
    public static void applyPressAnimationBatch(View... views) {
        for (View view : views) {
            applyPressAnimation(view);
        }
    }

    /**
     * 设置预览容器的圆角裁剪
     * @param container 预览容器
     */
    public static void setupPreviewContainerClipping(View container) {
        if (container == null) return;
        
        container.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        container.setClipToOutline(true);
    }

    /**
     * 给任意视图应用圆角裁剪，确保其子内容（图片、涟漪、背景）在滑动时
     * 也不会超出圆角边界，修复滑动过程中出现的“直角”问题。
     *
     * @param view      目标视图
     * @param radiusDp  圆角半径（dp）
     */
    public static void applyRoundedClip(final View view, final float radiusDp) {
        if (view == null) return;

        final float radius = view.getResources().getDisplayMetrics().density * radiusDp;
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    /**
     * 批量应用圆角裁剪
     */
    public static void applyRoundedClipBatch(float radiusDp, View... views) {
        if (views == null) return;
        for (View view : views) {
            applyRoundedClip(view, radiusDp);
        }
    }
}

