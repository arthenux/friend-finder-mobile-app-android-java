package com.alan.friendfindermobileapp.data

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import com.alan.friendfindermobileapp.R
import com.alan.friendfindermobileapp.model.ChatMessage
import com.alan.friendfindermobileapp.model.DiscoveryProfile
import com.alan.friendfindermobileapp.model.LocalUser
import com.alan.friendfindermobileapp.model.MatchThread
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.jvm.JvmSuppressWildcards

@OptIn(SupabaseExperimental::class)
class FriendFinderRepository private constructor(private val appContext: Context) {

    interface RepositoryListener {
        fun onDataChanged()
    }

    fun interface SimpleCallback {
        fun onComplete(errorMessage: String?)
    }

    fun interface SwipeCallback {
        fun onComplete(matchThread: MatchThread?, errorMessage: String?)
    }

    interface MessagesListener {
        fun onMessagesChanged(messages: List<@JvmSuppressWildcards ChatMessage>)
        fun onError(errorMessage: String)
    }

    interface Subscription {
        fun cancel()
    }

    private val listeners = CopyOnWriteArraySet<RepositoryListener>()
    private val profileLookup = linkedMapOf<String, DiscoveryProfile>()
    private val discoveryQueue = mutableListOf<DiscoveryProfile>()
    private val swipedProfileIds = linkedSetOf<String>()
    private val matches = mutableListOf<MatchThread>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val backendConfigured = isConfigured(appContext)

    private var supabase: SupabaseClient? = null
    private var currentUser: LocalUser? = null
    private var currentUserEmail: String = ""
    private var profilesJob: Job? = null
    private var swipesJob: Job? = null
    private var matchesJob: Job? = null

    init {
        if (backendConfigured) {
            supabase = createSupabaseClient(
                supabaseUrl = appContext.getString(R.string.supabase_url).trim(),
                supabaseKey = appContext.getString(R.string.supabase_anon_key).trim()
            ) {
                install(Auth)
                install(Postgrest)
                install(Storage)
                install(Realtime)
            }
            refreshAuthState()
        }
    }

    fun isBackendConfigured(): Boolean = backendConfigured

    fun isAuthenticated(): Boolean = supabase?.auth?.currentUserOrNull() != null

    fun hasCurrentUser(): Boolean = currentUser != null

    fun getCurrentUserEmail(): String = currentUserEmail

    fun getCurrentUser(): LocalUser? = currentUser

    fun registerListener(listener: RepositoryListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: RepositoryListener) {
        listeners.remove(listener)
    }

    fun signIn(email: String, password: String, callback: SimpleCallback) {
        val client = supabase ?: return callback.onComplete("Supabase is not configured yet.")
        scope.launch {
            runCatching {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }.onSuccess {
                refreshAuthState()
                deliverCallback(callback, null)
            }.onFailure {
                deliverCallback(callback, cleanError(it))
            }
        }
    }

    fun register(email: String, password: String, callback: SimpleCallback) {
        val client = supabase ?: return callback.onComplete("Supabase is not configured yet.")
        scope.launch {
            runCatching {
                client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
            }.onSuccess {
                refreshAuthState()
                deliverCallback(callback, null)
            }.onFailure {
                deliverCallback(callback, cleanError(it))
            }
        }
    }

    fun signOut() {
        val client = supabase ?: return
        scope.launch {
            runCatching { client.auth.signOut() }
            clearLocalState()
        }
    }

    fun saveCurrentUser(user: LocalUser, callback: SimpleCallback) {
        val client = supabase ?: return callback.onComplete("Supabase is not configured yet.")
        val uid = client.auth.currentUserOrNull()?.id ?: return callback.onComplete("Sign in first.")
        scope.launch {
            runCatching {
                val finalPhotoUrls = mutableListOf<String>()
                user.photoUris.forEachIndexed { index, photoUri ->
                    if (photoUri.startsWith("http://") || photoUri.startsWith("https://")) {
                        finalPhotoUrls.add(photoUri)
                    } else {
                        val path = "$uid/${UUID.randomUUID()}_$index.jpg"
                        appContext.contentResolver.openInputStream(Uri.parse(photoUri)).use { input ->
                            requireNotNull(input) { "Unable to open selected image." }
                            val bytes = input.readBytes()
                            client.storage.from("profile-photos").upload(path, bytes)
                            finalPhotoUrls.add(client.storage.from("profile-photos").publicUrl(path))
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val existingCreatedAt = client.from("profiles").select {
                    filter {
                        eq("id", uid)
                    }
                }.decodeSingleOrNull<ProfileRow>()?.createdAtMs ?: now
                val row = ProfileRow(
                    id = uid,
                    email = client.auth.currentUserOrNull()?.email.orEmpty(),
                    name = user.name,
                    age = user.age,
                    city = user.city,
                    jobTitle = user.jobTitle,
                    headline = user.headline,
                    about = user.about,
                    interests = user.interestsList,
                    photoUrls = finalPhotoUrls,
                    createdAtMs = existingCreatedAt,
                    updatedAtMs = now
                )
                client.from("profiles").upsert(row) {
                    onConflict = "id"
                }
            }.onSuccess {
                deliverCallback(callback, null)
            }.onFailure {
                deliverCallback(callback, cleanError(it))
            }
        }
    }

    fun getCurrentDiscoveryProfile(): DiscoveryProfile? = discoveryQueue.firstOrNull()

    fun getNextDiscoveryProfile(): DiscoveryProfile? = discoveryQueue.getOrNull(1)

    fun getDiscoveryQueue(): List<DiscoveryProfile> = discoveryQueue.toList()

    fun passProfile(profileId: String) {
        saveSwipe(profileId, "pass", null)
    }

    fun likeProfile(profileId: String, callback: SwipeCallback) {
        saveSwipe(profileId, "like") { swipeError ->
            if (swipeError != null) {
                deliverSwipeCallback(callback, null, swipeError)
                return@saveSwipe
            }
            checkForReciprocalMatch(profileId, callback)
        }
    }

    fun getMatches(): List<MatchThread> = matches.sortedByDescending { match ->
        match.lastMessage?.sentAtMillis ?: match.matchedAtMillis
    }

    fun findMatchById(matchId: String): MatchThread? = matches.firstOrNull { it.id == matchId }

    fun findMatch(profileId: String): MatchThread? = matches.firstOrNull { it.profileId == profileId }

    fun getProfile(profileId: String): DiscoveryProfile? = profileLookup[profileId]

    fun listenToMessages(matchId: String, listener: MessagesListener): Subscription? {
        val client = supabase ?: return null
        if (!isAuthenticated()) return null

        val job = scope.launch {
            runCatching {
                client.from("messages").selectAsFlow(
                    MessageRow::id,
                    filter = FilterOperation("match_id", FilterOperator.EQ, matchId)
                ).collectLatest { rows ->
                    val currentUid = client.auth.currentUserOrNull()?.id.orEmpty()
                    val messages = rows.sortedBy { it.sentAtMs }.map { row ->
                        ChatMessage(
                            row.id,
                            row.senderId == currentUid,
                            row.senderId,
                            row.text,
                            row.sentAtMs
                        )
                    }
                    deliverMessages(listener, messages)
                }
            }.onFailure {
                deliverMessagesError(listener, cleanError(it))
            }
        }
        return object : Subscription {
            override fun cancel() {
                job.cancel()
            }
        }
    }

    fun sendMessage(matchId: String, text: String) {
        val client = supabase ?: return
        val senderId = client.auth.currentUserOrNull()?.id ?: return
        val existingMatch = findMatchById(matchId) ?: return
        scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                client.from("messages").insert(
                    MessageRow(
                        id = UUID.randomUUID().toString(),
                        matchId = matchId,
                        senderId = senderId,
                        text = text,
                        sentAtMs = now
                    )
                )
                val userAId = buildMatchUsers(existingMatch.profileId, senderId).first
                val userBId = buildMatchUsers(existingMatch.profileId, senderId).second
                client.from("matches").upsert(
                    MatchRow(
                        id = matchId,
                        userAId = userAId,
                        userBId = userBId,
                        matchedAtMs = existingMatch.matchedAtMillis,
                        lastMessage = text,
                        lastSenderId = senderId,
                        lastMessageAtMs = now
                    )
                ) {
                    onConflict = "id"
                }
            }
        }
    }

    private fun refreshAuthState() {
        val client = supabase ?: return
        val user = client.auth.currentUserOrNull()
        currentUserEmail = user?.email.orEmpty()
        if (user == null) {
            clearLocalState()
            return
        }
        startRealtimeObservers(user.id)
    }

    private fun startRealtimeObservers(currentUid: String) {
        profilesJob?.cancel()
        swipesJob?.cancel()
        matchesJob?.cancel()

        val client = supabase ?: return

        profilesJob = scope.launch {
            runCatching {
                client.from("profiles").selectAsFlow(ProfileRow::id).collectLatest { rows ->
                    val me = rows.firstOrNull { it.id == currentUid }
                    currentUser = me?.toLocalUser()

                    profileLookup.clear()
                    rows.filter { it.id != currentUid }.forEach { row ->
                        profileLookup[row.id] = row.toDiscoveryProfile()
                    }

                    rebuildDiscoveryQueue()
                    notifyListeners()
                }
            }
        }

        swipesJob = scope.launch {
            runCatching {
                client.from("swipes").selectAsFlow(
                    SwipeRow::id,
                    filter = FilterOperation("swiper_id", FilterOperator.EQ, currentUid)
                ).collectLatest { rows ->
                    swipedProfileIds.clear()
                    rows.forEach { swipedProfileIds.add(it.swipedId) }
                    rebuildDiscoveryQueue()
                    notifyListeners()
                }
            }
        }

        matchesJob = scope.launch {
            runCatching {
                client.from("matches").selectAsFlow(MatchRow::id).collectLatest { rows ->
                    matches.clear()
                    rows.mapNotNull { row ->
                        val profileId = when (currentUid) {
                            row.userAId -> row.userBId
                            row.userBId -> row.userAId
                            else -> null
                        } ?: return@mapNotNull null

                        val previewMessages = if (row.lastMessage.isBlank()) {
                            emptyList()
                        } else {
                            listOf(
                                ChatMessage(
                                    "preview_${row.id}",
                                    row.lastSenderId == currentUid,
                                    row.lastSenderId,
                                    row.lastMessage,
                                    row.lastMessageAtMs
                                )
                            )
                        }
                        MatchThread(row.id, profileId, row.matchedAtMs, previewMessages)
                    }.also { matches.addAll(it) }

                    rebuildDiscoveryQueue()
                    notifyListeners()
                }
            }
        }
    }

    private fun rebuildDiscoveryQueue() {
        val matchedIds = matches.mapTo(linkedSetOf()) { it.profileId }
        discoveryQueue.clear()
        profileLookup.values.forEach { profile ->
            if (!swipedProfileIds.contains(profile.id) && !matchedIds.contains(profile.id)) {
                discoveryQueue.add(profile)
            }
        }
    }

    private fun saveSwipe(profileId: String, direction: String, callback: SimpleCallback?) {
        val client = supabase ?: return callback?.onComplete("Supabase is not configured yet.") ?: Unit
        val currentUid = client.auth.currentUserOrNull()?.id ?: return callback?.onComplete("Sign in first.") ?: Unit
        scope.launch {
            runCatching {
                client.from("swipes").upsert(
                    SwipeRow(
                        id = "$currentUid:$profileId",
                        swiperId = currentUid,
                        swipedId = profileId,
                        direction = direction,
                        createdAtMs = System.currentTimeMillis()
                    )
                ) {
                    onConflict = "id"
                }
            }.onSuccess {
                callback?.let { deliverCallback(it, null) }
            }.onFailure { throwable ->
                callback?.let { deliverCallback(it, cleanError(throwable)) }
            }
        }
    }

    private fun checkForReciprocalMatch(profileId: String, callback: SwipeCallback) {
        val client = supabase ?: return callback.onComplete(null, "Supabase is not configured yet.")
        val currentUid = client.auth.currentUserOrNull()?.id ?: return callback.onComplete(null, "Sign in first.")
        scope.launch {
            runCatching {
                val reciprocalSwipes = client.from("swipes").select {
                    filter {
                        eq("swiper_id", profileId)
                        eq("swiped_id", currentUid)
                        eq("direction", "like")
                    }
                }.decodeList<SwipeRow>()

                if (reciprocalSwipes.isEmpty()) {
                    deliverSwipeCallback(callback, null, null)
                    return@launch
                }

                val users = buildMatchUsers(profileId, currentUid)
                val now = System.currentTimeMillis()
                val matchId = "${users.first}:${users.second}"
                client.from("matches").upsert(
                    MatchRow(
                        id = matchId,
                        userAId = users.first,
                        userBId = users.second,
                        matchedAtMs = now,
                        lastMessage = "",
                        lastSenderId = "",
                        lastMessageAtMs = 0L
                    )
                ) {
                    onConflict = "id"
                }
                deliverSwipeCallback(callback, MatchThread(matchId, profileId, now, emptyList()), null)
            }.onFailure {
                deliverSwipeCallback(callback, null, cleanError(it))
            }
        }
    }

    private fun clearLocalState() {
        profilesJob?.cancel()
        swipesJob?.cancel()
        matchesJob?.cancel()
        currentUser = null
        currentUserEmail = ""
        profileLookup.clear()
        discoveryQueue.clear()
        swipedProfileIds.clear()
        matches.clear()
        notifyListeners()
    }

    private fun notifyListeners() {
        mainScope.launch {
            listeners.forEach { it.onDataChanged() }
        }
    }

    private fun deliverCallback(callback: SimpleCallback, errorMessage: String?) {
        mainScope.launch {
            callback.onComplete(errorMessage)
        }
    }

    private fun deliverSwipeCallback(
        callback: SwipeCallback,
        matchThread: MatchThread?,
        errorMessage: String?
    ) {
        mainScope.launch {
            callback.onComplete(matchThread, errorMessage)
        }
    }

    private fun deliverMessages(listener: MessagesListener, messages: List<ChatMessage>) {
        mainScope.launch {
            listener.onMessagesChanged(messages)
        }
    }

    private fun deliverMessagesError(listener: MessagesListener, errorMessage: String) {
        mainScope.launch {
            listener.onError(errorMessage)
        }
    }

    private fun cleanError(throwable: Throwable): String {
        return throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }

    private fun ProfileRow.toLocalUser(): LocalUser {
        return LocalUser(
            id,
            name,
            age,
            city,
            jobTitle,
            headline,
            about,
            interests.joinToString(", "),
            photoUrls
        )
    }

    private fun ProfileRow.toDiscoveryProfile(): DiscoveryProfile {
        val palette = PROFILE_PALETTES[kotlin.math.abs(id.hashCode()) % PROFILE_PALETTES.size]
        return DiscoveryProfile(
            id,
            name,
            age,
            city,
            jobTitle,
            headline,
            about,
            (kotlin.math.abs(id.hashCode()) % 24) + 1,
            initialsFrom(name),
            palette[0],
            palette[1],
            photoUrls.firstOrNull().orEmpty(),
            interests
        )
    }

    private fun initialsFrom(name: String): String {
        if (name.isBlank()) return "FF"
        val parts = name.trim().split("\\s+".toRegex())
        return if (parts.size == 1) {
            parts[0].take(2).uppercase()
        } else {
            (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    private fun buildMatchUsers(profileId: String, currentUid: String): Pair<String, String> {
        return if (profileId < currentUid) {
            profileId to currentUid
        } else {
            currentUid to profileId
        }
    }

    companion object {
        private val PROFILE_PALETTES = arrayOf(
            intArrayOf(Color.parseColor("#FF7A59"), Color.parseColor("#FFB347")),
            intArrayOf(Color.parseColor("#246BFD"), Color.parseColor("#56CCF2")),
            intArrayOf(Color.parseColor("#FF5D8F"), Color.parseColor("#FF99AC")),
            intArrayOf(Color.parseColor("#0A7C66"), Color.parseColor("#59C9A5")),
            intArrayOf(Color.parseColor("#6C63FF"), Color.parseColor("#95A8FF")),
            intArrayOf(Color.parseColor("#1D3557"), Color.parseColor("#457B9D")),
            intArrayOf(Color.parseColor("#C44569"), Color.parseColor("#F8B195"))
        )

        @Volatile
        private var instance: FriendFinderRepository? = null

        @JvmStatic
        fun getInstance(context: Context): FriendFinderRepository {
            return instance ?: synchronized(this) {
                instance ?: FriendFinderRepository(context.applicationContext).also { instance = it }
            }
        }

        private fun isConfigured(context: Context): Boolean {
            val url = context.getString(R.string.supabase_url).trim()
            val anonKey = context.getString(R.string.supabase_anon_key).trim()
            return !TextUtils.isEmpty(url)
                    && !TextUtils.isEmpty(anonKey)
                    && !url.startsWith("REPLACE_")
                    && !anonKey.startsWith("REPLACE_")
        }
    }
}

@Serializable
private data class ProfileRow(
    val id: String,
    val email: String = "",
    val name: String = "",
    val age: Int = 0,
    val city: String = "",
    @SerialName("job_title")
    val jobTitle: String = "",
    val headline: String = "",
    val about: String = "",
    val interests: List<String> = emptyList(),
    @SerialName("photo_urls")
    val photoUrls: List<String> = emptyList(),
    @SerialName("created_at_ms")
    val createdAtMs: Long = 0L,
    @SerialName("updated_at_ms")
    val updatedAtMs: Long = 0L
)

@Serializable
private data class SwipeRow(
    val id: String,
    @SerialName("swiper_id")
    val swiperId: String,
    @SerialName("swiped_id")
    val swipedId: String,
    val direction: String,
    @SerialName("created_at_ms")
    val createdAtMs: Long
)

@Serializable
private data class MatchRow(
    val id: String,
    @SerialName("user_a_id")
    val userAId: String,
    @SerialName("user_b_id")
    val userBId: String,
    @SerialName("matched_at_ms")
    val matchedAtMs: Long,
    @SerialName("last_message")
    val lastMessage: String = "",
    @SerialName("last_sender_id")
    val lastSenderId: String = "",
    @SerialName("last_message_at_ms")
    val lastMessageAtMs: Long = 0L
)

@Serializable
private data class MessageRow(
    val id: String,
    @SerialName("match_id")
    val matchId: String,
    @SerialName("sender_id")
    val senderId: String,
    val text: String,
    @SerialName("sent_at_ms")
    val sentAtMs: Long
)
