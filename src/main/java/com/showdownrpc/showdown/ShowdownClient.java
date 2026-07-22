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
import java.util.concurrent.ScheduledFuture;
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
    private static final long USERDETAILS_POLL_SECONDS = 15;

    private final Config config;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger attempt = new AtomicInteger(0);

    private final Map<String, BattleRoom> battleRooms = new ConcurrentHashMap<>();
    private final Set<String> knownBattles = ConcurrentHashMap.newKeySet();
    /** Battles our *other* clients are watching, discovered via userdetails polling. */
    private final Map<String, BattleRoom> spectatedRooms = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> userDetailsPoll;
    private volatile List<String> lastSearching = List.of();
    private volatile String myUserId;

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
                // Must be set before any |player| line is handled, otherwise every
                // battle looks like a spectated one. updateuser always precedes a
                // room join, so this is safe.
                myUserId = BattleRoom.toId(user.trim());
                if (named) {
                    log.info("Logged in as {} (userid={})", user.trim(), myUserId);
                    startUserDetailsPoll();
                } else {
                    log.debug("Guest session: {}", user.trim());
                }
            }
            case "queryresponse" -> {
                if ("userdetails".equals(msg.arg(0))) {
                    // The JSON payload can itself contain '|' (usernames), and
                    // FrameParser split on it — rejoin everything after the subtype.
                    String json = String.join("|", msg.args().subList(1, msg.args().size()));
                    onUserDetails(JsonParser.parseString(json).getAsJsonObject());
                }
            }
            case "updatesearch" -> onUpdateSearch(JsonParser.parseString(msg.arg(0)).getAsJsonObject());
            case "popup" -> log.warn("Server popup: {}", String.join("|", msg.args()));
            default -> {
                if (isBattleRoom(msg.room())) {
                    routeToBattleRoom(msg);
                } else {
                    log.debug("[{}] |{}|{}", msg.room(), msg.type(), String.join("|", msg.args()));
                }
            }
        }
    }

    private static boolean isBattleRoom(String room) {
        return room != null && room.startsWith("battle-");
    }

    /**
     * Feeds room-scoped protocol lines into their BattleRoom — this is what makes
     * IN_BATTLE reachable, and what keeps tier/turn/opponent current. Only covers
     * rooms this connection actually joined; spectated battles are handled by the
     * userdetails poll instead.
     */
    private void routeToBattleRoom(ProtocolMessage msg) {
        String roomId = msg.room();
        switch (msg.type()) {
            case "init" -> {
                if ("battle".equals(msg.arg(0))) {
                    battleRooms.computeIfAbsent(roomId, BattleRoom::new);
                    log.info("Tracking battle room {}", roomId);
                }
            }
            case "deinit", "noinit" -> {
                if (battleRooms.remove(roomId) != null) {
                    log.info("Stopped tracking battle room {}", roomId);
                }
                knownBattles.remove(roomId);
            }
            default -> {
                // computeIfAbsent rather than get: covers rooms whose |init| we
                // missed (e.g. joined before login completed).
                battleRooms.computeIfAbsent(roomId, BattleRoom::new).accept(msg, myUserId);
            }
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

    /**
     * Spectating is invisible to this connection: room membership is per-connection,
     * so a battle your browser is watching produces no traffic here and no |init|.
     * userdetails is the one server-side view that aggregates rooms across *all* of
     * a user's connections, so we poll ourselves to see what our other clients joined.
     */
    private synchronized void startUserDetailsPoll() {
        if (userDetailsPoll != null && !userDetailsPoll.isCancelled()) return;
        userDetailsPoll = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isOpen()) send("|/cmd userdetails " + config.username());
            } catch (Exception e) {
                log.debug("userdetails poll failed", e);
            }
        }, 0, USERDETAILS_POLL_SECONDS, TimeUnit.SECONDS);
        log.info("Polling userdetails every {}s to detect spectated battles", USERDETAILS_POLL_SECONDS);
    }

    private void onUserDetails(JsonObject json) {
        log.debug("userdetails: {}", json);

        // 'rooms' is false when the user is offline/idle, otherwise a roomid->meta map.
        if (!json.has("rooms") || !json.get("rooms").isJsonObject()) {
            clearSpectated();
            return;
        }

        Set<String> seen = new HashSet<>();
        JsonObject rooms = json.getAsJsonObject("rooms");

        for (String key : rooms.keySet()) {
            // Keys carry the user's room auth as a prefix, e.g. "*battle-gen9ou-1".
            String roomId = key.replaceFirst("^[^a-z0-9]+", "");
            if (!roomId.startsWith("battle-")) continue;
            // Battles we play in are tracked live from their protocol stream.
            if (knownBattles.contains(roomId)) continue;

            seen.add(roomId);
            BattleRoom existing = spectatedRooms.get(roomId);
            if (existing != null) {
                existing.touch();
                continue;
            }

            JsonObject meta = rooms.get(key).isJsonObject()
                ? rooms.getAsJsonObject(key) : new JsonObject();
            String p1 = meta.has("p1") ? meta.get("p1").getAsString() : null;
            String p2 = meta.has("p2") ? meta.get("p2").getAsString() : null;

            spectatedRooms.put(roomId, BattleRoom.spectated(roomId, p1, p2));
            log.info("Spectating {} ({} vs {})", roomId, p1, p2);
        }

        // Anything no longer listed means every client of ours left that room.
        spectatedRooms.keySet().removeIf(roomId -> {
            if (seen.contains(roomId)) return false;
            log.info("No longer spectating {}", roomId);
            return true;
        });
    }

    private void clearSpectated() {
        if (!spectatedRooms.isEmpty()) {
            log.info("No longer spectating {}", spectatedRooms.keySet());
            spectatedRooms.clear();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public List<String> searching(){
        return lastSearching;
    }

    /** Battles we're playing (live protocol stream) plus battles we're watching (polled). */
    public Collection<BattleRoom> battles()
    {
        List<BattleRoom> all = new ArrayList<>(battleRooms.values());
        all.addAll(spectatedRooms.values());
        return all;
    }

    /** Set by shutdown() so a deliberate close doesn't schedule a doomed reconnect. */
    private volatile boolean shuttingDown = false;

    /** Call before closeBlocking() so onClose knows this close is intentional. */
    public void shutdown() {
        this.shuttingDown = true;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (shuttingDown) {
            log.info("Disconnected during shutdown ({}) - not reconnecting", code);
            return;
        }

        log.warn("Disconnected ({}): {} - scheduling reconnect", code, reason);

        battleRooms.clear();
        knownBattles.clear();
        spectatedRooms.clear();
        if (userDetailsPoll != null) {
            userDetailsPoll.cancel(false);
            userDetailsPoll = null;   // re-armed by the next updateuser after reconnect
        }

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