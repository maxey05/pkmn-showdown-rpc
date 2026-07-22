package com.showdownrpc.discord;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;

public class DiscordConnection 
{
    private static final Logger log = LoggerFactory.getLogger(DiscordConnection.class);
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final IPCClient client;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger attempt = new AtomicInteger(0);

    public DiscordConnection(long clientId, ScheduledExecutorService scheduler) {
        this.client = new IPCClient(clientId);
        this.scheduler = scheduler;
        client.setListener(new IPCListener() {
            @Override public void onReady(IPCClient ipc) {
                log.info("Discord IPC ready");
                attempt.set(0);
            }
            @Override public void onClose(IPCClient ipc, JSONObject json) {
                log.warn("Discord IPC closed: {}", json);
                scheduleReconnect();
            }
            @Override public void onDisconnect(IPCClient ipc, Throwable t) {
                log.warn("Discord IPC disconnected", t);
                scheduleReconnect();
            }
        });
    }

    public void start() {
        attemptConnect();
    }

    private void attemptConnect() {
        try {
            client.connect();
        } catch (NoDiscordClientException e) {
            log.warn("Discord not running: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        long delay = Math.min(
            INITIAL_BACKOFF_MS * (1L << Math.min(attempt.getAndIncrement(), 5)),
            MAX_BACKOFF_MS);
        log.info("Retrying Discord connection in {} ms", delay);
        scheduler.schedule(this::attemptConnect, delay, TimeUnit.MILLISECONDS);
    }

    /** No-ops instead of throwing when Discord isn't currently connected. */
    public void sendRichPresence(RichPresence presence) {
        if (client.getStatus() == PipeStatus.CONNECTED) {
            client.sendRichPresence(presence);
        }
    }

    public void shutdown() {
        if (client.getStatus() == PipeStatus.CONNECTED) {
            try {
                client.sendRichPresence(null);   // clear before closing
                client.close();
            } catch (Exception e) {
                log.warn("Error during Discord shutdown", e);
            }
        }
    }
}