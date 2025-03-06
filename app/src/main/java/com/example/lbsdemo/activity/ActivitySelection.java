package com.example.lbsdemo.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lbsdemo.R;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.QuestionnaireData;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ActivitySelection extends AppCompatActivity {
    private AppDatabase db;
    private Button testApiButton;
    private TextView tvResponse;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);

        // 初始化数据库
        db = AppDatabase.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 启用返回按钮
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        testApiButton = findViewById(R.id.btn_test_api);
        tvResponse = findViewById(R.id.tv_response);

        // 获取按钮并设置点击事件
        Button activity1Button = findViewById(R.id.activity1Button);
        Button activity2Button = findViewById(R.id.activity2Button);
        Button activity3Button = findViewById(R.id.activity3Button);


        activity1Button.setOnClickListener(v -> navigateToMap(1)); // 返回index给主界面用于
        activity2Button.setOnClickListener(v -> navigateToMap(2)); // 示例经纬度
        activity3Button.setOnClickListener(v -> navigateToMap(3)); // 示例经纬度


        // 设置返回按钮点击事件
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 返回到主界面
                onBackPressed();
            }
        });
        setupTestApiButton();//setup test bottom
    }
    private void setupTestApiButton() {
        testApiButton.setOnClickListener(v -> {
            // 获取当前用户ID
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String userId = prefs.getString("user_id", "");

            if (userId.isEmpty()) {
                Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
                return;
            }

            tvResponse.setText("正在加载用户数据...");
            tvResponse.setVisibility(View.VISIBLE);

            new Thread(() -> {
                try {
                    // 从数据库获取问卷数据
                    QuestionnaireData userData = db.questionnaireDao().getByUserId(userId);

                    if (userData == null) {
                        runOnUiThread(() ->
                                tvResponse.setText("未找到问卷数据")
                        );
                        return;
                    }

                    // 构建API请求体
                    JSONObject reqBody = new JSONObject();
                    reqBody.put("model", "Qwen/Qwen2.5-32B-Instruct");

                    JSONArray messages = new JSONArray();
                    messages.put(new JSONObject()
                            .put("role", "system")
                            .put("content", "用户数据：\n" +
                                    "学习时长：" + userData.studyDuration + "\n" +
                                    "教学楼偏好：" + userData.buildingPreferences + "\n" +
                                    "时间表：" + userData.timeTable));
                    messages.put(new JSONObject()
                            .put("role", "user")
                            .put("content", "请你返回我上传的内容，一字不漏。并且根据此写一个20字的学习计划"));
                    reqBody.put("messages", messages);

                    // 发送请求
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder()
                            .url("https://api.siliconflow.cn/v1/chat/completions")
                            .addHeader("Authorization",
                                    "Bearer sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak")
                            .post(RequestBody.create(reqBody.toString(), MediaType.get("application/json")))
                            .build();

                    Response response = client.newCall(request).execute();
                    String responseData = response.body().string();

                    // 解析响应
                    String result = new JSONObject(responseData)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    runOnUiThread(() ->
                            tvResponse.setText("DeepSeek回复：\n" + result)
                    );
                } catch (Exception e) {
                    runOnUiThread(() ->
                            tvResponse.setText("请求失败：" + e.getMessage())
                    );
                }
            }).start();
        });
    }
    private void navigateToMap(int index) {
        Intent intent = new Intent();
        intent.putExtra("index", index);
        setResult(RESULT_OK, intent);
        finish();
    }

}
