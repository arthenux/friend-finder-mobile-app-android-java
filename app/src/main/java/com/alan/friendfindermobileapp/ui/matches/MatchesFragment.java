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
                ChatActivity.createIntent(requireContext(), matchThread.getProfileId())
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
        if (!repository.hasCurrentUser()) {
            binding.matchesSummary.setText("Create a profile to unlock your matches.");
            binding.emptyTitle.setText(com.alan.friendfindermobileapp.R.string.matches_locked_title);
            binding.emptyBody.setText(com.alan.friendfindermobileapp.R.string.matches_locked_body);
            binding.emptyContainer.setVisibility(View.VISIBLE);
            binding.matchesRecycler.setVisibility(View.GONE);
            return;
        }

        List<MatchThread> matches = repository.getMatches();
        adapter.submitList(matches);
        binding.matchesSummary.setText(
                matches.isEmpty()
                        ? "Your next match will show up here."
                        : matches.size() + (matches.size() == 1 ? " person liked you back." : " people liked you back.")
        );

        boolean isEmpty = matches.isEmpty();
        binding.emptyTitle.setText(com.alan.friendfindermobileapp.R.string.matches_empty_title);
        binding.emptyBody.setText(com.alan.friendfindermobileapp.R.string.matches_empty_body);
        binding.emptyContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.matchesRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
