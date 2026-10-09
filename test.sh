#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Remove stale surefire report so a failed build cannot reuse old results.
surefire_xml="target/surefire-reports/TEST-com.health.CommandsTest.xml"
rm -f "$surefire_xml"

# Run Maven tests (allow failure so we can parse results).
mvn -B -ntp test 2>&1 || true

if [[ -f "$surefire_xml" ]]; then
    cp "$surefire_xml" junit.xml
else
    # Fallback: generate a minimal junit.xml indicating all tests failed.
    cat > junit.xml <<'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.health.CommandsTest" tests="6" failures="6">
  <testcase name="test_check_records_a_new_service_and_rejects_a_bad_result"><failure message="build failed" /></testcase>
  <testcase name="test_check_raises_the_alert_after_three_downs_in_a_row"><failure message="build failed" /></testcase>
  <testcase name="test_check_clears_the_failure_streak_after_a_good_ping"><failure message="build failed" /></testcase>
  <testcase name="test_check_recovers_a_service_after_two_good_pings"><failure message="build failed" /></testcase>
  <testcase name="test_mute_silences_the_alert_and_checks_rules_in_order"><failure message="build failed" /></testcase>
  <testcase name="test_unmute_turns_alerts_back_on_without_raising_one"><failure message="build failed" /></testcase>
</testsuite>
XMLEOF
fi

# Print colored terminal output from junit.xml.
PYTHON_BIN="python3"
if ! "$PYTHON_BIN" --version >/dev/null 2>&1; then
    PYTHON_BIN="python"
fi

"$PYTHON_BIN" - <<'PY' || true
import xml.etree.ElementTree as ET, sys

try:
    tree = ET.parse("junit.xml")
except Exception:
    print("\033[1;31mCould not parse junit.xml\033[0m")
    sys.exit(0)

root = tree.getroot()
suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")

total = 0
failures = 0

for suite in suites:
    for tc in suite.findall("testcase"):
        name = tc.get("name", "unknown")
        total += 1
        fail = tc.find("failure")
        err = tc.find("error")
        problem = fail if fail is not None else err
        if problem is not None:
            failures += 1
            print(f"\033[1;31mFAIL: {name}\033[0m")
            msg = problem.get("message", "") or problem.text or ""
            for line in msg.splitlines():
                stripped = line.strip()
                if stripped:
                    print(f"       {stripped}")
        else:
            print(f"\033[32mPASS: {name}\033[0m")

if failures > 0:
    print(f"\n\033[1;31m{failures} test(s) failed.\033[0m")
else:
    print(f"\n\033[32mAll {total} tests passed!\033[0m")
PY
