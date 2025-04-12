// 创建新文件: LocationHistoryData.java
package com.example.lbsdemo.user;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_history",
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "studentId",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("user_id")})
public class LocationHistoryData {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "building_id")
    public String buildingId; // 建筑物标识符

    @ColumnInfo(name = "start_time")
    public long startTime;  // 开始时间戳

    @ColumnInfo(name = "end_time")
    public long endTime;    // 结束时间戳

    @ColumnInfo(name = "duration_minutes")
    public int durationMinutes; // 停留分钟数

    @ColumnInfo(name = "visit_date")
    public String visitDate; // 访问日期，格式为 "yyyy-MM-dd"

    @ColumnInfo(name = "timestamp")
    public long timestamp;
    
    @ColumnInfo(name = "latitude")
    public Double latitude; // 纬度
    
    @ColumnInfo(name = "longitude")
    public Double longitude; // 经度
    
    @ColumnInfo(name = "location_name")
    public String locationName; // 位置名称

    @ColumnInfo(name = "is_inside")
    public Integer isInside; // 是否在指定区域内：1表示在内，0表示在外
}
