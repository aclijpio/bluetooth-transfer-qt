package pio.aclij.bluetooth_transfer_qt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class BluetoothTransferQtPlugin implements FlutterPlugin, MethodCallHandler {
    private static final String TAG = "BluetoothTransferQt";
    private static final String CHANNEL_NAME = "bluetooth_transfer_qt";
    private static final String EVENT_CHANNEL_NAME = "bluetooth_transfer_qt/events";
    
    private static final String DEFAULT_SERVICE_NAME = "BluetoothTransferQt";
    private static final String DEFAULT_SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    
    private ExecutorService serverExecutor;
    private ScheduledExecutorService scheduledExecutor;
    private ConnectionManager connectionManager;
    private MessageHandler messageHandler;
    private TransferManager transferManager;
    private BluetoothServerManager serverManager;
    private BluetoothClientManager clientManager;
    
    private EventChannel.EventSink eventSink;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        serverExecutor = Executors.newCachedThreadPool();
        scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        connectionManager = new ConnectionManager(serverExecutor, scheduledExecutor, this);
        messageHandler = new MessageHandler(this);
        transferManager = new TransferManager(serverExecutor, this);
        serverManager = new BluetoothServerManager(context, bluetoothAdapter, serverExecutor, 
                                                 connectionManager, messageHandler, transferManager, this);
        clientManager = new BluetoothClientManager(context, bluetoothAdapter, serverExecutor,
                                                 connectionManager, messageHandler, transferManager, this);
        
        setupManagerListeners();
        
        methodChannel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);
        
        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), EVENT_CHANNEL_NAME);
        eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                eventSink = events;
            }

            @Override
            public void onCancel(Object arguments) {
                eventSink = null;
            }
        });
        
        Log.d(TAG, "Plugin initialized with all managers");
    }

    private void setupManagerListeners() {
        serverManager.setServerListener(new BluetoothServerManager.ServerListener() {
            @Override
            public void onServerStarted() {
                sendEvent("serverStarted", null);
            }

            @Override
            public void onServerStopped() {
                sendEvent("serverStopped", null);
            }

            @Override
            public void onClientConnected(String deviceAddress) {
                Log.d(TAG, "🔗 Server listener: client connected " + deviceAddress);
                sendConnectionEvent(deviceAddress, true);
            }

            @Override
            public void onClientDisconnected(String deviceAddress) {
                Log.d(TAG, "🔗 Server listener: client disconnected " + deviceAddress);
                sendConnectionEvent(deviceAddress, false);
            }

            @Override
            public void onError(String error) {
                sendErrorEvent("Server error: " + error);
            }
        });

        clientManager.setClientListener(new BluetoothClientManager.ClientListener() {
            @Override
            public void onDeviceDiscovered(Map<String, Object> device) {
                sendEvent("deviceDiscovered", device);
            }

            @Override
            public void onScanCompleted(List<Map<String, Object>> devices) {
                sendEvent("scanCompleted", devices);
            }

            @Override
            public void onScanFailed(String error) {
                sendErrorEvent("Scan failed: " + error);
            }

            @Override
            public void onConnected(String deviceAddress) {
                sendConnectionEvent(deviceAddress, true);
            }

            @Override
            public void onDisconnected(String deviceAddress) {
                sendConnectionEvent(deviceAddress, false);
            }

            @Override
            public void onConnectionFailed(String deviceAddress, String error) {
                sendErrorEvent("Connection failed to " + deviceAddress + ": " + error);
            }
        });
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        Log.d(TAG, "Method: " + call.method);
        
        try {
            switch (call.method) {
                case "getPlatformVersion":
                    result.success("Android " + Build.VERSION.RELEASE);
                    break;
                    
                case "isBluetoothEnabled":
                    result.success(isBluetoothEnabled());
                    break;
                    
                case "enableBluetooth":
                    result.success(enableBluetooth());
                    break;
                    
                case "setDeviceName":
                    String name = call.argument("name");
                    result.success(setDeviceName(name));
                    break;
                    
                case "startServer":
                    String serviceName = call.argument("serviceName");
                    String serviceUuid = call.argument("serviceUuid");
                    result.success(startServer(serviceName, serviceUuid));
                    break;
                    
                case "stopServer":
                    result.success(stopServer());
                    break;
                    
                case "requestPermissions":
                    requestPermissions(result);
                    break;
                    
                case "checkPermissions":
                    result.success(checkPermissions());
                    break;
                    
                case "scanForDevices":
                    Integer timeout = call.argument("timeout");
                    scanForDevices(timeout, result);
                    break;
                    
                case "stopScan":
                    stopScan();
                    result.success(true);
                    break;
                    
                case "connectToDevice":
                    String deviceAddress = call.argument("deviceAddress");
                    connectToDevice(deviceAddress, result);
                    break;
                    
                case "disconnectFromDevice":
                    String disconnectAddress = call.argument("deviceAddress");
                    result.success(disconnectFromDevice(disconnectAddress));
                    break;
                    
                case "sendMessage":
                    String messageDeviceAddress = call.argument("deviceAddress");
                    Map<String, Object> message = call.argument("message");
                    sendMessage(messageDeviceAddress, message, result);
                    break;
                    
                case "sendFile":
                    String fileDeviceAddress = call.argument("deviceAddress");
                    String filePath = call.argument("filePath");
                    sendFile(fileDeviceAddress, filePath, result);
                    break;
                    
                case "downloadFile":
                    String downloadDeviceAddress = call.argument("deviceAddress");
                    String fileName = call.argument("fileName");
                    String savePath = call.argument("savePath");
                    downloadFile(downloadDeviceAddress, fileName, savePath, result);
                    break;
                    
                case "sendDeviceInfo":
                    String infoDeviceAddress = call.argument("deviceAddress");
                    Map<String, Object> deviceInfo = call.argument("deviceInfo");
                    sendDeviceInfo(infoDeviceAddress, deviceInfo, result);
                    break;
                    
                case "requestDeviceInfo":
                    String requestDeviceAddress = call.argument("deviceAddress");
                    requestDeviceInfo(requestDeviceAddress, result);
                    break;
                    
                case "addMessageFilter":
                    String filterId = call.argument("filterId");
                    Map<String, Object> filterConfig = call.argument("filterConfig");
                    result.success(addMessageFilter(filterId, filterConfig));
                    break;
                    
                case "removeMessageFilter":
                    String removeFilterId = call.argument("filterId");
                    result.success(removeMessageFilter(removeFilterId));
                    break;
                    
                case "clearMessageFilters":
                    result.success(clearMessageFilters());
                    break;
                    
                case "getConnectedDevices":
                    result.success(getConnectedDevices());
                    break;
                    
                case "isDeviceConnected":
                    String checkDeviceAddress = call.argument("deviceAddress");
                    result.success(isDeviceConnected(checkDeviceAddress));
                    break;
                    
                case "cancelTransfer":
                    String transferId = call.argument("transferId");
                    result.success(cancelTransfer(transferId));
                    break;
                    
                case "getActiveTransfers":
                    result.success(getActiveTransfers());
                    break;
                    
                case "makeDiscoverable":
                    makeDiscoverable(result);
                    break;
                    
                case "isServerRunning":
                    isServerRunning(result);
                    break;
                    
                default:
      result.notImplemented();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in method call: " + call.method, e);
            result.error("BLUETOOTH_ERROR", e.getMessage(), null);
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        
        if (serverManager != null) {
            serverManager.stopServer();
        }
        
        if (clientManager != null) {
            clientManager.stopScan();
        }
        
        if (connectionManager != null) {
            connectionManager.cleanup();
        }
        
        if (transferManager != null) {
            transferManager.cleanup();
        }
        
        if (messageHandler != null) {
            messageHandler.clearFilters();
        }
        
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
        
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        
        Log.d(TAG, "Plugin cleaned up");
    }
    private boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private boolean enableBluetooth() {
        if (bluetoothAdapter == null) return false;
        return bluetoothAdapter.enable();
    }

    private boolean setDeviceName(String name) {
        if (bluetoothAdapter == null || name == null) return false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            return bluetoothAdapter.setName(name);
        }
        return false;
    }

    private boolean startServer(String serviceName, String serviceUuid) {
        return serverManager.startServer(serviceName, serviceUuid);
    }

    private boolean stopServer() {
        return serverManager.stopServer();
    }

    private void scanForDevices(Integer timeout, Result result) {
        serverExecutor.submit(() -> {
            boolean success = clientManager.startScan(timeout);
            if (!success) {
                result.error("SCAN_FAILED", "Failed to start device scan", null);
            } else {
                result.success(new ArrayList<>());
            }
        });
    }

    private void stopScan() {
        clientManager.stopScan();
    }

    private void connectToDevice(String deviceAddress, Result result) {
        clientManager.connectToDevice(deviceAddress).thenAccept(success -> {
            if (success) {
                result.success(true);
            } else {
                result.error("CONNECTION_FAILED", "Failed to connect to device: " + deviceAddress, null);
            }
        }).exceptionally(throwable -> {
            result.error("CONNECTION_ERROR", "Connection error: " + throwable.getMessage(), null);
            return null;
        });
    }

    private boolean disconnectFromDevice(String deviceAddress) {
        return connectionManager.removeConnection(deviceAddress);
    }

    private void sendMessage(String deviceAddress, Map<String, Object> message, Result result) {
        boolean success = clientManager.sendMessage(deviceAddress, message);
        result.success(success);
    }

    private void sendFile(String deviceAddress, String filePath, Result result) {
        String transferId = serverManager.sendFile(deviceAddress, filePath);
        if (transferId != null) {
            result.success(true);
        } else {
            result.error("TRANSFER_FAILED", "Failed to start file transfer", null);
        }
    }

    private void downloadFile(String deviceAddress, String fileName, String savePath, Result result) {
        String transferId = clientManager.requestFile(deviceAddress, fileName, savePath);
        if (transferId != null) {
            result.success(true);
        } else {
            result.error("DOWNLOAD_FAILED", "Failed to start file download", null);
        }
    }

    private void sendDeviceInfo(String deviceAddress, Map<String, Object> deviceInfo, Result result) {
        boolean success = clientManager.sendDeviceInfo(deviceAddress, deviceInfo);
        result.success(success);
    }

    private void requestDeviceInfo(String deviceAddress, Result result) {
        clientManager.requestDeviceInfo(deviceAddress);
        result.success(null);
    }

    private boolean addMessageFilter(String filterId, Map<String, Object> filterConfig) {
        return messageHandler.addFilter(filterId, filterConfig);
    }

    private boolean removeMessageFilter(String filterId) {
        return messageHandler.removeFilter(filterId);
    }

    private boolean clearMessageFilters() {
        messageHandler.clearFilters();
        return true;
    }

    private List<String> getConnectedDevices() {
        return List.of(connectionManager.getConnectedDevices());
    }

    private boolean isDeviceConnected(String deviceAddress) {
        return connectionManager.isConnected(deviceAddress);
    }

    private boolean cancelTransfer(String transferId) {
        return transferManager.cancelTransfer(transferId);
    }

    private List<String> getActiveTransfers() {
        return List.of(transferManager.getActiveTransferIds());
    }

    private void makeDiscoverable(Result result) {
        result.success(true);
        Log.d(TAG, "Device available for connections");
    }

    private void isServerRunning(Result result) {
        boolean running = serverManager.isRunning();
        Log.d(TAG, "Server running status: " + running);
        result.success(running);
    }

    public void sendEvent(String eventType, Object data) {
        if (eventSink != null) {
            Map<String, Object> event = new HashMap<>();
            event.put("type", eventType);
            event.put("data", data);
            
            mainHandler.post(() -> eventSink.success(event));
        }
    }

    public void sendConnectionEvent(String deviceAddress, boolean isConnected) {
        Log.d(TAG, "🔗 Sending connection event: " + deviceAddress + " -> " + isConnected);
        Map<String, Object> data = new HashMap<>();
        data.put("deviceAddress", deviceAddress);
        data.put("isConnected", isConnected);
        sendEvent("connectionStateChanged", data);
    }

    public void sendTransferProgressEvent(String transferId, String fileName, long totalBytes, long transferredBytes) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("transferId", transferId);
        progress.put("fileName", fileName);
        progress.put("totalBytes", totalBytes);
        progress.put("transferredBytes", transferredBytes);
        progress.put("percentage", totalBytes > 0 ? (double) transferredBytes / totalBytes * 100 : 0);
        progress.put("status", transferredBytes >= totalBytes ? "completed" : "inProgress");
        progress.put("timestamp", System.currentTimeMillis());
        
        sendEvent("transferProgress", progress);
    }

    public void sendMessageReceivedEvent(Map<String, Object> message) {
        sendEvent("messageReceived", message);
    }

    public void sendRawDataEvent(String deviceAddress, byte[] data) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("deviceAddress", deviceAddress);
        
        Integer[] dataArray = new Integer[data.length];
        for (int i = 0; i < data.length; i++) {
            dataArray[i] = data[i] & 0xFF;
        }
        eventData.put("data", java.util.Arrays.asList(dataArray));
        
        sendEvent("rawDataReceived", eventData);
    }

    public void sendErrorEvent(String error) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", error);
        data.put("timestamp", System.currentTimeMillis());
        sendEvent("error", data);
    }

    private void requestPermissions(Result result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            List<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            
            if (permissionsToRequest.isEmpty()) {
                result.success(true);
                return;
            }
            
            Log.w(TAG, "Permissions need to be requested by the main activity: " + permissionsToRequest);
            result.success(false);
        } else {
            String[] permissions = {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
            
            List<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            
            if (permissionsToRequest.isEmpty()) {
                result.success(true);
                return;
            }
            
            Log.w(TAG, "Permissions need to be requested by the main activity: " + permissionsToRequest);
            result.success(false);
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
}