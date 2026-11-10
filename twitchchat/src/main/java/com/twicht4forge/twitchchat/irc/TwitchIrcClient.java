package com.twicht4forge.twitchchat.irc;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Read-only, anonymous connection to the chat of one Twitch channel.
 * Runs on its own daemon thread and reconnects until {@link #stop()} is called.
 */
public final class TwitchIrcClient {
    public enum State { CONNECTING, JOINING, CONNECTED, RECONNECTING }

    private static final String HOST = "irc.chat.twitch.tv";
    private static final int PORT = 6697;
    /** Twitch pings roughly every five minutes; silence longer than this means a dead link. */
    private static final int READ_TIMEOUT_MS = 6 * 60 * 1000;
    private static final int[] DEFAULT_COLORS = {
            0xFF4A4A, 0x5C7CFF, 0x00C000, 0xD9534F, 0xFF7F50, 0x9ACD32, 0xFF6A2A, 0x2EAD77,
            0xDAA520, 0xD2691E, 0x5F9EA0, 0x1E90FF, 0xFF69B4, 0x9B59F0, 0x00FF7F
    };

    private final String channel;
    private final ChatLog log;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile Socket socket;
    private volatile State state = State.CONNECTING;

    public TwitchIrcClient(String channel, ChatLog log) {
        this.channel = channel;
        this.log = log;
        this.thread = new Thread(this::run, "Twitch chat #" + channel);
        this.thread.setDaemon(true);
    }

    public String channel() {
        return channel;
    }

    public State state() {
        return state;
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        closeQuietly(socket);
        thread.interrupt();
    }

    private void run() {
        long backoff = 1000;
        while (running) {
            Socket plain = new Socket();
            socket = plain;
            try {
                plain.connect(new InetSocketAddress(HOST, PORT), 10_000);
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(plain, HOST, PORT, true);
                socket = ssl;
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(params);
                ssl.setSoTimeout(READ_TIMEOUT_MS);

                Writer out = new OutputStreamWriter(ssl.getOutputStream(), StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(ssl.getInputStream(), StandardCharsets.UTF_8));
                send(out, "CAP REQ :twitch.tv/tags twitch.tv/commands");
                send(out, "NICK justinfan" + ThreadLocalRandom.current().nextInt(10_000, 100_000));
                send(out, "JOIN #" + channel);

                String line;
                while (running && (line = in.readLine()) != null) {
                    if (!handle(line, out)) {
                        break;
                    }
                    if (state == State.CONNECTED) {
                        backoff = 1000;
                    }
                }
            } catch (IOException | RuntimeException e) {
                // fall through to the reconnect below
            } finally {
                closeQuietly(socket);
                socket = null;
            }
            if (!running) {
                break;
            }
            state = State.RECONNECTING;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, 30_000);
        }
    }

    /** @return false if the server asked us to reconnect */
    private boolean handle(String line, Writer out) throws IOException {
        IrcLine irc = IrcLine.parse(line);
        if (irc == null) {
            return true;
        }
        switch (irc.command) {
            case "PING" -> send(out, "PONG :" + irc.trailing);
            case "RECONNECT" -> {
                return false;
            }
            case "001" -> state = State.JOINING;
            case "ROOMSTATE" -> state = State.CONNECTED;
            case "PRIVMSG" -> log.add(userMessage(irc));
            case "USERNOTICE" -> {
                String system = irc.tags.getOrDefault("system-msg", "");
                if (!system.isEmpty()) {
                    log.add(ChatMessage.system(clean(system)));
                }
                if (!irc.trailing.isEmpty()) {
                    log.add(userMessage(irc));
                }
            }
            case "NOTICE" -> {
                if (!irc.trailing.isEmpty()) {
                    log.add(ChatMessage.system(clean(irc.trailing)));
                }
            }
            case "CLEARCHAT" -> {
                // A timeout or ban names the user; without one the whole chat was cleared.
                if (irc.trailing.isEmpty()) {
                    log.clear();
                } else {
                    String login = irc.trailing;
                    log.removeIf(m -> m.login().equals(login));
                }
            }
            case "CLEARMSG" -> {
                String id = irc.tags.getOrDefault("target-msg-id", "");
                if (!id.isEmpty()) {
                    log.removeIf(m -> m.id().equals(id));
                }
            }
            default -> {
            }
        }
        return true;
    }

    private static ChatMessage userMessage(IrcLine irc) {
        String login = irc.nick;
        if (login.isEmpty()) {
            login = irc.tags.getOrDefault("login", "");
        }
        String author = irc.tags.getOrDefault("display-name", "");
        if (author.isEmpty()) {
            author = login;
        }
        String text = irc.trailing;
        boolean action = false;
        if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
            text = text.substring(8, text.length() - 1);
            action = true;
        }
        return new ChatMessage(irc.tags.getOrDefault("id", ""), login, clean(author),
                color(irc.tags.get("color"), login), clean(text), action, false);
    }

    private static int color(String tag, String login) {
        int rgb = DEFAULT_COLORS[Math.floorMod(login.hashCode(), DEFAULT_COLORS.length)];
        if (tag != null && tag.length() == 7 && tag.charAt(0) == '#') {
            try {
                rgb = Integer.parseInt(tag.substring(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        // Blend dark colours (pure blue is a Twitch default) towards white so names stay readable.
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        if (luminance < 100) {
            double mix = (100 - luminance) / (255 - luminance);
            r += (int) ((255 - r) * mix);
            g += (int) ((255 - g) * mix);
            b += (int) ((255 - b) * mix);
        }
        return r << 16 | g << 8 | b;
    }

    /** Drops control characters and Minecraft's formatting marker so chat cannot restyle the HUD. */
    private static String clean(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '§' && c >= ' ' && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static void send(Writer out, String line) throws IOException {
        out.write(line + "\r\n");
        out.flush();
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** A parsed IRCv3 line: {@code @tags :nick!user@host COMMAND params :trailing}. */
    static final class IrcLine {
        final Map<String, String> tags = new HashMap<>();
        String nick = "";
        String command = "";
        String trailing = "";

        static IrcLine parse(String line) {
            IrcLine irc = new IrcLine();
            int pos = 0;
            if (line.startsWith("@")) {
                int end = line.indexOf(' ');
                if (end < 0) {
                    return null;
                }
                for (String tag : line.substring(1, end).split(";")) {
                    int eq = tag.indexOf('=');
                    if (eq > 0) {
                        irc.tags.put(tag.substring(0, eq), unescape(tag.substring(eq + 1)));
                    }
                }
                pos = end + 1;
            }
            if (line.startsWith(":", pos)) {
                int end = line.indexOf(' ', pos);
                if (end < 0) {
                    return null;
                }
                String prefix = line.substring(pos + 1, end);
                int bang = prefix.indexOf('!');
                if (bang > 0) {
                    irc.nick = prefix.substring(0, bang);
                }
                pos = end + 1;
            }
            String rest = line.substring(pos);
            int trail = rest.indexOf(" :");
            if (trail >= 0) {
                irc.trailing = rest.substring(trail + 2);
                rest = rest.substring(0, trail);
            }
            int space = rest.indexOf(' ');
            irc.command = space < 0 ? rest : rest.substring(0, space);
            return irc.command.isEmpty() ? null : irc;
        }

        private static String unescape(String value) {
            if (value.indexOf('\\') < 0) {
                return value;
            }
            StringBuilder sb = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length()) {
                    char n = value.charAt(++i);
                    switch (n) {
                        case 's' -> sb.append(' ');
                        case ':' -> sb.append(';');
                        case 'r', 'n' -> sb.append(' ');
                        default -> sb.append(n);
                    }
                } else if (c != '\\') {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
