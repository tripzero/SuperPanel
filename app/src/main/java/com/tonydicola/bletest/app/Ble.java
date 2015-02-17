package com.tonydicola.bletest.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

interface BleListener
{
    void onBleMessage(String message);

    void onBleConnect();
    void onBleDisconnect();
}


public class Ble {

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BleListener iface;

    private static ParcelUuid superPanelUuid;
    private static UUID rxUUid;
    private static UUID txUUid;

    private static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGattCharacteristic rx = null;
    private BluetoothGattCharacteristic tx = null;
    private BluetoothGatt deviceGatt;

    private Context context;

    public Ble(Context c, BleListener i, String deviceUuid, String r, String t)
    {
        context = c;
        iface = i;
        superPanelUuid = ParcelUuid.fromString(deviceUuid);
        rxUUid = UUID.fromString(r);
        txUUid = UUID.fromString(t);

        adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter.getBluetoothLeScanner();

        ScanFilter.Builder filter = new ScanFilter.Builder();
        filter.setServiceUuid(superPanelUuid);

        ScanFilter.Builder superPanelFilter = new ScanFilter.Builder();
        superPanelFilter.setServiceUuid(superPanelUuid);

        ScanSettings.Builder settings = new ScanSettings.Builder();
        settings.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);

        List<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(filter.build());
        //filters.add(superPanelFilter.build());

        if(scanner == null)
        {
            System.out.println("Failed to get LE scanner");
            return;
        }
        scanner.startScan(filters, settings.build(), scanCallback);
    }

    public void sendMessage(String message)
    {
        if(deviceGatt == null)
            return;

        tx.setValue(message);
        deviceGatt.writeCharacteristic(tx);
    }


    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();

            System.out.println("I see a bluetooth device: " + device.getAddress());

            List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();

            if(uuids != null) {
                for (ParcelUuid uuid :uuids) {
                    System.out.println("Service UUID: " + uuid.getUuid());
                }
            }

            device.connectGatt(context, false, gattCallback);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            for(ScanResult result : results)
            {
                BluetoothDevice device = result.getDevice();

                System.out.println("I see a bluetooth device: " + device.getAddress());
            }
        }
    };

    private BluetoothGattCallback gattCallback= new BluetoothGattCallback() {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            String data = characteristic.getStringValue(0);
            System.out.println("received from device: " + data);
            iface.onBleMessage(data);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                deviceGatt = gatt;
                System.out.println("Connected!");
                gatt.discoverServices();
                iface.onBleConnect();
            }
            else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                deviceGatt = null;
                System.out.println("Disconnected");
                iface.onBleDisconnect();
            }

        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                System.out.println("Service discovery completed!");
            }
            else {
                System.out.println("Service discovery failed with status: " + status);
                return;
            }
            List<BluetoothGattCharacteristic> characteristics = gatt.getService(superPanelUuid.getUuid()).getCharacteristics();
            for(BluetoothGattCharacteristic c : characteristics)
            {
                System.out.println("characteristic: " + c.getUuid().toString());
            }

            rx = gatt.getService(superPanelUuid.getUuid()).getCharacteristic(rxUUid);
            tx = gatt.getService(superPanelUuid.getUuid()).getCharacteristic(txUUid);

            if (rx != null && !gatt.setCharacteristicNotification(rx, true)) {
                System.out.println("Couldn't set notifications for RX characteristic!");
            }
            // Next update the RX characteristic's client descriptor to enable notifications.
            if (rx != null && rx.getDescriptor(CLIENT_UUID) != null) {
                BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!gatt.writeDescriptor(desc)) {
                    System.out.println("Couldn't write RX client descriptor value!");
                }
            }
            else {
                System.out.println("Couldn't get RX client descriptor!");
            }
        }
    };

}
