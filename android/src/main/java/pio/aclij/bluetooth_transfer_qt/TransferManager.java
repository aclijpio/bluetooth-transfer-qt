package pio.aclij.bluetooth_transfer_qt;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Manages file transfers with progress tracking and cancellation support
 */
public class TransferManager {
    private static final String TAG = "TransferManager";
    private static final int BUFFER_SIZE = 8192;
    private static final int PROGRESS_UPDATE_INTERVAL = 500;

    private final Map<String, TransferTask> activeTransfers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothTransferQtPlugin plugin;

    public interface TransferProgressListener {
        void onProgress(String transferId, String fileName, long totalBytes, long transferredBytes, double percentage);
        void onCompleted(String transferId, String fileName, String filePath);
        void onFailed(String transferId, String fileName, String error);
        void onCancelled(String transferId, String fileName);
    }

    public TransferManager(ExecutorService executor, BluetoothTransferQtPlugin plugin) {
        this.executor = executor;
        this.plugin = plugin;
    }

    /**
     * Start file upload to remote device
     */
    public String startFileUpload(BluetoothSocket socket, String filePath, TransferProgressListener listener) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            if (listener != null) {
                listener.onFailed("", file.getName(), "File not found: " + filePath);
            }
            return null;
        }

        String transferId = generateTransferId();
        TransferTask task = new TransferTask(transferId, file, socket, TransferType.UPLOAD, listener);
        activeTransfers.put(transferId, task);

        Future<?> future = executor.submit(task);
        task.setFuture(future);

        Log.d(TAG, "Started file upload: " + transferId + " for file: " + file.getName());
        return transferId;
    }

    /**
     * Start file download from remote device
     */
    public String startFileDownload(BluetoothSocket socket, String fileName, String savePath, TransferProgressListener listener) {
        String transferId = generateTransferId();
        File saveFile = new File(savePath);
        
        // Create parent directories if they don't exist
        File parentDir = saveFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        TransferTask task = new TransferTask(transferId, saveFile, socket, TransferType.DOWNLOAD, listener);
        task.setRemoteFileName(fileName);
        activeTransfers.put(transferId, task);

        Future<?> future = executor.submit(task);
        task.setFuture(future);

        Log.d(TAG, "Started file download: " + transferId + " for file: " + fileName);
        return transferId;
    }

    /**
     * Cancel active transfer
     */
    public boolean cancelTransfer(String transferId) {
        TransferTask task = activeTransfers.get(transferId);
        if (task != null) {
            task.cancel();
            activeTransfers.remove(transferId);
            Log.d(TAG, "Cancelled transfer: " + transferId);
            return true;
        }
        Log.w(TAG, "Transfer not found for cancellation: " + transferId);
        return false;
    }

    /**
     * Get all active transfer IDs
     */
    public String[] getActiveTransferIds() {
        return activeTransfers.keySet().toArray(new String[0]);
    }

    /**
     * Get transfer info
     */
    public Map<String, Object> getTransferInfo(String transferId) {
        TransferTask task = activeTransfers.get(transferId);
        if (task != null) {
            return task.getInfo();
        }
        return null;
    }

    /**
     * Clean up completed/failed transfers
     */
    public void cleanup() {
        for (TransferTask task : activeTransfers.values()) {
            task.cancel();
        }
        activeTransfers.clear();
    }

    private String generateTransferId() {
        return "transfer_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void onTransferCompleted(String transferId) {
        activeTransfers.remove(transferId);
    }

    private void onTransferFailed(String transferId) {
        activeTransfers.remove(transferId);
    }

    private void onTransferCancelled(String transferId) {
        activeTransfers.remove(transferId);
    }

    enum TransferType {
        UPLOAD, DOWNLOAD
    }

    /**
     * Individual transfer task
     */
    private class TransferTask implements Runnable {
        private final String transferId;
        private final File file;
        private final BluetoothSocket socket;
        private final TransferType type;
        private final TransferProgressListener listener;
        private String remoteFileName;
        private Future<?> future;
        private volatile boolean cancelled = false;
        private long startTime;
        private long totalBytes;
        private long transferredBytes;

        public TransferTask(String transferId, File file, BluetoothSocket socket, TransferType type, TransferProgressListener listener) {
            this.transferId = transferId;
            this.file = file;
            this.socket = socket;
            this.type = type;
            this.listener = listener;
            this.startTime = System.currentTimeMillis();
        }

        public void setRemoteFileName(String remoteFileName) {
            this.remoteFileName = remoteFileName;
        }

        public void setFuture(Future<?> future) {
            this.future = future;
        }

        public void cancel() {
            cancelled = true;
            if (future != null) {
                future.cancel(true);
            }
            try {
                if (socket != null && socket.isConnected()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket during cancellation", e);
            }
        }

        public Map<String, Object> getInfo() {
            Map<String, Object> info = new HashMap<>();
            info.put("transferId", transferId);
            info.put("fileName", type == TransferType.UPLOAD ? file.getName() : remoteFileName);
            info.put("filePath", file.getAbsolutePath());
            info.put("type", type.name().toLowerCase());
            info.put("totalBytes", totalBytes);
            info.put("transferredBytes", transferredBytes);
            info.put("percentage", totalBytes > 0 ? (double) transferredBytes / totalBytes * 100 : 0);
            info.put("startTime", startTime);
            info.put("cancelled", cancelled);
            return info;
        }

        @Override
        public void run() {
            try {
                if (type == TransferType.UPLOAD) {
                    performUpload();
                } else {
                    performDownload();
                }
            } catch (Exception e) {
                if (!cancelled) {
                    Log.e(TAG, "Transfer failed: " + transferId, e);
                    notifyFailed(e.getMessage());
                    onTransferFailed(transferId);
                }
            }
        }

        private void performUpload() throws IOException {
            if (!socket.isConnected()) {
                throw new IOException("Socket not connected");
            }

            totalBytes = file.length();
            transferredBytes = 0;

            sendFileHeader(file.getName(), totalBytes);

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = socket.getOutputStream()) {
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long lastProgressUpdate = 0;

                while ((bytesRead = fis.read(buffer)) != -1 && !cancelled) {
                    os.write(buffer, 0, bytesRead);
                    os.flush();
                    
                    transferredBytes += bytesRead;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL) {
                        notifyProgress();
                        lastProgressUpdate = currentTime;
                    }
                }

                if (cancelled) {
                    notifyCancelled();
                    onTransferCancelled(transferId);
                } else {
                    notifyProgress();
                    notifyCompleted(file.getAbsolutePath());
                    onTransferCompleted(transferId);
                }
            }
        }

        private void performDownload() throws IOException {
            if (!socket.isConnected()) {
                throw new IOException("Socket not connected");
            }

            try (InputStream is = socket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(file)) {

                FileHeader header = readFileHeader(is);
                totalBytes = header.fileSize;
                transferredBytes = 0;

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long lastProgressUpdate = 0;

                while (transferredBytes < totalBytes && !cancelled) {
                    int toRead = (int) Math.min(buffer.length, totalBytes - transferredBytes);
                    bytesRead = is.read(buffer, 0, toRead);
                    
                    if (bytesRead == -1) {
                        throw new IOException("Unexpected end of stream");
                    }

                    fos.write(buffer, 0, bytesRead);
                    transferredBytes += bytesRead;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL) {
                        notifyProgress();
                        lastProgressUpdate = currentTime;
                    }
                }

                if (cancelled) {
                    if (file.exists()) {
                        file.delete();
                    }
                    notifyCancelled();
                    onTransferCancelled(transferId);
                } else {
                    notifyProgress();
                    notifyCompleted(file.getAbsolutePath());
                    onTransferCompleted(transferId);
                }
            }
        }

        private void sendFileHeader(String fileName, long fileSize) throws IOException {
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            outputStream.writeLong(fileSize);
            outputStream.flush();
        }


        private FileHeader readFileHeader(InputStream is) throws IOException {
            DataInputStream inputStream = new DataInputStream(is);
            long fileSize = inputStream.readLong();
            return new FileHeader("unknown", fileSize);
        }

        private void notifyProgress() {
            if (listener != null) {
                double percentage = totalBytes > 0 ? (double) transferredBytes / totalBytes * 100 : 0;
                String fileName = type == TransferType.UPLOAD ? file.getName() : remoteFileName;
                
                mainHandler.post(() -> {
                    listener.onProgress(transferId, fileName, totalBytes, transferredBytes, percentage);
                    
                    // Also notify plugin for Flutter events
                    plugin.sendTransferProgressEvent(transferId, fileName, totalBytes, transferredBytes);
                });
            }
        }

        private void notifyCompleted(String filePath) {
            if (listener != null) {
                String fileName = type == TransferType.UPLOAD ? file.getName() : remoteFileName;
                mainHandler.post(() -> listener.onCompleted(transferId, fileName, filePath));
            }
        }

        private void notifyFailed(String error) {
            if (listener != null) {
                String fileName = type == TransferType.UPLOAD ? file.getName() : remoteFileName;
                mainHandler.post(() -> listener.onFailed(transferId, fileName, error));
            }
        }

        private void notifyCancelled() {
            if (listener != null) {
                String fileName = type == TransferType.UPLOAD ? file.getName() : remoteFileName;
                mainHandler.post(() -> listener.onCancelled(transferId, fileName));
            }
        }
    }

    private static class FileHeader {
        final String fileName;
        final long fileSize;

        FileHeader(String fileName, long fileSize) {
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
    }
}

