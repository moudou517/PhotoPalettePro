package com.example.photopalettepro.helper;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.photopalettepro.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 选择弹窗。
 *
 * <h3>为什么不用 {@code ListPopupWindow}</h3>
 *
 * <p>原来的实现把 {@code android.R.layout.simple_list_item_1} 塞进
 * {@code ListPopupWindow}：系统默认的一行小字，没有说明、没有当前项标记，
 * 用户看到的就是几个孤零零的词。
 *
 * <p>现在自己搭 {@link PopupWindow}，换来三件事：
 * <ul>
 *   <li><b>每一项都有名字和一句说明</b>——「默认渲染」这种名字本身不说明任何问题；</li>
 *   <li><b>当前选中项打勾</b>，一眼看出现在用的是哪个；</li>
 *   <li><b>弹出与落点都有震动</b>，手指还按在屏幕上时，触感比视觉更早被感知到。</li>
 * </ul>
 *
 * <p>面板本身就是一张玻璃卡片（复用 {@code bg_glass_card}），
 * 和界面上其它表面是同一套语言。
 */
public final class PopupMenuHelper {

    /** 一个可选项：逻辑标识 + 给人看的名字 + 一句说明。 */
    public static final class Option {
        /** 逻辑标识。会写进历史记录、喂给取色与排版逻辑，所以不能随手改。 */
        public final String key;
        /** 显示名。 */
        public final String title;
        /** 一句话说明它做什么。可以为空。 */
        public final String summary;

        public Option(String key, String title, String summary) {
            this.key = key;
            this.title = title;
            this.summary = summary == null ? "" : summary;
        }

        /** 名字即标识、没有额外说明的选项（比如「窄边框 / 标准边框 / 宽边框」）。 */
        public static Option plain(String label) {
            return new Option(label, label, "");
        }
    }

    public interface SelectListener {
        void onSelected(Option option);
    }

    private final Context context;
    private final View anchorView;
    private final List<Option> options;
    private final String currentKey;
    private final SelectListener selectListener;

    public PopupMenuHelper(Context context, View anchorView, List<Option> options,
                           String currentKey, SelectListener selectListener) {
        this.context = context;
        this.anchorView = anchorView;
        this.options = options == null ? new ArrayList<>() : options;
        this.currentKey = currentKey;
        this.selectListener = selectListener;
    }

    /** 便捷构造：直接给一组标签，名字即标识。 */
    public static PopupMenuHelper ofLabels(Context context, View anchorView, String[] labels,
                                           String currentKey, SelectListener listener) {
        List<Option> list = new ArrayList<>();
        for (String label : Arrays.asList(labels)) list.add(Option.plain(label));
        return new PopupMenuHelper(context, anchorView, list, currentKey, listener);
    }

    public void show() {
        if (anchorView == null || options.isEmpty()) return;

        PopupWindow window = new PopupWindow(context);
        window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        window.setOutsideTouchable(true);
        window.setFocusable(true);
        window.setElevation(dp(16));
        window.setAnimationStyle(R.style.PopupAnimation);
        window.setContentView(buildPanel(window));

        anchorView.post(() -> {
            if (anchorView.getWindowToken() == null) return;

            Rect visible = new Rect();
            anchorView.getWindowVisibleDisplayFrame(visible);
            int[] location = new int[2];
            anchorView.getLocationOnScreen(location);

            // 宽度：跟着触发那一行。这是原来就有的行为，**不能写死**——
            // 行宽随屏幕宽度、卡片内外边距一起变，写死就会在某些机型上戳出屏幕。
            int width = popupWidth(anchorView.getWidth(), location[0],
                    visible.right, visible.width() - dp(24));
            window.setWidth(width);

            // 高度交给窗口自己按内容包裹。
            //
            // 之前是手工 measure 一个高度再 setHeight 塞给窗口——量出来的值和
            // PopupWindow 最终布局的高度对不上，多出来的部分被面板背景填成一片空白，
            // 屏幕上就是"面板底下空了一大截"（用户截图里那个）。
            //
            // 改成 WRAP_CONTENT：面板永远贴着内容，四个圆角也永远在正确的位置。
            // 内容高到放不下的情况（小屏上的五个胶片风格约 350dp）由里面那层
            // ScrollView 兜——它已经在 buildPanel 里备好了。
            window.setHeight(android.view.WindowManager.LayoutParams.WRAP_CONTENT);

            window.showAsDropDown(anchorView, 0, dp(6), Gravity.START);
            // 弹出来了：给一下触感，明确「有东西出现」
            HapticHelper.popupOpened(anchorView);
        });
    }

    /**
     * 弹窗宽度。
     *
     * <p>首选锚点那一行的实际宽度（原来 {@code ListPopupWindow.setWidth} 的行为），
     * 但必须夹在可视区域内：锚点靠右、屏幕又窄的时候，行宽本身就是"到屏幕边为止"，
     * 再往外就是黑的。锚点还没量出来（宽度为 0）时退回可视宽度。
     */
    public static int popupWidth(int anchorWidth, int anchorX, int visibleRight, int fallbackWidth) {
        int room = visibleRight - anchorX - 8;      // 右边留一点
        if (room <= 0) room = fallbackWidth;
        if (anchorWidth <= 0) return Math.max(1, Math.min(fallbackWidth, room));
        return Math.max(1, Math.min(anchorWidth, room));
    }

    /**
     * 弹窗最大高度：锚点下沿到屏幕可见区底部还剩多少。
     *
     * <p>不夹的话，小屏 / 横屏上会有一半选项落在屏幕外——点不到，也没有滚动条可拉。
     */
    public static int popupMaxHeight(int anchorBottom, int visibleBottom, int minHeight) {
        return Math.max(minHeight, visibleBottom - anchorBottom - 12);
    }

    private View buildPanel(PopupWindow window) {
        LayoutInflater inflater = LayoutInflater.from(context);

        // 内容层：只放行，不带背景
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        for (Option option : options) {
            View row = inflater.inflate(R.layout.item_popup_option, column, false);
            TextView title = row.findViewById(R.id.tvOptionTitle);
            TextView summary = row.findViewById(R.id.tvOptionSummary);
            ImageView check = row.findViewById(R.id.ivOptionCheck);

            title.setText(option.title);

            if (option.summary.isEmpty()) {
                summary.setVisibility(View.GONE);
            } else {
                summary.setText(option.summary);
            }

            boolean selected = option.key.equals(currentKey);
            check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);

            row.setOnClickListener(v -> {
                // 选中也要有反馈，力度比「弹出来」重一档
                HapticHelper.selectionCommitted(v);
                window.dismiss();
                if (selectListener != null) selectListener.onSelected(option);
            });
            column.addView(row);
        }

        // 可滚动层：只有在真的放不下时才滚（高度由 show() 决定）
        ScrollView scroller = new ScrollView(context);
        scroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // ⚠️ 圆角背景【必须】留在最外面这层。
        //
        // 放在 ScrollView 上的话，内容一滚，ScrollView 的绘制边缘会把
        // 外层的圆角切掉——屏幕上就是"下面两个角没了"（用户看到的就是这个）。
        // 现在外层永远是一块完整的圆角矩形，滚的只是它里面的内容。
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.bg_popup_panel);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return panel;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}



