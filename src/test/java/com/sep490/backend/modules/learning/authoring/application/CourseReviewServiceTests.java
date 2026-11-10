package com.sep490.backend.modules.learning.authoring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CourseReviewServiceTests {
  private ContentStore db;
  private CourseContentService courses;
  private NotificationPort notifications;
  private CourseReviewService service;

  private final LearnerPrincipal manager = actor(31L, "CONTENT");
  private final LearnerPrincipal otherManager = actor(32L, "CONTENT_MANAGER");
  private final LearnerPrincipal admin = actor(1L, "ADMIN");

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    courses = mock(CourseContentService.class);
    notifications = mock(NotificationPort.class);
    service = new CourseReviewService(db, courses, notifications);
  }

  @Test
  void ownerCanRequestEditingForPublishedCourseAndAdminIsNotified() {
    when(db.one(
            "SELECT id,title,status,created_by FROM courses WHERE id=? FOR UPDATE", 9L))
        .thenReturn(Map.of("id", 9L, "title", "Vietnamese A1", "status", "PUBLISHED", "createdBy", 31L));
    when(db.count(
            "SELECT count(*) FROM course_edit_requests WHERE course_id=? AND status='PENDING'", 9L))
        .thenReturn(0L);

    service.requestEdit(9L, "  Update lesson images  ", manager);

    verify(db).insert(
        "INSERT INTO course_edit_requests(course_id,requested_by,reason) VALUES(?,?,?)",
        9L,
        31L,
        "Update lesson images");
    var message = ArgumentCaptor.forClass(NotificationMessage.class);
    verify(notifications).sendToRole(eq("ADMIN"), message.capture());
    assertThat(message.getValue().message()).contains("Vietnamese A1", "Update lesson images");
    assertThat(message.getValue().mandatory()).isTrue();
  }

  @Test
  void editRequestRejectsNonOwnerNonPublishedAndDuplicateRequests() {
    when(db.one(
            "SELECT id,title,status,created_by FROM courses WHERE id=? FOR UPDATE", 9L))
        .thenReturn(Map.of("id", 9L, "title", "Course", "status", "PUBLISHED", "createdBy", 31L));
    assertCode(() -> service.requestEdit(9L, "Reason", otherManager), "COURSE_NOT_OWNED");

    when(db.one(
            "SELECT id,title,status,created_by FROM courses WHERE id=? FOR UPDATE", 10L))
        .thenReturn(Map.of("id", 10L, "title", "Course", "status", "DRAFT", "createdBy", 31L));
    assertCode(() -> service.requestEdit(10L, "Reason", manager), "COURSE_NOT_PUBLISHED");

    when(db.count(
            "SELECT count(*) FROM course_edit_requests WHERE course_id=? AND status='PENDING'", 9L))
        .thenReturn(1L);
    assertCode(() -> service.requestEdit(9L, "Reason", manager), "EDIT_REQUEST_PENDING");
  }

  @Test
  void adminApprovalOpensPublishedCourseForEditingAndNotifiesRequester() {
    when(db.one("SELECT id,title,status FROM courses WHERE id=? FOR UPDATE", 9L))
        .thenReturn(Map.of("id", 9L, "title", "Vietnamese A1", "status", "PUBLISHED"));
    when(db.one(
            "SELECT id,requested_by,reason FROM course_edit_requests"
                + " WHERE course_id=? AND status='PENDING' ORDER BY id DESC LIMIT 1 FOR UPDATE",
            9L))
        .thenReturn(Map.of("id", 88L, "requestedBy", 31L, "reason", "Update"));

    service.reviewEditRequest(9L, "APPROVE", "  Approved  ", admin);

    verify(db).update(
        "UPDATE course_edit_requests SET status=?,reviewed_by=?,review_note=?,"
            + " reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
        "APPROVED",
        1L,
        "Approved",
        88L);
    verify(courses).status(9L, "DRAFT");
    var message = ArgumentCaptor.forClass(NotificationMessage.class);
    verify(notifications).send(eq(31L), message.capture());
    assertThat(message.getValue().title()).contains("mở quyền chỉnh sửa");
  }

  @Test
  void rejectingEditRequestRequiresReasonAndKeepsCoursePublished() {
    when(db.one("SELECT id,title,status FROM courses WHERE id=? FOR UPDATE", 9L))
        .thenReturn(Map.of("id", 9L, "title", "Vietnamese A1", "status", "PUBLISHED"));
    when(db.one(
            "SELECT id,requested_by,reason FROM course_edit_requests"
                + " WHERE course_id=? AND status='PENDING' ORDER BY id DESC LIMIT 1 FOR UPDATE",
            9L))
        .thenReturn(Map.of("id", 88L, "requestedBy", 31L, "reason", "Update"));

    assertThatThrownBy(() -> service.reviewEditRequest(9L, "REJECT", " ", admin))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("reason is required");
    verify(courses, never()).status(any(Long.class), anyString());

    service.reviewEditRequest(9L, "REJECT", "Not necessary", admin);
    verify(db).update(
        anyString(), eq("REJECTED"), eq(1L), eq("Not necessary"), eq(88L));
    verify(courses, never()).status(9L, "DRAFT");
  }

  @Test
  void managerCanOnlyEditOwnedDraftOrRejectedCourse() {
    String sql = "SELECT c.created_by,c.submitted_by,c.status,u.role AS author_role FROM courses c"
        + " LEFT JOIN users u ON u.id=c.created_by WHERE c.id=?";
    when(db.one(sql, 9L))
        .thenReturn(Map.of("createdBy", 31L, "status", "DRAFT", "authorRole", "CONTENT"));
    service.assertCanEdit(9L, manager);

    when(db.one(sql, 10L))
        .thenReturn(Map.of("createdBy", 32L, "status", "DRAFT", "authorRole", "CONTENT"));
    assertCode(() -> service.assertCanEdit(10L, manager), "COURSE_NOT_OWNED");

    when(db.one(sql, 11L))
        .thenReturn(Map.of("createdBy", 31L, "status", "PUBLISHED", "authorRole", "CONTENT"));
    assertCode(() -> service.assertCanEdit(11L, manager), "COURSE_NOT_EDITABLE");
  }

  @Test
  void readinessReportsEveryMissingPublicationRequirement() {
    when(db.one("SELECT id FROM courses WHERE id=?", 9L)).thenReturn(Map.of("id", 9L));
    when(db.count(anyString(), any(Object[].class)))
        .thenReturn(0L, 0L, 1L, 1L, 1L, 1L, 1L);

    assertThat(service.readiness(9L))
        .containsExactly(
            "Add at least one module.",
            "Add at least one lesson.",
            "Every module needs a lesson.",
            "Publish every lesson before submitting.",
            "Every lesson needs a published required learning activity.",
            "Every lesson needs a published quiz activity.",
            "Set a published final course quiz and attach it to a lesson.");
  }

  private static LearnerPrincipal actor(long id, String role) {
    return new LearnerPrincipal(id, role.toLowerCase() + "@test.local", "", true, role);
  }

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ContentException.class, error -> assertThat(error.code).isEqualTo(code));
  }
}
