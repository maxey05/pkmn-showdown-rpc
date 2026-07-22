package com.showdownrpc.presence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.jagrosh.discordipc.entities.RichPresence;

public class PresenceMapper 
{
    private static final String LARGE_IMAGE_KEY = "showdown_logo";
    private static final String LARGE_IMAGE_TEXT = "Pokemon Showdown!";

    public RichPresence map(StateSnapshot s) 
    {
        return switch (s.state()) {
            case IDLE -> null;
            case SEARCHING -> new RichPresence.Builder()
                .setDetails("In the lobby")
                .setState("Searching: " + String.join(", ", s.searching()))
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .build();
            case IN_BATTLE -> new RichPresence.Builder()
                .setDetails(s.format())
                .setState("vs. " + s.opponent() + " · Turn " + s.turn())
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .setStartTimestamp(toOffsetDateTime(s.startedAtEpochSeconds()))
                .build();
            case SPECTATING -> new RichPresence.Builder()
                .setDetails(s.format())
                .setState("Watching: " + s.opponent())  // opponent() holds title() here
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .setStartTimestamp(toOffsetDateTime(s.startedAtEpochSeconds()))
                .build();
            // Phase 4b sets this state directly (bypassing compute()); no battle
            // fields are populated, so the mapping is deliberately static.
            case TEAMBUILDING -> new RichPresence.Builder()
                .setDetails("Building a team")
                .setLargeImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT)
                .build();
        };
    }

    private static OffsetDateTime toOffsetDateTime(long epochSeconds)
    {
        if(epochSeconds <= 0)
            return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
