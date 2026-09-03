package com.skala.miniai.domain.master;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemWeightRepository extends JpaRepository<ItemWeight, Long> {

    Optional<ItemWeight> findByKeyword(String keyword);

    List<ItemWeight> findByKeywordIn(List<String> keywords);
}
