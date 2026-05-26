FROM gradle:8.5-jdk17

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_HOME/cmdline-tools/latest && \
    cd /tmp && \
    curl -L https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline-tools.zip && \
    unzip cmdline-tools.zip && \
    mv cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/ && \
    rm cmdline-tools.zip

RUN mkdir -p $ANDROID_HOME/licenses && \
    echo "d975f751698a77b662f1254ddbeed3901e976f5a22d0c7a5026f44e186facd76" > $ANDROID_HOME/licenses/android-sdk-license && \
    echo "84831b9409646a918e30573bab4c9c91346d8abd" > $ANDROID_HOME/licenses/android-sdk-preview-license && \
    yes | sdkmanager --licenses

RUN yes | sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"

WORKDIR /app
COPY . .
RUN chmod +x gradlew

CMD ["./gradlew", "assembleDebug", "--no-daemon", "--stacktrace"]