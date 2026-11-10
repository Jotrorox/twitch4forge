package com.twicht4forge.twitchchat.irc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Thread-safe, bounded message history. The version changes whenever the content does. */
public final class ChatLog {
    private static final int CAPACITY = 200;

    private final ArrayDeque<ChatMessage> messages = new ArrayDeque<>();
    private int version;

    public synchronized void add(ChatMessage message) {
        messages.addLast(message);
        while (messages.size() > CAPACITY) {
            messages.removeFirst();
        }
        version++;
    }

    public synchronized void clear() {
        messages.clear();
        version++;
    }

    public synchronized void removeIf(Predicate<ChatMessage> filter) {
        if (messages.removeIf(filter)) {
            version++;
        }
    }

    public synchronized int version() {
        return version;
    }

    public synchronized boolean isEmpty() {
        return messages.isEmpty();
    }

    /** Oldest first. */
    public synchronized List<ChatMessage> snapshot() {
        return new ArrayList<>(messages);
    }
}
