package com.example.lbsdemo.utils;

import com.baidu.geofence.model.DPoint;

public class GeoFenceUtils {

//经纬度调换位置工具，因懒得手动在地理拾取系统上调换经纬度位置。
    public static DPoint createDPoint(double longitude, double latitude) {
        return new DPoint(latitude, longitude); // 将纬度作为第一个参数，经度作为第二个参数
    }
}
