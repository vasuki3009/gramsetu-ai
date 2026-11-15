package com.gramsetu.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "crop_advisories")
@Data
@NoArgsConstructor
public class CropAdvisory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String diseaseName;

    @Column(columnDefinition = "TEXT")
    private String verifiedSolution;

    private String cropType;

    private String source;
}
