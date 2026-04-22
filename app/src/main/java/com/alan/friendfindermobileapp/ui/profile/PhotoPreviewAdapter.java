package com.alan.friendfindermobileapp.ui.profile;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alan.friendfindermobileapp.databinding.ItemProfilePhotoBinding;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PhotoPreviewAdapter extends RecyclerView.Adapter<PhotoPreviewAdapter.PhotoViewHolder> {

    public interface Listener {
        void onRemoveClicked(String photoUri);
    }

    private final Listener listener;
    private final List<String> photoUris = new ArrayList<>();

    public PhotoPreviewAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<String> items) {
        photoUris.clear();
        photoUris.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new PhotoViewHolder(ItemProfilePhotoBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        String photoUri = photoUris.get(position);
        Glide.with(holder.binding.photoView)
                .load(photoUri)
                .centerCrop()
                .into(holder.binding.photoView);
        holder.binding.removeButton.setOnClickListener(view -> listener.onRemoveClicked(photoUri));
    }

    @Override
    public int getItemCount() {
        return photoUris.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {

        private final ItemProfilePhotoBinding binding;

        PhotoViewHolder(ItemProfilePhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
