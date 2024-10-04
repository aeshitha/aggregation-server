# Makefile

# Variables
SRC_DIR = src/main/java
BIN_DIR = target/classes

# Compile all Java source files
compile:
	mkdir -p $(BIN_DIR)
	javac -d $(BIN_DIR) $(SRC_DIR)/com/weather/aggregation/*.java

# Run AggregationServer
run-server:
	java -cp $(BIN_DIR) com.weather.aggregation.AggregationServer

# Run ContentServer
run-content:
	java -cp $(BIN_DIR) com.weather.aggregation.ContentServer

# Run GETClient
run-client:
	java -cp $(BIN_DIR) com.weather.aggregation.GETClient

# Clean the build directory
clean:
	rm -rf $(BIN_DIR)
