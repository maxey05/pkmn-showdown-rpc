package com.showdownrpc.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.discordipc.entities.RichPresence;
import com.showdownrpc.discord.DiscordConnection;


public class PresenceDispatcher 
{
    private static final Logger log = LoggerFactory.getLogger(PresenceDispatcher.class);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(15);
    
    private final DiscordConnection discord;
    private RichPresence lastSent;
    private Instant lastSentAt = Instant.EPOCH;

    public PresenceDispatcher(DiscordConnection discord)
    {
        this.discord = discord;
    }
    
    public synchronized void update(RichPresence desired)
    {
        if(sameContent(desired, lastSent))
            return;

        boolean boundaryTransition = (lastSent == null) != (desired == null);
        boolean cooldownElapsed = Duration.between(lastSentAt, Instant.now()).compareTo(MIN_INTERVAL) >= 0;

        if(boundaryTransition || cooldownElapsed)
            send(desired);
    }

    private void send(RichPresence presence)
    {
        discord.sendRichPresence(presence);
        lastSent = presence;
        lastSentAt = Instant.now();
        log.debug("Sent presence: {}", presence == null ? "null (cleared)" : presence.toJson());
    }

    private static boolean sameContent(RichPresence a, RichPresence b)
    {
        String aj = a == null ? null : a.toJson().toString();
        String bj = b == null ? null : b.toJson().toString();
        
        return Objects.equals(aj, bj);
    }
}
