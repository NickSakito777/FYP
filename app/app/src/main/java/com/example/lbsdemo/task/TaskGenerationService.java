// TaskGenerationService.java
package com.example.lbsdemo.task;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.lbsdemo.R;
import com.example.lbsdemo.activity.ActivitySelection;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.LocationHistoryData;
import com.example.lbsdemo.user.QuestionnaireData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class TaskGenerationService extends Service {
    private static final String TAG = "TaskGenerationService";
    private static final String CHANNEL_ID = "task_generation_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String API_KEY = "sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak"; // DeepSeek API密钥
    public static final String ACTION_TASK_GENERATED = "com.example.lbsdemo.TASK_GENERATED";
    public static final String EXTRA_TASK_RESPONSE = "task_response";
    public static final String EXTRA_TASK_SUCCESS = "task_success";
    private static volatile boolean isGenerating = false;
    private ScheduledExecutorService scheduler;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        db = AppDatabase.getInstance(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("任务生成服务正在运行"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: 服务启动，intent: " + (intent != null ? intent.getAction() : "null"));
        
        if (intent != null && "GENERATE_NOW".equals(intent.getAction())) {
            Log.d(TAG, "onStartCommand: 收到立即生成任务的请求");
            // 在后台线程中执行任务生成
            new Thread(() -> {
                try {
                    Log.d(TAG, "开始生成任务流程");
                    generateTasks();
                } catch (Exception e) {
                    Log.e(TAG, "任务生成过程中发生异常: " + e.getMessage(), e);
                    // 发送任务生成失败的广播
                    Intent failureIntent = new Intent(ACTION_TASK_GENERATED);
                    failureIntent.putExtra("success", false);
                    failureIntent.putExtra("error", e.getMessage());
                    sendBroadcast(failureIntent);
                }
            }).start();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        super.onDestroy();
    }

    private void generateTasks() {
        Log.d(TAG, "generateTasks: 开始生成任务");
        
        // 获取当前用户ID
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");
        
        if (userId.isEmpty()) {
            Log.e(TAG, "generateTasks: 用户ID为空，无法生成任务");
            sendTaskGenerationResult(false, "用户未登录");
            return;
        }
        
        Log.d(TAG, "generateTasks: 用户ID: " + userId);
        
        try {
            // 从数据库获取问卷数据
            QuestionnaireData userData = AppDatabase.getInstance(this).questionnaireDao().getByUserId(userId);
            
            if (userData == null) {
                Log.e(TAG, "generateTasks: 未找到用户问卷数据");
                sendTaskGenerationResult(false, "未找到问卷数据");
                return;
            }
            
            Log.d(TAG, "generateTasks: 成功获取用户问卷数据");
            
            // 准备用户数据
            String userDataString = prepareUserData(userData);
            Log.d(TAG, "generateTasks: 用户数据准备完成: " + userDataString);
            
            // 创建AI请求
            JSONObject reqBody = createAIRequest(userDataString);
            Log.d(TAG, "generateTasks: AI请求创建完成: " + reqBody.toString());
            
            // 发送请求
            Log.d(TAG, "generateTasks: 开始发送API请求");
            String responseData = sendRequest(reqBody);
            Log.d(TAG, "generateTasks: 收到API响应: " + responseData);
            
            // 解析响应
            List<TaskData> tasks = parseAIResponse(responseData, userId);
            Log.d(TAG, "generateTasks: 解析得到 " + tasks.size() + " 个任务");
            
            // 保存任务到数据库
            for (TaskData task : tasks) {
                AppDatabase.getInstance(this).taskDao().insertTask(task);
                Log.d(TAG, "generateTasks: 保存任务到数据库: " + task.title);
            }
            
            // 发送通知
            if (!tasks.isEmpty()) {
                Log.d(TAG, "generateTasks: 发送任务生成通知");
                sendTaskNotification(userId, tasks.size());
            }
            
            // 发送任务生成成功的广播
            Log.d(TAG, "generateTasks: 发送任务生成成功广播");
            sendTaskGenerationResult(true, null);
            
        } catch (Exception e) {
            Log.e(TAG, "generateTasks: 任务生成过程中发生异常", e);
            sendTaskGenerationResult(false, e.getMessage());
        }
    }
    
    private String sendRequest(JSONObject reqBody) throws IOException {
        Log.d(TAG, "sendRequest: 开始发送请求到API");
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
            
        Log.d(TAG, "sendRequest: 请求URL: https://api.siliconflow.cn/v1/chat/completions");
        Log.d(TAG, "sendRequest: 请求体: " + reqBody.toString());
        
        Request request = new Request.Builder()
                .url("https://api.siliconflow.cn/v1/chat/completions")
                .addHeader("Authorization", "Bearer sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.get("application/json"), reqBody.toString()))
                .build();
                
        Log.d(TAG, "sendRequest: 请求已构建，开始执行");
        
        try {
            Response response = client.newCall(request).execute();
            Log.d(TAG, "sendRequest: 收到响应，状态码: " + response.code());
            
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                Log.e(TAG, "sendRequest: 请求失败，状态码: " + response.code() + ", 错误: " + errorBody);
                throw new IOException("请求失败: " + response.code() + " " + response.message() + "\n" + errorBody);
            }
            
            String responseData = response.body().string();
            Log.d(TAG, "sendRequest: 响应数据: " + responseData);
            return responseData;
        } catch (IOException e) {
            Log.e(TAG, "sendRequest: 请求执行过程中发生IO异常", e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "sendRequest: 请求过程中发生未预期的异常", e);
            throw new IOException("请求过程中发生异常: " + e.getMessage(), e);
        }
    }
    
    private void sendTaskGenerationResult(boolean success, String errorMessage) {
        Log.d(TAG, "sendTaskGenerationResult: 发送任务生成结果广播, 成功: " + success + 
              (errorMessage != null ? ", 错误: " + errorMessage : ""));
        Intent resultIntent = new Intent(ACTION_TASK_GENERATED);
        resultIntent.putExtra("success", success);
        if (errorMessage != null) {
            resultIntent.putExtra("error", errorMessage);
        }
        sendBroadcast(resultIntent);
    }

    // 准备用户数据
    private String prepareUserData(QuestionnaireData questionnaireData) {
        try {
            // 构建用户数据JSON
            JSONObject userData = new JSONObject();
            userData.put("userId", questionnaireData.userId);
            userData.put("studyDuration", questionnaireData.studyDuration);
            userData.put("timeTable", new JSONObject(questionnaireData.timeTable));
            userData.put("timePattern", questionnaireData.timePattern);
            userData.put("learningScenes", new JSONArray(questionnaireData.learningScenes));
            userData.put("buildingPreferences", new JSONArray(questionnaireData.buildingPreferences));
            userData.put("taskTimeWindows", new JSONArray(questionnaireData.taskTimeWindows));

            // 获取位置历史数据
            List<LocationHistoryData> locationHistory = db.locationHistoryDao().getUserLocationHistory(questionnaireData.userId);

            // 添加位置历史统计
            JSONArray locationStats = new JSONArray();
            for (LocationHistoryData location : locationHistory) {
                JSONObject locationObj = new JSONObject();
                locationObj.put("buildingId", location.buildingId);
                locationObj.put("durationMinutes", location.durationMinutes);
                locationObj.put("visitDate", location.visitDate);
                locationStats.put(locationObj);
            }
            userData.put("locationHistory", locationStats);

            return userData.toString();
        } catch (Exception e) {
            Log.e(TAG, "准备用户数据时出错: " + e.getMessage(), e);
            return null;
        }
    }

    // 创建AI请求
    private JSONObject createAIRequest(String userDataString) throws JSONException {
        JSONObject reqBody = new JSONObject();
        reqBody.put("model", "deepseek-ai/DeepSeek-V3");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", "你是一个智能学习规划助手，需要为用户生成个性化的每日学习任务。" +
                        "基于用户的偏好和历史数据，创建3个有针对性的学习任务。另外地理位置返回只能带有楼栋英文缩写名称，不要带有楼栋名称" +
                        "每个任务都应包含标题、描述、地点、时间、时长和优先级等信息。"));

        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", "content\", \"请严格按照以下JSON数组格式返回3个任务，不要包含额外文本：\\n\" +\n" +
                        "        \"[{\\\"title\\\":..., \\\"description\\\":..., \\\"location\\\":..., \"+ \n" +
                        "        \"\\\"startTime\\\":\\\"HH:mm\\\", \\\"durationMinutes\\\":60 or 120 or 180, \\\"priority\\\":1 or 2 or 3}]\\n" +
                        userDataString));

        reqBody.put("messages", messages);
        return reqBody;
    }

    // 解析AI响应
    private List<TaskData> parseAIResponse(String responseJson, String userId) {
        try {
            JSONObject response = new JSONObject(responseJson);
            String content = response.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // 广播AI响应内容
            sendTaskGenerationResult(true, content);

            // 从回复中提取JSON部分
            int jsonStart = content.indexOf("[");
            int jsonEnd = content.lastIndexOf("]") + 1;

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonContent = content.substring(jsonStart, jsonEnd);
                JSONArray tasksJson = new JSONArray(jsonContent);
                return parseTasks(tasksJson, userId);
            } else {
                // 尝试解析整个回复
                JSONArray tasksJson = new JSONArray(content);
                return parseTasks(tasksJson, userId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析AI响应时出错: " + e.getMessage());
            // 广播失败消息
            sendTaskGenerationResult(false, e.getMessage());
            return null;
        }
    }

    private List<TaskData> parseTasks(JSONArray tasksJson, String userId) {
        Log.d(TAG, "parseTasks: 开始解析任务JSON数组，共 " + tasksJson.length() + " 个任务");
        List<TaskData> tasks = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        try {
            for (int i = 0; i < tasksJson.length(); i++) {
                JSONObject taskJson = tasksJson.getJSONObject(i);
                Log.d(TAG, "parseTasks: 解析第 " + (i+1) + " 个任务: " + taskJson.toString());
                
                TaskData task = new TaskData();
                task.userId = userId;
                task.title = taskJson.optString("title", "未命名任务");
                task.description = taskJson.optString("description", "无描述");
                task.location = taskJson.optString("location", "未指定地点");
                task.startTime = taskJson.optString("startTime", "00:00");
                task.durationMinutes = taskJson.optInt("durationMinutes", 60);
                task.priority = taskJson.optInt("priority", 1);
                task.isCompleted = false;
                task.creationDate = today;
                task.creationTimestamp = System.currentTimeMillis();
                
                // 随机决定一定比例的任务需要照片验证
                Random random = new Random();
                if (random.nextInt(100) < 30) { // 30%的任务需要照片验证
                    task.verificationMethod = "photo";
                    
                    // 所有拍照任务默认为无需地理位置验证
                    task.positionID = 0;
                    
                    // 为任务生成照片验证提示词
                    task.photoVerificationPrompt = generatePhotoVerificationPrompt(task);
                }
                
                Log.d(TAG, "parseTasks: 成功创建任务对象: " + task.title);
                tasks.add(task);
            }
            
            Log.d(TAG, "parseTasks: 成功解析所有任务，共 " + tasks.size() + " 个任务");
            return tasks;
        } catch (JSONException e) {
            Log.e(TAG, "parseTasks: 解析任务JSON时出错", e);
            return new ArrayList<>();
        }
    }

    // 发送任务通知
    private void sendTaskNotification(String userId, int taskCount) {
        // 创建通知点击意图
        Intent intent = new Intent(this, ActivitySelection.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("今日学习任务已生成")
                .setContentText("已为您创建" + taskCount + "个学习任务，点击查看详情")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // 显示通知
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(userId.hashCode(), builder.build());
    }

    // 创建通知渠道
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "任务生成";
            String description = "每日学习任务生成通知";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // 创建前台服务通知
    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, ActivitySelection.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("学习任务服务")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * 处理单个任务生成结果的方法
     */
    private void handleTaskGenerationResult(JSONObject taskJson, SharedPreferences prefs) {
        String userId = prefs.getString("user_id", "");
        if (userId.isEmpty()) {
            Log.e(TAG, "无法获取用户ID，任务生成失败");
            sendTaskGenerationResultBroadcast(false);
            return;
        }

        try {
            // 从JSON提取任务详情
            String title = taskJson.getString("title");
            String description = taskJson.getString("description");
            String location = taskJson.getString("location");
            String startTime = taskJson.optString("start_time", "");
            int durationMinutes = taskJson.getInt("duration_minutes");
            int priority = taskJson.optInt("priority", 3);
            
            // 获取当前日期
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            
            // 创建任务对象
            TaskData task = new TaskData();
            task.userId = userId;
            task.title = title;
            task.description = description;
            task.location = location;
            task.startTime = startTime;
            task.durationMinutes = durationMinutes;
            task.priority = priority;
            task.isCompleted = false;
            task.creationDate = today;
            task.creationTimestamp = System.currentTimeMillis();
            task.taskType = "daily"; // 设置任务类型为每日任务
            task.status = "pending"; // 设置初始状态为待定
            
            // 随机决定一定比例的任务需要照片验证
            Random random = new Random();
            if (random.nextInt(100) < 30) { // 30%的任务需要照片验证
                task.verificationMethod = "photo";
                
                // 所有拍照任务默认为无需地理位置验证
                task.positionID = 0;
                
                // 为任务生成照片验证提示词
                task.photoVerificationPrompt = generatePhotoVerificationPrompt(task);
            }
            
            // 保存任务到数据库
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AppDatabase.getInstance(this).taskDao().insertTask(task);
                    
                    // 在主线程发送广播表示任务生成成功
                    new Handler(Looper.getMainLooper()).post(() -> {
                        sendTaskGenerationResultBroadcast(true);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "保存任务到数据库失败: " + e.getMessage());
                    
                    // 在主线程发送广播表示任务生成失败
                    new Handler(Looper.getMainLooper()).post(() -> {
                        sendTaskGenerationResultBroadcast(false);
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "解析任务JSON失败: " + e.getMessage());
            sendTaskGenerationResultBroadcast(false);
        }
    }

    /**
     * 发送任务生成结果广播
     * @param success 是否成功
     */
    private void sendTaskGenerationResultBroadcast(boolean success) {
        Intent intent = new Intent(ACTION_TASK_GENERATED);
        intent.putExtra(EXTRA_TASK_SUCCESS, success);
        sendBroadcast(intent);
        Log.d(TAG, "发送任务生成结果广播: " + (success ? "成功" : "失败"));
    }

    /**
     * 生成照片验证提示词
     * @param task 任务对象
     * @return 照片验证提示词
     */
    private String generatePhotoVerificationPrompt(TaskData task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请拍摄一张能够证明你正在完成以下任务的照片: ").append(task.title).append("\n");
        prompt.append("任务详情: ").append(task.description).append("\n");
        if (task.location != null && !task.location.isEmpty()) {
            prompt.append("地点: ").append(task.location).append("\n");
        }
        prompt.append("请确保照片中能够清晰地看到相关的学习环境或学习内容。");
        return prompt.toString();
    }
}
