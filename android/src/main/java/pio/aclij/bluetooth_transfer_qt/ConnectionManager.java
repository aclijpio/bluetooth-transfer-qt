package pio.aclij.bluetooth_transfer_qt;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static final int HEARTBEAT_INTERVAL = 30;
    private static final int CONNECTION_TIMEOUT = 10;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private final Map<String, ConnectionHandler> connections = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothTransferQtPlugin plugin;

    public interface ConnectionListener {
        void onConnected(String deviceAddress);
        void onDisconnected(String deviceAddress);
        void onConnectionFailed(String deviceAddress, String error);
        void onMessageReceived(String deviceAddress, byte[] data);
        void onError(String deviceAddress, String error);
    }

    public ConnectionManager(ExecutorService executor, ScheduledExecutorService scheduler, BluetoothTransferQtPlugin plugin) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    public void addConnection(String deviceAddress, BluetoothSocket socket, ConnectionListener listener) {
        if (connections.containsKey(deviceAddress)) {
            Log.w(TAG, "Connection already exists for device: " + deviceAddress);
            removeConnection(deviceAddress);
        }

        ConnectionHandler handler = new ConnectionHandler(deviceAddress, socket, listener);
        connections.put(deviceAddress, handler);
        
        executor.submit(handler);
        Log.d(TAG, "Added connection for device: " + deviceAddress);
    }

    public boolean removeConnection(String deviceAddress) {
        ConnectionHandler handler = connections.remove(deviceAddress);
        if (handler != null) {
            handler.close();
            Log.d(TAG, "Removed connection for device: " + deviceAddress);
            return true;
        }
        return false;
    }
    
    public boolean sendCommand(String deviceAddress, String command) {
        ConnectionHandler handler = connections.get(deviceAddress);
        if (handler == null) {
            Log.w(TAG, "No connection found for device: " + deviceAddress);
            return false;
        }
        
        try {
            return handler.sendCommand(command);
        } catch (Exception e) {
            Log.e(TAG, "Error sending command to " + deviceAddress, e);
            return false;
        }
    }
    
    public boolean sendData(String deviceAddress, byte[] data) {
        ConnectionHandler handler = connections.get(deviceAddress);
        if (handler == null) {
            Log.w(TAG, "No connection found for device: " + deviceAddress);
            return false;
        }
        
        try {
            return handler.sendData(data);
        } catch (Exception e) {
            Log.e(TAG, "Error sending data to " + deviceAddress, e);
            return false;
        }
    }

    public void pauseReading(String deviceAddress) {
        ConnectionHandler handler = connections.get(deviceAddress);
        if (handler != null) {
            handler.setReadingPaused(true);
            Log.d(TAG, "Paused reading for device: " + deviceAddress);
        }
    }

    public void resumeReading(String deviceAddress) {
        ConnectionHandler handler = connections.get(deviceAddress);
        if (handler != null) {
            handler.setReadingPaused(false);
            Log.d(TAG, "Resumed reading for device: " + deviceAddress);
        }
    }

    public BluetoothSocket getConnection(String deviceAddress) {
        ConnectionHandler handler = connections.get(deviceAddress);
        return handler != null ? handler.getSocket() : null;
    }

    public boolean isConnected(String deviceAddress) {
        if (deviceAddress == null) {
            Log.w(TAG, "Device address is null in isConnected()");
            return false;
        }
        ConnectionHandler handler = connections.get(deviceAddress);
        return handler != null && handler.isConnected();
    }

    public String[] getConnectedDevices() {
        return connections.entrySet().stream()
            .filter(entry -> entry.getValue().isConnected())
            .map(Map.Entry::getKey)
            .toArray(String[]::new);
    }

    public boolean sendMessage(String deviceAddress, byte[] data) {
        ConnectionHandler handler = connections.get(deviceAddress);
        if (handler != null && handler.isConnected()) {
            return handler.sendMessage(data);
        }
        return false;
    }

    public Map<String, Object> getConnectionStats(String deviceAddress) {
        ConnectionHandler handler = connections.get(deviceAddress);
        return handler != null ? handler.getStats() : null;
    }

    public void cleanup() {
        for (ConnectionHandler handler : connections.values()) {
            handler.close();
        }
        connections.clear();
    }

    private class ConnectionHandler implements Runnable {
        private final String deviceAddress;
        private final BluetoothSocket socket;
        private final ConnectionListener listener;
        private final long connectTime;
        private volatile boolean running = true;
        private volatile boolean readingPaused = false;
        private volatile boolean connected = false;
        private ScheduledFuture<?> heartbeatTask;
        private long lastHeartbeat;
        private long bytesReceived = 0;
        private long bytesSent = 0;
        private int reconnectAttempts = 0;

        public ConnectionHandler(String deviceAddress, BluetoothSocket socket, ConnectionListener listener) {
            this.deviceAddress = deviceAddress;
            this.socket = socket;
            this.listener = listener;
            this.connectTime = System.currentTimeMillis();
            this.connected = socket.isConnected();
        }

        @Override
        public void run() {
            try {
                if (!socket.isConnected()) {
                    Log.w(TAG, "Socket not connected for device: " + deviceAddress);
                    notifyConnectionFailed("Socket not connected");
                    return;
                }

                connected = true;
                notifyConnected();
                startHeartbeat();

                try (InputStream inputStream = socket.getInputStream()) {
                    byte[] buffer = new byte[32768];
                    int bytesRead;

                    while (running && socket.isConnected()) {
                        try {
                            if (readingPaused) {
                                try { Thread.sleep(10); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                                continue;
                            }
                            bytesRead = inputStream.read(buffer);
                            if (bytesRead > 0) {
                                bytesReceived += bytesRead;
                                lastHeartbeat = System.currentTimeMillis();
                                
                                byte[] data = new byte[bytesRead];
                                System.arraycopy(buffer, 0, data, 0, bytesRead);
                                notifyMessageReceived(data);
                            } else if (bytesRead == -1) {
                                Log.d(TAG, "Connection closed by remote device: " + deviceAddress);
                                break;
                            }
                        } catch (IOException e) {
                            if (running) {
                                Log.e(TAG, "Error reading from socket: " + deviceAddress, e);
                                
                                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                                    reconnectAttempts++;
                                    Log.d(TAG, "Attempting reconnection " + reconnectAttempts + " for device: " + deviceAddress);
                                    
                                    try {
                                        Thread.sleep(2000);
                                        if (attemptReconnect()) {
                                            continue;
                                        }
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                                
                                notifyError("Connection lost: " + e.getMessage());
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "Error getting input stream for device: " + deviceAddress, e);
                        notifyError("Failed to get input stream: " + e.getMessage());
                    }
                }
            } finally {
                cleanup();
                connected = false;
                notifyDisconnected();
            }
        }

        public void setReadingPaused(boolean paused) {
            this.readingPaused = paused;
        }

        private boolean attemptReconnect() {
            try {
                if (socket.isConnected()) {
                    return true;
                }
                
                Log.d(TAG, "Reconnection attempt failed - socket cannot be reopened");
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Reconnection failed for device: " + deviceAddress, e);
                return false;
            }
        }

        private void startHeartbeat() {
            heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
                if (running && connected) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastHeartbeat > HEARTBEAT_INTERVAL * 1000) {
                        sendHeartbeat();
                    }
                }
            }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
            
            lastHeartbeat = System.currentTimeMillis();
        }

        private void sendHeartbeat() {
            try {
                byte[] heartbeat = "HEARTBEAT".getBytes();
                if (sendMessage(heartbeat)) {
                    Log.v(TAG, "Heartbeat sent to device: " + deviceAddress);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send heartbeat to device: " + deviceAddress, e);
            }
        }

        public boolean sendMessage(byte[] data) {
            if (!connected || !socket.isConnected()) {
                return false;
            }

            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(data);
                outputStream.flush();
                bytesSent += data.length;
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error sending message to device: " + deviceAddress, e);
                notifyError("Failed to send message: " + e.getMessage());
                return false;
            }
        }

        public BluetoothSocket getSocket() {
            return socket;
        }

        public boolean isConnected() {
            return connected && socket.isConnected();
        }

        public Map<String, Object> getStats() {
            Map<String, Object> stats = new ConcurrentHashMap<>();
            stats.put("deviceAddress", deviceAddress);
            stats.put("connected", connected);
            stats.put("connectTime", connectTime);
            stats.put("uptime", System.currentTimeMillis() - connectTime);
            stats.put("bytesReceived", bytesReceived);
            stats.put("bytesSent", bytesSent);
            stats.put("lastHeartbeat", lastHeartbeat);
            stats.put("reconnectAttempts", reconnectAttempts);
            return stats;
        }
        
        public boolean sendCommand(String command) {
            if (!isConnected()) {
                return false;
            }
            
            try {
                OutputStream out = socket.getOutputStream();
                byte[] commandBytes = (command + "\n").getBytes("UTF-8");
                out.write(commandBytes);
                out.flush();
                bytesSent += commandBytes.length;
                Log.d(TAG, "Sent command to " + deviceAddress + ": " + command);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error sending command to " + deviceAddress, e);
                notifyError("Failed to send command: " + e.getMessage());
                return false;
            }
        }
        
        public boolean sendData(byte[] data) {
            if (!isConnected()) {
                return false;
            }
            
            try {
                OutputStream out = socket.getOutputStream();
                out.write(data);
                out.flush();
                bytesSent += data.length;
                Log.d(TAG, "Sent " + data.length + " bytes to " + deviceAddress);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error sending data to " + deviceAddress, e);
                notifyError("Failed to send data: " + e.getMessage());
                return false;
            }
        }

        public void close() {
            running = false;
            connected = false;
            
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
            
            try {
                if (socket != null && socket.isConnected()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket for device: " + deviceAddress, e);
            }
        }

        private void cleanup() {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
        }

        private void notifyConnected() {
            if (listener != null) {
                mainHandler.post(() -> {
                    listener.onConnected(deviceAddress);
                    plugin.sendConnectionEvent(deviceAddress, true);
                });
            }
        }

        private void notifyDisconnected() {
            if (listener != null) {
                mainHandler.post(() -> {
                    listener.onDisconnected(deviceAddress);
                    plugin.sendConnectionEvent(deviceAddress, false);
                });
            }
        }

        private void notifyConnectionFailed(String error) {
            if (listener != null) {
                mainHandler.post(() -> listener.onConnectionFailed(deviceAddress, error));
            }
        }

        private void notifyMessageReceived(byte[] data) {
            if (listener != null) {
                mainHandler.post(() -> listener.onMessageReceived(deviceAddress, data));
            }
        }

        private void notifyError(String error) {
            if (listener != null) {
                mainHandler.post(() -> {
                    listener.onError(deviceAddress, error);
                    plugin.sendErrorEvent("Connection error for " + deviceAddress + ": " + error);
                });
            }
        }
    }
}