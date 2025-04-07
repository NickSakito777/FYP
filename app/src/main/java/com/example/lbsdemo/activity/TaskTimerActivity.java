package com.example.lbsdemo.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.lbsdemo.R;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.task.TaskVerificationData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.LocationHistoryData;
import com.example.lbsdemo.utils.GeoFenceManager;
import com.example.lbsdemo.task.TaskVerificationManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import android.Manifest;

/**
 * 任务计时器活动，用于支持time+geofence组合验证
 * 在指定地点显示倒计时，并验证用户是否在地理围栏内停留足够时间
 */
public class TaskTimerActivity extends AppCompatActivity {
    private static final String TAG = "TaskTimerActivity";
    private static final int LOCATION_CHECK_INTERVAL = 10000; // 10秒检查一次位置
    
    private TextView tvTaskTitle;
    private TextView tvTaskLocation;
    private TextView tvCountdown;
    private ProgressBar progressBar;
    private Button btnStartTimer;
    private Button btnCancel;
    
    private int taskId;
    private String taskTitle;
    private String taskLocation;
    private int durationMinutes;
    private Double taskLatitude;
    private Double taskLongitude;
    private Integer taskRadius;
    
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private long timeLeftInMillis;
    private long totalTimeInMillis;
    
    private Handler locationHandler = new Handler();
    private Runnable locationChecker;
    private LocationManager locationManager;
    private boolean isUserInLocation = false;
    private boolean hasTimerStarted = false;
    
    private AppDatabase db;
    private String userId;
    private boolean hasLeftFence = false;
    
    private GeoFenceManager geoFenceManager;
    private String currentFenceId;
    
    private TaskVerificationManager verificationManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_timer);
        
        // 初始化验证管理器
        verificationManager = TaskVerificationManager.getInstance(this);
        
        // 初始化数据库
        db = AppDatabase.getInstance(this);
        
        // 获取用户ID
        SharedPreferences preferences = getSharedPreferences("user_preferences", Context.MODE_PRIVATE);
        userId = preferences.getString("user_id", "");
        
        if (userId.isEmpty()) {
            Toast.makeText(this, "用户ID不存在，无法验证任务", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 初始化视图
        initViews();
        
        // 获取任务信息
        getTaskInfo();
        
        // 初始化地理围栏管理器
        initGeoFenceManager();
        
        // 注册地理围栏广播接收器
        registerGeoFenceReceiver();
        
        // 设置按钮点击事件
        setupButtons();
    }
    
    private void initViews() {
        tvTaskTitle = findViewById(R.id.tv_task_title);
        tvTaskLocation = findViewById(R.id.tv_task_location);
        tvCountdown = findViewById(R.id.tv_countdown);
        progressBar = findViewById(R.id.progress_bar);
        btnStartTimer = findViewById(R.id.btn_start_timer);
        btnCancel = findViewById(R.id.btn_cancel);
    }
    
    private void getTaskInfo() {
        Intent intent = getIntent();
        taskId = intent.getIntExtra("task_id", -1);
        taskTitle = intent.getStringExtra("task_title");
        taskLocation = intent.getStringExtra("task_location");
        durationMinutes = intent.getIntExtra("duration_minutes", 30);
        
        // 获取任务位置信息（可能从数据库中获取）
        Executors.newSingleThreadExecutor().execute(() -> {
            TaskData task = db.taskDao().getTaskById(taskId);
            if (task != null) {
                taskLatitude = task.latitude;
                taskLongitude = task.longitude;
                taskRadius = task.radius != null ? task.radius : 50; // 默认50米
                
                runOnUiThread(() -> {
                    tvTaskTitle.setText(taskTitle);
                    tvTaskLocation.setText(taskLocation);
                    
                    // 设置倒计时总时间
                    totalTimeInMillis = durationMinutes * 60 * 1000;
                    timeLeftInMillis = totalTimeInMillis;
                    updateCountdownText();
                    progressBar.setMax(100);
                    progressBar.setProgress(100);
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(TaskTimerActivity.this, "无法获取任务信息", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
    
    private void setupButtons() {
        btnStartTimer.setOnClickListener(v -> {
            if (!isUserInLocation) {
                Toast.makeText(this, "您不在任务指定位置，请先前往: " + taskLocation, Toast.LENGTH_LONG).show();
                return;
            }
            
            if (isTimerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });
        
        btnCancel.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("取消任务")
                    .setMessage("确定要取消任务计时吗？这将中断当前任务验证。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (countDownTimer != null) {
                            countDownTimer.cancel();
                        }
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }
    
    private void startTimer() {
        // 标记任务开始
        hasTimerStarted = true;
        
        // 创建并开始倒计时
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountdownText();
                
                // 更新进度条
                int progress = (int) (millisUntilFinished * 100 / totalTimeInMillis);
                progressBar.setProgress(progress);
            }
            
            @Override
            public void onFinish() {
                isTimerRunning = false;
                btnStartTimer.setText("任务完成");
                btnStartTimer.setEnabled(false);
                
                // 任务完成处理
                completeTask();
            }
        }.start();
        
        isTimerRunning = true;
        btnStartTimer.setText("暂停");
    }
    
    private void pauseTimer() {
        countDownTimer.cancel();
        isTimerRunning = false;
        btnStartTimer.setText("继续");
    }
    
    private void updateCountdownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        
        String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvCountdown.setText(timeFormatted);
    }
    
    private void initLocationChecker() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        
        // 位置权限检查
        if (ContextCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        
        // 初始化位置检查器
        locationChecker = new Runnable() {
            @Override
            public void run() {
                checkUserLocation();
                locationHandler.postDelayed(this, LOCATION_CHECK_INTERVAL);
            }
        };
        
        // 立即检查一次位置
        checkUserLocation();
        
        // 启动定期位置检查
        locationHandler.postDelayed(locationChecker, LOCATION_CHECK_INTERVAL);
    }
    
    private void checkUserLocation() {
        if (taskLatitude == null || taskLongitude == null) {
            return;
        }
        
        // 位置权限检查
        if (ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, 
            new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    // 计算用户位置与任务位置的距离
                    float[] results = new float[1];
                    Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        taskLatitude, taskLongitude, results);
                    float distanceInMeters = results[0];
                    
                    // 检查距离是否在任务半径范围内
                    boolean wasInLocation = isUserInLocation;
                    isUserInLocation = distanceInMeters <= taskRadius;
                    
                    // 更新UI
                    updateLocationStatus(isUserInLocation);
                    
                    // 记录位置历史
                    recordLocationHistory(location);
                    
                    // 如果用户离开了围栏且计时器正在运行，需要处理
                    if (wasInLocation && !isUserInLocation && isTimerRunning) {
                        hasLeftFence = true;
                        handleUserLeftFence();
                    }
                }
                
                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}
                
                @Override
                public void onProviderEnabled(String provider) {}
                
                @Override
                public void onProviderDisabled(String provider) {
                    isUserInLocation = false;
                    updateLocationStatus(false);
                }
            }, null);
    }
    
    private void updateLocationStatus(boolean isInLocation) {
        runOnUiThread(() -> {
            if (isInLocation) {
                tvTaskLocation.setText(taskLocation + " (已到达)");
                btnStartTimer.setEnabled(true);
            } else {
                tvTaskLocation.setText(taskLocation + " (未到达)");
                if (!hasTimerStarted) {
                    btnStartTimer.setEnabled(false);
                }
            }
        });
    }
    
    private void handleUserLeftFence() {
        if (isTimerRunning) {
            pauseTimer();
            
            runOnUiThread(() -> {
                new AlertDialog.Builder(TaskTimerActivity.this)
                        .setTitle("已离开任务区域")
                        .setMessage("您已离开任务区域，计时已暂停。返回任务区域后可继续计时。")
                        .setPositiveButton("确定", null)
                        .show();
            });
        }
    }
    
    private void recordLocationHistory(Location location) {
        if (taskId > 0) {
            LocationHistoryData historyData = new LocationHistoryData();
            historyData.userId = userId;
            historyData.buildingId = String.valueOf(taskId); // 使用任务ID作为建筑ID
            historyData.latitude = location.getLatitude();
            historyData.longitude = location.getLongitude();
            historyData.visitDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            historyData.isInside = isUserInLocation ? 1 : 0;
            
            // 在后台线程中插入数据
            Executors.newSingleThreadExecutor().execute(() -> {
                db.locationHistoryDao().insert(historyData);
                Log.d(TAG, "位置历史记录已保存");
            });
        }
    }
    
    private void completeTask() {
        // 创建验证数据
        TaskVerificationData verificationData = TaskVerificationData.createTimeGeofenceVerification(
                taskId, userId, taskLatitude, taskLongitude, durationMinutes);
        verificationData.verificationResult = true;
        verificationData.confidence = 100;
        verificationData.feedback = "已在指定位置完成" + durationMinutes + "分钟的任务";
        verificationData.verificationStatus = "verified";
        
        // 保存验证结果
        verificationManager.saveVerificationResult(verificationData, new TaskVerificationManager.SaveCallback() {
            @Override
            public void onSaveSuccess(long id) {
                runOnUiThread(() -> {
                    // 返回成功结果
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("task_id", taskId);
                    resultIntent.putExtra("is_completed", true);
                    setResult(RESULT_OK, resultIntent);
                    
                    new AlertDialog.Builder(TaskTimerActivity.this)
                            .setTitle("任务完成")
                            .setMessage("恭喜您成功完成任务！")
                            .setPositiveButton("返回", (dialog, which) -> finish())
                            .setCancelable(false)
                            .show();
                });
            }
            
            @Override
            public void onSaveFailed(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(TaskTimerActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void initGeoFenceManager() {
        geoFenceManager = new GeoFenceManager(this);
        
        // 创建任务位置的虚拟围栏
        geoFenceManager.createCircularFence(
            String.valueOf(taskId),
            taskLatitude,
            taskLongitude,
            taskRadius
        );
        
        // 设置围栏状态监听器
        geoFenceManager.setOnGeoFenceStatusListener(new GeoFenceManager.OnGeoFenceStatusListener() {
            @Override
            public void onUserEnterFence(String customId) {
                if (String.valueOf(taskId).equals(customId)) {
                    isUserInLocation = true;
                    currentFenceId = customId;
                    runOnUiThread(() -> {
                        Toast.makeText(TaskTimerActivity.this, "已进入任务区域", Toast.LENGTH_SHORT).show();
                        btnStartTimer.setEnabled(true);
                    });
                }
            }
            
            @Override
            public void onUserLeaveFence(String customId) {
                if (String.valueOf(taskId).equals(customId)) {
                    isUserInLocation = false;
                    if (currentFenceId != null && currentFenceId.equals(customId)) {
                        currentFenceId = null;
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(TaskTimerActivity.this, "已离开任务区域", Toast.LENGTH_SHORT).show();
                        pauseTimer();
                        btnStartTimer.setEnabled(false);
                    });
                }
            }
        });
    }
    
    private final BroadcastReceiver geofenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            geoFenceManager.handleGeoFenceBroadcast(context, intent);
        }
    };
    
    private void registerGeoFenceReceiver() {
        IntentFilter filter = new IntentFilter(GeoFenceManager.GEOFENCE_BROADCAST_ACTION);
        registerReceiver(geofenceReceiver, filter);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isTimerRunning) {
            pauseTimer();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (locationHandler != null && locationChecker != null) {
            locationHandler.removeCallbacks(locationChecker);
        }
        unregisterReceiver(geofenceReceiver);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initLocationChecker();
            } else {
                Toast.makeText(this, "需要位置权限来验证任务", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
} 