package com.example.lbsdemo.user;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.baidu.ar.arplay.core.engine3d.BuildConfig;
import com.example.lbsdemo.R;
import com.example.lbsdemo.activity.LoginActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// QuestionnaireActivity.java
public class QuestionnaireActivity extends AppCompatActivity {

    private boolean[][] timeSelections = new boolean[5][11]; // 记录选择状态[^23]
    // 从section_study_habits.xml获取 [^section_study_habits.xml]
    private RadioGroup rgStudyDuration;
    // 从section_activity_preference.xml获取 [^section_activity_preference.xml]
    private RadioGroup rgTimePattern;
    private CheckBox cbMorning, cbAfternoon, cbNight, cbEvening;
    private LinearLayout buildingCheckboxContainer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questionnaire);
        // 正确获取代号的RadioGroup [^section_study_habits.xml]
        rgStudyDuration = findViewById(R.id.rgStudyDuration);
        // 正确获取时间模式的RadioGroup [^section_activity_preference.xml]
        rgTimePattern = findViewById(R.id.rgTimePattern);
        rgTimePattern = findViewById(R.id.rgTimePattern);
        cbMorning = findViewById(R.id.cbMorning);
        cbAfternoon = findViewById(R.id.cbAfternoon);
        cbNight = findViewById(R.id.cbNight);
        cbEvening = findViewById(R.id.cbEvening);
        buildingCheckboxContainer = findViewById(R.id.buildingCheckboxContainer);
        initTimetable();
        setupSubmitButton();


        findViewById(R.id.btnSubmit).setOnClickListener(v -> validateForm());
    }

    // 动态生成课表网格
    private void initTimetable() {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        TableLayout table = new TableLayout(this);

        // 生成时间头（横向时间轴）
        TableRow headerRow = new TableRow(this);
        headerRow.addView(createHeaderCell("")); // 空白填充左上角

        // 显示时间区间（9-10, 10-11,...18-19）[^17]
        String[] timeSlots = getResources().getStringArray(R.array.time_slots);
        for (String slot : timeSlots) {
            headerRow.addView(createHeaderCell(slot));
        }
        table.addView(headerRow);

        // 生成每天的时间选择行
        String[] days = getResources().getStringArray(R.array.week_days);
        timeSelections = new boolean[days.length][timeSlots.length]; // 调整数组维度 [^28]

        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            TableRow row = new TableRow(this);
            row.addView(createHeaderCell(days[dayIndex]));

            for (int timeIndex = 0; timeIndex < timeSlots.length; timeIndex++) {
                CheckBox cb = new CheckBox(this);
                cb.setLayoutParams(new TableRow.LayoutParams(
                        TableRow.LayoutParams.WRAP_CONTENT,
                        150 // 固定高度保证可视性
                ));

                final int finalDay = dayIndex;
                final int finalTime = timeIndex;
                cb.setOnCheckedChangeListener((view, checked) -> {
                    timeSelections[finalDay][finalTime] = checked;
                });

                row.addView(cb);
            }
            table.addView(row);
        }

        scrollView.addView(table);
        ((LinearLayout) findViewById(R.id.timetableContainer)).addView(scrollView);
    }

    private TextView createHeaderCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    // 配置提交按钮点击事件[^18]
    private void setupSubmitButton() {
        findViewById(R.id.btnSubmit).setOnClickListener(v -> {
            if (!validateForm()) {

            }
            saveQuestionnaireData();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
    private void setupDebugQuery() {
        // 只在debug版本显示调试按钮
        if (BuildConfig.DEBUG) {
            findViewById(R.id.btnSubmit).setOnLongClickListener(v -> {
                queryDatabaseContent();
                return true;
            });
        }
    }

    private void queryDatabaseContent() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            // 查询用户数据
            List<User> users = db.userDao().getAllUsers();
            for (User u : users) {
                Log.d("DB_QUERY", "用户: " + u.studentId + "|" + u.username);
            }

            // 查询问卷数据
            List<QuestionnaireData> forms = db.questionnaireDao().getAllQuestionnaires();
            for (QuestionnaireData q : forms) {
                Log.d("DB_QUERY", "问卷: " + q.userId + "|" + q.timeTable);
            }
        });
    }

    private boolean validateForm() {
        boolean isValid = true;
        StringBuilder errorMessage = new StringBuilder();

        // 验证顺序1：必选项统一处理
        if (rgStudyDuration.getCheckedRadioButtonId() == -1) {
            isValid = false;
            errorMessage.append("• 请选择每周课外学习时长\n");
        }
        if (rgTimePattern.getCheckedRadioButtonId() == -1) {
            isValid = false;
            errorMessage.append("• 请选择时间管理偏好\n");
        }

        // 验证顺序2：多选项统一处理
        if (!(cbMorning.isChecked() || cbAfternoon.isChecked()
                || cbNight.isChecked() || cbEvening.isChecked())) {
            isValid = false;
            errorMessage.append("• 请至少选择一个任务时段\n");
        }
        if (!isAnyBuildingSelected()) { // 修正项 [^27][^24]
            isValid = false;
            errorMessage.append("• 请至少选择一栋教学楼\n");
        }

        // 完整收集错误后再处理结果
        if (isValid) {
            saveQuestionnaireData();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("缺少必要信息")
                    .setMessage(errorMessage)
                    .setPositiveButton("继续填写", null)
                    .show();
        }
        return isValid;
    }

    private boolean isAnyBuildingSelected() {
        // 通过嵌套循环检查每个CheckBox
        for (int rowIndex=0; rowIndex<buildingCheckboxContainer.getChildCount(); rowIndex++) {
            View row = buildingCheckboxContainer.getChildAt(rowIndex);
            if (row instanceof ViewGroup) {
                ViewGroup rowGroup = (ViewGroup) row;
                for (int col=0; col<rowGroup.getChildCount(); col++) {
                    View child = rowGroup.getChildAt(col);
                    // 跳过占位的空白View [^30]
                    if (child.getId() == R.id.placeholder) continue;

                    if (child instanceof CheckBox) {
                        CheckBox cb = (CheckBox) child;
                        if (cb.isChecked()) return true;
                    }
                }
            }
        }
        return false;
    }

    // 数据存储逻辑（简化版）[^7]
    private void saveQuestionnaireData() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // 后台线程进行数据库操作 [^1]
            QuestionnaireData data = new QuestionnaireData();
            data.userId = getIntent().getStringExtra("user_id");
            data.studyDuration = getStudyDurationSelection();
            data.timeTable = convertTimeSelectionsToJson();
            data.submitTime = System.currentTimeMillis();
            data.timePattern = getTimePatternSelection();
            data.learningScenes = collectLearningScenesSelection();   // 学习场景多选
            data.buildingPreferences = collectBuildingPreferences(); // 教学楼多选
            data.taskTimeWindows = collectTaskTimeWindows();          // 任务时段多选

            AppDatabase db = AppDatabase.getInstance(this);
            db.questionnaireDao().insert(data);

            // 如果需要UI更新需切回主线程
            runOnUiThread(() ->
                    Toast.makeText(this, "数据保存成功", Toast.LENGTH_SHORT).show());
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private String collectLearningScenesSelection() {
        JSONArray scenes = new JSONArray();

        // 从section_study_habits.xml获取选择项
        CheckBox cbLibrary = findViewById(R.id.cbLibrary);
        CheckBox cbClassroom = findViewById(R.id.cbClassroomLounge);
        CheckBox cbCafe = findViewById(R.id.cbCafe);
        CheckBox cbDorm = findViewById(R.id.cbDorm);
        // 其他复选框根据实际布局补充...

        if(cbLibrary.isChecked()) scenes.put("图书馆");
        if(cbClassroom.isChecked()) scenes.put("教学楼");
        if(cbCafe.isChecked()) scenes.put("咖啡厅");
        if (cbDorm.isChecked())scenes.put("宿舍");

        return scenes.toString();
    }
    private String collectBuildingPreferences() {
        JSONArray buildings = new JSONArray();

        LinearLayout container = findViewById(R.id.buildingCheckboxContainer);

        // 双层循环遍历行和列 [^14]
        for(int row = 0; row < container.getChildCount(); row++) {
            View rowView = container.getChildAt(row);
            if(rowView instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) rowView;
                for(int col=0; col < rowLayout.getChildCount(); col++){
                    View view = rowLayout.getChildAt(col);

                    // 根据[^section_study_habits.xml]占位符设置跳过逻辑
                    if(view.getId() == R.id.placeholder) continue;

                    if(view instanceof CheckBox) {
                        CheckBox cb = (CheckBox)view;
                        if(cb.isChecked()) buildings.put(cb.getText().toString());
                    }
                }
            }
        }
        return buildings.toString();
    }

    // 方法3：收集任务时段多选结果（对应section_activity_preference.xml中的时段多选）
    private String collectTaskTimeWindows() {
        JSONArray periods = new JSONArray();

        // 获取用户选择的时段（示例ID需与实际XML一致）[^8][^10]
        CheckBox cbMorningTask = findViewById(R.id.cbMorning);
        CheckBox cbAfternoonTask = findViewById(R.id.cbAfternoon);
        CheckBox cbNightTask = findViewById(R.id.cbNight);

        if(cbMorningTask.isChecked()) periods.put("早晨(8-10点)");
        if(cbAfternoonTask.isChecked()) periods.put("下午(14-16点)");
        if(cbNightTask.isChecked()) periods.put("晚上(20-22点)");
        if (cbEvening.isChecked())periods.put("凌晨(23-4)点");

        return periods.toString();
    }
    // 将时间选择转换为JSON
    private String convertTimeSelectionsToJson() {
        JSONObject root = new JSONObject();
        try {
            for (int day = 0; day < timeSelections.length; day++) {
                JSONArray hours = new JSONArray();
                for (int slot = 0; slot < timeSelections[day].length; slot++) {
                    if (timeSelections[day][slot]) {
                        // 根据实际时间槽计算开始时间（示例）
                        int startHour = 9 + slot;
                        hours.put(startHour + ":00-" + (startHour + 1) + ":00");
                    }
                }
                root.put("day_" + day, hours);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return root.toString();
    }

    // 学习时长选项获取 [^section_study_habits.xml]
    private String getStudyDurationSelection() {
        int selectedId = rgStudyDuration.getCheckedRadioButtonId();

        if (selectedId == R.id.rbLessThan1h) { // 检查是否匹配ID
            return "1小时以下";
        } else if (selectedId == R.id.rb1to2h) {
            return "1-2小时";
        } else if (selectedId == R.id.rb3to4h) {
            return "3-4小时";
        } else if (selectedId == R.id.rbMoreThan4h) {
            return "4小时以上";
        } else {
            return "未选择";
        }
    }


    // 时间模式选项获取 [^section_activity_preference.xml]
    private String getTimePatternSelection() {
        int selectedId = rgTimePattern.getCheckedRadioButtonId();

        // 通过XML定义的RadioButton ID匹配选项 [^5][^section_activity_preference.xml]
        if (selectedId == R.id.rbStrictSchedule) {
            return "严格作息";
        } else if (selectedId == R.id.rbFlexibleSchedule) {
            return "灵活调整";
        } else if (selectedId == R.id.rbNightActive) {
            return "夜间活跃";
        } else if (selectedId == R.id.cbEvening) {
            return "凌晨活跃";
        } else {
            return "未选择";
        }
    }
}

