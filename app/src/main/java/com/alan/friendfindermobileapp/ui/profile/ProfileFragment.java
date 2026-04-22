package com.alan.friendfindermobileapp.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.alan.friendfindermobileapp.MainActivity;
import com.alan.friendfindermobileapp.R;
import com.alan.friendfindermobileapp.data.FriendFinderRepository;
import com.alan.friendfindermobileapp.databinding.FragmentProfileBinding;
import com.alan.friendfindermobileapp.model.LocalUser;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment implements FriendFinderRepository.RepositoryListener {

    private static final int MAX_PHOTOS = 6;

    private FragmentProfileBinding binding;
    private FriendFinderRepository repository;
    private ActivityResultLauncher<String[]> photoPickerLauncher;
    private final List<String> selectedPhotoUris = new ArrayList<>();
    private boolean hasBoundInitialData;
    private boolean isBusy;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }

                    for (Uri uri : uris) {
                        if (selectedPhotoUris.size() >= MAX_PHOTOS) {
                            break;
                        }

                        String rawUri = uri.toString();
                        if (!selectedPhotoUris.contains(rawUri)) {
                            selectedPhotoUris.add(rawUri);
                            try {
                                requireContext().getContentResolver()
                                        .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) {
                            }
                        }
                    }
                    renderPhotoState();
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = FriendFinderRepository.getInstance(requireContext());

        PhotoPreviewAdapter adapter = new PhotoPreviewAdapter(photoUri -> {
            selectedPhotoUris.remove(photoUri);
            renderPhotoState();
        });
        binding.photoRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        binding.photoRecycler.setAdapter(adapter);

        binding.addPhotosButton.setOnClickListener(button -> photoPickerLauncher.launch(new String[]{"image/*"}));
        binding.saveProfileButton.setOnClickListener(button -> saveProfile());
        binding.signInButton.setOnClickListener(button -> authenticate(false));
        binding.createAccountButton.setOnClickListener(button -> authenticate(true));
        binding.signOutButton.setOnClickListener(button -> repository.signOut());

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
            binding.backendNoticeCard.setVisibility(View.VISIBLE);
            binding.authContainer.setVisibility(View.GONE);
            binding.signedInContainer.setVisibility(View.GONE);
            binding.profileFormContainer.setVisibility(View.GONE);
            binding.profileStateTitle.setText(R.string.backend_setup_title);
            binding.profileStateBody.setText(R.string.backend_setup_profile_body);
            binding.backendNoticeBody.setText(R.string.backend_setup_instruction_body);
            return;
        }

        binding.backendNoticeCard.setVisibility(View.GONE);

        if (!repository.isAuthenticated()) {
            binding.authContainer.setVisibility(View.VISIBLE);
            binding.signedInContainer.setVisibility(View.GONE);
            binding.profileFormContainer.setVisibility(View.GONE);
            binding.profileStateTitle.setText(R.string.profile_auth_title);
            binding.profileStateBody.setText(R.string.profile_auth_body);
            return;
        }

        binding.authContainer.setVisibility(View.GONE);
        binding.signedInContainer.setVisibility(View.VISIBLE);
        binding.profileFormContainer.setVisibility(View.VISIBLE);
        binding.signedInEmail.setText(repository.getCurrentUserEmail());

        LocalUser currentUser = repository.getCurrentUser();
        if (currentUser == null) {
            binding.profileStateTitle.setText(R.string.profile_header_new);
            binding.profileStateBody.setText(R.string.profile_status_new);

            if (!hasBoundInitialData) {
                clearForm();
            }
        } else {
            binding.profileStateTitle.setText(R.string.profile_header_existing);
            binding.profileStateBody.setText(R.string.profile_status_live);

            setTextIfChanged(binding.nameInput, currentUser.getName());
            setTextIfChanged(binding.ageInput, String.valueOf(currentUser.getAge()));
            setTextIfChanged(binding.cityInput, currentUser.getCity());
            setTextIfChanged(binding.jobInput, currentUser.getJobTitle());
            setTextIfChanged(binding.headlineInput, currentUser.getHeadline());
            setTextIfChanged(binding.aboutInput, currentUser.getAbout());
            setTextIfChanged(binding.interestsInput, currentUser.getInterestsCsv());

            selectedPhotoUris.clear();
            selectedPhotoUris.addAll(currentUser.getPhotoUris());
        }

        hasBoundInitialData = true;
        renderPhotoState();
        clearErrors();
        setBusy(false);
    }

    private void clearForm() {
        binding.nameInput.setText("");
        binding.ageInput.setText("");
        binding.cityInput.setText("");
        binding.jobInput.setText("");
        binding.headlineInput.setText("");
        binding.aboutInput.setText("");
        binding.interestsInput.setText("");
        selectedPhotoUris.clear();
    }

    private void renderPhotoState() {
        PhotoPreviewAdapter adapter = (PhotoPreviewAdapter) binding.photoRecycler.getAdapter();
        if (adapter != null) {
            adapter.submitList(selectedPhotoUris);
        }

        boolean hasPhotos = !selectedPhotoUris.isEmpty();
        binding.photoEmptyText.setVisibility(hasPhotos ? View.GONE : View.VISIBLE);
        binding.photoCountText.setText(selectedPhotoUris.size() + "/" + MAX_PHOTOS + " photos added");
    }

    private void clearErrors() {
        binding.authEmailLayout.setError(null);
        binding.authPasswordLayout.setError(null);
        binding.nameLayout.setError(null);
        binding.ageLayout.setError(null);
        binding.cityLayout.setError(null);
        binding.headlineLayout.setError(null);
        binding.aboutLayout.setError(null);
    }

    private void authenticate(boolean createAccount) {
        clearErrors();

        String email = valueOf(binding.authEmailInput);
        String password = valueOf(binding.authPasswordInput);

        if (TextUtils.isEmpty(email)) {
            binding.authEmailLayout.setError(getString(R.string.auth_email_required));
            return;
        }

        if (password.length() < 6) {
            binding.authPasswordLayout.setError(getString(R.string.auth_password_required));
            return;
        }

        setBusy(true);
        FriendFinderRepository.SimpleCallback callback = errorMessage -> {
            if (binding == null) {
                return;
            }
            setBusy(false);
            if (errorMessage != null) {
                Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                return;
            }
            binding.authPasswordInput.setText("");
            Snackbar.make(
                    binding.getRoot(),
                    createAccount ? R.string.auth_account_created : R.string.auth_signed_in,
                    Snackbar.LENGTH_LONG
            ).show();
        };

        if (createAccount) {
            repository.register(email, password, callback);
        } else {
            repository.signIn(email, password, callback);
        }
    }

    private void saveProfile() {
        clearErrors();

        String name = valueOf(binding.nameInput);
        String ageValue = valueOf(binding.ageInput);
        String city = valueOf(binding.cityInput);
        String jobTitle = valueOf(binding.jobInput);
        String headline = valueOf(binding.headlineInput);
        String about = valueOf(binding.aboutInput);
        String interests = valueOf(binding.interestsInput);

        if (TextUtils.isEmpty(name)) {
            binding.nameLayout.setError(getString(R.string.error_name_required));
            return;
        }

        int age;
        try {
            age = Integer.parseInt(ageValue);
        } catch (NumberFormatException exception) {
            binding.ageLayout.setError(getString(R.string.error_age_required));
            return;
        }

        if (age < 18) {
            binding.ageLayout.setError(getString(R.string.error_age_required));
            return;
        }

        if (TextUtils.isEmpty(city)) {
            binding.cityLayout.setError(getString(R.string.error_city_required));
            return;
        }

        if (TextUtils.isEmpty(headline)) {
            binding.headlineLayout.setError(getString(R.string.error_headline_required));
            return;
        }

        if (TextUtils.isEmpty(about)) {
            binding.aboutLayout.setError(getString(R.string.error_about_required));
            return;
        }

        if (selectedPhotoUris.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.error_photo_required, Snackbar.LENGTH_LONG).show();
            return;
        }

        LocalUser existingUser = repository.getCurrentUser();
        LocalUser savedUser = new LocalUser(
                existingUser != null ? existingUser.getId() : "firebase-user",
                name,
                age,
                city,
                jobTitle,
                headline,
                about,
                interests,
                new ArrayList<>(selectedPhotoUris)
        );

        setBusy(true);
        repository.saveCurrentUser(savedUser, errorMessage -> {
            if (binding == null) {
                return;
            }
            setBusy(false);
            if (errorMessage != null) {
                Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                return;
            }

            Snackbar.make(
                    binding.getRoot(),
                    existingUser == null ? R.string.profile_saved_message : R.string.profile_saved_and_ready_message,
                    Snackbar.LENGTH_LONG
            ).show();

            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateToDiscover();
            }
        });
    }

    private void setBusy(boolean busy) {
        isBusy = busy;
        binding.signInButton.setEnabled(!busy);
        binding.createAccountButton.setEnabled(!busy);
        binding.saveProfileButton.setEnabled(!busy);
        binding.signOutButton.setEnabled(!busy);
        binding.addPhotosButton.setEnabled(!busy);
    }

    private void setTextIfChanged(com.google.android.material.textfield.TextInputEditText input, String newValue) {
        String current = valueOf(input);
        if (!TextUtils.equals(current, newValue)) {
            input.setText(newValue);
        }
    }

    private String valueOf(com.google.android.material.textfield.TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
