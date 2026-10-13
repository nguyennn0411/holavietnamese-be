package com.sep490.backend.entity;

import com.sep490.backend.entity.enums.DestinationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "journey_stops")
@Getter @Setter @NoArgsConstructor
public class JourneyStopJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", unique = true) private DestinationJpaEntity destination;
    @Column(nullable = false) private int displayOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private DestinationStatus status = DestinationStatus.DRAFT;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false) private Instant updatedAt;
}
