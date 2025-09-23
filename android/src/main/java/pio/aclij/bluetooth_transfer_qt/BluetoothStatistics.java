package pio.aclij.bluetooth_transfer_qt;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and provides Bluetooth connection and transfer statistics
 */
public class BluetoothStatistics {
    private static final String TAG = "BluetoothStatistics";
    
    // Connection statistics
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong successfulConnections = new AtomicLong(0);
    private final AtomicLong failedConnections = new AtomicLong(0);
    private final AtomicLong totalDisconnections = new AtomicLong(0);
    
    // Transfer statistics
    private final AtomicLong totalTransfers = new AtomicLong(0);
    private final AtomicLong completedTransfers = new AtomicLong(0);
    private final AtomicLong failedTransfers = new AtomicLong(0);
    private final AtomicLong cancelledTransfers = new AtomicLong(0);
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    
    // Message statistics
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesFiltered = new AtomicLong(0);
    
    // Server statistics
    private final AtomicLong serverStartCount = new AtomicLong(0);
    private final AtomicLong clientConnectionsAccepted = new AtomicLong(0);
    
    // Client statistics
    private final AtomicLong scanAttempts = new AtomicLong(0);
    private final AtomicLong devicesDiscovered = new AtomicLong(0);
    
    // Timing
    private long startTime = System.currentTimeMillis();
    private long lastResetTime = System.currentTimeMillis();

    /**
     * Record a connection attempt
     */
    public void recordConnectionAttempt() {
        totalConnections.incrementAndGet();
        Log.v(TAG, "Connection attempt recorded");
    }

    /**
     * Record a successful connection
     */
    public void recordSuccessfulConnection() {
        successfulConnections.incrementAndGet();
        Log.v(TAG, "Successful connection recorded");
    }

    /**
     * Record a failed connection
     */
    public void recordFailedConnection() {
        failedConnections.incrementAndGet();
        Log.v(TAG, "Failed connection recorded");
    }

    /**
     * Record a disconnection
     */
    public void recordDisconnection() {
        totalDisconnections.incrementAndGet();
        Log.v(TAG, "Disconnection recorded");
    }

    /**
     * Record a transfer start
     */
    public void recordTransferStart() {
        totalTransfers.incrementAndGet();
        Log.v(TAG, "Transfer start recorded");
    }

    /**
     * Record a completed transfer
     */
    public void recordTransferCompleted(long bytesTransferred) {
        completedTransfers.incrementAndGet();
        totalBytesTransferred.addAndGet(bytesTransferred);
        Log.v(TAG, "Transfer completed recorded: " + bytesTransferred + " bytes");
    }

    /**
     * Record a failed transfer
     */
    public void recordTransferFailed() {
        failedTransfers.incrementAndGet();
        Log.v(TAG, "Transfer failed recorded");
    }

    /**
     * Record a cancelled transfer
     */
    public void recordTransferCancelled() {
        cancelledTransfers.incrementAndGet();
        Log.v(TAG, "Transfer cancelled recorded");
    }

    /**
     * Record a message sent
     */
    public void recordMessageSent() {
        messagesSent.incrementAndGet();
        Log.v(TAG, "Message sent recorded");
    }

    /**
     * Record a message received
     */
    public void recordMessageReceived() {
        messagesReceived.incrementAndGet();
        Log.v(TAG, "Message received recorded");
    }

    /**
     * Record a message filtered
     */
    public void recordMessageFiltered() {
        messagesFiltered.incrementAndGet();
        Log.v(TAG, "Message filtered recorded");
    }

    /**
     * Record server start
     */
    public void recordServerStart() {
        serverStartCount.incrementAndGet();
        Log.v(TAG, "Server start recorded");
    }

    /**
     * Record client connection accepted by server
     */
    public void recordClientConnectionAccepted() {
        clientConnectionsAccepted.incrementAndGet();
        Log.v(TAG, "Client connection accepted recorded");
    }

    /**
     * Record scan attempt
     */
    public void recordScanAttempt() {
        scanAttempts.incrementAndGet();
        Log.v(TAG, "Scan attempt recorded");
    }

    /**
     * Record device discovered
     */
    public void recordDeviceDiscovered() {
        devicesDiscovered.incrementAndGet();
        Log.v(TAG, "Device discovered recorded");
    }

    /**
     * Get all statistics as a map
     */
    public Map<String, Object> getAllStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Connection stats
        Map<String, Object> connectionStats = new HashMap<>();
        connectionStats.put("totalAttempts", totalConnections.get());
        connectionStats.put("successful", successfulConnections.get());
        connectionStats.put("failed", failedConnections.get());
        connectionStats.put("disconnections", totalDisconnections.get());
        connectionStats.put("successRate", getConnectionSuccessRate());
        stats.put("connections", connectionStats);
        
        // Transfer stats
        Map<String, Object> transferStats = new HashMap<>();
        transferStats.put("total", totalTransfers.get());
        transferStats.put("completed", completedTransfers.get());
        transferStats.put("failed", failedTransfers.get());
        transferStats.put("cancelled", cancelledTransfers.get());
        transferStats.put("totalBytesTransferred", totalBytesTransferred.get());
        transferStats.put("averageTransferSize", getAverageTransferSize());
        transferStats.put("successRate", getTransferSuccessRate());
        stats.put("transfers", transferStats);
        
        // Message stats
        Map<String, Object> messageStats = new HashMap<>();
        messageStats.put("sent", messagesSent.get());
        messageStats.put("received", messagesReceived.get());
        messageStats.put("filtered", messagesFiltered.get());
        messageStats.put("totalMessages", messagesSent.get() + messagesReceived.get());
        stats.put("messages", messageStats);
        
        // Server stats
        Map<String, Object> serverStats = new HashMap<>();
        serverStats.put("startCount", serverStartCount.get());
        serverStats.put("clientConnectionsAccepted", clientConnectionsAccepted.get());
        stats.put("server", serverStats);
        
        // Client stats
        Map<String, Object> clientStats = new HashMap<>();
        clientStats.put("scanAttempts", scanAttempts.get());
        clientStats.put("devicesDiscovered", devicesDiscovered.get());
        clientStats.put("averageDevicesPerScan", getAverageDevicesPerScan());
        stats.put("client", clientStats);
        
        // Timing stats
        Map<String, Object> timingStats = new HashMap<>();
        timingStats.put("startTime", startTime);
        timingStats.put("uptime", System.currentTimeMillis() - startTime);
        timingStats.put("lastResetTime", lastResetTime);
        timingStats.put("timeSinceReset", System.currentTimeMillis() - lastResetTime);
        stats.put("timing", timingStats);
        
        return stats;
    }

    /**
     * Get connection success rate as percentage
     */
    public double getConnectionSuccessRate() {
        long total = totalConnections.get();
        if (total == 0) return 0.0;
        return (double) successfulConnections.get() / total * 100.0;
    }

    /**
     * Get transfer success rate as percentage
     */
    public double getTransferSuccessRate() {
        long total = totalTransfers.get();
        if (total == 0) return 0.0;
        return (double) completedTransfers.get() / total * 100.0;
    }

    /**
     * Get average transfer size in bytes
     */
    public long getAverageTransferSize() {
        long completed = completedTransfers.get();
        if (completed == 0) return 0;
        return totalBytesTransferred.get() / completed;
    }

    /**
     * Get average devices discovered per scan
     */
    public double getAverageDevicesPerScan() {
        long scans = scanAttempts.get();
        if (scans == 0) return 0.0;
        return (double) devicesDiscovered.get() / scans;
    }

    /**
     * Reset all statistics
     */
    public void reset() {
        totalConnections.set(0);
        successfulConnections.set(0);
        failedConnections.set(0);
        totalDisconnections.set(0);
        
        totalTransfers.set(0);
        completedTransfers.set(0);
        failedTransfers.set(0);
        cancelledTransfers.set(0);
        totalBytesTransferred.set(0);
        
        messagesSent.set(0);
        messagesReceived.set(0);
        messagesFiltered.set(0);
        
        serverStartCount.set(0);
        clientConnectionsAccepted.set(0);
        
        scanAttempts.set(0);
        devicesDiscovered.set(0);
        
        lastResetTime = System.currentTimeMillis();
        
        Log.d(TAG, "Statistics reset");
    }

    /**
     * Get summary statistics as a formatted string
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Bluetooth Statistics Summary:\n");
        summary.append(String.format("Connections: %d total (%d successful, %.1f%% success rate)\n", 
            totalConnections.get(), successfulConnections.get(), getConnectionSuccessRate()));
        summary.append(String.format("Transfers: %d total (%d completed, %.1f%% success rate)\n", 
            totalTransfers.get(), completedTransfers.get(), getTransferSuccessRate()));
        summary.append(String.format("Data transferred: %d bytes (avg: %d bytes per transfer)\n", 
            totalBytesTransferred.get(), getAverageTransferSize()));
        summary.append(String.format("Messages: %d sent, %d received, %d filtered\n", 
            messagesSent.get(), messagesReceived.get(), messagesFiltered.get()));
        summary.append(String.format("Uptime: %d ms\n", System.currentTimeMillis() - startTime));
        
        return summary.toString();
    }

    /**
     * Log current statistics
     */
    public void logStatistics() {
        Log.i(TAG, getSummary());
    }
}



