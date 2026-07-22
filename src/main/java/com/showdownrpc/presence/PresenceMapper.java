package com.showdownrpc.presence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.jagrosh.discordipc.entities.RichPresence;

public class PresenceMapper {
    private static final String LARGE_IMAGE_KEY = "showdown_logo";
    private static final String LARGE_IMAGE_TEXT = "Pokemon Showdown!";

    public RichPresence map(StateSnapshot s) {
        return switch (s.state()) {
            // Was null (which cleared presence and let Discord's auto game-detection
            // take the slot). Now a real presence so the app always owns the slot.
            case IDLE -> new RichPresence.Builder()
                .setDetails("In the lobby")
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .build();
            case SEARCHING -> new RichPresence.Builder()
                .setDetails("In the lobby")
                .setState("Searching: " + String.join(", ", s.searching()))
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .build();
            case IN_BATTLE -> new RichPresence.Builder()
                .setDetails(formatOr(s, "In a battle"))
                .setState("vs. " + safe(s.opponent()) + " · Turn " + s.turn())
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .setStartTimestamp(toOffsetDateTime(s.startedAtEpochSeconds()))
                .build();
            case SPECTATING -> new RichPresence.Builder()
                .setDetails(formatOr(s, "Spectating"))
                .setState("Watching: " + safe(s.opponent()))
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .setStartTimestamp(toOffsetDateTime(s.startedAtEpochSeconds()))
                .build();
            case TEAMBUILDING -> new RichPresence.Builder()
                .setDetails("Building a team")
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .build();
        };
    }

    // Discord may drop an activity whose details are null. |tier| can arrive a beat
    // after the battle room opens, so fall back rather than send null details.
    private static String formatOr(StateSnapshot s, String fallback) {
        return s.format() != null && !s.format().isBlank() ? s.format() : fallback;
    }

    private static String safe(String v) {
        return v != null ? v : "?";
    }

    private static OffsetDateTime toOffsetDateTime(long epochSeconds) {
        if (epochSeconds <= 0) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
