package com.example.photopalettepro;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * 用 Robolectric 的 NATIVE 图形模式（真实 Skia）渲染 Zine 明信片并导出 PNG，
 * 便于在不启动设备的情况下肉眼校验版式与配色。
 *
 * 产物：app/build/zine-out/front.png、back.png
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZineRenderSmokeTest {

    @Test
    public void renderFrontAndBackToPng() throws Exception {
        Bitmap photo = makeSamplePhoto(1200, 800);

        ZinePostcardConfig cfg = new ZinePostcardConfig();
        cfg.title = "Alpine Study";
        cfg.subtitle = "Postcard Study";
        cfg.location = "31.23°N 121.47°E";
        cfg.date = "2025.05.17";
        cfg.index = "002";

        File dir = new File("build/zine-out");
        assertTrue(dir.exists() || dir.mkdirs());

        // 三种取色逻辑各渲染一次，确认明信片确实跟随主页面的取色逻辑
        String[] modes = {"默认渲染", "取反差色", "突出原色"};
        for (int i = 0; i < modes.length; i++) {
            // 与 App 一致：保留该色板的全部颜色（最多 6 个）
            List<Integer> palette = ColorExtractor.getTopWeightedColors(photo, modes[i], 6);
            assertNotNull(palette);
            assertTrue(modes[i] + " 应至少取到 1 个颜色", palette.size() > 0);
            System.out.println("[" + modes[i] + "] 色板(" + palette.size() + " 色) = " + toHex(palette));

            Bitmap front = ZinePostcardRenderer.renderFront(photo, palette, cfg, 1200);
            assertNotNull(front);
            assertTrue("front 应为 4:3 横版",
                    front.getWidth() * 3 == front.getHeight() * 4);
            writePng(front, new File(dir, "front-mode" + i + ".png"));
        }

        List<Integer> defPalette = ColorExtractor.getTopWeightedColors(photo, "默认渲染", 6);
        Bitmap back = ZinePostcardRenderer.renderBack(photo, defPalette, cfg, 1200);
        assertNotNull(back);
        assertTrue("back 应为 4:3 横版", back.getWidth() * 3 == back.getHeight() * 4);
        writePng(back, new File(dir, "back.png"));
    }

    /**
     * 用真实照片跑一遍：合成测试图太平滑，测不出高频细节的处理能力。
     */
    @Test
    public void renderRealPhotoToPng() throws Exception {
        File source = new File(
                "E:/Data/AndroidWorkSpace/PhotoPalettePro/build/zine-ref/02-winter-crossing-source.jpg");
        if (!source.exists()) {
            System.out.println("跳过真实样张测试：找不到 " + source.getAbsolutePath());
            return;
        }

        Bitmap photo = BitmapFactory.decodeFile(source.getAbsolutePath());
        assertNotNull(photo);

        List<Integer> palette = ColorExtractor.getTopWeightedColors(photo, "默认渲染", 6);
        ZinePostcardConfig cfg = new ZinePostcardConfig();
        cfg.title = "Forest Homestead";
        cfg.subtitle = "Postcard Study";
        cfg.location = "43.21°N 87.65°E";
        cfg.date = "2025.07.02";
        cfg.index = "004";

        File dir = new File("build/zine-out");
        assertTrue(dir.exists() || dir.mkdirs());

        Bitmap front = ZinePostcardRenderer.renderFront(photo, palette, cfg, 1200);
        assertNotNull(front);
        writePng(front, new File(dir, "front-real.png"));

        // 增强现实：按拉普拉斯信息密度把原照片软边嵌回插画
        ZinePostcardConfig arCfg = new ZinePostcardConfig();
        arCfg.title = cfg.title;
        arCfg.subtitle = cfg.subtitle;
        arCfg.location = cfg.location;
        arCfg.date = cfg.date;
        arCfg.index = cfg.index;
        arCfg.realityAnchor = true;
        Bitmap augmented = ZinePostcardRenderer.renderFront(photo, palette, arCfg, 1200);
        assertNotNull(augmented);
        writePng(augmented, new File(dir, "front-real-ar.png"));
        System.out.println("真实样张已渲染 -> front-real.png / front-real-ar.png");
    }

    private static String toHex(List<Integer> colors) {
        StringBuilder sb = new StringBuilder();
        for (int c : colors) {
            sb.append(String.format("#%06X ", 0xFFFFFF & c));
        }
        return sb.toString().trim();
    }

    private static void writePng(Bitmap bmp, File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    /** 合成一张有明确主色特征的风景照，方便观察版面与取色 */
    private static Bitmap makeSamplePhoto(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 天空
        p.setShader(new LinearGradient(0, 0, 0, h * 0.62f,
                Color.rgb(118, 168, 210), Color.rgb(228, 216, 192), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.62f, p);
        p.setShader(null);

        // 远山
        p.setColor(Color.rgb(94, 112, 104));
        Path m = new Path();
        m.moveTo(0, h * 0.62f);
        m.lineTo(w * 0.20f, h * 0.32f);
        m.lineTo(w * 0.38f, h * 0.58f);
        m.lineTo(w * 0.56f, h * 0.28f);
        m.lineTo(w * 0.78f, h * 0.56f);
        m.lineTo(w, h * 0.38f);
        m.lineTo(w, h * 0.62f);
        m.close();
        c.drawPath(m, p);

        // 湖水（主色特征：青绿）
        p.setShader(new LinearGradient(0, h * 0.62f, 0, h,
                Color.rgb(62, 158, 160), Color.rgb(26, 90, 104), Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.62f, w, h, p);
        p.setShader(null);

        // 岸边浅滩
        p.setColor(Color.rgb(214, 202, 168));
        c.drawRect(0, h * 0.615f, w, h * 0.645f, p);

        // 暖色点缀
        p.setColor(Color.rgb(208, 124, 76));
        c.drawCircle(w * 0.74f, h * 0.20f, h * 0.052f, p);

        return b;
    }
}
