# Java: Health Checks

Your task is to fix the health checks tool so that every command updates the data correctly and prints the correct success message or error reason. It reads the state from `data/health.json`, processes the `check`, `mute` and `unmute` commands, and writes the updated state back.

## Project Structure

```text
├── data/
│   └── health.json             # Persistent monitor state (every service's streaks and status)
├── src/
│   ├── main/java/com/health/
│   │   ├── Commands.java       # Command handlers
│   │   ├── FileIo.java         # State file load and save
│   │   ├── Main.java           # CLI entry point
│   │   ├── Models.java         # Data models
│   │   └── Parser.java         # JSON parsing
│   └── test/java/com/health/
│       └── CommandsTest.java
├── health                      # CLI launcher script
├── run.sh                      # Setup and run script
├── reset.sh                    # Restore data/health.json to its initial state
├── test.sh                     # Test script (writes junit.xml)
├── pom.xml                     # Maven build configuration
└── README.md
```

## Commands

### Install

```bash
mvn -q -DskipTests package
```

### Run

```bash
bash run.sh
```

### Test

```bash
bash test.sh
```

### Reset

Restore `data/health.json` to its initial state.

```bash
bash reset.sh
```

## Examples

Each example below runs independently against the initial `data/health.json` and shows only the records it changes.

### Example 1: A good ping extends the pass streak

Command:

```bash
./health check api up
```

Output:

```text
api up (10 in a row)
```

Changed records in `data/health.json`:

```json
{"service":"api","status":"ok","fails_in_row":0,"passes_in_row":10,"muted":false,"alerts":2}
```

### Example 2: A result the monitor does not record is rejected

Command:

```bash
./health check api sideways
```

Output:

```text
Error: INVALID_RESULT
```

`data/health.json`: unchanged.

### Example 3: Muting a service silences its alerts

Command:

```bash
./health mute notify
```

Output:

```text
notify muted
```

Changed records in `data/health.json`:

```json
{"service":"notify","status":"ok","fails_in_row":1,"passes_in_row":0,"muted":true,"alerts":0}
```

### Example 4: Turning alerts back on does not page for an outage already running

Command:

```bash
./health unmute search
```

Output:

```text
search unmuted
```

Changed records in `data/health.json`:

```json
{"service":"search","status":"alerting","fails_in_row":5,"passes_in_row":0,"muted":false,"alerts":4}
```

### Example 5: A service that was never checked is not found

Command:

```bash
./health mute ghost-service
```

Output:

```text
Error: SERVICE_NOT_FOUND
```

`data/health.json`: unchanged.
