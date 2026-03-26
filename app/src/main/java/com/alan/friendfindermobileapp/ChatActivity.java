package com.alan.friendfindermobileapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.alan.friendfindermobileapp.data.FriendFinderRepository;
import com.alan.friendfindermobileapp.databinding.ActivityChatBinding;
import com.alan.friendfindermobileapp.model.DiscoveryProfile;
import com.alan.friendfindermobileapp.model.MatchThread;
import com.alan.friendfindermobileapp.ui.chat.MessageAdapter;

public class ChatActivity extends AppCompatActivity implements FriendFinderRepository.RepositoryListener {

    private static final String EXTRA_PROFILE_ID = "extra_profile_id";

    private ActivityChatBinding binding;
    private FriendFinderRepository repository;
    private MessageAdapter adapter;
    private String profileId;

    public static Intent createIntent(Context context, String profileId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = FriendFinderRepository.getInstance(this);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        if (profileId == null) {
            finish();
            return;
        }

        setupInsets();
        setupToolbar();
        setupMessages();
        setupComposer();
        renderConversation();
    }

    @Override
    protected void onStart() {
        super.onStart();
        repository.registerListener(this);
    }

    @Override
    protected void onStop() {
        repository.unregisterListener(this);
        super.onStop();
    }

    @Override
    public void onDataChanged() {
        renderConversation();
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.chatToolbar.setPadding(
                    binding.chatToolbar.getPaddingLeft(),
                    systemBars.top,
                    binding.chatToolbar.getPaddingRight(),
                    binding.chatToolbar.getPaddingBottom()
            );
            binding.composerContainer.setPadding(
                    binding.composerContainer.getPaddingLeft(),
                    binding.composerContainer.getPaddingTop(),
                    binding.composerContainer.getPaddingRight(),
                    systemBars.bottom + binding.composerContainer.getPaddingBottom()
            );
            return insets;
        });
    }

    private void setupToolbar() {
        binding.chatToolbar.setNavigationIcon(android.R.drawable.ic_media_previous);
        binding.chatToolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupMessages() {
        adapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);
        binding.messageList.setLayoutManager(layoutManager);
        binding.messageList.setAdapter(adapter);
    }

    private void setupComposer() {
        binding.sendButton.setOnClickListener(view -> {
            String message = binding.messageInput.getText() == null
                    ? ""
                    : binding.messageInput.getText().toString().trim();
            if (message.isEmpty()) {
                return;
            }

            repository.sendMessage(profileId, message);
            binding.messageInput.setText("");
        });
    }

    private void renderConversation() {
        MatchThread match = repository.findMatch(profileId);
        DiscoveryProfile profile = repository.getProfile(profileId);
        if (match == null || profile == null) {
            finish();
            return;
        }

        binding.chatToolbar.setTitle(profile.getName());
        binding.chatToolbar.setSubtitle(profile.getCity() + " - " + profile.getJobTitle());
        adapter.submitList(match.getMessages());

        boolean hasMessages = !match.getMessages().isEmpty();
        binding.emptyContainer.setVisibility(hasMessages ? View.GONE : View.VISIBLE);
        binding.messageList.setVisibility(hasMessages ? View.VISIBLE : View.GONE);
        if (hasMessages) {
            binding.messageList.post(() -> binding.messageList.scrollToPosition(adapter.getItemCount() - 1));
        }
    }
}
