package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.photopalettepro.helper.PopupMenuHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 弹窗尺寸必须跟着机型走。
 *
 * <p>这是回归测试：重做 UI 时我把原来的
 * {@code listPopupWindow.setWidth(anchorView.getWidth())}
 * 改成了 {@code Math.max(anchorView.getWidth(), 220dp)}——
 * 多出来的那个下限会让弹窗比触发它的那一行还宽，窄屏上直接戳出屏幕。
 * 「锚点行宽」本来就是动态算的，不能替换成常量。
 *
 * <p>尺寸计算抽成了纯函数，所以这里能把各种机型的几何直接喂进去验，
 * 不需要真的把一个 PopupWindow 弹出来（Robolectric 里也弹不出来）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PopupSizingTest {

    /** 常见机型的可视区宽度（px，按 xxhdpi 密度 3.0 折算）。 */
    private static final int SMALL_W = 320 * 3;    // 小屏 320dp
    private static final int NORMAL_W = 411 * 3;   // 主流 411dp
    private static final int TABLET_W = 800 * 3;   // 平板 800dp

    /** 卡片里的设置行：屏幕宽 - 卡片外边距 12dp×2 - 卡片内边距 8dp×2。 */
    private static int rowWidth(int screenWidthDp) {
        return (screenWidthDp - 12 * 2 - 8 * 2) * 3;
    }

    private static int rowX() {
        return (12 + 8) * 3;   // 卡片外边距 + 卡片内边距
    }

    @Test
    public void widthFollowsTheAnchorRow() {
        // 主流机型：弹窗宽度就等于触发它的那一行
        int screen = NORMAL_W;
        int anchor = rowWidth(411);
        assertEquals(anchor, PopupMenuHelper.popupWidth(anchor, rowX(), screen, screen - 72));
    }

    @Test
    public void widthNeverExceedsTheVisibleArea() {
        // 各种机型、把锚点摆到最右边（最坏情况），都不能超出可视区
        int[][] cases = {
                {SMALL_W, 320}, {NORMAL_W, 411}, {TABLET_W, 800},
        };
        for (int[] c : cases) {
            int screen = c[0];
            int anchor = rowWidth(c[1]);
            int x = rowX();

            int width = PopupMenuHelper.popupWidth(anchor, x, screen, screen - 72);
            assertTrue("宽度 " + width + " 超出了可视区 " + screen, x + width <= screen);
            assertTrue("宽度不该是负的或零", width > 0);
        }
    }

    @Test
    public void widthFallsBackWhenTheAnchorIsNotLaidOutYet() {
        // ViewPager2 里还没量过的页面 getWidth() 会是 0——不能因此弹出个 0 宽的窗口
        int screen = NORMAL_W;
        int width = PopupMenuHelper.popupWidth(0, rowX(), screen, screen - 72);

        assertTrue("锚点还没量出来时也要有个合理宽度", width > 0);
        assertTrue("而且不能超出屏幕", rowX() + width <= screen);
    }

    @Test
    public void narrowDeviceGetsANarrowerPopupNotAFixedOne() {
        // 这就是那个 bug：写死 220dp 下限的话，小屏上弹窗会比锚点行还宽
        int smallAnchor = rowWidth(320);
        int small = PopupMenuHelper.popupWidth(smallAnchor, rowX(), SMALL_W, SMALL_W - 72);

        int normalAnchor = rowWidth(411);
        int normal = PopupMenuHelper.popupWidth(normalAnchor, rowX(), NORMAL_W, NORMAL_W - 72);

        assertTrue("小屏的弹窗必须比主流机型窄", small < normal);
        assertEquals("小屏上也应当正好等于锚点行宽", smallAnchor, small);
    }

    @Test
    public void heightIsClampedToTheSpaceBelow() {
        // 屏幕底部只剩 200px：弹窗不能有 600px 那么高，否则一半选项点不到
        assertEquals(188, PopupMenuHelper.popupMaxHeight(1000, 1200, 120));

        // 空间充足时不该被压
        assertTrue(PopupMenuHelper.popupMaxHeight(1000, 3000, 120) > 1500);
    }

    @Test
    public void heightAlwaysLeavesRoomForAtLeastSomething() {
        // 锚点就贴着屏幕底部：仍然给一个最小值，配合内部滚动至少能看见内容
        int height = PopupMenuHelper.popupMaxHeight(1190, 1200, 120);
        assertEquals("兜底高度应当生效", 120, height);
    }
}
