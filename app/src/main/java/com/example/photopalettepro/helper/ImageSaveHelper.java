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
 */
public class ImageSaveHelper {

    private static final String TAG = "ImageSave";
    private static final String APP_FOLDER = "PhotoPalettePro";

    private final Context context;
    private final SaveCallback callback;
    // 用于确保回调在主线程执行
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SaveCallback {
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    public ImageSaveHelper(Context context, SaveCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * 保存图片到相册
     */
    public void saveBitmapToGallery(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            sendError("图片无效或已被回收");
            return;
        }

        new Thread(() -> {
            try {
                String fileName = generateFileName();

                // Android 10 (Q) 及以上使用 MediaStore API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(bitmap, fileName);
                } else {
                    saveWithLegacyAPI(bitmap, fileName);
                }

                // 【关键修改】保存成功后及时回收高清位图，释放内存
                bitmap.recycle();

                sendSuccess("已成功导出到相册");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save bitmap", e);
                sendError("保存失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Android Q (10) 及以上的保存方式 (分区存储)
     */
    private void saveWithMediaStore(Bitmap bitmap, String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        // Android 10 及以上支持 RELATIVE_PATH
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + APP_FOLDER);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                }
            }
        } else {
            throw new Exception("无法创建媒体文件路径");
        }
    }

    /**
     * Android Q 以下的保存方式 (传统文件 API)
     */
    private void saveWithLegacyAPI(Bitmap bitmap, String fileName) throws Exception {
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
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
    }

    private String generateFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        return "PPP_" + timeStamp + ".png";
    }

    // 辅助方法：确保回调在主线程
    private void sendSuccess(String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(msg);
        });
    }

    // 辅助方法：确保回调在主线程
    private void sendError(String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(msg);
        });
    }
}