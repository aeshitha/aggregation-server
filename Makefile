# Variables
SRC_DIR = src/main/java
BIN_DIR = target/classes
MAIN_CLASS = com.weather.aggregation.AggregationServer

# Target to compile the source files
compile:
	mkdir -p $(BIN_DIR)
	javac -d $(BIN_DIR) $(SRC_DIR)/com/weather/aggregation/*.java

# Target to run the program
run:
	java -cp $(BIN_DIR) $(MAIN_CLASS)

# Clean target to remove compiled classes
clean:
	rm -rf $(BIN_DIR)

.PHONY: compile run clean
