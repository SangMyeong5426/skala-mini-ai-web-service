package com.skala.miniai.domain.photo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectedObjectRepository extends JpaRepository<DetectedObject, Long> {

    List<DetectedObject> findByPhotoIdInOrderById(Collection<Long> photoIds);
}
