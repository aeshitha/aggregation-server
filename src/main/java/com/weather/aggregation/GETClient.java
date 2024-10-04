package com.weather.aggregation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.ConnectException;

public class GETClient {

    private static final int MAX_RETRIES = 3; // Max retry attempts
    private static final int RETRY_DELAY_MS = 3000; // Delay between retries (3 seconds)
    private static final LamportClock lamportClock = new LamportClock(); // Initialize Lamport clock

    public static void main(String[] args) {
        String serverUrl = "http://localhost:4567/weather.json";
        if (args.length > 0) serverUrl = args[0];

        boolean success = false;
        int attempts = 0;

        while (!success && attempts < MAX_RETRIES) {
            try {
                attempts++;
                lamportClock.tick(); // Tick before sending request
                sendGetRequest(serverUrl);
                success = true;
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

    private static void sendGetRequest(String serverUrl) throws IOException {
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Lamport-Clock", String.valueOf(lamportClock.getTime()));

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String responseLine;
            StringBuilder response = new StringBuilder();
            while ((responseLine = in.readLine()) != null) {
                response.append(responseLine);
            }
            System.out.println("Server response: " + response.toString());

            // Update Lamport clock with server time
            int serverClock = extractClockFromResponse(connection);
            lamportClock.update(serverClock);
        }

        connection.disconnect();
    }

    private static int extractClockFromResponse(HttpURLConnection connection) {
        String clockHeader = connection.getHeaderField("Lamport-Clock");
        return (clockHeader != null) ? Integer.parseInt(clockHeader) : 0;
    }
}
