package com.alan.friendfindermobileapp.ui.matches;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.alan.friendfindermobileapp.ChatActivity;
import com.alan.friendfindermobileapp.R;
import com.alan.friendfindermobileapp.data.FriendFinderRepository;
import com.alan.friendfindermobileapp.databinding.FragmentMatchesBinding;
import com.alan.friendfindermobileapp.model.MatchThread;

import java.util.List;

public class MatchesFragment extends Fragment implements FriendFinderRepository.RepositoryListener {

    private FragmentMatchesBinding binding;
    private FriendFinderRepository repository;
    private MatchAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMatchesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = FriendFinderRepository.getInstance(requireContext());

        adapter = new MatchAdapter(repository, matchThread -> startActivity(
                ChatActivity.createIntent(requireContext(), matchThread.getId())
        ));
        binding.matchesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.matchesRecycler.setAdapter(adapter);

        renderState();
    }

    @Override
    public void onStart() {
        super.onStart();
        repository.registerListener(this);
    }

    @Override
    public void onStop() {
        repository.unregisterListener(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDataChanged() {
        if (binding != null) {
            renderState();
        }
    }

    private void renderState() {
        if (!repository.isBackendConfigured()) {
            showEmptyState(
                    getString(R.string.backend_setup_title),
                    getString(R.string.backend_setup_matches_body),
                    "Add your Supabase URL and anon key in the app resources first."
            );
            return;
        }

        if (!repository.isAuthenticated()) {
            showEmptyState(
                    getString(R.string.matches_auth_title),
                    getString(R.string.matches_auth_body),
                    "Sign in from the Profile tab to start receiving live matches."
            );
            return;
        }

        if (!repository.hasCurrentUser()) {
            showEmptyState(
                    getString(R.string.matches_profile_title),
                    getString(R.string.matches_profile_body),
                    "Finish your profile first so other people can discover you."
            );
            return;
        }

        List<MatchThread> matches = repository.getMatches();
        adapter.submitList(matches);
        binding.matchesSummary.setText(
                matches.isEmpty()
                        ? "Your real-time matches will appear here."
                        : matches.size() + (matches.size() == 1 ? " live match right now." : " live matches right now.")
        );

        boolean isEmpty = matches.isEmpty();
        binding.emptyTitle.setText(R.string.matches_empty_title);
        binding.emptyBody.setText(R.string.matches_empty_body);
        binding.emptyContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.matchesRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(String title, String body, String summary) {
        binding.matchesSummary.setText(summary);
        binding.emptyTitle.setText(title);
        binding.emptyBody.setText(body);
        binding.emptyContainer.setVisibility(View.VISIBLE);
        binding.matchesRecycler.setVisibility(View.GONE);
    }
}
