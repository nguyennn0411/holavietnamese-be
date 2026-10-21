package com.sep490.backend.service;

import com.sep490.backend.dto.request.*;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.entity.*;
import com.sep490.backend.entity.enums.*;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.mapper.DestinationMapper;
import com.sep490.backend.mapper.DestinationResponseMapper;
import com.sep490.backend.repository.JourneyRepository;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Legacy support for the shared dashboard and opt-in integration seed, not the Explore flow. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class JourneyService {
    // Preserve the old model for existing consumers; new Explore uses DestinationExploreService.
    private static final String UNLOCK_RULE = "PREVIOUS_COMPLETED";
    private static final String COMPLETION_RULE = "ALL_LINKED_LESSONS";
    private final JourneyRepository repository;
    private final Clock clock;
    private final RegionalVocabularyService regionalVocabulary;

    public JourneyResponse.Configuration configuration() {
        var stops = repository.stops().stream().map(s -> new JourneyResponse.AdminStop(
                destination(s.getDestination()), s.getDisplayOrder(), s.getStatus().name())).toList();
        return new JourneyResponse.Configuration(stops, UNLOCK_RULE, COMPLETION_RULE);
    }
    @Transactional
    public JourneyResponse.Configuration configure(JourneyRequest request) {
        repository.configure(request);
        return configuration();
    }
    public JourneyResponse journey(long user, String keyword, VietnamRegion region) {
        var state = state(user);
        String query = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        var rows = state.rows().stream().filter(row -> {
            var d = row.destination();
            return (region == null || d.region() == region) && (query.isEmpty()
                    || d.nameVi().toLowerCase(Locale.ROOT).contains(query) || d.nameEn().toLowerCase(Locale.ROOT).contains(query));
        }).toList();
        int completed = (int) state.rows().stream().filter(s -> "COMPLETED".equals(s.status())).count();
        Long current = state.rows().stream().filter(s -> "CURRENT".equals(s.status())).map(s -> s.destination().id()).findFirst().orElse(null);
        int total = state.rows().size();
        return new JourneyResponse(rows, total, completed, total == 0 ? null : completed * 100 / total, current, true, null);
    }
    public List<JourneyResponse.Passport> passport(long user) {
        var entries = repository.passport(user);
        var result = new ArrayList<JourneyResponse.Passport>();
        for (var entry : entries) {
            var d = entry.getDestination();
            result.add(new JourneyResponse.Passport(entry.getId(), d.getId(), d.getNameVi(), d.getNameEn(), true, entry.getStampedAt()));
        }
        // Shared Achievements may still read historical stamps; Explore issues no new ones.
        return List.copyOf(result);
    }
    public DestinationContentResponse adminContent(long destination) {
        repository.destination(destination);
        return content(destination, Set.of(), false, null, null);
    }
    @Transactional
    public DestinationContentResponse link(long destination, DestinationContentRequest request) {
        repository.link(destination, request);
        return adminContent(destination);
    }
    public DestinationContentResponse learnerContent(long user, long destination) {
        var d = repository.destination(destination);
        if (d.getStatus() != DestinationStatus.PUBLISHED) throw LearningException.notFound("Destination");
        var state = state(user); var row = state.row(destination);
        assertAccessible(row);
        var required = repository.lessons(destination).stream().map(DestinationLessonJpaEntity::getLesson).toList();
        String reason = row != null && "COMPLETED".equals(row.status()) && row.passportStamped() ? null : blockedReason(row, required, state.completedLessons());
        return content(destination, state.completedLessons(), true, row, reason);
    }
    @Transactional
    public DestinationContentResponse start(long user, long destination) {
        repository.lockUser(user);
        var d = repository.lockDestination(destination);
        if (d.getStatus() != DestinationStatus.PUBLISHED) throw LearningException.notFound("Destination");
        var row = state(user).row(destination);
        assertAccessible(row);
        // Published destinations outside the configured journey remain a public catalogue.
        if (row != null) {
            var progress = progress(user, d);
            var now = clock.instant();
            if (progress.getStartedAt() == null) progress.setStartedAt(now);
            if (progress.getStatus() != LessonStatus.COMPLETED) progress.setStatus(LessonStatus.IN_PROGRESS);
            progress.setProgressPercent(row.progressPercent());
            progress.setLastAccessedAt(now);
            repository.save(progress);
        }
        return learnerContent(user, destination);
    }
    @Transactional
    public void complete(long user, long destination) {
        // The database user lock serializes retries across application instances as well.
        // The destination lock also serializes configuration/link changes with completion.
        repository.lockUser(user);
        var d = repository.lockDestination(destination);
        var state = state(user); var row = state.row(destination);
        if (d.getStatus() != DestinationStatus.PUBLISHED || row == null) throw LearningException.notFound("Journey destination");
        assertAccessible(row);
        var previous = repository.progress(user, destination);
        var stamp = repository.passport(user, destination);
        if (previous.filter(JourneyService::completed).isPresent() && stamp.isPresent()) return;
        var required = repository.lessons(destination).stream().map(DestinationLessonJpaEntity::getLesson).toList();
        var blocked = blockedReason(row, required, state.completedLessons());
        if (blocked != null) throw new LearningException(LearningException.Kind.CONFLICT, blocked);
        var progress = previous.orElseGet(() -> progress(user, d));
        var now = clock.instant();
        if (progress.getStartedAt() == null) progress.setStartedAt(now);
        if (progress.getCompletedAt() == null) progress.setCompletedAt(now);
        progress.setLastAccessedAt(now);
        progress.setProgressPercent(100);
        progress.setStatus(LessonStatus.COMPLETED);
        repository.save(progress);
        if (stamp.isEmpty()) {
            var entry = new PassportEntryJpaEntity();
            entry.setUserId(user); entry.setDestination(d); entry.setStampedAt(progress.getCompletedAt());
            repository.save(entry);
        }
    }
    private JourneyProgressJpaEntity progress(long user, DestinationJpaEntity destination) {
        return repository.progress(user, destination.getId()).orElseGet(() -> {
            var p = new JourneyProgressJpaEntity(); p.setUserId(user); p.setDestination(destination); return p;
        });
    }
    private record State(List<JourneyResponse.Stop> rows, Set<Long> completedLessons) {
        JourneyResponse.Stop row(long id) { return rows.stream().filter(r -> r.destination().id().equals(id)).findFirst().orElse(null); }
    }
    private State state(long user) {
        var active = activeStops();
        var progress = repository.progress(user).stream().collect(Collectors.toMap(p -> p.getDestination().getId(), p -> p));
        var completedLessons = repository.completedLessons(user);
        var linked = repository.lessonLinks(active.stream().map(s -> s.getDestination().getId()).toList()).stream()
                .collect(Collectors.groupingBy(DestinationLessonJpaEntity::getDestinationId,
                        Collectors.mapping(DestinationLessonJpaEntity::getLesson, Collectors.toList())));
        var stamped = repository.passport(user).stream().map(p -> p.getDestination().getId()).collect(Collectors.toSet());
        var rows = new ArrayList<JourneyResponse.Stop>();
        boolean previousCompleted = true, currentChosen = false;
        for (int i = 0; i < active.size(); i++) {
            var stop = active.get(i); var d = stop.getDestination(); var p = progress.get(d.getId());
            boolean done = completed(p), accessible = done || previousCompleted;
            var lessons = linked.getOrDefault(d.getId(), List.of());
            int count = (int) lessons.stream().filter(l -> completedLessons.contains(l.getId())).count();
            int percent = done ? 100 : lessons.isEmpty() ? 0 : count * 100 / lessons.size();
            String status = done ? "COMPLETED" : !accessible ? "LOCKED" : !currentChosen ? "CURRENT" : p == null ? "NOT_STARTED" : p.getStatus().name();
            if ("CURRENT".equals(status)) currentChosen = true;
            boolean hasStamp = stamped.contains(d.getId());
            boolean canComplete = accessible && !hasStamp && !lessons.isEmpty() && count == lessons.size();
            rows.add(new JourneyResponse.Stop(destination(d), stop.getDisplayOrder(), i == 0, status, accessible, percent,
                    p == null ? null : p.getStartedAt(), p == null ? null : p.getCompletedAt(), lessons.size(), count, canComplete, hasStamp));
            previousCompleted = done;
        }
        return new State(List.copyOf(rows), completedLessons);
    }
    private String blockedReason(JourneyResponse.Stop row, List<LessonJpaEntity> required, Set<Long> completed) {
        if (row == null) return "Điểm đến chưa được thêm vào hành trình công khai.";
        if (required.isEmpty()) return "Điểm đến chưa có bài học liên kết nên chưa thể hoàn thành.";
        if (row.completedLessons() != required.size()) {
            if (required.stream().anyMatch(l -> !published(l) && !completed.contains(l.getId()))) return "Có bài học liên kết chưa hoàn thành và chưa được xuất bản. Vui lòng chờ cập nhật nội dung.";
            return "Hoàn thành toàn bộ bài học được liên kết trước khi hoàn thành điểm đến.";
        }
        return null;
    }
    private DestinationContentResponse content(long id, Set<Long> completed, boolean publishedOnly, JourneyResponse.Stop row, String reason) {
        var lessons = repository.lessons(id).stream().map(DestinationLessonJpaEntity::getLesson)
                .filter(l -> !publishedOnly || published(l))
                .map(l -> new DestinationContentResponse.Lesson(l.getId(), l.getCourse().getId(), l.getTitle(), l.getTitleVi(), l.getCourse().getTitle(), published(l), completed.contains(l.getId()))).toList();
        var words = repository.words(id).stream().map(DestinationVocabularyJpaEntity::getVocabulary)
                .filter(w -> !publishedOnly || w.getStatus() == VocabularyStatus.PUBLISHED)
                .map(w -> new DestinationContentResponse.Word(w.getId(), w.getWord(), w.getPronunciation(), w.getStatus().name())).toList();
        var variants = regionalVocabulary.forDestination(words.stream().map(DestinationContentResponse.Word::id).toList(),
                repository.destination(id).getRegion(), publishedOnly);
        return new DestinationContentResponse(lessons, words, List.of("CULTURE", "AI_PRACTICE"), row, reason, variants);
    }
    private void assertAccessible(JourneyResponse.Stop row) { if (row != null && !row.accessible()) throw forbidden(); }
    private static LearningException forbidden() { return new LearningException(LearningException.Kind.FORBIDDEN, "Hoàn thành điểm đến trước để mở khóa điểm đến này."); }
    private List<JourneyStopJpaEntity> activeStops() {
        return repository.stops().stream().filter(s -> s.getStatus() == DestinationStatus.PUBLISHED && s.getDestination().getStatus() == DestinationStatus.PUBLISHED).toList();
    }
    private static boolean published(LessonJpaEntity lesson) { return lesson.isPublished() && lesson.getCourse().getStatus() == CourseStatus.PUBLISHED; }
    private static boolean completed(JourneyProgressJpaEntity p) { return p != null && p.getStatus() == LessonStatus.COMPLETED; }
    private static DestinationResponse destination(DestinationJpaEntity d) { return DestinationResponseMapper.toResponse(DestinationMapper.toDomain(d)); }
}
