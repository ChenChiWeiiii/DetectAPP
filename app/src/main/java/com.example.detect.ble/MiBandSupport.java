package com.example.detect.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.util.UUID;

public class MiBandSupport extends AbstractSupport {

    private static final String TAG = "MiBandSupport";

    // 小米手環震動 UUID（這是根據 Gadgetbridge 取得的，可能因型號需調整）
    private static final UUID SERVICE_UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb");
    private static final UUID VIBRATION_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    public MiBandSupport(BluetoothGatt gatt) {
        super(gatt);
    }

    public void vibrate() {
        BluetoothGattCharacteristic vibrationChar = BluetoothGattCharacteristicHelper.getCharacteristic(bluetoothGatt, SERVICE_UUID, VIBRATION_UUID);
        if (vibrationChar != null) {
            boolean success = writeCharacteristic(vibrationChar, new byte[]{0x01});
            Log.d(TAG, "嘗試震動小米手環: " + success);
        } else {
            Log.w(TAG, "找不到震動服務或特徵值");
        }
    }
}
