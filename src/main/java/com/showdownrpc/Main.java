package com.showdownrpc;

import java.nio.file.Files;
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
import com.showdownrpc.platform.WindowWatcher;
import com.showdownrpc.showdown.ShowdownClient;

public class Main 
{
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final long DISCORD_CLIENT_ID = 1529123648940675072L;
    private static final long TICK_INTERVAL_SECONDS = 2;

    public static void main(String[] args) throws Exception {
        Config config = Config.load(resolveConfigPath());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        ShowdownClient showdown = new ShowdownClient(config, scheduler);
        DiscordConnection discord = new DiscordConnection(DISCORD_CLIENT_ID, scheduler);
        StateTracker tracker = new StateTracker();
        PresenceMapper mapper = new PresenceMapper();
        PresenceDispatcher dispatcher = new PresenceDispatcher(discord);
        WindowWatcher windowWatcher = new WindowWatcher();

        discord.start();
        showdown.connect();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                StateSnapshot snapshot = tracker.compute(showdown.searching(), showdown.battles());
                if (snapshot.state() == com.showdownrpc.presence.AppState.IDLE
                        && windowWatcher.inTeambuilder()) {
                    snapshot = new StateSnapshot(
                        com.showdownrpc.presence.AppState.TEAMBUILDING,
                        null, null, 0, 0, java.util.List.of());
                }
                dispatcher.update(snapshot.state(), mapper.map(snapshot));
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
        // Flag the client first so its onClose doesn't try to schedule a reconnect,
        // then close connections, then stop the scheduler last.
        showdown.shutdown();
        scheduler.shutdownNow();
        discord.shutdown();
        try {
            showdown.closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Shutdown complete");
    }

    private static Path resolveConfigPath()
    {
        try
        {
            Path jarDir = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            Path beside = jarDir.resolve("config.properties");
            if(Files.exists(beside))
                return beside;
        }
        catch(Exception e)
        {

        }

        return Path.of("config.properties");
    }
}