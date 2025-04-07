// TaskDao.java
package com.example.lbsdemo.task;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Insert
    long insertTask(TaskData task);

    @Update
    void updateTask(TaskData task);

    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND creation_date = :date")
    List<TaskData> getTasksForDate(String userId, String date);

    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND is_completed = 0")
    List<TaskData> getUncompletedTasks(String userId);

    @Query("DELETE FROM daily_tasks WHERE user_id = :userId AND creation_date = :date")
    void deleteTasksForDate(String userId, String date);

    @Query("SELECT * FROM daily_tasks WHERE id = :taskId")
    TaskData getTaskById(int taskId);
    
    @Query("SELECT * FROM daily_tasks WHERE location LIKE :locationPattern AND is_completed = 0")
    List<TaskData> getTasksByLocation(String locationPattern);
    
    /**
     * 获取用户特定角色的最新任务
     * @param userId 用户ID
     * @param characterId 虚拟角色ID
     * @return 最新的任务，如果没有则返回null
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND character_id = :characterId ORDER BY creation_timestamp DESC LIMIT 1")
    TaskData getLatestTaskByUserIdAndCharacterId(String userId, String characterId);
    
    /**
     * 获取用户最近完成的任务
     * @param userId 用户ID
     * @param limit 返回的任务数量限制
     * @return 最近完成的任务列表，按完成时间降序排序
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND is_completed = 1 ORDER BY creation_timestamp DESC LIMIT :limit")
    List<TaskData> getRecentCompletedTasks(String userId, int limit);
    
    /**
     * 查询特定类型的任务
     * @param userId 用户ID
     * @param taskType 任务类型
     * @return 指定类型的任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND task_type = :taskType")
    List<TaskData> getTasksByTypeAndUserId(String userId, String taskType);
    
    /**
     * 获取子任务列表
     * @param parentTaskId 父任务ID
     * @return 子任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE parent_task_id = :parentTaskId")
    List<TaskData> getTasksByParentId(int parentTaskId);
    
    /**
     * 获取用户所有未完成的任务
     * @param userId 用户ID
     * @return 未完成的任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND is_completed = 0")
    List<TaskData> getIncompleteTasksByUserId(String userId);
    
    /**
     * 获取用户特定类型的未完成任务
     * @param userId 用户ID
     * @param taskType 任务类型
     * @return 未完成的特定类型任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND task_type = :taskType AND is_completed = 0")
    List<TaskData> getIncompleteTasksByTypeAndUserId(String userId, String taskType);
    
    /**
     * 获取特定日期和类型的任务
     * @param userId 用户ID
     * @param date 日期字符串(yyyy-MM-dd)
     * @param taskType 任务类型
     * @return 指定日期和类型的任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND creation_date = :date AND task_type = :taskType")
    List<TaskData> getTasksByDateAndType(String userId, String date, String taskType);
    
    /**
     * 获取主线任务的所有子任务
     * @param mainTaskId 主线任务ID
     * @return 关联的子任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE parent_task_id = :mainTaskId AND task_type = 'sub'")
    List<TaskData> getSubTasksByMainTaskId(int mainTaskId);
    
    /**
     * 获取有位置信息的未完成任务
     * @param userId 用户ID
     * @return 有位置信息的未完成任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND is_completed = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL")
    List<TaskData> getTasksWithLocationByUserId(String userId);
    
    /**
     * 获取特定验证方式的任务
     * @param userId 用户ID
     * @param method 验证方式
     * @return 指定验证方式的任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND verification_method = :method AND is_completed = 0")
    List<TaskData> getTasksByVerificationMethod(String userId, String method);
    
    /**
     * 检查是否已存在同名任务
     * @param userId 用户ID
     * @param title 任务标题
     * @return 同名任务的数量
     */
    @Query("SELECT COUNT(*) FROM daily_tasks WHERE user_id = :userId AND title = :title")
    int countTasksWithTitle(String userId, String title);

    /**
     * 获取用户的已完成任务（按任务类型）
     * @param userId 用户ID
     * @param taskType 任务类型
     * @return 已完成的特定类型任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId AND task_type = :taskType AND is_completed = 1")
    List<TaskData> getCompletedTasksByUserIdAndTaskType(String userId, String taskType);

    /**
     * 获取用户的所有任务
     * @param userId 用户ID
     * @return 用户的所有任务列表
     */
    @Query("SELECT * FROM daily_tasks WHERE user_id = :userId")
    List<TaskData> getTasksByUserId(String userId);
}
