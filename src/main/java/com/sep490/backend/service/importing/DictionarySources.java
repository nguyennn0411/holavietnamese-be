package com.sep490.backend.service.importing;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Only reviewed source manifests are deployed; uploads cannot claim their own license. */
@Component
public class DictionarySources {
    private final JsonNode manifest;
    public DictionarySources(ObjectMapper json) throws IOException {
        try (var input = new ClassPathResource("dictionary/source-manifest.json").getInputStream()) { manifest = json.readTree(input); }
    }
    public JsonNode profile() { return manifest; }
    public boolean accepts(String sha256) {
        for (var file : manifest.path("acceptedFiles")) if (file.path("sha256").asText().equals(sha256)) return true;
        return false;
    }
    public ClassPathResource pilot() { return new ClassPathResource("dictionary/"+manifest.path("acceptedFiles").get(0).path("filename").asText()); }
}
