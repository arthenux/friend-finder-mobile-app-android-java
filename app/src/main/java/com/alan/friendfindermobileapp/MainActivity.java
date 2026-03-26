package com.alan.friendfindermobileapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.alan.friendfindermobileapp.data.FriendFinderRepository;
import com.alan.friendfindermobileapp.databinding.ActivityMainBinding;
import com.alan.friendfindermobileapp.ui.discover.DiscoverFragment;
import com.alan.friendfindermobileapp.ui.matches.MatchesFragment;
import com.alan.friendfindermobileapp.ui.profile.ProfileFragment;

public class MainActivity extends AppCompatActivity implements FriendFinderRepository.RepositoryListener {

    private ActivityMainBinding binding;
    private FriendFinderRepository repository;
    private int selectedTabId = R.id.menu_discover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = FriendFinderRepository.getInstance(this);
        setupInsets();
        setupBottomNavigation();

        if (savedInstanceState == null) {
            selectedTabId = repository.hasCurrentUser() ? R.id.menu_discover : R.id.menu_profile;
            binding.bottomNav.setSelectedItemId(selectedTabId);
        } else {
            selectedTabId = savedInstanceState.getInt("selected_tab_id", selectedTabId);
            binding.bottomNav.setSelectedItemId(selectedTabId);
        }
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            showSelectedFragment(selectedTabId);
        }
        updateToolbar();
    }

    @Override
    protected void onStart() {
        super.onStart();
        repository.registerListener(this);
        updateToolbar();
    }

    @Override
    protected void onStop() {
        repository.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_tab_id", selectedTabId);
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.topAppBar.setPadding(
                    binding.topAppBar.getPaddingLeft(),
                    systemBars.top,
                    binding.topAppBar.getPaddingRight(),
                    binding.topAppBar.getPaddingBottom()
            );
            binding.bottomNav.setPadding(
                    binding.bottomNav.getPaddingLeft(),
                    binding.bottomNav.getPaddingTop(),
                    binding.bottomNav.getPaddingRight(),
                    systemBars.bottom
            );
            binding.fragmentContainer.setPadding(
                    binding.fragmentContainer.getPaddingLeft(),
                    binding.fragmentContainer.getPaddingTop(),
                    binding.fragmentContainer.getPaddingRight(),
                    0
            );
            return insets;
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            if (selectedTabId == item.getItemId()
                    && getSupportFragmentManager().findFragmentById(R.id.fragment_container) != null) {
                updateToolbar();
                return true;
            }

            selectedTabId = item.getItemId();
            showSelectedFragment(item.getItemId());
            updateToolbar();
            return true;
        });
    }

    private void showSelectedFragment(@IdRes int itemId) {
        Fragment fragment;
        if (itemId == R.id.menu_matches) {
            fragment = new MatchesFragment();
        } else if (itemId == R.id.menu_profile) {
            fragment = new ProfileFragment();
        } else {
            fragment = new DiscoverFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void navigateToMatches() {
        binding.bottomNav.setSelectedItemId(R.id.menu_matches);
    }

    public void navigateToDiscover() {
        binding.bottomNav.setSelectedItemId(R.id.menu_discover);
    }

    @Override
    public void onDataChanged() {
        updateToolbar();
        if (!repository.hasCurrentUser() && selectedTabId == R.id.menu_discover) {
            binding.bottomNav.setSelectedItemId(R.id.menu_profile);
        }
    }

    private void updateToolbar() {
        String title;
        String subtitle;

        if (selectedTabId == R.id.menu_matches) {
            int matchCount = repository.getMatches().size();
            title = getString(R.string.matches_title);
            subtitle = matchCount == 0
                    ? getString(R.string.matches_subtitle_empty)
                    : getResources().getQuantityString(R.plurals.match_count_subtitle, matchCount, matchCount);
        } else if (selectedTabId == R.id.menu_profile) {
            title = repository.hasCurrentUser()
                    ? getString(R.string.profile_title)
                    : getString(R.string.create_account_title);
            subtitle = repository.hasCurrentUser()
                    ? getString(R.string.profile_subtitle_ready)
                    : getString(R.string.profile_subtitle_new);
        } else {
            title = getString(R.string.discover_title);
            subtitle = repository.hasCurrentUser()
                    ? getString(R.string.discover_subtitle)
                    : getString(R.string.discover_locked_subtitle);
        }

        binding.topAppBar.setTitle(title);
        binding.topAppBar.setSubtitle(subtitle);
    }
}
