package com.example.lbsdemo.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.geofence.GeoFence;
import com.example.lbsdemo.R;
import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.chat.ChatMessage;
import com.example.lbsdemo.chat.ChatMessageAdapter;
import com.example.lbsdemo.llm.ImageLLMManager;
import com.example.lbsdemo.llm.LLMManager;
import com.example.lbsdemo.llm.TaskGenerator;
import com.example.lbsdemo.llm.TextLLMManager;
import com.example.lbsdemo.map.FloatWindowManager;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.task.TaskGenerationService;
import com.example.lbsdemo.task.TaskVerificationData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.utils.GeoFenceManager;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
//import com.google.android.gms.location.Geofence;

public class ActivitySelection extends AppCompatActivity implements GeoFenceManager.OnGeoFenceStatusListener {
    private AppDatabase db;
    private Button generateTaskButton;
    private ViewGroup activityList;
    private String userId;
    private BroadcastReceiver taskReceiver;
    private boolean isTaskGenerating = false; // 添加标志来跟踪任务生成状态
    private GeoFenceManager geoFenceManager; // 添加地理围栏管理器
    private BroadcastReceiver geoFenceReceiver; // 添加地理围栏广播接收器
    private BroadcastReceiver locationReceiver;

    // 定义常量
    private static final String PREF_BUTTON_DISABLED = "button_disabled";
    private static final String PREF_BUTTON_DISABLED_UNTIL = "button_disabled_until";

    // 侧边栏按钮和内容区域
    private Button sidebarBtn1, sidebarBtn2, sidebarBtn3;
    private View contentTasks, contentPage2, contentPage3;

    // 添加新的成员变量
    private EditText messageInput;
    private ImageButton sendButton;
    private RecyclerView chatRecyclerView;
    private ProgressBar chatLoadingIndicator;
    private ChatMessageAdapter chatAdapter;
    private LLMManager llmManager;
    private TextLLMManager textLLMManager;

    // 添加打字指示器相关变量
    private View typingIndicator;
    private CardView typingIndicatorCard;
    private TextView tvDotOne, tvDotTwo, tvDotThree;
    private Handler animationHandler = new Handler();
    private Runnable dotAnimation;

    // 添加请求码常量
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_VERIFICATION_IMAGE_CAPTURE = 3;
    private static final int REQUEST_VERIFICATION_PICK_IMAGE = 4;
    private static final int PERMISSION_REQUEST_LOCATION = 100;
    private String currentPhotoPath;

    // 照片验证相关变量
    private TaskData currentVerificationTask;
    private String verificationPrompt;
    private Uri currentVerificationPhotoUri;

    private TaskGenerator taskGenerator;
    private String characterId = "agent_zero"; // 特工Zero的角色ID
    private int currentAgentStage = 1; // 当前特工任务阶段

    // 任务计时器相关变量
    private View taskTimerCardView;
    private TextView tvTimerTaskTitle;
    private TextView tvTimerTaskLocation;
    private TextView tvTimerCountdown;
    private TextView tvTimerStatus;
    private ProgressBar progressTimer;
    private Button btnTimerCancel;
    private CountDownTimer taskCountDownTimer;
    private boolean isTaskTimerRunning = false;
    private long taskTimeLeftInMillis;
    private long taskTotalTimeInMillis;
    private TaskData currentTimerTask;
    private Handler taskLocationHandler = new Handler();
    private Runnable taskLocationChecker;
    private static final int TASK_LOCATION_CHECK_INTERVAL = 10000; // 10秒检查一次位置
    private static final int REQUEST_TASK_TIMER = 5;
    private int taskIdToShowCompletionMessages = -1; // <-- 1. Add member variable

    // 在类的开头添加常量定义
    public static final String TASK_TYPE_DAILY = "daily_task";
    public static final String TASK_TYPE_AGENT = "agent_task";
    public static final String EXTRA_TASK_TYPE = "task_type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);
        
        // --- 2. Modify onCreate --- 
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("task_completed", false)) {
            int taskId = intent.getIntExtra("task_id", -1);
            if (taskId != -1) {
                 this.taskIdToShowCompletionMessages = taskId; // Store task ID
                 updateTaskStatusInBackground(taskId); // Only update DB status here
                 // Removed call to markAgentTaskAsCompleted(taskId);
            }
        }
        // --- End Modify onCreate ---

        activityList = findViewById(R.id.activityList);
        //tvResponse = findViewById(R.id.tv_response);

        // 隐藏浮动窗口
        FloatWindowManager.get().hideToolView();

        // 初始化数据库
        db = AppDatabase.getInstance(this);

        // 获取当前用户ID
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", "");
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // 启用返回按钮
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        generateTaskButton = findViewById(R.id.btn_generate_task);

        // 设置返回按钮点击事件
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 返回到主界面
                onBackPressed();
            }
        });

        // 初始化任务计时器视图
        setupTaskTimerViews();

        // 初始化LLM管理器
        llmManager = LLMManager.getInstance(this);
        textLLMManager = TextLLMManager.getInstance(this);

        // 初始化侧边栏和内容区域
        initSidebarAndContent();

        // 先设置按钮文本和点击事件
        setupGenerateTaskButton();

        // 然后检查按钮是否应该被禁用
        checkButtonDisabledState();

        // 加载今日任务
        loadTodayTasks();

        // 在应用启动时设置每日定时任务生成
        //TaskScheduler.scheduleDailyTaskGeneration(this);

        // 注册广播接收器
        registerTaskReceiver();

        // 添加日志记录第三个按钮的状态
        Button sidebarBtn3 = findViewById(R.id.sidebar_btn_3);
        if (sidebarBtn3 != null) {
            Log.d("ActivitySelection", "onCreate中第三个按钮状态: 存在, 可见性=" +
                    (sidebarBtn3.getVisibility() == View.VISIBLE ? "VISIBLE" :
                            sidebarBtn3.getVisibility() == View.INVISIBLE ? "INVISIBLE" : "GONE"));
            // 确保第三个按钮隐藏
            sidebarBtn3.setVisibility(View.GONE);
        } else {
            Log.d("ActivitySelection", "onCreate中第三个按钮状态: 不存在");
        }

        // 初始化任务生成器
        taskGenerator = new TaskGenerator(this);

        // 检查特工角色是否存在，不存在则创建
        checkAndCreateAgentCharacter();

        // 初始化地理围栏管理器
        geoFenceManager = new GeoFenceManager(this);
        geoFenceManager.setOnGeoFenceStatusListener(this);
        geoFenceManager.initGeoFence();

        // 注册地理围栏广播接收器
        registerGeoFenceReceiver();

        // 注册位置广播接收器
        registerLocationReceiver();
    }

    // --- 3. Create updateTaskStatusInBackground --- 
    private void updateTaskStatusInBackground(int taskId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase database = AppDatabase.getInstance(getApplicationContext()); 
            if (database == null) {
                 Log.e("ActivitySelection", "[updateTaskStatusInBackground] Database instance is null!");
                 return;
            }
             TaskData task = database.taskDao().getTaskById(taskId);
             if (task != null) {
                 task.isCompleted = true;
                 database.taskDao().updateTask(task);
                 Log.d("ActivitySelection", "[updateTaskStatusInBackground] Task status updated for taskId: " + taskId);
             } else {
                 Log.w("ActivitySelection", "[updateTaskStatusInBackground] Task not found for ID: " + taskId);
             }
        });
    }
    // --- End Create updateTaskStatusInBackground --- 

    // 这里删除了addAgentTaskButtonToPage2方法

    // 创建选项菜单，添加刷新按钮和特工任务按钮
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 添加特工任务按钮到菜单 - 放在刷新按钮左侧
        MenuItem agentTaskItem = menu.add(Menu.NONE, R.id.action_agent_task, 1, "特工任务");
        agentTaskItem.setIcon(android.R.drawable.ic_menu_compass); // 使用系统自带图标
        agentTaskItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // 添加刷新按钮到菜单
        MenuItem refreshItem = menu.add(Menu.NONE, R.id.action_refresh, 2, "刷新");
        refreshItem.setIcon(android.R.drawable.ic_menu_rotate);
        refreshItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // 添加测试照片验证按钮
        MenuItem testPhotoItem = menu.add(Menu.NONE, R.id.action_test_photo, 3, "测试照片验证");
        testPhotoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return true;
    }

    // 处理菜单项点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            // 显示刷新提示
            Toast.makeText(this, "正在刷新任务列表...", Toast.LENGTH_SHORT).show();

            // 清空当前任务列表
            activityList.removeAllViews();

            // 添加加载动画或提示（可选）
            View loadingView = LayoutInflater.from(this).inflate(R.layout.empty_task_view, null, false);
            TextView tvEmptyMessage = loadingView.findViewById(R.id.tvEmptyMessage);
            tvEmptyMessage.setText("正在加载任务...");
            activityList.addView(loadingView);

            // 延迟500毫秒后重新加载任务，提供视觉反馈
            new Handler().postDelayed(() -> {
                loadTodayTasks();
            }, 500);

            return true;
        } else if (id == R.id.action_test_photo) {
            // 创建测试照片验证任务
            createTestPhotoVerificationTask();
            return true;
        } else if (id == R.id.action_agent_task) {
            // 启动特工任务活动
            Intent intent = new Intent(this, AgentTaskActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void registerTaskReceiver() {
        taskReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TaskGenerationService.ACTION_TASK_GENERATED.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(TaskGenerationService.EXTRA_TASK_SUCCESS, false);
                    // 无论成功与否，都立即重新启用按钮
                    // generateTaskButton.setEnabled(true); // 不直接设置按钮状态，因为按钮将在loadTodayTasks中重新添加
                    isTaskGenerating = false; // 重置任务生成状态

                    // 更新按钮状态，立即解除禁用状态
                    SharedPreferences prefs = getSharedPreferences("button_state", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(PREF_BUTTON_DISABLED, false);
                    editor.apply();

                    // 临时存储需要保留的生成按钮（如果存在）
                    Button generateTaskBtn = null;
                    for (int i = 0; i < activityList.getChildCount(); i++) {
                        View child = activityList.getChildAt(i);
                        if (child.getId() == R.id.btn_generate_task) {
                            generateTaskBtn = (Button) child;
                            generateTaskBtn.setEnabled(true); // 确保按钮被启用
                            break;
                        }
                    }

                    // 清除任务列表并显示加载指示器
                    activityList.removeAllViews();
                    View loadingView = LayoutInflater.from(ActivitySelection.this).inflate(R.layout.empty_task_view, null, false);
                    TextView tvEmptyMessage = loadingView.findViewById(R.id.tvEmptyMessage);
                    tvEmptyMessage.setText("正在加载最新任务...");
                    activityList.addView(loadingView);

                    if (success) {
                        // 显示生成成功的提示
                        Toast.makeText(ActivitySelection.this, "任务生成成功，正在加载...", Toast.LENGTH_SHORT).show();

                        // 延迟加载新任务，给数据库足够时间完成操作
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                loadTodayTasks();
                            }
                        }, 1500); // 增加延迟到1.5秒
                    } else {
                        // 显示错误信息
                        Toast.makeText(ActivitySelection.this, "生成任务失败，请稍后再试", Toast.LENGTH_SHORT).show();

                        // 即使失败，也尝试加载任何可能存在的任务
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                loadTodayTasks();
                            }
                        }, 1000);
                    }
                }
            }
        };
        // 注册广播接收器
        IntentFilter filter = new IntentFilter(TaskGenerationService.ACTION_TASK_GENERATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(taskReceiver, filter, RECEIVER_NOT_EXPORTED);
        }
    }

    // 注册地理围栏广播接收器
    private void registerGeoFenceReceiver() {
        geoFenceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(GeoFenceManager.GEOFENCE_BROADCAST_ACTION)) {
                    geoFenceManager.handleGeoFenceBroadcast(context, intent);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(geoFenceReceiver, new IntentFilter(GeoFenceManager.GEOFENCE_BROADCAST_ACTION), RECEIVER_NOT_EXPORTED);
        }
    }

    // 实现 OnGeoFenceStatusListener 接口方法
    @Override
    public void onUserEnterFence(String customId) {
        Log.i("GeoFence", "用户进入围栏：" + customId);
//        runOnUiThread(() -> {
//            Toast.makeText(this, "进入区域：" + customId, Toast.LENGTH_SHORT).show();
//        });
    }

    @Override
    public void onUserLeaveFence(String customId) {
        Log.i("GeoFence", "用户离开围栏：" + customId);
//        runOnUiThread(() -> {
//            Toast.makeText(this, "离开区域：" + customId, Toast.LENGTH_SHORT).show();
//        });
    }

    private void loadTodayTasks() {
        if (userId.isEmpty()) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        // 清空现有任务视图，但保留生成任务按钮
        Button generateTaskBtn = (Button) IntStream.range(0, activityList.getChildCount()).mapToObj(i -> activityList.getChildAt(i)).filter(child -> child.getId() == R.id.btn_generate_task).findFirst().orElse(null);

        activityList.removeAllViews();

        // 添加加载中提示
        View loadingView = LayoutInflater.from(this).inflate(R.layout.empty_task_view, null, false);
        AtomicReference<TextView> tvEmptyMessage = new AtomicReference<>(loadingView.findViewById(R.id.tvEmptyMessage));
        tvEmptyMessage.get().setText("正在加载任务数据...");
        activityList.addView(loadingView);

        // 获取今天的日期
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        // 异步加载任务
        Executors.newSingleThreadExecutor().execute(() -> {
            List<TaskData> tasks = db.taskDao().getTasksForDate(userId, today);
            // 确保在UI线程更新界面
            runOnUiThread(() -> {
                // 再次清空视图，移除加载提示
                activityList.removeAllViews();

                if (tasks.isEmpty()) {
                    // 如果没有今日任务，显示生成任务提示
                    View emptyView = LayoutInflater.from(this).inflate(R.layout.empty_task_view, null, false);
                    tvEmptyMessage.set(emptyView.findViewById(R.id.tvEmptyMessage));
                    tvEmptyMessage.get().setText("今日暂无学习任务，点击下方按钮生成");
                    activityList.addView(emptyView);
                } else {
                    // 显示任务列表
                    for (TaskData task : tasks) {
                        addTaskView(task);
                    }
                }

                // 添加生成任务按钮到最下方
                if (generateTaskBtn != null) {
                    activityList.addView(generateTaskBtn);
                } else {
                    // 如果按钮不存在，则创建一个新的
                    Button newGenerateTaskBtn = new Button(this);
                    newGenerateTaskBtn.setId(R.id.btn_generate_task);
                    newGenerateTaskBtn.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    newGenerateTaskBtn.setText("生成今日学习任务");
                    newGenerateTaskBtn.setTextSize(18);
                    newGenerateTaskBtn.setPadding(12, 12, 12, 12);
                    newGenerateTaskBtn.setBackgroundTintList(getResources().getColorStateList(R.color.button_color));

                    // 设置上边距
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) newGenerateTaskBtn.getLayoutParams();
                    params.topMargin = 16;
                    newGenerateTaskBtn.setLayoutParams(params);

                    // 设置点击事件
                    newGenerateTaskBtn.setOnClickListener(v -> {
                        if (userId.isEmpty()) {
                            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 检查是否已经有任务生成过程在进行
                        if (isTaskGenerating) {
                            Toast.makeText(this, "任务生成中，请稍候...", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 标记任务生成状态
                        isTaskGenerating = true;

                        // 禁用按钮，防止重复点击
                        newGenerateTaskBtn.setEnabled(false);

                        // 保存按钮禁用状态和时间
                        SharedPreferences prefs = getSharedPreferences("button_state", MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(PREF_BUTTON_DISABLED, true);
                        // 设置10秒后按钮可用
                        long disabledUntil = System.currentTimeMillis() + 10000;
                        editor.putLong(PREF_BUTTON_DISABLED_UNTIL, disabledUntil);
                        editor.apply();

                        // 开始10秒倒计时
                        startCountdown(10);
                    });

                    activityList.addView(newGenerateTaskBtn);
                    // 更新成员变量引用
                    generateTaskButton = newGenerateTaskBtn;
                }
            });
        });
    }


    private void addTaskView(TaskData task) {
        View taskView = LayoutInflater.from(this).inflate(R.layout.task_item_view, null, false);
        TextView tvTaskTitle = taskView.findViewById(R.id.tvTaskTitle);
        TextView tvTaskDescription = taskView.findViewById(R.id.tvTaskDescription);
        TextView tvTaskLocation = taskView.findViewById(R.id.tvTaskLocation);
        TextView tvTaskTime = taskView.findViewById(R.id.tvTaskTime);
        Button btnAcceptTask = taskView.findViewById(R.id.btnAcceptTask);

        // 设置任务详细信息
        tvTaskTitle.setText(task.title);
        tvTaskDescription.setText(task.description);
        tvTaskLocation.setText("地点: " + task.location);
        tvTaskTime.setText("时间: " + task.startTime + " (" + task.durationMinutes + "分钟)");

        // 根据任务状态设置按钮文本和状态
        if (task.isCompleted) {
            btnAcceptTask.setText("已完成");
            btnAcceptTask.setEnabled(false);
        } else if ("accepted".equals(task.status)) {
            btnAcceptTask.setText("查看任务");
            btnAcceptTask.setEnabled(true);
        } else {
            btnAcceptTask.setText("接受任务");
            btnAcceptTask.setEnabled(true);
        }

        // 点击任务接受按钮
        btnAcceptTask.setOnClickListener(v -> {
            // 不再使用全局状态，而是更新任务自身的status
            if (!"accepted".equals(task.status)) {
                // 更新数据库中的任务状态
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    task.status = "accepted";
                    AppDatabase.getInstance(this).taskDao().updateTask(task);
                });
                btnAcceptTask.setText("查看任务");
            }

            // 向地图页面传递任务位置信息和任务详情
            navigateToMapWithLocation(task);
        });

        activityList.addView(taskView);
    }

    /**
     * 根据任务位置导航到地图
     */
    private void navigateToMapWithLocation(TaskData task) {
        // 添加详细日志，显示原始位置信息
        Log.d("ActivitySelection", "导航任务位置原始信息: " + task.location);

        // 根据位置名称确定要导航的位置索引
        String location = task.location;
        int locationIndex = 1; // 默认为第一个位置

        // 定义所有可能的建筑代码，与TaskGenerator中保持一致
        String[] allBuildingCodes = {"SB", "CB", "SD", "FB", "SC", "SA", "PB", "MA", "MB", "EB", "EE"};

        // 首先，查找位置字符串中是否有明确的楼号格式（如 "FB楼: "）
        for (String code : allBuildingCodes) {
            String pattern = code + "楼:";
            if (location.contains(pattern)) {
                // 找到明确格式的楼号，记录匹配的代码
                Log.d("ActivitySelection", "找到明确楼号格式: " + pattern);
                switch (code) {
                    case "SB": locationIndex = 1; break;
                    case "CB": locationIndex = 2; break;
                    case "SD": locationIndex = 3; break;
                    case "FB": locationIndex = 4; break;
                    case "SC": locationIndex = 5; break;
                    case "SA": locationIndex = 6; break;
                    case "PB": locationIndex = 7; break;
                    case "MA": locationIndex = 8; break;
                    case "MB": locationIndex = 9; break;
                    case "EB": locationIndex = 10; break;
                    case "EE": locationIndex = 11; break;
                }
                Log.d("ActivitySelection", "设置位置索引为: " + locationIndex + " (代码: " + code + ")");
                // 找到明确的楼号后，不再继续检查
                break;
            }
        }

        // 如果没有找到明确的楼号格式，则使用旧的检测方式作为后备
        if (locationIndex == 1) {
            Log.d("ActivitySelection", "使用常规方式检测楼号...");
            if (location.contains("SB")) {
                locationIndex = 1;
                Log.d("ActivitySelection", "检测到SB，设置索引为1");
            } else if (location.contains("CB") || location.contains("图书馆")|| location.contains("中心图书馆")) {
                locationIndex = 2;
                Log.d("ActivitySelection", "检测到CB/图书馆，设置索引为2");
            } else if (location.contains("SD")) {
                locationIndex = 3;
                Log.d("ActivitySelection", "检测到SD，设置索引为3");
            } else if (location.contains("FB") || location.contains("英语")) {
                locationIndex = 4;
                Log.d("ActivitySelection", "检测到FB，设置索引为4");
            } else if (location.contains("SC")) {
                locationIndex = 5;
                Log.d("ActivitySelection", "检测到SC，设置索引为5");
            } else if (location.contains("SA")) {
                locationIndex = 6;
                Log.d("ActivitySelection", "检测到SA，设置索引为6");
            } else if (location.contains("PB")) {
                locationIndex = 7;
                Log.d("ActivitySelection", "检测到PB，设置索引为7");
            } else if (location.contains("MA")) {
                locationIndex = 8;
                Log.d("ActivitySelection", "检测到MA，设置索引为8");
            } else if (location.contains("MB")) {
                locationIndex = 9;
                Log.d("ActivitySelection", "检测到MB，设置索引为9");
            } else if (location.contains("EB")) {
                locationIndex = 10;
                Log.d("ActivitySelection", "检测到EB，设置索引为10");
            } else if (location.contains("EE")) {
                locationIndex = 11;
                Log.d("ActivitySelection", "检测到EE，设置索引为11");
            }
        }

        // 记录最终的导航结果
        Log.d("ActivitySelection", "最终导航到位置索引: " + locationIndex);

        // 创建导航意图
        Intent intent = new Intent();
        intent.putExtra("index", locationIndex);

        // 额外的任务数据，帮助地图活动显示任务信息
        intent.putExtra("task_id", task.id);
        intent.putExtra("task_title", task.title);
        intent.putExtra("task_location", task.location);
        intent.putExtra("task_time", task.startTime + " (" + task.durationMinutes + "分钟)");
        intent.putExtra("task_description", task.description);
        if (task.characterId != null && task.characterId.equals(characterId)) {
            intent.putExtra(EXTRA_TASK_TYPE, TASK_TYPE_AGENT); // 特工任务
            Log.d("TaskType", "导航到特工任务: " + task.title + ", ID: " + task.id);
        } else {
            intent.putExtra(EXTRA_TASK_TYPE, TASK_TYPE_DAILY);  // 日常任务
            Log.d("TaskType", "导航到日常任务: " + task.title + ", ID: " + task.id);
        }
        // 构建任务详情文本，用于标记点弹窗
        String markerContent = task.title + "\n" +
                "地点：" + task.location + "\n" +
                (task.startTime != null ? "时间：" + task.startTime + "\n" : "") +
                "描述：" + task.description + "\n" +
                "时长：" + task.durationMinutes + "分钟";

        intent.putExtra("marker_content", markerContent);

        // 添加拍照验证相关信息
        if ("photo".equals(task.verificationMethod)) {
            intent.putExtra("verify_photo", true);
            intent.putExtra("position_id", task.positionID);
        }
        setResult(RESULT_OK,intent);
        finish();
    }
    private void setupGenerateTaskButton() {
        // 确保按钮已初始化
        if (generateTaskButton != null) {
            // 设置按钮点击事件
            generateTaskButton.setOnClickListener(v -> {
                if (userId.isEmpty()) {
                    Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查是否已经有任务生成过程在进行
                if (isTaskGenerating) {
                    Toast.makeText(this, "任务生成中，请稍候...", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 标记任务生成状态
                isTaskGenerating = true;

                // 禁用按钮，防止重复点击
                generateTaskButton.setEnabled(false);

                // 保存按钮禁用状态和时间
                SharedPreferences prefs = getSharedPreferences("button_state", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(PREF_BUTTON_DISABLED, true);
                // 设置10秒后按钮可用
                long disabledUntil = System.currentTimeMillis() + 10000;
                editor.putLong(PREF_BUTTON_DISABLED_UNTIL, disabledUntil);
                editor.apply();

                // 开始10秒倒计时
                startCountdown(10);
            });
        }
    }

    private void navigateToMap(int index) {
        Intent intent = new Intent();
        intent.putExtra("index", index);
        setResult(RESULT_OK, intent);
        finish();
    }



    // 检查按钮是否应该被禁用
    private void checkButtonDisabledState() {
        SharedPreferences prefs = getSharedPreferences("button_state", MODE_PRIVATE);
        boolean isDisabled = prefs.getBoolean(PREF_BUTTON_DISABLED, false);
        long disabledUntil = prefs.getLong(PREF_BUTTON_DISABLED_UNTIL, 0);
        long currentTime = System.currentTimeMillis();

        if (isDisabled && currentTime < disabledUntil) {
            // 按钮仍在禁用期内
            generateTaskButton.setEnabled(false);

            // 计算剩余禁用时间（秒）
            int secondsRemaining = (int)((disabledUntil - currentTime) / 1000) + 1;

            // 显示当前状态
            Toast.makeText(this, "按钮禁用中，剩余" + secondsRemaining + "秒", Toast.LENGTH_SHORT).show();

            // 重新启动倒计时
            startCountdown(secondsRemaining);
        } else if (isDisabled) {
            // 禁用期已过，但状态未更新
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PREF_BUTTON_DISABLED, false);
            editor.apply();
            generateTaskButton.setEnabled(true);
        }
    }

    // 开始倒计时
    private void startCountdown(int seconds) {
        final int[] secondsRemaining = {seconds};
        final Handler handler = new Handler();
        final Runnable[] countdownRunnable = new Runnable[1];

        countdownRunnable[0] = new Runnable() {
            @Override
            public void run() {
                // 显示剩余等待时间
                Toast.makeText(ActivitySelection.this,
                        "请等待" + secondsRemaining[0] + "秒",
                        Toast.LENGTH_SHORT).show();

                secondsRemaining[0]--;

                if (secondsRemaining[0] > 0) {
                    // 继续倒计时
                    handler.postDelayed(this, 1000);
                } else {
                    // 倒计时结束
                    if (secondsRemaining[0] == 0) {
                        // 只有在正常倒计时结束时才启动服务
                        Toast.makeText(ActivitySelection.this,
                                "开始生成学习任务",
                                Toast.LENGTH_SHORT).show();

                        // 启动任务生成服务
                        Intent serviceIntent = new Intent(ActivitySelection.this, TaskGenerationService.class);
                        serviceIntent.setAction("GENERATE_NOW");
                        startService(serviceIntent);
                    }

                    // 更新按钮状态
                    SharedPreferences prefs = getSharedPreferences("button_state", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(PREF_BUTTON_DISABLED, false);
                    editor.apply();

                    // 任务生成后，按钮会通过广播接收器自动启用
                }
            }
        };

        // 开始倒计时
        handler.post(countdownRunnable[0]);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 在页面恢复时再次检查按钮状态
        checkButtonDisabledState();

        // 添加日志记录第三个按钮的状态
        if (sidebarBtn3 != null) {
            Log.d("ActivitySelection", "onResume中第三个按钮状态: 可见性=" +
                    (sidebarBtn3.getVisibility() == View.VISIBLE ? "VISIBLE" :
                            sidebarBtn3.getVisibility() == View.INVISIBLE ? "INVISIBLE" : "GONE"));

            // 确保第三个按钮可见
            sidebarBtn3.setVisibility(View.VISIBLE);
            Log.d("ActivitySelection", "onResume中设置第三个按钮可见性为VISIBLE");
        } else {
            Log.d("ActivitySelection", "onResume中第三个按钮为null");
        }
    }

    /**
     * 初始化侧边栏和内容区域
     */
    private void initSidebarAndContent() {
        // 初始化侧边栏按钮
        sidebarBtn1 = findViewById(R.id.sidebar_btn_1);
        sidebarBtn2 = findViewById(R.id.sidebar_btn_2);
        sidebarBtn3 = findViewById(R.id.sidebar_btn_3);

        // 添加日志记录第三个按钮的初始化状态
        Log.d("ActivitySelection", "第三个按钮初始化: " + (sidebarBtn3 != null ? "成功" : "失败"));

        // 初始化内容区域
        contentTasks = findViewById(R.id.content_tasks);
        contentPage2 = findViewById(R.id.content_page2);
        contentPage3 = findViewById(R.id.content_page3);

        // 设置按钮点击事件
        sidebarBtn1.setOnClickListener(v -> switchContent(1));
        sidebarBtn2.setOnClickListener(v -> switchContent(2));
        sidebarBtn3.setOnClickListener(v -> {
            Log.d("ActivitySelection", "第三个按钮被点击");
            switchContent(3);
        });

        // 确保第三个按钮可见
        sidebarBtn3.setVisibility(View.VISIBLE);
        Log.d("ActivitySelection", "第三个按钮可见性设置为: " + (sidebarBtn3.getVisibility() == View.VISIBLE ? "VISIBLE" : "非VISIBLE"));

        // 初始化聊天视图
        initChatView();
    }

    /**
     * 切换显示内容
     * @param index 内容索引
     */
    private void switchContent(int index) {
        Log.d("ActivitySelection", "切换内容到: " + index);

        // 重置所有按钮状态
        sidebarBtn1.setBackground(getDrawable(R.drawable.sidebar_button_normal));
        sidebarBtn2.setBackground(getDrawable(R.drawable.sidebar_button_normal));
        sidebarBtn3.setBackground(getDrawable(R.drawable.sidebar_button_normal));

        // 隐藏所有内容
        contentTasks.setVisibility(View.GONE);
        contentPage2.setVisibility(View.GONE);
        contentPage3.setVisibility(View.GONE);

        // 根据选择显示对应内容和设置按钮状态
        switch (index) {
            case 1:
                contentTasks.setVisibility(View.VISIBLE);
                sidebarBtn1.setBackground(getDrawable(R.drawable.sidebar_button_selected));
                break;
            case 2:
                contentPage2.setVisibility(View.VISIBLE);
                sidebarBtn2.setBackground(getDrawable(R.drawable.sidebar_button_selected));
                break;
            case 3:
                Log.d("ActivitySelection", "显示第三个内容页面");
                contentPage3.setVisibility(View.VISIBLE);
                sidebarBtn3.setBackground(getDrawable(R.drawable.sidebar_button_selected));
                loadAgentTaskContent();
                break;
        }
    }

    // 新增方法：初始化聊天界面
    private void initChatView() {
        try {
            // 获取聊天界面中的控件
            messageInput = findViewById(R.id.messageInput);
            sendButton = findViewById(R.id.sendButton);
            chatRecyclerView = findViewById(R.id.chatRecyclerView);
            chatLoadingIndicator = findViewById(R.id.loadingIndicator);

            // 初始化图片选择按钮
            ImageButton imagePickerButton = findViewById(R.id.imagePickerButton);
            if (imagePickerButton != null) {
                imagePickerButton.setOnClickListener(v -> {
                    // 显示媒体选择悬浮窗
                    showMediaPickerPopup(v);
                });
            }

            // 初始化打字指示器
            typingIndicator = findViewById(R.id.typingIndicator);
            typingIndicatorCard = typingIndicator.findViewById(R.id.typingIndicatorCard);
            tvDotOne = typingIndicator.findViewById(R.id.tvDotOne);
            tvDotTwo = typingIndicator.findViewById(R.id.tvDotTwo);
            tvDotThree = typingIndicator.findViewById(R.id.tvDotThree);

            // 初始化点点动画
            initDotAnimation();

            if (chatRecyclerView == null) {
                Log.e("ActivitySelection", "找不到chatRecyclerView控件");
                return;
            }

            // 设置RecyclerView
            chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));

            // 初始化聊天消息适配器
            String userId = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_id", "default_user");
            chatAdapter = new ChatMessageAdapter(this, new ArrayList<>(), new ChatMessageAdapter.ChatMessageListener() {
                @Override
                public void onTaskAccepted(int taskId) {
                    // 处理任务接受事件
                    handleTaskAccepted(taskId);
                }

                @Override
                public void onTaskRejected(int taskId) {
                    // 处理任务拒绝事件
                    handleTaskRejected(taskId);
                }

                @Override
                public void onAgentTaskCardClicked(TaskData task) {
                    // 处理特工任务卡片点击
                    if (task.isCompleted) {
                        // 任务已完成，显示提示
                        Toast.makeText(ActivitySelection.this, "任务已完成", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 设置验证提示词（用于后续验证）
                    if (task.photoVerificationPrompt != null && !task.photoVerificationPrompt.isEmpty()) {
                        verificationPrompt = task.photoVerificationPrompt;
                    } else {
                        verificationPrompt = "请拍摄一张能够证明你正在完成以下任务的照片: " + task.title;
                    }

                    // 统一使用verifyAgentTask进行验证
                    verifyAgentTask(task);
                }

                @Override
                public TaskData getTaskById(int taskId) {
                    // 从数据库获取任务
                    return AppDatabase.getInstance(ActivitySelection.this).taskDao().getTaskById(taskId);
                }
            });
            chatRecyclerView.setAdapter(chatAdapter);

            // 设置任务卡片监听器
            chatAdapter.setTaskCardListener(new ChatMessageAdapter.TaskCardListener() {
                @Override
                public void onTaskAccepted(int taskId) {
                    // 处理任务接受事件
                    handleTaskAccepted(taskId);
                }

                @Override
                public void onTaskRejected(int taskId) {
                    // 处理任务拒绝事件
                    handleTaskRejected(taskId);
                }

                @Override
                public TaskData getTaskById(int taskId) {
                    // 从数据库获取任务
                    return AppDatabase.getInstance(ActivitySelection.this).taskDao().getTaskById(taskId);
                }
            });

            // 初始化LLM管理器
            llmManager = LLMManager.getInstance(this);
            llmManager.initialize(null, null);  // 使用默认配置

            // 加载历史聊天记录
            loadChatHistory(userId);

            sendButton.setOnClickListener(v -> {
                String message = messageInput.getText().toString().trim();
                if (!message.isEmpty()) {
                    // 显示用户消息
                    ChatMessage userMessage = new ChatMessage(userId, "user", message);
                    chatAdapter.addMessage(userMessage);
                    scrollChatToBottom();

                    // 保存用户消息到数据库
                    saveChatMessageToDatabase(userMessage);

                    // 清空输入框
                    messageInput.setText("");

                    // 显示加载指示器
                    if (chatLoadingIndicator != null) {
                        // chatLoadingIndicator.setVisibility(View.VISIBLE); // 不再显示加载转盘
                    }

                    // 显示打字指示器
                    showTypingIndicator();

                    // 使用LLM接口发送消息
                    llmManager.sendMessage(userId, message, null, new LLMManager.LLMResponseCallback() {
                        @Override
                        public void onResponse(String response) {
                            // 在UI线程处理响应
                            runOnUiThread(() -> {
                                // 隐藏加载指示器
                                if (chatLoadingIndicator != null) {
                                    chatLoadingIndicator.setVisibility(View.GONE);
                                }

                                // 隐藏打字指示器
                                hideTypingIndicator();

                                // 始终先清理响应中的JSON部分
                                String cleanResponse = removeJsonFromResponse(response);

                                // 如果清理后的响应为空或只包含空白字符，添加友好提示
                                if (cleanResponse.trim().isEmpty()) {
                                    cleanResponse = "我已为您准备了一个学习任务建议，请查看下方的任务卡片。";
                                }

                                // 解析响应中的任务建议
                                TaskData taskData = parseLLMResponseForTaskSuggestion(response);

                                if (taskData != null) {
                                    // 如果包含任务建议，添加任务卡片
                                    taskData.userId = userId;
                                    taskData.creationDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                                    taskData.creationTimestamp = System.currentTimeMillis();
                                    taskData.taskType = "daily"; // 设置任务类型为每日任务
                                    taskData.status = "pending"; // 设置初始状态为待定

                                    // 显示AI文本回复（已经清理了JSON部分）
                                    ChatMessage aiMessage = new ChatMessage(userId, "assistant", cleanResponse);
                                    chatAdapter.addMessage(aiMessage);

                                    // 保存AI回复到数据库
                                    saveChatMessageToDatabase(aiMessage);

                                    // 先向UI添加任务卡片（不保存到数据库，等待任务保存后再保存消息）
                                    addTaskCardToChat(taskData);

                                    // 保存任务到数据库并自动更新ID
                                    saveTaskToDatabase(taskData);
                                } else {
                                    // 显示干净的AI回复 (没有任务但仍然需要清理可能的JSON)
                                    ChatMessage aiMessage = new ChatMessage(userId, "assistant", cleanResponse);
                                    chatAdapter.addMessage(aiMessage);

                                    // 保存AI回复到数据库
                                    saveChatMessageToDatabase(aiMessage);
                                }

                                scrollChatToBottom();
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            // 在UI线程处理错误
                            runOnUiThread(() -> {
                                // 隐藏加载指示器（添加非空检查）
                                if (chatLoadingIndicator != null) {
                                    chatLoadingIndicator.setVisibility(View.GONE);
                                }

                                // 隐藏打字指示器
                                hideTypingIndicator();

                                // 显示错误消息
                                String errorMsg = "无法获取回复: " + e.getMessage();
                                ChatMessage errorMessage = new ChatMessage(userId, "system", errorMsg);
                                chatAdapter.addMessage(errorMessage);

                                // 保存错误消息到数据库
                                saveChatMessageToDatabase(errorMessage);

                                scrollChatToBottom();
                            });
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e("ActivitySelection", "初始化聊天视图时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 加载聊天历史记录
     * @param userId 用户ID
     */
    private void loadChatHistory(String userId) {
        if (chatLoadingIndicator != null) {
            // chatLoadingIndicator.setVisibility(View.VISIBLE); // 不再显示加载转盘
        }

        // 使用Room数据库异步加载聊天记录
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase dbInstance = AppDatabase.getInstance(getApplicationContext());
            if (dbInstance == null) {
                 Log.e("ActivitySelection", "[loadChatHistory] Database instance is null!");
                 return;
            }
            List<ChatMessage> messages = dbInstance.chatMessageDao().getRecentMessagesByUserId(userId, 50);

            runOnUiThread(() -> {
                if (chatAdapter != null) {
                    chatAdapter.clearMessages(); 

                    // Add historical messages
                    for (ChatMessage message : messages) {
                        chatAdapter.addMessage(message);
                    }

                    // Check and add completion messages if needed
                    if (taskIdToShowCompletionMessages != -1) {
                        addCompletionMessagesToChat(taskIdToShowCompletionMessages);
                        taskIdToShowCompletionMessages = -1; // Reset after adding
                    }

                    // Add welcome message if no history and no completion messages were just added
                    if (chatAdapter.getItemCount() == 0) { 
                        ChatMessage welcomeMessage = new ChatMessage(userId, "assistant", "传讯已建立，双面镜已唤醒，密探暗影。这里是信使Zero。霍格沃兹我们需要您协助调查校园内的黑雾组织活动。请随时准备接收密令。");
                        welcomeMessage.senderName = "特工Zero";
                        chatAdapter.addMessage(welcomeMessage);
                        saveChatMessageToDatabase(welcomeMessage);
                    }

                    // Scroll to bottom after all messages are potentially added
                    if (chatAdapter.getItemCount() > 0) {
                        scrollChatToBottom(false);
                    }
                }

                // 隐藏加载指示器
                if (chatLoadingIndicator != null) {
                    chatLoadingIndicator.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * 保存聊天消息到数据库
     * @param message 要保存的消息
     */
    private void saveChatMessageToDatabase(ChatMessage message) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            db.chatMessageDao().insert(message);
        });
    }

    // 添加滚动到底部的方法
    private void scrollChatToBottom() {
        scrollChatToBottom(true); // 默认使用平滑滚动
    }

    /**
     * 滚动聊天记录到底部
     * @param smooth 是否使用平滑滚动
     */
    private void scrollChatToBottom(boolean smooth) {
        if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
            if (smooth) {
                chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            } else {
                chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        }
    }

    /**
     * 从LLM响应中解析任务建议
     */
    private TaskData parseLLMResponseForTaskSuggestion(String response) {
        try {
            // 查找JSON部分
            int startIndex = response.indexOf("```json");
            if (startIndex != -1) {
                // 如果是代码块格式，找到代码块内容的开始
                startIndex = response.indexOf("{", startIndex);
            } else {
                // 否则直接查找任务建议JSON
                startIndex = response.indexOf("{\"type\":\"task_suggestion\"");
                if (startIndex == -1) {
                    return null;
                }
                startIndex = response.indexOf("{", startIndex);
            }

            if (startIndex == -1) return null;

            // 找到JSON结束位置
            int endIndex = findMatchingCloseBrace(response, startIndex);
            if (endIndex == -1) return null;

            // 提取JSON字符串
            String jsonStr = response.substring(startIndex, endIndex + 1);
            Log.d("ActivitySelection", "提取的任务JSON: " + jsonStr);

            // 解析JSON
            JSONObject jsonObject = new JSONObject(jsonStr);

            // 检查类型
            if (!"task_suggestion".equals(jsonObject.optString("type"))) {
                return null;
            }

            // 获取任务对象
            JSONObject taskObj = jsonObject.getJSONObject("task");

            // 创建任务数据对象
            TaskData taskData = new TaskData();
            taskData.title = taskObj.getString("title");
            taskData.description = taskObj.getString("description");
            taskData.location = taskObj.getString("location");
            taskData.durationMinutes = taskObj.getInt("duration_minutes");
            taskData.priority = 2; // 默认优先级
            taskData.isCompleted = false;
            taskData.startTime = ""; // 默认开始时间为空，用户可以自行决定
            taskData.status = "pending"; // 设置初始状态为待定

            return taskData;
        } catch (Exception e) {
            Log.e("ActivitySelection", "解析任务建议出错: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 查找匹配的右大括号
     * @param text 文本
     * @param startIndex 左大括号的位置
     * @return 匹配的右大括号位置，如果没有找到则返回-1
     */
    private int findMatchingCloseBrace(String text, int startIndex) {
        int count = 1;
        for (int i = startIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                count++;
            } else if (c == '}') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 从响应中移除JSON部分
     */
    private String removeJsonFromResponse(String response) {
        // 识别各种可能的JSON模式
        int jsonStartIndex = -1;
        int jsonEndIndex = -1;

        // 模式1: 代码块中的JSON (```json ... ```)
        int codeBlockStart = response.indexOf("```json");
        if (codeBlockStart != -1) {
            int codeBlockEnd = response.indexOf("```", codeBlockStart + 6);
            if (codeBlockEnd != -1) {
                jsonStartIndex = codeBlockStart;
                jsonEndIndex = codeBlockEnd + 3; // 包含结束的```
            }
        }

        // 模式2: 直接的JSON对象 (没有代码块，但有任务建议)
        if (jsonStartIndex == -1) {
            int taskSuggestionStart = response.indexOf("{\"type\":\"task_suggestion\"");
            if (taskSuggestionStart != -1) {
                // 找到完整JSON的结束括号
                int braceStart = taskSuggestionStart;
                int matchingBrace = findMatchingCloseBrace(response, braceStart);
                if (matchingBrace != -1) {
                    jsonStartIndex = taskSuggestionStart;
                    jsonEndIndex = matchingBrace + 1; // 包含最后的}
                }
            }
        }

        // 模式3: 检查任何可能的JSON对象，包括其他格式
        if (jsonStartIndex == -1) {
            int potentialJsonStart = response.indexOf("{");
            if (potentialJsonStart != -1) {
                int potentialJsonEnd = findMatchingCloseBrace(response, potentialJsonStart);
                if (potentialJsonEnd != -1) {
                    // 验证这是否是有效的JSON
                    String jsonCandidate = response.substring(potentialJsonStart, potentialJsonEnd + 1);
                    try {
                        new JSONObject(jsonCandidate);
                        // 如果能解析为JSON，则认为是有效的JSON
                        jsonStartIndex = potentialJsonStart;
                        jsonEndIndex = potentialJsonEnd + 1;
                    } catch (Exception ignored) {
                        // 不是有效的JSON，忽略
                    }
                }
            }
        }

        // 如果找到了JSON部分，则将其移除
        if (jsonStartIndex != -1 && jsonEndIndex != -1) {
            // 提取JSON前后的文本
            String beforeJson = response.substring(0, jsonStartIndex).trim();
            String afterJson = response.substring(jsonEndIndex).trim();

            Log.d("ActivitySelection", "已从响应中移除JSON部分");

            // 合并前后文本
            if (beforeJson.isEmpty() && afterJson.isEmpty()) {
                // 如果JSON是整个响应，返回默认消息
                return "我已为您准备了一个学习任务建议，请查看下方的任务卡片。";
            } else if (beforeJson.isEmpty()) {
                return afterJson;
            } else if (afterJson.isEmpty()) {
                return beforeJson;
            } else {
                // 用空格连接，使显示更清晰
                return beforeJson + " " + afterJson;
            }
        }

        // 如果没有找到JSON，返回原响应
        return response;
    }

    /**
     * 保存任务到数据库
     */
    private void saveTaskToDatabase(TaskData taskData) {
        // 显示加载提示
        Toast.makeText(this, "正在保存任务...", Toast.LENGTH_SHORT).show();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 设置创建时间和状态
                if (taskData.creationDate == null) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    taskData.creationDate = dateFormat.format(new Date());
                }
                if (taskData.creationTimestamp == 0) {
                    taskData.creationTimestamp = System.currentTimeMillis();
                }

                // 确保任务状态正确
                taskData.isCompleted = false;
                if (taskData.status == null) {
                    taskData.status = "pending";
                }

                // 保存任务到数据库
                long taskId = AppDatabase.getInstance(this).taskDao().insertTask(taskData);

                // 更新任务ID
                if (taskId > 0) {
                    taskData.id = (int) taskId;
                    Log.d("ActivitySelection", "任务已保存到数据库，ID: " + taskId);

                    // 在UI线程添加任务卡片到聊天界面
                    runOnUiThread(() -> {
                        // 创建任务卡片消息
                        ChatMessage taskCardMessage = new ChatMessage();
                        taskCardMessage.userId = userId;
                        taskCardMessage.role = "assistant";
                        taskCardMessage.content = "这是一个任务卡片: " + taskData.title;
                        taskCardMessage.timestamp = System.currentTimeMillis();
                        taskCardMessage.relatedTaskId = taskData.id; // 使用新的任务ID
                        taskCardMessage.messageType = "task_card";

                        // 添加任务卡片到聊天界面
                        chatAdapter.addMessage(taskCardMessage);

                        // 保存任务卡片消息到数据库
                        saveChatMessageToDatabase(taskCardMessage);

                        // 滚动到底部
                        scrollChatToBottom();

                        // 显示成功提示
                        Toast.makeText(ActivitySelection.this, "任务已保存并添加到聊天", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // 任务保存失败
                    runOnUiThread(() -> {
                        Toast.makeText(ActivitySelection.this, "任务保存失败，请重试", Toast.LENGTH_SHORT).show();

                        // 添加错误消息到聊天
                        ChatMessage errorMessage = new ChatMessage();
                        errorMessage.userId = userId;
                        errorMessage.role = "system";
                        errorMessage.content = "任务保存失败，请重试或联系管理员";
                        errorMessage.timestamp = System.currentTimeMillis();

                        chatAdapter.addMessage(errorMessage);
                        saveChatMessageToDatabase(errorMessage);
                    });
                }
            } catch (Exception e) {
                Log.e("ActivitySelection", "保存任务失败: " + e.getMessage(), e);

                // 在UI线程显示错误
                runOnUiThread(() -> {
                    Toast.makeText(ActivitySelection.this, "保存任务时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // 添加错误消息到聊天
                    ChatMessage errorMessage = new ChatMessage();
                    errorMessage.userId = userId;
                    errorMessage.role = "system";
                    errorMessage.content = "保存任务时出错: " + e.getMessage();
                    errorMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(errorMessage);
                    saveChatMessageToDatabase(errorMessage);
                });
            }
        });
    }

    /**
     * 添加任务卡片到聊天界面
     */
    private void addTaskCardToChat(TaskData taskData) {
        // 创建任务卡片消息
        ChatMessage taskCardMessage = new ChatMessage();
        taskCardMessage.userId = userId;
        taskCardMessage.role = "assistant";
        taskCardMessage.content = "这是一个任务卡片: " + taskData.title;
        taskCardMessage.timestamp = System.currentTimeMillis();
        taskCardMessage.relatedTaskId = taskData.id; // 可能是临时ID=0
        taskCardMessage.messageType = "task_card";

        // 添加到UI
        chatAdapter.addMessage(taskCardMessage);
    }

    /**
     * 处理任务接受事件
     */
    private void handleTaskAccepted(int taskId) {
        // 从数据库获取任务
        AppDatabase.databaseWriteExecutor.execute(() -> {
            TaskData task = AppDatabase.getInstance(this).taskDao().getTaskById(taskId);

            if (task != null) {
                // 更新任务状态为已接受
                task.status = "accepted";
                AppDatabase.getInstance(this).taskDao().updateTask(task);

                // 检查是否是需要照片验证的任务
                if ("photo".equals(task.verificationMethod)) {
                    // 所有拍照任务设置为不需要位置验证
                    task.positionID = 0;
                    AppDatabase.getInstance(this).taskDao().updateTask(task);

                    // 保存当前验证任务
                    currentVerificationTask = task;

                    // 使用任务中的照片验证提示词（如果有）
                    if (task.photoVerificationPrompt != null && !task.photoVerificationPrompt.isEmpty()) {
                        verificationPrompt = task.photoVerificationPrompt;
                    } else {
                        // 没有预设提示词，生成一个默认的
                        verificationPrompt = "请拍摄一张能够证明你正在完成以下任务的照片: " + task.title +
                                "\n任务详情: " + task.description;
                        if (task.location != null && !task.location.isEmpty()) {
                            verificationPrompt += "\n地点: " + task.location;
                        }
                    }

                    // 在UI线程显示提示
                    runOnUiThread(() -> {
                        Toast.makeText(this, "任务已接受: " + task.title, Toast.LENGTH_SHORT).show();

                        // 添加系统消息通知
                        String userId = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_id", "default_user");
                        ChatMessage systemMessage = new ChatMessage(userId, "system",
                                "你已接受任务: " + task.title + "，请提供照片完成验证。");
                        systemMessage.messageType = "system";
                        chatAdapter.addMessage(systemMessage);

                        // 保存系统消息到数据库
                        saveChatMessageToDatabase(systemMessage);

                        // 显示左下角拍照按钮
                        showPhotoButton();
                    });
                } else {
                    // 其他类型任务处理
                    runOnUiThread(() -> {
                        Toast.makeText(this, "任务已接受: " + task.title, Toast.LENGTH_SHORT).show();

                        // 添加系统消息通知
                        String userId = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_id", "default_user");
                        ChatMessage systemMessage = new ChatMessage(userId, "system", "你已接受任务: " + task.title);
                        systemMessage.messageType = "system";
                        chatAdapter.addMessage(systemMessage);

                        // 保存系统消息到数据库
                        saveChatMessageToDatabase(systemMessage);

                        // 隐藏左下角拍照按钮（如果已显示）
                        hidePhotoButton();
                    });
                }
            }
        });
    }

    /**
     * 处理任务拒绝事件
     */
    private void handleTaskRejected(int taskId) {
        // 从数据库获取任务
        AppDatabase.databaseWriteExecutor.execute(() -> {
            TaskData task = AppDatabase.getInstance(this).taskDao().getTaskById(taskId);

            if (task != null) {
                // 仅更新任务状态为已拒绝，不再使用全局SharedPreferences
                task.status = "rejected";
                AppDatabase.getInstance(this).taskDao().updateTask(task);

                runOnUiThread(() -> {
                    Toast.makeText(this, "已拒绝任务: " + task.title, Toast.LENGTH_SHORT).show();

                    // 添加系统消息通知
                    String userId = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_id", "default_user");
                    ChatMessage systemMessage = new ChatMessage(userId, "system", "你已拒绝任务: " + task.title);
                    systemMessage.messageType = "system";
                    chatAdapter.addMessage(systemMessage);

                    // 保存系统消息到数据库
                    saveChatMessageToDatabase(systemMessage);
                });
            }
        });
    }

    /**
     * 初始化点点动画
     */
    private void initDotAnimation() {
        final int[] animationState = {0};
        dotAnimation = new Runnable() {
            @Override
            public void run() {
                switch (animationState[0]) {
                    case 0:
                        tvDotOne.setAlpha(1.0f);
                        tvDotTwo.setAlpha(0.3f);
                        tvDotThree.setAlpha(0.3f);
                        break;
                    case 1:
                        tvDotOne.setAlpha(0.3f);
                        tvDotTwo.setAlpha(1.0f);
                        tvDotThree.setAlpha(0.3f);
                        break;
                    case 2:
                        tvDotOne.setAlpha(0.3f);
                        tvDotTwo.setAlpha(0.3f);
                        tvDotThree.setAlpha(1.0f);
                        break;
                }

                animationState[0] = (animationState[0] + 1) % 3;
                animationHandler.postDelayed(this, 300); // 每300毫秒切换一次状态
            }
        };
    }

    /**
     * 显示打字指示器
     */
    private void showTypingIndicator() {
        if (typingIndicatorCard != null) {
            typingIndicatorCard.setVisibility(View.VISIBLE);
            // 启动动画
            animationHandler.removeCallbacks(dotAnimation);
            animationHandler.post(dotAnimation);
        }
    }

    /**
     * 隐藏打字指示器
     */
    private void hideTypingIndicator() {
        if (typingIndicatorCard != null) {
            typingIndicatorCard.setVisibility(View.GONE);
            // 停止动画
            animationHandler.removeCallbacks(dotAnimation);
        }
    }

    /**
     * 显示媒体选择悬浮窗
     * @param anchorView 锚点视图
     */
    private void showMediaPickerPopup(View anchorView) {
        try {
            // 加载悬浮窗布局
            View popupView = LayoutInflater.from(this).inflate(R.layout.media_picker_popup, null);

            // 创建PopupWindow
            PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true // 可获取焦点
            );

            // 设置背景，这样点击外部区域时PopupWindow会消失
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.setOutsideTouchable(true);

            // 设置动画
            popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

            // 设置拍照选项点击事件
            View cameraOption = popupView.findViewById(R.id.cameraOption);
            cameraOption.setOnClickListener(v -> {
                // 关闭悬浮窗
                popupWindow.dismiss();

                // 启动相机
                dispatchTakePictureIntent();
            });

            // 设置图库选项点击事件
            View galleryOption = popupView.findViewById(R.id.galleryOption);
            galleryOption.setOnClickListener(v -> {
                // 关闭悬浮窗
                popupWindow.dismiss();

                // 打开图库
                openGallery();
            });

            // 测量视图大小
            popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

            // 计算显示位置 - 在按钮上方
            int[] location = new int[2];
            anchorView.getLocationOnScreen(location);
            int x = location[0] - (popupView.getMeasuredWidth() - anchorView.getWidth()) / 2;
            int y = location[1] - popupView.getMeasuredHeight() - 20; // 上方20dp的间距

            // 显示悬浮窗
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);

        } catch (Exception e) {
            Log.e("ActivitySelection", "显示媒体选择悬浮窗出错: " + e.getMessage(), e);
            Toast.makeText(this, "无法显示媒体选择菜单", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            // 检查是否是任务完成的返回
            if (data.getBooleanExtra("task_completed", false)) {
                int taskId = data.getIntExtra("task_id", -1);
                if (taskId != -1) {
                    // 在数据库中更新任务状态
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        TaskData task = db.taskDao().getTaskById(taskId);
                        if (task != null) {
                            task.isCompleted = true;
                            db.taskDao().updateTask(task);

                            // 在UI线程添加完成消息到聊天
                            runOnUiThread(() -> {
                                // 添加系统消息到聊天
                                ChatMessage systemMessage = new ChatMessage();
                                systemMessage.userId = userId;
                                systemMessage.role = "system";
                                systemMessage.content = "任务已完成: " + task.title;
                                systemMessage.messageType = "system";
                                systemMessage.timestamp = System.currentTimeMillis();
                                chatAdapter.addMessage(systemMessage);
                                saveChatMessageToDatabase(systemMessage);

                                // 添加特工Zero的表扬消息
                                ChatMessage agentMessage = new ChatMessage();
                                agentMessage.userId = userId;
                                agentMessage.role = "assistant";
                                agentMessage.content = "做得好，夜鸦！你成功完成了密令，守护了霍格沃兹。" + generateRandomCompliment();
                                agentMessage.senderName = "Zero";
                                agentMessage.timestamp = System.currentTimeMillis();
                                chatAdapter.addMessage(agentMessage);
                                saveChatMessageToDatabase(agentMessage);

                                // 滚动到底部
                                scrollChatToBottom();
                            });
                        }
                    });
                }
            }
        }
        // 处理图片选择结果
        if (resultCode == RESULT_OK) {
            Uri imageUri = null;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                // 处理相机拍照结果
                if (currentPhotoPath != null) {
                    File f = new File(currentPhotoPath);
                    imageUri = Uri.fromFile(f);

                    // 将照片添加到图库
                    galleryAddPic();
                }
            } else if (requestCode == REQUEST_PICK_IMAGE && data != null) {
                // 处理图库选择结果
                imageUri = data.getData();
            } else if (requestCode == REQUEST_VERIFICATION_IMAGE_CAPTURE) {
                // 处理任务验证的相机拍照结果
                if (currentVerificationPhotoUri != null) {
                    // 处理验证照片
                    verifyPhotoWithLLM(currentVerificationPhotoUri);
                    return;
                }
            } else if (requestCode == REQUEST_VERIFICATION_PICK_IMAGE && data != null) {
                // 处理任务验证的图库选择结果
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    // 处理验证照片
                    verifyPhotoWithLLM(selectedImageUri);
                    return;
                }
            }

            // 处理聊天中的一般图片
            if (imageUri != null) {
                processSelectedImage(imageUri);
            }
        }
    }

    /**
     * 处理选择的图片
     */
    private void processSelectedImage(Uri imageUri) {
        try {
            // 获取当前用户ID
            String currentUserId = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_id", "default_user");

            // 保存图片到本地存储
            String savedImagePath = saveImageToLocalStorage(imageUri);

            // 创建图片消息并添加到聊天
            ChatMessage imageMessage = new ChatMessage(
                    currentUserId,
                    "user",
                    "图片消息"
            );
            imageMessage.setImageUri(imageUri.toString());
            imageMessage.timestamp = System.currentTimeMillis();

            // 保存图片消息到数据库
            saveChatMessageToDatabase(imageMessage);

            // 添加到聊天适配器
            if (chatAdapter != null) {
                chatAdapter.addMessage(imageMessage);
                chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

                // 显示打字指示器
                showTypingIndicator();

                // 使用专门的图片处理方法
                processImageWithLLM(imageUri, currentUserId, typingIndicator);
            }

            // 显示保存成功的提示
            if (savedImagePath != null) {
                Toast.makeText(this, "图片已保存到: " + savedImagePath, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "处理图片时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("ActivitySelection", "处理图片时出错", e);
        }
    }

    /**
     * 保存图片到本地存储
     * @param sourceUri 源图片URI
     * @return 保存后的图片路径，失败返回null
     */
    private String saveImageToLocalStorage(Uri sourceUri) {
        try {
            // 创建目标文件夹
            File picturesDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ChatImages");
            if (!picturesDir.exists()) {
                if (!picturesDir.mkdirs()) {
                    Log.e("ActivitySelection", "无法创建图片目录");
                    return null;
                }
            }

            // 创建目标文件
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "IMG_" + timeStamp + ".jpg";
            File destFile = new File(picturesDir, imageFileName);

            // 复制文件内容
            java.io.InputStream in = getContentResolver().openInputStream(sourceUri);
            java.io.OutputStream out = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            in.close();
            out.close();

            // 通知媒体库更新
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(destFile);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);

            // 返回保存的路径
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("ActivitySelection", "保存图片到本地存储时出错: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 使用专门的图片LLM模型处理图片
     * @param imageUri 图片URI
     * @param userId 用户ID
     * @param typingIndicator 打字指示器视图
     */
    private void processImageWithLLM(Uri imageUri, String userId, View typingIndicator) {
        // 使用专用的图片LLM管理器
        ImageLLMManager imageLLM = ImageLLMManager.getInstance(this);

        // 构建更优化的提示文本
        String promptText = "请用简体中文分析这张图片，遵循以下要求：\n" +
                "1. 描述图片中的主要物体、场景和人物\n" +
                "2. 如果有文字，请识别并提取出来\n" +
                "3. 回答必须使用规范的中文，不要使用英文或其他语言\n" +
                "4. 保持回答简洁明了，不超过200字\n" +
                "5. 不要使用特殊符号或复杂格式\n" +
                "6. 如果无法清晰识别图片内容，请直接说明";

        // 调用带有自定义提示的处理方法
        imageLLM.processImage(imageUri, promptText, new ImageLLMManager.ImageLLMCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    // 隐藏打字指示器
                    hideTypingIndicator();

                    // 清理响应文本，移除可能导致乱码的字符
                    String cleanedResponse = cleanResponseText(response);

                    // 创建AI响应消息
                    ChatMessage aiResponse = new ChatMessage(
                            userId,
                            "assistant",
                            cleanedResponse
                    );

                    // 保存AI响应到数据库
                    saveChatMessageToDatabase(aiResponse);

                    // 添加到聊天记录
                    if (chatAdapter != null) {
                        chatAdapter.addMessage(aiResponse);
                        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    // 隐藏打字指示器
                    hideTypingIndicator();

                    // 显示错误消息
                    Toast.makeText(ActivitySelection.this,
                            "图片处理出错: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // 添加错误消息到聊天
                    ChatMessage errorMessage = new ChatMessage(
                            userId,
                            "assistant",
                            "很抱歉，处理图片时出现了问题，请稍后再试。"
                    );

                    // 保存错误消息到数据库
                    saveChatMessageToDatabase(errorMessage);

                    if (chatAdapter != null) {
                        chatAdapter.addMessage(errorMessage);
                        chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                });
            }
        });
    }

    /**
     * 清理响应文本，移除可能导致乱码的字符
     * @param response 原始响应文本
     * @return 清理后的文本
     */
    private String cleanResponseText(String response) {
        if (response == null) {
            return "无法解析图片内容";
        }

        try {
            // 记录原始响应（用于调试）
            Log.d("ActivitySelection", "原始响应: " + response);

            // 检查是否包含常见的乱码标记
            if (response.contains("typing") || response.contains("sound") ||
                    response.contains("treasure") || response.contains("MosB") ||
                    response.contains("Furomoslinii") || response.contains("pole South")) {

                // 如果包含这些标记，可能是乱码，尝试提取有效部分
                Log.d("ActivitySelection", "检测到可能的乱码内容，尝试提取有效部分");

                // 尝试提取图片描述的前半部分（通常是有效的）
                int cutoffIndex = response.length() / 2;
                if (response.length() > 100) {
                    // 寻找合适的截断点（句号或换行）
                    for (int i = 100; i < Math.min(cutoffIndex + 100, response.length()); i++) {
                        if (response.charAt(i) == '。' || response.charAt(i) == '\n') {
                            cutoffIndex = i + 1;
                            break;
                        }
                    }
                    response = response.substring(0, cutoffIndex);
                    response += "\n\n[图片分析已截断，因为后续内容可能包含无效文本]";
                }
            }

            // 移除不可见字符和一些特殊符号
            String cleaned = response.replaceAll("[\\p{Cntrl}&&[^\n\r]]", "");

            // 移除连续的空格
            cleaned = cleaned.replaceAll("\\s{2,}", " ");

            // 移除可能导致乱码的特殊字符组合
            cleaned = cleaned.replaceAll("\\p{So}|\\p{Sk}", "");

            // 如果清理后文本为空，返回默认消息
            if (cleaned.trim().isEmpty()) {
                return "图片分析完成，但未能提取有效内容";
            }

            // 记录清理后的响应（用于调试）
            Log.d("ActivitySelection", "清理后响应: " + cleaned);

            return cleaned;
        } catch (Exception e) {
            Log.e("ActivitySelection", "清理响应文本时出错", e);
            return "图片分析过程中出现错误，请稍后再试。错误信息: " + e.getMessage();
        }
    }

    /**
     * 启动相机拍照
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // 创建保存照片的文件
                File photoFile = createImageFile();

                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            "com.example.lbsdemo.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

                    // 记录这是一个拍照请求，以便在onActivityResult中特殊处理
                    SharedPreferences prefs = getSharedPreferences("camera_prefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("is_camera_capture", true);
                    editor.putString("last_photo_path", currentPhotoPath);
                    editor.apply();
                }
            } else {
                Toast.makeText(this, "没有可用的相机应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("ActivitySelection", "启动相机出错: " + e.getMessage(), e);
            Toast.makeText(this, "无法启动相机: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 创建保存照片的文件
     */
    private File createImageFile() throws IOException {
        // 创建图片文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* 前缀 */
                ".jpg",         /* 后缀 */
                storageDir      /* 目录 */
        );

        // 保存文件路径用于后续使用
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    /**
     * 打开图库选择图片
     */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    /**
     * 将照片添加到图库
     */
    private void galleryAddPic() {
        try {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            File f = new File(currentPhotoPath);
            Uri contentUri = Uri.fromFile(f);
            mediaScanIntent.setData(contentUri);
            this.sendBroadcast(mediaScanIntent);

            Log.d("ActivitySelection", "照片已添加到媒体库: " + currentPhotoPath);
        } catch (Exception e) {
            Log.e("ActivitySelection", "添加照片到媒体库时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 生成照片验证提示词
     */
    private void generatePhotoVerificationPrompt(TaskData task) {
        // 根据任务内容生成适合的验证提示词
        String basePrompt = "请拍摄一张能够证明你正在完成以下任务的照片: " + task.title;

        // 添加位置要求（如果有）
        if (task.location != null && !task.location.isEmpty()) {
            basePrompt += "，位置应该是: " + task.location;
        }

        // 添加任务详情（如果有）
        if (task.description != null && !task.description.isEmpty()) {
            basePrompt += "。任务详情: " + task.description;
        }

        // 存储验证提示词
        verificationPrompt = basePrompt;

        // 同时保存到数据库
        String finalBasePrompt = basePrompt;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 检查是否已有验证数据
            TaskVerificationData verificationData =
                    AppDatabase.getInstance(this).taskVerificationDao().getVerificationForTask(task.id);

            if (verificationData == null) {
                // 创建新的验证数据
                verificationData = new TaskVerificationData();
                verificationData.taskId = task.id;
                verificationData.userId = userId;
                verificationData.verificationType = "photo";
                verificationData.photoVerificationPrompt = finalBasePrompt;
                verificationData.verificationStatus = "pending";

                // 插入数据库
                AppDatabase.getInstance(this).taskVerificationDao().insertVerification(verificationData);
            } else {
                // 更新现有验证数据
                verificationData.photoVerificationPrompt = finalBasePrompt;
                verificationData.verificationStatus = "pending";

                // 更新数据库
                AppDatabase.getInstance(this).taskVerificationDao().updateVerification(verificationData);
            }
        });
    }

    /**
     * 显示照片验证选项
     */
    private void showPhotoVerificationOptions() {
        // 创建对话框让用户选择拍照或从图库选择
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("照片验证");
        builder.setMessage("请选择获取照片的方式来完成任务验证");

        // 添加拍照选项
        builder.setPositiveButton("拍照", (dialog, which) -> {
            // 无需位置验证，直接拍照
            launchVerificationCamera();
        });

        // 添加从图库选择选项
        builder.setNegativeButton("从图库选择", (dialog, which) -> {
            // 检查位置是否符合要求（仅当positionID为1时）
            if (currentVerificationTask != null && currentVerificationTask.positionID == 1) {
                checkLocationBeforeGallery();
            } else {
                // 无需位置验证，直接从图库选择
                openVerificationGallery();
            }
        });

        // 显示对话框
        builder.create().show();
    }

    /**
     * 拍照前检查位置
     */
    private void checkLocationBeforeCapture() {
        // 如果任务没有特定位置要求(positionID=0)，直接进行拍照
        if (currentVerificationTask == null || currentVerificationTask.positionID == 0) {
            launchVerificationCamera();
            return;
        }

        // 1. 获取当前位置的 customId
        String currentCumId = getSharedPreferences("location_prefs", MODE_PRIVATE)
                .getString("current_cum_id", "");
        Log.d("GeoFenceCheckCapture", "[checkLocationBeforeCapture] 读取到的 current_cum_id: " + currentCumId); // <-- 添加日志
        Log.d("GeoFenceCheckCapture", "当前Geofence位置ID: " + currentCumId);

        // 2. 从任务位置提取要求的教学楼 ID
        String taskLocation = currentVerificationTask.location.toUpperCase();
        String requiredLocationId = "";
        String[] allBuildingCodes = {"SB", "CB", "SD", "FB", "SC", "SA", "PB", "MA", "MB", "EB", "EE"};
        for (String code : allBuildingCodes) {
            if (taskLocation.contains(code + "楼") || taskLocation.contains(code + " ") || taskLocation.endsWith(code)) {
                requiredLocationId = code;
                break;
            }
        }
        if (requiredLocationId.isEmpty()){
             for (String code : allBuildingCodes) {
                 if (taskLocation.contains(code)) {
                     requiredLocationId = code;
                     break;
                 }
             }
        }
        Log.d("GeoFenceCheckCapture", "任务要求位置ID: " + requiredLocationId);

        // 3. 比较 ID
        if (!requiredLocationId.isEmpty() && requiredLocationId.equals(currentCumId)) {
            // 位置匹配，允许拍照
            launchVerificationCamera();
        } else {
            // 位置不匹配或无法获取当前位置
            String message;
            if (currentCumId.isEmpty()) {
                message = "无法确认当前位置，请确保您已进入教学楼区域并等待片刻。任务地点：" + currentVerificationTask.location + " (" + requiredLocationId + ")";
            } else {
                message = "您当前在 " + currentCumId + " 楼，不在拍照任务要求的教学楼 (" + requiredLocationId + ")，请前往: " + currentVerificationTask.location;
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            // 显示导航选项对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("拍照位置不符");
            builder.setMessage("您需要前往 " + currentVerificationTask.location + " (" + requiredLocationId + ") 才能进行拍照验证。是否现在导航到该位置？");
            builder.setPositiveButton("导航", (dialog, which) -> {
                navigateToMapWithLocation(currentVerificationTask);
            });
            builder.setNegativeButton("稍后", (dialog, which) -> {
                dialog.dismiss();
            });
            builder.create().show();
        }
    }

    /**
     * 从图库选择前检查位置
     */
    private void checkLocationBeforeGallery() {
        // 如果任务没有特定位置要求(positionID=0)，直接进行图库选择
        if (currentVerificationTask == null || currentVerificationTask.positionID == 0) {
            openVerificationGallery();
            return;
        }

        // 1. 获取当前位置的 customId
        String currentCumId = getSharedPreferences("location_prefs", MODE_PRIVATE)
                .getString("current_cum_id", "");
        Log.d("GeoFenceCheckGallery", "[checkLocationBeforeGallery] 读取到的 current_cum_id: " + currentCumId); // <-- 添加日志
        Log.d("GeoFenceCheckGallery", "当前Geofence位置ID: " + currentCumId);

        // 2. 从任务位置提取要求的教学楼 ID
        String taskLocation = currentVerificationTask.location.toUpperCase();
        String requiredLocationId = "";
        String[] allBuildingCodes = {"SB", "CB", "SD", "FB", "SC", "SA", "PB", "MA", "MB", "EB", "EE"};
        for (String code : allBuildingCodes) {
            if (taskLocation.contains(code + "楼") || taskLocation.contains(code + " ") || taskLocation.endsWith(code)) {
                requiredLocationId = code;
                break;
            }
        }
        if (requiredLocationId.isEmpty()){
             for (String code : allBuildingCodes) {
                 if (taskLocation.contains(code)) {
                     requiredLocationId = code;
                     break;
                 }
             }
        }
        Log.d("GeoFenceCheckGallery", "任务要求位置ID: " + requiredLocationId);

        // 3. 比较 ID
        if (!requiredLocationId.isEmpty() && requiredLocationId.equals(currentCumId)) {
            // 位置匹配，允许选择图片
            openVerificationGallery();
        } else {
            // 位置不匹配或无法获取当前位置
            String message;
            if (currentCumId.isEmpty()) {
                message = "无法确认当前位置，请确保您已进入教学楼区域并等待片刻。任务地点：" + currentVerificationTask.location + " (" + requiredLocationId + ")";
            } else {
                message = "您当前在 " + currentCumId + " 楼，不在图库验证任务要求的教学楼 (" + requiredLocationId + ")，请前往: " + currentVerificationTask.location;
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            // 显示导航选项对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("图库选择位置不符");
            builder.setMessage("您需要前往 " + currentVerificationTask.location + " (" + requiredLocationId + ") 才能使用图库照片进行验证。是否现在导航到该位置？");
            builder.setPositiveButton("导航", (dialog, which) -> {
                navigateToMapWithLocation(currentVerificationTask);
            });
            builder.setNegativeButton("稍后", (dialog, which) -> {
                dialog.dismiss();
            });
            builder.create().show();
        }
    }

    /**
     * 权限请求结果处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，重新尝试位置检查
                checkLocationBeforeCapture();
            } else {
                // 权限被拒绝，显示提示
                Toast.makeText(this, "需要位置权限完成任务验证", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 启动相机进行任务验证拍照
     */
    private void launchVerificationCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // 创建保存照片的文件
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // 处理错误
                Toast.makeText(this, "无法创建图像文件", Toast.LENGTH_SHORT).show();
                return;
            }

            // 如果文件创建成功，继续拍照
            if (photoFile != null) {
                currentVerificationPhotoUri = FileProvider.getUriForFile(this,
                        "com.example.lbsdemo.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentVerificationPhotoUri);
                startActivityForResult(takePictureIntent, REQUEST_VERIFICATION_IMAGE_CAPTURE);
            }
        }
    }

    /**
     * 打开图库选择照片进行任务验证
     */
    private void openVerificationGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VERIFICATION_PICK_IMAGE);
    }

    /**
     * 处理验证照片
     */
    private void verifyPhotoWithLLM(Uri photoUri) {
        // 显示验证中的提示
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在验证照片...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 保存图片URI到验证数据
        String photoUriString = photoUri.toString();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 获取验证数据
            TaskVerificationData verificationData = AppDatabase.getInstance(this)
                    .taskVerificationDao().getVerificationForTask(currentVerificationTask.id);

            // 确保使用任务中定义的验证提示词
            String taskPrompt = currentVerificationTask.photoVerificationPrompt;
            if (taskPrompt == null || taskPrompt.isEmpty()) {
                // 如果任务中没有定义提示词，使用默认的验证提示词
                taskPrompt = verificationPrompt;
            }

            // 最终要使用的提示词
            final String finalPrompt = taskPrompt;

            if (verificationData != null) {
                // 更新验证数据
                verificationData.verificationData = photoUriString;
                verificationData.photoVerificationPrompt = finalPrompt;
                AppDatabase.getInstance(this).taskVerificationDao().updateVerification(verificationData);

                // 使用ImageLLMManager进行照片验证
                ImageLLMManager imageLLM = ImageLLMManager.getInstance(this);
                imageLLM.verifyTaskImage(
                        photoUri,
                        finalPrompt,
                        currentVerificationTask.title,
                        new ImageLLMManager.TaskVerificationCallback() {
                            @Override
                            public void onVerificationSuccess(int score, String feedback) {
                                // 在UI线程处理验证结果
                                runOnUiThread(() -> {
                                    progressDialog.dismiss();

                                    // 更新验证数据
                                    AppDatabase.databaseWriteExecutor.execute(() -> {
                                        TaskVerificationData data = AppDatabase.getInstance(ActivitySelection.this)
                                                .taskVerificationDao().getVerificationForTask(currentVerificationTask.id);

                                        if (data != null) {
                                            data.confidence = score;
                                            // 修改验证阈值，从60改为10，使得10分以上就可以通过
                                            data.verificationResult = score >= 10;
                                            data.verificationStatus = score >= 10 ? "verified" : "failed";
                                            data.feedback = feedback;
                                            data.llmDescription = feedback;

                                            // 更新数据库
                                            AppDatabase.getInstance(ActivitySelection.this)
                                                    .taskVerificationDao().updateVerification(data);

                                            // 如果验证成功，更新任务状态
                                            // 修改验证阈值，从60改为10
                                            if (score >= 10) {
                                                currentVerificationTask.isCompleted = true;
                                                AppDatabase.getInstance(ActivitySelection.this)
                                                        .taskDao().updateTask(currentVerificationTask);
                                            }
                                        }
                                    });

                                    // 根据得分显示成功或失败对话框
                                    // 修改验证阈值，从60改为10
                                    if (score >= 10) {
                                        showVerificationSuccess();
                                    } else {
                                        showVerificationFailure();
                                    }
                                });
                            }

                            @Override
                            public void onVerificationError(Exception e) {
                                // 在UI线程处理验证错误
                                runOnUiThread(() -> {
                                    progressDialog.dismiss();
                                    Toast.makeText(ActivitySelection.this,
                                            "验证失败: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                            }
                        });
            } else {
                // 验证数据不存在，创建新的
                verificationData = new TaskVerificationData();
                verificationData.taskId = currentVerificationTask.id;
                verificationData.userId = userId;
                verificationData.verificationType = "photo";
                verificationData.verificationData = photoUriString;
                verificationData.photoVerificationPrompt = finalPrompt;
                verificationData.verificationStatus = "pending";

                // 插入数据库
                long id = AppDatabase.getInstance(this).taskVerificationDao().insertVerification(verificationData);

                if (id > 0) {
                    // 创建验证数据副本，以便在回调中使用
                    final TaskVerificationData finalVerificationData = verificationData;

                    // 使用ImageLLMManager进行照片验证
                    ImageLLMManager imageLLM = ImageLLMManager.getInstance(this);
                    imageLLM.verifyTaskImage(
                            photoUri,
                            finalPrompt,
                            currentVerificationTask.title,
                            new ImageLLMManager.TaskVerificationCallback() {
                                @Override
                                public void onVerificationSuccess(int score, String feedback) {
                                    // 在UI线程处理验证结果
                                    runOnUiThread(() -> {
                                        progressDialog.dismiss();

                                        // 更新验证数据
                                        AppDatabase.databaseWriteExecutor.execute(() -> {
                                            TaskVerificationData data = AppDatabase.getInstance(ActivitySelection.this)
                                                    .taskVerificationDao().getVerificationForTask(currentVerificationTask.id);

                                            if (data != null) {
                                                data.confidence = score;
                                                // 修改验证阈值，从60改为10，使得10分以上就可以通过
                                                data.verificationResult = score >= 10;
                                                data.verificationStatus = score >= 10 ? "verified" : "failed";
                                                data.feedback = feedback;
                                                data.llmDescription = feedback;

                                                // 更新数据库
                                                AppDatabase.getInstance(ActivitySelection.this)
                                                        .taskVerificationDao().updateVerification(data);

                                                // 如果验证成功，更新任务状态
                                                // 修改验证阈值，从60改为10
                                                if (score >= 10) {
                                                    currentVerificationTask.isCompleted = true;
                                                    AppDatabase.getInstance(ActivitySelection.this)
                                                            .taskDao().updateTask(currentVerificationTask);
                                                }
                                            }
                                        });

                                        // 根据得分显示成功或失败对话框
                                        // 修改验证阈值，从60改为10
                                        if (score >= 10) {
                                            showVerificationSuccess();
                                        } else {
                                            showVerificationFailure();
                                        }
                                    });
                                }

                                @Override
                                public void onVerificationError(Exception e) {
                                    // 在UI线程处理验证错误
                                    runOnUiThread(() -> {
                                        progressDialog.dismiss();
                                        Toast.makeText(ActivitySelection.this,
                                                "验证失败: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    });
                                }
                            });
                } else {
                    // 插入失败
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(ActivitySelection.this,
                                "保存验证数据失败",
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    /**
     * 处理验证结果
     */
    private void handleVerificationResult(int score) {
        // 更新验证数据
        AppDatabase.databaseWriteExecutor.execute(() -> {
            TaskVerificationData verificationData = AppDatabase.getInstance(this)
                    .taskVerificationDao().getVerificationForTask(currentVerificationTask.id);

            if (verificationData != null) {
                // 更新验证数据
                verificationData.confidence = score;
                verificationData.verificationResult = score >= 50;
                verificationData.verificationStatus = score >= 50 ? "verified" : "failed";

                // 添加反馈信息
                if (score >= 50) {
                    verificationData.feedback = "照片验证成功！分数: " + score;
                } else {
                    verificationData.feedback = "照片与任务不符，请重新尝试。分数: " + score;
                }

                // 更新数据库
                AppDatabase.getInstance(this).taskVerificationDao().updateVerification(verificationData);

                // 如果验证成功，更新任务状态
                if (score >= 50) {
                    currentVerificationTask.isCompleted = true;
                    AppDatabase.getInstance(this).taskDao().updateTask(currentVerificationTask);
                }

                // 在UI线程显示结果
                runOnUiThread(() -> {
                    if (score >= 50) {
                        // 验证成功
                        showVerificationSuccess();
                    } else {
                        // 验证失败
                        showVerificationFailure();
                    }
                });
            }
        });
    }

    /**
     * 显示验证成功对话框
     */
    private void showVerificationSuccess() {
        // 检查是否是特工任务
        boolean isAgentTask = currentVerificationTask != null &&
                currentVerificationTask.characterId != null &&
                currentVerificationTask.characterId.equals(characterId);

        if (isAgentTask) {
            // 特工任务验证成功
            markAgentTaskAsCompleted(currentVerificationTask.id);
        } else {
            // 普通任务验证成功，使用原有逻辑
            // 创建成功对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("验证成功");
            builder.setMessage("恭喜！您的照片已通过验证，任务已完成。");
            builder.setIcon(android.R.drawable.ic_dialog_info);

            // 添加确认按钮
            builder.setPositiveButton("好的", (dialog, which) -> {
                // 可以在这里添加额外逻辑，如刷新任务列表
                refreshTaskList();

                // 添加系统消息到聊天
                String userId = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        .getString("user_id", "default_user");
                ChatMessage systemMessage = new ChatMessage(userId, "system",
                        "你已完成任务: " + currentVerificationTask.title);
                systemMessage.messageType = "system";
                chatAdapter.addMessage(systemMessage);

                // 保存系统消息到数据库
                saveChatMessageToDatabase(systemMessage);
            });

            // 显示对话框
            builder.create().show();
        }
    }

    /**
     * 显示验证失败对话框
     */
    private void showVerificationFailure() {
        // 创建失败对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("验证失败");
        builder.setMessage("照片与任务要求不符，请重新尝试。");
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        // 添加重试按钮
        builder.setPositiveButton("重新尝试", (dialog, which) -> {
            // 再次显示照片验证选项
            showPhotoVerificationOptions();
        });

        // 添加取消按钮
        builder.setNegativeButton("取消", null);

        // 显示对话框
        builder.create().show();
    }

    /**
     * 刷新任务列表
     */
    private void refreshTaskList() {
        // 清空现有任务列表
        activityList.removeAllViews();

        // 从数据库重新加载任务
        loadTodayTasks();
    }

    // 创建测试照片验证任务的方法
    private void createTestPhotoVerificationTask() {
        if (userId.isEmpty()) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示生成中提示
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在生成照片验证任务...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 使用TextLLMManager生成任务文本
        textLLMManager.generatePhotoVerificationTaskText(new TextLLMManager.PhotoVerificationTextCallback() {
            @Override
            public void onSuccess(String title, String description, String verificationPrompt) {
                // 获取今天的日期
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                // 创建测试任务
                TaskData testTask = new TaskData();
                testTask.userId = userId;
                testTask.title = title;
                testTask.description = description;
                testTask.location = "任意位置";
                testTask.startTime = "现在";
                testTask.durationMinutes = 10;
                testTask.priority = 3;
                testTask.isCompleted = false;
                testTask.creationDate = today;
                testTask.creationTimestamp = System.currentTimeMillis();
                testTask.taskType = "test";
                testTask.status = "pending";
                testTask.verificationMethod = "photo";

                // 所有拍照任务设置为无需位置验证
                testTask.positionID = 0;

                // 使用LLM生成的验证提示词
                testTask.photoVerificationPrompt = verificationPrompt;

                // 保存任务到数据库
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    long taskId = AppDatabase.getInstance(ActivitySelection.this).taskDao().insertTask(testTask);

                    if (taskId > 0) {
                        // 更新任务ID
                        testTask.id = (int) taskId;

                        runOnUiThread(() -> {
                            // 关闭加载对话框
                            progressDialog.dismiss();

                            Toast.makeText(ActivitySelection.this,
                                    "已创建照片验证测试任务",
                                    Toast.LENGTH_SHORT).show();

                            // 在聊天界面添加系统消息
                            String chatUserId = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_id", "default_user");

                            // 添加AI助手消息，介绍新任务
                            ChatMessage aiMessage = new ChatMessage(chatUserId, "assistant",
                                    "我创建了一个照片验证测试任务，你可以通过拍摄照片来完成验证。");
                            chatAdapter.addMessage(aiMessage);
                            saveChatMessageToDatabase(aiMessage);

                            // 添加任务卡片到聊天界面
                            addTaskCardToChat(testTask);

                            // 滚动聊天到底部
                            if (chatRecyclerView != null) {
                                chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                            }

                            // 刷新任务列表
                            loadTodayTasks();
                        });
                    } else {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(ActivitySelection.this, "创建任务失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(ActivitySelection.this,
                            "生成任务文本失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 显示左下角拍照按钮
     */
    private void showPhotoButton() {
        // 查找照相按钮
        ImageButton photoButton = findViewById(R.id.photoButton);
        if (photoButton != null) {
            photoButton.setVisibility(View.VISIBLE);

            // 设置点击事件，点击后显示照片验证选项
            photoButton.setOnClickListener(v -> {
                showPhotoVerificationOptions();
            });
        }
    }

    /**
     * 隐藏左下角拍照按钮
     */
    private void hidePhotoButton() {
        // 查找照相按钮
        ImageButton photoButton = findViewById(R.id.photoButton);
        if (photoButton != null) {
            photoButton.setVisibility(View.GONE);
        }
    }

    /**
     * 加载特工任务内容到第三个页面
     */
    private void loadAgentTaskContent() {
        Log.d("ActivitySelection", "开始加载特工任务内容");
        try {
            // 检查contentPage3是否为null
            if (contentPage3 == null) {
                Log.e("ActivitySelection", "contentPage3为null");
                return;
            }

            Log.d("ActivitySelection", "contentPage3类型: " + contentPage3.getClass().getName());

            // 清空现有内容
            FrameLayout container = (FrameLayout) ((LinearLayout)contentPage3).getChildAt(0);
            if (container == null) {
                Log.e("ActivitySelection", "container为null");
                return;
            }

            Log.d("ActivitySelection", "清空container内容");
            container.removeAllViews();

            // 加载特工任务布局
            Log.d("ActivitySelection", "开始加载特工任务布局");
            View agentTaskView = getLayoutInflater().inflate(R.layout.activity_agent_task, container, false);
            container.addView(agentTaskView);
            Log.d("ActivitySelection", "特工任务布局加载完成");

            // 初始化特工任务视图中的控件
            TextView tvTaskTitle = agentTaskView.findViewById(R.id.tv_task_title);
            TextView tvTaskDescription = agentTaskView.findViewById(R.id.tv_task_description);
            TextView tvTaskLocation = agentTaskView.findViewById(R.id.tv_task_location);
            Button btnStartTask = agentTaskView.findViewById(R.id.btn_start_task);
            Button btnNextStage = agentTaskView.findViewById(R.id.btn_next_stage);

            // 获取当前用户ID - 使用类成员变量
            String characterId = "agent_zero";

            // 检查特工角色是否存在，不存在则创建
            Log.d("ActivitySelection", "检查特工角色");
            checkAndCreateAgentCharacter();

            // 加载当前任务
            Log.d("ActivitySelection", "开始加载当前任务");
            loadCurrentAgentTask(userId, characterId, tvTaskTitle, tvTaskDescription, tvTaskLocation, btnStartTask, btnNextStage);

            // 设置按钮点击事件
            btnStartTask.setOnClickListener(v -> startAgentTask(userId, characterId));
            btnNextStage.setOnClickListener(v -> moveToNextAgentStage(userId, characterId));
            Log.d("ActivitySelection", "特工任务内容加载完成");
        } catch (Exception e) {
            Log.e("ActivitySelection", "加载特工任务内容失败: " + e.getMessage(), e);
            Toast.makeText(this, "加载任务内容失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查并创建特工角色
     */
    private void checkAndCreateAgentCharacter() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Character agentZero = db.characterDao().getCharacterById(characterId);
            if (agentZero == null) {
                // 创建特工角色
                agentZero = Character.createAgentZero();
                db.characterDao().insertCharacter(agentZero);
                Log.d("ActivitySelection", "Agent Zero character created");
            }
        });
    }

    /**
     * 加载当前特工任务
     */
    private void loadCurrentAgentTask(String userId, String characterId, TextView tvTaskTitle,
                                      TextView tvTaskDescription, TextView tvTaskLocation,
                                      Button btnStartTask, Button btnNextStage) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 查询当前用户的特工任务
            TaskData task = db.taskDao().getLatestTaskByUserIdAndCharacterId(userId, characterId);

            runOnUiThread(() -> {
                if (task != null) {
                    // 更新UI显示任务信息
                    tvTaskTitle.setText(task.title);
                    tvTaskDescription.setText(task.description);
                    tvTaskLocation.setText("地点: " + task.location);

                    // 根据任务状态更新按钮
                    if (task.isCompleted) {
                        btnStartTask.setVisibility(View.GONE);
                        btnNextStage.setVisibility(View.VISIBLE);
                    } else {
                        btnStartTask.setVisibility(View.VISIBLE);
                        btnNextStage.setVisibility(View.GONE);
                    }
                } else {
                    // 没有任务，生成第一个任务
                    // 使用无参数的generateNewAgentTask方法
                    generateNewAgentTask();

                    // 显示默认信息
                    tvTaskTitle.setText("正在生成任务...");
                    tvTaskDescription.setText("请稍候，正在接收凤凰社密令。");
                    tvTaskLocation.setText("地点: 待定");
                    btnStartTask.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * 生成新的特工任务
     */
    private void generateNewAgentTask() {
        // 调用带参数的方法，使用类中已有的userId和characterId
        generateNewAgentTask(userId, characterId, currentAgentStage);
    }

    /**
     * 开始特工任务
     */
    private void startAgentTask(String userId, String characterId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 获取最新任务
            TaskData task = db.taskDao().getLatestTaskByUserIdAndCharacterId(userId, characterId);

            if (task != null) {
                runOnUiThread(() -> {
                    // 显示任务详情对话框
                    showAgentTaskDetailDialog(task);
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "无法加载任务详情", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 显示特工任务详情对话框
     */
    private void showAgentTaskDetailDialog(TaskData task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(task.title);

        // 创建对话框视图
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_task_detail, null);
        TextView tvDescription = dialogView.findViewById(R.id.tv_task_description);
        TextView tvLocation = dialogView.findViewById(R.id.tv_task_location);
        TextView tvVerificationMethod = dialogView.findViewById(R.id.tv_verification_method);
        Button btnVerify = dialogView.findViewById(R.id.btn_verify_task);

        // 设置任务详情
        tvDescription.setText(task.description);
        tvLocation.setText("地点: " + task.location);

        String verificationText = "验证方式: ";
        if ("photo".equals(task.verificationMethod)) {
            verificationText += "拍照验证";
        } else if ("geofence".equals(task.verificationMethod)) {
            verificationText += "位置验证";
        } else if ("time".equals(task.verificationMethod)) {
            verificationText += "时长验证 (" + task.durationMinutes + "分钟)";
        } else {
            verificationText += task.verificationMethod;
        }
        tvVerificationMethod.setText(verificationText);

        builder.setView(dialogView);

        // 添加关闭按钮
        builder.setNegativeButton("关闭", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        // 设置验证按钮点击事件
        btnVerify.setOnClickListener(v -> {
            dialog.dismiss();
            verifyAgentTask(task);
        });

        dialog.show();
    }

    /**
     * 验证特工任务
     */
    private void verifyAgentTask(TaskData task) {
        // 根据验证方式执行不同的验证逻辑
        if ("photo".equals(task.verificationMethod)) {
            // 获取当前位置的cumId（从广播中获取）
            String currentCumId = getSharedPreferences("location_prefs", MODE_PRIVATE)
                    .getString("current_cum_id", "");
            
            Log.d("LocationCheck", "当前位置ID: " + currentCumId);
            
            // 获取任务位置的英文标识
            String taskLocation = task.location.toUpperCase(); 
            String locationId = "";

            // 提取位置标识
            if (taskLocation.contains("EE")) {
                locationId = "EE";
            } else if (taskLocation.contains("CB")) {
                locationId = "CB";
            } else if (taskLocation.contains("SD")) {
                locationId = "SD";
            } else if (taskLocation.contains("FB")) {
                locationId = "FB";
            } else if (taskLocation.contains("SC")) {
                locationId = "SC";
            } else if (taskLocation.contains("SA")) {
                locationId = "SA";
            } else if (taskLocation.contains("PB")) {
                locationId = "PB";
            } else if (taskLocation.contains("MA")) {
                locationId = "MA";
            } else if (taskLocation.contains("MB")) {
                locationId = "MB";
            } else if (taskLocation.contains("EB")) {
                locationId = "EB";
            } else if (taskLocation.contains("SB")) {
                locationId = "SB";
            }

            Log.d("LocationCheck", "任务位置ID: " + locationId);

            // ===================== 开发模式开关 START =====================
            // 要恢复正常的位置验证，只需注释掉下面这行代码
            // boolean devMode = false;  // 设置为 true 允许在任何位置验证
            // // ===================== 开发模式开关 END =====================

//            if (devMode) {
//                // 开发模式：跳过位置验证，直接允许拍照
//                Log.d("LocationCheck", "开发模式：跳过位置验证，允许在任何位置拍照");
//                //Toast.makeText(this, "开发模式：允许拍照验证", Toast.LENGTH_SHORT).show();
//                currentVerificationTask = task;
//                showPhotoVerificationOptions();
//                return;
//            }

            // 正常的位置验证逻辑
            if (locationId.equals(currentCumId) && !currentCumId.isEmpty()) {
                // 位置匹配且有效，允许拍照
                Toast.makeText(this, "位置验证成功，请拍照验证任务", Toast.LENGTH_SHORT).show();
                currentVerificationTask = task;
                showPhotoVerificationOptions();
            } else {
                // 位置不匹配或无效，提示用户前往指定地点
                String message = currentCumId.isEmpty() ?
                        "无法获取当前位置，请确保在指定地点: " + task.location :
                        "您当前不在指定地点，请前往: " + task.location;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                
                // 显示导航选项对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("拍照位置不符");
                builder.setMessage("您需要前往 " + task.location + " (" + locationId + ") 才能进行拍照验证。是否现在导航到该位置？");
                builder.setPositiveButton("导航", (dialog, which) -> {
                    navigateToMapWithLocation(task);
                });
                builder.setNegativeButton("稍后", (dialog, which) -> {
                    dialog.dismiss();
                });
                builder.create().show();
            }
        } else if ("time".equals(task.verificationMethod)) {
            // 时长验证的逻辑保持不变
            Toast.makeText(this, "需要在指定位置停留" + task.durationMinutes + "分钟", Toast.LENGTH_SHORT).show();
            markAgentTaskAsCompleted(task.id);
        } else if ("time+geofence".equals(task.verificationMethod)) {
            // // ===================== 开发模式开关 START =====================
            // // 该段代码仅用于开发调试，上线前必须注释掉！
            // boolean devMode = false;  // 设置为 true 允许立即完成任务
            // if (devMode) {
            //     // 立即完成任务
            //     AppDatabase.databaseWriteExecutor.execute(() -> {
            //         // 更新任务状态为已完成
            //         task.isCompleted = true;
            //         db.taskDao().updateTask(task);

            //         // 创建验证数据
            //         TaskVerificationData verificationData = new TaskVerificationData();
            //         verificationData.taskId = task.id;
            //         verificationData.userId = userId;
            //         verificationData.verificationType = "time+geofence";
            //         verificationData.verificationData = "在" + task.location + "停留了" + task.durationMinutes + "分钟";
            //         verificationData.verificationResult = true;
            //         verificationData.confidence = 100;
            //         verificationData.verificationStatus = "completed";
            //         verificationData.timestamp = System.currentTimeMillis();

            //         // 保存验证数据
            //         db.taskVerificationDao().insertVerification(verificationData);

            //         // 在UI线程处理后续操作
            //         runOnUiThread(() -> {
            //             //Toast.makeText(ActivitySelection.this, "开发模式：任务已立即完成", Toast.LENGTH_SHORT).show();
                        
            //             // 添加系统消息到聊天
            //             ChatMessage systemMessage = new ChatMessage();
            //             systemMessage.userId = userId;
            //             systemMessage.role = "system";
            //             systemMessage.content = "任务已完成: " + task.title;
            //             systemMessage.messageType = "system";
            //             systemMessage.timestamp = System.currentTimeMillis();
            //             chatAdapter.addMessage(systemMessage);
            //             saveChatMessageToDatabase(systemMessage);

            //             // 添加特工Zero的表扬消息
            //             ChatMessage agentMessage = new ChatMessage();
            //             agentMessage.userId = userId;
            //             agentMessage.role = "assistant";
            //             agentMessage.content = "做得好，夜鸦！你成功完成了密令，守护了霍格沃兹。" + generateRandomCompliment();
            //             agentMessage.senderName = "Zero";
            //             agentMessage.timestamp = System.currentTimeMillis();
            //             chatAdapter.addMessage(agentMessage);
            //             saveChatMessageToDatabase(agentMessage);

            //             // 启动下一阶段任务
            //             moveToNextAgentStage();

            //             // 跳转到地图
            //             showTaskLocationOnMap(task);
            //         });
            //     });
            //     return;
            // }
            // // ===================== 开发模式开关 END =====================

            // 正常的任务验证逻辑
            // 检查用户是否在任务位置
            checkIfUserInTaskLocation(task, isInLocation -> {
                if (isInLocation) {
                    // 用户在位置内，启动计时器
                    startTimerForTask(task);
                    // 导航到地图页面
                    showTaskLocationOnMap(task);
                } else {
                    // 用户不在位置内，显示提示
                    Toast.makeText(this, "请先前往任务指定地点：" + task.location, Toast.LENGTH_LONG).show();
                    // 显示导航选项对话框
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("位置提示");
                    builder.setMessage("您需要前往 " + task.location + " 才能开始任务计时。是否现在导航到该位置？");
                    builder.setPositiveButton("导航", (dialog, which) -> {
                        navigateToMapWithLocation(task);
                    });
                    builder.setNegativeButton("稍后", (dialog, which) -> {
                        dialog.dismiss();
                    });
                    builder.create().show();
                }
            });
        } else if ("time+geofence".equals(task.verificationMethod)) {
            // // ===================== 开发模式开关 START =====================
            // // ... (保留开发模式代码的注释)
            // // ===================== 开发模式开关 END =====================

            // --- Start: Copied logic from checkLocationBeforeCapture --- 
            // 1. 获取当前位置的 customId
            String currentCumId = getSharedPreferences("location_prefs", MODE_PRIVATE)
                    .getString("current_cum_id", "");
            Log.d("GeoFenceCheck", "[verifyAgentTask - Copied Logic] 读取到的 current_cum_id: " + currentCumId);

            // 2. 从任务位置提取要求的教学楼 ID (使用 'task' 变量)
            String taskLocation = task.location.toUpperCase();
            String requiredLocationId = "";
            String[] allBuildingCodes = {"SB", "CB", "SD", "FB", "SC", "SA", "PB", "MA", "MB", "EB", "EE"};
            for (String code : allBuildingCodes) {
                if (taskLocation.contains(code + "楼") || taskLocation.contains(code + " ") || taskLocation.endsWith(code)) {
                    requiredLocationId = code;
                    break;
                }
            }
            if (requiredLocationId.isEmpty()){
                 for (String code : allBuildingCodes) {
                     if (taskLocation.contains(code)) {
                         requiredLocationId = code;
                         break;
                     }
                 }
            }
            Log.d("GeoFenceCheck", "[verifyAgentTask - Copied Logic] 任务要求位置ID: " + requiredLocationId);

            // 3. 比较 ID
            if (!requiredLocationId.isEmpty() && requiredLocationId.equals(currentCumId)) {
                // 位置匹配成功: 用户在正确的教学楼内
                Toast.makeText(this, "位置确认 ("+ requiredLocationId +")，开始任务计时", Toast.LENGTH_SHORT).show();
                startTimerForTask(task);
                showTaskLocationOnMap(task);
            } else {
                // 位置不匹配或无法获取当前位置
                String message;
                if (currentCumId.isEmpty()) {
                    message = "无法确认当前位置，请确保您已进入教学楼区域并等待片刻。任务地点：" + task.location + " (" + requiredLocationId + ")";
                } else {
                    message = "您当前在 " + currentCumId + " 楼，不在任务要求的教学楼 (" + requiredLocationId + ")，请前往: " + task.location;
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                // 显示导航选项对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("位置不符");
                builder.setMessage("您需要前往 " + task.location + " (" + requiredLocationId + ") 才能开始任务计时。是否现在导航到该位置？");
                builder.setPositiveButton("导航", (dialog, which) -> {
                    navigateToMapWithLocation(task); // 使用 'task' 变量
                });
                builder.setNegativeButton("稍后", (dialog, which) -> {
                    dialog.dismiss();
                });
                builder.create().show();
            }
             // --- End: Copied logic --- 
        } else {
            // 其他验证方式
            Toast.makeText(this, "未知验证方式: " + task.verificationMethod, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查用户是否在任务指定位置
     * @param task 当前任务
     * @param callback 位置检查回调
     */
    private void checkIfUserInTaskLocation(TaskData task, OnLocationCheckCallback callback) {
        // 获取任务位置信息
        if (task.latitude == null || task.longitude == null) {
            // 任务没有位置信息
            callback.onLocationChecked(false);
            return;
        }

        // 获取用户当前位置
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 没有位置权限
            Toast.makeText(this, "需要位置权限来验证任务", Toast.LENGTH_SHORT).show();
            callback.onLocationChecked(false);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        // 计算用户位置与任务位置的距离
                        float[] results = new float[1];
                        Location.distanceBetween(
                                location.getLatitude(), location.getLongitude(),
                                task.latitude, task.longitude, results);
                        float distanceInMeters = results[0];

                        // 检查距离是否在任务半径范围内
                        int radius = task.radius != null ? task.radius : 50; // 默认50米
                        boolean isInLocation = distanceInMeters <= radius;

                        // 通过回调返回结果
                        callback.onLocationChecked(isInLocation);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {
                        callback.onLocationChecked(false);
                    }
                }, null);
    }

    /**
     * 位置检查回调接口
     */
    interface OnLocationCheckCallback {
        void onLocationChecked(boolean isInLocation);
    }

    /**
     * 启动任务计时器
     * @param task 需要计时的任务
     */
    private void startTimerForTask(TaskData task) {
        // 保存当前任务
        currentTimerTask = task;

        // 设置UI显示
        tvTimerTaskTitle.setText(task.title);
        tvTimerTaskLocation.setText(task.location);

        // 计算总时间
        taskTotalTimeInMillis = task.durationMinutes * 60 * 1000;
        taskTimeLeftInMillis = taskTotalTimeInMillis;

        // 更新UI
        updateTaskTimerUI();

        // 显示计时器卡片
        taskTimerCardView.setVisibility(View.VISIBLE);

        // 添加一条系统消息
        ChatMessage timerStartMessage = new ChatMessage();
        timerStartMessage.userId = userId;
        timerStartMessage.role = "system";
        timerStartMessage.content = "开始执行任务: " + task.title + " - 需要在" + task.location + "停留" + task.durationMinutes + "分钟";
        timerStartMessage.messageType = "system";
        timerStartMessage.timestamp = System.currentTimeMillis();

        chatAdapter.addMessage(timerStartMessage);
        saveChatMessageToDatabase(timerStartMessage);

        // 添加滚动到最新消息
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        // 开始位置检查
        startTaskLocationCheck();

        // 创建并开始倒计时
        startTaskCountdown();
    }

    private void startTaskCountdown() {
        taskCountDownTimer = new CountDownTimer(taskTimeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                taskTimeLeftInMillis = millisUntilFinished;
                updateTaskTimerUI();
            }

            @Override
            public void onFinish() {
                completeTimerTask();
            }
        }.start();

        isTaskTimerRunning = true;
        tvTimerStatus.setText("任务进行中 - 请保持在指定区域内");
        tvTimerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
    }

    private void updateTaskTimerUI() {
        int minutes = (int) (taskTimeLeftInMillis / 1000) / 60;
        int seconds = (int) (taskTimeLeftInMillis / 1000) % 60;

        String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimerCountdown.setText(timeFormatted);

        // 更新进度条
        int progress = (int) (taskTimeLeftInMillis * 100 / taskTotalTimeInMillis);
        progressTimer.setProgress(progress);
    }

    private void startTaskLocationCheck() {
        // 位置检查器
        taskLocationChecker = new Runnable() {
            @Override
            public void run() {
                checkIfUserInTaskLocation(currentTimerTask, isInLocation -> {
                    if (!isInLocation && isTaskTimerRunning) {
                        // 用户离开了指定区域，暂停计时器
                        pauseTaskTimer();
                    } else if (isInLocation && !isTaskTimerRunning && taskCountDownTimer != null) {
                        // 用户回到了指定区域，恢复计时器
                        resumeTaskTimer();
                    }

                    // 继续检查位置
                    taskLocationHandler.postDelayed(this, TASK_LOCATION_CHECK_INTERVAL);
                });
            }
        };

        // 开始周期性检查位置
        taskLocationHandler.post(taskLocationChecker);
    }

    private void pauseTaskTimer() {
        if (taskCountDownTimer != null) {
            taskCountDownTimer.cancel();
        }
        isTaskTimerRunning = false;
        tvTimerStatus.setText("已暂停 - 您已离开指定区域");
        tvTimerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));

        // 添加一条系统消息
        ChatMessage pauseMessage = new ChatMessage();
        pauseMessage.userId = userId;
        pauseMessage.role = "system";
        pauseMessage.content = "您已离开任务区域，计时已暂停";
        pauseMessage.messageType = "system";
        pauseMessage.timestamp = System.currentTimeMillis();

        chatAdapter.addMessage(pauseMessage);
        saveChatMessageToDatabase(pauseMessage);

        // 滚动到最新消息
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void resumeTaskTimer() {
        // 重新创建倒计时器
        taskCountDownTimer = new CountDownTimer(taskTimeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                taskTimeLeftInMillis = millisUntilFinished;
                updateTaskTimerUI();
            }

            @Override
            public void onFinish() {
                completeTimerTask();
            }
        }.start();

        isTaskTimerRunning = true;
        tvTimerStatus.setText("任务进行中 - 请保持在指定区域内");
        tvTimerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));

        // 添加一条系统消息
        ChatMessage resumeMessage = new ChatMessage();
        resumeMessage.userId = userId;
        resumeMessage.role = "system";
        resumeMessage.content = "您已返回任务区域，计时已恢复";
        resumeMessage.messageType = "system";
        resumeMessage.timestamp = System.currentTimeMillis();

        chatAdapter.addMessage(resumeMessage);
        saveChatMessageToDatabase(resumeMessage);

        // 滚动到最新消息
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void completeTimerTask() {
        // 停止位置检查
        taskLocationHandler.removeCallbacks(taskLocationChecker);

        // 更新UI
        isTaskTimerRunning = false;
        tvTimerStatus.setText("任务完成！");
        tvTimerStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        btnTimerCancel.setVisibility(View.GONE);

        // 添加一条系统消息
        ChatMessage completeMessage = new ChatMessage();
        completeMessage.userId = userId;
        completeMessage.role = "system";
        completeMessage.content = "恭喜！任务\"" + currentTimerTask.title + "\"已完成";
        completeMessage.messageType = "system";
        completeMessage.timestamp = System.currentTimeMillis();

        chatAdapter.addMessage(completeMessage);
        saveChatMessageToDatabase(completeMessage);

        // 滚动到最新消息
        chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        // 标记任务为已完成
        markAgentTaskAsCompleted(currentTimerTask.id);

        // 一段时间后隐藏计时器卡片
        new Handler().postDelayed(() -> {
            taskTimerCardView.setVisibility(View.GONE);
        }, 3000);

        // 使用TaskVerificationManager保存验证结果
        saveTaskVerificationResult();
    }

    private void saveTaskVerificationResult() {
        // 创建验证数据对象
        TaskVerificationData verificationData = new TaskVerificationData();
        verificationData.taskId = currentTimerTask.id;
        verificationData.userId = userId;
        verificationData.verificationType = "time+geofence";
        verificationData.verificationData = "在" + currentTimerTask.location + "停留了" + currentTimerTask.durationMinutes + "分钟";
        verificationData.verificationResult = true; // 修改为布尔类型值
        verificationData.confidence = 100; // 确定性为100%
        verificationData.verificationStatus = "completed";
        verificationData.timestamp = System.currentTimeMillis();

        // 保存到数据库
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.taskVerificationDao().insertVerification(verificationData); // 修正方法名

            // 更新任务状态
            TaskData task = db.taskDao().getTaskById(currentTimerTask.id);
            if (task != null) {
                task.isCompleted = true;
                db.taskDao().updateTask(task);
            }
        });
    }

    /**
     * 显示任务位置地图
     * @param task 需要显示位置的任务
     */
    private void showTaskLocationOnMap(TaskData task) {
        // 启动地图活动，显示任务位置
        Intent mapIntent = new Intent(this, CrucialMapViewImplement.class);
        
        // 额外的任务数据，帮助地图活动显示任务信息
        mapIntent.putExtra("task_id", task.id);
        mapIntent.putExtra("task_title", task.title);
        mapIntent.putExtra("task_location", task.location);
        mapIntent.putExtra("task_time", task.startTime + " (" + task.durationMinutes + "分钟)");
        mapIntent.putExtra("task_description", task.description);
        mapIntent.putExtra("latitude", task.latitude);
        mapIntent.putExtra("longitude", task.longitude);
        
        // 设置任务类型为特工任务
        mapIntent.putExtra(EXTRA_TASK_TYPE, TASK_TYPE_AGENT);
        Log.d("TaskType", "显示特工任务位置: " + task.title + ", ID: " + task.id);
        
        // 构建任务详情文本，用于标记点弹窗
        String markerContent = task.title + "\n" +
                "地点：" + task.location + "\n" +
                (task.startTime != null ? "时间：" + task.startTime + "\n" : "") +
                "描述：" + task.description + "\n" +
                "时长：" + task.durationMinutes + "分钟";
        
        mapIntent.putExtra("marker_content", markerContent);

        // 添加拍照验证相关信息
        if ("photo".equals(task.verificationMethod)) {
            mapIntent.putExtra("verify_photo", true);
            mapIntent.putExtra("position_id", task.positionID);
        }
        
        startActivity(mapIntent);
    }

    private void cancelTaskTimer() {
        // 显示确认对话框
        new AlertDialog.Builder(this)
                .setTitle("取消任务")
                .setMessage("确定要取消任务计时吗？这将中断当前任务验证。")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 停止计时器
                    if (taskCountDownTimer != null) {
                        taskCountDownTimer.cancel();
                    }

                    // 停止位置检查
                    taskLocationHandler.removeCallbacks(taskLocationChecker);

                    // 隐藏计时器卡片
                    taskTimerCardView.setVisibility(View.GONE);

                    // 添加一条系统消息
                    ChatMessage cancelMessage = new ChatMessage();
                    cancelMessage.userId = userId;
                    cancelMessage.role = "system";
                    cancelMessage.content = "任务\"" + currentTimerTask.title + "\"已取消";
                    cancelMessage.messageType = "system";
                    cancelMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(cancelMessage);
                    saveChatMessageToDatabase(cancelMessage);

                    // 滚动到最新消息
                    chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 注销广播接收器
        if (taskReceiver != null) {
            unregisterReceiver(taskReceiver);
            taskReceiver = null;
        }

        // 清理动画资源
        if (animationHandler != null) {
            animationHandler.removeCallbacks(dotAnimation);
        }

        // 清理计时器和位置检查
        if (taskCountDownTimer != null) {
            taskCountDownTimer.cancel();
        }
        if (taskLocationHandler != null && taskLocationChecker != null) {
            taskLocationHandler.removeCallbacks(taskLocationChecker);
        }

        // 隐藏工具视图
        FloatWindowManager.get().visibleToolView();

        // 注销地理围栏广播接收器
        if (geoFenceReceiver != null) {
            unregisterReceiver(geoFenceReceiver);
        }

        // 注销位置广播接收器
        if (locationReceiver != null) {
            unregisterReceiver(locationReceiver);
            locationReceiver = null;
        }
    }
    /**
     * 标记特工任务为已完成
     */
    private void markAgentTaskAsCompleted(int taskId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Get a fresh instance inside the background thread using application context
            AppDatabase database = AppDatabase.getInstance(getApplicationContext()); 
            if (database == null) {
                 Log.e("ActivitySelection", "Database instance is null in background thread!");
                 return; // Cannot proceed without database
            }
            // Get task
            // TaskData task = db.taskDao().getTaskById(taskId); // Use the instance obtained within the lambda
            TaskData task = database.taskDao().getTaskById(taskId); 
            if (task != null) {
                // Update task status
                task.isCompleted = true;
                // db.taskDao().updateTask(task); // Use the instance obtained within the lambda
                database.taskDao().updateTask(task); 

                // 在UI线程添加完成消息到聊天
                runOnUiThread(() -> {
                    // 添加系统消息到聊天
                    ChatMessage systemMessage = new ChatMessage();
                    systemMessage.userId = userId;
                    systemMessage.role = "system";
                    systemMessage.content = "任务已完成: " + task.title;
                    systemMessage.messageType = "system";
                    systemMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(systemMessage);
                    saveChatMessageToDatabase(systemMessage);

                    // 添加特工Zero的表扬消息
                    ChatMessage agentMessage = new ChatMessage();
                    agentMessage.userId = userId;
                    agentMessage.role = "assistant";
                    agentMessage.content = "做得好，夜鸦！你成功完成了密令，守护了霍格沃兹。" + generateRandomCompliment();
                    agentMessage.senderName = "Zero";
                    agentMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(agentMessage);
                    saveChatMessageToDatabase(agentMessage);

                    // 如果是最后一个任务，提示进入下一阶段
                    if (isLastTaskInStage(task)) {
                        ChatMessage nextStageMessage = new ChatMessage();
                        nextStageMessage.userId = userId;
                        nextStageMessage.role = "assistant";
                        nextStageMessage.content = "所有阶段任务已完成。准备好接受下一阶段的挑战了吗？";
                        nextStageMessage.senderName = "信使Zero";
                        nextStageMessage.timestamp = System.currentTimeMillis();

                        chatAdapter.addMessage(nextStageMessage);
                        saveChatMessageToDatabase(nextStageMessage);

                        // 添加进入下一阶段的特殊按钮消息
                        ChatMessage buttonMessage = new ChatMessage();
                        buttonMessage.userId = userId;
                        buttonMessage.role = "system";
                        buttonMessage.content = "";
                        buttonMessage.messageType = "next_stage_button";
                        buttonMessage.timestamp = System.currentTimeMillis();

                        chatAdapter.addMessage(buttonMessage);
                        saveChatMessageToDatabase(buttonMessage);
                    }

                    // 滚动到底部
                    scrollChatToBottom();
                });
            }
        });
    }

    /**
     * 判断是否是当前阶段的最后一个任务
     */
    private boolean isLastTaskInStage(TaskData task) {
        // 这里简化为使用一个任务代表一个阶段
        // 实际应用中可能需要根据任务ID或其他标识判断
        return true;
    }

    /**
     * 生成随机的表扬语
     */
    private String generateRandomCompliment() {
        String[] compliments = {
                "你的响应比凤凰羽毛还迅捷！",
                "凤凰社将你的名字刻在了守秘银瓶上。",
                "你的无声咒和伪装术连阿拉戈克的眼睛都能骗过，这次行动完美无缺",
                "你的技能令人印象深刻。",
                "任务完成得干净利落。"
        };

        int index = (int) (Math.random() * compliments.length);
        return compliments[index];
    }

    /**
     * 进入下一阶段特工任务（带参数版本）
     */
    public void moveToNextAgentStage(String userId, String characterId) {
        // 简单调用无参数版本，保持逻辑一致
        moveToNextAgentStage();
    }

    /**
     * 启动特工Zero任务
     */
    private void startAgentZeroMission() {
        // 初始化特工任务状态
        currentAgentStage = 1;

        // 检查特工角色是否存在，不存在则创建
        checkAndCreateAgentCharacter();

        // 创建欢迎消息
        ChatMessage welcomeMessage = new ChatMessage();
        welcomeMessage.userId = userId;
        welcomeMessage.role = "assistant";
        welcomeMessage.content = "你好，夜鸦。这里是凤凰社信使Zero。很高兴你加入了我们的秘密行动。\n\n" +
                "情报显示，乌姆里奇和黑魔头手下的黑雾组织正在霍格沃茨散布虚假信息，企图影响学生的精神。\n\n" +
                "你的任务是协助我们收集证据并阻止他们的行动。我会通过双面镜为你分配具体任务。\n\n" +
                "请保持警惕，所有通讯都已施加保护咒语。";
        welcomeMessage.senderName = "凤凰社信使Zero";
        welcomeMessage.timestamp = System.currentTimeMillis();

        // 添加消息
        chatAdapter.addMessage(welcomeMessage);
        saveChatMessageToDatabase(welcomeMessage);

        // 生成第一个任务
        new Handler().postDelayed(() -> {
            generateNewAgentTask();
        }, 1500);
    }

    // 修改sendMessageToLLM方法，添加对特工Zero的识别
    private void sendMessageToLLM(String message) {
        // 显示用户消息
        ChatMessage userMessage = new ChatMessage();
        userMessage.userId = userId;
        userMessage.role = "user";
        userMessage.content = message;
        userMessage.timestamp = System.currentTimeMillis();

        chatAdapter.addMessage(userMessage);

        // 保存用户消息到数据库
        saveChatMessageToDatabase(userMessage);

        // 清空输入框
        messageInput.setText("");

        // 检查消息是否是针对特工Zero的指令
        if (message.toLowerCase().contains("zero") ||
                message.toLowerCase().contains("agent") ||
                message.toLowerCase().contains("task")) {

            // 如果还没有开始特工任务，则启动
            boolean hasStartedMission = getSharedPreferences("agent_prefs", MODE_PRIVATE)
                    .getBoolean("mission_started", false);

            if (!hasStartedMission) {
                // 标记任务已启动
                getSharedPreferences("agent_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("mission_started", true)
                        .apply();

                // 启动特工任务
                startAgentZeroMission();
                return;
            } else {
                // 如果已经启动过任务，先检查任务状态
                checkTaskStatusAndRespond(message);
                return;
            }
        }

        // 显示打字指示器
        showTypingIndicator();

        // 使用LLM生成回复
        llmManager.sendMessage(userId, message, null, new LLMManager.LLMResponseCallback() {
            @Override
            public void onResponse(String response) {
                // 隐藏打字指示器
                hideTypingIndicator();

                // 检查响应中是否包含任务建议
                TaskData suggestedTask = parseLLMResponseForTaskSuggestion(response);

                if (suggestedTask != null) {
                    // 清理响应文本，移除JSON部分
                    String cleanedResponse = removeJsonFromResponse(response);

                    // 添加LLM回复（不包含JSON部分）
                    ChatMessage responseMessage = new ChatMessage();
                    responseMessage.userId = userId;
                    responseMessage.role = "assistant";
                    responseMessage.content = cleanedResponse;
                    responseMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(responseMessage);
                    saveChatMessageToDatabase(responseMessage);

                    // 保存任务到数据库并添加任务卡片
                    saveTaskToDatabase(suggestedTask);
                } else {
                    // 添加普通LLM回复
                    ChatMessage responseMessage = new ChatMessage();
                    responseMessage.userId = userId;
                    responseMessage.role = "assistant";
                    responseMessage.content = response;
                    responseMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(responseMessage);
                    saveChatMessageToDatabase(responseMessage);
                }

                // 滚动到底部
                scrollChatToBottom();
            }

            @Override
            public void onError(Exception e) {
                // 隐藏打字指示器
                hideTypingIndicator();

                // 显示错误消息
                ChatMessage errorMessage = new ChatMessage();
                errorMessage.userId = userId;
                errorMessage.role = "system";
                errorMessage.content = "Sorry, error generating response: " + e.getMessage();
                errorMessage.timestamp = System.currentTimeMillis();

                chatAdapter.addMessage(errorMessage);
                saveChatMessageToDatabase(errorMessage);

                // 滚动到底部
                scrollChatToBottom();
            }
        });
    }

    /**
     * 检查任务状态并响应用户消息
     */
    private void checkTaskStatusAndRespond(String message) {
        // 显示打字指示器
        showTypingIndicator();

        // 在后台线程检查任务状态
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 获取当前未完成的任务
                List<TaskData> uncompletedTasks = db.taskDao().getIncompleteTasksByUserId(userId);
                boolean hasUncompletedTask = uncompletedTasks != null && !uncompletedTasks.isEmpty();

                // 在UI线程响应
                runOnUiThread(() -> {
                    // 隐藏打字指示器
                    hideTypingIndicator();

                    // 添加Zero的回复
                    ChatMessage responseMessage = new ChatMessage();
                    responseMessage.userId = userId;
                    responseMessage.role = "assistant";
                    responseMessage.senderName = "信使Zero";
                    responseMessage.timestamp = System.currentTimeMillis();

                    // 根据任务状态和用户消息生成响应
                    if (hasUncompletedTask) {
                        TaskData currentTask = uncompletedTasks.get(0);

                        // 如果用户请求新任务但有未完成任务
                        if (message.toLowerCase().contains("新") &&
                                message.toLowerCase().contains("任务")) {
                            responseMessage.content = "信使夜枭，你有一个正在进行的任务尚未完成。请先完成当前任务：" +
                                    currentTask.title + "，位于" + currentTask.location + "。";
                        }
                        // 如果用户询问该做什么
                        else if (message.toLowerCase().contains("做什么") ||
                                message.toLowerCase().contains("任务") ||
                                message.toLowerCase().contains("下一步")) {
                            StringBuilder response = new StringBuilder();
                            response.append("信使夜枭，你的当前任务尚未完成：\n\n");
                            response.append("代号：").append(currentTask.title).append("\n");
                            response.append("简报：").append(currentTask.description).append("\n");

                            if (currentTask.location != null && !currentTask.location.isEmpty()) {
                                response.append("目标地点：").append(currentTask.location).append("\n");
                            }

                            response.append("\n请优先完成这项任务。任务完成后通过验证系统上传证据。");
                            responseMessage.content = response.toString();
                        }
                        // 其他情况
                        else {
                            responseMessage.content = generateZeroResponse(message);
                        }
                    } else {
                        // 没有未完成任务，可以生成新任务
                        if (message.toLowerCase().contains("新") &&
                                message.toLowerCase().contains("任务")) {
                            responseMessage.content = "确认请求，信使夜枭。正在准备新的密令...";

                            // 添加消息
                            chatAdapter.addMessage(responseMessage);
                            saveChatMessageToDatabase(responseMessage);

                            // 生成新任务
                            generateNewAgentTask();
                            return;
                        } else {
                            responseMessage.content = generateZeroResponse(message);
                        }
                    }

                    // 添加消息
                    chatAdapter.addMessage(responseMessage);
                    saveChatMessageToDatabase(responseMessage);

                    // 滚动到底部
                    scrollChatToBottom();
                });
            } catch (Exception e) {
                Log.e("ActivitySelection", "检查任务状态失败: " + e.getMessage(), e);

                // 在UI线程显示错误
                runOnUiThread(() -> {
                    hideTypingIndicator();

                    ChatMessage errorMessage = new ChatMessage();
                    errorMessage.userId = userId;
                    errorMessage.role = "assistant";
                    errorMessage.content = "系统检测到任务状态异常，请刷新页面或联系管理员";
                    errorMessage.senderName = "信使Zero";
                    errorMessage.timestamp = System.currentTimeMillis();

                    chatAdapter.addMessage(errorMessage);
                    saveChatMessageToDatabase(errorMessage);

                    scrollChatToBottom();
                });
            }
        });
    }

    /**
     * 生成Zero的响应
     */
    private String generateZeroResponse(String userMessage) {
        // 首先获取当前任务状态
        TaskData currentTask = null;

        try {
            // 尝试从数据库获取当前未完成的任务
            List<TaskData> uncompletedTasks = db.taskDao().getIncompleteTasksByUserId(userId);
            if (uncompletedTasks != null && !uncompletedTasks.isEmpty()) {
                // 获取第一个未完成的任务
                currentTask = uncompletedTasks.get(0);
            }
        } catch (Exception e) {
            Log.e("ActivitySelection", "获取任务状态失败: " + e.getMessage());
            return "双面镜受到干扰，请稍后重试或寻求其他凤凰社成员帮助";
        }

        // 用户询问该做什么或询问任务相关问题
        if (userMessage.toLowerCase().contains("做什么") ||
                userMessage.toLowerCase().contains("任务") ||
                userMessage.toLowerCase().contains("下一步") ||
                userMessage.toLowerCase().contains("指示")) {

            if (currentTask != null && !currentTask.isCompleted) {
                StringBuilder response = new StringBuilder();
                response.append("夜鸦，你的当前任务尚未完成：\n\n");
                response.append("代号：").append(currentTask.getTitle()).append("\n");
                response.append("简报：").append(currentTask.getDescription()).append("\n");

                if (currentTask.getLocation() != null && !currentTask.getLocation().isEmpty()) {
                    response.append("目标地点：").append(currentTask.getLocation()).append("\n");
                }

                response.append("\n请优先完成这项任务。任务完成后通过双面镜上传证据。");
                return response.toString();
            } else {
                return "目前没有进行中的任务。需要我为你分配新的任务吗？";
            }
        }

        // 用户表示要新任务
        if (userMessage.toLowerCase().contains("新") &&
                userMessage.toLowerCase().contains("任务")) {

            // 检查是否有未完成的任务
            if (currentTask != null && !currentTask.isCompleted) {
                return "夜鸦，你有一个正在进行的任务尚未完成。请先完成当前任务：" +
                        currentTask.getTitle() + "，在" + currentTask.getLocation() + "。";
            } else {
                return "收到请求，夜鸦。正在通过双面镜传送新的任务指示，请保持警惕...";
            }
        }

        // 用户表示任务完成
        if (userMessage.toLowerCase().contains("完成")) {
            if (currentTask != null) {
                return "任务验证需要使用正确的协议咒语。请使用双面镜中指定的内容进行验证。";
            } else {
                return "目前没有待验证的任务。需要新的任务指示吗？";
            }
        }

        // 用户请求帮助
        if (userMessage.toLowerCase().contains("帮助") || userMessage.toLowerCase().contains("怎么")) {
            return "魔法使用指南：点击任务卡片开始行动，完成后使用指定咒语（拍照或定位）进行验证。需要新的指示，直接询问即可。记住，保持警惕！";
        }

        // 用户询问身份
        if (userMessage.toLowerCase().contains("你是谁") || userMessage.toLowerCase().contains("身份")) {
            return "我是凤凰社的守护者，负责协调这次针对乌姆里奇和黑雾组织的行动。你是我们招募的特工夜鸦，我们的通讯已经施加了保护咒语。请勿在公共场合暴露身份。";
        }

        // 用户询问组织
        if (userMessage.toLowerCase().contains("乌姆里奇") || userMessage.toLowerCase().contains("黑雾")) {
            return "乌姆里奇正在与黑雾组织合作，他们计划在霍格沃茨散布虚假信息和黑魔法。我们已经掌握了部分证据，但需要你的实地调查来获取更多信息。";
        }

        // 默认响应，更符合魔法世界风格
        String[] responses;

        if (currentTask != null && !currentTask.isCompleted) {
            // 有未完成任务时的提醒响应
            responses = new String[] {
                    "双面镜通讯正常，夜鸦。请继续执行当前任务 '" + currentTask.getTitle() + "'，保持隐蔽。",
                    "提醒：你的当前任务尚未完成。目标位置：" + currentTask.getLocation() + "。",
                    "注意，夜鸦，优先完成当前分配的任务。",
                    "魔法练习进展如何？需要关于当前任务的更多指导吗？",
                    "请专注于当前任务。乌姆里奇的监视越来越严密了。",
                    "记住任务目标。完成后立即通过双面镜报告。"
            };
        } else {
            // 没有任务或任务已完成时的一般响应
            responses = new String[] {
                    "双面镜通讯正常，夜鸦。有新的情报要分享吗？",
                    "保护咒语已确认，可以安全交流。需要新的任务指示吗？",
                    "凤凰社随时待命，准备提供支援。",
                    "这条通讯已经施加了静音咒，暂时不会被发现。",
                    "记住你的伪装身份，夜鸦。在公共场合时，你只是一名普通学生。",
                    "正在检查活点地图...请等待进一步指示。",
                    "确认你的最新位置，需要支援咒语吗？",
                    "保持警惕，夜鸦。随时注意乌姆里奇的探子。"
            };
        }

        int index = (int) (Math.random() * responses.length);
        return responses[index];
    }

    /**
     * 加载并显示特工任务
     */
    private void loadAndDisplayAgentTask(int taskId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 从数据库加载任务
            TaskData task = db.taskDao().getTaskById(taskId);

            runOnUiThread(() -> {
                if (task != null) {
                    // 创建特工Zero的消息
                    ChatMessage agentMessage = new ChatMessage();
                    agentMessage.userId = userId;
                    agentMessage.role = "assistant";

                    // 构建包含详细任务信息的消息
                    StringBuilder messageContent = new StringBuilder();
                    messageContent.append("夜枭，我有一项新的机密任务给你。\n\n");
                    messageContent.append("任务代号：").append(task.title).append("\n");
                    messageContent.append("任务简报：").append(task.description).append("\n");
                    messageContent.append("目标地点：").append(task.location).append("\n");
                    messageContent.append("预计时长：").append(task.durationMinutes).append("分钟\n\n");
                    messageContent.append("任务已加密并存储到记忆库。请查看任务卡片获取详细信息。");

                    agentMessage.content = messageContent.toString();
                    agentMessage.senderName = "信使Zero";
                    agentMessage.timestamp = System.currentTimeMillis();

                    // 添加消息
                    chatAdapter.addMessage(agentMessage);
                    saveChatMessageToDatabase(agentMessage);

                    // 添加任务卡片到聊天
                    addAgentTaskCardToChat(task);

                    // 滚动到底部
                    scrollChatToBottom();
                } else {
                    Toast.makeText(ActivitySelection.this, "Failed to load task details", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * 添加特工任务卡片到聊天
     */
    private void addAgentTaskCardToChat(TaskData task) {
        // 创建任务卡片消息
        ChatMessage taskCardMessage = new ChatMessage();
        taskCardMessage.userId = userId;
        taskCardMessage.role = "system";
        taskCardMessage.content = "";
        taskCardMessage.messageType = "agent_task_card";
        taskCardMessage.relatedTaskId = task.id;
        taskCardMessage.timestamp = System.currentTimeMillis();

        // 添加到适配器
        chatAdapter.addMessage(taskCardMessage);

        // 保存到数据库
        saveChatMessageToDatabase(taskCardMessage);
    }

    /**
     * 进入下一阶段特工任务（无参数版本）
     */
    public void moveToNextAgentStage() {
        currentAgentStage++;
        if (currentAgentStage > 3) {
            // 所有阶段已完成
            ChatMessage completionMessage = new ChatMessage();
            completionMessage.userId = userId;
            completionMessage.role = "assistant";
            completionMessage.content = "Congratulations, agent! You have successfully completed all tasks. The Grey Zone organization's plot has been foiled, and campus security has returned to normal. Agent Zero expresses gratitude on behalf of the organization.";
            completionMessage.senderName = "Agent Zero";
            completionMessage.timestamp = System.currentTimeMillis();

            chatAdapter.addMessage(completionMessage);
            saveChatMessageToDatabase(completionMessage);

            // 重置阶段
            currentAgentStage = 1;
            return;
        }

        // 生成下一阶段任务
        generateNewAgentTask();
    }

    /**
     * 生成新的特工任务（带参数版本）
     */
    private void generateNewAgentTask(String userId, String characterId, int stage) {
        // 显示加载提示
        Toast.makeText(this, "Generating task...", Toast.LENGTH_SHORT).show();

        // 显示打字指示器
        showTypingIndicator();

        // 在后台线程生成任务
        new Thread(() -> {
            TaskGenerator taskGen = new TaskGenerator(this);
            int taskId = taskGen.generateAgentTask(userId, characterId, stage);

            runOnUiThread(() -> {
                hideTypingIndicator();

                if (taskId != -1) {
                    // 任务生成成功，获取任务详情并加入聊天
                    loadAndDisplayAgentTask(taskId);
                } else {
                    // 任务生成失败
                    Toast.makeText(this, "Task generation failed, please try again", Toast.LENGTH_SHORT).show();

                    // 添加错误消息到聊天
                    ChatMessage errorMessage = new ChatMessage();
                    errorMessage.userId = userId;
                    errorMessage.role = "system";
                    errorMessage.content = "Due to communication interference, unable to receive Agent Zero's new task. Please try again later.";
                    errorMessage.timestamp = System.currentTimeMillis();
                    chatAdapter.addMessage(errorMessage);
                    saveChatMessageToDatabase(errorMessage);
                }
            });
        }).start();
    }

    private void setupTaskTimerViews() {
        // 加载计时器卡片布局并初始化为隐藏状态
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        taskTimerCardView = inflater.inflate(R.layout.layout_task_timer_card, null);
        taskTimerCardView.setVisibility(View.GONE);

        // 找到父容器并添加计时器卡片
        LinearLayout chatContainer = findViewById(R.id.activityList);
        chatContainer.addView(taskTimerCardView, 0); // 添加到顶部

        // 初始化计时器卡片中的控件
        tvTimerTaskTitle = taskTimerCardView.findViewById(R.id.tv_timer_task_title);
        tvTimerTaskLocation = taskTimerCardView.findViewById(R.id.tv_timer_task_location);
        tvTimerCountdown = taskTimerCardView.findViewById(R.id.tv_timer_countdown);
        tvTimerStatus = taskTimerCardView.findViewById(R.id.tv_timer_status);
        progressTimer = taskTimerCardView.findViewById(R.id.progress_timer);
        btnTimerCancel = taskTimerCardView.findViewById(R.id.btn_timer_cancel);

        // 设置取消按钮点击事件
        btnTimerCancel.setOnClickListener(v -> cancelTaskTimer());
    }

    private void registerLocationReceiver() {
        locationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (GeoFenceManager.GEOFENCE_BROADCAST_ACTION.equals(intent.getAction())) {
                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        int status = bundle.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS);
                        String customId = bundle.getString(GeoFence.BUNDLE_KEY_CUSTOMID);
                        
                        // 保存当前位置ID到SharedPreferences
                        if (status == GeoFence.STATUS_IN || status == GeoFence.STATUS_STAYED) {
                            SharedPreferences.Editor editor = getSharedPreferences("location_prefs", MODE_PRIVATE).edit();
                            editor.putString("current_cum_id", customId);
                            editor.apply();
                            Log.d("LocationReceiver", "更新当前位置ID: " + customId);
                        } else if (status == GeoFence.STATUS_OUT) {
                            // 当用户离开围栏时，清除当前位置ID
                            SharedPreferences.Editor editor = getSharedPreferences("location_prefs", MODE_PRIVATE).edit();
                            editor.remove("current_cum_id");
                            editor.apply();
                            Log.d("LocationReceiver", "用户离开围栏，清除位置ID");
                        }
                    }
                }
            }
        };

        // 注册广播接收器
        IntentFilter filter = new IntentFilter(GeoFenceManager.GEOFENCE_BROADCAST_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(locationReceiver, filter, RECEIVER_NOT_EXPORTED);
        }
        Log.d("LocationReceiver", "位置广播接收器已注册");
    }

    // --- 4. Create addCompletionMessagesToChat --- 
    private void addCompletionMessagesToChat(int taskId) {
        AppDatabase database = AppDatabase.getInstance(getApplicationContext());
        if (database == null) { 
            Log.e("ActivitySelection", "[addCompletionMessagesToChat] Database instance is null!");
            return; 
        }

        TaskData task = database.taskDao().getTaskById(taskId); 
        if (task == null) {
             Log.e("ActivitySelection", "[addCompletionMessagesToChat] Cannot find task with ID " + taskId);
             return;
        }

        Log.d("ActivitySelection", "[addCompletionMessagesToChat] Adding messages for taskId: " + taskId);

        // Add system message
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.userId = userId;
        systemMessage.role = "system";
        systemMessage.content = "任务已完成: " + task.title;
        systemMessage.messageType = "system";
        systemMessage.timestamp = System.currentTimeMillis();
        chatAdapter.addMessage(systemMessage);
        saveChatMessageToDatabase(systemMessage);

        // Add agent message
        ChatMessage agentMessage = new ChatMessage();
        agentMessage.userId = userId;
        agentMessage.role = "assistant";
        agentMessage.content = "做得好，夜鸦！你成功完成了密令，守护了霍格沃兹。" + generateRandomCompliment();
        agentMessage.senderName = "Zero"; // Assuming Zero for agent tasks
        agentMessage.timestamp = System.currentTimeMillis();
        chatAdapter.addMessage(agentMessage);
        saveChatMessageToDatabase(agentMessage);

        // Add next stage message/button if applicable
        if (isLastTaskInStage(task)) { 
            ChatMessage nextStageMessage = new ChatMessage();
            nextStageMessage.userId = userId;
            nextStageMessage.role = "assistant";
            nextStageMessage.content = "所有阶段任务已完成。准备好接受下一阶段的挑战了吗？";
            nextStageMessage.senderName = "信使Zero"; 
            nextStageMessage.timestamp = System.currentTimeMillis();
            chatAdapter.addMessage(nextStageMessage);
            saveChatMessageToDatabase(nextStageMessage);

            ChatMessage buttonMessage = new ChatMessage();
            buttonMessage.userId = userId;
            buttonMessage.role = "system";
            buttonMessage.content = "";
            buttonMessage.messageType = "next_stage_button";
            buttonMessage.timestamp = System.currentTimeMillis();
            chatAdapter.addMessage(buttonMessage);
            saveChatMessageToDatabase(buttonMessage);
        }
    }
    // --- End Create addCompletionMessagesToChat --- 
}
