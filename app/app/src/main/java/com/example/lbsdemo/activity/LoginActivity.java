package com.example.lbsdemo.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.lbsdemo.R;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.QuestionnaireActivity;
import com.example.lbsdemo.user.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilStudentId;
    private EditText etUsername, etPassword;
    private AppDatabase db;
    private boolean isRegisterMode = false;
    private MaterialButton btnAuthAction;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        checkPrivacyAgreement();
        initDatabase();
        setupUIComponents();
    }

    private void checkPrivacyAgreement() {
        boolean hasAgreed = getSharedPreferences("file", Context.MODE_PRIVATE)
                .getBoolean("AGREE", false);

        if (!hasAgreed) {
            startActivity(new Intent(this, PrivacyCheck.class));
            finish();
        }
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(this);
    }

    private void setupUIComponents() {
        tilStudentId = findViewById(R.id.tilStudentId);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnAuthAction = findViewById(R.id.btnAuthAction); // 更改为统一操作按钮
        TextView tvSwitchMode = findViewById(R.id.textSwitchMode);

        btnAuthAction.setOnClickListener(v -> handleAuthAction());
        findViewById(R.id.textForgotPwd).setOnClickListener(v -> showPasswordResetDialog());
        tvSwitchMode.setOnClickListener(v -> toggleRegisterMode(v));
    }

    private void toggleRegisterMode(View v) {
        isRegisterMode = !isRegisterMode;
        tilStudentId.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        btnAuthAction.setText(isRegisterMode ? "完成注册" : "登录"); // 动态改变按钮文本
        ((TextView)findViewById(R.id.textSwitchMode)).setText(
                isRegisterMode ? "已有账号？登录" : "注册账号");
    }

    private void handleAuthAction() {
        if (isRegisterMode) {
            handleRegistration();
        } else {
            handleLogin();
        }
    }

    // 修复点：登录验证使用子线程 [^7]
    private void handleLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (validateInputs(username, password)) {
            new Thread(() -> {
                User user = db.userDao().getUserByUsername(username);
                boolean isValid = user != null && user.password.equals(password);

                runOnUiThread(() -> {
                    if (isValid) {
                        getSharedPreferences("user_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("user_id", user.studentId) // 这里使用用户对象的主键
                                .apply();
                        navigateToMap();
                    } else {
                        showAuthError("用户名或密码错误");
                    }
                });
            }).start();
        }
    }

    private void handleRegistration() {
        String studId = ((EditText)findViewById(R.id.etStudentId)).getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (validateInputs(studId, username, password)) {
            new Thread(() -> {
                User existingUser = db.userDao().getUserById(studId);
                boolean userExists = existingUser != null;

                if (!userExists) {
                    db.userDao().insertUser(new User(studId, username, password));

                    // 新增：跳转问卷页面 [^1][^25]
                    runOnUiThread(() -> {
                        getSharedPreferences("user_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("user_id", studId) // studId来自注册表单
                                .apply();
                        Intent questionnaireIntent = new Intent(LoginActivity.this, QuestionnaireActivity.class);
                        questionnaireIntent.putExtra("user_id", studId);
                        startActivity(questionnaireIntent);
                        finish(); // 关闭当前登录页面
                    });
                } else {
                    runOnUiThread(() -> showAuthError("该学号已注册"));
                }
            }).start();
        }
    }

    // 修复点：密码重置异步处理 [^18]
    private void showPasswordResetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_reset_pwd, null);

        EditText etStudId = view.findViewById(R.id.et_reset_student_id);
        EditText etNewPassword = view.findViewById(R.id.et_new_password);

        builder.setView(view)
                .setPositiveButton("确认", (dialog, which) -> {
                    String studId = etStudId.getText().toString().trim();
                    String newPassword = etNewPassword.getText().toString().trim();

                    if (!studId.isEmpty() && !newPassword.isEmpty()) {
                        new Thread(() -> {
                            User user = db.userDao().getUserById(studId);
                            boolean exists = user != null;

                            if (exists) {
                                user.password = newPassword;
                                db.userDao().updatePassword(user);
                            }

                            runOnUiThread(() -> {
                                if (exists) {
                                    Toast.makeText(this, "密码已更新", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "学号不存在", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean validateInputs(String... inputs) {
        for (String input : inputs) {
            if (input.isEmpty()) {
                Toast.makeText(this, "请填写所有必填项", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void navigateToMap() {
//        startActivity(new Intent(this, CartoonMapView.class));

        startActivity(new Intent(this,CrucialMapViewImplement.class));
        finish();
    }

    private void showAuthError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void resetUIState() {
        isRegisterMode = false;
        tilStudentId.setVisibility(View.GONE);
        ((TextView)findViewById(R.id.textSwitchMode)).setText("注册账号");
        clearInputFields();
    }

    private void clearInputFields() {
        ((EditText)findViewById(R.id.etStudentId)).setText("");
        etUsername.setText("");
        etPassword.setText("");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 登录界面不需要处理浮动窗口的隐藏/显示
        // FloatWindowManager.get().visibleToolView();
    }
}
