package com.showdownrpc.presence;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.showdownrpc.protocol.ProtocolMessage;
import com.showdownrpc.showdown.BattleRoom;

class StateTrackerTest {

    private final StateTracker tracker = new StateTracker();

    @Test
    void idleWhenNothingIsHappening() {
        StateSnapshot s = tracker.compute(List.of(), List.of());
        assertEquals(AppState.IDLE, s.state());
        assertNull(new PresenceMapper().map(s));   // null means "clear presence"
    }

    @Test
    void searchingReflectsQueuedFormats() {
        StateSnapshot s = tracker.compute(List.of("gen9ou"), List.of());
        assertEquals(AppState.SEARCHING, s.state());
        assertTrue(new PresenceMapper().map(s).toJson().toString().contains("gen9ou"));
    }

    @Test
    void mostRecentBattleWinsOverAnOlderOne() throws InterruptedException {
        BattleRoom older = new BattleRoom("battle-gen9ou-1");
        older.accept(ProtocolMessage.parse("battle-gen9ou-1", "|title|Older Battle", 8), "matthew");

        Thread.sleep(5);   // now correctly between the two rooms' last activity

        BattleRoom newer = new BattleRoom("battle-gen9ou-2");
        newer.accept(ProtocolMessage.parse("battle-gen9ou-2", "|title|Newer Battle", 8), "matthew");

        StateSnapshot s = tracker.compute(List.of(), List.of(older, newer));
        assertEquals("Newer Battle", s.opponent());   // opponent() holds title() while spectating
    }

    @Test
    void turnCountAndOpponentComeFromRealMessages() {
        BattleRoom room = new BattleRoom("battle-gen9ou-1");
        room.accept(ProtocolMessage.parse("battle-gen9ou-1", "|tier|[Gen 9] OU", 8), "matthew");
        room.accept(ProtocolMessage.parse("battle-gen9ou-1", "|player|p1|Matthew|60|1200", 8), "matthew");
        room.accept(ProtocolMessage.parse("battle-gen9ou-1", "|player|p2|Rival|60|1180", 8), "matthew");
        room.accept(ProtocolMessage.parse("battle-gen9ou-1", "|turn|3", 8), "matthew");

        StateSnapshot s = tracker.compute(List.of(), List.of(room));
        assertEquals(AppState.IN_BATTLE, s.state());
        assertEquals("[Gen 9] OU", s.format());
        assertEquals("Rival", s.opponent());
        assertEquals(3, s.turn());
    }

    @Test
    void finishedBattlesAreIgnored() {
        BattleRoom room = new BattleRoom("battle-gen9ou-1");
        room.accept(ProtocolMessage.parse("battle-gen9ou-1", "|player|p1|Matthew|60|1200", 8), "matthew");
        room.accept(ProtocolMessage.parse("battle-gen9ou-1", "|win|Matthew", 8), "matthew");

        StateSnapshot s = tracker.compute(List.of(), List.of(room));
        assertEquals(AppState.IDLE, s.state());
    }

    @Test
    void teambuildingMapsToAStaticPresence() {
        var s = new StateSnapshot(AppState.TEAMBUILDING, null, null, 0, 0, List.of());
        var presence = new PresenceMapper().map(s);
        assertNotNull(presence);
        assertTrue(presence.toJson().toString().contains("Building a team"));
    }
}