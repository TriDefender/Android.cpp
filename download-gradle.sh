#!/bin/bash

echo "Downloading gradle wrapper..."
curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar
echo "Download complete"