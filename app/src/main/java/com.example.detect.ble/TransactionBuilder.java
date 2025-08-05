package com.example.detect.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.ArrayList;
import java.util.List;

public class TransactionBuilder {
    private final BluetoothGatt gatt;
    private final List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();

    public TransactionBuilder(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    public void write(BluetoothGattCharacteristic characteristic, byte[] value) {
        characteristic.setValue(value);
        gatt.writeCharacteristic(characteristic);
        characteristics.add(characteristic);
    }
}
