package com.gramsetu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CropResponse {

    private String diagnosis;
    private boolean verified;
    private String verifiedSource;
    private String audioBase64;
    /** True when the diagnosis text is a "not sure" / "unavailable" message: no disease, no medicine. */
    private boolean unsure;

    public CropResponse(String diagnosis, boolean verified, String verifiedSource, String audioBase64) {
        this(diagnosis, verified, verifiedSource, audioBase64, false);
    }
}
