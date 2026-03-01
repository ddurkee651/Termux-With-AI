package com.termux.app.ai;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AIAssistantChatAdapter extends RecyclerView.Adapter<AIAssistantChatAdapter.VH> {

    private final List<AIAssistantMessage> items = new ArrayList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);

    private static final int BG_ASSISTANT = 0xFF1E232B;
    private static final int BG_USER = 0xFF2A2140;
    private static final int TXT_MAIN = 0xFFE9EDF1;
    private static final int TXT_META = 0xFFAEB6C2;
    private static final int TXT_ATTACH = 0xFFC9D1DB;
    private static final int TXT_ERR = 0xFFFFB4AB;

    public static final class VH extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final LinearLayout bubble;
        final TextView meta;
        final TextView text;
        final TextView attachments;
        final TextView error;

        VH(@NonNull View itemView) {
            super(itemView);
            root = (LinearLayout) itemView.findViewById(R.id.ai_msg_root);
            bubble = (LinearLayout) itemView.findViewById(R.id.ai_msg_bubble);
            meta = (TextView) itemView.findViewById(R.id.ai_msg_meta);
            text = (TextView) itemView.findViewById(R.id.ai_msg_text);
            attachments = (TextView) itemView.findViewById(R.id.ai_msg_attachments);
            error = (TextView) itemView.findViewById(R.id.ai_msg_error);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_ai_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AIAssistantMessage m = items.get(position);
        boolean isUser = m.role == AIAssistantMessage.Role.USER;

        LinearLayout.LayoutParams bubbleLp = (LinearLayout.LayoutParams) h.bubble.getLayoutParams();
        bubbleLp.gravity = isUser ? Gravity.END : Gravity.START;
        h.bubble.setLayoutParams(bubbleLp);

        h.bubble.setBackgroundColor(isUser ? BG_USER : BG_ASSISTANT);

        h.meta.setTextColor(TXT_META);
        h.text.setTextColor(TXT_MAIN);
        h.attachments.setTextColor(TXT_ATTACH);
        h.error.setTextColor(TXT_ERR);

        h.meta.setText(buildMeta(m));
        h.text.setText(safeText(m));

        if (m.attachments != null && !m.attachments.isEmpty()) {
            h.attachments.setVisibility(View.VISIBLE);
            h.attachments.setText(formatAttachments(m.attachments));
        } else {
            h.attachments.setVisibility(View.GONE);
            h.attachments.setText("");
        }

        if (m.status == AIAssistantMessage.Status.ERROR && !TextUtils.isEmpty(m.errorText)) {
            h.error.setVisibility(View.VISIBLE);
            h.error.setText("Error: " + m.errorText);
        } else {
            h.error.setVisibility(View.GONE);
            h.error.setText("");
        }

        float a = m.status == AIAssistantMessage.Status.STREAMING ? 0.85f : 1.0f;
        h.bubble.setAlpha(a);
    }

    private String safeText(AIAssistantMessage m) {
        if (m == null) return "";
        if (m.status == AIAssistantMessage.Status.ERROR) return "";
        return m.text == null ? "" : m.text;
    }

    private String buildMeta(AIAssistantMessage m) {
        String t = timeFmt.format(new Date(m.timestampMs));
        String who = m.role == AIAssistantMessage.Role.USER ? "You" : "AI";
        String src;
        if (m.modelSource == AIAssistantMessage.ModelSource.LOCAL) src = "Local";
        else if (m.modelSource == AIAssistantMessage.ModelSource.HUGGINGFACE) src = "HF";
        else src = "Gemini";
        String model = TextUtils.isEmpty(m.modelId) ? "" : (" • " + m.modelId);
        String st = m.status == AIAssistantMessage.Status.STREAMING ? " • typing…" : "";
        return who + " • " + src + model + " • " + t + st;
    }

    private String formatAttachments(List<AIAssistantMessage.Attachment> atts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Attachments:");
        for (int i = 0; i < atts.size(); i++) {
            AIAssistantMessage.Attachment a = atts.get(i);
            sb.append("\n• ");
            if (a.displayName != null && !a.displayName.isEmpty()) sb.append(a.displayName);
            else if (a.uri != null) sb.append(a.uri.toString());
            else sb.append("file");
            if (a.sizeBytes > 0) sb.append(" (").append(a.sizeBytes).append(" bytes)");
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public List<AIAssistantMessage> snapshot() {
        return new ArrayList<>(items);
    }

    public void setMessages(List<AIAssistantMessage> msgs) {
        items.clear();
        if (msgs != null) items.addAll(msgs);
        notifyDataSetChanged();
    }

    public int addMessage(AIAssistantMessage msg) {
        if (msg == null) return -1;
        items.add(msg);
        int pos = items.size() - 1;
        notifyItemInserted(pos);
        return pos;
    }

    public int findById(long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) return i;
        }
        return -1;
    }

    public void updateMessage(long id, AIAssistantMessage newMsg) {
        int idx = findById(id);
        if (idx < 0 || newMsg == null) return;
        items.set(idx, newMsg);
        notifyItemChanged(idx);
    }
}
