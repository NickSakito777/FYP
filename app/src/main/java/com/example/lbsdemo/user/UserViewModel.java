package com.example.lbsdemo.user;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

/**
 * 用户视图模型
 * 用于管理用户信息和状态
 */
public class UserViewModel extends AndroidViewModel {
    private static final String TAG = "UserViewModel";
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_CURRENT_USER_ID = "current_user_id";
    
    public UserViewModel(@NonNull Application application) {
        super(application);
    }
    
    /**
     * 获取当前登录用户ID
     * @return 用户ID，如果未登录则返回null
     */
    public String getCurrentUserId() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String userId = prefs.getString(KEY_CURRENT_USER_ID, null);
        
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "没有找到已登录用户");
            return null;
        }
        
        return userId;
    }
    
    /**
     * 设置当前登录用户ID
     * @param userId 要设置的用户ID
     */
    public void setCurrentUserId(String userId) {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        if (userId == null || userId.isEmpty()) {
            // 清除用户ID（登出）
            editor.remove(KEY_CURRENT_USER_ID);
            Log.d(TAG, "已清除当前用户ID");
        } else {
            // 设置新的用户ID（登录）
            editor.putString(KEY_CURRENT_USER_ID, userId);
            Log.d(TAG, "已设置当前用户ID: " + userId);
        }
        
        editor.apply();
    }
    
    /**
     * 检查用户是否已登录
     * @return 是否已登录
     */
    public boolean isUserLoggedIn() {
        return getCurrentUserId() != null;
    }
    
    /**
     * 登出当前用户
     */
    public void logout() {
        setCurrentUserId(null);
    }
} 