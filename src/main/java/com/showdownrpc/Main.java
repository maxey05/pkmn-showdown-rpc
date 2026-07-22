package com.showdownrpc;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.showdownrpc.config.Config;
import com.showdownrpc.discord.DiscordConnection;
import com.showdownrpc.presence.PresenceDispatcher;
import com.showdownrpc.presence.PresenceMapper;
import com.showdownrpc.presence.StateSnapshot;
import com.showdownrpc.presence.StateTracker;
import com.showdownrpc.showdown.ShowdownClient;

public class Main 
{
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final long DISCORD_CLIENT_ID = 1529123648940675072L;
    private static final long TICK_INTERVAL_SECONDS = 2;

    public static void main(String[] args) throws Exception {
        Config config = Config.load(Path.of("config.properties"));

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        ShowdownClient showdown = new ShowdownClient(config, scheduler);
        DiscordConnection discord = new DiscordConnection(DISCORD_CLIENT_ID, scheduler);
        StateTracker tracker = new StateTracker();
        PresenceMapper mapper = new PresenceMapper();
        PresenceDispatcher dispatcher = new PresenceDispatcher(discord);

        discord.start();
        showdown.connect();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                StateSnapshot snapshot = tracker.compute(showdown.searching(), showdown.battles());
                dispatcher.update(mapper.map(snapshot));
            } catch (Exception e) {
                log.warn("Presence tick failed", e);
            }
        }, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(scheduler, showdown, discord)));

        log.info("Running. Ctrl+C to stop.");
        Thread.currentThread().join();
    }

    private static void shutdown(ScheduledExecutorService scheduler,
                                  ShowdownClient showdown,
                                  DiscordConnection discord) {
        log.info("Shutting down...");
        scheduler.shutdownNow();
        discord.shutdown();
        try {
            showdown.closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Shutdown complete");
    }
}