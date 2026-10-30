package com.sep490.backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryProperties {
    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private String avatarFolder = "hola-vietnamese/avatars";
    private String audioFolder = "hola-vietnamese/audio";
    private String courseCoverFolder = "hola-vietnamese/courses/covers";
    private String lessonImageFolder = "hola-vietnamese/lessons/images";
    private String lessonVideoFolder = "hola-vietnamese/lessons/videos";
    private String questionImageFolder = "hola-vietnamese/questions/images";
    private String questionAudioFolder = "hola-vietnamese/questions/audio";
}
