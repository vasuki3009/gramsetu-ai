package com.gramsetu.repository;

import com.gramsetu.model.DialectWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DialectWordRepository extends JpaRepository<DialectWord, Long> {
}
