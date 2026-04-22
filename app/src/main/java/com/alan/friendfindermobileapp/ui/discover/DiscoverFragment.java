package com.alan.friendfindermobileapp.ui.discover;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alan.friendfindermobileapp.MainActivity;
import com.alan.friendfindermobileapp.R;
import com.alan.friendfindermobileapp.data.FriendFinderRepository;
import com.alan.friendfindermobileapp.databinding.FragmentDiscoverBinding;
import com.alan.friendfindermobileapp.databinding.ViewDiscoveryCardBinding;
import com.alan.friendfindermobileapp.model.DiscoveryProfile;
import com.alan.friendfindermobileapp.model.MatchThread;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class DiscoverFragment extends Fragment implements FriendFinderRepository.RepositoryListener {

    private FragmentDiscoverBinding binding;
    private FriendFinderRepository repository;
    private DiscoveryProfile currentProfile;
    private float downX;
    private float downY;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = FriendFinderRepository.getInstance(requireContext());

        binding.passButton.setOnClickListener(button -> animateSwipe(false));
        binding.likeButton.setOnClickListener(button -> animateSwipe(true));
        binding.currentCard.getRoot().setOnTouchListener(cardTouchListener);

        renderDeck();
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
            renderDeck();
        }
    }

    private final View.OnTouchListener cardTouchListener = (view, motionEvent) -> {
        if (currentProfile == null) {
            return false;
        }

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = motionEvent.getRawX();
                downY = motionEvent.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = motionEvent.getRawX() - downX;
                float dy = motionEvent.getRawY() - downY;
                view.setTranslationX(dx);
                view.setTranslationY(dy * 0.15f);
                view.setRotation(dx / 30f);
                updateSwipeBadge(dx);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float threshold = view.getWidth() * 0.28f;
                if (Math.abs(view.getTranslationX()) > threshold) {
                    finishSwipe(view.getTranslationX() > 0f);
                } else {
                    resetCardPosition();
                }
                return true;
            default:
                return false;
        }
    };

    private void renderDeck() {
        if (!repository.isBackendConfigured()) {
            showLockedState(
                    "Supabase setup required",
                    getString(R.string.backend_setup_discover_body),
                    "Connect your Supabase project values to load live profiles."
            );
            return;
        }

        if (!repository.isAuthenticated()) {
            showLockedState(
                    "Sign in to start swiping",
                    getString(R.string.discover_auth_body),
                    "Create an account from the Profile tab to unlock the live discovery feed."
            );
            return;
        }

        if (!repository.hasCurrentUser()) {
            showLockedState(
                    "Finish your profile first",
                    getString(R.string.discover_profile_body),
                    "Add your details and photos before you appear in the live deck."
            );
            return;
        }

        currentProfile = repository.getCurrentDiscoveryProfile();
        DiscoveryProfile nextProfile = repository.getNextDiscoveryProfile();
        int queueSize = repository.getDiscoveryQueue().size();
        binding.deckSummary.setText(queueSize + " live profiles waiting nearby.");

        if (currentProfile == null) {
            binding.emptyTitle.setText(R.string.discover_empty_title);
            binding.emptyBody.setText(R.string.discover_empty_body);
            binding.emptyContainer.setVisibility(View.VISIBLE);
            binding.currentCard.getRoot().setVisibility(View.GONE);
            binding.nextCard.getRoot().setVisibility(View.GONE);
            binding.actionRow.setVisibility(View.GONE);
            return;
        }

        binding.emptyContainer.setVisibility(View.GONE);
        binding.currentCard.getRoot().setVisibility(View.VISIBLE);
        binding.actionRow.setVisibility(View.VISIBLE);

        bindCard(binding.currentCard, currentProfile);
        binding.currentCard.getRoot().setTranslationX(0f);
        binding.currentCard.getRoot().setTranslationY(0f);
        binding.currentCard.getRoot().setRotation(0f);
        binding.currentCard.actionBadge.setVisibility(View.GONE);
        binding.currentCard.actionBadge.setAlpha(0f);

        if (nextProfile != null) {
            binding.nextCard.getRoot().setVisibility(View.VISIBLE);
            binding.nextCard.getRoot().setScaleX(0.96f);
            binding.nextCard.getRoot().setScaleY(0.96f);
            binding.nextCard.getRoot().setTranslationY(18f);
            bindCard(binding.nextCard, nextProfile);
        } else {
            binding.nextCard.getRoot().setVisibility(View.GONE);
        }
    }

    private void bindCard(ViewDiscoveryCardBinding cardBinding, DiscoveryProfile profile) {
        GradientDrawable heroBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{profile.getPrimaryColor(), profile.getSecondaryColor()}
        );
        heroBackground.setCornerRadii(new float[]{28f, 28f, 28f, 28f, 0f, 0f, 0f, 0f});
        cardBinding.heroPanel.setBackground(heroBackground);

        if (TextUtils.isEmpty(profile.getPrimaryPhotoUrl())) {
            cardBinding.heroImage.setVisibility(View.GONE);
            cardBinding.initialsText.setVisibility(View.VISIBLE);
            cardBinding.initialsText.setText(profile.getInitials());
        } else {
            cardBinding.heroImage.setVisibility(View.VISIBLE);
            cardBinding.initialsText.setVisibility(View.GONE);
            Glide.with(cardBinding.heroImage)
                    .load(profile.getPrimaryPhotoUrl())
                    .centerCrop()
                    .into(cardBinding.heroImage);
        }

        cardBinding.nameAgeText.setText(profile.getName() + ", " + profile.getAge());
        cardBinding.headlineText.setText(profile.getHeadline());
        cardBinding.metaText.setText(profile.getCity() + " - " + profile.getDistanceMiles() + " miles away - " + profile.getJobTitle());
        cardBinding.aboutText.setText(profile.getAbout());
        cardBinding.interestsText.setText(TextUtils.join("  |  ", profile.getInterests()));
    }

    private void updateSwipeBadge(float translationX) {
        float alpha = Math.min(1f, Math.abs(translationX) / 260f);
        binding.currentCard.actionBadge.setAlpha(alpha);
        binding.currentCard.actionBadge.setVisibility(alpha > 0f ? View.VISIBLE : View.GONE);
        binding.currentCard.actionBadge.setText(
                translationX >= 0f ? R.string.discover_badge_like : R.string.discover_badge_pass
        );
        binding.currentCard.actionBadge.setBackgroundTintList(
                ColorStateList.valueOf(
                        getResources().getColor(
                                translationX >= 0f ? R.color.success : R.color.error_red,
                                requireContext().getTheme()
                        )
                )
        );
    }

    private void resetCardPosition() {
        binding.currentCard.getRoot().animate()
                .translationX(0f)
                .translationY(0f)
                .rotation(0f)
                .setDuration(180L)
                .withEndAction(() -> {
                    binding.currentCard.actionBadge.setAlpha(0f);
                    binding.currentCard.actionBadge.setVisibility(View.GONE);
                })
                .start();
    }

    private void animateSwipe(boolean like) {
        if (currentProfile == null) {
            return;
        }
        finishSwipe(like);
    }

    private void finishSwipe(boolean like) {
        DiscoveryProfile swipedProfile = currentProfile;
        float direction = like ? 1f : -1f;
        binding.currentCard.getRoot().animate()
                .translationX(direction * (binding.deckContainer.getWidth() + 320f))
                .translationY(40f)
                .rotation(direction * 16f)
                .alpha(0.4f)
                .setDuration(220L)
                .withEndAction(() -> {
                    binding.currentCard.getRoot().setAlpha(1f);
                    if (like) {
                        repository.likeProfile(swipedProfile.getId(), (matchThread, errorMessage) -> {
                            if (binding == null) {
                                return;
                            }
                            if (errorMessage != null) {
                                Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                                return;
                            }
                            if (matchThread != null) {
                                showMatchDialog(swipedProfile.getName());
                            }
                        });
                    } else {
                        repository.passProfile(swipedProfile.getId());
                    }
                })
                .start();
    }

    private void showLockedState(String title, String body, String summary) {
        currentProfile = null;
        binding.deckSummary.setText(summary);
        binding.emptyTitle.setText(title);
        binding.emptyBody.setText(body);
        binding.emptyContainer.setVisibility(View.VISIBLE);
        binding.currentCard.getRoot().setVisibility(View.GONE);
        binding.nextCard.getRoot().setVisibility(View.GONE);
        binding.actionRow.setVisibility(View.GONE);
    }

    private void showMatchDialog(String name) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.match_dialog_title)
                .setMessage(getString(R.string.match_dialog_message, name))
                .setPositiveButton(R.string.match_dialog_positive, (dialogInterface, i) -> {
                    if (requireActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).navigateToMatches();
                    }
                })
                .setNegativeButton(R.string.match_dialog_negative, null)
                .show();
    }
}
