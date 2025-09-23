package pio.aclij.bluetooth_transfer_qt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.plugin.common.MethodChannel;

public class BluetoothServerManager {
    private static final String TAG = "BluetoothServerManager";
    private static final String DEFAULT_SERVICE_NAME = "TMC31BluetoothFileTransfer";
    private static final String DEFAULT_SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConnectionManager connectionManager;
    private final MessageHandler messageHandler;
    private final TransferManager transferManager;
    private BluetoothTransferQtPlugin plugin;

    private BluetoothServerSocket serverSocket;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private String serviceName = DEFAULT_SERVICE_NAME;
    private String serviceUuid = DEFAULT_SERVICE_UUID;

    public interface ServerListener {
        void onServerStarted();
        void onServerStopped();
        void onClientConnected(String deviceAddress);
        void onClientDisconnected(String deviceAddress);
        void onError(String error);
    }

    private ServerListener listener;

    public BluetoothServerManager(Context context, BluetoothAdapter bluetoothAdapter, 
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

    public void setServerListener(ServerListener listener) {
        this.listener = listener;
    }

    public boolean startServer(String serviceName, String serviceUuid) {
        if (isRunning.get()) {
            Log.w(TAG, "Server is already running");
            return true;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter is null or not enabled");
            notifyError("Bluetooth not available or not enabled");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Bluetooth permissions not granted (Android 12+)");
                notifyError("Bluetooth permissions not granted");
                return false;
            }
        } else {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) 
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) 
            != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Bluetooth permissions not granted (Pre-Android 12)");
                notifyError("Bluetooth permissions not granted");
            return false;
            }
        }

        this.serviceName = serviceName != null ? serviceName : DEFAULT_SERVICE_NAME;
        this.serviceUuid = serviceUuid != null ? serviceUuid : DEFAULT_SERVICE_UUID;

        try {
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(this.serviceName, UUID.fromString(this.serviceUuid));
            
            isRunning.set(true);
            executor.submit(this::acceptConnections);
            
            Log.d(TAG, "Bluetooth server started with service: " + this.serviceName);
            notifyServerStarted();
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start Bluetooth server", e);
            notifyError("Failed to start server: " + e.getMessage());
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when starting server", e);
            notifyError("Security exception: " + e.getMessage());
            return false;
        }
    }

    public boolean stopServer() {
        if (!isRunning.get()) {
            return true;
        }

        isRunning.set(false);

        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
            
            notifyServerStopped();
            return true;

        } catch (IOException e) {
            notifyError("Error stopping server: " + e.getMessage());
            return false;
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    private void acceptConnections() {
        Log.d(TAG, "Started accepting connections");

        while (isRunning.get()) {
            try {
                if (serverSocket == null) {
                    Log.w(TAG, "Server socket is null, stopping accept loop");
                    break;
                }

                Log.d(TAG, "Waiting for client connection...");
                BluetoothSocket clientSocket = serverSocket.accept();
                
                if (clientSocket != null && isRunning.get()) {
                    String deviceAddress = clientSocket.getRemoteDevice().getAddress();
                    Log.d(TAG, "Client connected: " + deviceAddress);
                    
                    connectionManager.addConnection(deviceAddress, clientSocket, new ConnectionManager.ConnectionListener() {
                        @Override
                        public void onConnected(String deviceAddress) {
                            Log.d(TAG, "Connection established with client: " + deviceAddress);
                            notifyClientConnected(deviceAddress);
                        }

                        @Override
                        public void onDisconnected(String deviceAddress) {
                            Log.d(TAG, "Client disconnected: " + deviceAddress);
                            notifyClientDisconnected(deviceAddress);
                        }

                        @Override
                        public void onConnectionFailed(String deviceAddress, String error) {
                            Log.e(TAG, "Connection failed with client: " + deviceAddress + ", error: " + error);
                            notifyError("Connection failed with " + deviceAddress + ": " + error);
                        }

                        @Override
                        public void onMessageReceived(String deviceAddress, byte[] data) {
                            plugin.sendRawDataEvent(deviceAddress, data);
                            
                            messageHandler.processIncomingData(deviceAddress, data, new MessageHandler.MessageListener() {
                                @Override
                                public void onMessageProcessed(String deviceAddress, java.util.Map<String, Object> message) {
                                    handleProcessedMessage(deviceAddress, message);
                                }

                                @Override
                                public void onError(String deviceAddress, String error) {
                                    Log.e(TAG, "Message processing error for " + deviceAddress + ": " + error);
                                    notifyError("Message processing error: " + error);
                                }
                            });
                        }

                        @Override
                        public void onError(String deviceAddress, String error) {
                            Log.e(TAG, "Connection error with client: " + deviceAddress + ", error: " + error);
                            notifyError("Connection error with " + deviceAddress + ": " + error);
                        }
                    });

                } else if (clientSocket != null) {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing client socket during shutdown", e);
                    }
                }

            } catch (IOException e) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error accepting client connection", e);
                    notifyError("Error accepting connection: " + e.getMessage());
                } else {
                    Log.d(TAG, "Server socket closed, stopping accept loop");
                }
                break;
            }
        }

        Log.d(TAG, "Stopped accepting connections");
    }

    private void handleProcessedMessage(String deviceAddress, java.util.Map<String, Object> message) {
        String messageType = (String) message.get("type");
        String content = (String) message.get("content");
        
        Log.d(TAG, "Received message from " + deviceAddress + " of type: " + messageType);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> metadata = (java.util.Map<String, Object>) message.get("metadata");
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
            message.put("metadata", metadata);
        }
        metadata.put("senderAddress", deviceAddress);
        metadata.put("deviceAddress", deviceAddress);

        plugin.sendMessageReceivedEvent(message);
    }

    public boolean sendMessage(String deviceAddress, java.util.Map<String, Object> message) {
        byte[] data = messageHandler.processOutgoingMessage(message);
        if (data != null) {
            return connectionManager.sendMessage(deviceAddress, data);
        }
        return false;
    }

    public String sendFile(String deviceAddress, String filePath) {
        BluetoothSocket socket = connectionManager.getConnection(deviceAddress);
        if (socket != null) {
            return transferManager.startFileUpload(socket, filePath, new TransferManager.TransferProgressListener() {
                @Override
                public void onProgress(String transferId, String fileName, long totalBytes, long transferredBytes, double percentage) {
                    Log.d(TAG, "File upload progress: " + fileName + " " + percentage + "%");
                }

                @Override
                public void onCompleted(String transferId, String fileName, String filePath) {
                    Log.d(TAG, "File upload completed: " + fileName);
                }

                @Override
                public void onFailed(String transferId, String fileName, String error) {
                    Log.e(TAG, "File upload failed: " + fileName + " - " + error);
                }

                @Override
                public void onCancelled(String transferId, String fileName) {
                    Log.d(TAG, "File upload cancelled: " + fileName);
                }
            });
        }
        return null;
    }

    private void notifyServerStarted() {
        if (listener != null) {
            mainHandler.post(() -> listener.onServerStarted());
        }
    }

    private void notifyServerStopped() {
        if (listener != null) {
            mainHandler.post(() -> listener.onServerStopped());
        }
    }

    private void notifyClientConnected(String deviceAddress) {
        Log.d(TAG, "🔗 Notifying client connected: " + deviceAddress);
        if (listener != null) {
            mainHandler.post(() -> {
                Log.d(TAG, "🔗 Calling listener.onClientConnected for: " + deviceAddress);
                listener.onClientConnected(deviceAddress);
            });
        } else {
            Log.w(TAG, "🔗 No listener set for client connected notification");
        }
    }

    private void notifyClientDisconnected(String deviceAddress) {
        Log.d(TAG, "🔗 Notifying client disconnected: " + deviceAddress);
        if (listener != null) {
            mainHandler.post(() -> {
                Log.d(TAG, "🔗 Calling listener.onClientDisconnected for: " + deviceAddress);
                listener.onClientDisconnected(deviceAddress);
            });
        } else {
            Log.w(TAG, "🔗 No listener set for client disconnected notification");
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            mainHandler.post(() -> listener.onError(error));
        }
    }
}