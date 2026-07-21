package com.showdownrpc.protocol;

import java.util.ArrayList;
import java.util.List;

public final class FrameParser 
{
    private static final String GLOBAL = "";

    /** Splits a raw WebSocket payload into per-room protocol lines. */
    public static List<ProtocolMessage> parse(String payload) {
        List<ProtocolMessage> out = new ArrayList<>();
        if (payload == null || payload.isEmpty()) return out;

        String room = GLOBAL;
        String[] lines = payload.split("\n");
        int start = 0;

        if (lines[0].startsWith(">")) {
            room = lines[0].substring(1).trim();
            start = 1;
        }

        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;          // docs: ignore empty lines
            out.add(ProtocolMessage.parse(room, line, 8));
        }
        return out;
    }
}