package com.gramsetu.repository;

import com.gramsetu.model.FarmerQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmerQueryRepository extends JpaRepository<FarmerQuery, Long> {
}
