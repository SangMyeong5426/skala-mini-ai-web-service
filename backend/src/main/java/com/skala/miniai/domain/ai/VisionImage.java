package com.skala.miniai.domain.ai;

/**
 * 모델에 넘길 사진 한 장.
 *
 * @param photoId {@code trip_photos.id}. 모델이 이 값을 그대로 돌려줘야 인식 결과를 사진에 붙일 수 있다.
 * @param dataUrl {@code data:image/jpeg;base64,...} 형식. OpenAI 는 URL 대신 이 형식을 받는다 —
 *                업로드 디렉터리는 로그인 뒤에만 열리므로 외부에서 가져갈 수 있는 URL 이 없다.
 */
public record VisionImage(long photoId, String dataUrl) { }
