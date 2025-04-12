package com.example.lbsdemo.activity;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.walknavi.WalkNavigateHelper;
import com.baidu.mapapi.walknavi.adapter.IWEngineInitListener;
import com.baidu.mapapi.walknavi.adapter.IWRoutePlanListener;
import com.baidu.mapapi.walknavi.model.WalkRoutePlanError;
import com.baidu.mapapi.walknavi.params.WalkNaviLaunchParam;
import com.baidu.mapapi.walknavi.params.WalkRouteNodeInfo;
import com.example.lbsdemo.bluetooth.BleManager;
import com.example.lbsdemo.map.FloatWindowManager;
import com.example.lbsdemo.utils.GeoFenceManager;
import com.example.lbsdemo.media.PhotoActivity;
import com.example.lbsdemo.R;
import com.example.lbsdemo.navigation.WNaviGuideActivity;
import com.example.lbsdemo.view.CartoonMapView;
import com.example.lbsdemo.user.LocationHistoryData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.task.TaskScheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.Locale;

public class CrucialMapViewImplement extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "CrucialMapView";
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1234;
    private static final int REQUEST_CODE_MANAGER_ALL_FILE_PERMISSION = 1235;
    private static final int REQUEST_TASK_TIMER = 1001;
    private TextView locationInfo;
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private LocationClient mLocationClient;
    private boolean isFirstLoc = true;
    private float mCurrentDirection = 0;
    private double mCurrentLat = 0.0;
    private double mCurrentLon = 0.0;
    private float mCurrentAccracy;
    private SensorManager mSensorManager;
    private MyLocationData myLocationData;
    private float[] mAccValues = new float[3]; // 加速度传感器数据
    private float[] mMagValues = new float[3]; // 地磁传感器数据
    private final float[] mR = new float[9]; // 旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3]; // 模拟方向传感器的数据（原始数据为弧度）
    private final Handler handler = new Handler(); // 设置为 final
    private Runnable locationUpdateRunnable;
    private GeoFenceManager geoFenceManager;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private Map<String, Marker> taskMarkers = new HashMap<>();
    private String currentFenceId = null; // 当前用户所在的围栏ID
    private String currentBleId = null; // 当前BLE广播接收到的楼栋ID
    private PopupWindow timerPopupWindow;
    private long startTime = 0;
    private long pausedTime = 0;
    private long totalTime = 0;
    private boolean isTimerRunning = false;
    private Handler timerHandler = new Handler();
    private String currentTimerMarkerId;
    private TextView timerTextView;
    private ProgressBar timerProgressBar;
    private Runnable timerRunnable;
    private long taskDuration = 120 * 60 * 1000; // 默认任务时长2小时（毫秒）
    private android.location.Location lastLocation; // 最后一次获取的位置信息
    private String currentTaskType = null;
    /*这是最重要的java Class
       实现功能如下：
       1. 地理围栏的调用：调用了GeoFenceManager以及GeoFenceUtils,接受用户在地理围栏位置的ID1
       2. 蓝牙签到BLE的实现： 调用了blemana， 接受特定的ID2
       3. 百度地图的加载：并且通过传感器实现了方向（百度地图的传感器方向实现过于过时，并且兼容性不高，于是override了）
       4. 在地图上的教学楼添加可交互marker，该marker实现 I. 点击跳出透明窗口 II.窗口带有三个按钮，打卡，导航及关闭。
       5. 打卡事件判定：用户处于打卡地理围栏内，连接到打卡点蓝牙，点击到正确的打卡按钮：ID1=2=3 ->拍照页面
       6. 一些助于运行的组件代码，对于主要功能帮助不大但必须要有，例如一些注释的调试代码，或许有用。
       * */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("showToolView", "onCreate");
        setContentView(R.layout.activity_main3);
        LocationClient.setAgreePrivacy(true);
        initView();
        checkManagerAllFileOrRequestPermissions();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始化地理围栏管理器
        geoFenceManager = new GeoFenceManager(this);
        geoFenceManager.initGeoFence();
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");
        if (!userId.isEmpty()) {
            // 设置位置记录管理器的用户ID
            geoFenceManager.setCurrentUserId(userId);
        }
        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(GeoFenceManager.GEOFENCE_BROADCAST_ACTION);
        registerReceiver(mGeoFenceReceiver, filter);

        try {
            startLocation();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 等待地图加载完成后添加标记点
        mBaiduMap.setOnMapLoadedCallback(() -> {
            // 不在这里直接添加标记点，而是等待接收到index后再添加
            // 以下是所有标记点的信息，作为注释保留
            /*
            addMarker(31.280866,120.744594, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "FB");// FB activity

            addMarker(31.278817,120.746363, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SC楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "SC");// SC activity

            addMarker(31.279303, 120.746498, "SB楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SB楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "SB");//SB activity1

            addMarker(31.279145, 120.744912, "CB楼任务", "打卡点2：咖啡香的知识\n" +
                    "地点：CB楼\n" +
                    "任务：寻找二楼的咖啡店\n" +
                    "描述：这里是学校的灵魂建筑，学生们的学习圣地。去二楼的咖啡馆看看，体会一下在这里学习的乐趣。\n" +
                    "奖励：西浦特色咖啡", "CB");//CB activity2
            addMarker(31.278331, 120.746453, "SD楼任务：", "打卡点3：电梯之谜\n" +
                    "地点：SD楼\n" +
                    "任务：寻找SD楼一层的电梯并签到\n" +
                    "描述：由于SD楼有两栋，寻找电梯的时候容易迷路。想要快点到教室，必须先认清电梯位置\n" +
                    "奖励：西浦楼立牌", "SD");//SD activity3

            addMarker(31.279666,120.746404, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "SA"); //SA activity

            addMarker(31.27962,120.747553, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "PB"); //PB activity

            addMarker(31.279678,120.748366, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "MA"); //MA activity

            addMarker(31.279315,120.748425, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "MB"); //MA activity

            addMarker(31.278624,120.748865, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "EB"); //EB activity

            addMarker(31.278378,120.747603, "SA楼任务", "打卡点1：盛会的观景台\n" +
                    "地点：SA楼\n" +
                    "任务：寻找身穿皮卡丘服的打卡人\n" +
                    "描述：在这栋楼，一位身穿皮卡丘活动服的人在等待着你。在他的位置看看，你会发现学校的宝藏之地。\n" +
                    "奖励：西浦小熊", "EE"); //EE activity
            */
            Log.i("Marker", "地图加载完成");
        });


        // 按钮点击事件
        Button checkInButton = findViewById(R.id.checkInButton);
        Button positionButton = findViewById(R.id.positionButton);
        Button profileButton = findViewById(R.id.profileButton);

        // 跳转到打卡点页面
        checkInButton.setOnClickListener(v -> {
            Intent intent = new Intent(CrucialMapViewImplement.this, ActivitySelection.class);
            startActivityForResult(intent,0);
        });

        // 跳转到我的位置页面
        positionButton.setOnClickListener(v -> {
            moveCamera(mBaiduMap,new LatLng(mCurrentLat,mCurrentLon), 20f);
//            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
//            startActivity(intent);
        });
        // 跳转到我的主页页面
        profileButton.setOnClickListener(v -> {
          Intent intent = new Intent(CrucialMapViewImplement.this, ProfileActivity.class);
           startActivity(intent);
        });


        checkFloatPermission();
        // 设置围栏状态监听器
        geoFenceManager.setOnGeoFenceStatusListener(new GeoFenceManager.OnGeoFenceStatusListener() {
            @Override
            public void onUserEnterFence(String customId) {
                // 用户进入某围栏，更新currentFenceId
                currentFenceId = customId;
                Log.i("GeoFence", "用户进入围栏区域: " + customId);

                // 检查是否是已接受的任务
                SharedPreferences prefs = getSharedPreferences("task_prefs", MODE_PRIVATE);
                boolean isAcceptedTask = prefs.getBoolean("task_" + customId + "_accepted", false);

                if (isAcceptedTask) {
                    // 如果是已接受的任务，显示到达提醒
                    runOnUiThread(() -> {
                        // 创建到达提醒对话框
                        AlertDialog.Builder builder = new AlertDialog.Builder(CrucialMapViewImplement.this);
                        builder.setTitle("已到达任务地点")
                                .setMessage("您已到达任务地点，是否开始执行任务？")
                                .setPositiveButton("开始任务", (dialog, which) -> {
                                    // 直接打开拍照活动
                                    Intent intent = new Intent(CrucialMapViewImplement.this, PhotoActivity.class);
                                    startActivity(intent);
                                })
                                .setNegativeButton("稍后", null)
                                .show();
                    });
                }
            }



            @Override
            public void onUserLeaveFence(String customId) {
                // 用户离开围栏，如果离开的是当前所在的那个围栏，则清空
                if (currentFenceId != null && currentFenceId.equals(customId)) {
                    currentFenceId = null;
                }
            }
        });

        showToolFloatWindow();
    }

    // 修改打卡按钮点击事件处理方法
    private void handleCheckInButtonClick(String markerId) {
        // 禁用所有打卡按钮
        disableAllCheckInButtons();
        
        // 获取当前用户ID
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = userPrefs.getString("current_user_id", "");
        
        // 开始位置停留记录（用于时长验证任务）
        startLocationStayTracking(userId, markerId);
        
        // 显示计时器视图
        showTimerView(markerId);
        
        // 记录打卡状态
        SharedPreferences prefs = getSharedPreferences("checkin_status", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("checked_in_" + markerId, true);
        editor.apply();
    }

    // 修改showPopupLayer方法，添加计时器视图
    private void showPopupLayer(LatLng position, String title, String description, String markerId) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.popup_layer, null);

        // 设置弹出层内容
        TextView popupTitle = popupView.findViewById(R.id.popup_title);
        TextView popupContent = popupView.findViewById(R.id.popup_content);
        Button closeButton = popupView.findViewById(R.id.popup_close);
        Button navigationButtton = popupView.findViewById(R.id.popup_navigation);
        Button CheckInButton = popupView.findViewById(R.id.popup_checkin);

        // 使用传入的title和description来设置文本
        popupTitle.setText(title);
        popupContent.setText(description);

        // 创建弹出窗口
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        navigationButtton.setOnClickListener(v -> {
            walkNavigate(position);
            popupWindow.dismiss(); // 点击后关闭弹窗
        });
        
        // 检查该标记点是否正在打卡中
        SharedPreferences checkinPrefs = getSharedPreferences("checkin_status", MODE_PRIVATE);
        boolean isCheckingIn = checkinPrefs.getBoolean("checking_in_" + markerId, false);
        
        if (isCheckingIn) {
            // 如果正在打卡中，禁用按钮并更改文本
            CheckInButton.setText("正在打卡中");
            CheckInButton.setEnabled(false);
            // 可以改变按钮颜色以表示禁用状态
            CheckInButton.setBackgroundColor(Color.GRAY);
            
            // 在打卡按钮和关闭按钮之间添加计时器视图
            LinearLayout buttonContainer = popupView.findViewById(R.id.popup_button_container);
            
            // 创建计时器视图
            View timerView = inflater.inflate(R.layout.timer_popup, null);
            
            // 设置计时器视图的布局参数
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 10, 0, 10); // 设置上下边距
            timerView.setLayoutParams(params);
            
            // 将计时器视图添加到按钮容器中，位置在打卡按钮和关闭按钮之间
            buttonContainer.addView(timerView, buttonContainer.indexOfChild(closeButton));
            
            // 获取计时器视图中的UI元素
            timerTextView = timerView.findViewById(R.id.timerTextView);
            timerProgressBar = timerView.findViewById(R.id.timerProgressBar);
            TextView currentTimeTextView = timerView.findViewById(R.id.currentTimeTextView);
            TextView totalTimeTextView = timerView.findViewById(R.id.totalTimeTextView);
            ImageButton pauseButton = timerView.findViewById(R.id.pauseTimerButton);
            ImageButton resumeButton = timerView.findViewById(R.id.resumeTimerButton);
            ImageButton finishButton = timerView.findViewById(R.id.finishTimerButton);
            
            // 设置按钮点击事件
            pauseButton.setOnClickListener(v -> {
                pauseTimer();
                pauseButton.setVisibility(View.GONE);
                resumeButton.setVisibility(View.VISIBLE);
            });
            
            resumeButton.setOnClickListener(v -> {
                resumeTimer();
                resumeButton.setVisibility(View.GONE);
                pauseButton.setVisibility(View.VISIBLE);
            });
            
            finishButton.setOnClickListener(v -> {
                if (ActivitySelection.TASK_TYPE_AGENT.equals(currentTaskType)) {
                    // 特工任务：计时完成后进入拍照验证
                    Log.d("TaskType", "特工任务计时完成：开始拍照验证");
                    finishTimer(); // 结束计时
                    popupWindow.dismiss(); // 关闭弹窗
                    onCheckInButtonClicked(markerId); // 启动拍照验证
                } else {
                    // 日常任务：直接完成计时
                    Log.d("TaskType", "日常任务计时完成");
                    finishTimer();
                    popupWindow.dismiss();
                }
            });
            
            // 存储当前标记ID
            currentTimerMarkerId = markerId;
            
            // 获取任务时长配置（如果存在）
            SharedPreferences taskPrefs = getSharedPreferences("task_settings", MODE_PRIVATE);
            taskDuration = taskPrefs.getLong("task_duration_" + markerId, 120 * 60 * 1000); // 默认2小时
            
            // 设置总时间显示
            long hours = taskDuration / 3600000;
            long minutes = (taskDuration % 3600000) / 60000;
            totalTimeTextView.setText(String.format("%02d:%02d", hours, minutes));
            
            // 设置进度条最大值
            timerProgressBar.setMax(100);
            timerProgressBar.setProgress(0);
            
            // 如果计时器已经在运行，则更新UI显示当前状态
            if (isTimerRunning) {
                // 更新计时器显示
                updateTimerText(totalTime);
                updateProgressBar(totalTime);
                updateCurrentTimeText(currentTimeTextView, totalTime);
                
                // 根据暂停状态显示正确的按钮
                if (isTimerRunning) {
                    pauseButton.setVisibility(View.VISIBLE);
                    resumeButton.setVisibility(View.GONE);
                } else {
                    pauseButton.setVisibility(View.GONE);
                    resumeButton.setVisibility(View.VISIBLE);
                }
            } else {
                // 如果计时器尚未开始，则开始计时
                startTimer(currentTimeTextView);
            }
        } else {
            // 正常设置打卡按钮点击事件
            CheckInButton.setOnClickListener(v -> {
                // 根据任务类型区分处理逻辑
                if (ActivitySelection.TASK_TYPE_AGENT.equals(currentTaskType)) {
                    // 特工任务：先开始计时
                    Log.d("TaskType", "特工任务打卡：开始计时");
                    // 设置正在打卡状态
                    SharedPreferences prefs = getSharedPreferences("checkin_status", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("checking_in_" + markerId, true);
                    editor.apply();
                    
                    // 关闭当前弹窗
                    popupWindow.dismiss();
                    
                    // 显示带计时器的弹窗
                    showTimerView(markerId);
                } else {
                    // 日常任务：直接调用原有的打卡逻辑
                    Log.d("TaskType", "日常任务打卡：直接拍照");
                    onCheckInButtonClicked(markerId);
                    popupWindow.dismiss();
                }
            });
        }

        // 设置弹出窗口的关闭按钮功能 - 修改这里，使关闭按钮只关闭弹窗，不停止计时器
        closeButton.setOnClickListener(v -> {
            // 只关闭弹窗，不影响计时器状态
            popupWindow.dismiss();
            
            // 如果计时器正在运行，显示一个提示
            if (isTimerRunning) {
                Toast.makeText(CrucialMapViewImplement.this, 
                    "计时器在后台继续运行，再次点击标记点可查看", 
                    Toast.LENGTH_SHORT).show();
            }
        });

        // 显示弹出窗口
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        popupWindow.showAtLocation(mMapView, Gravity.CENTER, 0, 0);
        
        // 修改这里，不再在弹窗关闭时自动重新显示弹窗
        // 只有在用户按下返回键且计时器正在运行时才提示
        popupWindow.setOnDismissListener(() -> {
            if (isTimerRunning) {
                // 只显示提示，不再自动重新显示弹窗
                Toast.makeText(CrucialMapViewImplement.this, 
                    "计时器在后台继续运行，再次点击标记点可查看", 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 修改showTimerView方法，使其直接调用showPopupLayer
    private void showTimerView(String markerId) {
        // 首先设置该标记点为正在打卡中
        SharedPreferences prefs = getSharedPreferences("checkin_status", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("checking_in_" + markerId, true);
        editor.apply();
        
        // 获取标记点信息
        Marker marker = taskMarkers.get(markerId);
        if (marker != null) {
            Bundle extra = marker.getExtraInfo();
            if (extra != null) {
                String title = extra.getString("title", "默认标题");
                String description = extra.getString("description", "无描述信息");
                
                // 显示带有计时器的弹窗
                showPopupLayer(marker.getPosition(), title, description, markerId);
            }
        } else {
            // 如果找不到标记点，使用默认信息
            Log.e(TAG, "找不到标记点: " + markerId);
            LatLng defaultPosition = new LatLng(mCurrentLat, mCurrentLon);
            showPopupLayer(defaultPosition, "打卡任务", "正在进行打卡任务", markerId);
        }
    }

    // 添加新方法：禁用所有打卡按钮
    private void disableAllCheckInButtons() {
        // 遍历所有标记点，找到对应的打卡按钮并禁用
        for (String key : taskMarkers.keySet()) {
            Marker marker = taskMarkers.get(key);
            if (marker != null) {
                // 记录该标记点正在打卡中
                SharedPreferences prefs = getSharedPreferences("checkin_status", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("checking_in_" + key, true);
                editor.apply();
            }
        }
    }

    // 开始计时
    private void startTimer(TextView currentTimeTextView) {
        startTime = System.currentTimeMillis();
        pausedTime = 0;
        totalTime = 0;
        isTimerRunning = true;
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTimerRunning) {
                    long now = System.currentTimeMillis();
                    totalTime = now - startTime - pausedTime;
                    
                    // 更新主计时器显示
                    updateTimerText(totalTime);
                    
                    // 更新进度条和当前时间显示
                    updateProgressBar(totalTime);
                    updateCurrentTimeText(currentTimeTextView, totalTime);
                    
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        
        timerHandler.post(timerRunnable);
        Toast.makeText(this, "开始计时", Toast.LENGTH_SHORT).show();
    }

    // 暂停计时
    private void pauseTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            pausedTime += System.currentTimeMillis() - startTime - totalTime;
            timerHandler.removeCallbacks(timerRunnable);
            Toast.makeText(this, "计时已暂停", Toast.LENGTH_SHORT).show();
        }
    }

    // 继续计时
    private void resumeTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            startTime = System.currentTimeMillis() - totalTime - pausedTime;
            timerHandler.post(timerRunnable);
            Toast.makeText(this, "计时已继续", Toast.LENGTH_SHORT).show();
        }
    }

    // 完成计时
    private void finishTimer() {
        // 停止计时器
        timerHandler.removeCallbacks(timerRunnable);
        isTimerRunning = false;
        
        // 保存在教学楼的时间记录
        saveLocationTime(currentTimerMarkerId, totalTime);
        
        // 计算时间
        long hours = totalTime / 3600000;
        long minutes = (totalTime % 3600000) / 60000;
        long seconds = (totalTime % 60000) / 1000;
        
        String timeDisplay = hours > 0 ? 
            String.format("%d 小时 %d 分 %d 秒", hours, minutes, seconds) : 
            String.format("%d 分 %d 秒", minutes, seconds);
        
        // 获取当前用户ID
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = userPrefs.getString("current_user_id", "");
        
        // 检查是否有与此位置关联的时长验证任务
        checkTimeVerificationTasks(userId, currentTimerMarkerId, (int)(totalTime / 60000));
        
        // 标记任务完成
        markTaskAsCompleted(currentTimerMarkerId);
        
        // 重置打卡状态
        resetCheckInStatus(currentTimerMarkerId);
        
        // 显示完成信息
        new AlertDialog.Builder(this)
            .setTitle("任务完成")
            .setMessage(String.format("您在 %s 停留了 %s", 
                    currentTimerMarkerId, timeDisplay))
            .setPositiveButton("确定", (dialog, which) -> {
                // 创建返回到ActivitySelection的Intent
                Intent activitySelectionIntent = new Intent(this, ActivitySelection.class);
                // 添加任务完成状态
                activitySelectionIntent.putExtra("task_completed", true);
                // 从原始Intent中获取任务ID
                int taskId = getIntent().getIntExtra("task_id", -1);
                activitySelectionIntent.putExtra("task_id", taskId);
                Log.i("CrucialMapViewImplement", "taskId: " + taskId);
                // 设置标志，保持正常的活动栈
                activitySelectionIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(activitySelectionIntent);
            })
            .show();
    }
    
    /**
     * 检查是否有与此位置关联的时长验证任务
     * 
     * @param userId 用户ID
     * @param markerId 标记点ID
     * @param durationMinutes 停留时间（分钟）
     */
    private void checkTimeVerificationTasks(String userId, String markerId, int durationMinutes) {
        if (userId.isEmpty()) {
            Log.e(TAG, "用户ID为空，无法检查时长验证任务");
            return;
        }
        
        // 创建TaskScheduler实例
        TaskScheduler taskScheduler = new TaskScheduler(this);
        
        // 获取与此位置相关的任务
        AppDatabase db = AppDatabase.getInstance(this);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 查询所有与此位置相关的任务
                List<TaskData> tasks = db.taskDao().getTasksByLocation("%" + markerId + "%");
                
                for (TaskData task : tasks) {
                    // 检查是否是时长验证任务
                    if ("time".equals(task.verificationMethod) && !task.isCompleted) {
                        // 结束位置停留记录
                        int recordedDuration = taskScheduler.endLocationStayTracking(userId, task.id);
                        
                        // 验证停留时间
                        TaskScheduler.TimeVerificationResult result = 
                                taskScheduler.validateStayDuration(userId, task.id);
                        
                        // 在主线程显示结果
                        runOnUiThread(() -> {
                            if (result.isValid) {
                                Toast.makeText(this, 
                                        "任务「" + task.title + "」完成！停留时间验证通过。", 
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, 
                                        "任务「" + task.title + "」" + result.message, 
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "检查时长验证任务时出错: " + e.getMessage(), e);
            }
        });
    }

    // 添加新方法：开始位置停留记录
    private void startLocationStayTracking(String userId, String markerId) {
        if (userId.isEmpty()) {
            Log.e(TAG, "用户ID为空，无法开始位置停留记录");
            return;
        }
        
        // 创建TaskScheduler实例
        TaskScheduler taskScheduler = new TaskScheduler(this);
        
        // 获取与此位置相关的任务
        AppDatabase db = AppDatabase.getInstance(this);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 查询所有与此位置相关的任务
                List<TaskData> tasks = db.taskDao().getTasksByLocation("%" + markerId + "%");
                
                // 获取当前位置
                Location currentLocation = new Location("");
                if (lastLocation != null) {
                    currentLocation.setLatitude(lastLocation.getLatitude());
                    currentLocation.setLongitude(lastLocation.getLongitude());
                } else {
                    // 如果没有位置信息，使用标记点位置
                    Marker marker = taskMarkers.get(markerId);
                    if (marker != null) {
                        currentLocation.setLatitude(marker.getPosition().latitude);
                        currentLocation.setLongitude(marker.getPosition().longitude);
                    }
                }
                
                for (TaskData task : tasks) {
                    // 检查是否是时长验证任务
                    if ("time".equals(task.verificationMethod) && !task.isCompleted) {
                        // 开始位置停留记录
                        boolean started = taskScheduler.startLocationStayTracking(
                                userId, task.id, currentLocation);
                        
                        // 在主线程显示结果
                        final boolean finalStarted = started;
                        runOnUiThread(() -> {
                            if (finalStarted) {
                                Toast.makeText(this, 
                                        "开始记录「" + task.title + "」的停留时间", 
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "开始位置停留记录时出错: " + e.getMessage(), e);
            }
        });
    }

    // 标记任务为已完成
    private void markTaskAsCompleted(String markerId) {
        // 首先获取任务ID
        SharedPreferences taskPrefs = getSharedPreferences("task_status", MODE_PRIVATE);
        SharedPreferences.Editor editor = taskPrefs.edit();
        
        // 遍历所有进行中的任务，查找匹配的位置
        for (String key : taskPrefs.getAll().keySet()) {
            if (key.startsWith("task_") && key.endsWith("_in_progress")) {
                // 尝试从数据库获取任务详情
                int taskId = Integer.parseInt(key.substring(5, key.indexOf("_in_progress")));
                
                // 异步更新任务状态
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(this);
                    TaskData task = db.taskDao().getTaskById(taskId);
                    
                    if (task != null && task.location.contains(markerId)) {
                        // 如果任务地点包含当前markerId，则标记为已完成
                        task.isCompleted = true;
                        db.taskDao().updateTask(task);
                        
                        // 移除进行中标记
                        runOnUiThread(() -> {
                            editor.remove(key);
                            editor.apply();
                        });
                    }
                });
            }
        }
    }

    // 更新计时器文本
    private void updateTimerText(long timeInMillis) {
        long hours = timeInMillis / 3600000;
        long minutes = (timeInMillis % 3600000) / 60000;
        long seconds = (timeInMillis % 60000) / 1000;
        
        if (hours > 0) {
            timerTextView.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        } else {
            timerTextView.setText(String.format("%02d:%02d", minutes, seconds));
        }
    }
    
    // 更新进度条
    private void updateProgressBar(long timeInMillis) {
        int progress = (int) ((timeInMillis * 100) / taskDuration);
        // 确保进度不超过100
        progress = Math.min(progress, 100);
        timerProgressBar.setProgress(progress);
    }
    
    // 更新当前时间文本
    private void updateCurrentTimeText(TextView currentTimeTextView, long timeInMillis) {
        long hours = timeInMillis / 3600000;
        long minutes = (timeInMillis % 3600000) / 60000;
        
        if (hours > 0) {
            currentTimeTextView.setText(String.format("%02d:%02d", hours, minutes));
        } else {
            currentTimeTextView.setText(String.format("%02d:%02d", 0, minutes));
        }
    }

    // 保存位置时间记录
    private void saveLocationTime(String buildingId, long timeInMillis) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");
        
        if (!userId.isEmpty()) {
            // 保存当天的记录
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            
            // 转换为分钟
            int durationMinutes = (int) (timeInMillis / 60000);
            
            // 在异步线程中操作数据库
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // 创建位置历史记录
                    LocationHistoryData historyData = new LocationHistoryData();
                    historyData.userId = userId;
                    historyData.buildingId = buildingId;
                    historyData.durationMinutes = durationMinutes;
                    historyData.visitDate = today;
                    historyData.timestamp = System.currentTimeMillis();
                    
                    // 使用数据库实例，调用正确的方法插入数据
                    AppDatabase db = AppDatabase.getInstance(this);
                    db.locationHistoryDao().insertLocationHistory(historyData);
                    
                    runOnUiThread(() -> Toast.makeText(this, 
                        "已记录您在 " + buildingId + " 停留 " + durationMinutes + " 分钟", 
                        Toast.LENGTH_SHORT).show());
                    
                } catch (Exception e) {
                    Log.e("TimerError", "保存位置时间记录失败: " + e.getMessage());
                }
            });
        }
    }

    private void checkFloatPermission() {
        showFloatWindow();
        showToolFloatWindow();
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
        }
//        if (!Settings.canDrawOverlays(this)) {
//            // 请求悬浮窗的权限
//            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                    Uri.parse("package:" + getPackageName()));
//            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
//        } else {
//            showFloatWindow();
//        }
    }
    //悬浮窗实现
    private void showFloatWindow() {
        locationInfo.postDelayed(new Runnable() {
            @Override
            public void run() {

                FloatWindowManager.get().showToolView(String.valueOf(CrucialMapViewImplement.class.hashCode()), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(CrucialMapViewImplement.this,CartoonMapView.class));
                        finish();
                    }
                });
            }
        }, 1000);//晚一秒实现，先等onCreate的都跑完再跑，不然会出现不显示的问题。
    }
    // 在showFloatWindow方法后添加新悬浮窗逻辑
    private void showToolFloatWindow() {
//        FloatWindowManager.get().showToolView("NAV_TOOL_" + this.hashCode(), new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(CrucialMapViewImplement.this, CartoonMapView.class);
//                startActivity(intent);
//                FloatWindowManager.get().hindToolView("NAV_TOOL_" + CrucialMapViewImplement.this.hashCode());
//            }
//        });
    }



    //创建marker的位置
    private void addMarker(double lat, double lon, String title, String description, String markerId, String iconName) {
        // 创建标记点位置
        LatLng point = new LatLng(lat, lon);

        // 创建标记点图标
        BitmapDescriptor bitmap = BitmapDescriptorFactory.fromAsset("marker3.png"); // 确保您在 drawable 文件夹中有 marker.png 或类似图标
        if (bitmap == null) {
            Log.e("MarkerError", "图标加载失败，使用默认图标");
            bitmap = BitmapDescriptorFactory.fromAsset("marker3.png");
        }

        // 创建 OverlayOptions
        OverlayOptions options = new MarkerOptions()
                .position(point) // 标记点位置
                .icon(bitmap)    // 标记点图标
                .title(title)
                .draggable(false); // 标记点是否可拖动？

        // 添加标记点到地图
        Marker marker = (Marker) mBaiduMap.addOverlay(options);

        // 为 Marker 设置额外信息
        Bundle bundle = new Bundle();
        bundle.putString("markerId", markerId);
        bundle.putString("title", title);
        bundle.putString("description", description);
        bundle.putDouble("latitude", lat);
        bundle.putDouble("longitude", lon);
        marker.setExtraInfo(bundle);

        // 添加一个动画效果，使标记更醒目
        // marker.setAnimateType(MarkerOptions.MarkerAnimateType.grow);
        // 设置点击事件监听器
        mBaiduMap.setOnMarkerClickListener(marker1 -> {
            if (marker1.equals(marker)) {
                Log.i("MarkerClick", "标记点被点击，显示弹出层");
                // 获取该 Marker 的额外信息
                Bundle extra = marker1.getExtraInfo();
                String markerIds = extra != null ? extra.getString("markerId", "未知ID") : "未知ID";
                String markerTitle = extra != null ? extra.getString("title", "默认标题") : "默认标题";
                String markerDescription = extra != null ? extra.getString("description", "无描述信息") : "无描述信息";

                // 显示弹出层时传入标题和描述
                showPopupLayer(marker1.getPosition(), markerTitle, markerDescription, markerIds);
                return true;
            }
            return false;
        });
        // 记录添加的标记点，方便后续管理
        if (!taskMarkers.containsKey(markerId)) {
            taskMarkers.put(markerId, marker);
        }


        // 设置地图中心到标记点位置，并调整缩放级别
        MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(point, 18.0f);
        mBaiduMap.animateMapStatus(update);
        Log.i("Marker", "标记点添加成功");
    }

    //任务接受处理方法:
    private void onTaskAccepted(String markerId, LatLng position) {
        // 记录任务接受状态
        SharedPreferences prefs = getSharedPreferences("task_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("task_" + markerId + "_accepted", true);
        editor.apply();

        // 更新标记样式（可以更换图标表示已接受）
        Marker marker = taskMarkers.get(markerId);
        if (marker != null) {
            BitmapDescriptor acceptedIcon = BitmapDescriptorFactory.fromAsset("marker3.png");
            // 使用现有的marker3.png而不是marker_accepted.png
            marker.setIcon(acceptedIcon);
        }

        // 设置地理围栏提醒
        setupTaskGeofence(markerId, position);

        // 显示接受成功提示
        Toast.makeText(this, "已接受任务，导航至目标位置完成任务", Toast.LENGTH_LONG).show();

        // 自动开始导航
        walkNavigate(position);
    }
    private void setupTaskGeofence(String markerId, LatLng position) {
        // 为任务位置创建地理围栏，当用户到达时触发提醒
        geoFenceManager.createCircularFence(
                markerId,  // 围栏ID使用任务ID
                position.latitude,
                position.longitude,
                50  // 100米半径
        );
    }

    // 周期性检查用户是否在任务目标点附近
    private void startLocationCheckForTask(String markerId, LatLng targetPosition) {
        // 创建一个每5秒检查一次的Runnable
        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查任务是否仍被接受
                SharedPreferences prefs = getSharedPreferences("task_prefs", MODE_PRIVATE);
                boolean isTaskAccepted = prefs.getBoolean("task_" + markerId + "_accepted", false);

                if (isTaskAccepted && mCurrentLat != 0 && mCurrentLon != 0) {
                    // 检查用户是否在目标位置附近
                    boolean isNearTarget = geoFenceManager.isUserInVirtualFence(
                            markerId, mCurrentLat, mCurrentLon);

                    if (isNearTarget) {
                        // 用户已到达目标位置，显示提示
                        runOnUiThread(() -> {
                            // 检查弹窗是否已显示过
                            boolean hasShown = prefs.getBoolean("shown_arrival_" + markerId, false);
                            if (!hasShown) {
                                // 标记为已显示过
                                prefs.edit().putBoolean("shown_arrival_" + markerId, true).apply();

                                // 显示到达提醒
                                AlertDialog.Builder builder = new AlertDialog.Builder(CrucialMapViewImplement.this);
                                builder.setTitle("已到达任务地点")
                                        .setMessage("您已到达 " + markerId + " 任务地点，是否开始执行任务？")
                                        .setPositiveButton("开始任务", (dialog, which) -> {
                                            // 打开拍照或任务执行页面
                                            Intent intent = new Intent(CrucialMapViewImplement.this, PhotoActivity.class);
                                            startActivity(intent);
                                        })
                                        .setNegativeButton("稍后", null)
                                        .show();
                            }
                        });
                    }
                }

                // 计划下一次检查
                handler.postDelayed(this, 5000); // 5秒后再次检查
            }
        };
        // 保存runnable以便在销毁时移除
        locationUpdateRunnable = checkRunnable;

        // 开始第一次检查
        handler.post(checkRunnable);
    }



        /**
         * 初始化 View
         */
    private void initView() {
        locationInfo = findViewById(R.id.locationInfo);
        mMapView = findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMyLocationEnabled(true);
        MyLocationConfiguration myLocationConfiguration = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
        mBaiduMap.setMyLocationConfiguration(myLocationConfiguration);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_UI);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d("CrucialMapView", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        if (requestCode == REQUEST_TASK_TIMER) {
            // 拍照完成后的处理
            String markerId = null;
            
            // 首先尝试从返回的Intent中获取markerId
            if (data != null && data.hasExtra("marker_id")) {
                markerId = data.getStringExtra("marker_id");
                Log.d("CrucialMapView", "拍照返回，从Intent获取markerId: " + markerId);
            }
            
            // 如果Intent中没有，则尝试从SharedPreferences获取
            if (markerId == null || markerId.isEmpty()) {
                SharedPreferences checkinPrefs = getSharedPreferences("checkin_prefs", MODE_PRIVATE);
                markerId = checkinPrefs.getString("current_checkin_marker", "");
                Log.d("CrucialMapView", "从SharedPreferences获取markerId: " + markerId);
            }
            
            // 如果还是没有，使用默认值
            if (markerId == null || markerId.isEmpty()) {
                markerId = "default_marker";
                Log.w("CrucialMapView", "无法获取markerId，使用默认值: " + markerId);
            }
            
            // 清除存储的markerId
            SharedPreferences checkinPrefs = getSharedPreferences("checkin_prefs", MODE_PRIVATE);
            checkinPrefs.edit().remove("current_checkin_marker").apply();
            
            final String finalMarkerId = markerId;
            
            // 根据任务类型处理不同的逻辑
            if (ActivitySelection.TASK_TYPE_AGENT.equals(currentTaskType)) {
                // 特工任务：拍照完成后直接结束打卡流程
                Log.d("TaskType", "特工任务拍照完成，结束打卡流程");
                
                // 重置打卡状态
                resetCheckInStatus(finalMarkerId);
                
                // 标记任务完成
                markTaskAsCompleted(finalMarkerId);
                
                // 显示完成提示
                Toast.makeText(this, "特工任务完成！", Toast.LENGTH_SHORT).show();

                // 创建返回到ActivitySelection的Intent
                Intent activitySelectionIntent = new Intent(this, ActivitySelection.class);
                // 添加任务完成状态
                activitySelectionIntent.putExtra("task_completed", true);
                // 从原始Intent中获取任务ID
                int taskId = getIntent().getIntExtra("task_id", -1);
                activitySelectionIntent.putExtra("task_id", taskId);
                // 设置标志，保持正常的活动栈
                activitySelectionIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(activitySelectionIntent);
                
            } else {
                // 日常任务：继续显示计时界面
                Log.d("TaskType", "日常任务拍照完成，显示计时视图");
                // 使用Handler延迟一下，确保UI更新完成
                new Handler().post(() -> showTimerView(finalMarkerId));
            }
        } else if(requestCode==REQUEST_CODE_OVERLAY_PERMISSION) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkFloatPermission();
                }
            }, 500);
        } else if(requestCode==REQUEST_CODE_MANAGER_ALL_FILE_PERMISSION){
            checkManagerAllFileOrRequestPermissions();
        }
        else if(requestCode== 0 && resultCode==RESULT_OK && data != null){
            // 清除所有现有的标记点
            mBaiduMap.clear();
            
            // 获取任务类型
            currentTaskType = data.getStringExtra(ActivitySelection.EXTRA_TASK_TYPE);
            
            int index = data.getIntExtra("index", 0);
            String markerContent = data.getStringExtra("marker_content");
            String taskTitle = data.getStringExtra("task_title");
            String taskLocation = data.getStringExtra("task_location");
            String taskDescription = data.getStringExtra("task_description");
            int taskId = data.getIntExtra("task_id", -1);
            boolean verifyPhoto = data.getBooleanExtra("verify_photo", false);

            // 根据不同的任务类型使用不同的marker图标
            String markerIcon = ActivitySelection.TASK_TYPE_AGENT.equals(currentTaskType) ? 
                               "marker_agent.png" : "marker3.png";

            // 如果没有传递任务详情，使用默认提示信息
            String defaultContent = "暂无任务，请前往任务列表接收任务";
            
            if (index == 1) {
                moveCamera(mBaiduMap, new LatLng(31.279303, 120.746498), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "SB楼任务";
                addMarker(31.279303, 120.746498, title, content, "SB", markerIcon);
            } else if (index == 2) {
                moveCamera(mBaiduMap, new LatLng(31.279145, 120.744912), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "CB楼任务";
                addMarker(31.279145, 120.744912, title, content, "CB", markerIcon);
            } else if (index == 3) {
                moveCamera(mBaiduMap, new LatLng(31.278331, 120.746453), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "SD楼任务";
                addMarker(31.278331, 120.746453, title, content, "SD", markerIcon);
            } else if (index == 4) {
                moveCamera(mBaiduMap, new LatLng(31.280866, 120.744594), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "FB楼任务";
                addMarker(31.280866, 120.744594, title, content, "FB", markerIcon);
            } else if (index == 5) {
                moveCamera(mBaiduMap, new LatLng(31.278817, 120.746363), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "SC楼任务";
                addMarker(31.278817, 120.746363, title, content, "SC", markerIcon);
            } else if (index == 6) {
                moveCamera(mBaiduMap, new LatLng(31.279666, 120.746404), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "SA楼任务";
                addMarker(31.279666, 120.746404, title, content, "SA", markerIcon);
            } else if (index == 7) {
                moveCamera(mBaiduMap, new LatLng(31.27962, 120.747553), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "PB楼任务";
                addMarker(31.27962, 120.747553, title, content, "PB", markerIcon);
            } else if (index == 8) {
                moveCamera(mBaiduMap, new LatLng(31.279678, 120.748366), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "MA楼任务";
                addMarker(31.279678, 120.748366, title, content, "MA", markerIcon);
            } else if (index == 9) {
                moveCamera(mBaiduMap, new LatLng(31.279315, 120.748425), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "MB楼任务";
                addMarker(31.279315, 120.748425, title, content, "MB", markerIcon);
            } else if (index == 10) {
                moveCamera(mBaiduMap, new LatLng(31.278624, 120.748865), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "EB楼任务";
                addMarker(31.278624, 120.748865, title, content, "EB", markerIcon);
            } else if (index == 11) {
                moveCamera(mBaiduMap, new LatLng(31.278378, 120.747603), 20f);
                String content = markerContent != null ? markerContent : defaultContent;
                String title = taskTitle != null ? taskTitle : "EE楼任务";
                addMarker(31.278378, 120.747603, title, content, "EE", markerIcon);
            } else {
                Log.w("CrucialMapView", "收到了无效的索引: " + index + "，无法添加标记");
                Toast.makeText(this, "无效的位置索引: " + index, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 启动定位
     */
    private void startLocation() throws Exception {
        // 定位初始化
        mLocationClient = new LocationClient(this);
        mLocationClient.registerLocationListener(new MyLocationListener());
        LocationClientOption locationClientOption = new LocationClientOption();
        locationClientOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        locationClientOption.setCoorType("bd09ll");
        locationClientOption.setScanSpan(1000);
        locationClientOption.setOpenGps(true);
        locationClientOption.setIsNeedAddress(true);
        locationClientOption.setIsNeedLocationPoiList(true);
        locationClientOption.setIsNeedAddress(true);
        mLocationClient.setLocOption(locationClientOption);
        mLocationClient.start();
    }

    private void checkManagerAllFileOrRequestPermissions(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            if(!Environment.isExternalStorageManager()){
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),REQUEST_CODE_MANAGER_ALL_FILE_PERMISSION);
            }else{
                requestPermissions(new ArrayList<>());
            }
        }else{
            ArrayList<String> permissionList = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(CrucialMapViewImplement.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            requestPermissions(permissionList);
        }
    }
    /**
     * 请求权限
     */
    private void requestPermissions(List<String> permissionList) {
        if (ContextCompat.checkSelfPermission(CrucialMapViewImplement.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(CrucialMapViewImplement.this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(android.Manifest.permission.READ_PHONE_STATE);
        }

        if (!permissionList.isEmpty()) {
            String[] permissions = permissionList.toArray(new String[0]);
            ActivityCompat.requestPermissions(CrucialMapViewImplement.this, permissions, 1);
        }
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "必须同意所有的权限才能使用本程序", Toast.LENGTH_LONG).show();
//                        finish();
                        return;
                    }
                }
                try {
                    startLocation();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                Toast.makeText(this, "发生未知错误", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // 围栏广播接收器
    private final BroadcastReceiver mGeoFenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            geoFenceManager.handleGeoFenceBroadcast(context, intent);
        }
    };

    /**
     * 传感器方向信息回调
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccValues = sensorEvent.values;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagValues = sensorEvent.values;
        }

        // 计算方向矩阵和设备方向
        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);

        float newDirection = (float) Math.toDegrees(mDirectionValues[0]); // 转为角度制
        if (newDirection < 0) {
            newDirection += 360; // 确保在 0~360 度之间
        }

        // 方向变化超过 1 度时才更新
        if (Math.abs(newDirection - mCurrentDirection) > 1.0) {
            mCurrentDirection = newDirection;

            // 更新定位数据并刷新蓝点
            myLocationData = new MyLocationData.Builder()
                    .accuracy(mCurrentAccracy)
                    .direction(mCurrentDirection)
                    .latitude(mCurrentLat)
                    .longitude(mCurrentLon)
                    .build();
            mBaiduMap.setMyLocationData(myLocationData);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不需要实现
    }

    /**
     * 自定义定位监听器类
     */
    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null || mMapView == null) return;

            mCurrentLat = location.getLatitude();
            mCurrentLon = location.getLongitude();
            mCurrentAccracy = location.getRadius();

            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(mCurrentAccracy)
                    .direction(mCurrentDirection)  // 实时更新方向
                    .latitude(mCurrentLat)
                    .longitude(mCurrentLon)
                    .build();

            mBaiduMap.setMyLocationData(locData);

            if (isFirstLoc) {
                isFirstLoc = false;
                LatLng ll = new LatLng(mCurrentLat, mCurrentLon);
                MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(ll, 18.5f);
                mBaiduMap.animateMapStatus(update);
            }

            // 更新 TextView 信息 一开始用于调试现如今没啥用了。但应该会作为后续上传用户log的基准——future work
            StringBuilder currentPosition = new StringBuilder();
            //currentPosition.append("纬度：").append(location.getLatitude()).append("\n");
            //currentPosition.append("经度：").append(location.getLongitude()).append("\n");
            //currentPosition.append("国家：").append(location.getCountry()).append("\n");
            //currentPosition.append("省：").append(location.getProvince()).append("\n");
            //currentPosition.append("市：").append(location.getCity()).append("\n");
            // currentPosition.append("区：").append(location.getDistrict()).append("\n");
            //currentPosition.append("街道：").append(location.getStreet()).append("\n");
            //currentPosition.append("地址：").append(location.getAddrStr()).append("\n");


            String locationDescribe = location.getLocationDescribe();
            if (locationDescribe != null && !locationDescribe.isEmpty()) {
                currentPosition.append("位置描述：").append(locationDescribe).append("\n");
            } else {
                // 如果 locationDescribe 为 null 或空
                currentPosition.append("请连接到可传输数据的网络\n");
            }
            //currentPosition.append("更新时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
//            currentPosition.append("定位方式：");
//            if (location.getLocType() == BDLocation.TypeGpsLocation) {
//                Log.d("LocationTypeCheck", "Location type is TypeGpsLocation");
//                currentPosition.append("请连接到可传输数据的网络\n"); // 添加换行符
//            }
//            else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {
//                currentPosition.append("网络");
//            }

            locationInfo.setText(currentPosition);
        }
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();
//        if (Settings.canDrawOverlays(this)) {
            showFloatWindow();
            showToolFloatWindow();
//        }

    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
        FloatWindowManager.get().hindToolView(String.valueOf(this.hashCode()));
        FloatWindowManager.get().hindToolView("NAV_TOOL_" + this.hashCode()); // 新悬浮窗 [^2][^19]
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e("showToolView", "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.e("showToolView", "onDestroy");
        unregisterReceiver(mGeoFenceReceiver);
        handler.removeCallbacks(locationUpdateRunnable);
        mLocationClient.stop();
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        FloatWindowManager.get().hindToolView(String.valueOf(this.hashCode()));
        FloatWindowManager.get().hindToolView("NAV_TOOL_" + this.hashCode());
        //bleManager.onDestroy();

    }


    private void walkNavigate(LatLng endPt) {
        WalkNavigateHelper.getInstance().initNaviEngine(this, new IWEngineInitListener() {

            @Override
            public void engineInitSuccess() {
                //引擎初始化成功回调
                routeWalkPlanWithParam(endPt);
            }

            @Override
            public void engineInitFail() {
                //引擎初始化失败回调
            }
        });
    }
    //开启步行导航
    private void routeWalkPlanWithParam(LatLng endPt) {
        //起终点位置
        WalkRouteNodeInfo startNodeInfo = new WalkRouteNodeInfo();
        startNodeInfo.setLocation(new LatLng(mCurrentLat, mCurrentLon));
        WalkRouteNodeInfo endNodeInfo = new WalkRouteNodeInfo();
        endNodeInfo.setLocation(endPt);
        WalkNaviLaunchParam mWalkParam = new WalkNaviLaunchParam().startNodeInfo(startNodeInfo).endNodeInfo(endNodeInfo);

        //发起算路

        WalkNavigateHelper.getInstance().routePlanWithRouteNode(mWalkParam, new IWRoutePlanListener() {
            @Override
            public void onRoutePlanStart() {
                //开始算路的回调
            }

            @Override
            public void onRoutePlanSuccess() {
                //算路成功
                //跳转至诱导页面
                Intent intent = new Intent(CrucialMapViewImplement.this, WNaviGuideActivity.class);
                startActivity(intent);
            }

            @Override
            public void onRoutePlanFail(WalkRoutePlanError walkRoutePlanError) {
                //算路失败的回调
            }
        });
    }

    //移动视角的实现
    private void moveCamera(BaiduMap map, LatLng latLng, float zoom) {
        map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latLng, zoom));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    // 修改打卡按钮点击事件设置
    public void onCheckInButtonClicked(String markerId) {
        Log.d(TAG, "打卡按钮点击，markerId: " + markerId + ", taskType: " + currentTaskType);
        
        if (ActivitySelection.TASK_TYPE_AGENT.equals(currentTaskType)) {
            // 特工任务的验证逻辑
            if (currentFenceId != null && currentFenceId.equals(markerId) ) {
                // 特工任务需要同时满足地理围栏和蓝牙验证
                startPhotoActivity(markerId);
            } else {
                Toast.makeText(this, "任务需要在指定位置，请前往指定地点", Toast.LENGTH_SHORT).show();
            }
        } 
    }
    
    // 启动拍照功能
    private void startPhotoActivity(String markerId) {
        Log.d(TAG, "启动拍照界面，传递markerId: " + markerId);
        
        // 存储当前打卡的markerId，用于拍照完成后继续处理
        SharedPreferences prefs = getSharedPreferences("checkin_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("current_checkin_marker", markerId);
        editor.apply();
        
        // 跳转到拍照页面
        Intent intent = new Intent(this, PhotoActivity.class);
        intent.putExtra("marker_id", markerId);
        startActivityForResult(intent, REQUEST_TASK_TIMER); // 设置请求码1001，用于识别拍照返回
    }
    
    // 添加新方法：重置打卡状态
    private void resetCheckInStatus(String markerId) {
        // 清除所有标记点的打卡状态
        SharedPreferences prefs = getSharedPreferences("checkin_status", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // 如果传入了特定的markerId，只重置该标记点
        if (markerId != null && !markerId.isEmpty()) {
            editor.remove("checking_in_" + markerId);
        } else {
            // 否则重置所有标记点
            for (String key : taskMarkers.keySet()) {
                editor.remove("checking_in_" + key);
            }
        }
        
        editor.apply();
    }
}
