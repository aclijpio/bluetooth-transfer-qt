package pio.aclij.bluetooth_transfer_qt;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages automatic reconnection to lost Bluetooth connections
 */
public class AutoReconnectManager {
    private static final String TAG = "AutoReconnectManager";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final long DEFAULT_INITIAL_DELAY = 2000; // 2 seconds
    private static final long DEFAULT_MAX_DELAY = 30000; // 30 seconds
    private static final double BACKOFF_MULTIPLIER = 1.5;

    private final ScheduledExecutorService scheduler;
    private final BluetoothClientManager clientManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private final Map<String, ReconnectTask> reconnectTasks = new ConcurrentHashMap<>();
    private boolean autoReconnectEnabled = true;
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private long initialDelay = DEFAULT_INITIAL_DELAY;
    private long maxDelay = DEFAULT_MAX_DELAY;

    public interface ReconnectListener {
        void onReconnectAttempt(String deviceAddress, int attempt, int maxAttempts);
        void onReconnectSuccess(String deviceAddress, int attempts);
        void onReconnectFailed(String deviceAddress, int attempts);
        void onReconnectAborted(String deviceAddress, String reason);
    }

    private ReconnectListener listener;

    public AutoReconnectManager(ScheduledExecutorService scheduler, BluetoothClientManager clientManager) {
        this.scheduler = scheduler;
        this.clientManager = clientManager;
    }

    public void setReconnectListener(ReconnectListener listener) {
        this.listener = listener;
    }

    /**
     * Enable or disable auto-reconnect
     */
    public void setAutoReconnectEnabled(boolean enabled) {
        this.autoReconnectEnabled = enabled;
        if (!enabled) {
            cancelAllReconnectTasks();
        }
        Log.d(TAG, "Auto-reconnect " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Set maximum reconnect attempts
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
        Log.d(TAG, "Max reconnect attempts set to: " + this.maxAttempts);
    }

    /**
     * Set initial delay between reconnect attempts
     */
    public void setInitialDelay(long delayMs) {
        this.initialDelay = Math.max(500, delayMs);
        Log.d(TAG, "Initial reconnect delay set to: " + this.initialDelay + "ms");
    }

    /**
     * Set maximum delay between reconnect attempts
     */
    public void setMaxDelay(long delayMs) {
        this.maxDelay = Math.max(1000, delayMs);
        Log.d(TAG, "Max reconnect delay set to: " + this.maxDelay + "ms");
    }

    /**
     * Start reconnect attempts for a device
     */
    public void startReconnect(String deviceAddress, String reason) {
        if (!autoReconnectEnabled) {
            Log.d(TAG, "Auto-reconnect disabled, not attempting reconnect to: " + deviceAddress);
            return;
        }

        if (reconnectTasks.containsKey(deviceAddress)) {
            Log.d(TAG, "Reconnect already in progress for device: " + deviceAddress);
            return;
        }

        Log.d(TAG, "Starting reconnect for device: " + deviceAddress + ", reason: " + reason);
        ReconnectTask task = new ReconnectTask(deviceAddress, reason);
        reconnectTasks.put(deviceAddress, task);
        task.scheduleNextAttempt(initialDelay);
    }

    /**
     * Stop reconnect attempts for a device
     */
    public void stopReconnect(String deviceAddress, String reason) {
        ReconnectTask task = reconnectTasks.remove(deviceAddress);
        if (task != null) {
            task.cancel();
            Log.d(TAG, "Stopped reconnect for device: " + deviceAddress + ", reason: " + reason);
            notifyReconnectAborted(deviceAddress, reason);
        }
    }

    /**
     * Cancel all active reconnect tasks
     */
    public void cancelAllReconnectTasks() {
        for (Map.Entry<String, ReconnectTask> entry : reconnectTasks.entrySet()) {
            entry.getValue().cancel();
            notifyReconnectAborted(entry.getKey(), "Manager shutdown");
        }
        reconnectTasks.clear();
        Log.d(TAG, "All reconnect tasks cancelled");
    }

    /**
     * Check if device has active reconnect task
     */
    public boolean isReconnecting(String deviceAddress) {
        return reconnectTasks.containsKey(deviceAddress);
    }

    /**
     * Get reconnect status for device
     */
    public Map<String, Object> getReconnectStatus(String deviceAddress) {
        ReconnectTask task = reconnectTasks.get(deviceAddress);
        if (task != null) {
            return task.getStatus();
        }
        return null;
    }

    /**
     * Get status of all active reconnect tasks
     */
    public Map<String, Map<String, Object>> getAllReconnectStatus() {
        Map<String, Map<String, Object>> status = new ConcurrentHashMap<>();
        for (Map.Entry<String, ReconnectTask> entry : reconnectTasks.entrySet()) {
            status.put(entry.getKey(), entry.getValue().getStatus());
        }
        return status;
    }

    private class ReconnectTask {
        private final String deviceAddress;
        private final String originalReason;
        private final long startTime;
        private int attempts = 0;
        private long currentDelay = initialDelay;
        private ScheduledFuture<?> scheduledFuture;
        private volatile boolean cancelled = false;

        public ReconnectTask(String deviceAddress, String originalReason) {
            this.deviceAddress = deviceAddress;
            this.originalReason = originalReason;
            this.startTime = System.currentTimeMillis();
        }

        public void scheduleNextAttempt(long delay) {
            if (cancelled) return;

            scheduledFuture = scheduler.schedule(this::attemptReconnect, delay, TimeUnit.MILLISECONDS);
            Log.v(TAG, "Scheduled reconnect attempt for " + deviceAddress + " in " + delay + "ms");
        }

        private void attemptReconnect() {
            if (cancelled) return;

            attempts++;
            Log.d(TAG, "Reconnect attempt " + attempts + "/" + maxAttempts + " for device: " + deviceAddress);
            
            notifyReconnectAttempt(deviceAddress, attempts, maxAttempts);

            // Try to reconnect
            clientManager.connectToDevice(deviceAddress).thenAccept(success -> {
                if (cancelled) return;

                if (success) {
                    Log.d(TAG, "Reconnect successful for device: " + deviceAddress + " after " + attempts + " attempts");
                    reconnectTasks.remove(deviceAddress);
                    notifyReconnectSuccess(deviceAddress, attempts);
                } else {
                    handleReconnectFailure();
                }
            }).exceptionally(throwable -> {
                if (!cancelled) {
                    handleReconnectFailure();
                }
                return null;
            });
        }

        private void handleReconnectFailure() {
            if (attempts >= maxAttempts) {
                Log.w(TAG, "Reconnect failed for device: " + deviceAddress + " after " + attempts + " attempts");
                reconnectTasks.remove(deviceAddress);
                notifyReconnectFailed(deviceAddress, attempts);
            } else {
                // Schedule next attempt with exponential backoff
                currentDelay = Math.min((long) (currentDelay * BACKOFF_MULTIPLIER), maxDelay);
                scheduleNextAttempt(currentDelay);
                Log.d(TAG, "Reconnect attempt " + attempts + " failed for " + deviceAddress + 
                      ", next attempt in " + currentDelay + "ms");
            }
        }

        public void cancel() {
            cancelled = true;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }

        public Map<String, Object> getStatus() {
            Map<String, Object> status = new ConcurrentHashMap<>();
            status.put("deviceAddress", deviceAddress);
            status.put("originalReason", originalReason);
            status.put("startTime", startTime);
            status.put("attempts", attempts);
            status.put("maxAttempts", maxAttempts);
            status.put("currentDelay", currentDelay);
            status.put("cancelled", cancelled);
            status.put("elapsedTime", System.currentTimeMillis() - startTime);
            return status;
        }
    }

    // Notification methods
    private void notifyReconnectAttempt(String deviceAddress, int attempt, int maxAttempts) {
        if (listener != null) {
            mainHandler.post(() -> listener.onReconnectAttempt(deviceAddress, attempt, maxAttempts));
        }
    }

    private void notifyReconnectSuccess(String deviceAddress, int attempts) {
        if (listener != null) {
            mainHandler.post(() -> listener.onReconnectSuccess(deviceAddress, attempts));
        }
    }

    private void notifyReconnectFailed(String deviceAddress, int attempts) {
        if (listener != null) {
            mainHandler.post(() -> listener.onReconnectFailed(deviceAddress, attempts));
        }
    }

    private void notifyReconnectAborted(String deviceAddress, String reason) {
        if (listener != null) {
            mainHandler.post(() -> listener.onReconnectAborted(deviceAddress, reason));
        }
    }

    /**
     * Get configuration as map
     */
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new ConcurrentHashMap<>();
        config.put("enabled", autoReconnectEnabled);
        config.put("maxAttempts", maxAttempts);
        config.put("initialDelay", initialDelay);
        config.put("maxDelay", maxDelay);
        config.put("backoffMultiplier", BACKOFF_MULTIPLIER);
        config.put("activeReconnectTasks", reconnectTasks.size());
        return config;
    }
}



