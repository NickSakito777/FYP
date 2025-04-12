package com.example.lbsdemo.map;

import android.app.Application;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
//用于初始化百度地图api服务，虽然在第三层main函数已经
public class BaiduInitialization extends Application {
    public static Application application;
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            application = this;
            SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
            SDKInitializer.initialize(this);
            SDKInitializer.setCoordType(CoordType.BD09LL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getStatusBarHeight() {
        int result = 0;
        int resourceId = application.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result =  application.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
}

