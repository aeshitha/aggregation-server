package com.weather.aggregation;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class ContentServerTest extends BaseServerTest {

    private static final String TEST_FILE_PATH = "src/test/resources/sample_weather_data.txt";
    private static final String MOCK_SERVER_URL = "http://localhost:4567"; // Test server URL

    // Test to check file content is correctly read
    @Test
    public void testReadFileContent() throws IOException {
        // Simulate creating a simple input file
        String expectedContent = "id:IDS60901\nname:Adelaide (West Terrace / ngayirdapira)\nstate:SA";
        createTestFile(TEST_FILE_PATH, expectedContent);

        // Read file content
        StringBuilder content = new StringBuilder();
        BufferedReader fileReader = new BufferedReader(new FileReader(TEST_FILE_PATH));
        String line;
        while ((line = fileReader.readLine()) != null) {
            content.append(line);
        }
        fileReader.close();

        assertEquals(expectedContent.replace("\n", ""), content.toString());
    }

    // Test to check retry logic when server is unavailable
    @Test
    public void testRetryLogicWhenServerUnavailable() throws IOException, InterruptedException {
        System.out.println("Ensure server is down to test retry logic.");

        // Simulate ContentServer trying to send data to unavailable server
        String expectedContent = "id:IDS60901\nname:Adelaide (West Terrace / ngayirdapira)\nstate:SA";
        createTestFile(TEST_FILE_PATH, expectedContent);

        // Run ContentServer logic in a separate thread to observe retry behavior
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            ContentServer.main(new String[]{MOCK_SERVER_URL, TEST_FILE_PATH});
        });

        // Wait to observe retry attempts
        Thread.sleep(5000);

        executor.shutdown();
    }

    // Test for multiple ContentServer instances sending concurrent PUT requests
    @Test
    public void testMultipleContentServers() throws InterruptedException {
        // Create multiple threads to simulate multiple ContentServer instances
        ExecutorService executorService = Executors.newFixedThreadPool(5); // 5 concurrent ContentServers

        for (int i = 0; i < 5; i++) {
            final int instanceNumber = i;
            executorService.submit(() -> {
                try {
                    // Create unique content for each instance
                    String uniqueData = "id:IDS6090" + instanceNumber + "\nname:Adelaide-" + instanceNumber + "\nstate:SA";
                    createTestFile(TEST_FILE_PATH, uniqueData);

                    // Send data to the AggregationServer
                    URL url = new URL(MOCK_SERVER_URL + "/weather.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PUT");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Lamport-Clock", String.valueOf(instanceNumber));
                    connection.setDoOutput(true);

                    // Write JSON content to request body
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(uniqueData.getBytes());
                        os.flush();
                    }

                    // Get server response code
                    int responseCode = connection.getResponseCode();
                    assertEquals(HttpURLConnection.HTTP_CREATED, responseCode);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        // Allow time for all threads to complete
        Thread.sleep(3000);

        executorService.shutdown();
        System.out.println("Test for multiple ContentServer instances complete.");
    }

    // Helper method to create a sample file for testing
    private void createTestFile(String filePath, String content) throws IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(filePath);
        writer.write(content);
        writer.close();
    }
}
