package com.example.photopalettepro.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExportHistoryRepository {

    private final ExportHistoryDao dao;
    private final ExecutorService executor;
    // 新增：用于将数据库查询结果安全地切回主线程的回调 Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ExportHistoryRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.dao = db.exportHistoryDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    // 异步插入记录
    public void insertHistory(ExportHistory history, OnRecordSavedListener listener) {
        executor.execute(() -> {
            long id = dao.insertHistory(history);
            if (listener != null) {
                // 切回主线程回调
                mainHandler.post(() -> listener.onSaved(id));
            }
        });
    }

    // 异步获取单张图片的上一次配置
    public void getLastConfig(String uri, OnConfigLoadedListener listener) {
        executor.execute(() -> {
            ExportHistory history = dao.getLastConfigByUri(uri);
            if (listener != null) {
                // 切回主线程回调
                mainHandler.post(() -> listener.onLoaded(history));
            }
        });
    }

    // 异步获取所有历史记录
    public void getAllHistory(OnAllHistoryLoadedListener listener) {
        executor.execute(() -> {
            List<ExportHistory> list = dao.getAllHistory();
            if (listener != null) {
                // 切回主线程回调
                mainHandler.post(() -> listener.onLoaded(list));
            }
        });
    }

    // 定义各种回调接口
    public interface OnRecordSavedListener {
        void onSaved(long id);
    }

    public interface OnConfigLoadedListener {
        void onLoaded(ExportHistory history);
    }

    public interface OnAllHistoryLoadedListener {
        void onLoaded(List<ExportHistory> historyList);
    }
}