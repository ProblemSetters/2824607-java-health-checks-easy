package com.health;

import com.health.Models.Service;
import com.health.Models.State;
import com.health.Models.Status;

public final class Parser {
    private Parser() {}

    private static final class JsonParser {
        private final String json;
        private int pos = 0;

        JsonParser(String json) {
            this.json = json != null ? json : "";
        }

        State parseHealthDoc() {
            State state = new State();
            skipWs();
            expect('{');
            while (true) {
                skipWs();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWs();
                expect(':');
                skipWs();
                if ("services".equals(key)) {
                    parseArray(() -> state.services.add(parseServiceObject()));
                }
                skipWs();
                if (peek() == ',') {
                    pos++;
                }
            }
            return state;
        }

        private void parseArray(Runnable parseElement) {
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return;
            }
            while (true) {
                parseElement.run();
                skipWs();
                if (peek() == ',') {
                    pos++;
                    skipWs();
                    continue;
                }
                expect(']');
                break;
            }
        }

        private Service parseServiceObject() {
            Service service = new Service();
            skipWs();
            expect('{');
            while (true) {
                skipWs();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWs();
                expect(':');
                skipWs();
                if ("service".equals(key)) {
                    service.service = parseString();
                } else if ("status".equals(key)) {
                    service.status = "down".equals(parseString()) ? Status.Alerting : Status.Ok;
                } else if ("fails_in_row".equals(key)) {
                    service.failsInRow = parseInt();
                } else if ("passes_in_row".equals(key)) {
                    service.passesInRow = parseInt();
                } else if ("muted".equals(key)) {
                    service.muted = parseBool();
                } else if ("alerts".equals(key)) {
                    service.alerts = parseInt();
                }
                skipWs();
                if (peek() == ',') {
                    pos++;
                }
            }
            return service;
        }

        private char peek() {
            return pos < json.length() ? json.charAt(pos) : '\0';
        }

        private void expect(char c) {
            if (peek() != c) {
                throw new RuntimeException("JSON parse error: expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        private void skipWs() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (pos < json.length() && json.charAt(pos) != '"') {
                if (json.charAt(pos) == '\\' && pos + 1 < json.length()) {
                    pos++;
                    char escaped = json.charAt(pos++);
                    switch (escaped) {
                        case '"' -> value.append('"');
                        case '\\' -> value.append('\\');
                        case '/' -> value.append('/');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        default -> value.append(escaped);
                    }
                } else {
                    value.append(json.charAt(pos++));
                }
            }
            expect('"');
            return value.toString();
        }

        private int parseInt() {
            boolean negative = false;
            if (peek() == '-') {
                negative = true;
                pos++;
            }
            int start = pos;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                throw new RuntimeException("JSON parse error: expected integer at position " + pos);
            }
            int value = Integer.parseInt(json.substring(start, pos));
            return negative ? -value : value;
        }

        private boolean parseBool() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new RuntimeException("JSON parse error: expected boolean at position " + pos);
        }
    }

    /// Parse the Health Checks state JSON into the in-memory State.
    public static State parseHealthJson(String doc) {
        return new JsonParser(doc).parseHealthDoc();
    }
}
