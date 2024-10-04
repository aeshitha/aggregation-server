package com.weather.aggregation;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class AggregationServerTest extends BaseServerTest {

    private final String serverUrl = "http://localhost:4567";

    // Test sending a valid PUT request to the server
    @Test
    public void testPutRequestWithValidData() throws IOException {
        String jsonString = "{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace / ngayirdapira)\",\"state\":\"SA\"}";

        // Send PUT request
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setRequestProperty("Lamport-Clock", "1"); // Set initial Lamport clock

        // Write JSON data to request body
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonString.getBytes());
            os.flush();
        }

        // Assert response code is 201 Created
        int responseCode = connection.getResponseCode();
        assertEquals(HttpURLConnection.HTTP_CREATED, responseCode);

        // Close the connection
        connection.disconnect();
    }

    // Test sending a GET request to retrieve data from the server
    @Test
    public void testGetRequestReturnsData() throws IOException {
        // Send GET request
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Lamport-Clock", "1");

        // Read the response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String responseLine;
            StringBuilder response = new StringBuilder();
            while ((responseLine = in.readLine()) != null) {
                response.append(responseLine);
            }

            // Assert response contains expected JSON data
            assertTrue(response.toString().contains("\"id\":\"IDS60901\""));
        }

        // Close the connection
        connection.disconnect();
    }

    // Test sending an invalid JSON in the PUT request
    @Test
    public void testPutRequestWithInvalidData() throws IOException {
        String invalidJsonString = "{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace / ngayirdapira)\""; // Missing closing bracket

        // Send PUT request
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Write invalid JSON data to request body
        try (OutputStream os = connection.getOutputStream()) {
            os.write(invalidJsonString.getBytes());
            os.flush();
        }

        // Assert response code is 500 Internal Server Error
        int responseCode = connection.getResponseCode();
        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, responseCode);

        // Close the connection
        connection.disconnect();
    }

    // Test concurrent GET requests to check if server handles concurrency properly
    @Test
    public void testConcurrentGetRequests() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10); // 10 concurrent clients
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    URL url = new URL(serverUrl + "/weather.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Lamport-Clock", "1");

                    int responseCode = connection.getResponseCode();
                    assertEquals(HttpURLConnection.HTTP_OK, responseCode);

                    connection.disconnect();
                } catch (IOException e) {
                    fail("GET request failed: " + e.getMessage());
                }
                return null;
            });
        }

        // Execute tasks
        executor.invokeAll(tasks);
        executor.shutdown();
    }

    // Test concurrent PUT requests to check if server handles updates properly
    @Test
    public void testConcurrentPutRequests() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10); // 10 concurrent content servers
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int index = i;
            tasks.add(() -> {
                try {
                    String jsonString = "{\"id\":\"IDS60901_" + index + "\",\"name\":\"Adelaide_" + index + "\",\"state\":\"SA\"}";

                    URL url = new URL(serverUrl + "/weather.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PUT");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Lamport-Clock", String.valueOf(index));
                    connection.setDoOutput(true);

                    // Write JSON data to request body
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(jsonString.getBytes());
                        os.flush();
                    }

                    // Assert response code is 201 Created
                    int responseCode = connection.getResponseCode();
                    assertEquals(HttpURLConnection.HTTP_CREATED, responseCode);

                    connection.disconnect();
                } catch (IOException e) {
                    fail("PUT request failed: " + e.getMessage());
                }
                return null;
            });
        }

        // Execute tasks
        executor.invokeAll(tasks);
        executor.shutdown();
    }


    @Test
    public void testDataExpiry() throws IOException, InterruptedException {
        // Send a PUT request to add a weather entry
        String jsonString = "{\"id\":\"EXPIRY_TEST\",\"name\":\"Test Entry\",\"state\":\"SA\"}";
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Lamport-Clock", "1");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonString.getBytes());
            os.flush();
        }

        // Verify that the server accepted the PUT request
        int responseCode = connection.getResponseCode();
        assertEquals(HttpURLConnection.HTTP_CREATED, responseCode);

        connection.disconnect();

        // Wait for the expiry interval + a buffer to ensure the entry is expired
        Thread.sleep(35000);

        // Send a GET request to verify that the entry has expired
        url = new URL(serverUrl + "/weather.json");
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Read the response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String responseLine;
            StringBuilder response = new StringBuilder();
            while ((responseLine = in.readLine()) != null) {
                response.append(responseLine);
            }

            // Verify that the response no longer contains the expired entry
            assertFalse(response.toString().contains("\"id\":\"EXPIRY_TEST\""));
        }

        connection.disconnect();
    }


    @Test
    public void testSelectiveDataExpiry() throws IOException, InterruptedException {
        // Send a PUT request to add a weather entry
        String jsonString = "{\"id\":\"EXPIRY_TEST\",\"name\":\"Test Entry\",\"state\":\"SA\"}";
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Lamport-Clock", "1");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonString.getBytes());
            os.flush();
        }

        // Verify that the server accepted the PUT request
        int responseCode = connection.getResponseCode();
        assertEquals(HttpURLConnection.HTTP_CREATED, responseCode);

        connection.disconnect();

        // Wait for the expiry interval + a buffer to ensure the entry is expired
        Thread.sleep(40000);

        // Send a GET request to verify that the entry has expired
        url = new URL(serverUrl + "/weather.json");
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Read the response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String responseLine;
            StringBuilder response = new StringBuilder();
            while ((responseLine = in.readLine()) != null) {
                response.append(responseLine);
            }

            // Verify that the response no longer contains the expired entry
            assertFalse(response.toString().contains("\"id\":\"EXPIRY_TEST\""), "Expired entry was found in the response.");
        }

        connection.disconnect();
    }



    @Test
    public void testLamportClockSynchronization() throws IOException, InterruptedException {
        // Initial Lamport clock value
        int initialClock = 0;

        // Send a PUT request with an initial Lamport clock value
        String jsonString = "{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace)\",\"state\":\"SA\"}";
        URL url = new URL(serverUrl + "/weather.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Lamport-Clock", String.valueOf(initialClock));
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonString.getBytes());
            os.flush();
        }

        // Verify that the server returns a Lamport clock greater than or equal to the initial clock + 1
        int responseClock = Integer.parseInt(connection.getHeaderField("Lamport-Clock"));
        assertTrue(responseClock >= initialClock + 1);

        // Store the updated clock value for the next request
        int updatedClock = responseClock;

        connection.disconnect();

        // Send a GET request and ensure the Lamport clock is correctly maintained
        url = new URL(serverUrl + "/weather.json");
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Lamport-Clock", String.valueOf(updatedClock));

        // Verify that the server returns a Lamport clock greater than or equal to the previous clock
        responseClock = Integer.parseInt(connection.getHeaderField("Lamport-Clock"));
        assertTrue(responseClock >= updatedClock);

        connection.disconnect();
    }

    @Test
    public void testLamportClockMultiplePUTRequests() throws IOException, InterruptedException {
        int initialClock = 0;
        String[] jsonStrings = {
                "{\"id\":\"IDS60901\",\"name\":\"Entry1\",\"state\":\"SA\"}",
                "{\"id\":\"IDS60902\",\"name\":\"Entry2\",\"state\":\"SA\"}"
        };

        for (String jsonString : jsonStrings) {
            URL url = new URL(serverUrl + "/weather.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Lamport-Clock", String.valueOf(initialClock));
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonString.getBytes());
                os.flush();
            }

            // Get updated Lamport clock
            int responseClock = Integer.parseInt(connection.getHeaderField("Lamport-Clock"));
            assertTrue(responseClock >= initialClock + 1);
            initialClock = responseClock;

            connection.disconnect();
        }
    }




    // Test for concurrent PUT and GET requests to check overall server behavior
    @Test
    public void testConcurrentPutAndGetRequests() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20); // Mix of 10 PUTs and 10 GETs
        List<Callable<Void>> tasks = new ArrayList<>();

        // 10 PUT requests
        for (int i = 0; i < 10; i++) {
            final int index = i;
            tasks.add(() -> {
                try {
                    String jsonString = "{\"id\":\"IDS60901_" + index + "\",\"name\":\"Adelaide_" + index + "\",\"state\":\"SA\"}";

                    URL url = new URL(serverUrl + "/weather.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PUT");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Lamport-Clock", String.valueOf(index));
                    connection.setDoOutput(true);

                    // Write JSON data to request body
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(jsonString.getBytes());
                        os.flush();
                    }

                    // Assert response code is 201 Created
                    int responseCode = connection.getResponseCode();
                    assertEquals(HttpURLConnection.HTTP_CREATED, responseCode);

                    connection.disconnect();
                } catch (IOException e) {
                    fail("PUT request failed: " + e.getMessage());
                }
                return null;
            });
        }

        // 10 GET requests
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    URL url = new URL(serverUrl + "/weather.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Lamport-Clock", "1");

                    int responseCode = connection.getResponseCode();
                    assertEquals(HttpURLConnection.HTTP_OK, responseCode);

                    connection.disconnect();
                } catch (IOException e) {
                    fail("GET request failed: " + e.getMessage());
                }
                return null;
            });
        }

        // Execute tasks
        executor.invokeAll(tasks);
        executor.shutdown();
    }
}
