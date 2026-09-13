package com.example.photopalettepro.helper;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * 玻璃效果助手类
 * 职责：为背景视图应用真实的背景模糊（RenderEffect，仅 Android 12+），
 * 从而实现 iOS 风格的磨砂玻璃质感。低版本系统自动降级为半透明表面。
 */
public final class GlassEffectHelper {

    private GlassEffectHelper() {
    }

    /**
     * 给视图应用磨砂模糊效果（Android 12 / API 31 及以上）。
     * 在低版本上静默跳过，由半透明的玻璃表面兜底。
     *
     * @param view   需要模糊的视图（通常是铺满全屏的背景图）
     * @param radius 模糊半径（像素）
     */
    public static void applyBackdropBlur(View view, float radius) {
        if (view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {
                // 某些设备可能不支持，忽略并回退到半透明表面
            }
        }
    }

    /**
     * 移除视图上的模糊效果
     */
    public static void clearBlur(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null);
        }
    }
}
