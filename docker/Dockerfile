# Optional reproducible Android build image; not an app server/runtime container.
FROM eclipse-temurin:17-jdk-jammy
ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle-cache
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip && rm -rf /var/lib/apt/lists/* \
 && mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/android-tools.zip \
 && unzip -q /tmp/android-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
 && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm /tmp/android-tools.zip \
 && yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
 && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
WORKDIR /workspace
COPY . /workspace
RUN chmod +x ./gradlew
CMD ["./gradlew", "testDebugUnitTest", "assembleDebug"]
