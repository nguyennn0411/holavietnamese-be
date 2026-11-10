package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.learning.progress.port.VocabularyProgressReader;
import com.sep490.backend.modules.vocabulary.entity.VocabularyNotebookEntry;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface VocabularyNotebookRepository
        extends JpaRepository<VocabularyNotebookEntry, Long>, VocabularyProgressReader {

    /** Khoa muc so tu thuoc nguoi dung de cap nhat on tap an toan. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VocabularyNotebookEntry v where v.id = :id and v.user.id = :userId")
    Optional<VocabularyNotebookEntry> lockOwned(Long id, Long userId);

    // Xoa truc tiep de database xoa kem trang thai va lich su on tap,
    // ke ca khi trang thai dang duoc quan ly trong transaction hien tai.
    /** Xoa muc so tu theo chu so huu bang truy van truc tiep de database xoa kem lich su. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VocabularyNotebookEntry v where v.id = :id and v.user.id = :userId")
    int deleteOwned(Long id, Long userId);

    @Query(
            """
            select v from VocabularyNotebookEntry v
            left join VocabularyReviewState s on s.id = v.id
            where v.user.id = :userId and (s.id is null or s.nextReviewAt <= :now)
            and not exists (select log.id from VocabularyReviewLog log
                where log.vocabularyEntry.id = v.id and log.sessionId = :sessionId)
            order by coalesce(s.nextReviewAt, v.createdAt), v.id
            """)
    /** Lay cac tu den han va chua duoc danh gia trong phien on tap. */
    List<VocabularyNotebookEntry> findDue(Long userId, java.time.Instant now, String sessionId);

    /** Tim muc so tu theo ID va chu so huu. */
    Optional<VocabularyNotebookEntry> findByIdAndUserId(Long id, Long userId);

    /** Dem so tu da luu cua nguoi dung cho tien do va huy hieu. */
    @Override
    @Query("select count(entry) from VocabularyNotebookEntry entry where entry.user.id = :userId")
    int countByUser(Long userId);

    /** Tim lien ket tu chuan trong so ca nhan de chan luu trung. */
    Optional<VocabularyNotebookEntry> findByUser_IdAndMasterVocabulary_Id(
            Long userId, Long masterVocabularyId);

    @Query(
            """
select v from VocabularyNotebookEntry v left join v.lesson l
where v.user.id = :userId
and (:courseId is null or l.course.id = :courseId)
and (:lessonId is null or l.id = :lessonId)
and (:search = '' or locate(lower(:search), lower(v.word)) > 0 or locate(lower(:search), lower(v.meaning)) > 0)
order by v.createdAt desc, v.id desc
""")
    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    List<VocabularyNotebookEntry> search(Long userId, String search, Long courseId, Long lessonId);
}
