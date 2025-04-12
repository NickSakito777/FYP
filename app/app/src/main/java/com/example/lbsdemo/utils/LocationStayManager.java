// 创建新文件：LocationStayManager.java
package com.example.lbsdemo.utils;

import android.content.Context;
import android.util.Log;

import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.LocationHistoryDao;
import com.example.lbsdemo.user.LocationHistoryData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理用户位置停留记录的类
 */
public class LocationStayManager {
    private static final String TAG = "LocationStayManager";
    private static final long MINIMUM_STAY_TIME_MS = 5 * 60 * 1000; // 5分钟，单位毫秒

    private final Context context;
    private final Map<String, UserStayInfo> activeStays; // 跟踪用户当前的停留
    private final ExecutorService executor;
    private String userId; // 当前用户ID

    public LocationStayManager(Context context) {
        this.context = context;
        this.activeStays = new HashMap<>();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 设置当前用户ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 用户进入某个地理围栏区域
     */
    public void onUserEnterLocation(String buildingId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "用户ID未设置，无法记录停留");
            return;
        }

        if (!activeStays.containsKey(buildingId)) {
            UserStayInfo stayInfo = new UserStayInfo(userId, buildingId, System.currentTimeMillis());
            activeStays.put(buildingId, stayInfo);
            Log.i(TAG, "用户开始在 " + buildingId + " 区域停留");
        }
    }

    /**
     * 用户离开某个地理围栏区域
     */
    public void onUserLeaveLocation(String buildingId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        UserStayInfo stayInfo = activeStays.remove(buildingId);
        if (stayInfo != null) {
            long stayDurationMs = System.currentTimeMillis() - stayInfo.startTime;

            // 只记录超过最小停留时间的停留
            if (stayDurationMs >= MINIMUM_STAY_TIME_MS) {
                int durationMinutes = (int) (stayDurationMs / (60 * 1000));
                saveStayRecord(stayInfo.userId, stayInfo.buildingId, stayInfo.startTime,
                        System.currentTimeMillis(), durationMinutes);
                Log.i(TAG, "记录用户在 " + buildingId + " 停留了 " + durationMinutes + " 分钟");
            } else {
                Log.i(TAG, "用户在 " + buildingId + " 停留未超过5分钟，不记录");
            }
        }
    }

    /**
     * 保存停留记录到数据库
     */
    private void saveStayRecord(String userId, String buildingId, long startTime,
                                long endTime, int durationMinutes) {
        executor.execute(() -> {
            try {
                LocationHistoryData data = new LocationHistoryData();
                data.userId = userId;
                data.buildingId = buildingId;
                data.startTime = startTime;
                data.endTime = endTime;
                data.durationMinutes = durationMinutes;

                // 设置访问日期（年-月-日格式）
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                data.visitDate = dateFormat.format(new Date(startTime));

                // 保存到数据库
                AppDatabase db = AppDatabase.getInstance(context);
                LocationHistoryDao dao = db.locationHistoryDao();
                long id = dao.insert(data);

                Log.i(TAG, "停留记录保存成功，ID: " + id);
            } catch (Exception e) {
                Log.e(TAG, "保存停留记录失败", e);
            }
        });
    }

    /**
     * 用于存储用户当前停留信息的内部类
     */
    private static class UserStayInfo {
        final String userId;
        final String buildingId;
        final long startTime;

        UserStayInfo(String userId, String buildingId, long startTime) {
            this.userId = userId;
            this.buildingId = buildingId;
            this.startTime = startTime;
        }
    }
}
