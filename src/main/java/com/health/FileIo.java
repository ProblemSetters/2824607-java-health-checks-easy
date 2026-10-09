package com.health;

import com.health.Models.Service;
import com.health.Models.State;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class FileIo {
    private FileIo() {}

    /// Escape a string for safe inclusion inside JSON double quotes.
    public static String escapeJsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    /// Format a single service as a single-line JSON object.
    public static String serviceToJson(Service service) {
        return "{\"service\":\"" + escapeJsonString(service.service) + "\"" +
                ",\"status\":\"" + service.status + "\"" +
                ",\"fails_in_row\":" + service.failsInRow +
                ",\"passes_in_row\":" + service.passesInRow +
                ",\"muted\":" + (service.muted ? "true" : "false") +
                ",\"alerts\":" + service.alerts + "}";
    }

    /// Read a text file as a single string.
    public static String readFileToString(String path) {
        try {
            return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to open file: " + path, e);
        }
    }

    /// Format the Health Checks state as JSON for data/health.json.
    public static String healthToJson(State state) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"services\": [");
        List<Service> services = state.services;
        for (int i = 0; i < services.size(); i++) {
            if (i == 0) {
                builder.append("\n    ");
            } else {
                builder.append(",\n    ");
            }
            builder.append(serviceToJson(services.get(i)));
        }
        if (!services.isEmpty()) {
            builder.append("\n  ");
        }
        builder.append("]\n}");
        return builder.toString();
    }

    /// Write the Health Checks state JSON to the given file.
    public static void writeHealthJson(String path, State state) {
        try {
            Files.writeString(Paths.get(path), healthToJson(state) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to open output file: " + path, e);
        }
    }
}
