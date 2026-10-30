package com.sep490.backend.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessage {

    @Id
    @Column(length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiSession session;

    @Column(nullable = false, length = 20)
    private String sender; // 'user' or 'ai'

    @Column(length = 100)
    private String name;

    @Column(length = 255)
    private String avatar;

    @Column(nullable = false, length = 50)
    private String mode;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String translation;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "native_alternative", columnDefinition = "TEXT")
    private String nativeAlternative;

    @Column(name = "cultural_note", columnDefinition = "TEXT")
    private String culturalNote;

    @Column(name = "grammar_check_json", columnDefinition = "JSON")
    private String grammarCheckJson;

    public String getGrammarCorrection() {
        return grammarCheckJson;
    }

    public void setGrammarCorrection(String grammarCorrection) {
        this.grammarCheckJson = grammarCorrection;
    }

    public String getFeedbackJson() {
        return grammarCheckJson;
    }

    @Column(name = "saved_words_json", columnDefinition = "JSON")
    private String savedWordsJson;

    @Column(name = "tokens_in")
    @Builder.Default
    private Integer tokensIn = 0;

    @Column(name = "tokens_out")
    @Builder.Default
    private Integer tokensOut = 0;

    @Column(name = "latency_ms")
    @Builder.Default
    private Integer latencyMs = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
