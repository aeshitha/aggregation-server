package com.weather.aggregation;

import java.util.*;

public class CustomJsonUtils {

    // Method to create a JSON string from a Map
    public static String createJsonString(Map<String, String> data) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");

        int size = data.size();
        int count = 0;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            jsonBuilder.append("\"").append(entry.getKey()).append("\":");
            jsonBuilder.append("\"").append(entry.getValue()).append("\"");
            count++;
            if (count < size) {
                jsonBuilder.append(",");
            }
        }

        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    // Method to parse a JSON string into a Map
    public static Map<String, String> parseJsonString(String jsonString) {
        Map<String, String> data = new HashMap<>();

        // Remove curly braces
        jsonString = jsonString.trim();
        if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
            jsonString = jsonString.substring(1, jsonString.length() - 1);
        } else {
            throw new IllegalArgumentException("Invalid JSON string");
        }

        // Split key-value pairs
        String[] pairs = jsonString.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2); // Split into key and value
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", ""); // Remove quotes
                String value = keyValue[1].trim().replace("\"", ""); // Remove quotes
                data.put(key, value);
            }
        }
        return data;
    }

}
