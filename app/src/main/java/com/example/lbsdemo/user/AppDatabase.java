// AppDatabase.java
package com.example.lbsdemo.user;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {User.class, QuestionnaireData.class},
        version = 5) // 版本号需递增 [^1]
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    // 必须声明所有DAO接口
    public abstract UserDao userDao();
    public abstract QuestionnaireDao questionnaireDao(); // 新增DAO声明 [^2]

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "app_db"
                            )
//                            .addMigrations(MIGRATION_2_3) // 添加版本迁移
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

//    // === 版本迁移 ===
//    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
//        @Override
//        public void migrate(SupportSQLiteDatabase database) {
//            // 创建新表（问卷数据）
//            database.execSQL(
//                    "CREATE TABLE questionnaire_data (" +
//                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
//                            "user_id TEXT, " +
//                            "geo_data TEXT, " +
//                            "submit_time INTEGER)"
//            );
//        }
//    };
}
