package com.example.photopalettepro.helper;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.example.photopalettepro.R;

/**
 * 应用自己的对话框。
 *
 * <h3>为什么不用 {@code AlertDialog}</h3>
 *
 * <p>系统那套是一张方白纸：直角、纯白、字体和按钮都是平台默认的。
 * 摆在一堆玻璃卡片中间非常割裂——用户的原话是"显得和割裂"。
 *
 * <p>试过用主题覆盖（{@code alertDialogTheme} + {@code shapeAppearanceOverlay}），
 * 结果 Material 的对话框布局直接 inflate 失败
 * （{@code You must supply a layout_width attribute}）——它依赖自己主题里那几个属性，
 * 覆盖任何一个都会把整个布局打回平台主题。
 *
 * <p>所以干脆自己搭：{@link Dialog} + {@link Window#setBackgroundDrawable} 置空 +
 * 自己的圆角面板。想画成什么样就是什么样，也不再受 Material 主题牵制。
 *
 * <h3>视觉</h3>
 *
 * <p>用和选择弹窗<b>同一块面板素材</b>（{@code bg_popup_panel}）：
 * 20dp 圆角、近实底、上沿高光、下沿内阴影、发丝边。
 * 按钮沿用主按钮的蓝紫渐变，整块读起来就是"带确认按钮的模块"。
 */
public final class AppDialog {

    private AppDialog() {
    }

    /** 单按钮：只有「知道了」。 */
    public static Dialog show(Context context, String title, String message,
                              String positive, Runnable onPositive) {
        return show(context, title, message, positive, onPositive, null, null);
    }

    /**
     * 完整版。
     *
     * @param negative   次按钮文案；null 表示不显示
     * @param onNegative 次按钮回调；null 表示点了只关闭
     */
    public static Dialog show(Context context, String title, String message,
                              String positive, Runnable onPositive,
                              String negative, Runnable onNegative) {
        if (context == null) return null;

        View content = LayoutInflater.from(context).inflate(R.layout.dialog_app, null, false);

        TextView tvTitle = content.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = content.findViewById(R.id.tvDialogMessage);
        Button btnPositive = content.findViewById(R.id.btnDialogPositive);
        TextView btnNegative = content.findViewById(R.id.btnDialogNegative);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnPositive.setText(positive);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);

        // 窗口背景置空：圆角由内容自己那层画。留着默认背景就会在四角露出白底。
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // 左右留边，别顶到屏幕边缘
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);

            window.setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels
                            - 48 * context.getResources().getDisplayMetrics().density),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run();
        });

        if (negative == null || negative.isEmpty()) {
            btnNegative.setVisibility(View.GONE);
        } else {
            btnNegative.setVisibility(View.VISIBLE);
            btnNegative.setText(negative);
            btnNegative.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegative != null) onNegative.run();
            });
        }

        dialog.show();
        return dialog;
    }
}
