package com.showdownrpc.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.discordipc.entities.RichPresence;
import com.showdownrpc.discord.DiscordConnection;

public class PresenceDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PresenceDispatcher.class);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(15);

    private final DiscordConnection discord;
    private RichPresence lastSent;
    private AppState lastState;
    private Instant lastSentAt = Instant.EPOCH;

    public PresenceDispatcher(DiscordConnection discord) {
        this.discord = discord;
    }

    /**
     * A change of AppState (idle -> searching -> battle -> spectating, etc.) is a
     * one-off event and sends immediately. Only same-state content updates — the
     * turn counter ticking up within one ongoing battle — are throttled to respect
     * Discord's rate limit.
     */
    public synchronized void update(AppState state, RichPresence desired) {
        boolean stateChanged = state != lastState;

        if (!stateChanged && sameContent(desired, lastSent)) {
            return; // nothing changed since the last send
        }

        boolean cooldownElapsed =
            Duration.between(lastSentAt, Instant.now()).compareTo(MIN_INTERVAL) >= 0;

        if (stateChanged || cooldownElapsed) {
            send(state, desired);
        }
        // else: a same-state content change queued mid-cooldown. The next tick
        // re-runs this with the current desired state and sends once the window clears.
    }

    private void send(AppState state, RichPresence presence) {
        discord.sendRichPresence(presence);
        lastSent = presence;
        lastState = state;
        lastSentAt = Instant.now();
        log.debug("Sent presence ({}): {}", state,
            presence == null ? "null (cleared)" : presence.toJson());
    }

    private static boolean sameContent(RichPresence a, RichPresence b) {
        String aj = a == null ? null : a.toJson().toString();
        String bj = b == null ? null : b.toJson().toString();
        return Objects.equals(aj, bj);
    }
}
