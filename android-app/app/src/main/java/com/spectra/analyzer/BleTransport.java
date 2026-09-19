package com.spectra.analyzer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

final class BleTransport {
    static final UUID SERVICE_UUID = UUID.fromString("7a240001-8e7c-4f31-9a62-6d4f53503234");
    private static final UUID STATUS_UUID = UUID.fromString("7a240002-8e7c-4f31-9a62-6d4f53503234");
    private static final UUID CONTROL_UUID = UUID.fromString("7a240003-8e7c-4f31-9a62-6d4f53503234");
    private static final UUID SPECTRUM_UUID = UUID.fromString("7a240004-8e7c-4f31-9a62-6d4f53503234");
    private static final UUID EVENT_UUID = UUID.fromString("7a240005-8e7c-4f31-9a62-6d4f53503234");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    interface Callback {
        void onBleState(String state, boolean connected);
        void onBleDevice(String name, String address, int rssi);
        void onBleJson(String json);
        void onBleSpectrum(byte[] packet);
    }

    private final Context context;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> pendingCommands = new ArrayDeque<>();
    private final Queue<BluetoothGattDescriptor> descriptorQueue = new ArrayDeque<>();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic controlCharacteristic;
    private BluetoothGattCharacteristic statusCharacteristic;
    private boolean stopped;
    private boolean scanning;
    private boolean autoConnect;
    private boolean subscriptionStarted;
    private boolean ready;
    private boolean writeInFlight;
    private int writeRetries;
    private String preferredAddress = "";
    private final WireProtocol.FragmentAssembler jsonAssembler = new WireProtocol.FragmentAssembler((byte) 'J');
    private final WireProtocol.FragmentAssembler spectrumAssembler = new WireProtocol.FragmentAssembler((byte) 'S');

    BleTransport(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        BluetoothManager manager = this.context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
    }

    boolean isSupported() { return adapter != null; }
    boolean isEnabled() { return adapter != null && adapter.isEnabled(); }
    boolean isConnected() { return gatt != null && controlCharacteristic != null && ready; }

    boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    void scan(boolean connectFirst, String savedAddress) {
        stopped = false;
        if (isConnected()) return;
        if (!hasPermissions()) {
            callback.onBleState("Bluetooth izni gerekli", false);
            return;
        }
        if (!isEnabled()) {
            callback.onBleState("Bluetooth kapalı", false);
            return;
        }
        stopScan();
        autoConnect = connectFirst;
        preferredAddress = savedAddress == null ? "" : savedAddress;
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            callback.onBleState("BLE tarayıcı kullanılamıyor", false);
            return;
        }
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build());
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        try {
            scanner.startScan(filters, settings, scanCallback);
            scanning = true;
            callback.onBleState("Yakındaki SPECTRA cihazları aranıyor", false);
            handler.postDelayed(this::stopScan, 12000);
        } catch (SecurityException exception) {
            callback.onBleState("Bluetooth izni reddedildi", false);
        }
    }

    void connect(String address) {
        stopped = false;
        if (!hasPermissions() || adapter == null || address == null || address.isEmpty()) return;
        stopScan();
        closeGatt();
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            callback.onBleState(device.getName() + " bağlanıyor", false);
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (IllegalArgumentException | SecurityException exception) {
            callback.onBleState("BLE adresi geçersiz veya izin yok", false);
        }
    }

    synchronized void send(String json) {
        if (json == null || json.isEmpty()) return;
        if (pendingCommands.size() >= 20) pendingCommands.poll();
        pendingCommands.offer(json);
        drainWriteQueue();
    }

    void stop() {
        stopped = true;
        autoConnect = false;
        handler.removeCallbacksAndMessages(null);
        stopScan();
        pendingCommands.clear();
        descriptorQueue.clear();
        closeGatt();
    }

    void pauseDiscovery() {
        autoConnect = false;
        stopScan(); // Keep an established GATT link as the fallback.
    }

    private void stopScan() {
        if (scanning && scanner != null && hasPermissions()) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) {}
        }
        scanning = false;
    }

    private void closeGatt() {
        BluetoothGatt old = gatt;
        gatt = null;
        controlCharacteristic = null;
        statusCharacteristic = null;
        subscriptionStarted = false;
        ready = false;
        writeInFlight = false;
        writeRetries = 0;
        if (old != null && hasPermissions()) {
            try { old.disconnect(); old.close(); } catch (SecurityException ignored) {}
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = "SPECTRA-24";
            try {
                if (device.getName() != null && !device.getName().isEmpty()) name = device.getName();
            } catch (SecurityException ignored) {}
            String address = device.getAddress();
            callback.onBleDevice(name, address, result.getRssi());
            if (autoConnect && gatt == null) {
                connect(address);
            }
        }

        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            callback.onBleState("BLE tarama hatası: " + errorCode, false);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
            if (stopped || source != gatt) return;
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                callback.onBleState("BLE bağlı · servisler hazırlanıyor", false);
                try { source.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                controlCharacteristic = null;
                statusCharacteristic = null;
                subscriptionStarted = false;
                ready = false;
                writeInFlight = false;
                closeGatt();
                callback.onBleState("BLE bağlantısı kesildi", false);
                if(autoConnect) handler.postDelayed(() -> { if(!stopped) scan(true,preferredAddress); },1500);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                callback.onBleState("BLE servisleri okunamadı", false);
                return;
            }
            BluetoothGattService service = source.getService(SERVICE_UUID);
            if (service == null) {
                callback.onBleState("SPECTRA BLE servisi bulunamadı", false);
                return;
            }
            controlCharacteristic = service.getCharacteristic(CONTROL_UUID);
            statusCharacteristic = service.getCharacteristic(STATUS_UUID);
            try {
                source.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                if (!source.requestMtu(247)) enableNotifications(source, service);
                else handler.postDelayed(() -> enableNotifications(source, service), 1800);
            } catch (SecurityException exception) {
                callback.onBleState("BLE bağlantı izni kaybedildi", false);
            }
        }

        @Override public void onMtuChanged(BluetoothGatt source, int mtu, int status) {
            BluetoothGattService service = source.getService(SERVICE_UUID);
            if (service != null) enableNotifications(source, service);
        }

        @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
            if (source != gatt || stopped) return;
            if (status != BluetoothGatt.GATT_SUCCESS) { callback.onBleState("BLE bildirim kaydı başarısız",false); closeGatt(); return; }
            writeNextDescriptor(source);
        }

        @Override public void onCharacteristicWrite(BluetoothGatt source,
                                                     BluetoothGattCharacteristic characteristic,
                                                     int status) {
            synchronized (BleTransport.this) {
                writeInFlight = false;
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingCommands.poll();
                    writeRetries = 0;
                } else if (++writeRetries >= 3) {
                    pendingCommands.poll();
                    writeRetries = 0;
                    callback.onBleState("BLE komutu gönderilemedi", true);
                }
                handler.postDelayed(BleTransport.this::drainWriteQueue,
                        status == BluetoothGatt.GATT_SUCCESS ? 15 : 180);
            }
        }

        @Override public void onCharacteristicRead(BluetoothGatt source,
                                                    BluetoothGattCharacteristic characteristic,
                                                    byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && STATUS_UUID.equals(characteristic.getUuid())) {
                callback.onBleJson(new String(value, StandardCharsets.UTF_8));
            }
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicRead(BluetoothGatt source,
                                                    BluetoothGattCharacteristic characteristic,
                                                    int status) {
            if (Build.VERSION.SDK_INT < 33 && status == BluetoothGatt.GATT_SUCCESS
                    && STATUS_UUID.equals(characteristic.getUuid())) {
                callback.onBleJson(new String(characteristic.getValue(), StandardCharsets.UTF_8));
            }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt source,
                                                       BluetoothGattCharacteristic characteristic,
                                                       byte[] value) {
            consumeNotification(characteristic.getUuid(), value);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt source,
                                                       BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT < 33) consumeNotification(characteristic.getUuid(), characteristic.getValue());
        }
    };

    private synchronized void enableNotifications(BluetoothGatt source, BluetoothGattService service) {
        if (subscriptionStarted || source != gatt) return;
        subscriptionStarted = true;
        descriptorQueue.clear();
        queueNotification(source, service.getCharacteristic(SPECTRUM_UUID));
        queueNotification(source, service.getCharacteristic(EVENT_UUID));
        queueNotification(source, service.getCharacteristic(STATUS_UUID));
        writeNextDescriptor(source);
    }

    private void queueNotification(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) return;
        try {
            source.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor != null) descriptorQueue.offer(descriptor);
        } catch (SecurityException ignored) {}
    }

    @SuppressWarnings("deprecation")
    private synchronized void writeNextDescriptor(BluetoothGatt source) {
        BluetoothGattDescriptor descriptor = descriptorQueue.poll();
        if (descriptor == null) {
            ready = true;
            callback.onBleState("BLE canlı ölçüm hazır", true);
            pendingCommands.offerFirst("{\"action\":\"hello\"}");
            drainWriteQueue();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                source.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                source.writeDescriptor(descriptor);
            }
        } catch (SecurityException exception) {
            callback.onBleState("BLE bildirim izni yok", false);
        }
    }

    @SuppressWarnings("deprecation")
    private synchronized void drainWriteQueue() {
        if (stopped || !ready || writeInFlight || pendingCommands.isEmpty()) return;
        BluetoothGatt currentGatt = gatt;
        BluetoothGattCharacteristic characteristic = controlCharacteristic;
        if (currentGatt == null || characteristic == null || !hasPermissions()) return;
        String json = pendingCommands.peek();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try {
            boolean started;
            if (Build.VERSION.SDK_INT >= 33) {
                started = currentGatt.writeCharacteristic(characteristic, bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS;
            } else {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                characteristic.setValue(bytes);
                started = currentGatt.writeCharacteristic(characteristic);
            }
            writeInFlight = started;
            if (!started) handler.postDelayed(this::drainWriteQueue, 180);
        } catch (SecurityException exception) {
            handler.postDelayed(this::drainWriteQueue, 300);
        }
    }

    private void consumeNotification(UUID uuid, byte[] value) {
        if (value == null || value.length == 0) return;
        if (STATUS_UUID.equals(uuid)) {
            callback.onBleJson(new String(value, StandardCharsets.UTF_8));
        } else if (EVENT_UUID.equals(uuid)) {
            byte[] complete = jsonAssembler.add(value);
            if (complete != null) callback.onBleJson(new String(complete, StandardCharsets.UTF_8));
        } else if (SPECTRUM_UUID.equals(uuid)) {
            byte[] complete = spectrumAssembler.add(value);
            if (complete != null) callback.onBleSpectrum(complete);
        }
    }

}
