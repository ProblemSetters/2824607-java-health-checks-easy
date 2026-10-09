package com.health;

import com.health.Models.CommandResult;
import com.health.Models.Reason;
import com.health.Models.State;
import org.junit.jupiter.api.DisplayName;
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

    private static State makeState(String doc) {
        return Parser.parseHealthJson(doc);
    }

    @Test
    @DisplayName("check records a new service and rejects a bad result")
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

    @Test
    @DisplayName("check raises the alert after three downs in a row")
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

    @Test
    @DisplayName("check clears the failure streak after a good ping")
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

    @Test
    @DisplayName("check recovers a service after two good pings")
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

    @Test
    @DisplayName("mute silences the alert and checks rules in order")
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

    @Test
    @DisplayName("unmute turns alerts back on without raising one")
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
