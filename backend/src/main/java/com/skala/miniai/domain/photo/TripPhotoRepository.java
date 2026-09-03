package com.skala.miniai.domain.photo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPhotoRepository extends JpaRepository<TripPhoto, Long> {

    List<TripPhoto> findByTripIdOrderById(Long tripId);

    Optional<TripPhoto> findByIdAndTripId(Long id, Long tripId);

    /** 파일 제공 경로에서 쓴다. 상대 경로가 실제로 등록된 사진인지 먼저 확인한다. */
    Optional<TripPhoto> findByFilePath(String filePath);
}
