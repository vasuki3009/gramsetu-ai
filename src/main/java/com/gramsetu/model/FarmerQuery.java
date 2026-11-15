package com.gramsetu.model;

import com.gramsetu.dto.DialectResult;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "farmer_queries")
@Data
@NoArgsConstructor
public class FarmerQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String queryText;

    @Column(columnDefinition = "TEXT")
    private String responseText;

    @Column(length = 20)
    private String dialect;

    @Column(length = 100)
    private String dialectRegion;

    private double dialectConfidence;

    @Column(length = 20)
    private String detectedBy;

    @Column(length = 100)
    private String location;

    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    public FarmerQuery(String queryText, String responseText, DialectResult dialect, String location) {
        this.queryText = queryText;
        this.responseText = responseText;
        this.dialect = dialect.getDialect();
        this.dialectRegion = dialect.getDialectRegion();
        this.dialectConfidence = dialect.getConfidence();
        this.detectedBy = dialect.getDetectedBy();
        this.location = location;
    }
}
