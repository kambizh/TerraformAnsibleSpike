#!/bin/bash

# Run Task Service Startup Script

echo "Starting Terraform Run Task Service..."

# Check Java version
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Error: Java 17 or higher is required"
    exit 1
fi

# Check if built
if [ ! -f "target/run-task-service-1.0.0.jar" ]; then
    echo "Building project..."
    mvn clean package -DskipTests
fi

# Run the service
echo "Starting service on port 8080..."
java -jar target/run-task-service-1.0.0.jar

