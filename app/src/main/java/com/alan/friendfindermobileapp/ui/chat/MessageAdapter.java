package com.alan.friendfindermobileapp.ui.chat;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alan.friendfindermobileapp.databinding.ItemMessageIncomingBinding;
import com.alan.friendfindermobileapp.databinding.ItemMessageOutgoingBinding;
import com.alan.friendfindermobileapp.model.ChatMessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_INCOMING = 0;
    private static final int VIEW_TYPE_OUTGOING = 1;

    private final List<ChatMessage> messages = new ArrayList<>();

    public void submitList(List<ChatMessage> items) {
        messages.clear();
        messages.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isFromCurrentUser() ? VIEW_TYPE_OUTGOING : VIEW_TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_OUTGOING) {
            return new OutgoingMessageViewHolder(ItemMessageOutgoingBinding.inflate(inflater, parent, false));
        }
        return new IncomingMessageViewHolder(ItemMessageIncomingBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        String formattedTime = DateFormat.getTimeFormat(holder.itemView.getContext())
                .format(new Date(message.getSentAtMillis()));

        if (holder instanceof OutgoingMessageViewHolder) {
            OutgoingMessageViewHolder outgoingHolder = (OutgoingMessageViewHolder) holder;
            outgoingHolder.binding.messageText.setText(message.getText());
            outgoingHolder.binding.messageTime.setText(formattedTime);
        } else if (holder instanceof IncomingMessageViewHolder) {
            IncomingMessageViewHolder incomingHolder = (IncomingMessageViewHolder) holder;
            incomingHolder.binding.messageText.setText(message.getText());
            incomingHolder.binding.messageTime.setText(formattedTime);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class IncomingMessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemMessageIncomingBinding binding;

        IncomingMessageViewHolder(ItemMessageIncomingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class OutgoingMessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemMessageOutgoingBinding binding;

        OutgoingMessageViewHolder(ItemMessageOutgoingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
