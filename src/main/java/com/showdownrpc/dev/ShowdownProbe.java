package com.showdownrpc.dev;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.discordipc.entities.RichPresence;
import com.showdownrpc.config.Config;
import com.showdownrpc.presence.PresenceMapper;
import com.showdownrpc.presence.StateSnapshot;
import com.showdownrpc.presence.StateTracker;
import com.showdownrpc.showdown.ShowdownClient;

public class ShowdownProbe 
{
    private static final Logger log = LoggerFactory.getLogger(ShowdownProbe.class);

    public static void main(String[] args) throws Exception {
        Config config = Config.load(Path.of("config.properties"));
        StateTracker tracker = new StateTracker();
        PresenceMapper mapper = new PresenceMapper();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ShowdownClient client = new ShowdownClient(config, scheduler);

        client.connect();   // async — returns immediately

        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            try {
                StateSnapshot snapshot = tracker.compute(client.searching(), client.battles());
                RichPresence presence = mapper.map(snapshot);
                log.info("state={} -> {}", snapshot.state(),
                    presence == null ? "null (would clear)" : presence.toJson());
            } catch (Exception e) {
                log.warn("Failed to compute/map presence", e);
            }
        }, 2, 2, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(poller::shutdownNow));
        Thread.currentThread().join();
    }
}