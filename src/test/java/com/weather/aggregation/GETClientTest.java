package com.weather.aggregation;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class GETClientTest extends BaseServerTest {

    private static final String MOCK_SERVER_URL = "http://localhost:4567/weather.json";

    // Test for multiple clients sending concurrent GET requests
    @Test
    public void testMultipleGETClients() throws InterruptedException {
        // Create multiple threads to simulate multiple GETClient instances
        ExecutorService executorService = Executors.newFixedThreadPool(5); // 5 concurrent GETClients

        for (int i = 0; i < 5; i++) {
            executorService.submit(() -> {
                try {
                    // Send GET request to the AggregationServer
                    URL url = new URL(MOCK_SERVER_URL);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Lamport-Clock", "1");

                    // Get response code
                    int responseCode = connection.getResponseCode();
                    assertEquals(HttpURLConnection.HTTP_OK, responseCode);

                    // Read response content
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }

                        // Print the response
                        System.out.println("Client received: " + response.toString());

                        // Ensure response contains expected JSON data format
                        assertTrue(response.toString().contains("\"id\""));
                    }

                    connection.disconnect();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        // Allow time for all threads to complete
        Thread.sleep(3000);

        executorService.shutdown();
        System.out.println("Test for multiple GETClient instances complete.");
    }
}
