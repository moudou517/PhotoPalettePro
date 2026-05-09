package com.example.photopalettepro.helper;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * UI 交互助手类
 * 职责：处理按钮按下动画等通用 UI 交互反馈
 */
public class UIInteractionHelper {

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
}

