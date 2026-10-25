# SmartSight – AI Assistive Vision 👓

### Smart Navigation Glasses with Obstacle Detection and Heart Rate Monitoring

SmartSight is an AI-powered assistive technology project designed to help visually impaired individuals and elderly people navigate their surroundings more safely and independently. The system combines smart glasses hardware with an Android application to provide voice assistance, obstacle detection, GPS-based navigation, and heart rate monitoring.

The project aims to improve personal safety, mobility, and accessibility through wearable technology, sensors, and artificial intelligence.

## 🎯 Objectives

- Detect obstacles and nearby objects.
- Provide voice-based guidance through an Android application.
- Support GPS-based location tracking and navigation.
- Alert users about nearby obstacles using audio and vibration.
- Monitor heart rate using a MAX30102 sensor.
- Provide an SOS feature for emergencies.
- Support a hands-free and user-friendly experience.

## ✨ Key Features

### 1. AI-Based Obstacle Detection
The system is designed to identify obstacles in front of the user and provide timely warnings to help avoid collisions.

### 2. Voice Assistance
The Android application uses speech recognition and text-to-speech technology to support hands-free interaction. Users can give voice commands and receive spoken responses.

### 3. GPS Navigation
The application uses the phone's location services to obtain location information and support navigation-related features.

### 4. Vibration Alerts
A vibration motor provides tactile warnings when an obstacle or safety event requires the user's attention.

### 5. Heart Rate Monitoring
The MAX30102 sensor is intended to measure heart rate and provide readings through the system.

### 6. Emergency SOS
The planned emergency feature can help users request assistance and share their location with designated contacts.

### 7. Multilingual Assistance
The application is designed to support natural voice interaction in multiple languages, including English, Tamil, and Telugu.

## 🛠️ Technologies Used

### Software
- **Programming Language:** Kotlin
- **Development Environment:** Android Studio
- **User Interface:** Jetpack Compose
- **Voice Recognition:** Android SpeechRecognizer
- **Text-to-Speech:** Android TextToSpeech
- **Location Services:** Google Play Services Location API
- **Version Control:** Git and GitHub

### Hardware
- ESP32-S3 camera development board
- Camera module for visual input
- Time-of-Flight distance sensor for obstacle-distance measurement
- MPU6050 accelerometer and gyroscope
- MAX30102 heart rate sensor
- Vibration motor
- Rechargeable lithium-polymer battery

*Hardware integration and feature availability depend on the current prototype configuration.*

## 🏗️ System Architecture

The SmartSight system consists of three main components:

1. **Smart Glasses Hardware:** Collects sensor data and provides obstacle-related information.
2. **Android Application:** Handles voice interaction, location services, user communication, and application-level processing.
3. **User Alert System:** Communicates important information through spoken guidance and vibration alerts.

### Working Principle

1. Sensors collect information about the user's surroundings.
2. The hardware processes sensor readings and communicates available data to the Android application.
3. The application processes available information and determines the appropriate response.
4. Voice messages or vibration alerts notify the user.
5. Location and emergency features can assist the user when required.

## 📱 Android Application

The SmartSight Android application is developed using Kotlin and Jetpack Compose.

The application is designed to provide:
- Voice-based interaction.
- Spoken responses using text-to-speech.
- GPS location information.
- Obstacle alert demonstrations and sensor-status information.
- Heart rate status and future sensor integration.
- Emergency assistance functionality.

Some features may require additional hardware integration or configuration before they work with live sensor data.

## 🚀 Getting Started

### Prerequisites

- Android Studio
- Android SDK compatible with the project
- Kotlin and Gradle support
- An Android smartphone for testing

### Installation

1. Clone this repository:

   ```bash
   git clone https://github.com/YOUR-USERNAME/SmartSight-Android-App.git
   ```

2. Open Android Studio.

3. Select **Open** and choose the downloaded project folder.

4. Allow Gradle to synchronize the project.

5. Connect an Android smartphone or start an emulator.

6. Build and run the application.

Replace `YOUR-USERNAME` with your GitHub username and adjust the repository URL if your repository has a different name.

## 🔌 Hardware Integration

The planned hardware system uses an ESP32-S3-based board with a camera and additional sensors.

| Component | Purpose |
|---|---|
| ESP32-S3 camera board | Embedded processing and hardware communication |
| Camera | Visual information collection |
| ToF distance sensor | Distance measurement |
| MPU6050 | Motion and orientation sensing |
| MAX30102 | Heart rate measurement |
| Vibration motor | Tactile alerts |
| Android smartphone | Voice interaction, location, and application processing |

The availability of individual functions depends on sensor connectivity, firmware implementation, and communication between the hardware and Android application.

## 🔒 Privacy and Safety

- Location access requires the appropriate Android permissions.
- Emergency location sharing must be configured before use.
- Sensor readings should be validated before being used for safety-related decisions.
- The heart rate feature is intended for project demonstration and is not a substitute for medical equipment.
- SmartSight is an assistive prototype and should not be relied upon as the sole method of navigation or emergency protection.

## 🔮 Future Enhancements

- Real-time object recognition using AI.
- Improved obstacle detection and distance estimation.
- Reliable communication between the glasses hardware and Android application.
- Live heart rate monitoring through the MAX30102 sensor.
- Configurable emergency contacts and SOS location sharing.
- Expanded multilingual voice assistance.
- Improved battery efficiency and wearable design.
- Testing and evaluation in real-world environments.

## 👩‍💻 Project Status

**Development Status:** Prototype under development.

SmartSight is being developed as an assistive wearable technology project. The Android application and hardware components are being integrated progressively. Individual features may be at different stages of implementation and testing.

## 🤝 Contributions

Suggestions and contributions that improve accessibility, reliability, usability, and safety are welcome.

## 📄 License

A license has not yet been selected. Add an appropriate open-source license if you intend to allow others to reuse or modify this project.

## ❤️ Acknowledgement

SmartSight is developed with the goal of making everyday navigation more accessible through assistive technology, embedded systems, and mobile application development.

**SmartSight – Making Navigation Smarter, Safer, and More Accessible.** 👓
