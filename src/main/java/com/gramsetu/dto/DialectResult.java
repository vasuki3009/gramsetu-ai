package com.gramsetu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DialectResult {

    private String dialect;
    private double confidence;
    private String dialectRegion;
    private String detectedBy;

    public static DialectResult kongu() {
        return new DialectResult("KONGU", 70, "Kongu Nadu", "default-farming");
    }

    public static DialectResult kongu(double confidence) {
        return new DialectResult("KONGU", confidence, "Hosur / Coimbatore", "KEYWORD");
    }

    public static DialectResult madurai(double confidence) {
        return new DialectResult("MADURAI", confidence, "Madurai / Dindigul", "KEYWORD");
    }

    public static DialectResult nellai(double confidence) {
        return new DialectResult("NELLAI", confidence, "Tirunelveli / Thoothukudi", "KEYWORD");
    }

    public static DialectResult chennai(double confidence) {
        return new DialectResult("CHENNAI", confidence, "Chennai / Kancheepuram", "KEYWORD");
    }

    public static DialectResult standard() {
        return new DialectResult("STANDARD", 1.0, "Tamil Nadu", "FALLBACK");
    }
}
