package com.gramsetu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SchemeResponse {

    private String schemeName;
    private String eligibility;
    private String benefits;
    private String officialLink;
    private String tamilExplanation;
}
