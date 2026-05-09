package com.example.photopalettepro.helper;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;

/**
 * 图片处理助手类
 * 职责：处理图片加载、采样、缩放等操作
 */
public class ImageProcessingHelper {

    private static final String TAG = "ImageProcessing";

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
     * 从 URI 加载原始图片尺寸
     */
    public static BitmapFactory.Options getImageDimensions(ContentResolver contentResolver, Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options);
        } catch (IOException e) {
            Log.e(TAG, "Failed to decode image dimensions", e);
        }
        return options;
    }

    /**
     * 加载高清图片（用于处理和导出）
     */
    public static Bitmap loadSourceBitmap(ContentResolver contentResolver, Uri uri, 
                                         int maxWidth, int maxHeight) {
        BitmapFactory.Options options = getImageDimensions(contentResolver, uri);
        options.inJustDecodeBounds = false;
        options.inMutable = true;
        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);

        try {
            return BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load source bitmap", e);
            return null;
        }
    }

    /**
     * 加载预览图片（用于 UI 显示）
     */
    public static Bitmap loadPreviewBitmap(ContentResolver contentResolver, Uri uri, 
                                          int maxWidth, int maxHeight) {
        BitmapFactory.Options options = getImageDimensions(contentResolver, uri);
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);

        try {
            return BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load preview bitmap", e);
            return null;
        }
    }

    /**
     * 读取照片总像素数
     */
    public static long readPhotoDimensions(ContentResolver contentResolver, Uri uri, 
                                          int[] widthHeight) {
        BitmapFactory.Options options = getImageDimensions(contentResolver, uri);
        int width = options.outWidth;
        int height = options.outHeight;
        
        if (widthHeight != null && widthHeight.length >= 2) {
            widthHeight[0] = width;
            widthHeight[1] = height;
        }
        
        return (long) width * height;
    }

    /**
     * 检查是否为高清图片（超过 40MP）
     */
    public static boolean isHighResolution(long totalPixels) {
        return totalPixels > 40000000;
    }

    /**
     * 回收 Bitmap
     */
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

