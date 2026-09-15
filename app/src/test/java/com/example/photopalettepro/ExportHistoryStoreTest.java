package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;

import com.example.photopalettepro.data.AppDatabase;
import com.example.photopalettepro.data.ExportHistory;
import com.example.photopalettepro.data.ExportHistoryDao;
import com.example.photopalettepro.film.FilmStock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import com.example.photopalettepro.config.FilmBorderConfig;

/**
 * 导出历史的落库与回读（Room v4，含 filmJson 列）。
 *
 * <p>这里用内存库跑真实的 Room 实现，校验两件事：
 * <ol>
 *   <li>新增的 {@code filmJson} 列能存能取；</li>
 *   <li>{@code getLastConfigByUri} 取的是<b>最新一行</b>——胶片导出走的是
 *       「读回上一条再把 filmJson 并进去」，正确性依赖这条查询语义。</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ExportHistoryStoreTest {

    private static final String URI = "content://media/external/images/media/42";

    private AppDatabase db;
    private ExportHistoryDao dao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.exportHistoryDao();
    }

    @After
    public void tearDown() {
        if (db != null) db.close();
    }

    private static ExportHistory record(String uri, String optionsJson, String zineJson,
                                        String filmJson, long timestamp) {
        return new ExportHistory(uri, optionsJson, "@me", "/out/" + timestamp + ".png", timestamp,
                "SONY A7M4", "FE 50MM", "1/200s", "f/1.8", "ISO400",
                zineJson, filmJson);
    }

    // ------------------------------------------------------------------

    @Test
    public void filmConfigSurvivesARoundTrip() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.style = FilmStock.STYLE_FUJI;
        cfg.width = FilmBorderConfig.WIDTH_NARROW;
        cfg.sprocketHoles = false;
        cfg.stock = "SUPERIA 400";

        dao.insertHistory(record(URI, "", null, cfg.toJson(), 1000L));

        ExportHistory loaded = dao.getLastConfigByUri(URI);
        assertNotNull(loaded);
        assertNotNull("filmJson 应当落库", loaded.filmJson);

        FilmBorderConfig restored = FilmBorderConfig.fromJson(loaded.filmJson, null);
        assertNotNull(restored);
        assertEquals(FilmStock.STYLE_FUJI, restored.style);
        assertEquals(FilmBorderConfig.WIDTH_NARROW, restored.width);
        assertEquals(false, restored.sprocketHoles);
        assertEquals("SUPERIA 400", restored.stock);
    }

    @Test
    public void lastConfigIsTheNewestRow() {
        dao.insertHistory(record(URI, "{\"mode\":\"默认渲染\"}", "{\"title\":\"旧\"}", "{\"style\":\"经典白框\"}", 1000L));
        dao.insertHistory(record(URI, "", null, "{\"style\":\"暗夜黑\"}", 2000L));

        ExportHistory latest = dao.getLastConfigByUri(URI);
        assertNotNull(latest);
        assertEquals("应当取时间戳最大的那条", 2000L, latest.timestamp);

        // 这正是「胶片导出要先读回上一条再把 filmJson 并进去」的原因：
        // 直接插一行只有 filmJson 的记录，海报那一半就再也取不回来了。
        FilmBorderConfig film = FilmBorderConfig.fromJson(latest.filmJson, null);
        assertNotNull(film);
        assertEquals(FilmStock.STYLE_NOIR, film.style);
    }

    @Test
    public void mergingKeepsThePosterHalfOfTheRecord() {
        // 1. 先有一次海报导出：记下了渲染模式与明信片文案
        ExportHistory poster = record(URI,
                "{\"mode\":\"取反差色\",\"style\":\"马赛克化\"}",
                "{\"title\":\"Forest Homestead\"}",
                "{\"style\":\"经典白框\"}", 1000L);
        dao.insertHistory(poster);

        // 2. 之后有一次胶片导出：把上一条读回来，只替换 filmJson
        ExportHistory previous = dao.getLastConfigByUri(URI);
        assertNotNull(previous);

        ExportHistory merged = new ExportHistory(
                previous.originalUri, previous.optionsJson, previous.sign, "/out/film.png",
                System.currentTimeMillis(),
                previous.device, previous.lens, previous.shutter, previous.aperture, previous.iso,
                previous.zineJson,
                "{\"style\":\"柯达金\"}");
        dao.insertHistory(merged);

        // 3. 再次导入这张照片：海报那一半还在，胶片设置也拿得到
        ExportHistory loaded = dao.getLastConfigByUri(URI);
        assertNotNull(loaded);
        assertEquals("海报的渲染模式不能被胶片导出顶掉",
                "{\"mode\":\"取反差色\",\"style\":\"马赛克化\"}", loaded.optionsJson);
        assertEquals("{\"title\":\"Forest Homestead\"}", loaded.zineJson);

        FilmBorderConfig film = FilmBorderConfig.fromJson(loaded.filmJson, null);
        assertNotNull(film);
        assertEquals(FilmStock.STYLE_KODAK, film.style);
    }

    @Test
    public void otherPhotosAreNotMixedUp() {
        dao.insertHistory(record(URI, "{\"mode\":\"默认渲染\"}", null, null, 1000L));
        dao.insertHistory(record("content://other/7", "{\"mode\":\"突出原色\"}", null, null, 2000L));

        ExportHistory first = dao.getLastConfigByUri(URI);
        assertNotNull(first);
        assertEquals("{\"mode\":\"默认渲染\"}", first.optionsJson);

        assertNull("没记录过的照片应当查不到", dao.getLastConfigByUri("content://nobody/0"));
    }

    @Test
    public void oldRowsWithoutFilmJsonStillLoad() {
        // v3 时代写下的记录：filmJson 为 null，不该让读取炸掉
        dao.insertHistory(record(URI, "{\"mode\":\"默认渲染\"}", "{\"title\":\"老记录\"}", null, 1000L));

        ExportHistory loaded = dao.getLastConfigByUri(URI);
        assertNotNull(loaded);
        assertNull(loaded.filmJson);
        assertEquals("{\"title\":\"老记录\"}", loaded.zineJson);

        // 胶片侧拿到 null 应当安静跳过，而不是抛异常
        FilmBorderConfig restored = FilmBorderConfig.fromJson(loaded.filmJson, null);
        assertNull(restored);

        List<ExportHistory> all = dao.getAllHistory();
        assertEquals(1, all.size());
    }

    @Test
    public void clearingWipesEverything() {
        dao.insertHistory(record(URI, "{}", null, "{}", 1000L));
        dao.insertHistory(record("content://other/7", "{}", null, "{}", 2000L));
        assertEquals(2, dao.getAllHistory().size());

        dao.clearAllHistory();
        assertEquals(0, dao.getAllHistory().size());
    }

    @Test
    public void deleteRemovesOnlyThatRow() {
        dao.insertHistory(record(URI, "{}", null, "{}", 1000L));
        dao.insertHistory(record("content://other/7", "{}", null, "{}", 2000L));

        ExportHistory target = dao.getLastConfigByUri(URI);
        assertNotNull(target);
        dao.deleteHistory(target);

        List<ExportHistory> remaining = dao.getAllHistory();
        assertEquals(1, remaining.size());
        assertEquals("content://other/7", remaining.get(0).originalUri);
        assertNull(dao.getLastConfigByUri(URI));
    }

    @Test
    public void historyIsOrderedNewestFirst() {
        dao.insertHistory(record(URI, "{}", null, null, 1000L));
        dao.insertHistory(record("content://other/7", "{}", null, null, 3000L));
        dao.insertHistory(record("content://third/9", "{}", null, null, 2000L));

        List<ExportHistory> all = dao.getAllHistory();
        List<Long> stamps = Arrays.asList(all.get(0).timestamp, all.get(1).timestamp, all.get(2).timestamp);
        assertEquals(Arrays.asList(3000L, 2000L, 1000L), stamps);
    }
}
