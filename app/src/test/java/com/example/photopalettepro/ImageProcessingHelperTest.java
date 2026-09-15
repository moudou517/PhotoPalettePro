package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import com.example.photopalettepro.helper.ImageProcessingHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 解码尺寸的收口。
 *
 * <p>{@link ImageProcessingHelper#fitWithin} 是「十几张照片不炸内存」的最后一道闸：
 * {@code decodeStream} 的 {@code inSampleSize} 只能取 2 的幂，所以实际解码结果
 * 常常是目标的 1~2 倍，而这个「多出来的部分」会乘以张数。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageProcessingHelperTest {

    private static Bitmap bitmap(int w, int h) {
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    @Test
    public void shrinksWhenLargerThanTarget() {
        Bitmap source = bitmap(1000, 750);

        Bitmap result = ImageProcessingHelper.fitWithin(source, 600, 450);

        assertNotNull(result);
        assertTrue("结果不应超过目标宽度", result.getWidth() <= 600);
        assertTrue("结果不应超过目标高度", result.getHeight() <= 450);
        // 等比收缩：长宽比不变
        assertEquals(1000 / 750f, result.getWidth() / (float) result.getHeight(), 0.02f);
        assertTrue("原图应当被回收，避免多占一份内存", source.isRecycled());
    }

    @Test
    public void keepsTheSameInstanceWhenAlreadySmallEnough() {
        Bitmap source = bitmap(400, 300);

        Bitmap result = ImageProcessingHelper.fitWithin(source, 600, 450);

        assertSame("本来就在范围内就不该再复制一份", source, result);
        assertTrue(!source.isRecycled());
    }

    @Test
    public void limitsByTheBindingDimensionOnly() {
        // 只有高度超了：应当按高度收，宽度跟着等比缩，而不是两边都硬压到目标
        Bitmap source = bitmap(600, 1200);

        Bitmap result = ImageProcessingHelper.fitWithin(source, 600, 400);

        assertEquals("高度应当收到目标", 400, result.getHeight());
        assertEquals("宽度应按原比例缩到 200", 200, result.getWidth());
    }

    @Test
    public void handlesDegenerateInput() {
        assertEquals(null, ImageProcessingHelper.fitWithin(null, 100, 100));

        Bitmap source = bitmap(200, 200);
        assertSame("目标非法时原样返回", source, ImageProcessingHelper.fitWithin(source, 0, 0));
        assertSame(source, ImageProcessingHelper.fitWithin(source, -5, 100));
    }

    @Test
    public void recycledBitmapIsPassedThroughUntouched() {
        Bitmap source = bitmap(800, 600);
        source.recycle();

        // 已回收的位图不该再被碰，也不该抛
        assertSame(source, ImageProcessingHelper.fitWithin(source, 100, 100));
    }

    // ------------------------------------------------------------------
    //  预览图的采样率：必须按最长边算
    // ------------------------------------------------------------------

    @Test
    public void oldSamplerWouldDecodeTheWholePhotoForAPreview() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = 4000;
        options.outHeight = 3000;

        // 旧算法要求「两个方向都超标」才降采样。4000×3000 求 1600 时高度不超标，
        // 于是返回 1 —— 等于把整张 48MB 的原图解出来当预览。
        // 这条断言把这个坑钉住：算法不能悄悄改回去。
        assertEquals("旧算法在 4000×3000 求 1600 时会返回 1", 1,
                ImageProcessingHelper.calculateInSampleSize(options, 1600, 1600));
    }

    @Test
    public void previewSamplingUsesTheLongestEdge() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = 4000;
        options.outHeight = 3000;

        // 按最长边算：4000 → 2000 → 1000
        assertEquals(2, ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, 1600));
        assertEquals(2, ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, 2000));
        assertEquals(4, ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, 1000));
        // 本来就不超标：不采样
        assertEquals(1, ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, 4000));
        assertEquals(1, ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, 8000));
        // 非法目标：不采样，交给 fitWithin 收
        assertEquals(1, ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, 0));
    }

    @Test
    public void previewSamplingNeverOvershootsTheTarget() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = 8000;
        options.outHeight = 6000;

        // 采样只能取 2 的幂，所以结果可能仍然偏大——但不能偏小到糊
        for (int target : new int[]{400, 800, 1200, 1600, 2400, 3000}) {
            int sample = ImageProcessingHelper.calculateInSampleSizeForMaxDimension(options, target);
            int decodedMaxSide = 8000 / sample;
            assertTrue("采样后最长边 " + decodedMaxSide + " 不该小于目标 " + target,
                    decodedMaxSide >= target);
        }
    }

    // ------------------------------------------------------------------
    //  解码路径
    // ------------------------------------------------------------------

    /**
     * 真文件走 fd 那条路：能读出尺寸，也能解出像素。
     *
     * <p>注意这里验的是<b>结果正确</b>。真正的回归点是「绝不用
     * {@code BitmapFactory.decodeStream} 解 ContentProvider 的流」——
     * 那是 native 层的越界读（SIGSEGV），Robolectric 用桌面 Skia 解码，
     * 从原理上就复现不出来。那一条只能靠真机日志确认。
     */
    @Test
    public void decodesARealFileAndReadsItsDimensions() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Uri uri = samplePhotoUri(context, "plain.jpg", 1200, 800);

        int[] size = new int[2];
        long pixels = ImageProcessingHelper.readPhotoDimensions(context, uri, size);
        assertEquals(1200, size[0]);
        assertEquals(800, size[1]);
        assertEquals(1200L * 800L, pixels);

        Bitmap decoded = ImageProcessingHelper.loadPreviewBitmap(context, uri, 600, 600);
        assertNotNull("真文件必须能解出来", decoded);
        assertTrue(decoded.getWidth() <= 600 && decoded.getHeight() <= 600);
        assertEquals(1200 / 800f, decoded.getWidth() / (float) decoded.getHeight(), 0.05f);
    }

    /**
     * 管道型的 {@code content://} URI 必须落盘成真文件再解。
     *
     * <p>场景对应真机上的系统相册（云相册、Android 14+ 的
     * {@code picker_get_content}）：拿不到可 seek 的文件描述符。
     * 这里只注册了输入流、没有注册 fd，所以 {@code openFileDescriptor} 必然失败，
     * 代码只能走「落盘再解」那条路——尺寸仍然要读对。
     */
    @Test
    public void pipeBackedUriReadsDimensionsThroughATempFile() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        byte[] bytes = photoBytes(1600, 900);
        Uri uri = Uri.parse("content://com.example.photopalettepro.fake/photo");

        org.robolectric.Shadows.shadowOf(context.getContentResolver())
                .registerInputStream(uri, new java.io.ByteArrayInputStream(bytes));

        int[] size = new int[2];
        ImageProcessingHelper.readPhotoDimensions(context, uri, size);

        assertEquals("没有可 seek 的 fd 时，落盘之后也必须读对宽高", 1600, size[0]);
        assertEquals(900, size[1]);
    }

    @Test
    public void tempFilesAreCleanedUpAfterDecoding() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        byte[] bytes = photoBytes(800, 600);
        Uri uri = Uri.parse("content://com.example.photopalettepro.fake/cleanup");

        org.robolectric.Shadows.shadowOf(context.getContentResolver())
                .registerInputStream(uri, new java.io.ByteArrayInputStream(bytes));
        ImageProcessingHelper.readPhotoDimensions(context, uri, new int[2]);

        File[] leftovers = context.getCacheDir().listFiles(
                (dir, name) -> name.startsWith("decode-") && name.endsWith(".tmp"));
        assertTrue("临时的解码文件必须清掉，否则缓存目录会越积越大",
                leftovers == null || leftovers.length == 0);
    }

    /** 造一张照片并写成文件，返回它的 file:// URI。 */
    private static Uri samplePhotoUri(Context context, String name, int width, int height)
            throws Exception {
        File file = new File(context.getCacheDir(), name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(photoBytes(width, height));
        }
        return Uri.fromFile(file);
    }

    private static byte[] photoBytes(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);

        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, buffer);
        bitmap.recycle();
        return buffer.toByteArray();
    }
}




