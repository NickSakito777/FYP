// 创建新文件: LocationHistoryDao.java
package com.example.lbsdemo.user;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocationHistoryDao {
    @Insert
    long insert(LocationHistoryData data);

    @Update
    void update(LocationHistoryData data);

    @Query("SELECT * FROM location_history WHERE user_id = :userId ORDER BY start_time DESC")
    List<LocationHistoryData> getUserLocationHistory(String userId);

    @Query("SELECT * FROM location_history WHERE user_id = :userId AND building_id = :buildingId AND end_time = 0 LIMIT 1")
    LocationHistoryData getActiveLocationSession(String userId, String buildingId);

    @Query("SELECT building_id, SUM(duration_minutes) as total_time, COUNT(*) as visit_count FROM location_history WHERE user_id = :userId GROUP BY building_id ORDER BY total_time DESC")
    List<BuildingStatistics> getBuildingStatistics(String userId);

    @Query("SELECT building_id, SUM(duration_minutes) as total_time, COUNT(*) as visit_count FROM location_history WHERE user_id = :userId AND visit_date >= :startDate AND visit_date <= :endDate GROUP BY building_id")
    List<BuildingStatistics> getBuildingStatisticsByDateRange(String userId, String startDate, String endDate);

    @Insert
    long insertLocationHistory(LocationHistoryData locationHistory);
}

// 用于统计查询结果的POJO类
class BuildingStatistics {
    public String building_id;
    public int total_time;
    public int visit_count;
}
