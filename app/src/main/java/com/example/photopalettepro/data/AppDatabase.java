package com.example.photopalettepro.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ExportHistory.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /**
     * v2 → v3：新增 zineJson 列（Zine 明信片信息）。
     * 用正式迁移而不是破坏性迁移，保证老用户已有的导出历史不丢。
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE export_history ADD COLUMN zineJson TEXT");
        }
    };

    public abstract ExportHistoryDao exportHistoryDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "photo_palette_pro_db")
                            .addMigrations(MIGRATION_2_3)
                            // 兜底：以后若漏写迁移，宁可重建也不要闪退
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}