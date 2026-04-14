# Airline Reservation System

A Java-based airline reservation system with a local web interface. The app starts a local HTTP server and serves a browser UI at `http://localhost:8080`.

---

## Features

- Route search by cheapest or fastest path
- Booking queue with loyalty-tier priority
- Flight network viewer
- Dijkstra performance benchmark

---

## Requirements

- Java JDK 8 or later

---

## Project Structure

```
src/main/java/org/example/
├── AirlineReservationSystem.java   # Entry point — starts the app
├── AirlineWebServer.java           # Serves the web UI and API endpoints
└── AirlineSystem.java              # Graph, booking queue, Dijkstra logic, and benchmark
```

---

## Option 1 — Run from PowerShell

### Step 1 — Compile

From the project root, run:

```powershell
javac -d build\tmp\manual-compile src\main\java\org\example\*.java
```

> **Java not on PATH?** Use the full JDK path instead:
> ```powershell
> & 'C:\Program Files\Java\jdk1.8.0_201\bin\javac.exe' -d build\tmp\manual-compile src\main\java\org\example\*.java
> ```

### Step 2 — Run

```powershell
java -cp build\tmp\manual-compile org.example.AirlineReservationSystem
```

> **Java not on PATH?** Use the full JDK path instead:
> ```powershell
> & 'C:\Program Files\Java\jdk1.8.0_201\bin\java.exe' -cp build\tmp\manual-compile org.example.AirlineReservationSystem
> ```

### Step 3 — Open the app

Once the server starts, open your browser and go to:

```
http://localhost:8080
```

Keep the terminal open while using the app — closing it stops the server.

---

## Option 2 — Run from IntelliJ IDEA

### One-time setup

1. Open IntelliJ IDEA.
2. Go to **File > Open** and select the project root folder.
3. Go to **File > Project Structure > Project** and set the SDK to JDK 8 or later.
4. If IntelliJ detects a `build.gradle` or `pom.xml`, click **Import** when prompted — this sets up the classpath automatically.

### Running the app

1. In the **Project** panel, navigate to:
   ```
   src/main/java/org/example/AirlineReservationSystem.java
   ```
2. Open the file.
3. Click the green **play button** in the gutter next to the `main` method, or right-click the file and select **Run 'AirlineReservationSystem.main()'**.

### Open the app

Once the server starts, open your browser and go to:

```
http://localhost:8080
```

> **Tip:** To make launching easier in the future, IntelliJ saves the run configuration automatically. You can re-run the app anytime from the toolbar using the green **Run** button at the top.

---

## API Endpoints

The web UI communicates with these backend endpoints:

| Endpoint | Description |
|---|---|
| `/api/cities` | List of available cities |
| `/api/network` | Full flight network data |
| `/api/route` | Route search (cheapest or fastest) |
| `/api/bookings` | View current bookings |
| `/api/bookings/process` | Process the booking queue |
| `/api/benchmark` | Run Dijkstra performance benchmark |

---

## Troubleshooting

### Port 8080 is already in use

If the app fails to start, another process may be using port 8080. Run this in PowerShell to free it:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

Then try running the app again.

### Browser shows outdated UI

- Try a normal page refresh.
- If the issue persists, do a hard refresh (`Ctrl + Shift + R`) to clear cached JavaScript and CSS.

### `javac` or `java` not found

Use the full JDK path as shown in the PowerShell instructions above, or add your JDK `bin` folder to your system `PATH`.

---

## Notes

- Flight data is hardcoded in the Java source.
- All data is stored in memory and resets when the app is closed.
- Closing the Java process (or IntelliJ's run window) stops the server.



