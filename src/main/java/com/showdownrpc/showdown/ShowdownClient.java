package com.showdownrpc.showdown;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.showdownrpc.config.Config;
import com.showdownrpc.protocol.FrameParser;
import com.showdownrpc.protocol.ProtocolMessage;

public class ShowdownClient extends WebSocketClient 
{
    private static final Logger log = LoggerFactory.getLogger(ShowdownClient.class);
    private static final URI SERVER = URI.create("wss://sim3.psim.us/showdown/websocket");
    private static final String LOGIN_URL = "https://play.pokemonshowdown.com/api/login";
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final Config config;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger attempt = new AtomicInteger(0);

    private final Map<String, BattleRoom> battleRooms = new ConcurrentHashMap<>();
    private final Set<String> knownBattles = ConcurrentHashMap.newKeySet();
    private volatile List<String> lastSearching = List.of();
    private volatile String myUserId;

    private volatile String tier, title, p1, p2, myPlayerSlot;
    private volatile int turn;
    private volatile long startedAtEpochSeconds;
    private volatile boolean finished;
    private volatile long lastActivityMillis = System.currentTimeMillis();

    public ShowdownClient(Config config, ScheduledExecutorService scheduler) {
        super(SERVER);
        this.config = config;
        this.scheduler = scheduler;
        setConnectionLostTimeout(60);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("Connected to Showdown");
        attempt.set(0);
    }

    @Override
    public void onMessage(String payload) {
        for (ProtocolMessage msg : FrameParser.parse(payload)) {
            try {
                handle(msg);
            } catch (Exception e) {
                // Never let one bad message kill the reader.
                log.warn("Failed handling |{}| in room '{}'", msg.type(), msg.room(), e);
            }
        }
    }

    private void handle(ProtocolMessage msg) throws Exception {
        switch (msg.type()) {
            case "challstr" -> {
                // CHALLSTR contains '|' — rejoin everything after the type.
                String challstr = String.join("|", msg.args());
                logIn(challstr);
            }
            case "updateuser" -> {
                String user = msg.arg(0);
                boolean named = "1".equals(msg.arg(1));
                if (named) {
                    log.info("Logged in as {}", user.trim());
                } else {
                    log.debug("Guest session: {}", user.trim());
                }
            }
            case "updatesearch" -> onUpdateSearch(JsonParser.parseString(msg.arg(0)).getAsJsonObject());
            case "popup" -> log.warn("Server popup: {}", String.join("|", msg.args()));
            default -> log.debug("[{}] |{}|{}", msg.room(), msg.type(), String.join("|", msg.args()));
        }
    }

    private void logIn(String challstr) throws Exception {
        String body = "name=" + enc(config.username())
                    + "&pass=" + enc(config.password())
                    + "&challstr=" + enc(challstr);

        HttpRequest req = HttpRequest.newBuilder(URI.create(LOGIN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        String raw = res.body();

        // Docs: "the response will start with ] followed by a JSON object"
        if (raw.startsWith("]")) raw = raw.substring(1);

        JsonObject data = JsonParser.parseString(raw).getAsJsonObject();
        if (!data.has("assertion")) {
            throw new IllegalStateException("Login failed, no assertion in response: " + data);
        }
        String assertion = data.get("assertion").getAsString();

        // An assertion beginning with ';' is an error string, not a token.
        if (assertion.startsWith(";")) {
            throw new IllegalStateException("Login rejected: " + assertion);
        }

        send("|/trn " + config.username() + ",0," + assertion);
        log.info("Sent /trn, awaiting updateuser");
    }

    private void onUpdateSearch(JsonObject json) {
        // searching: ladder queue, e.g. ["gen9ou"]
        List<String> searching = new ArrayList<>();
        if (json.has("searching") && json.get("searching").isJsonArray()) {
            json.getAsJsonArray("searching").forEach(e -> searching.add(e.getAsString()));
        }
        this.lastSearching = List.copyOf(searching);

        // games: {roomid: title}, or null when you're in none.
        Set<String> battles = new HashSet<>();
        if (json.has("games") && json.get("games").isJsonObject()) {
            for (String roomId : json.getAsJsonObject("games").keySet()) {
                // Filter: 'games' includes non-Pokemon games like Mafia.
                if (roomId.startsWith("battle-")) battles.add(roomId);
            }
        }

        for (String roomId : battles) {
            if (knownBattles.add(roomId)) {
                log.info("New battle: {} — joining", roomId);
                send("|/join " + roomId);
            }
        }
        knownBattles.retainAll(battles);

        log.info("searching={} battles={}", searching, battles);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public List<String> searching(){
        return lastSearching;
    }

    public Collection<BattleRoom> battles()
    {
        return battleRooms.values();
    }

    @Override 
    public void onClose(int code, String reason, boolean remote) {
        log.warn("Disconnected ({}): {} - scheduling reconnect", code, reason);

        battleRooms.clear();
        knownBattles.clear();

        long delay = Math.min(
            INITIAL_BACKOFF_MS * (1L << Math.min(attempt.getAndIncrement(), 5)),
            MAX_BACKOFF_MS);

        log.info("Reconnecting to Showdown in {} ms", delay);

        scheduler.schedule(this::reconnect, delay, TimeUnit.MILLISECONDS);
    }

    @Override 
    public void onError(Exception e) {
        log.error("WebSocket error", e);
    }
}