package com.twicht4forge.twitchchat.irc;

/**
 * One line in the chat widget.
 *
 * @param id     Twitch message id, or "" if unknown
 * @param login  lowercase login of the author, or "" for system lines
 * @param author display name
 * @param color  author colour as 0xRRGGBB
 * @param action true for /me messages
 * @param system true for notices such as subs and raids
 */
public record ChatMessage(String id, String login, String author, int color, String text,
                          boolean action, boolean system) {

    public static ChatMessage system(String text) {
        return new ChatMessage("", "", "", 0xAAAAAA, text, false, true);
    }
}
