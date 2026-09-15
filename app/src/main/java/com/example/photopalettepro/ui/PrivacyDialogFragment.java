package com.example.photopalettepro.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.photopalettepro.R;

/**
 * 隐私政策弹窗。
 *
 * <p>从 {@code MainActivity} 里提出来——它本来就是自包含的内部类，
 * 不碰 Activity 的任何字段，所以是整个拆分里风险最低的一块。
 *
 * <p>用法不变：{@code new PrivacyDialogFragment().show(getSupportFragmentManager(), tag)}。
 */
public class PrivacyDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_privacy, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.btnAgree).setOnClickListener(v -> {
            SharedPreferences prefs = getActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("privacy_agreed", true).apply();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btnReject).setOnClickListener(v -> {
            getActivity().finishAffinity();
        });

        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                // 和选择弹窗同一块面板：四角圆角、上沿高光、发丝边——整套 UI 一套语言
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_popup_panel);
                dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setCancelable(false);
            }
        });

        return dialog;
    }
}
