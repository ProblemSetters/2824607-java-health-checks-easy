package com.health;

import com.health.Models.CommandResult;
import com.health.Models.Reason;
import com.health.Models.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandsTest {

    private static void assertOk(CommandResult result, String expectedMessage) {
        assertTrue(result.ok, () -> "expected ok with \"" + expectedMessage + "\", got ok=" + result.ok + " reason=" + result.reason);
        assertEquals(expectedMessage, result.message);
    }

    private static void assertError(CommandResult result, Reason expectedReason) {
        assertFalse(result.ok, () -> "expected error " + expectedReason + ", got ok=" + result.ok + " message=\"" + result.message + "\"");
        assertEquals(expectedReason, result.reason);
    }

    private static void assertHealthJson(State state, String expectedJson) {
        assertEquals(expectedJson, FileIo.healthToJson(state));
    }

    /// Build a State from a data/health.json document.
    private static State makeState(String doc) {
        return Parser.parseHealthJson(doc);
    }

    /// check adds a service the first time its name arrives, counts that check against it, keeps the
    /// services in the order they were first seen, and refuses a result that is neither up nor down
    /// without recording anything.
    @Test
    public void test_check_records_a_new_service_and_rejects_a_bad_result() {
        State state = makeState("""
{
  "services": [
    {"service":"api","status":"ok","fails_in_row":1,"passes_in_row":0,"muted":false,"alerts":2},
    {"service":"report-export","status":"ok","fails_in_row":0,"passes_in_row":14,"muted":true,"alerts":6}
  ]
}""");

        CommandResult firstSeen = Commands.check(state, "queue-drain", "down");        // a name the file has never carried
        CommandResult seenAgain = Commands.check(state, "queue-drain", "down");        // the new service counts its own streak
        CommandResult anotherNew = Commands.check(state, "session-store", "up");       // arrives behind the first new one
        CommandResult badWord = Commands.check(state, "queue-drain", "flapping");      // neither up nor down
        CommandResult badWordNewName = Commands.check(state, "cdn-purge", "maybe");  // refused before the name is added

        assertOk(firstSeen, "queue-drain down (1 in a row)");
        assertOk(seenAgain, "queue-drain down (2 in a row)");
        assertOk(anotherNew, "session-store up (1 in a row)");
        assertError(badWord, Reason.INVALID_RESULT);
        assertError(badWordNewName, Reason.INVALID_RESULT);

        assertHealthJson(state, """
{
  "services": [
    {"service":"api","status":"ok","fails_in_row":1,"passes_in_row":0,"muted":false,"alerts":2},
    {"service":"report-export","status":"ok","fails_in_row":0,"passes_in_row":14,"muted":true,"alerts":6},
    {"service":"queue-drain","status":"ok","fails_in_row":2,"passes_in_row":0,"muted":false,"alerts":0},
    {"service":"session-store","status":"ok","fails_in_row":0,"passes_in_row":1,"muted":false,"alerts":0}
  ]
}""");
    }

    /// The third failure in a row on a healthy service raises the alert, counts it and says so, while
    /// the first two only report the running count and end any good run behind them; a service that is
    /// already alerting is not counted a second time.
    @Test
    public void test_check_raises_the_alert_after_three_downs_in_a_row() {
        State state = makeState("""
{
  "services": [
    {"service":"api","status":"ok","fails_in_row":2,"passes_in_row":0,"muted":false,"alerts":1},
    {"service":"checkout","status":"ok","fails_in_row":0,"passes_in_row":4,"muted":false,"alerts":0},
    {"service":"pdf-render","status":"ok","fails_in_row":0,"passes_in_row":3,"muted":false,"alerts":1},
    {"service":"image-resize","status":"alerting","fails_in_row":0,"passes_in_row":1,"muted":false,"alerts":3}
  ]
}""");

        CommandResult oneBlip = Commands.check(state, "checkout", "down");   // one bad ping wakes nobody, and the good run ends
        CommandResult twoBlips = Commands.check(state, "checkout", "down");  // one short of the threshold
        CommandResult thirdInARow = Commands.check(state, "api", "down");  // api was two down already, so this one pages
        CommandResult stillDown = Commands.check(state, "api", "down");      // already alerting, so nobody is paged twice
        CommandResult relapseOne = Commands.check(state, "image-resize", "down");    // one good ping never cleared its alert
        CommandResult relapseTwo = Commands.check(state, "image-resize", "down");
        CommandResult relapseThree = Commands.check(state, "image-resize", "down");  // three in a row again, still the same alert

        assertOk(oneBlip, "checkout down (1 in a row)");
        assertOk(twoBlips, "checkout down (2 in a row)");
        assertOk(thirdInARow, "api ALERT: down 3 in a row");
        assertOk(stillDown, "api down (4 in a row)");
        assertOk(relapseOne, "image-resize down (1 in a row)");
        assertOk(relapseTwo, "image-resize down (2 in a row)");
        assertOk(relapseThree, "image-resize down (3 in a row)");

        assertHealthJson(state, """
{
  "services": [
    {"service":"api","status":"alerting","fails_in_row":4,"passes_in_row":0,"muted":false,"alerts":2},
    {"service":"checkout","status":"ok","fails_in_row":2,"passes_in_row":0,"muted":false,"alerts":0},
    {"service":"pdf-render","status":"ok","fails_in_row":0,"passes_in_row":3,"muted":false,"alerts":1},
    {"service":"image-resize","status":"alerting","fails_in_row":3,"passes_in_row":0,"muted":false,"alerts":3}
  ]
}""");
    }

    /// A good ping clears the count of failures and starts the good run, so the failure after it counts
    /// as the first of a new run and two failures either side of a good ping never reach the threshold.
    @Test
    public void test_check_clears_the_failure_streak_after_a_good_ping() {
        State state = makeState("""
{
  "services": [
    {"service":"notify","status":"ok","fails_in_row":2,"passes_in_row":0,"muted":false,"alerts":3},
    {"service":"cache-warmer","status":"ok","fails_in_row":0,"passes_in_row":7,"muted":false,"alerts":0}
  ]
}""");

        CommandResult goodPing = Commands.check(state, "notify", "up");    // two failures behind it, and the bad run ends here
        CommandResult badAgain = Commands.check(state, "notify", "down");  // the first failure of a new run
        CommandResult badTwice = Commands.check(state, "notify", "down");  // still one short, the good ping cleared the count
        CommandResult goodAgain = Commands.check(state, "notify", "up");   // the second run of failures is cleared as well

        assertOk(goodPing, "notify up (1 in a row)");
        assertOk(badAgain, "notify down (1 in a row)");
        assertOk(badTwice, "notify down (2 in a row)");
        assertOk(goodAgain, "notify up (1 in a row)");

        assertHealthJson(state, """
{
  "services": [
    {"service":"notify","status":"ok","fails_in_row":0,"passes_in_row":1,"muted":false,"alerts":3},
    {"service":"cache-warmer","status":"ok","fails_in_row":0,"passes_in_row":7,"muted":false,"alerts":0}
  ]
}""");
    }

    /// One good ping is not enough to clear an alert; the second says the service recovered, muted or
    /// not, leaves no failures behind it, keeps the good run counting afterwards, and does not take back
    /// an alert that was already raised.
    @Test
    public void test_check_recovers_a_service_after_two_good_pings() {
        State state = makeState("""
{
  "services": [
    {"service":"auth","status":"alerting","fails_in_row":1,"passes_in_row":0,"muted":true,"alerts":4},
    {"service":"image-resize","status":"ok","fails_in_row":0,"passes_in_row":9,"muted":false,"alerts":3}
  ]
}""");

        CommandResult firstGood = Commands.check(state, "auth", "up");   // one good ping is not a recovery
        CommandResult secondGood = Commands.check(state, "auth", "up");  // two in a row and the alert is cleared

        assertOk(firstGood, "auth up (1 in a row)");
        assertOk(secondGood, "auth recovered");

        assertHealthJson(state, """
{
  "services": [
    {"service":"auth","status":"ok","fails_in_row":0,"passes_in_row":2,"muted":true,"alerts":4},
    {"service":"image-resize","status":"ok","fails_in_row":0,"passes_in_row":9,"muted":false,"alerts":3}
  ]
}""");
    }

    /// mute silences a service and says so; its third failure in a row still marks the service as
    /// alerting and reports the plain count, but counts no alert and sends no page; muting it twice and
    /// muting a service nobody has checked are both refused, the unknown name before its mute state.
    @Test
    public void test_mute_silences_the_alert_and_checks_rules_in_order() {
        State state = makeState("""
{
  "services": [
    {"service":"billing-worker","status":"ok","fails_in_row":2,"passes_in_row":0,"muted":false,"alerts":1},
    {"service":"mail-relay","status":"ok","fails_in_row":0,"passes_in_row":12,"muted":true,"alerts":4}
  ]
}""");

        CommandResult silenced = Commands.mute(state, "billing-worker");                 // two failures in and nobody wants the page
        CommandResult thirdInARow = Commands.check(state, "billing-worker", "down");  // the outage is real, the page is not sent
        CommandResult silencedAgain = Commands.mute(state, "billing-worker");           // already silenced
        CommandResult unknown = Commands.mute(state, "cdn-purge");                       // never checked, so the name answers first

        assertOk(silenced, "billing-worker muted");
        assertOk(thirdInARow, "billing-worker down (3 in a row)");
        assertError(silencedAgain, Reason.ALREADY_MUTED);
        assertError(unknown, Reason.SERVICE_NOT_FOUND);

        assertHealthJson(state, """
{
  "services": [
    {"service":"billing-worker","status":"alerting","fails_in_row":3,"passes_in_row":0,"muted":true,"alerts":1},
    {"service":"mail-relay","status":"ok","fails_in_row":0,"passes_in_row":12,"muted":true,"alerts":4}
  ]
}""");
    }

    /// unmute turns a service's alerts back on and says so without counting one for the outage already
    /// running, and a further failure on a service that is already alerting counts none either;
    /// unmuting a service that is not muted and one nobody has checked are both refused, the unknown
    /// name before its mute state.
    @Test
    public void test_unmute_turns_alerts_back_on_without_raising_one() {
        State state = makeState("""
{
  "services": [
    {"service":"search","status":"alerting","fails_in_row":5,"passes_in_row":0,"muted":true,"alerts":4},
    {"service":"thumbnailer","status":"ok","fails_in_row":0,"passes_in_row":6,"muted":true,"alerts":1}
  ]
}""");

        CommandResult reopened = Commands.unmute(state, "search");           // the outage is already known, so nothing is raised
        CommandResult stillDown = Commands.check(state, "search", "down");  // already alerting, so this counts no alert either
        CommandResult reopenedAgain = Commands.unmute(state, "search");     // alerts are on again
        CommandResult unknown = Commands.unmute(state, "cdn-purge");         // never checked, so the name answers first
        CommandResult healthy = Commands.unmute(state, "thumbnailer");       // muted while healthy, so there was never a page to restore

        assertOk(reopened, "search unmuted");
        assertOk(stillDown, "search down (6 in a row)");
        assertError(reopenedAgain, Reason.NOT_MUTED);
        assertError(unknown, Reason.SERVICE_NOT_FOUND);
        assertOk(healthy, "thumbnailer unmuted");

        assertHealthJson(state, """
{
  "services": [
    {"service":"search","status":"alerting","fails_in_row":6,"passes_in_row":0,"muted":false,"alerts":4},
    {"service":"thumbnailer","status":"ok","fails_in_row":0,"passes_in_row":6,"muted":false,"alerts":1}
  ]
}""");
    }
}
