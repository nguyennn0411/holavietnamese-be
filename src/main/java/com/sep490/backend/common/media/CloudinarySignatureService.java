package com.sep490.backend.common.media;

import com.sep490.backend.common.config.CloudinaryProperties;
import com.sep490.backend.common.dto.CloudinaryUploadSignatureResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CloudinarySignatureService {
    CloudinaryProperties properties;

    public CloudinaryUploadSignatureResponse createAvatarSignature(Long userId) {
        ensureConfigured();

        long timestamp = Instant.now().getEpochSecond();
        String folder = normalizeFolder(properties.getAvatarFolder());
        String publicId = "user-" + userId + "-" + UUID.randomUUID();
        String signaturePayload = "folder=" + folder
                + "&public_id=" + publicId
                + "&timestamp=" + timestamp
                + properties.getApiSecret();

        return CloudinaryUploadSignatureResponse.builder()
                .cloudName(properties.getCloudName())
                .apiKey(properties.getApiKey())
                .uploadUrl("https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/image/upload")
                .folder(folder)
                .publicId(publicId)
                .timestamp(timestamp)
                .signature(sha1(signaturePayload))
                .build();
    }

    public CloudinaryUploadSignatureResponse createAudioSignature(String titleOrPrefix) {
        ensureConfigured();

        long timestamp = Instant.now().getEpochSecond();
        String folder = normalizeFolder(properties.getAudioFolder());
        String safePrefix = (titleOrPrefix == null || titleOrPrefix.isBlank())
                ? "audio"
                : titleOrPrefix.trim().toLowerCase().replaceAll("[^a-zA-Z0-9_-]+", "-");
        String publicId = safePrefix + "-" + UUID.randomUUID();
        String signaturePayload = "folder=" + folder
                + "&public_id=" + publicId
                + "&timestamp=" + timestamp
                + properties.getApiSecret();

        return CloudinaryUploadSignatureResponse.builder()
                .cloudName(properties.getCloudName())
                .apiKey(properties.getApiKey())
                // Cloudinary xử lý file âm thanh (mp3, wav, aac...) dưới resource_type video hoặc auto
                .uploadUrl("https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/video/upload")
                .folder(folder)
                .publicId(publicId)
                .timestamp(timestamp)
                .signature(sha1(signaturePayload))
                .build();
    }

    public CloudinaryUploadSignatureResponse createCourseCoverSignature() {
        ensureConfigured();

        long timestamp = Instant.now().getEpochSecond();
        String folder = normalizeFolder(properties.getCourseCoverFolder());
        String publicId = "course-cover-" + UUID.randomUUID();
        String signaturePayload = "folder=" + folder
                + "&public_id=" + publicId
                + "&timestamp=" + timestamp
                + properties.getApiSecret();

        return CloudinaryUploadSignatureResponse.builder()
                .cloudName(properties.getCloudName())
                .apiKey(properties.getApiKey())
                .uploadUrl("https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/image/upload")
                .folder(folder)
                .publicId(publicId)
                .timestamp(timestamp)
                .signature(sha1(signaturePayload))
                .build();
    }

    public CloudinaryUploadSignatureResponse createLessonMediaSignature(String type) {
        ensureConfigured();
        if (!"image".equals(type) && !"video".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported lesson media type.");
        }

        long timestamp = Instant.now().getEpochSecond();
        String folder = normalizeFolder("image".equals(type)
                ? properties.getLessonImageFolder() : properties.getLessonVideoFolder());
        String publicId = "lesson-" + type + "-" + UUID.randomUUID();
        String signaturePayload = "folder=" + folder
                + "&public_id=" + publicId
                + "&timestamp=" + timestamp
                + properties.getApiSecret();

        return CloudinaryUploadSignatureResponse.builder()
                .cloudName(properties.getCloudName())
                .apiKey(properties.getApiKey())
                .uploadUrl("https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/" + type + "/upload")
                .folder(folder)
                .publicId(publicId)
                .timestamp(timestamp)
                .signature(sha1(signaturePayload))
                .build();
    }

    public CloudinaryUploadSignatureResponse createQuestionMediaSignature(String type) {
        ensureConfigured();
        if (!"image".equals(type) && !"audio".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported question media type.");
        }

        long timestamp = Instant.now().getEpochSecond();
        String folder = normalizeFolder("image".equals(type)
                ? properties.getQuestionImageFolder() : properties.getQuestionAudioFolder());
        String publicId = "question-" + type + "-" + UUID.randomUUID();
        String signaturePayload = "folder=" + folder
                + "&public_id=" + publicId
                + "&timestamp=" + timestamp
                + properties.getApiSecret();

        return CloudinaryUploadSignatureResponse.builder()
                .cloudName(properties.getCloudName())
                .apiKey(properties.getApiKey())
                .uploadUrl("https://api.cloudinary.com/v1_1/" + properties.getCloudName()
                        + ("image".equals(type) ? "/image/upload" : "/video/upload"))
                .folder(folder)
                .publicId(publicId)
                .timestamp(timestamp)
                .signature(sha1(signaturePayload))
                .build();
    }

    public boolean isAllowedAvatarUrl(String avatarUrl) {
        ensureConfigured();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(avatarUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/" + properties.getCloudName() + "/image/upload/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isAllowedAudioUrl(String audioUrl) {
        ensureConfigured();
        if (audioUrl == null || audioUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(audioUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && (uri.getPath().startsWith("/" + properties.getCloudName() + "/video/upload/")
                    || uri.getPath().startsWith("/" + properties.getCloudName() + "/raw/upload/"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void ensureConfigured() {
        if (isBlank(properties.getCloudName()) || isBlank(properties.getApiKey()) || isBlank(properties.getApiSecret())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.");
        }
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "hola-vietnamese/avatars";
        }
        return folder.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 digest is not available", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
