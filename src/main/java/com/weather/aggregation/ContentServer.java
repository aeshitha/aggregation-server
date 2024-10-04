package com.weather.aggregation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

public class ContentServer {

    private static final int MAX_RETRIES = 3; // Max retry attempts
    private static final int RETRY_DELAY_MS = 3000; // Delay between retries (3 seconds)
    private static final LamportClock lamportClock = new LamportClock(); // Initialize Lamport clock

    public static void main(String[] args) {
        System.out.println("Arguments received:");
        for (String arg : args) {
            System.out.println(arg);
        }

        if (args.length < 2) {
            System.err.println("Usage: java ContentServer <server-url> <file-path>");
            return;
        }

        String serverUrl = args[0];
        String filePath = args[1];

        boolean success = false;
        int attempts = 0;

        while (!success && attempts < MAX_RETRIES) {
            try {
                attempts++;
                System.out.println("Attempt " + attempts + " to send data...");

                Map<String, String> weatherData = readWeatherDataFromFile(filePath);

                // Tick before sending the request
                lamportClock.tick();
                String jsonString = CustomJsonUtils.createJsonString(weatherData);

                System.out.println("JSON content to send: " + jsonString);

                URL url = new URL(serverUrl + "/weather.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock.getTime()));
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(jsonString.getBytes());
                    os.flush();
                }

                int responseCode = connection.getResponseCode();
                int serverClock = extractClockFromResponse(connection); // Extract clock from server
                lamportClock.update(serverClock); // Update Lamport clock with server clock

                System.out.println("Response code: " + responseCode);
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    System.out.println("Data successfully sent to server.");
                    success = true;
                } else {
                    throw new ConnectException("Failed to send PUT request: Unexpected response code.");
                }

            } catch (ConnectException e) {
                System.err.println("Server unavailable. Retry attempt " + attempts + "...");
                if (attempts < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.err.println("Max retries reached. Could not connect to server.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static Map<String, String> readWeatherDataFromFile(String filePath) throws IOException {
        Map<String, String> data = new HashMap<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                String[] keyValue = line.split(":", 2);
                if (keyValue.length == 2) {
                    data.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return data;
    }

    private static int extractClockFromResponse(HttpURLConnection connection) {
        String clockHeader = connection.getHeaderField("Lamport-Clock");
        return (clockHeader != null) ? Integer.parseInt(clockHeader) : 0;
    }
}
