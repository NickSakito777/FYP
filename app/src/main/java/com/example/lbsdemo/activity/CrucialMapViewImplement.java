package com.example.lbsdemo.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CrucialMapViewImplement extends AppCompatActivity implements SensorEventListener {
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1234;
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

    private String currentFenceId = null; // 当前用户所在的围栏ID
    private String currentBleId = null; // 当前BLE广播接收到的楼栋ID
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
        requestPermissions();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始化地理围栏管理器
        geoFenceManager = new GeoFenceManager(this);
        geoFenceManager.initGeoFence();

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
            // 固定位置添加标记点
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

            Log.i("Marker", "地图加载完成，添加标记点");
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
                Log.i("customID", "get the id:" + customId);
            }

            @Override
            public void onUserLeaveFence(String customId) {
                // 用户离开围栏，如果离开的是当前所在的那个围栏，则清空
                if (currentFenceId != null && currentFenceId.equals(customId)) {
                    currentFenceId = null;
                }
            }
        });
        BleManager bleManager = new BleManager(this, new BleManager.OnBleIdReceivedListener() {
            @Override
            public void onBleIdReceived(String bleId) {
                Log.i("MainActivity", "接收到 BLE ID: " + bleId);
                setBleId(bleId); // 设置当前蓝牙ID
                if (currentFenceId != null && currentFenceId.equals(currentBleId)) {
                    Log.i("MainActivity", "蓝牙ID与地理围栏ID匹配，可以打卡");
                } else {
                    Log.i("MainActivity", "蓝牙ID与地理围栏ID不匹配，无法打卡");
                }
            }

            @Override
            public void onBleScanStatusUpdate(String status, boolean enableButton) {
                Log.i("MainActivity", "蓝牙扫描状态更新: " + status);
            }
        });

// 启动蓝牙扫描
        bleManager.startScanning();

    }

    // 在 BLE接收器在接收到相应楼栋ID时调用
    public void setBleId(String bleId) {
        this.currentBleId = bleId;
    }

    // 打卡按钮点击事件设置
    public void onCheckInButtonClicked(String markerId) {
        Log.i("check111", "the id from current fence,ble and marker is: " + currentFenceId + markerId + currentBleId);
        // 判断围栏ID和BLE ID是否匹配，同时判断markerId是否一致
        if (currentFenceId != null && currentFenceId.equals(currentBleId) && currentFenceId.equals(markerId)) {
            // 条件都满足，可以打卡！
            Intent intent = new Intent(CrucialMapViewImplement.this, PhotoActivity.class);
            startActivity(intent);
        } else {
            // 条件不满足，提示用户
            Toast.makeText(this, "不在正确打卡楼或未找到签到处，无法打卡", Toast.LENGTH_SHORT).show();
        }
    }


    private void checkFloatPermission() {
        showFloatWindow();
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

                FloatWindowManager.get().showToolView(String.valueOf(CrucialMapViewImplement.this.hashCode()), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
        }, 1000);//晚一秒实现，先等onCreate的都跑完再跑，不然会出现不显示的问题。
    }


    //创建marker的位置
    private void addMarker(double lat, double lon, String title, String description, String markerId) {
        // 创建标记点位置
        LatLng point = new LatLng(lat, lon);

        // 创建标记点图标
        BitmapDescriptor bitmap = BitmapDescriptorFactory.fromAsset("marker3.png"); // 确保您在 drawable 文件夹中有 marker.png 或类似图标
        if (bitmap == null) {
            Log.e("MarkerError", "图标加载失败，请检查资源文件！");
            return;
        }

        // 创建 OverlayOptions
        OverlayOptions options = new MarkerOptions()
                .position(point) // 标记点位置
                .icon(bitmap)    // 标记点图标
                .draggable(true); // 标记点是否可拖动？

        // 添加标记点到地图
        Marker marker = (Marker) mBaiduMap.addOverlay(options);

        // 为 Marker 设置额外信息
        Bundle bundle = new Bundle();
        bundle.putString("markerId", markerId);
        bundle.putString("title", title);
        bundle.putString("description", description);
        marker.setExtraInfo(bundle);

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

        // 设置地图中心到标记点位置，并调整缩放级别
        MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(point, 18.0f);
        mBaiduMap.animateMapStatus(update);
        Log.i("Marker", "标记点添加成功");
    }

    //marker弹出来的布局
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
        });
        CheckInButton.setOnClickListener(v -> {
            onCheckInButtonClicked(markerId);
        });
        // 设置弹出窗口的关闭按钮功能
        closeButton.setOnClickListener(v -> popupWindow.dismiss());

        // 显示弹出窗口
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.showAtLocation(mMapView, Gravity.CENTER, 0, 0);
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
        Intent Layout2intent = getIntent();
        int Getindex = Layout2intent.getIntExtra("index", 0);
        // 根据 index 值移动地图指定位置

        // 使用 Log.i 输出调试信息，查看是否正确获取了 index
        Log.i("MainActivity", "Received index: " + Getindex);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkFloatPermission();
                }
            }, 500);//延迟一小会，用于错开时间戳，不然会显示错误，血的教训
        }
        //cartoonmap的index接收以及处理
        if (Getindex == 4) {
            moveCamera(mBaiduMap, new LatLng(31.279303, 120.746498), 20f);
        } else if (Getindex == 5) {
            moveCamera(mBaiduMap, new LatLng(31.279145, 120.744912), 20f);
        } else if (Getindex== 6) {
            moveCamera(mBaiduMap, new LatLng(31.278331, 120.746453), 20f);
        }

        if(requestCode== 0 && resultCode==RESULT_OK){
            int index = data.getIntExtra("index", 0);
            if (index == 1) {
                moveCamera(mBaiduMap, new LatLng(31.279303, 120.746498), 20f);
            } else if (index == 2) {
                moveCamera(mBaiduMap, new LatLng(31.279145, 120.744912), 20f);  // 修改为实际经纬度
            } else if (index == 3) {
                moveCamera(mBaiduMap, new LatLng(31.278331, 120.746453), 20f);  // 修改为实际经纬度
            } else {
        //接收来自上一层和下一层的index，若index符合，则跳转到对应的位置，设置主视角的经纬度以及缩放大小，起到任务追踪的功能
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

    /**
     * 请求权限
     */
    private void requestPermissions() {
        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(CrucialMapViewImplement.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(CrucialMapViewImplement.this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(android.Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(CrucialMapViewImplement.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
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
            currentPosition.append("更新时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
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
//        }

    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
        FloatWindowManager.get().hindToolView(String.valueOf(this.hashCode()));
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
}
