package com.sep490.backend.service.websiteimport;

import com.sep490.backend.exception.ContentException;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Network boundary for imports. Every request and redirect is checked against SSRF targets. */
@Component
public class SafeWebFetcher {
  static final int MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;
  static final int MAX_REDIRECTS = 4;
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

  public Fetched fetch(String rawUrl) {
    URI uri = publicUri(rawUrl);
    for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
      try {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
            .header("User-Agent", "HolaVietnameseCourseImporter/1.0 (+admin-reviewed educational import)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.2")
            .GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
          String location = response.headers().firstValue("location")
              .orElseThrow(() -> ContentException.invalid("Nguồn trả về redirect không hợp lệ."));
          uri = publicUri(uri.resolve(location).toString());
          continue;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300)
          throw ContentException.invalid("Không tải được nguồn (HTTP " + response.statusCode() + "). Hãy dùng file XHTML/XML chính thức nếu website chặn truy cập.");
        String type = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (!(type.contains("html") || type.contains("xml") || type.isBlank()))
          throw ContentException.invalid("URL phải trả về HTML hoặc XHTML, nhận được: " + type);
        byte[] body = limited(response.body(), MAX_DOCUMENT_BYTES);
        return new Fetched(uri, body, type);
      } catch (ContentException exception) {
        throw exception;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw ContentException.invalid("Kết nối tới nguồn đã bị gián đoạn.");
      } catch (Exception exception) {
        throw ContentException.invalid("Không thể kết nối an toàn tới nguồn: " + exception.getMessage());
      }
    }
    throw ContentException.invalid("Nguồn chuyển hướng quá nhiều lần.");
  }

  public WebsiteImportModel.MediaProbe probe(String rawUrl) {
    try {
      URI uri = publicUri(rawUrl);
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
          .header("User-Agent", "HolaVietnameseCourseImporter/1.0")
          .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      String mime = response.headers().firstValue("content-type").orElse(null);
      Long length = response.headers().firstValueAsLong("content-length").isPresent()
          ? response.headers().firstValueAsLong("content-length").getAsLong() : null;
      String status = response.statusCode() >= 200 && response.statusCode() < 400
          ? "AVAILABLE_EXTERNAL" : "LOAD_FAILED";
      return new WebsiteImportModel.MediaProbe(status, mime, length);
    } catch (Exception ignored) {
      return new WebsiteImportModel.MediaProbe("LOAD_FAILED", null, null);
    }
  }

  public URI publicUri(String raw) {
    try {
      URI uri = URI.create(Objects.requireNonNull(raw, "URL").trim()).normalize();
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null)
        throw ContentException.invalid("Chỉ hỗ trợ URL http(s) công khai, không kèm thông tin đăng nhập.");
      if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)
        throw ContentException.invalid("Cổng URL không được phép.");
      for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()
            || blockedRange(address.getAddress()))
          throw ContentException.invalid("URL nội bộ hoặc địa chỉ mạng riêng không được phép.");
      return uri;
    } catch (ContentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ContentException.invalid("URL nguồn không hợp lệ hoặc không phân giải được.");
    }
  }

  private static boolean blockedRange(byte[] address) {
    if (address.length == 4) {
      int a = address[0] & 255, b = address[1] & 255;
      return a == 0 || a == 10 || a == 127 || a >= 224
          || a == 100 && b >= 64 && b <= 127
          || a == 169 && b == 254 || a == 172 && b >= 16 && b <= 31
          || a == 192 && b == 168 || a == 198 && (b == 18 || b == 19);
    }
    return address.length == 16 && (address[0] & 0xfe) == 0xfc;
  }

  private static byte[] limited(java.io.InputStream input, int maximum) throws java.io.IOException {
    try (input; var output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; int read, total = 0;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maximum) throw ContentException.invalid("Trang nguồn vượt quá giới hạn 8 MiB.");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  public record Fetched(URI finalUri, byte[] body, String contentType) {}
}
