package com.example.detect.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.UUID;

public class BluetoothGattCharacteristicHelper {

    public static BluetoothGattCharacteristic getCharacteristic(BluetoothGatt gatt, UUID serviceUUID, UUID charUUID) {
        if (gatt == null) return null;
        BluetoothGattService service = gatt.getService(serviceUUID);
        if (service == null) return null;
        return service.getCharacteristic(charUUID);
    }
}
