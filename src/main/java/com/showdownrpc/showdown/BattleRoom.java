package com.showdownrpc.showdown;

import com.showdownrpc.protocol.ProtocolMessage;

public class BattleRoom
{
    private final String roomId;
    private volatile String tier, title;
    // Written on the WebSocket thread, read on the presence-tick thread.
    private volatile String p1, p2, myPlayerSlot;
    private volatile int turn;
    private volatile long startedAtEpochSeconds;
    private volatile boolean finished;

    private volatile long lastActivityMillis = System.currentTimeMillis();

    public BattleRoom(String roomId) {
        this.roomId = roomId;
    }

    /**
     * A battle we are watching but have NOT joined on this connection, built from a
     * userdetails poll. We therefore never receive its protocol stream, so there is
     * no turn counter and no start timestamp — the SPECTATING presence doesn't use
     * either. myPlayerSlot stays null, so isPlaying() correctly reports false.
     */
    public static BattleRoom spectated(String roomId, String p1, String p2) {
        BattleRoom room = new BattleRoom(roomId);
        room.p1 = p1;
        room.p2 = p2;
        room.tier = formatFromRoomId(roomId);
        room.title = (p1 != null && p2 != null) ? p1 + " vs. " + p2 : roomId;
        return room;
    }

    /** Keeps a poll-derived room from looking stale next to a live one. */
    public void touch() {
        lastActivityMillis = System.currentTimeMillis();
    }

    /**
     * "battle-gen9ou-2314" -> "gen9ou". Format ids never contain '-', so the second
     * segment is always the whole format, even with a password suffix.
     */
    static String formatFromRoomId(String roomId) {
        String[] parts = roomId.split("-");
        return parts.length >= 2 ? parts[1] : null;
    }

    public void accept(ProtocolMessage msg, String myUserId) {
        lastActivityMillis = System.currentTimeMillis();
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

    public String roomId()
    {
        return roomId;
    }

    public String tier()
    {
        return tier;
    }

    public String title()
    {
        return title;
    }

    public int turn()
    {
        return turn;
    }

    public long startedAtEpochSeconds()
    {
        return startedAtEpochSeconds;
    }

    public boolean finished()
    {
        return finished;
    }

    public boolean isPlaying()
    {
        return myPlayerSlot != null;
    }

    public long lastActivityMillis()
    {
        return lastActivityMillis;
    }
}
