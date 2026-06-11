# 🏆 World Cup 2026 Discord Prediction Bot

A highly efficient, feature-rich, and low-resource Discord prediction bot written in **Java 21** using **Maven**, **JDA 5.x**, **Jackson**, and **OkHttp**. Specially optimized to run under tight hosting constraints (fits perfectly within **512 MB RAM** and **25% CPU**).

---

## ⚡ Key Features
*   **Smart API Polling Engine**: Automatically adjusts check intervals based on game schedules (30s during active games, 1m before game starts, 5m when idle) to minimize CPU/Network overhead.
*   **Interactive Gameplay Buttons**: Prediction controls are handled entirely via JDA button elements. No legacy Discord reactions.
*   **Secret User Abilities**:
    *   🎲 **Gambling** (Max 3 uses): Doubles match points on correct predictions.
    *   💣 **Sabotage** (Max 5 uses): Deducts half of the match points from target player's total. Works using Discord client-side `EntitySelectMenu` user targeting.
    *   👁 **Scouting** (Max 7 uses): Ephemerally leaks a target's prediction (Team 1, Draw, or Team 2).
*   **Robust State Persistence**: Writes data atomically directly to disk (`points.json`, `votes.json`, `abilities.json`, `matches.json`) to survive crashes/reboots, and schedules automated hourly backups.
*   **Admin Debugging Suite**: Full manual game controls (`!test <t1> <t2>`, `!test start`, and `!testr <t1><score1> <t2><score2>`) to simulate games and test outcomes on the fly.

---

## 📂 Project Structure
```
worldcupbot/
├── pom.xml                   # Maven dependencies & build plugins
├── config.json               # Bot tokens and target channels/roles
├── mock_api_matches.json     # Local mock matches playground (auto-generated)
└── src/
    └── main/
        ├── java/com/worldcup/
        │   ├── Main.java     # Entry point (cache flags disabled for low RAM)
        │   ├── config/       # Configurations mapper
        │   ├── database/     # Atomic storage & backup manager
        │   ├── models/       # Match, Stats, Votes & Abilities models
        │   ├── services/     # Polling scheduler & Discord message dispatcher
        │   └── listeners/    # Text commands & interaction handlers
        └── resources/
            └── logback.xml   # Optimized logging layout
```

---

## ⚙️ Configuration (`config.json`)
Make sure to fill in your credentials in `config.json` before compiling:
```json
{
  "discordToken": "YOUR_DISCORD_BOT_TOKEN",
  "apiToken": "YOUR_WORLD_CUP_API_KEY",
  "apiUrl": "WORLD_CUP_API_HERE", // Set to "WORLD_CUP_API_HERE" to enable local mock testing
  "channelId": "1472840652784926881",
  "mentionRoleId": "1407107176346161168",
  "ownerId": "1011280134923366450"
}
```

---

## 🛠️ Building & Running on Your Host

### Prerequisites
*   **JDK 21** or newer.
*   **Maven** installed on the host.

### 1. Compile the Project
Build the executable shaded JAR (fat JAR containing all dependencies):
```bash
mvn clean package
```
This outputs a single executable file at `target/worldcupbot-1.0-SNAPSHOT.jar`.

### 2. Run the Bot
To run the bot under tight memory limits (fits within 512 MB RAM):
```bash
java -Xmx256m -jar target/worldcupbot-1.0-SNAPSHOT.jar
```
*   The `-Xmx256m` flag restricts the JVM Heap size, keeping total resident memory usage (RSS) between **350-400 MB**.

---

## 🧪 Admin Test Commands
These commands are restricted to server Administrators and the Bot Owner:
*   `!test <t1> <t2>`: Instantly creates a test match and sends the prediction embed. Supported flag codes: `sa`, `usa`, `ar`, `fr`, `br`, `de`, `jp`.
*   `!test start`: Starts the test game immediately, disabling prediction buttons and ability options.
*   `!testr <t1><score1> <t2><score2>`: Ends the test game with scores (e.g., `!testr sa4 usa3`), awards points, processes ability multipliers or sabotage deductions, and sends the final match result embed.
*   `!po`: Displays the top 10 members in a premium graphical Embed.
*   `!po + <amount> @mention`: Manually grants points to a user.
*   `!po - <amount> @mention`: Manually deducts points from a user.
