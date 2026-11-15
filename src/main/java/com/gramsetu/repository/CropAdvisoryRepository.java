package com.gramsetu.repository;

import com.gramsetu.model.CropAdvisory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CropAdvisoryRepository extends JpaRepository<CropAdvisory, Long> {
    Optional<CropAdvisory> findByDiseaseNameContainingIgnoreCase(String diseaseName);
}
