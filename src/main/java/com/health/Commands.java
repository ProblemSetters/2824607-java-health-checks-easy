package com.health;

import com.health.Models.CommandResult;
import com.health.Models.Reason;
import com.health.Models.Service;
import com.health.Models.State;
import com.health.Models.Status;

public final class Commands {
    private Commands() {}

    private static final int ALERT_AFTER = 3;
    private static final int RECOVER_AFTER = 2;

    /// Find a service by name.
    private static Service findService(State state, String name) {
        for (Service s : state.services) {
            if (s.service.equals(name)) {
                return s;
            }
        }
        return null;
    }

    /// Build a successful result carrying the command's output message.
    private static CommandResult success(String message) {
        return CommandResult.success(message);
    }

    /// Build a failed result carrying the reason code.
    private static CommandResult failure(Reason reason) {
        return CommandResult.failure(reason);
    }

    /// Record the result of a check on a service.
    public static CommandResult check(State state, String name, String result) {
        if (!"up".equals(result) && !"down".equals(result)) {
            return failure(Reason.INVALID_RESULT);
        }

        Service service = findService(state, name);
        if (service == null) {
            Service fresh = new Service();
            fresh.service = name;
            state.services.add(fresh);
            return success(name + " " + result + " (1 in a row)");
        }

        if ("down".equals(result)) {
            service.failsInRow++;
            service.passesInRow = 0;
            if (service.failsInRow == ALERT_AFTER) {
                service.status = Status.Alerting;
                service.alerts++;
                if (!service.muted) {
                    return success(name + " ALERT: down " + service.failsInRow + " in a row");
                }
            }
            return success(name + " down (" + service.failsInRow + " in a row)");
        }

        service.passesInRow++;
        if (service.failsInRow > 0) {
            service.failsInRow--;
        }
        if (service.passesInRow >= RECOVER_AFTER && service.status == Status.Alerting) {
            service.status = Status.Ok;
            if (!service.muted) {
                return success(name + " recovered");
            }
        }

        return success(name + " up (" + service.passesInRow + " in a row)");
    }

    /// Silence a service's alerts.
    public static CommandResult mute(State state, String name) {
        Service service = findService(state, name);
        if (service == null) {
            return failure(Reason.SERVICE_NOT_FOUND);
        }
        if (service.muted) {
            return failure(Reason.ALREADY_MUTED);
        }

        service.muted = true;

        return success(name + " muted");
    }

    /// Turn a service's alerts back on.
    public static CommandResult unmute(State state, String name) {
        Service service = findService(state, name);
        if (service == null || !service.muted) {
            return failure(Reason.NOT_MUTED);
        }

        service.muted = false;

        return success(name + " unmuted");
    }
}
