# Weight-Based Delivery System (Nasugbu Delivery Simulation)

An Android Kotlin delivery simulation application built with Jetpack Compose, MapLibre SDK, and OSRM turn-by-turn road routing. The app simulates real-time logistics, payload weight capacity constraints, dispatch workload distribution, and traffic delays for 5 delivery riders operating from the YTO Express Nasugbu Branch in Batangas, Philippines.

---

## 🛠️ Project Specifications & Environment

| Property | Value |
| :--- | :--- |
| **IDE** | Android Studio Ladybug (2024.2.2+) |
| **JDK Version** | JDK 11 (Java 11 compatibility) |
| **Compile SDK** | 34 |
| **Target SDK** | 34 |
| **Min SDK** | 25 (Android 7.1 Nougat) |
| **Language** | Kotlin |
| **UI Toolkit** | Jetpack Compose (Material 3) |
| **Map Engine** | MapLibre Native SDK for Android |
| **Routing Engine** | OSRM (Open Source Routing Machine) Public API |

---

## 🌐 Network & API Requirements

* **Internet Connection Required:** The app requires an active internet connection to fetch real-world road geometries from the OSRM Routing Engine (`https://router.project-osrm.org`) and load map tiles from OpenFreeMap (`https://tiles.openfreemap.org`).
* **No API Keys Required:** OSRM and OpenFreeMap public tile servers do not require any API keys or authentication credentials.

---

## 🚀 Setup & Execution Instructions

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/your-username/LIMED.git
   cd LIMED
   ```

2. **Open in Android Studio:**
   * Launch Android Studio (Ladybug 2024.2.2 or higher recommended).
   * Select **Open** and choose the project root folder.

3. **Gradle Sync:**
   * Android Studio will automatically perform a Gradle sync to download dependencies.
   * If prompted, click **Sync Project with Gradle Files**.

4. **Run the Application:**
   * Connect an Android device (API 25+) or launch an Android Virtual Device (AVD).
   * Click **Run 'app'** (`Shift + F10`) to deploy and start the simulation.

---

## 📦 Key Features

* **5-Rider Weight-Based Dispatch:** Vehicle-constrained dispatch (Motorcycles, L300 Vans, Heavy Cargo Truck).
* **OSRM Road Routing:** Real-world turn-by-turn routing with rate-limited request queuing, cache layer, and fallback mechanisms.
* **Deterministic Skip & Error Recovery:** Automatic skip handling for unreachable off-road targets and retry hard-capping.
* **Real-Time Simulation Engine:** 24-hour simulation clock starting at 6:00 AM with speed controls ($1\times, 10\times, 30\times, 60\times$), traffic delays, and unloading status tracking.
* **Interactive Map View:** Live rider tracking, destination pin markers, mismatch warnings, and interactive callouts.
