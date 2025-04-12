package com.example.lbsdemo.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lbsdemo.R;
import com.example.lbsdemo.map.FloatWindowManager;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.User;

//这是用户的个人主页面的activity
public class ProfileActivity extends AppCompatActivity {
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        FloatWindowManager.get().hideToolView();

        db = AppDatabase.getInstance(this);

        // 设置自定义的 Toolbar 为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 启用返回按钮
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // 设置返回按钮点击事件
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 返回到主界面
                onBackPressed();
            }
        });

        // 从SharedPreferences获取用户ID
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");

        // 从数据库获取完整的用户信息
        new Thread(() -> {
            User user = db.userDao().getUserById(userId);
            if (user != null) {
                runOnUiThread(() -> {
                    // 设置学生ID
                    TextView studentIdTextView = findViewById(R.id.studentId);
                    studentIdTextView.setText("学生ID: " + user.studentId);

                    // 设置用户名
                    TextView usernameTextView = findViewById(R.id.username);
                    usernameTextView.setText("用户名: " + user.username);
                });
            }
        }).start();

        // 设置西浦码按钮点击事件
        Button btnXiluCode = findViewById(R.id.btnXiluCode);
        btnXiluCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileActivity.this, WebViewActivity.class);
                startActivity(intent);
            }
        });
    }
    
    // 创建选项菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 加载菜单资源
        getMenuInflater().inflate(R.menu.profile_menu, menu);
        return true;
    }
    
    // 处理菜单项点击事件
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_logout) {
            // 显示退出登录确认对话框
            showLogoutConfirmDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    // 显示退出登录确认对话框
    private void showLogoutConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("退出登录")
               .setMessage("确定要退出登录吗？")
               .setPositiveButton("退出", (dialog, which) -> {
                   // 执行退出登录操作
                   logout();
               })
               .setNegativeButton("取消", (dialog, which) -> {
                   // 取消操作，对话框关闭
                   dialog.dismiss();
               })
               .show();
    }
    
    // 执行退出登录操作
    private void logout() {
        // 清除用户登录信息
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); // 清除所有用户数据
        editor.apply();
        
        // 跳转到登录页面，并完全销毁所有Activity
        Intent intent = new Intent(this, LoginActivity.class);
        // 清除返回栈中的所有Activity
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        
        // 结束当前Activity
        finish();
        
        // 可选：强制结束进程，完全退出应用
        // 注意：这是一种强制方式，通常不推荐使用，但在退出登录场景可以考虑
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FloatWindowManager.get().visibleToolView();
    }
}
