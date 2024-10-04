package com.weather.aggregation;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationServer {
    private static final int PORT = 4567;
    private static final ConcurrentHashMap<String, WeatherEntry> weatherData = new ConcurrentHashMap<>();
    private static final LamportClock lamportClock = new LamportClock(); // Initialize Lamport clock

    public static void main(String[] args) {
        startExpiryChecker(); // Start the expiry checker thread
        //main logic for server startup
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Aggregation server started on port " + PORT);

            //accept client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //periodically check and remove expired weather data entries
    public static void startExpiryChecker() {
        new Thread(() -> {
            while (true) {
                try {
                    // Sleep interval during testing
                    Thread.sleep(5000);

                    System.out.println("Running expiry checker...");

                    // Check for and remove expired entries
                    weatherData.entrySet().removeIf(entry -> {
                        boolean expired = entry.getValue().isExpired();
                        if (expired) {
                            System.out.println("Removing expired entry: " + entry.getKey());
                        }
                        return expired;
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // Exit if interrupted
                }
            }
        }).start();
    }


    // Inner class to handle client requests
    static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String line = in.readLine();
                if (line.startsWith("GET")) {
                    handleGetRequest(out);
                } else if (line.startsWith("PUT")) {
                    handlePutRequest(in, out);
                } else {
                    out.println("HTTP/1.1 400 Bad Request");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Handle GET request to return stored weather data in JSON format
        private void handleGetRequest(PrintWriter out) {
            lamportClock.tick(); // Increment the clock on a GET request

            StringBuilder responseJson = new StringBuilder();
            responseJson.append("{");

            int count = 0;
            for (Map.Entry<String, WeatherEntry> entry : weatherData.entrySet()) {
                String id = entry.getKey();
                Map<String, String> data = entry.getValue().getData();

                responseJson.append("\"").append(id).append("\":");
                responseJson.append(CustomJsonUtils.createJsonString(data));

                count++;
                if (count < weatherData.size()) {
                    responseJson.append(",");
                }
            }
            responseJson.append("}");

            // Return JSON data
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Lamport-Clock: " + lamportClock.getTime());
            out.println();
            out.println(responseJson.toString());
            out.flush();
        }

        // Handle PUT request to store incoming weather data
        private void handlePutRequest(BufferedReader in, PrintWriter out) throws IOException {
            StringBuilder payload = new StringBuilder();
            String line;
            int contentLength = 0;

            // Read headers
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(": ")[1].trim());
                } else if (line.startsWith("Lamport-Clock:")) {
                    int receivedClock = Integer.parseInt(line.split(": ")[1].trim());
                    lamportClock.update(receivedClock); // Update Lamport clock with received value
                }
            }

            // Read the payload according to the content length
            int readChars = 0;
            while (readChars < contentLength) {
                int c = in.read();
                if (c == -1) break;
                payload.append((char) c);
                readChars++;
            }

            // Parse JSON and store it
            try {
                if (payload.toString().isEmpty() || payload.toString().equals("{}")) {
                    out.println("HTTP/1.1 400 Bad Request");
                    out.println();
                    out.flush();
                    return;
                }

                Map<String, String> parsedData = CustomJsonUtils.parseJsonString(payload.toString());

                if (!parsedData.containsKey("id")) {
                    out.println("HTTP/1.1 400 Bad Request");
                    out.println();
                    out.flush();
                    return;
                }

                // Store the parsed data in the weatherData map
                String id = parsedData.get("id");
                WeatherEntry weatherEntry = new WeatherEntry(parsedData);
                weatherData.put(id, weatherEntry);

                lamportClock.tick(); // Tick the clock for this PUT request

                out.println("HTTP/1.1 201 Created");
                out.println("Lamport-Clock: " + lamportClock.getTime());
                out.println();
                out.flush();
            } catch (Exception e) {
                out.println("HTTP/1.1 500 Internal Server Error");
                out.println();
                out.flush();
                e.printStackTrace();
            }
        }
    }

}
