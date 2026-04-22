package com.alan.friendfindermobileapp.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchThread {

    private final String id;
    private final String profileId;
    private final long matchedAtMillis;
    private final List<ChatMessage> messages;

    public MatchThread(String id, String profileId, long matchedAtMillis, List<ChatMessage> messages) {
        this.id = id;
        this.profileId = profileId;
        this.matchedAtMillis = matchedAtMillis;
        this.messages = new ArrayList<>(messages);
    }

    public String getId() {
        return id;
    }

    public String getProfileId() {
        return profileId;
    }

    public long getMatchedAtMillis() {
        return matchedAtMillis;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void replaceMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
    }

    public ChatMessage getLastMessage() {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("profileId", profileId);
        object.put("matchedAtMillis", matchedAtMillis);

        JSONArray messagesJson = new JSONArray();
        for (ChatMessage message : messages) {
            messagesJson.put(message.toJson());
        }
        object.put("messages", messagesJson);
        return object;
    }

    public static MatchThread fromJson(JSONObject object) {
        JSONArray messagesJson = object.optJSONArray("messages");
        List<ChatMessage> restoredMessages = new ArrayList<>();
        if (messagesJson != null) {
            for (int i = 0; i < messagesJson.length(); i++) {
                JSONObject messageObject = messagesJson.optJSONObject(i);
                if (messageObject != null) {
                    restoredMessages.add(ChatMessage.fromJson(messageObject));
                }
            }
        }

        return new MatchThread(
                object.optString("id"),
                object.optString("profileId"),
                object.optLong("matchedAtMillis"),
                restoredMessages
        );
    }
}
