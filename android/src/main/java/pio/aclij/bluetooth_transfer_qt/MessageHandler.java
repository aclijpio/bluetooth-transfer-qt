package pio.aclij.bluetooth_transfer_qt;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MessageHandler {
    private static final String TAG = "MessageHandler";
    
    private final Map<String, MessageFilter> filters = new ConcurrentHashMap<>();
    private final BluetoothTransferQtPlugin plugin;

    public interface MessageListener {
        void onMessageProcessed(String deviceAddress, Map<String, Object> message);
        void onError(String deviceAddress, String error);
    }

    public MessageHandler(BluetoothTransferQtPlugin plugin) {
        this.plugin = plugin;
    }

    public void processIncomingData(String deviceAddress, byte[] data, MessageListener listener) {
        try {
            String rawMessage = new String(data, StandardCharsets.UTF_8);
            
            Map<String, Object> message = parseMessage(rawMessage);
            if (message == null) {
                message = createTextMessage(rawMessage);
            }

            message = applyIncomingFilters(message);

            if (listener != null) {
                listener.onMessageProcessed(deviceAddress, message);
            }

            plugin.sendMessageReceivedEvent(message);

        } catch (Exception e) {
            Log.e(TAG, "Error processing incoming message from " + deviceAddress, e);
            if (listener != null) {
                listener.onError(deviceAddress, "Failed to process message: " + e.getMessage());
            }
        }
    }

    public byte[] processOutgoingMessage(Map<String, Object> message) {
        try {
            Map<String, Object> processedMessage = applyOutgoingFilters(message);
            String jsonMessage = mapToJson(processedMessage);
            return jsonMessage.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error processing outgoing message", e);
            return null;
        }
    }

    public boolean addFilter(String filterId, Map<String, Object> config) {
        try {
            MessageFilter filter = createFilter(filterId, config);
            filters.put(filterId, filter);
            Log.d(TAG, "Added message filter: " + filterId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add filter: " + filterId, e);
            return false;
        }
    }

    public boolean removeFilter(String filterId) {
        MessageFilter removed = filters.remove(filterId);
        if (removed != null) {
            Log.d(TAG, "Removed message filter: " + filterId);
            return true;
        }
        return false;
    }

    public void clearFilters() {
        filters.clear();
        Log.d(TAG, "Cleared all message filters");
    }

    public String[] getActiveFilters() {
        return filters.keySet().toArray(new String[0]);
    }

    private Map<String, Object> parseMessage(String rawMessage) {
        try {
            JSONObject json = new JSONObject(rawMessage);
            return jsonToMap(json);
        } catch (JSONException e) {
            return null;
        }
    }

    private Map<String, Object> createTextMessage(String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "text");
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }

    private Map<String, Object> applyIncomingFilters(Map<String, Object> message) {
        Map<String, Object> processedMessage = new HashMap<>(message);
        
        List<MessageFilter> sortedFilters = new ArrayList<>(filters.values());
        sortedFilters.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

        for (MessageFilter filter : sortedFilters) {
            try {
                processedMessage = filter.processIncoming(processedMessage);
            } catch (Exception e) {
                Log.e(TAG, "Error in incoming filter: " + filter.getId(), e);
            }
        }

        return processedMessage;
    }

    private Map<String, Object> applyOutgoingFilters(Map<String, Object> message) {
        Map<String, Object> processedMessage = new HashMap<>(message);
        
        List<MessageFilter> sortedFilters = new ArrayList<>(filters.values());
        sortedFilters.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (MessageFilter filter : sortedFilters) {
            try {
                processedMessage = filter.processOutgoing(processedMessage);
            } catch (Exception e) {
                Log.e(TAG, "Error in outgoing filter: " + filter.getId(), e);
            }
        }

        return processedMessage;
    }

    private MessageFilter createFilter(String filterId, Map<String, Object> config) {
        String type = (String) config.get("type");
        int priority = config.containsKey("priority") ? (Integer) config.get("priority") : 0;

        switch (type) {
            case "logging":
                return new LoggingFilter(filterId, priority, config);
            case "compression":
                return new CompressionFilter(filterId, priority, config);
            case "encryption":
                return new EncryptionFilter(filterId, priority, config);
            case "validation":
                return new ValidationFilter(filterId, priority, config);
            default:
                throw new IllegalArgumentException("Unknown filter type: " + type);
        }
    }

    private String mapToJson(Map<String, Object> map) {
        try {
            JSONObject json = new JSONObject(map);
            return json.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error converting map to JSON", e);
            return "{}";
        }
    }

    private Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> map = new HashMap<>();
        
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            
            if (value == JSONObject.NULL) {
                map.put(key, null);
            } else if (value instanceof JSONObject) {
                map.put(key, jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                // Handle JSON arrays if needed in the future
                map.put(key, value);
            } else {
                // Convert any JSONObject internal types to proper values
                if (value != null && value.getClass().getName().contains("JSONObject$")) {
                    // This handles JSONObject$1 and similar internal classes
                    try {
                        if (value instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mapValue = (Map<String, Object>) value;
                            map.put(key, new HashMap<>(mapValue));
                        } else {
                            map.put(key, value.toString());
                        }
                    } catch (Exception e) {
                        map.put(key, value.toString());
                    }
                } else {
                    map.put(key, value);
                }
            }
        }
        
        return map;
    }

    public abstract static class MessageFilter {
        protected final String id;
        protected final int priority;

        public MessageFilter(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        public String getId() { return id; }
        public int getPriority() { return priority; }

        public abstract Map<String, Object> processIncoming(Map<String, Object> message);
        public abstract Map<String, Object> processOutgoing(Map<String, Object> message);
    }

    public static class LoggingFilter extends MessageFilter {
        private final boolean logIncoming;
        private final boolean logOutgoing;

        public LoggingFilter(String id, int priority, Map<String, Object> config) {
            super(id, priority);
            this.logIncoming = config.containsKey("logIncoming") ? (Boolean) config.get("logIncoming") : true;
            this.logOutgoing = config.containsKey("logOutgoing") ? (Boolean) config.get("logOutgoing") : true;
        }

        @Override
        public Map<String, Object> processIncoming(Map<String, Object> message) {
            if (logIncoming) {
                Log.d(TAG, "[Filter:" + id + "] Incoming: " + message.get("type") + " - " + message.get("content"));
            }
            return message;
        }

        @Override
        public Map<String, Object> processOutgoing(Map<String, Object> message) {
            if (logOutgoing) {
                Log.d(TAG, "[Filter:" + id + "] Outgoing: " + message.get("type") + " - " + message.get("content"));
            }
            return message;
        }
    }

    public static class CompressionFilter extends MessageFilter {
        private final int threshold;

        public CompressionFilter(String id, int priority, Map<String, Object> config) {
            super(id, priority);
            this.threshold = config.containsKey("threshold") ? (Integer) config.get("threshold") : 1024;
        }

        @Override
        public Map<String, Object> processIncoming(Map<String, Object> message) {
            Object compressed = message.get("compressed");
            if (compressed != null && (Boolean) compressed) {
                String content = (String) message.get("content");
                if (content != null) {
                    try {
                        String decompressed = decompress(content);
                        Map<String, Object> result = new HashMap<>(message);
                        result.put("content", decompressed);
                        result.remove("compressed");
                        result.put("decompressed", true);
                        return result;
                    } catch (Exception e) {
                        Log.e(TAG, "Decompression failed", e);
                    }
                }
            }
            return message;
        }

        @Override
        public Map<String, Object> processOutgoing(Map<String, Object> message) {
            String content = (String) message.get("content");
            if (content != null && content.length() > threshold) {
                try {
                    String compressed = compress(content);
                    Map<String, Object> result = new HashMap<>(message);
                    result.put("content", compressed);
                    result.put("compressed", true);
                    result.put("originalSize", content.length());
                    return result;
                } catch (Exception e) {
                    Log.e(TAG, "Compression failed", e);
                }
            }
            return message;
        }

        private String compress(String data) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(data.getBytes(StandardCharsets.UTF_8));
            }
            return android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.DEFAULT);
        }

        private String decompress(String compressedData) throws IOException {
            byte[] compressed = android.util.Base64.decode(compressedData, android.util.Base64.DEFAULT);
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzip.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                return bos.toString(StandardCharsets.UTF_8.name());
            }
        }
    }

    public static class EncryptionFilter extends MessageFilter {
        private final String key;

        public EncryptionFilter(String id, int priority, Map<String, Object> config) {
            super(id, priority);
            this.key = (String) config.get("key");
        }

        @Override
        public Map<String, Object> processIncoming(Map<String, Object> message) {
            Object encrypted = message.get("encrypted");
            if (encrypted != null && (Boolean) encrypted) {
                String content = (String) message.get("content");
                if (content != null) {
                    String decrypted = simpleDecrypt(content, key);
                    Map<String, Object> result = new HashMap<>(message);
                    result.put("content", decrypted);
                    result.remove("encrypted");
                    result.put("decrypted", true);
                    return result;
                }
            }
            return message;
        }

        @Override
        public Map<String, Object> processOutgoing(Map<String, Object> message) {
            String content = (String) message.get("content");
            if (content != null) {
                String encrypted = simpleEncrypt(content, key);
                Map<String, Object> result = new HashMap<>(message);
                result.put("content", encrypted);
                result.put("encrypted", true);
                return result;
            }
            return message;
        }

        private String simpleEncrypt(String data, String key) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < data.length(); i++) {
                result.append((char) (data.charAt(i) ^ key.charAt(i % key.length())));
            }
            return android.util.Base64.encodeToString(result.toString().getBytes(), android.util.Base64.DEFAULT);
        }

        private String simpleDecrypt(String encryptedData, String key) {
            try {
                String data = new String(android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT));
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < data.length(); i++) {
                    result.append((char) (data.charAt(i) ^ key.charAt(i % key.length())));
                }
                return result.toString();
            } catch (Exception e) {
                Log.e(TAG, "Decryption failed", e);
                return encryptedData;
            }
        }
    }

    public static class ValidationFilter extends MessageFilter {
        private final List<String> requiredFields;

        @SuppressWarnings("unchecked")
        public ValidationFilter(String id, int priority, Map<String, Object> config) {
            super(id, priority);
            this.requiredFields = (List<String>) config.getOrDefault("requiredFields", new ArrayList<>());
        }

        @Override
        public Map<String, Object> processIncoming(Map<String, Object> message) {
            validateMessage(message);
            return message;
        }

        @Override
        public Map<String, Object> processOutgoing(Map<String, Object> message) {
            validateMessage(message);
            return message;
        }

        private void validateMessage(Map<String, Object> message) {
            String type = (String) message.get("type");
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("Message type cannot be empty");
            }

            for (String field : requiredFields) {
                if (!message.containsKey(field) || message.get(field) == null) {
                    throw new IllegalArgumentException("Required field missing: " + field);
                }
            }
        }
    }
}