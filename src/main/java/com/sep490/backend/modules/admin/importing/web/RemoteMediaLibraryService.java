package com.sep490.backend.modules.admin.importing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.dto.CloudinaryUploadSignatureResponse;
import com.sep490.backend.common.media.CloudinarySignatureService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Copies rights-confirmed public media into the configured Hola Vietnamese media library. */
@Service
@RequiredArgsConstructor
public class RemoteMediaLibraryService {
  private final CloudinarySignatureService signatures;
  private final SafeWebFetcher safeWeb;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public ImportResult copy(String sourceUrl, String blockType) {
    try {
      safeWeb.publicUri(sourceUrl);
      CloudinaryUploadSignatureResponse signed = "AUDIO".equals(blockType)
          ? signatures.createAudioSignature("website-import")
          : signatures.createLessonMediaSignature("VIDEO".equals(blockType) ? "video" : "image");
      String boundary = "HolaImport" + UUID.randomUUID().toString().replace("-", "");
      Map<String,String> fields = new LinkedHashMap<>();
      fields.put("file", sourceUrl); fields.put("api_key", signed.getApiKey());
      fields.put("timestamp", String.valueOf(signed.getTimestamp())); fields.put("signature", signed.getSignature());
      fields.put("folder", signed.getFolder()); fields.put("public_id", signed.getPublicId());
      byte[] body = multipart(boundary, fields);
      var request = HttpRequest.newBuilder(URI.create(signed.getUploadUrl())).timeout(Duration.ofSeconds(90))
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        return new ImportResult(sourceUrl, "MEDIA_UPLOAD_FAILED", "Media Library trả về HTTP " + response.statusCode());
      String secureUrl = json.readTree(response.body()).path("secure_url").asText(null);
      if (secureUrl == null || !secureUrl.startsWith("https://res.cloudinary.com/"))
        return new ImportResult(sourceUrl, "MEDIA_UPLOAD_FAILED", "Media Library không trả về secure_url hợp lệ.");
      return new ImportResult(secureUrl, "IMPORTED_MEDIA", null);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new ImportResult(sourceUrl, "MEDIA_UPLOAD_FAILED", "Tác vụ tải media bị gián đoạn.");
    } catch (Exception exception) {
      return new ImportResult(sourceUrl, "MEDIA_UPLOAD_FAILED", exception.getMessage());
    }
  }

  private static byte[] multipart(String boundary, Map<String,String> fields) {
    StringBuilder body = new StringBuilder();
    for (var field : fields.entrySet()) body.append("--").append(boundary).append("\r\n")
        .append("Content-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n")
        .append(field.getValue()).append("\r\n");
    body.append("--").append(boundary).append("--\r\n");
    return body.toString().getBytes(StandardCharsets.UTF_8);
  }

  public record ImportResult(String mediaUrl, String status, String error) {}
}
