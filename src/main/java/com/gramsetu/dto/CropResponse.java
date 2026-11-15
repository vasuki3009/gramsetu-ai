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
}
