package com.gramsetu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class VoiceResponse {

    private String tamilAnswer;
    private String audioBase64;
    private String dialectDetected;
    private String dialectRegion;
    private double confidence;
    private String detectedBy;
    private List<SchemeResponse> matchingSchemes;
    private String suggestedFollowUp;
    private String transcript;   // STT result — populated by /voice, question text for /ask
    /** The language the answer is written in: "english" or "tamil". The field is called tamilAnswer for historical reasons. */
    private String answerLanguage;

    /** Tamil answer (used by the legacy voice endpoint and by error responses). */
    public VoiceResponse(String tamilAnswer, String audioBase64, String dialectDetected, String dialectRegion,
                         double confidence, String detectedBy, List<SchemeResponse> matchingSchemes,
                         String suggestedFollowUp, String transcript) {
        this(tamilAnswer, audioBase64, dialectDetected, dialectRegion, confidence, detectedBy, matchingSchemes,
                suggestedFollowUp, transcript, "tamil");
    }
}
