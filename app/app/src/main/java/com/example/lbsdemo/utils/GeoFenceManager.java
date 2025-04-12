package com.example.lbsdemo.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.baidu.geofence.GeoFence;
import com.baidu.geofence.GeoFenceClient;
import com.baidu.geofence.GeoFenceListener;
import com.baidu.geofence.model.DPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//这是地理围栏：手动绘制了地理围栏，通过遍历每个围栏的边界点，创建围栏，用于判断，并且返回结果给CrucialMVI
public class GeoFenceManager {
    public static final String GEOFENCE_BROADCAST_ACTION = "com.example.lbsdemo.GEOFENCE_BROADCAST";
    private final GeoFenceClient mGeoFenceClient;
    private final Context mContext;
    private LocationStayManager locationStayManager;

    // 回调接口，用于传递当前用户所在的围栏ID
    public interface OnGeoFenceStatusListener {
        void onUserEnterFence(String customId);
        void onUserLeaveFence(String customId);
    }

    private OnGeoFenceStatusListener onGeoFenceStatusListener;

    public GeoFenceManager(Context context) {
        this.mContext = context;
        mGeoFenceClient = new GeoFenceClient(context.getApplicationContext());
        locationStayManager = new LocationStayManager(context);

    }

    public void setOnGeoFenceStatusListener(OnGeoFenceStatusListener listener) {
        this.onGeoFenceStatusListener = listener;
    }
    public void setCurrentUserId(String userId) {
        if (locationStayManager != null) {
            locationStayManager.setUserId(userId);
        }
    }

    // 初始化多个地理围栏
    public void initGeoFence() {
        if (mGeoFenceClient == null) {
            Log.e("GeoFence", "mGeoFenceClient 未初始化");
            return;
        } else {
            Log.i("GeoFence", "mGeoFenceClient 已初始化");
        }

        // 设置触发条件和触发次数
        mGeoFenceClient.setActivateAction(GeoFenceClient.GEOFENCE_IN_OUT_STAYED);
        mGeoFenceClient.setTriggerCount(3, 3, 3); // 设置进入、离开、停留的触发次数
        mGeoFenceClient.setStayTime(60); // 设置停留时间为 60 秒
        mGeoFenceClient.createPendingIntent(GEOFENCE_BROADCAST_ACTION);

        // 定义多个围栏的边界点和对应的名称
        List<List<DPoint>> geoFencePointsList = new ArrayList<>();
        List<String> geoFenceNames = new ArrayList<>();

        // 添加围栏的边界点和名称
        // SA楼 围栏边界点
        List<DPoint> fence1 = new ArrayList<>();
        fence1.add(GeoFenceUtils.createDPoint(120.745874, 31.279793));
        fence1.add(GeoFenceUtils.createDPoint(120.746799, 31.279812));
        fence1.add(GeoFenceUtils.createDPoint(120.746808, 31.279646));
        fence1.add(GeoFenceUtils.createDPoint(120.745878, 31.279623));
        geoFencePointsList.add(fence1);
        geoFenceNames.add("SA");
//        DPoint saCenter = GeoFenceUtils.createDPoint(120.746404,31.279666);

        // SB楼 围栏边界点
        List<DPoint> fence2 = new ArrayList<>();
        fence2.add(GeoFenceUtils.createDPoint(120.745905, 31.279334));
        fence2.add(GeoFenceUtils.createDPoint(120.746812, 31.279345));
        fence2.add(GeoFenceUtils.createDPoint(120.746821, 31.279183));
        fence2.add(GeoFenceUtils.createDPoint(120.74591, 31.279168));
        geoFencePointsList.add(fence2);
        geoFenceNames.add("SB");
//        DPoint sbCenter = GeoFenceUtils.createDPoint(120.746404,31.279666);
        // SC楼 围栏边界点
        List<DPoint> fence3 = new ArrayList<>();
        fence3.add(GeoFenceUtils.createDPoint(120.745919, 31.278886));
        fence3.add(GeoFenceUtils.createDPoint(120.746826, 31.278905));
        fence3.add(GeoFenceUtils.createDPoint(120.746844, 31.278736));
        fence3.add(GeoFenceUtils.createDPoint(120.745928, 31.278716));
        geoFencePointsList.add(fence3);
        geoFenceNames.add("SC");

        // SD楼 围栏边界点
        List<DPoint> fence4 = new ArrayList<>();
        fence4.add(GeoFenceUtils.createDPoint(120.745941, 31.278412));
        fence4.add(GeoFenceUtils.createDPoint(120.746857, 31.278439));
        fence4.add(GeoFenceUtils.createDPoint(120.745954,31.278171));
        fence4.add(GeoFenceUtils.createDPoint(120.746857,31.278194));
        geoFencePointsList.add(fence4);
        geoFenceNames.add("SD");

        // 下沉广场 围栏边界点
        List<DPoint> fence5 = new ArrayList<>();
        fence5.add(GeoFenceUtils.createDPoint(120.747149, 31.279291));
        fence5.add(GeoFenceUtils.createDPoint(120.747356, 31.279288));
        fence5.add(GeoFenceUtils.createDPoint(120.747194, 31.278586));
        fence5.add(GeoFenceUtils.createDPoint(120.747405, 31.278624));
        geoFencePointsList.add(fence5);
        geoFenceNames.add("DownStairS");

        // PB楼 围栏边界点
        List<DPoint> fence6 = new ArrayList<>();
        fence6.add(GeoFenceUtils.createDPoint(120.747032, 31.279785));
        fence6.add(GeoFenceUtils.createDPoint(120.747841, 31.279793));
        fence6.add(GeoFenceUtils.createDPoint(120.747854, 31.279315));
        fence6.add(GeoFenceUtils.createDPoint(120.747414, 31.279303));
        fence6.add(GeoFenceUtils.createDPoint(120.747396, 31.279589));
        fence6.add(GeoFenceUtils.createDPoint(120.747391, 31.279581));
        geoFencePointsList.add(fence6);
        geoFenceNames.add("PB");

        // MA楼 围栏边界点
        List<DPoint> fence7 = new ArrayList<>();
        fence7.add(GeoFenceUtils.createDPoint(120.747998, 31.279813));
        fence7.add(GeoFenceUtils.createDPoint(120.748586, 31.279824));
        fence7.add(GeoFenceUtils.createDPoint(120.7486, 31.279631));
        fence7.add(GeoFenceUtils.createDPoint(120.748559, 31.279435));
        fence7.add(GeoFenceUtils.createDPoint(120.74829, 31.279423));
        fence7.add(GeoFenceUtils.createDPoint(120.748254, 31.27962));
        fence7.add(GeoFenceUtils.createDPoint(120.748011, 31.279616));
        geoFencePointsList.add(fence7);
        geoFenceNames.add("MA");

        // MB楼 围栏边界点
        List<DPoint> fence8 = new ArrayList<>();
        fence8.add(GeoFenceUtils.createDPoint(120.748025, 31.279361));
        fence8.add(GeoFenceUtils.createDPoint(120.74895, 31.279384));
        fence8.add(GeoFenceUtils.createDPoint(120.748959, 31.279218));
        fence8.add(GeoFenceUtils.createDPoint(120.748043, 31.279191));
        geoFencePointsList.add(fence8);
        geoFenceNames.add("MB");

        // EB楼 围栏边界点
        List<DPoint> fence9 = new ArrayList<>();
        fence9.add(GeoFenceUtils.createDPoint(120.748227, 31.278936));
        fence9.add(GeoFenceUtils.createDPoint(120.749165, 31.278963));
        fence9.add(GeoFenceUtils.createDPoint(120.749201, 31.278296));
        fence9.add(GeoFenceUtils.createDPoint(120.748276, 31.278269));
        fence9.add(GeoFenceUtils.createDPoint(120.748128, 31.278373));
        fence9.add(GeoFenceUtils.createDPoint(120.748442, 31.278427));
        fence9.add(GeoFenceUtils.createDPoint(120.74824, 31.278751));
        geoFencePointsList.add(fence9);
        geoFenceNames.add("EB");

        // EE楼 围栏边界点
        List<DPoint> fence10 = new ArrayList<>();
        fence10.add(GeoFenceUtils.createDPoint(120.747207, 31.278423));
        fence10.add(GeoFenceUtils.createDPoint(120.747589, 31.278477));
        fence10.add(GeoFenceUtils.createDPoint(120.74758, 31.278651));
        fence10.add(GeoFenceUtils.createDPoint(120.74758, 31.278651));
        fence10.add(GeoFenceUtils.createDPoint(120.747796, 31.278485));
        fence10.add(GeoFenceUtils.createDPoint(120.747935, 31.278477));
        fence10.add(GeoFenceUtils.createDPoint(120.748061, 31.278361));
        fence10.add(GeoFenceUtils.createDPoint(120.747976, 31.278273));
        fence10.add(GeoFenceUtils.createDPoint(120.747212, 31.278257));
        geoFencePointsList.add(fence10);
        geoFenceNames.add("EE");

        // FB楼 围栏边界点
        List<DPoint> fence11 = new ArrayList<>();
        fence11.add(GeoFenceUtils.createDPoint(120.743664,31.281205));
        fence11.add(GeoFenceUtils.createDPoint(120.745519,31.281239));
        fence11.add(GeoFenceUtils.createDPoint(120.745546,31.280765));
        fence11.add(GeoFenceUtils.createDPoint(120.744764,31.280464));
        fence11.add(GeoFenceUtils.createDPoint(120.744517,31.280456));
        fence11.add(GeoFenceUtils.createDPoint(120.743704,31.280641));
        geoFencePointsList.add(fence11);
        geoFenceNames.add("FB");

        // CB楼 围栏边界点
        List<DPoint> fence12 = new ArrayList<>();
        fence12.add(GeoFenceUtils.createDPoint(120.743821,31.279738));
        fence12.add(GeoFenceUtils.createDPoint(120.745343,31.279792));
        fence12.add(GeoFenceUtils.createDPoint(120.745343,31.278662));
        fence12.add(GeoFenceUtils.createDPoint(120.744131,31.278635));
        geoFencePointsList.add(fence12);
        geoFenceNames.add("CB");

        // 遍历每个围栏的边界点，调用 addGeoFence 方法逐个创建围栏
        for (int i = 0; i < geoFencePointsList.size(); i++) {
            List<DPoint> points = geoFencePointsList.get(i);
            String customId = geoFenceNames.get(i); // 使用实际的名称

            try {
                mGeoFenceClient.addGeoFence((ArrayList<DPoint>) points, GeoFenceClient.BD09LL, customId);
                Log.i("GeoFence", "尝试添加地理围栏，ID: " + customId);
            } catch (Exception e) {
                Log.e("GeoFence", "添加地理围栏时发生异常，ID: " + customId + "，异常：" + e.getMessage());
            }
        }

        // 设置围栏创建监听器
        GeoFenceListener fenceListener = new GeoFenceListener() {
            @Override
            public void onGeoFenceCreateFinished(List<GeoFence> geoFenceList, int errorCode, String customId) {
                if (errorCode == GeoFence.ADDGEOFENCE_SUCCESS) {
                    Log.i("GeoFence", "多边形围栏添加成功，ID: " + customId);
                } else {
                    Log.e("GeoFence", "添加围栏失败，ID: " + customId + "，错误代码：" + errorCode);
                }
            }
        };
        mGeoFenceClient.setGeoFenceListener(fenceListener);
    }
    // 使用坐标距离计算代替圆形围栏
    public void createCircularFence(String customId, double latitude, double longitude, int radius) {
        // 记录这个自定义ID对应的位置和半径，供后续判断使用
        try {
            // 将位置添加到虚拟围栏列表中
            Log.i("GeoFence", "虚拟围栏位置记录成功: " + customId + " (经度:" + longitude + ", 纬度:" + latitude + ", 半径:" + radius + "m)");
            // 记录这个位置信息，供后续使用
            storeVirtualFence(customId, latitude, longitude, radius);
        } catch (Exception e) {
            Log.e("GeoFence", "围栏位置记录失败", e);
        }
    }
    // 存储虚拟围栏信息
    private Map<String, VirtualFence> virtualFences = new HashMap<>();
    private void storeVirtualFence(String id, double latitude, double longitude, int radius) {
        virtualFences.put(id, new VirtualFence(latitude, longitude, radius));
    }
    // 虚拟围栏内部类
    private static class VirtualFence {
        public final double latitude;
        public final double longitude;
        public final int radius;

        public VirtualFence(double latitude, double longitude, int radius) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;
        }
    }
    /**
     * 检查用户是否在指定ID的虚拟围栏内
     * @param id 围栏ID
     * @param userLatitude 用户当前纬度
     * @param userLongitude 用户当前经度
     * @return 是否在围栏内
     */
    public boolean isUserInVirtualFence(String id, double userLatitude, double userLongitude) {
        VirtualFence fence = virtualFences.get(id);
        if (fence == null) return false;

        // 计算距离(米)
        double distance = calculateDistance(
                userLatitude, userLongitude,
                fence.latitude, fence.longitude);

        // 判断是否在半径范围内
        boolean isInFence = distance <= fence.radius;

        if (isInFence) {
            Log.i("GeoFence", "用户在围栏 " + id + " 内(距离:" + distance + "m, 半径:" + fence.radius + "m)");
            if (onGeoFenceStatusListener != null) {
                onGeoFenceStatusListener.onUserEnterFence(id);
            }
        }

        return isInFence;
    }
    /**
     * 计算两个经纬度点之间的距离(米)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半径(公里)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // 转为米

        return distance;
    }




    // 处理围栏广播
    // 在handleGeoFenceBroadcast中进行回调
    public void handleGeoFenceBroadcast(Context context, Intent intent) {
        if (intent.getAction().equals(GEOFENCE_BROADCAST_ACTION)) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                int status = bundle.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS);
                String customId = bundle.getString(GeoFence.BUNDLE_KEY_CUSTOMID);
                switch (status) {
                    case GeoFence.STATUS_IN:
                        Log.i("GeoFence", "进入围栏：" + customId);
                        if (onGeoFenceStatusListener != null) {
                            onGeoFenceStatusListener.onUserEnterFence(customId);
                        }
                        // 记录用户进入位置
                        locationStayManager.onUserEnterLocation(customId);
                        break;
                    case GeoFence.STATUS_OUT:
                        Log.i("GeoFence", "退出围栏：" + customId);
                        if (onGeoFenceStatusListener != null) {
                            onGeoFenceStatusListener.onUserLeaveFence(customId);
                        }
                        // 记录用户离开位置
                        locationStayManager.onUserLeaveLocation(customId);
                        break;
                    case GeoFence.STATUS_STAYED:
                        Log.i("GeoFence", "停留在围栏内：" + customId);
                        if (onGeoFenceStatusListener != null) {
                            onGeoFenceStatusListener.onUserEnterFence(customId);
                        }
                        // 停留事件我们也视为进入事件
                        locationStayManager.onUserEnterLocation(customId);
                        break;
                }
            }
        }
    }


}
