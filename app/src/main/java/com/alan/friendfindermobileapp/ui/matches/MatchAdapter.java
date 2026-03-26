package com.alan.friendfindermobileapp.ui.matches;

import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alan.friendfindermobileapp.data.FriendFinderRepository;
import com.alan.friendfindermobileapp.databinding.ItemMatchBinding;
import com.alan.friendfindermobileapp.model.ChatMessage;
import com.alan.friendfindermobileapp.model.DiscoveryProfile;
import com.alan.friendfindermobileapp.model.MatchThread;

import java.util.ArrayList;
import java.util.List;

public class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {

    public interface Listener {
        void onMatchClicked(MatchThread matchThread);
    }

    private final FriendFinderRepository repository;
    private final Listener listener;
    private final List<MatchThread> items = new ArrayList<>();

    public MatchAdapter(FriendFinderRepository repository, Listener listener) {
        this.repository = repository;
        this.listener = listener;
    }

    public void submitList(List<MatchThread> matches) {
        items.clear();
        items.addAll(matches);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new MatchViewHolder(ItemMatchBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        MatchThread item = items.get(position);
        DiscoveryProfile profile = repository.getProfile(item.getProfileId());
        if (profile == null) {
            return;
        }

        holder.binding.matchName.setText(profile.getName() + ", " + profile.getAge());
        holder.binding.matchMeta.setText(profile.getCity() + " - " + profile.getJobTitle());

        ChatMessage lastMessage = item.getLastMessage();
        holder.binding.matchPreview.setText(
                lastMessage == null ? holder.binding.getRoot().getContext().getString(
                        com.alan.friendfindermobileapp.R.string.match_item_new_match
                ) : lastMessage.getText()
        );
        holder.binding.matchTime.setText(
                DateUtils.getRelativeTimeSpanString(
                        lastMessage == null ? item.getMatchedAtMillis() : lastMessage.getSentAtMillis(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                )
        );
        holder.binding.avatarInitials.setText(profile.getInitials());
        applyAvatar(holder.binding.avatarContainer, profile);
        holder.binding.getRoot().setOnClickListener(view -> listener.onMatchClicked(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void applyAvatar(View target, DiscoveryProfile profile) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                new int[]{profile.getPrimaryColor(), profile.getSecondaryColor()}
        );
        drawable.setShape(GradientDrawable.OVAL);
        target.setBackground(drawable);
    }

    static class MatchViewHolder extends RecyclerView.ViewHolder {

        private final ItemMatchBinding binding;

        MatchViewHolder(ItemMatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
