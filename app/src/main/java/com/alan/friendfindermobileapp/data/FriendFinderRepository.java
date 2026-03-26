package com.alan.friendfindermobileapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.Nullable;

import com.alan.friendfindermobileapp.model.ChatMessage;
import com.alan.friendfindermobileapp.model.DiscoveryProfile;
import com.alan.friendfindermobileapp.model.LocalUser;
import com.alan.friendfindermobileapp.model.MatchThread;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FriendFinderRepository {

    public interface RepositoryListener {
        void onDataChanged();
    }

    private static final String PREFS_NAME = "friend_finder_state";
    private static final String KEY_CURRENT_USER = "current_user";
    private static final String KEY_PASSED_IDS = "passed_ids";
    private static final String KEY_LIKED_IDS = "liked_ids";
    private static final String KEY_MATCHES = "matches";

    private static FriendFinderRepository instance;

    private final SharedPreferences preferences;
    private final List<DiscoveryProfile> sampleProfiles = new ArrayList<>();
    private final Map<String, DiscoveryProfile> profileLookup = new LinkedHashMap<>();
    private final Set<String> passedIds = new LinkedHashSet<>();
    private final Set<String> likedIds = new LinkedHashSet<>();
    private final List<MatchThread> matches = new ArrayList<>();
    private final Set<RepositoryListener> listeners = new LinkedHashSet<>();

    private LocalUser currentUser;

    private FriendFinderRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        seedProfiles();
        restoreState();
    }

    public static synchronized FriendFinderRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FriendFinderRepository(context);
        }
        return instance;
    }

    public void registerListener(RepositoryListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(RepositoryListener listener) {
        listeners.remove(listener);
    }

    public boolean hasCurrentUser() {
        return currentUser != null;
    }

    @Nullable
    public LocalUser getCurrentUser() {
        return currentUser;
    }

    public void saveCurrentUser(LocalUser user) {
        currentUser = user;
        persistState();
        notifyListeners();
    }

    @Nullable
    public DiscoveryProfile getCurrentDiscoveryProfile() {
        List<DiscoveryProfile> queue = getDiscoveryQueue();
        return queue.isEmpty() ? null : queue.get(0);
    }

    @Nullable
    public DiscoveryProfile getNextDiscoveryProfile() {
        List<DiscoveryProfile> queue = getDiscoveryQueue();
        return queue.size() > 1 ? queue.get(1) : null;
    }

    public List<DiscoveryProfile> getDiscoveryQueue() {
        List<DiscoveryProfile> queue = new ArrayList<>();
        for (DiscoveryProfile profile : sampleProfiles) {
            if (!passedIds.contains(profile.getId()) && !likedIds.contains(profile.getId())) {
                queue.add(profile);
            }
        }
        return queue;
    }

    public void passProfile(String profileId) {
        passedIds.add(profileId);
        persistState();
        notifyListeners();
    }

    @Nullable
    public MatchThread likeProfile(String profileId) {
        likedIds.add(profileId);
        MatchThread matchThread = null;

        DiscoveryProfile profile = profileLookup.get(profileId);
        if (profile != null && profile.isMatchOnLike()) {
            matchThread = findMatch(profileId);
            if (matchThread == null) {
                matchThread = createMatch(profile);
                matches.add(matchThread);
            }
        }

        persistState();
        notifyListeners();
        return matchThread;
    }

    public List<MatchThread> getMatches() {
        List<MatchThread> sortedMatches = new ArrayList<>(matches);
        sortedMatches.sort((left, right) -> Long.compare(getLastActivity(right), getLastActivity(left)));
        return sortedMatches;
    }

    @Nullable
    public MatchThread findMatch(String profileId) {
        for (MatchThread match : matches) {
            if (match.getProfileId().equals(profileId)) {
                return match;
            }
        }
        return null;
    }

    @Nullable
    public DiscoveryProfile getProfile(String profileId) {
        return profileLookup.get(profileId);
    }

    public void sendMessage(String profileId, String text) {
        MatchThread match = findMatch(profileId);
        if (match == null) {
            return;
        }

        long now = System.currentTimeMillis();
        match.addMessage(new ChatMessage(UUID.randomUUID().toString(), true, text, now));

        String reply = buildReply(profileId, match.getMessages().size());
        if (!reply.isEmpty()) {
            match.addMessage(new ChatMessage(UUID.randomUUID().toString(), false, reply, now + 45_000L));
        }

        persistState();
        notifyListeners();
    }

    private MatchThread createMatch(DiscoveryProfile profile) {
        long now = System.currentTimeMillis();
        List<ChatMessage> starterMessages = Arrays.asList(
                new ChatMessage(UUID.randomUUID().toString(), false, buildIntroMessage(profile), now),
                new ChatMessage(UUID.randomUUID().toString(), false, "I liked your profile too. What are you up to this week?", now + 20_000L)
        );
        return new MatchThread(profile.getId(), now, starterMessages);
    }

    private String buildIntroMessage(DiscoveryProfile profile) {
        return "Hey, I am " + profile.getName() + ". You seem fun. Want to chat?";
    }

    private String buildReply(String profileId, int messageCount) {
        DiscoveryProfile profile = profileLookup.get(profileId);
        if (profile == null) {
            return "";
        }

        List<String> replies = Arrays.asList(
                "That sounds great. I am always up for a good conversation.",
                "You picked a good topic. I am curious to hear more.",
                "I could be convinced. Coffee and a walk is usually my move.",
                "That is a solid idea. I am free after work later this week."
        );
        return replies.get(messageCount % replies.size());
    }

    private long getLastActivity(MatchThread thread) {
        ChatMessage lastMessage = thread.getLastMessage();
        return lastMessage != null ? lastMessage.getSentAtMillis() : thread.getMatchedAtMillis();
    }

    private void notifyListeners() {
        List<RepositoryListener> snapshot = new ArrayList<>(listeners);
        for (RepositoryListener listener : snapshot) {
            listener.onDataChanged();
        }
    }

    private void persistState() {
        SharedPreferences.Editor editor = preferences.edit();

        if (currentUser != null) {
            try {
                editor.putString(KEY_CURRENT_USER, currentUser.toJson().toString());
            } catch (JSONException ignored) {
                editor.remove(KEY_CURRENT_USER);
            }
        } else {
            editor.remove(KEY_CURRENT_USER);
        }

        editor.putStringSet(KEY_PASSED_IDS, new LinkedHashSet<>(passedIds));
        editor.putStringSet(KEY_LIKED_IDS, new LinkedHashSet<>(likedIds));

        JSONArray matchesJson = new JSONArray();
        for (MatchThread match : matches) {
            try {
                matchesJson.put(match.toJson());
            } catch (JSONException ignored) {
            }
        }
        editor.putString(KEY_MATCHES, matchesJson.toString());
        editor.apply();
    }

    private void restoreState() {
        String rawUser = preferences.getString(KEY_CURRENT_USER, null);
        if (rawUser != null) {
            try {
                currentUser = LocalUser.fromJson(rawUser);
            } catch (JSONException ignored) {
                currentUser = null;
            }
        }

        Set<String> restoredPassed = preferences.getStringSet(KEY_PASSED_IDS, Collections.emptySet());
        passedIds.clear();
        if (restoredPassed != null) {
            passedIds.addAll(restoredPassed);
        }

        Set<String> restoredLiked = preferences.getStringSet(KEY_LIKED_IDS, Collections.emptySet());
        likedIds.clear();
        if (restoredLiked != null) {
            likedIds.addAll(restoredLiked);
        }

        String rawMatches = preferences.getString(KEY_MATCHES, null);
        matches.clear();
        if (rawMatches != null) {
            try {
                JSONArray matchesJson = new JSONArray(rawMatches);
                for (int i = 0; i < matchesJson.length(); i++) {
                    matches.add(MatchThread.fromJson(matchesJson.getJSONObject(i)));
                }
            } catch (JSONException ignored) {
                matches.clear();
            }
        }
    }

    private void seedProfiles() {
        addProfile(new DiscoveryProfile(
                "harper",
                "Harper",
                27,
                "London",
                "Product Designer",
                "Museum dates, brutal honesty, and Sunday markets.",
                "Designer who is always chasing the next great cappuccino.",
                3,
                "HP",
                Color.parseColor("#FF7A59"),
                Color.parseColor("#FFB347"),
                true,
                Arrays.asList("design", "coffee", "city walks")
        ));
        addProfile(new DiscoveryProfile(
                "jordan",
                "Jordan",
                30,
                "Bristol",
                "Documentary Filmmaker",
                "Equal parts camera nerd and dog park regular.",
                "I am happiest outside, preferably with a camera and a ridiculous snack lineup.",
                11,
                "JR",
                Color.parseColor("#246BFD"),
                Color.parseColor("#56CCF2"),
                false,
                Arrays.asList("films", "hiking", "dogs")
        ));
        addProfile(new DiscoveryProfile(
                "mia",
                "Mia",
                26,
                "Manchester",
                "Chef",
                "Can cook dinner. Need someone else to pick dessert.",
                "Big fan of live music, tiny restaurants, and people who can keep up with good banter.",
                6,
                "MA",
                Color.parseColor("#FF5D8F"),
                Color.parseColor("#FF99AC"),
                true,
                Arrays.asList("food", "vinyl", "weekend trips")
        ));
        addProfile(new DiscoveryProfile(
                "noah",
                "Noah",
                29,
                "Leeds",
                "Architecture Graduate",
                "Asks too many questions about your favorite building.",
                "Trying every bakery in town and pretending I know how to surf.",
                15,
                "NH",
                Color.parseColor("#0A7C66"),
                Color.parseColor("#59C9A5"),
                false,
                Arrays.asList("architecture", "surf", "pastries")
        ));
        addProfile(new DiscoveryProfile(
                "sophia",
                "Sophia",
                28,
                "Liverpool",
                "Startup Operator",
                "Fast talker, slow mornings, excellent playlists.",
                "If there is a rooftop, a bookshop, or a pasta special, I am there.",
                8,
                "SP",
                Color.parseColor("#6C63FF"),
                Color.parseColor("#95A8FF"),
                true,
                Arrays.asList("books", "travel", "playlists")
        ));
        addProfile(new DiscoveryProfile(
                "leo",
                "Leo",
                31,
                "Bath",
                "Personal Trainer",
                "Will absolutely challenge you to mini golf.",
                "Very into early gym sessions and very into compensating with burgers after.",
                19,
                "LT",
                Color.parseColor("#1D3557"),
                Color.parseColor("#457B9D"),
                false,
                Arrays.asList("fitness", "golf", "road trips")
        ));
        addProfile(new DiscoveryProfile(
                "ava",
                "Ava",
                25,
                "Oxford",
                "Writer",
                "Collects postcards and overthinks texts only a little.",
                "Looking for something genuine, playful, and a little spontaneous.",
                5,
                "AV",
                Color.parseColor("#C44569"),
                Color.parseColor("#F8B195"),
                true,
                Arrays.asList("writing", "galleries", "late dinners")
        ));
    }

    private void addProfile(DiscoveryProfile profile) {
        sampleProfiles.add(profile);
        profileLookup.put(profile.getId(), profile);
    }
}
