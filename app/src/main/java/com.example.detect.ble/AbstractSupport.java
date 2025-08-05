package com.example.detect.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public abstract class AbstractSupport {

    protected BluetoothGatt bluetoothGatt;

    public AbstractSupport(BluetoothGatt gatt) {
        this.bluetoothGatt = gatt;
    }

    protected boolean writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (characteristic == null || bluetoothGatt == null) return false;
        characteristic.setValue(value);
        return bluetoothGatt.writeCharacteristic(characteristic);
    }
}
