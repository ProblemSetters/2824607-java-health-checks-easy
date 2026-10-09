package com.health;

import com.health.Models.CommandResult;
import com.health.Models.State;

import java.io.PrintStream;

public final class Main {
    private static final String HEALTH_PATH = "data/health.json";

    private Main() {}

    /// Print the supported commands and their arguments.
    private static void printUsage(PrintStream out) {
        out.print("Usage:\n" +
                  "  health check <service> <up|down>\n" +
                  "  health mute <service>\n" +
                  "  health unmute <service>\n");
    }

    /// Load the Health Checks state, dispatch the requested command, and write any changes back.
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage(System.err);
            System.exit(1);
        }

        String command = args[0];

        if ("check".equals(command)) {
            if (args.length != 3) {
                printUsage(System.err);
                System.exit(1);
            }
        } else if ("mute".equals(command)) {
            if (args.length != 2) {
                printUsage(System.err);
                System.exit(1);
            }
        } else if ("unmute".equals(command)) {
            if (args.length != 2) {
                printUsage(System.err);
                System.exit(1);
            }
        } else {
            System.err.println("Unknown command: " + command);
            printUsage(System.err);
            System.exit(1);
        }

        try {
            State state = Parser.parseHealthJson(FileIo.readFileToString(HEALTH_PATH));

            CommandResult result;
            if ("check".equals(command)) {
                result = Commands.check(state, args[1], args[2]);
            } else if ("mute".equals(command)) {
                result = Commands.mute(state, args[1]);
            } else {
                result = Commands.unmute(state, args[1]);
            }

            if (result.ok) {
                FileIo.writeHealthJson(HEALTH_PATH, state);
                System.out.println(result.message);
            } else {
                System.out.println("Error: " + result.reason);
            }
        } catch (Exception error) {
            System.err.println("Error: " + error.getMessage());
            System.exit(1);
        }
    }
}
