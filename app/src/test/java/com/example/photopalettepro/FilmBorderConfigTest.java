package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.photopalettepro.film.FilmStock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 胶片配置的持久化转换。
 *
 * <p>{@link FilmBorderConfig#toJson()} / {@link FilmBorderConfig#fromJson} 是纯数据转换
 * （不碰任何 View），所以能直接测——「再次导入同一张照片能不能拿回上次的胶片设置」
 * 全看这一段。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FilmBorderConfigTest {

    private static FilmBorderConfig filled() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.style = FilmStock.STYLE_NOIR;
        cfg.width = FilmBorderConfig.WIDTH_WIDE;
        cfg.sprocketHoles = false;
        cfg.frameNumber = false;
        cfg.stock = "CINE 800T";
        cfg.camera = "NIKON ZF";
        cfg.lens = "LENS 40MM";
        cfg.exposure = "1/60s · f/2 · ISO800";
        cfg.date = "2026.09.03";
        return cfg;
    }

    @Test
    public void roundTripKeepsEveryField() {
        FilmBorderConfig restored = FilmBorderConfig.fromJson(filled().toJson(), null);

        assertNotNull(restored);
        assertEquals(FilmStock.STYLE_NOIR, restored.style);
        assertEquals(FilmBorderConfig.WIDTH_WIDE, restored.width);
        assertFalse("齿孔开关要能关得住", restored.sprocketHoles);
        assertFalse("帧号开关要能关得住", restored.frameNumber);
        assertEquals("CINE 800T", restored.stock);
        assertEquals("NIKON ZF", restored.camera);
        assertEquals("LENS 40MM", restored.lens);
        assertEquals("1/60s · f/2 · ISO800", restored.exposure);
        assertEquals("2026.09.03", restored.date);
    }

    @Test
    public void defaultConfigRoundTripsToo() {
        FilmBorderConfig restored = FilmBorderConfig.fromJson(new FilmBorderConfig().toJson(), null);

        assertNotNull(restored);
        assertEquals(FilmStock.STYLE_CLASSIC, restored.style);
        assertEquals(FilmBorderConfig.WIDTH_NORMAL, restored.width);
        assertTrue(restored.sprocketHoles);
        assertTrue(restored.frameNumber);
        // 空文案不该因为存过一圈就变成别的东西
        assertTrue(restored.stock.isEmpty());
        assertEquals("PORTRA 400", restored.resolveStock());
    }

    @Test
    public void missingKeysKeepTheFallbackInsteadOfClearingIt() {
        // 模拟老版本写下的记录：只有风格，没有片边文字几个键
        FilmBorderConfig fallback = new FilmBorderConfig();
        fallback.camera = "已有值";

        FilmBorderConfig restored = FilmBorderConfig.fromJson(
                "{\"style\":\"富士绿\"}", fallback);

        assertEquals("JSON 里有的按键取", FilmStock.STYLE_FUJI, restored.style);
        assertEquals("JSON 里没有的键保留原值，不能被清空", "已有值", restored.camera);
        // 其余没提到的字段保持默认
        assertEquals(FilmBorderConfig.WIDTH_NORMAL, restored.width);
    }

    @Test
    public void blankOrBrokenJsonReportsNoDataInsteadOfThrowing() {
        FilmBorderConfig fallback = filled();

        assertSame("空串原样返回 fallback", fallback, FilmBorderConfig.fromJson("", fallback));
        assertSame(fallback, FilmBorderConfig.fromJson("   ", fallback));
        assertSame("坏 JSON 不能抛出去把导入流程带崩",
                fallback, FilmBorderConfig.fromJson("{不是 json", fallback));

        // 没有 fallback 时，没有可用数据就返回 null —— 调用方据此安静跳过
        assertNull(FilmBorderConfig.fromJson(null, null));
        assertNull(FilmBorderConfig.fromJson("{也不是 json", null));
    }
}
