package com.weather.aggregation;

import java.time.Instant;
import java.util.Map;

public class WeatherEntry {
    private Map<String, String> data;
    private Instant timestamp;

    public WeatherEntry(Map<String, String> data) {
        this.data = data;
        this.timestamp = Instant.now(); // Record the current time when created
    }

    public Map<String, String> getData() {
        return data;
    }

    public void updateTimestamp() {
        this.timestamp = Instant.now(); // Update the timestamp when new data is added
    }

    public boolean isExpired() {
        // Check if the entry is older than 30 seconds
        return Instant.now().isAfter(timestamp.plusSeconds(30));
    }
}
