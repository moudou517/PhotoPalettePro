package com.example.photopalettepro.helper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.media.MediaScannerConnection;

/**
 * 图片保存助手类
 * 修复点：增加主线程回调保障、自动回收内存、优化版本兼容性
 * 增强点：成功回调时返回真实的图片文件路径或 URI，供 Room 历史记录使用
 */
public class ImageSaveHelper {

    private static final String TAG = "ImageSave";
    private static final String APP_FOLDER = "PhotoPalettePro";

    private final Context context;
    private final SaveCallback callback;
    // 用于确保回调在主线程执行
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SaveCallback {
        // 修改：此处的 message 将会承载保存成功后的真实路径/URI 字符串
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    public ImageSaveHelper(Context context, SaveCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * 异步保存 Bitmap 到相册
     */
    public void saveBitmapToGallery(final Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            sendError("无法保存空图片或已被回收的图片");
            return;
        }

        new Thread(() -> {
            Bitmap targetBitmap = bitmap;
            try {
                String fileName = generateFileName();
                String savedPath;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    savedPath = saveWithScopedStorage(targetBitmap, fileName);
                } else {
                    savedPath = saveWithLegacyAPI(targetBitmap, fileName);
                }

                // 成功后传递真实路径
                sendSuccess(savedPath);
            } catch (Exception e) {
                Log.e(TAG, "Save failed", e);
                sendError("保存失败: " + e.getMessage());
            } finally {
                // 核心修复：在这里统一回收由 PosterRenderer 生成的高清大图，防止 MainActivity 闪退或内存泄漏
                if (targetBitmap != null && !targetBitmap.isRecycled()) {
                    targetBitmap.recycle();
                }
            }
        }).start();
    }

    /**
     * Android Q (10) 及以上的 Scoped Storage 保存方式
     */
    private String saveWithScopedStorage(Bitmap bitmap, String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + APP_FOLDER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri itemUri = resolver.insert(collectionUri, values);

        if (itemUri == null) {
            throw new Exception("无法在 MediaStore 中创建新记录");
        }

        try (OutputStream os = resolver.openOutputStream(itemUri)) {
            if (os == null) {
                throw new Exception("无法打开输出流");
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);
        }

        return itemUri.toString(); // 返回保存成功的媒体库 content:// URI 字符串
    }

    /**
     * Android Q 以下的保存方式 (传统文件 API)
     */
    private String saveWithLegacyAPI(Bitmap bitmap, String fileName) throws Exception {
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File appDir = new File(storageDir, APP_FOLDER);

        if (!appDir.exists() && !appDir.mkdirs()) {
            throw new Exception("无法创建文件夹");
        }

        File imageFile = new File(appDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

            // 通知媒体库更新
            MediaScannerConnection.scanFile(context,
                    new String[]{imageFile.getAbsolutePath()},
                    new String[]{"image/png"}, null);
        }
        return imageFile.getAbsolutePath(); // 返回物理绝对路径
    }

    private String generateFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "PPP_" + timeStamp + ".png"; // 移除了 \
    }

    // 辅助方法：确保回调在主线程
    private void sendSuccess(String path) {
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(path);
        });
    }

    // 辅助方法：确保回调在主线程
    private void sendError(String errorMsg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(errorMsg);
        });
    }
}