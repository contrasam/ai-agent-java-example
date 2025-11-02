#!/bin/bash

# Load environment variables from .env file
if [ -f .env ]; then
    echo "Loading environment variables from .env file..."
    export $(grep -v '^#' .env | grep -v '^//' | xargs)
else
    echo "Warning: .env file not found"
fi

# Run the application
echo "Starting Appointment Scheduling Agent..."
echo ""
./gradlew run -q --console=plain

