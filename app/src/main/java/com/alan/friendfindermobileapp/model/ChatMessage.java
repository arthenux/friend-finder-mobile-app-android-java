package com.alan.friendfindermobileapp.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ChatMessage {

    private final String id;
    private final boolean fromCurrentUser;
    private final String senderId;
    private final String text;
    private final long sentAtMillis;

    public ChatMessage(String id, boolean fromCurrentUser, String senderId, String text, long sentAtMillis) {
        this.id = id;
        this.fromCurrentUser = fromCurrentUser;
        this.senderId = senderId;
        this.text = text;
        this.sentAtMillis = sentAtMillis;
    }

    public String getId() {
        return id;
    }

    public boolean isFromCurrentUser() {
        return fromCurrentUser;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getText() {
        return text;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("fromCurrentUser", fromCurrentUser);
        object.put("senderId", senderId);
        object.put("text", text);
        object.put("sentAtMillis", sentAtMillis);
        return object;
    }

    public static ChatMessage fromJson(JSONObject object) {
        return new ChatMessage(
                object.optString("id"),
                object.optBoolean("fromCurrentUser"),
                object.optString("senderId"),
                object.optString("text"),
                object.optLong("sentAtMillis")
        );
    }
}
