# C++: Health Checks

A monitor checks each service regularly. It raises an alert after three failed checks in a row and clears the alert after two successful checks in a row, so a single failed check does not alert anyone but a real outage always does. A service can also be muted to pause its alerts. If one failed check raises an alert, a muted service still counts alerts, or a service that has recovered still shows as down, the team stops trusting the alerts.

Your task is to fix the health checks tool so that every command updates the data correctly and prints the correct success message or error reason.

## State File

**File: `data/health.json`**

```json
{
  "services": [
    {"service":"api","status":"ok","fails_in_row":0,"passes_in_row":9,"muted":false,"alerts":2},
    {"service":"search","status":"alerting","fails_in_row":5,"passes_in_row":0,"muted":true,"alerts":4}
  ]
}
```

Fields:

- `services` (list): every service checked so far, in the order first seen.
  - `service` (string): the service name.
  - `status` (string): `ok` or `alerting`.
  - `fails_in_row`, `passes_in_row` (integers): the current streaks; a check of one kind resets the other.
  - `muted` (boolean): whether alerts are silenced.
  - `alerts` (integer): how many alerts have been raised.

When writing `data/health.json` back, preserve the field order shown above, and keep services in the order they appear in the file.

## Command Output

Each command prints a one-line result on success, or `Error: <REASON>` on failure. Its rules are checked in the order listed, stopping at the first failure, and on failure `data/health.json` is not modified.

## Commands

### `check`

Records the result of a check for a service.

**Usage**

```bash
./health check <service> <up|down>
```

**Output**

```text
<service> up (<n> in a row)
<service> down (<n> in a row)
<service> ALERT: down <n> in a row
<service> recovered
```

**Rules**

- If the result is not `up` or `down`, fail with `INVALID_RESULT`.

On success:

- A service seen for the first time is appended as `ok`.
- A down check adds one to `fails_in_row` and resets `passes_in_row`. When the fails reach three and the status is `ok`, the status becomes `alerting`; unless the service is muted, `alerts` goes up by one and the ALERT line is printed.
- An up check adds one to `passes_in_row` and resets `fails_in_row`. When the passes reach two and the status is `alerting`, the status becomes `ok` and the recovered line is printed.

### `mute`

Pauses alerts for a service.

**Usage**

```bash
./health mute <service>
```

**Output**

```text
<service> muted
```

**Rules**

Check these rules in order and stop at the first failure:

- If the service has never been checked, fail with `SERVICE_NOT_FOUND`.
- If it is already muted, fail with `ALREADY_MUTED`.

On success, set `muted`.

### `unmute`

Resumes alerts for a muted service.

**Usage**

```bash
./health unmute <service>
```

**Output**

```text
<service> unmuted
```

**Rules**

Check these rules in order and stop at the first failure:

- If the service has never been checked, fail with `SERVICE_NOT_FOUND`.
- If it is not muted, fail with `NOT_MUTED`.

On success, clear `muted`; a service that is already alerting does not raise a new alert.

> **Note:** Refer to `README.md` for more details and examples.
