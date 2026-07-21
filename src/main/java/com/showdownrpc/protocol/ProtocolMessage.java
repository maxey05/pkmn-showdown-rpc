package com.showdownrpc.protocol;

import java.util.List;

public record ProtocolMessage(String room, String type, List<String> args) 
{

    public String arg(int i) {
        return i < args.size() ? args.get(i) : "";
    }

    /**
     * Parses a line like "|turn|3" into type "turn", args ["3"].
     *
     * maxArgs caps the split so trailing pipes survive — required for
     * challstr and chat, whose payloads legitimately contain '|'.
     */
    public static ProtocolMessage parse(String room, String line, int maxArgs) {
        if (line.isEmpty() || line.charAt(0) != '|') {
            return new ProtocolMessage(room, "", List.of(line));
        }
        // Drop the leading '|', then split into (type + maxArgs) fields.
        String body = line.substring(1);
        String[] parts = body.split("\\|", maxArgs + 1);
        String type = parts[0];
        List<String> args = List.of(parts).subList(1, parts.length);
        return new ProtocolMessage(room, type, args);
    }
}