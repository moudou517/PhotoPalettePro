package com.example.photopalettepro.helper;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;

import com.example.photopalettepro.R;

/**
 * 弹出菜单助手类
 * 职责：处理选择器弹出菜单的创建和显示
 */
public class PopupMenuHelper {

    public interface SelectListener {
        void onSelected(String item);
    }

    private final Context context;
    private final View anchorView;
    private final String[] options;
    private final SelectListener selectListener;

    public PopupMenuHelper(Context context, View anchorView, String[] options, SelectListener selectListener) {
        this.context = context;
        this.anchorView = anchorView;
        this.options = options;
        this.selectListener = selectListener;
    }

    /**
     * 显示弹出菜单
     */
    public void show() {
        ListPopupWindow listPopupWindow = new ListPopupWindow(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_list_item_1, options);
        listPopupWindow.setAdapter(adapter);

        listPopupWindow.setAnchorView(anchorView);
        listPopupWindow.setWidth(anchorView.getWidth());
        listPopupWindow.setDropDownGravity(android.view.Gravity.START);
        listPopupWindow.setHorizontalOffset(0);
        listPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_card_rounded));
        listPopupWindow.setModal(true);

        listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            selectListener.onSelected(options[position]);
            listPopupWindow.dismiss();
        });

        listPopupWindow.show();
    }
}

