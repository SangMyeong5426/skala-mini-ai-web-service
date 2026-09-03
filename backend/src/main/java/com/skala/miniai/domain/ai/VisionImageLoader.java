package com.skala.miniai.domain.ai;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.skala.miniai.config.UploadConfig;
import com.skala.miniai.domain.photo.TripPhoto;

/**
 * 저장된 사진 파일을 모델이 받을 수 있는 형태로 바꾼다 (07 로드맵 3단계 "이미지 크기·장수 제한").
 *
 * <p><b>원본을 그대로 보내지 않는다.</b> {@code MAX_UPLOAD_SIZE} 가 10MB 라 20장이면 요청이
 * 200MB 를 넘고, base64 는 거기서 다시 33% 가 붙는다. 긴 변을 {@code AI_VISION_MAX_EDGE_PX}
 * 로 줄여 JPEG 로 다시 굽는다 — 물품을 알아보는 데는 이 해상도로 충분하고 토큰 비용도 준다.
 *
 * <p>실패는 예외로 올리지 않고 {@link Optional#empty()} 로 돌려준다. 07 이 실패한 사진을
 * {@code failedPhotoIds} 로 남기고 <b>성공한 사진만</b> 결과에 넣으라고 정했기 때문이다.
 *
 * <p>webp 는 JDK 의 {@code ImageIO} 가 읽지 못한다. 그때는 줄이지 않고 원본 바이트를 그대로
 * 보내되 {@code AI_VISION_MAX_RAW_BYTES} 를 넘으면 실패로 둔다 — 라이브러리를 새로 넣지 않는다.
 */
@Component
public class VisionImageLoader {

    private static final Logger log = LoggerFactory.getLogger(VisionImageLoader.class);

    private final Path uploadDir;
    private final int maxEdgePx;
    private final long maxRawBytes;

    public VisionImageLoader(UploadConfig uploadConfig,
                             @Value("${app.ai.vision.max-edge-px:1024}") int maxEdgePx,
                             @Value("${app.ai.vision.max-raw-bytes:4194304}") long maxRawBytes) {
        this.uploadDir = uploadConfig.dir();
        this.maxEdgePx = maxEdgePx;
        this.maxRawBytes = maxRawBytes;
    }

    /** 읽지 못하면 {@code empty}. 호출한 쪽이 {@code failedPhotoIds} 에 넣는다. */
    public Optional<VisionImage> load(TripPhoto photo) {
        try {
            Path file = uploadDir.resolve(photo.getFilePath()).normalize();
            // PhotoService 와 같은 검사를 여기서도 한다. 시드가 넣은 경로도 이 통로를 지난다.
            if (!file.startsWith(uploadDir) || !Files.isReadable(file)) {
                log.warn("사진 {} 파일을 읽을 수 없습니다: {}", photo.getId(), photo.getFilePath());
                return Optional.empty();
            }

            BufferedImage source = ImageIO.read(file.toFile());
            if (source == null) return rawFallback(photo, file);   // webp 등 ImageIO 가 못 읽는 형식

            byte[] jpeg = toScaledJpeg(source);
            return Optional.of(new VisionImage(photo.getId(), dataUrl("image/jpeg", jpeg)));

        } catch (Exception e) {
            // 사진 한 장이 깨져도 나머지 분석은 계속된다.
            log.warn("사진 {} 를 모델 입력으로 바꾸지 못했습니다", photo.getId(), e);
            return Optional.empty();
        }
    }

    /** 긴 변이 {@code maxEdgePx} 를 넘을 때만 줄인다. 알파 채널은 JPEG 에 없으므로 RGB 로 옮긴다. */
    private byte[] toScaledJpeg(BufferedImage source) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        double ratio = Math.min(1.0, (double) maxEdgePx / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(target, "jpeg", out);
        return out.toByteArray();
    }

    /** ImageIO 가 못 읽는 형식. 원본을 그대로 보내되 너무 크면 포기한다. */
    private Optional<VisionImage> rawFallback(TripPhoto photo, Path file) throws Exception {
        long size = Files.size(file);
        if (size > maxRawBytes) {
            log.warn("사진 {} 는 줄일 수 없고 {} 바이트라 건너뜁니다", photo.getId(), size);
            return Optional.empty();
        }
        return Optional.of(new VisionImage(photo.getId(),
                dataUrl(mimeOf(photo.getFilePath()), Files.readAllBytes(file))));
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /** PhotoService 가 허용한 형식만 저장돼 있다. 그 밖은 jpeg 로 두어도 모델이 알아서 판별한다. */
    private static String mimeOf(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
