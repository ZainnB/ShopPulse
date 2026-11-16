package com.shoppulse.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ai_briefings")
public class AiBriefing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private String model = "unknown";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AiBriefing() {
    }

    public AiBriefing(LocalDate weekStart, String content, String model) {
        this.weekStart = weekStart;
        this.content = content;
        this.model = model;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getWeekStart() { return weekStart; }
    public String getContent() { return content; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
}
