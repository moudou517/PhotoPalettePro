package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 锁页时「离胶片页走了多远」的计算。
 *
 * <p>这条是回归测试：第一版用 {@code position != PAGE_FILM} 判方向，限制完全没生效。
 * 原因是 ViewPager2 往回翻时 {@code position} 报的是<b>目标页</b>——
 * 从胶片页（2）滑向海报页（1）时报 {@code position = 1, offset: 1 → 0}，
 * 那个判断永远对不上。
 *
 * <p>正确的量是 {@code |2 - (position + offset)|}：从 2 滑向 1 时它从 0 单调涨到 1。
 */
public class LockedSwipeTest {

    /** 从胶片页滑向海报页：ViewPager2 报的是 position=1，offset 从 1 递减到 0。 */
    @Test
    public void slidingAwayFromFilmRampsUpMonotonically() {
        float previous = -1f;
        // 用整数步长算 offset：浮点累减到不了 0，最后一步会停在 0.0999…
        for (int step = 0; step <= 10; step++) {
            float offset = 1f - step / 10f;
            float away = MainActivity.swipeAwayFromFilm(1, offset);
            assertTrue("应当单调递增：offset=" + offset + " 时 " + away + " < " + previous,
                    away >= previous - 0.0001f);
            previous = away;
        }
        assertEquals("翻到底就是 1", 1f, previous, 0.001f);
    }

    @Test
    public void stillOnTheFilmPageMeansZero() {
        // 刚起手：position=1、offset=1，等价于还停在胶片页
        assertEquals(0f, MainActivity.swipeAwayFromFilm(1, 1f), 0.0001f);
    }

    @Test
    public void theThirdThresholdLandsWhereExpected() {
        // 走过三分之一 → 越过阈值；不到 → 不越过
        assertTrue("走到 0.4 应当触发",
                MainActivity.swipeAwayFromFilm(1, 0.6f) > 1f / 3f);
        assertTrue("只走 0.2 不该触发",
                MainActivity.swipeAwayFromFilm(1, 0.8f) < 1f / 3f);
    }

    /**
     * 反向对照：老写法（直接用 offset）在这条路径上是<b>从上往下</b>走的——
     * 起手 1.0、翻到底 0.0，所以「offset > 1/3」永远在起手那一刻就成立，
     * 或者按老代码的守卫直接提前返回。
     */
    @Test
    public void theOldOffsetOnlyFormulaWasWrong() {
        float oldAtStart = 1f;
        float oldAtEnd = 0f;
        float newAtStart = MainActivity.swipeAwayFromFilm(1, oldAtStart);
        float newAtEnd = MainActivity.swipeAwayFromFilm(1, oldAtEnd);

        assertEquals("起手时确实还没走开", 0f, newAtStart, 0.0001f);
        assertEquals("翻到底才是走开", 1f, newAtEnd, 0.0001f);
        assertTrue("老的 offset 是反着走的——起手最大、翻到底最小",
                oldAtStart > oldAtEnd);
    }
}

