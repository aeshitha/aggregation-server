package com.weather.aggregation;

public class LamportClock {
    private int clock;

    public LamportClock() {
        this.clock = 0;
    }

    // Increment the local clock time
    public synchronized void tick() {
        clock++;
    }

    // Update the local clock based on received clock time
    public synchronized void update(int receivedClock) {
        clock = Math.max(clock, receivedClock) + 1;
    }

    // Get the current time of the Lamport clock
    public synchronized int getTime() {
        return clock;
    }
}
