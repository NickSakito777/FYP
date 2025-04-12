package com.example.lbsdemo.user;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

// 文件：QuestionnaireDao.java
@Dao
public interface QuestionnaireDao {
    @Insert
    void insert(QuestionnaireData data);

    @Query("SELECT * FROM questionnaire_data WHERE user_id = :userId")
    QuestionnaireData getByUserId(String userId);


    @Query("SELECT * FROM questionnaire_data")
    List<QuestionnaireData> getAllQuestionnaires(); // 修改方法名

    // 移除重复的getAll方法
}

