package com.example.lbsdemo.task;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskVerificationDao {
    
    @Insert
    long insertVerification(TaskVerificationData verification);
    
    @Update
    void updateVerification(TaskVerificationData verification);
    
    @Query("SELECT * FROM task_verification WHERE task_id = :taskId ORDER BY timestamp DESC LIMIT 1")
    TaskVerificationData getVerificationForTask(int taskId);
    
    @Query("SELECT * FROM task_verification WHERE task_id = :taskId AND verification_type = :verificationType ORDER BY timestamp DESC LIMIT 1")
    TaskVerificationData getVerificationByType(int taskId, String verificationType);
    
    @Query("SELECT * FROM task_verification WHERE user_id = :userId ORDER BY timestamp DESC")
    List<TaskVerificationData> getVerificationsForUser(String userId);
    
    @Query("SELECT * FROM task_verification WHERE task_id = :taskId ORDER BY timestamp DESC")
    List<TaskVerificationData> getAllVerificationsForTask(int taskId);
    
    @Query("SELECT COUNT(*) FROM task_verification WHERE task_id = :taskId AND verification_result = 1")
    int countSuccessfulVerifications(int taskId);
} 