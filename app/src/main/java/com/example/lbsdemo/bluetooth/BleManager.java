package com.example.lbsdemo.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

// 蓝牙接收端代码
public class BleManager {
    private static final String TAG = "BLEManager";

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private OnBleIdReceivedListener listener;

    // 自定义 Service UUID 为0000FFFE-0000-1000-8000-00805F9B34FB 作为过滤器
    private static final UUID SERVICE_UUID = UUID.fromString("0000FFFE-0000-1000-8000-00805F9B34FB");

    public BleManager(Context context, OnBleIdReceivedListener listener) {
        this.context = context;
        this.listener = listener;
        checkBluetoothSupport();
    }

    // 回调结果
    public interface OnBleIdReceivedListener {
        void onBleIdReceived(String bleId); // 接收到目标蓝牙ID时调用
        void onBleScanStatusUpdate(String status, boolean enableButton); // 更新UI状态
    }

    // 检查蓝牙以及BLE广播权限
    private boolean checkBluetoothSupport() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "无法获取 BluetoothManager");
            return false;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙");
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "蓝牙未开启");
            return false;
        }

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "设备不支持BLE");
            return false;
        }

        return true;
    }

    // 开始扫描BLE！
    public void startScanning() {
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "无法获取 BluetoothLeScanner");
            if (listener != null) {
                listener.onBleScanStatusUpdate("无法启动扫描", true);
            }
            return;
        }

        if (listener != null) {
            listener.onBleScanStatusUpdate("正在搜索中...", false);
        }

        // 配置过滤器，仅接收特定 Service UUID 的广播
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID)) // 过滤特定 Service UUID
                .build();

        // 设置扫描模式，这里设置为低延迟
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        /*
           启动蓝牙扫描
           它会获取设备的广播数据并停止扫描，接收到有效数据时，触发回调函数
           解析数据并通过监听器传递出去
           @@@重要函数@@@
        */
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    // 获取服务数据
                    byte[] serviceData = scanRecord.getServiceData(new ParcelUuid(SERVICE_UUID));
                    if (serviceData != null && serviceData.length > 0) {
                        String receivedId = new String(serviceData, StandardCharsets.UTF_8);
                        Log.i(TAG, "接收到广播数据: " + receivedId);

                        // 比较接收的ID
                        if (listener != null) {
                            listener.onBleIdReceived(receivedId);
                            listener.onBleScanStatusUpdate("接收到设备广播ID: " + receivedId, true);
                        }

                        stopScanning(); // 停止扫描
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "BLE 扫描失败，错误码：" + errorCode);
                if (listener != null) {
                    listener.onBleScanStatusUpdate("扫描失败，请重试", true);
                }
            }
        };

        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "缺少 BLUETOOTH_SCAN 权限，无法开始扫描");
                if (listener != null) {
                    listener.onBleScanStatusUpdate("缺少 BLUETOOTH_SCAN 权限，无法开始扫描", true);
                }
                return;
            }
        } else {
            // 对于 Android 10 及以下，检查位置权限
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "缺少位置权限，无法开始扫描");
                if (listener != null) {
                    listener.onBleScanStatusUpdate("缺少位置权限，无法开始扫描", true);
                }
                return;
            }
        }

        // 开始扫描
        bluetoothLeScanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        Log.d(TAG, "开始 BLE 扫描");
    }

    // 停止扫描
    public void stopScanning() {
        if (bluetoothLeScanner != null && scanCallback != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d(TAG, "停止 BLE 扫描");
        }
    }

    // 清理资源
    public void onDestroy() {
        stopScanning();
    }
}
