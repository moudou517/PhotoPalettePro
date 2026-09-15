package com.example.photopalettepro.helper;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 图片处理助手类
 * 职责：图片尺寸读取、解码、采样、缩放。
 *
 * <h3>为什么不直接用 {@code BitmapFactory.decodeStream(openInputStream(uri))}</h3>
 *
 * <p>这是本项目踩过的最狠的一个坑，值得写在这里。
 *
 * <p>当 URI 由 ContentProvider 提供时，{@code ContentResolver.openInputStream()} 往往
 * 返回一个带 {@link android.content.res.AssetFileDescriptor} 的流，而
 * {@code BitmapFactory.decodeStream()} 会把这个 AFD 里声明的<b>起点与长度直接交给
 * native 解码器</b>。系统相册、以及 Android 14+ 的系统相册选择器
 * （{@code content://media/picker_get_content/...}）给出的长度未必可靠——
 * 一旦对不上，native 侧就是一次越界访问。
 *
 * <p>后果特别有迷惑性：<b>直接 SIGSEGV，Java 层连异常都收不到</b>，
 * 所以既没有堆栈、也没有崩溃回调，表现就是「点导入秒退，什么都没有」。
 * 真机日志证实了这一点：面包屑停在「loadSourceImage 开始」，
 * 连下一条「读尺寸」都没写出来，而日志里完全没有崩溃记录。
 *
 * <p>所以本类里所有解码都走 {@link #decodeSafely}：
 * <b>只让 native 解码器拿到一个真正可 seek 的输入</b>——
 * 优先用文件描述符（文件型 URI 走这条，最快最省内存），
 * 拿不到就把流落盘成临时文件再解。
 * 顺带还省掉了 decodeStream 内部把整个流拷进内存的那一份开销。
 */
public class ImageProcessingHelper {

    private static final String TAG = "ImageProcessing";

    /** 解码内存不足时的额外重试次数（每次把采样率翻倍）。 */
    private static final int MAX_DECODE_RETRIES = 2;

    /** 只读图片边界时最多预读这么多字节——图片头都在最前面。 */
    private static final int BOUNDS_PEEK_BYTES = 512 * 1024;

    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    /**
     * 计算最优采样率
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 按「最长边不超过 {@code maxDimension}」算采样率。
     *
     * <p>{@link #calculateInSampleSize} 要求<b>两个方向都超标</b>才会降采样，
     * 于是 4000×3000 的照片求 2000 时它返回 1 —— 等于把整张原图（48MB）
     * 解出来当「预览图」。预览要的是「最长边」这个语义，所以另开一个：
     * 同一张照片求 1600 会得到 inSampleSize 2（2000×1500），再收一道到 1600×1200，
     * 内存从 48MB 掉到 7.7MB。
     */
    public static int calculateInSampleSizeForMaxDimension(BitmapFactory.Options options,
                                                           int maxDimension) {
        int maxSide = Math.max(options.outWidth, options.outHeight);
        if (maxDimension <= 0 || maxSide <= maxDimension) return 1;

        int inSampleSize = 1;
        while (maxSide / (inSampleSize * 2) >= maxDimension) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    // ====================================================================
    //  读数
    // ====================================================================

    /** 读取照片总像素数，并把宽高一并写进 {@code widthHeight}。 */
    public static long readPhotoDimensions(Context context, Uri uri, int[] widthHeight) {
        BitmapFactory.Options options = getImageDimensions(context, uri);
        int width = options.outWidth;
        int height = options.outHeight;

        if (widthHeight != null && widthHeight.length >= 2) {
            widthHeight[0] = width;
            widthHeight[1] = height;
        }
        return (long) width * height;
    }

    /** 只读宽高，不解像素。 */
    public static BitmapFactory.Options getImageDimensions(Context context, Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        if (context == null || uri == null) return options;

        decodeSafely(context, uri, options, true);

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            // 头部比预读的那点还长（很少见）：老老实实整个读一遍再来
            decodeSafely(context, uri, options, false);
        }
        return options;
    }

    // ====================================================================
    //  解码
    // ====================================================================

    /** 加载高清图片（用于处理和导出）。 */
    public static Bitmap loadSourceBitmap(Context context, Uri uri,
                                          int maxWidth, int maxHeight) {
        BitmapFactory.Options options = getImageDimensions(context, uri);
        options.inJustDecodeBounds = false;
        options.inMutable = true;
        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);
        return decodeWithRetry(context, uri, options);
    }

    /**
     * 加载预览图片（用于 UI 显示）。
     *
     * <p>采样按最长边算，再把结果精确收进目标尺寸——预览图给整屏和磨砂背景用，
     * 不需要、也养不起一张全尺寸原图。
     */
    public static Bitmap loadPreviewBitmap(Context context, Uri uri,
                                           int maxWidth, int maxHeight) {
        int maxDimension = Math.max(maxWidth, maxHeight);

        BitmapFactory.Options options = getImageDimensions(context, uri);
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateInSampleSizeForMaxDimension(options, maxDimension);

        Bitmap decoded = decodeWithRetry(context, uri, options);
        return fitWithin(decoded, maxWidth, maxHeight);
    }

    /**
     * 解码，并在内存不足时降一档采样重试。
     *
     * <p>同样一张 12MP 照片，在不同机器上的可用堆差别很大：高端机随便过，
     * 低端机上就是一次 {@link OutOfMemoryError}——而 OOM 是 {@link Error}，
     * 调用方那些 {@code catch (Exception)} 一个都拦不住，App 直接没了。
     * 降一档采样再试，换来的是「图糊一点」，而不是「App 没了」。
     */
    private static Bitmap decodeWithRetry(Context context, Uri uri, BitmapFactory.Options options) {
        for (int attempt = 0; attempt <= MAX_DECODE_RETRIES; attempt++) {
            try {
                return decodeSafely(context, uri, options, false);
            } catch (OutOfMemoryError oom) {
                int next = Math.max(2, options.inSampleSize * 2);
                Log.w(TAG, "解码内存不足，采样率 " + options.inSampleSize + " → " + next + " 后重试");
                options.inSampleSize = next;
            } catch (Exception e) {
                Log.e(TAG, "解码失败", e);
                return null;
            }
        }
        Log.e(TAG, "解码重试 " + MAX_DECODE_RETRIES + " 次仍然内存不足，放弃");
        return null;
    }

    /**
     * 安全解码：只让 native 解码器处理可 seek 的输入。
     *
     * @param boundsOnly 只读边界时为 true——这时只预读文件头，不必把整张图落盘
     */
    private static Bitmap decodeSafely(Context context, Uri uri, BitmapFactory.Options options,
                                       boolean boundsOnly) {
        ContentResolver resolver = context.getContentResolver();

        // 1) 真文件（相册里的原图大多是这种）：fd 可 seek，直接解，最快也最省内存。
        //    getStatSize() 对管道返回 0 / 负数，正好用来把它排除掉。
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd != null && pfd.getStatSize() > 0) {
                Bitmap decoded = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);
                if (decoded != null) return decoded;
            }
        } catch (Exception e) {
            Log.w(TAG, "按文件描述符解码失败，改走临时文件", e);
        }

        // 2) 管道（云相册、系统相册选择器）：落盘成一个真文件再解。
        //    这一步就是绕开 decodeStream 的 AFD 长度问题——见类注释。
        File temp = copyToTempFile(context, uri, boundsOnly);
        if (temp == null) return null;
        try {
            return BitmapFactory.decodeFile(temp.getAbsolutePath(), options);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** 把 URI 的字节流复制进缓存目录里的临时文件。 */
    private static File copyToTempFile(Context context, Uri uri, boolean boundsOnly) {
        File dir = context.getCacheDir();
        if (dir == null) return null;

        File temp;
        try {
            temp = File.createTempFile("decode-", ".tmp", dir);
        } catch (Exception e) {
            Log.e(TAG, "创建临时文件失败", e);
            return null;
        }

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(temp)) {

            if (is == null) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                return null;
            }

            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            long written = 0;
            long limit = boundsOnly ? BOUNDS_PEEK_BYTES : Long.MAX_VALUE;
            int read;
            while (written < limit && (read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
                written += read;
            }
        } catch (Exception e) {
            Log.e(TAG, "读取图片流失败", e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return null;
        }
        return temp;
    }

    // ====================================================================
    //  收口
    // ====================================================================

    /**
     * 把已经解码出来的位图收进指定尺寸内。
     *
     * <p>因为 {@code inSampleSize} 只能取 2 的幂，{@link #loadSourceBitmap} 的
     * 实际结果常常是目标的 1~2 倍。单张时这点富余无所谓，但多张合成时
     * 「富余 × 张数」正是最容易 OOM 的地方——所以按精确的格子尺寸再收一道。
     *
     * @return 收缩后的位图；原本就在范围内时原样返回
     */
    public static Bitmap fitWithin(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap == null || bitmap.isRecycled()) return bitmap;
        if (maxWidth <= 0 || maxHeight <= 0) return bitmap;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return bitmap;

        float scale = Math.min(maxWidth / (float) width, maxHeight / (float) height);
        if (scale >= 1f) return bitmap;

        int targetW = Math.max(1, Math.round(width * scale));
        int targetH = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
        if (scaled != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return scaled;
    }

    /** 检查是否为高清图片（超过 40MP） */
    public static boolean isHighResolution(long totalPixels) {
        return totalPixels > 40000000;
    }

    /** 回收 Bitmap */
    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    /**
     * 创建缩放后的预览图（防止 Canvas 过大）
     */
    public static Bitmap createPreviewFromResult(Bitmap result, int scaleFactor) {
        if (result == null) return null;

        int previewWidth = Math.max(1, result.getWidth() / scaleFactor);
        int previewHeight = Math.max(1, result.getHeight() / scaleFactor);

        return Bitmap.createScaledBitmap(result, previewWidth, previewHeight, true);
    }
}
