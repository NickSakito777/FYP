package com.example.lbsdemo.task;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.example.lbsdemo.activity.TaskTimerActivity;
import com.example.lbsdemo.user.AppDatabase;

import java.util.concurrent.Executors;

/**
 * 任务验证管理器
 * 统一管理所有任务的验证逻辑
 */
public class TaskVerificationManager {
    private static final String TAG = "TaskVerificationManager";
    private static TaskVerificationManager instance;
    private final Context context;
    private final AppDatabase database;
    
    private TaskVerificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
    }
    
    public static synchronized TaskVerificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new TaskVerificationManager(context);
        }
        return instance;
    }
    
    /**
     * 开始任务验证
     * @param task 要验证的任务
     * @param callback 验证结果回调
     */
    public void startVerification(TaskData task, VerificationCallback callback) {
        if (task == null) {
            callback.onVerificationFailed("任务数据为空");
            return;
        }
        
        switch (task.verificationMethod) {
            case "photo":
                startPhotoVerification(task, callback);
                break;
            case "geofence":
                startGeofenceVerification(task, callback);
                break;
            case "time":
            case "time+geofence":
                startTimerVerification(task, callback);
                break;
            default:
                callback.onVerificationFailed("不支持的验证方式: " + task.verificationMethod);
                break;
        }
    }
    
    private void startPhotoVerification(TaskData task, VerificationCallback callback) {
        // TODO: 实现拍照验证逻辑
        Toast.makeText(context, "拍照验证功能开发中", Toast.LENGTH_SHORT).show();
    }
    
    private void startGeofenceVerification(TaskData task, VerificationCallback callback) {
        // TODO: 实现位置验证逻辑
        Toast.makeText(context, "位置验证功能开发中", Toast.LENGTH_SHORT).show();
    }
    
    private void startTimerVerification(TaskData task, VerificationCallback callback) {
        if (task.latitude == null || task.longitude == null) {
            callback.onVerificationFailed("任务缺少位置信息");
            return;
        }
        
        Intent intent = new Intent(context, TaskTimerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("task_id", task.id);
        intent.putExtra("task_title", task.title);
        intent.putExtra("task_location", task.location);
        intent.putExtra("duration_minutes", task.durationMinutes);
        intent.putExtra("latitude", task.latitude);
        intent.putExtra("longitude", task.longitude);
        intent.putExtra("radius", task.radius != null ? task.radius : 50);
        context.startActivity(intent);
    }
    
    /**
     * 标记任务为已完成
     * @param taskId 任务ID
     * @param callback 完成回调
     */
    public void markTaskAsCompleted(int taskId, CompletionCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                TaskData task = database.taskDao().getTaskById(taskId);
                if (task != null) {
                    task.isCompleted = true;
                    database.taskDao().updateTask(task);
                    callback.onTaskCompleted(task);
                } else {
                    callback.onCompletionFailed("找不到任务: " + taskId);
                }
            } catch (Exception e) {
                Log.e(TAG, "更新任务状态失败", e);
                callback.onCompletionFailed("更新任务状态失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 保存验证结果
     * @param verificationData 验证数据
     * @param callback 保存回调
     */
    public void saveVerificationResult(TaskVerificationData verificationData, SaveCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long id = database.taskVerificationDao().insertVerification(verificationData);
                callback.onSaveSuccess(id);
            } catch (Exception e) {
                Log.e(TAG, "保存验证结果失败", e);
                callback.onSaveFailed("保存验证结果失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 验证结果回调接口
     */
    public interface VerificationCallback {
        void onVerificationSuccess(TaskVerificationData result);
        void onVerificationFailed(String error);
    }
    
    /**
     * 任务完成回调接口
     */
    public interface CompletionCallback {
        void onTaskCompleted(TaskData task);
        void onCompletionFailed(String error);
    }
    
    /**
     * 保存结果回调接口
     */
    public interface SaveCallback {
        void onSaveSuccess(long id);
        void onSaveFailed(String error);
    }
} 