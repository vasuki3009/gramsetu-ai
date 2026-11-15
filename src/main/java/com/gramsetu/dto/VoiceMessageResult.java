package com.gramsetu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result returned by WhatsAppVoiceService.processVoiceMessage().
 *
 * Contains everything the controller needs to reply to the farmer:
 *  - answerText  → send as a WhatsApp text message (always present)
 *  - transcript  → shown to the farmer so they know what was understood
 *  - dialect / dialectRegion → for logging / analytics
 *  - success     → false if a critical step failed (download or STT)
 *  - failedStep  → which step failed ("DOWNLOAD" | "STT"); null on success
 */
@Data
@Builder
public class VoiceMessageResult {
    private boolean success;
    private String failedStep;

    private String transcript;
    private String dialect;
    private String dialectRegion;

    private String answerText;
}
