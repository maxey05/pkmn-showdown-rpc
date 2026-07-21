package com.showdownrpc.showdown;

import com.showdownrpc.protocol.ProtocolMessage;

public class BattleRoom
{
    private final String roomId;
    private String tier, title, p1, p2, myPlayerSlot;
    private int turn;
    private long startedAtEpochSeconds;
    private boolean finished;

    BattleRoom(String roomId) {
        this.roomId = roomId;
    }

    void accept(ProtocolMessage msg, String myUserId) {
        switch (msg.type()) {
            case "title" -> title = msg.arg(0);
            case "tier"  -> tier = msg.arg(0);
            case "turn"  -> turn = Integer.parseInt(msg.arg(0));
            case "t:"    -> { if (startedAtEpochSeconds == 0) startedAtEpochSeconds = Long.parseLong(msg.arg(0)); }
            case "player" -> {
                String slot = msg.arg(0), name = msg.arg(1);
                if (name.isEmpty()) return;          // slot vacated
                if ("p1".equals(slot)) p1 = name; else if ("p2".equals(slot)) p2 = name;
                if (toId(name).equals(myUserId)) myPlayerSlot = slot;
            }
            case "win", "tie" -> finished = true;
            default -> { }
        }
    }

    public String opponent() {
        if (myPlayerSlot == null) return null;       // spectating
        return "p1".equals(myPlayerSlot) ? p2 : p1;
    }

    /** Showdown's userid normalization: lowercase, alphanumerics only. */
    static String toId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
