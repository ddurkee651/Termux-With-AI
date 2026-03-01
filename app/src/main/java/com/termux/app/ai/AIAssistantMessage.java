package com.termux.app.ai;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AIAssistantMessage {

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    public enum Status {
        FINAL,
        STREAMING,
        ERROR
    }

    public enum ModelSource {
        LOCAL,
        HUGGINGFACE,
        GEMINI
    }

    public static final class Attachment {
        public final Uri uri;
        public final String displayName;
        public final String mimeType;
        public final long sizeBytes;

        public Attachment(Uri uri, String displayName, String mimeType, long sizeBytes) {
            this.uri = uri;
            this.displayName = displayName == null ? "" : displayName;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.sizeBytes = sizeBytes;
        }
    }

    public final long id;
    public final long timestampMs;
    public final Role role;
    public final Status status;
    public final ModelSource modelSource;
    public final String modelId;
    public final String text;
    public final String errorText;
    public final List<Attachment> attachments;

    public AIAssistantMessage(long id,
                             long timestampMs,
                             Role role,
                             Status status,
                             ModelSource modelSource,
                             String modelId,
                             String text,
                             String errorText,
                             List<Attachment> attachments) {
        this.id = id;
        this.timestampMs = timestampMs;
        this.role = role == null ? Role.ASSISTANT : role;
        this.status = status == null ? Status.FINAL : status;
        this.modelSource = modelSource == null ? ModelSource.LOCAL : modelSource;
        this.modelId = modelId == null ? "" : modelId;
        this.text = text == null ? "" : text;
        this.errorText = errorText == null ? "" : errorText;
        if (attachments == null || attachments.isEmpty()) {
            this.attachments = Collections.emptyList();
        } else {
            this.attachments = Collections.unmodifiableList(new ArrayList<>(attachments));
        }
    }

    public boolean isUser() {
        return role == Role.USER;
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public static AIAssistantMessage user(long id, long ts, String text, List<Attachment> attachments, ModelSource modelSource, String modelId) {
        return new AIAssistantMessage(id, ts, Role.USER, Status.FINAL, modelSource, modelId, text, "", attachments);
    }

    public static AIAssistantMessage assistant(long id, long ts, String text, Status status, ModelSource modelSource, String modelId) {
        return new AIAssistantMessage(id, ts, Role.ASSISTANT, status, modelSource, modelId, text, "", Collections.emptyList());
    }

    public static AIAssistantMessage error(long id, long ts, String errorText, ModelSource modelSource, String modelId) {
        return new AIAssistantMessage(id, ts, Role.ASSISTANT, Status.ERROR, modelSource, modelId, "", errorText, Collections.emptyList());
    }
}
