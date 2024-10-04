package com.weather.aggregation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseServerTest {

    @BeforeAll
    public static void setUp() throws Exception {
        // Start your server process here. Adjust as necessary.
        AggregationServer.main(new String[]{});

        // Wait for the server to be fully started
        Thread.sleep(5000); // Adjust this as needed
    }

    @AfterAll
    public static void tearDown() throws Exception {
        // Optionally add teardown logic if you need to stop the server
        // You can use a mechanism to gracefully shut down the server.
    }
}
