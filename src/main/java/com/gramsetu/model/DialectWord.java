package com.gramsetu.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dialect_words")
@Data
@NoArgsConstructor
public class DialectWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String word;

    private String dialect;

    private double weight;
}
