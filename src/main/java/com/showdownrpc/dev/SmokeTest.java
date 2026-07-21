package com.showdownrpc.dev;

import java.time.OffsetDateTime;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;

public class SmokeTest
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SmokeTest.class);

    public static void main(String[] args) throws InterruptedException, com.jagrosh.discordipc.exceptions.NoDiscordClientException
    {
        IPCClient client = new IPCClient(1529123648940675072L);
        client.setListener(new IPCListener() {
            @Override public void onReady(IPCClient c) {
                c.sendRichPresence(new RichPresence.Builder()
                    .setDetails("Hello from Java")
                    .setState("Testing")
                    .setLargeImage("showdown_logo", "Pokémon Showdown")
                    .setStartTimestamp(OffsetDateTime.now())
                    .build());
                    log.info("IPC ready, sending presence");
            }
        });
        client.connect();
        Thread.sleep(60_000);   // presence disappears when the process exits
    }
    
}
