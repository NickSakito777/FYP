package com.example.lbsdemo.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.example.lbsdemo.map.FloatWindowManager;
import com.example.lbsdemo.utils.MappingTransform;
import com.example.lbsdemo.R;
import com.example.lbsdemo.activity.CrucialMapViewImplement;

import java.util.ArrayList;
import java.util.List;
/*此乃第二层手绘地图，实现了呃
1. 放置卡通图
2. 启用百度经纬度定位，且将经纬度传递到MappingTransform进行计算，绘制当前红点坐标
3. 通过marker的经纬度坐标，依旧传到MapT里进行计算，绘制marker
4. 拿到这些数据通过MapIV来绘制
5. 在画一个悬浮窗的圆形，更新到悬浮窗
6. 按下marker按钮可以跳转到下一个第三层
*/
public class CartoonMapView extends AppCompatActivity implements MapImageView.OnMarkerClickListener {
    private static final String TAG = "Activity_Function";
    private LocationClient mLocationClient;
    private MappingTransform mappingTransform;
    private MapImageView mapImageView;

    private int imageWidth;
    private int imageViewWidth;
    private int imageViewHeight;
    private int imageHeight;

    private float density;

    // 新增字段存储调整后的坐标
    private float adjustedX = -1;
    private float adjustedY = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        requestPermissions();

        try {
            mappingTransform = new MappingTransform();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "映射点数量不足: " + e.getMessage());
            Toast.makeText(this, "映射点数量不足，无法初始化坐标转换", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mapImageView = findViewById(R.id.mapImageView);
        if (mapImageView == null) {
            Log.e(TAG, "MapImageView 未找到！");
            Toast.makeText(this, "地图视图未找到", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        //addMarker();
        mapImageView.setOnMarkerClickListener((MapImageView.OnMarkerClickListener) this);

        LocationClient.setAgreePrivacy(true);
        try {
            initLocationClient();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Button buttonShowMap = findViewById(R.id.button_show_map);
        if (buttonShowMap != null) {
            buttonShowMap.setOnClickListener(v -> {
                Intent intent = new Intent(CartoonMapView.this, CrucialMapViewImplement.class);
                startActivity(intent);
            });
        }
    }

    private void requestPermissions() {
        List<String> permissionList = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissionList.isEmpty()) {
            String[] permissions = permissionList.toArray(new String[0]);
            ActivityCompat.requestPermissions(this, permissions, 1);
        }
    }

    private void initLocationClient() throws Exception {
        mLocationClient = new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(new MyLocationListener());

        LocationClientOption locationOption = new LocationClientOption();
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        locationOption.setCoorType("bd09ll");
        locationOption.setScanSpan(1000);
        locationOption.setOpenGps(true);
        locationOption.setIsNeedAddress(false);

        mLocationClient.setLocOption(locationOption);
        mLocationClient.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "必须同意所有权限才能使用此功能", Toast.LENGTH_LONG).show();
//                    finish();
                    return;
                }
            }
            try {
                initLocationClient();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            Toast.makeText(this, "发生未知错误", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    @Override
    public void onMarkerClick(MapImageView.CustomMarker marker) {
        switch (marker.id) {
            case "marker1":
                navigateToMap(1);
                break;
            case "marker2":
                navigateToMap(2);
                break;
            case "marker3":
                navigateToMap(3);
                break;
            default:
                Toast.makeText(this, "点击了未知标记", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // 用于跳转到目标Activity并传递index值
    private void navigateToMap(int index) {
        Intent intent = new Intent(CartoonMapView.this, CrucialMapViewImplement.class);
        intent.putExtra("index", index);  // 把index传递到目标Activity
        startActivityForResult(intent,RESULT_OK); // 启动并等待返回结果
    }


    private class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null) return;

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            Log.d(TAG, "当前经纬度：纬度=" + latitude + ", 经度=" + longitude);

            MappingTransform.Point userPixel = mappingTransform.transform(longitude, latitude);
            Log.d(TAG, "转换后的像素坐标：x=" + userPixel.x + ", y=" + userPixel.y);

            MappingTransform.Point mark1 = mappingTransform.transform(120.746431,31.279261);//120.746431,31.279261 SB maker1
            MappingTransform.Point mark2 = mappingTransform.transform(120.74462,31.279165);//120.74462,31.279165 CB marker2
            MappingTransform.Point mark3 = mappingTransform.transform(120.746399,31.278362);//120.746399,31.278362 SD marker3
            runOnUiThread(() -> {
                // 更新红点位置
                mapImageView.setUserPosition((float) userPixel.x, (float) userPixel.y);
//                mapImageView.addMarks((float) mark1.x, (float) mark1.y);
//                mapImageView.addMarks((float) mark2.x, (float) mark2.y);
//                mapImageView.addMarks((float) mark3.x, (float) mark3.y);
                if (mapImageView.getMarks().isEmpty()) {
                    mapImageView.addMark((float) mark1.x, (float) mark1.y, "marker1");
                    mapImageView.addMark((float) mark2.x, (float) mark2.y, "marker2");
                    mapImageView.addMark((float) mark3.x, (float) mark3.y, "marker3");
                }
                // 在下一次绘制完成后获取adjusted坐标和截取圆形图
                mapImageView.post(() -> {
                    float adjX = mapImageView.getAdjustedX();
                    float adjY = mapImageView.getAdjustedY();
                    Log.d(TAG, "屏幕坐标：adjustedX=" + adjX + ", adjustedY=" + adjY);

                    // 获取当前显示内容的截图
                    Bitmap displayedBitmap = Bitmap.createBitmap(mapImageView.getWidth(), mapImageView.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas tempCanvas = new Canvas(displayedBitmap);
                    mapImageView.draw(tempCanvas);

                    // 截取圆形区域
                    float radius = 100; // 可自行调整圆形半径
                    Bitmap circleBitmap = getCircularBitmapFromDisplayed(displayedBitmap, adjX, adjY, radius);

                    // 更新到悬浮窗
                    View toolView = FloatWindowManager.get().getToolView();
                    if (toolView != null) {
                        ImageView ivCircleMap = toolView.findViewById(R.id.iv_circle_map);
                        if (ivCircleMap != null && circleBitmap != null) {
                            ivCircleMap.setImageBitmap(circleBitmap);
                        }
                    }
                });
            });
        }
    }

    private Bitmap getCircularBitmapFromDisplayed(Bitmap displayedBitmap, float centerX, float centerY, float radius) {
        int left = (int)(centerX - radius);
        int top = (int)(centerY - radius);
        int diameter = (int)(2 * radius);

        if (left < 0) left = 0;
        if (top < 0) top = 0;
        if (left + diameter > displayedBitmap.getWidth()) {
            diameter = displayedBitmap.getWidth() - left;
        }
        if (top + diameter > displayedBitmap.getHeight()) {
            diameter = displayedBitmap.getHeight() - top;
        }

        if (diameter <= 0) return null;

        Bitmap squareBitmap = Bitmap.createBitmap(displayedBitmap, left, top, diameter, diameter);
        Bitmap output = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(diameter / 2f, diameter / 2f, diameter / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squareBitmap, 0, 0, paint);

        return output;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLocationClient != null) {
            mLocationClient.stop();
        }
    }

    @Override
    public void onBackPressed() {
    }
}
