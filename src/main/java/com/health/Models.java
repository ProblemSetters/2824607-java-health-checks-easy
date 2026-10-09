package com.health;

import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}

    public enum Status {
        Ok("ok"),
        Alerting("alerting");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static Status parseStatus(String text) {
            if ("ok".equals(text)) return Ok;
            if ("alerting".equals(text)) return Alerting;
            throw new RuntimeException("unknown status: " + text);
        }
    }

    public static final class Service {
        public String service = "";
        public Status status = Status.Ok;
        public int failsInRow = 0;
        public int passesInRow = 0;
        public boolean muted = false;
        public int alerts = 0;

        public Service() {}

        public Service(String service, Status status, int failsInRow, int passesInRow, boolean muted, int alerts) {
            this.service = service;
            this.status = status;
            this.failsInRow = failsInRow;
            this.passesInRow = passesInRow;
            this.muted = muted;
            this.alerts = alerts;
        }
    }

    public static final class State {
        public List<Service> services = new ArrayList<>();

        public State() {}

        public State(List<Service> services) {
            this.services = new ArrayList<>(services);
        }
    }

    public enum Reason {
        NONE,
        INVALID_RESULT,
        SERVICE_NOT_FOUND,
        ALREADY_MUTED,
        NOT_MUTED
    }

    public static final class CommandResult {
        public boolean ok = false;
        public String message = "";
        public Reason reason = Reason.NONE;

        public CommandResult() {}

        public CommandResult(boolean ok, String message, Reason reason) {
            this.ok = ok;
            this.message = message;
            this.reason = reason;
        }

        public static CommandResult success(String message) {
            return new CommandResult(true, message, Reason.NONE);
        }

        public static CommandResult failure(Reason reason) {
            return new CommandResult(false, "", reason);
        }
    }
}
