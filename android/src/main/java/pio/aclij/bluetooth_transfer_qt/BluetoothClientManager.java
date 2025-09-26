package pio.aclij.bluetooth_transfer_qt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothClientManager {
    private static final String TAG = "BluetoothClientManager";
    private static final String DEFAULT_SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final int DEFAULT_SCAN_TIMEOUT = 15000;
    private static final int CONNECTION_TIMEOUT = 10000;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConnectionManager connectionManager;
    private final MessageHandler messageHandler;
    private final TransferManager transferManager;
    private final BluetoothTransferQtPlugin plugin;

    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private BroadcastReceiver discoveryReceiver;
    private final List<Map<String, Object>> discoveredDevices = new ArrayList<>();

    public interface ClientListener {
        void onDeviceDiscovered(Map<String, Object> device);
        void onScanCompleted(List<Map<String, Object>> devices);
        void onScanFailed(String error);
        void onConnected(String deviceAddress);
        void onDisconnected(String deviceAddress);
        void onConnectionFailed(String deviceAddress, String error);
    }

    private ClientListener listener;

    public BluetoothClientManager(Context context, BluetoothAdapter bluetoothAdapter,
                                ExecutorService executor, ConnectionManager connectionManager,
                                MessageHandler messageHandler, TransferManager transferManager,
                                BluetoothTransferQtPlugin plugin) {
        this.context = context;
        this.bluetoothAdapter = bluetoothAdapter;
        this.executor = executor;
        this.connectionManager = connectionManager;
        this.messageHandler = messageHandler;
        this.transferManager = transferManager;
        this.plugin = plugin;
    }

    public void setClientListener(ClientListener listener) {
        this.listener = listener;
    }

    public boolean startScan(Integer timeoutMs) {
        if (isScanning.get()) {
            Log.w(TAG, "Scan already in progress");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Log.e(TAG, "Bluetooth adapter is null or not enabled");
                notifyScanFailed("Bluetooth not available or not enabled");
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Bluetooth scan permission not granted (Android 12+)");
                notifyScanFailed("Bluetooth scan permission not granted");
                return false;
            }
        } else {
            boolean hasBt = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
            boolean hasAdmin = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
                == PackageManager.PERMISSION_GRANTED;
            boolean hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
            boolean hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
            if (!hasBt || !hasAdmin || !(hasFine || hasCoarse)) {
                Log.e(TAG, "Bluetooth permissions not granted (Pre-Android 12)");
                notifyScanFailed("Bluetooth permissions not granted");
                return false;
            }
        }

        discoveredDevices.clear();
        isScanning.set(true);

        try {
            // Only discover new devices during scan, not paired ones
            // Paired devices should be retrieved separately via getBondedDevices()
            setupDiscoveryReceiver();
            boolean started = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                started = bluetoothAdapter.startDiscovery();
            }

            if (!started) {
                Log.e(TAG, "Failed to start discovery");
                isScanning.set(false);
                notifyScanFailed("Failed to start discovery");
                return false;
            }

            int timeout = timeoutMs != null ? timeoutMs : DEFAULT_SCAN_TIMEOUT;
            mainHandler.postDelayed(this::stopScan, timeout);

            Log.d(TAG, "Started Bluetooth scan with timeout: " + timeout + "ms");
            Log.d(TAG, "Discovered devices cleared, starting with " + discoveredDevices.size() + " devices");
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during scan", e);
            isScanning.set(false);
            notifyScanFailed("Security exception: " + e.getMessage());
            return false;
        }
    }

    public void stopScan() {
        if (!isScanning.get()) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }

            if (discoveryReceiver != null) {
                try {
                    context.unregisterReceiver(discoveryReceiver);
                } catch (IllegalArgumentException e) {
                }
                discoveryReceiver = null;
            }

            isScanning.set(false);
            Log.d(TAG, "Stopped Bluetooth scan");
            notifyScanCompleted();

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception stopping scan", e);
        }
    }

    public CompletableFuture<Boolean> connectToDevice(String deviceAddress) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (deviceAddress == null || deviceAddress.isEmpty()) {
            Log.e(TAG, "Device address is null or empty");
            future.complete(false);
            return future;
        }

        if (connectionManager.isConnected(deviceAddress)) {
            Log.d(TAG, "Already connected to device: " + deviceAddress);
            future.complete(true);
            return future;
        }

        executor.submit(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                    try {
                        if (bluetoothAdapter.isDiscovering()) {
                            bluetoothAdapter.cancelDiscovery();
                        }
                    } catch (SecurityException ignored) {
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                        throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
                    }
                } else {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                        != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
                        != PackageManager.PERMISSION_GRANTED) {
                        throw new SecurityException("Bluetooth permissions not granted");
                    }
                }

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                BluetoothSocket socket = createSocket(device);
                
                Log.d(TAG, "Attempting to connect to device: " + deviceAddress);
                
                CompletableFuture<Void> connectTask = CompletableFuture.runAsync(() -> {
                    try {
                        socket.connect();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                try {
                    connectTask.get(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    socket.close();
                    throw new IOException("Connection timeout", e);
                }

                if (socket.isConnected()) {
                    connectionManager.addConnection(deviceAddress, socket, new ConnectionManager.ConnectionListener() {
                        @Override
                        public void onConnected(String deviceAddress) {
                            Log.d(TAG, "Successfully connected to device: " + deviceAddress);
                            notifyConnected(deviceAddress);
                        }

                        @Override
                        public void onDisconnected(String deviceAddress) {
                            Log.d(TAG, "Disconnected from device: " + deviceAddress);
                            notifyDisconnected(deviceAddress);
                        }

                        @Override
                        public void onConnectionFailed(String deviceAddress, String error) {
                            Log.e(TAG, "Connection failed to device: " + deviceAddress + ", error: " + error);
                            notifyConnectionFailed(deviceAddress, error);
                        }

                        @Override
                        public void onMessageReceived(String deviceAddress, byte[] data) {
                            plugin.sendRawDataEvent(deviceAddress, data);
                            
                            messageHandler.processIncomingData(deviceAddress, data, new MessageHandler.MessageListener() {
                                @Override
                                public void onMessageProcessed(String deviceAddress, Map<String, Object> message) {
                                    handleProcessedMessage(deviceAddress, message);
                                }

                                @Override
                                public void onError(String deviceAddress, String error) {
                                    Log.e(TAG, "Message processing error for " + deviceAddress + ": " + error);
                                }
                            });
                        }

                        @Override
                        public void onError(String deviceAddress, String error) {
                            Log.e(TAG, "Connection error with device: " + deviceAddress + ", error: " + error);
                        }
                    });

                    future.complete(true);
                } else {
                    socket.close();
                    future.complete(false);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to device: " + deviceAddress, e);
                notifyConnectionFailed(deviceAddress, e.getMessage());
                future.complete(false);
            }
        });

        return future;
    }

    public boolean disconnectFromDevice(String deviceAddress) {
        return connectionManager.removeConnection(deviceAddress);
    }

    public boolean sendMessage(String deviceAddress, Map<String, Object> message) {
        byte[] data = messageHandler.processOutgoingMessage(message);
        if (data != null) {
            return connectionManager.sendMessage(deviceAddress, data);
        }
        return false;
    }

    public String requestFile(String deviceAddress, String fileName, String savePath) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "file_request");
        request.put("content", fileName);
        request.put("timestamp", System.currentTimeMillis());

        if (sendMessage(deviceAddress, request)) {
            BluetoothSocket socket = connectionManager.getConnection(deviceAddress);
            if (socket != null) {
                return transferManager.startFileDownload(socket, fileName, savePath, 
                    new TransferManager.TransferProgressListener() {
                        @Override
                        public void onProgress(String transferId, String fileName, long totalBytes, long transferredBytes, double percentage) {
                            Log.d(TAG, "File download progress: " + fileName + " " + percentage + "%");
                        }

                        @Override
                        public void onCompleted(String transferId, String fileName, String filePath) {
                            Log.d(TAG, "File download completed: " + fileName);
                        }

                        @Override
                        public void onFailed(String transferId, String fileName, String error) {
                            Log.e(TAG, "File download failed: " + fileName + " - " + error);
                        }

                        @Override
                        public void onCancelled(String transferId, String fileName) {
                            Log.d(TAG, "File download cancelled: " + fileName);
                        }
                    });
            }
        }
        return null;
    }

    public void requestDeviceInfo(String deviceAddress) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "device_info_request");
        request.put("timestamp", System.currentTimeMillis());
        
        sendMessage(deviceAddress, request);
    }

    public boolean sendDeviceInfo(String deviceAddress, Map<String, Object> deviceInfo) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "device_info");
        message.putAll(deviceInfo);
        message.put("timestamp", System.currentTimeMillis());
        
        return sendMessage(deviceAddress, message);
    }

    public List<Map<String, Object>> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }

    public boolean isScanning() {
        return isScanning.get();
    }

    // This method is not used during scanning to avoid mixing paired and discovered devices
    // Paired devices should be retrieved separately via getBondedDevices()
    @SuppressWarnings("unused")
    private void addPairedDevices() {
        boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
        }
        if (!hasPermission) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d(TAG, "Found " + pairedDevices.size() + " paired devices");
        for (BluetoothDevice device : pairedDevices) {
            Map<String, Object> deviceMap = createDeviceMap(device);
            deviceMap.put("paired", true);
            discoveredDevices.add(deviceMap);
            Log.d(TAG, "Added paired device: " + deviceMap);
            // Don't send paired devices as discovered devices
            // They should be retrieved separately via getBondedDevices()
        }
    }

    private void setupDiscoveryReceiver() {
        discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    } else {
                        @SuppressWarnings("deprecation")
                        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        device = d;
                    }
                    if (device != null) {
                        Map<String, Object> deviceMap = createDeviceMap(device);
                        
                        boolean alreadyFound = discoveredDevices.stream()
                            .anyMatch(d -> device.getAddress().equals(d.get("address")));
                        
                        if (!alreadyFound) {
                            discoveredDevices.add(deviceMap);
                            Log.d(TAG, "Device discovered: " + deviceMap);
                            notifyDeviceDiscovered(deviceMap);
                        } else {
                            Log.d(TAG, "Device already found: " + device.getAddress());
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d(TAG, "Discovery finished");
                    stopScan();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);
    }

    private Map<String, Object> createDeviceMap(BluetoothDevice device) {
        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("address", device.getAddress());
        
        boolean namePermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            namePermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        } else {
            namePermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
        }
        if (namePermission) {
            deviceMap.put("name", device.getName());
        } else {
            deviceMap.put("name", null);
        }
        
        // Add device type
        deviceMap.put("type", "classic");
        
        // Add bond state
        int bondState = device.getBondState();
        String bondStateStr;
        switch (bondState) {
            case BluetoothDevice.BOND_BONDED:
                bondStateStr = "bonded";
                break;
            case BluetoothDevice.BOND_BONDING:
                bondStateStr = "bonding";
                break;
            default:
                bondStateStr = "none";
                break;
        }
        deviceMap.put("bondState", bondStateStr);
        
        // Add connection status
        deviceMap.put("isConnected", connectionManager.isConnected(device.getAddress()));
        
        return deviceMap;
    }

    private BluetoothSocket createSocket(BluetoothDevice device) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Bluetooth permission not granted");
            }
        }
        
        return device.createRfcommSocketToServiceRecord(UUID.fromString(DEFAULT_SERVICE_UUID));
    }

    private void handleProcessedMessage(String deviceAddress, Map<String, Object> message) {
        String messageType = (String) message.get("type");
        Log.d(TAG, "Received message from " + deviceAddress + " of type: " + messageType);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) message.get("metadata");
        if (metadata == null) {
            metadata = new HashMap<>();
            message.put("metadata", metadata);
        }
        metadata.put("senderAddress", deviceAddress);
        metadata.put("deviceAddress", deviceAddress);

        plugin.sendMessageReceivedEvent(message);

        switch (messageType) {
            case "device_info":
                handleDeviceInfoResponse(deviceAddress, message);
                break;
            case "heartbeat_ack":
                handleHeartbeatAck(deviceAddress, message);
                break;
            default:
                Log.d(TAG, "Received message: " + messageType + " from " + deviceAddress + " (forwarded to Flutter)");
                break;
        }
    }

    private void handleDeviceInfoResponse(String deviceAddress, Map<String, Object> message) {
        Log.d(TAG, "Received device info from " + deviceAddress + ": " + message);
    }

    private void handleHeartbeatAck(String deviceAddress, Map<String, Object> message) {
        Log.v(TAG, "Heartbeat acknowledgment from " + deviceAddress);
    }

    private void notifyDeviceDiscovered(Map<String, Object> device) {
        if (listener != null) {
            mainHandler.post(() -> listener.onDeviceDiscovered(device));
        }
    }

    private void notifyScanCompleted() {
        if (listener != null) {
            mainHandler.post(() -> listener.onScanCompleted(new ArrayList<>(discoveredDevices)));
        }
    }

    private void notifyScanFailed(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onScanFailed(error));
        }
    }

    private void notifyConnected(String deviceAddress) {
        if (listener != null) {
            mainHandler.post(() -> listener.onConnected(deviceAddress));
        }
    }

    private void notifyDisconnected(String deviceAddress) {
        if (listener != null) {
            mainHandler.post(() -> listener.onDisconnected(deviceAddress));
        }
    }

    private void notifyConnectionFailed(String deviceAddress, String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionFailed(deviceAddress, error));
        }
    }
}