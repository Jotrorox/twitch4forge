package com.twicht4forge.twitchchat.irc;

/**
 * One line in the chat widget.
 *
 * @param id     Twitch message id, or "" if unknown
 * @param login  lowercase login of the author, or "" for system lines
 * @param author display name
 * @param color  author colour as 0xRRGGBB, exactly as chosen on Twitch
 * @param action true for /me messages
 * @param system true for notices such as subs and raids
 * @param time   when the message arrived, in epoch milliseconds
 */
public record ChatMessage(String id, String login, String author, int color, String text,
                          boolean action, boolean system, long time) {

    public static ChatMessage system(String text) {
        return new ChatMessage("", "", "", 0xAAAAAA, text, false, true, System.currentTimeMillis());
    }
}
