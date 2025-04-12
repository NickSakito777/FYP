package com.example.lbsdemo.user;

import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
// QuestionnaireData.java
@Entity(tableName = "questionnaire_data")
public class QuestionnaireData {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "time_table")
    public String timeTable;

    @ColumnInfo(name = "submit_time")
    public long submitTime;

    @ColumnInfo(name = "study_duration")
    public String studyDuration; // 新增学习时长字段 [^section_study_habits.xml]

    @ColumnInfo(name = "time_pattern")
    public String timePattern;   // 新增时间模式字段 [^section_activity_preference.xml]
    // 新增多选字段
    @ColumnInfo(name = "learning_scenes")
    public String learningScenes;  // 对应"偏好哪种学习场景"

    @ColumnInfo(name = "building_preferences")
    public String buildingPreferences;  // 对应"上课和自习的教学楼"

    @ColumnInfo(name = "task_time_windows")
    public String taskTimeWindows;  // 对应"接受新任务时段"
    
    // 添加缺少的字段
    @ColumnInfo(name = "study_interests")
    public String studyInterests;  // 学习兴趣
    
    @ColumnInfo(name = "extracurricular_interests")
    public String extracurricularInterests;  // 课外兴趣
    
    @ColumnInfo(name = "frequent_places")
    public String frequentPlaces;  // 常去地点
    
    @ColumnInfo(name = "schedule")
    public String schedule;  // 课表
    
    @ColumnInfo(name = "available_time")
    public String availableTime;  // 可用时间
}


