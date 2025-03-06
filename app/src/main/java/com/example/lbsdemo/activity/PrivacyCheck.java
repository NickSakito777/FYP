
/*
1、打开app弹出协议，禁止返回键取消显示。
2、再次打开协议页不再弹出。
 */
package com.example.lbsdemo.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.lbsdemo.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

public class PrivacyCheck extends AppCompatActivity {
    Dialog dialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        PravicyCheck();
    }
    public void onClickAgree(View v)
    {
        dialog.dismiss();
        //下面将已阅读标志写入文件，再次启动的时候判断是否显示。
        this.getSharedPreferences("file", Context.MODE_PRIVATE).edit()
                .putBoolean("AGREE", true)
                .apply();
        Intent intent = new Intent(PrivacyCheck.this, LoginActivity.class);
        startActivity(intent);
        finish();

    }
    public void onClickDisagree(View v)
    {
        System.exit(0);//退出软件
    }
    public void showPrivacy(String privacyFileName){
        String str = initAssets(privacyFileName);
        final View inflate = LayoutInflater.from(PrivacyCheck.this).inflate(R.layout.dialog_privacy_show, null);
        TextView tv_title = inflate.findViewById(R.id.tv_title);
        tv_title.setText("隐私政策授权提示");
        TextView tv_content = inflate.findViewById(R.id.tv_content);
        tv_content.setText(str);
        dialog = new AlertDialog
                .Builder(PrivacyCheck.this)
                .setView(inflate)
                .show();
        // 通过WindowManager获取
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        final WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = dm.widthPixels*4/5;
        params.height = dm.heightPixels*1/2;
        dialog.setCancelable(false);//屏蔽返回键
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }
    /**
     * 从assets下的txt文件中读取数据
     */
    public String initAssets(String fileName) {
        String str = null;
        try {
            InputStream inputStream = getAssets().open(fileName);

            str = getString(inputStream);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return str;
    }
    public static String getString(InputStream inputStream) {
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }
        BufferedReader reader = new BufferedReader(inputStreamReader);
        StringBuffer sb = new StringBuffer("");
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
    public void PravicyCheck(){
        Boolean status =this.getSharedPreferences("file",Context.MODE_PRIVATE)
                .getBoolean("AGREE",false);
        if (status==true){

        }else{
            showPrivacy("PrivacyContent.txt");//放在assets目录下的隐私政策文本文件
        }
    }

}