#!/usr/bin/env python3
"""Import MSU's Advanced Vietnamese as one draft course per chapter."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


BOOK_TITLE = "Advanced Vietnamese"
BOOK_AUTHOR = "Tung Hoang"
BOOK_YEAR = "2024"
BOOK_URL = "https://openbooks.lib.msu.edu/advancedvietnamese/"
LICENSE_NAME = "Creative Commons Attribution-NonCommercial 4.0 International"
LICENSE_URL = "https://creativecommons.org/licenses/by-nc/4.0/"

CHAPTERS = [
    (1, "In the Workplace", "Nơi làm việc"),
    (2, "The Rising Economy", "Nền kinh tế đang phát triển"),
    (3, "The Country by the Sea", "Đất nước bên bờ biển"),
    (4, "Health and Well-being", "Sức khỏe và hạnh phúc"),
    (5, "For a Better Education", "Vì một nền giáo dục tốt đẹp hơn"),
    (6, "Women in Society", "Phụ nữ trong xã hội"),
    (7, "Culture and Heritages", "Văn hóa và di sản"),
]

LESSON_TITLES = {
    1: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Flexible Working", "Đọc: Chế độ làm việc linh hoạt", "NORMAL"),
        ("Listening: Workplace Trends in 2023", "Nghe: Xu thế công việc năm 2023", "NORMAL"),
        ("Speaking: Attractive Careers", "Nói: Những công việc hấp dẫn", "CONVERSATION"),
        ("Writing: A Job Application Letter", "Viết: Thư xin việc", "NORMAL"),
    ],
    2: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Vietnam's Economy after COVID", "Đọc: Kinh tế Việt Nam sau COVID", "NORMAL"),
        ("Listening: Exporting Organic Produce", "Nghe: Xuất khẩu nông sản hữu cơ", "NORMAL"),
        ("Speaking: Famous Vietnamese Companies", "Nói: Các công ty nổi tiếng", "CONVERSATION"),
        ("Writing: Describing a Chart", "Viết: Mô tả biểu đồ", "NORMAL"),
    ],
    3: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Vietnam's Most Beautiful Coastal Areas", "Đọc: Những vùng biển đẹp nhất Việt Nam", "NORMAL"),
        ("Listening: Travelling in Cat Ba", "Nghe: Du lịch Cát Bà", "NORMAL"),
        ("Speaking: Coastal Disasters", "Nói: Thảm họa biển", "CONVERSATION"),
        ("Writing: Planning an Itinerary", "Viết: Lên lịch trình", "NORMAL"),
    ],
    4: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Mental Health", "Đọc: Sức khỏe tinh thần", "NORMAL"),
        ("Listening: A Vegetarian Diet", "Nghe: Chế độ ăn chay", "NORMAL"),
        ("Speaking: Healthy Habits", "Nói: Những thói quen vì sức khỏe", "CONVERSATION"),
        ("Writing: Traditional Treatments", "Viết: Phương pháp điều trị truyền thống", "NORMAL"),
    ],
    5: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Helping Highland Children Attend School", "Đọc: Để trẻ vùng cao được tới trường", "NORMAL"),
        ("Listening: A Happy School", "Nghe: Trường học hạnh phúc", "NORMAL"),
        ("Speaking: Let Children Go to School", "Nói: Cho em đến trường", "CONVERSATION"),
        ("Writing: Useful School Subjects", "Viết: Các môn học có ích", "NORMAL"),
    ],
    6: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Important Women", "Đọc: Những người phụ nữ quan trọng", "NORMAL"),
        ("Listening: Gender Equality in Everyday Life", "Nghe: Bình đẳng giới từ những điều nhỏ nhất", "NORMAL"),
        ("Speaking: Overcoming Gender Stereotypes", "Nói: Xóa bỏ định kiến giới", "CONVERSATION"),
        ("Writing: A Biography", "Viết: Giới thiệu tiểu sử", "NORMAL"),
    ],
    7: [
        ("Introduction and Objectives", "Giới thiệu và mục tiêu", "NORMAL"),
        ("Reading: Vietnamese Traditional Performing Arts", "Đọc: Nghệ thuật biểu diễn dân gian Việt Nam", "CULTURE"),
        ("Listening: Dong Ho Folk Paintings", "Nghe: Tranh dân gian Đông Hồ", "CULTURE"),
        ("Speaking: World Heritage in Vietnam", "Nói: Di sản thế giới tại Việt Nam", "CULTURE"),
        ("Writing: Introducing an Artwork", "Viết: Giới thiệu tác phẩm nghệ thuật", "CULTURE"),
        ("Listening Transcript", "Lời thoại bài nghe", "REVIEW"),
        ("Speaking Video Transcript", "Lời thoại video bài nói", "REVIEW"),
    ],
}


@dataclass
class LessonSource:
    title_en: str
    title_vi: str
    lesson_type: str
    body: str
    pages: list[int]


@dataclass
class ChapterSource:
    number: int
    title_en: str
    title_vi: str
    lessons: list[LessonSource]
    pages: list[int]


def split_pages(text: str) -> dict[int, str]:
    parts = re.split(r"===== PDF PAGE (\d+) =====", text)
    return {int(parts[index]): parts[index + 1] for index in range(1, len(parts), 2)}


def locate_chapter_starts(pages: dict[int, str]) -> dict[int, int]:
    starts: dict[int, int] = {}
    for number, _, _ in CHAPTERS:
        matches = [
            page
            for page, body in pages.items()
            if re.search(rf"(?m)^CHAPTER {number}:", body)
        ]
        if not matches:
            raise ValueError(f"Cannot locate chapter {number}")
        starts[number] = min(matches)
    return starts


def locate_lesson_starts(
    pages: dict[int, str], chapter: int, after_page: int
) -> list[int]:
    result: list[int] = []
    for section in range(1, 6):
        pattern = re.compile(rf"(?m)^{chapter}\.{section}\.\s+")
        matches = [
            page
            for page, body in pages.items()
            if page > after_page and pattern.search(body)
        ]
        if not matches:
            raise ValueError(f"Cannot locate section {chapter}.{section}")
        result.append(min(matches))
    return result


def locate_appendices(pages: dict[int, str], after_page: int) -> list[int]:
    headings = [
        "Appendix 1 - Chapter 7",
        "Appendix 2 - Chapter 7",
    ]
    result = []
    for heading in headings:
        matches = [
            page for page, body in pages.items() if page > after_page and heading in body
        ]
        if not matches:
            raise ValueError(f"Cannot locate {heading}")
        result.append(min(matches))
    return result


def clean_page_lines(body: str) -> list[str]:
    output: list[str] = []
    for raw in body.splitlines():
        line = re.sub(r"\s+", " ", raw.strip())
        if not line:
            output.append("")
            continue
        if re.match(r"^\d+\s*\|\s*(Chapter|Appendix)", line, re.I):
            continue
        if re.match(r"^(Chapter|Appendix).+\|\s*\d+$", line, re.I):
            continue
        if re.match(r"^PART [IVXLCDM]+$", line, re.I):
            continue
        if re.match(r"^CHAPTER \d+:", line, re.I):
            continue
        output.append(line)
    return output


def tidy(lines: list[str]) -> str:
    output: list[str] = []
    previous_blank = True
    for value in lines:
        if not value:
            if output and not previous_blank:
                output.append("")
            previous_blank = True
            continue
        previous_blank = False
        output.append(value)
    while output and not output[-1]:
        output.pop()
    body = "\n".join(output)
    body = re.sub(
        r"(https://openbooks\.lib\.msu\.edu/)\n(advancedvietnamese/)",
        r"\1\2",
        body,
    )
    body = re.sub(r"\n{3,}", "\n\n", body)
    return body.strip()


def extract_range(pages: dict[int, str], start: int, end: int) -> str:
    lines: list[str] = []
    for page in range(start, end + 1):
        lines.extend(clean_page_lines(pages.get(page, "")))
    return tidy(lines)


def parse_source(text_path: Path) -> list[ChapterSource]:
    pages = split_pages(text_path.read_text(encoding="utf-8"))
    chapter_starts = locate_chapter_starts(pages)
    appendices = locate_appendices(pages, chapter_starts[7])
    max_page = max(pages)
    result: list[ChapterSource] = []

    for number, title_en, title_vi in CHAPTERS:
        starts = locate_lesson_starts(pages, number, chapter_starts[number])
        next_chapter = chapter_starts.get(number + 1)
        chapter_end = (next_chapter - 1) if next_chapter else appendices[0] - 1
        lesson_meta = LESSON_TITLES[number]
        lessons: list[LessonSource] = []
        for index, start in enumerate(starts):
            end = starts[index + 1] - 1 if index + 1 < len(starts) else chapter_end
            body = extract_range(pages, start, end)
            if len(body) < 100:
                raise ValueError(f"Section {number}.{index + 1} is unexpectedly short")
            lesson_title_en, lesson_title_vi, lesson_type = lesson_meta[index]
            lessons.append(
                LessonSource(
                    lesson_title_en,
                    lesson_title_vi,
                    lesson_type,
                    body,
                    list(range(start, end + 1)),
                )
            )

        if number == 7:
            appendix_ranges = [
                (appendices[0], appendices[1] - 1),
                (appendices[1], max_page),
            ]
            for meta, (start, end) in zip(lesson_meta[5:], appendix_ranges):
                body = extract_range(pages, start, end)
                if len(body) < 100:
                    raise ValueError(f"Appendix at PDF page {start} is unexpectedly short")
                lessons.append(
                    LessonSource(meta[0], meta[1], meta[2], body, list(range(start, end + 1)))
                )

        result.append(
            ChapterSource(
                number,
                title_en,
                title_vi,
                lessons,
                list(range(chapter_starts[number], chapter_end + 1)),
            )
        )
    return result


class ApiClient:
    token: str | None = None

    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url.rstrip("/")
        login = self.request(
            "POST",
            "/api/auth/token",
            {"username": username, "password": password},
            authenticated=False,
        )
        self.token = login["result"]["token"]
        self.user_id = int(login["result"]["userId"])

    def request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None = None,
        *,
        authenticated: bool = True,
    ) -> Any:
        data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
        if authenticated and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(
            f"{self.base_url}{path}", data=data, headers=headers, method=method
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                response_body = response.read()
                return json.loads(response_body.decode("utf-8")) if response_body else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} failed ({error.code}): {detail}") from error


def attribution() -> str:
    return (
        f"Adapted from {BOOK_TITLE} by {BOOK_AUTHOR} ({BOOK_YEAR}), {LICENSE_NAME}. "
        f"Source: {BOOK_URL} License: {LICENSE_URL}"
    )


def attribution_vi() -> str:
    return (
        f"Biên soạn từ {BOOK_TITLE} của {BOOK_AUTHOR} ({BOOK_YEAR}), theo giấy phép "
        f"{LICENSE_NAME}. Nguồn: {BOOK_URL} Giấy phép: {LICENSE_URL}"
    )


def slug_part(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def course_payload(chapter: ChapterSource) -> dict[str, Any]:
    intro = chapter.lessons[0].body
    objective_lines = [
        line.lstrip("•◦- ").strip()
        for line in intro.splitlines()
        if line.strip().startswith(("•", "◦", "-"))
    ]
    return {
        "code": f"MSU-ADV-VI-CH{chapter.number:02d}",
        "slug": f"msu-advanced-vietnamese-chapter-{chapter.number:02d}",
        "titleEn": f"Advanced Vietnamese - Chapter {chapter.number}: {chapter.title_en}",
        "titleVi": f"Tiếng Việt nâng cao - Chương {chapter.number}: {chapter.title_vi}",
        "descriptionEn": (
            f"A structured draft course based on Chapter {chapter.number}, “{chapter.title_en}”. "
            f"It preserves the source order as {len(chapter.lessons)} lessons. {attribution()}"
        ),
        "descriptionVi": (
            f"Khóa học nháp có cấu trúc dựa trên Chương {chapter.number}, “{chapter.title_vi}”. "
            f"Nội dung được giữ đúng thứ tự nguồn trong {len(chapter.lessons)} bài học. "
            f"{attribution_vi()}"
        ),
        "level": "B1" if chapter.number <= 3 else "B2",
        "thumbnailUrl": "",
        "estimatedMinutes": len(chapter.lessons) * 35,
        "learningOutcomes": "\n".join(objective_lines[:12]) or intro[:1800],
        "isFree": True,
    }


def create_chapter(api: ApiClient, chapter: ChapterSource) -> dict[str, Any]:
    course = api.request("POST", "/api/admin/courses", course_payload(chapter))
    course_id = int(course["id"])
    module = api.request(
        "POST",
        f"/api/admin/courses/{course_id}/modules",
        {
            "titleEn": f"Chapter {chapter.number}: {chapter.title_en}",
            "titleVi": f"Chương {chapter.number}: {chapter.title_vi}",
            "descriptionEn": "Source-ordered lessons for reading, listening, speaking, and writing.",
            "descriptionVi": "Bài học theo đúng thứ tự nguồn, phát triển kỹ năng đọc, nghe, nói và viết.",
        },
    )
    module_id = int(module["id"])
    lesson_ids: list[int] = []
    activity_ids: list[int] = []
    previous: int | None = None

    for sequence, lesson_source in enumerate(chapter.lessons, start=1):
        start_page, end_page = min(lesson_source.pages), max(lesson_source.pages)
        page_label = f"PDF pages {start_page}-{end_page}" if start_page != end_page else f"PDF page {start_page}"
        lesson = api.request(
            "POST",
            f"/api/admin/modules/{module_id}/lessons",
            {
                "code": f"MSU-ADV-CH{chapter.number:02d}-L{sequence:02d}",
                "slug": f"msu-advanced-ch{chapter.number:02d}-{sequence:02d}-{slug_part(lesson_source.title_en)}",
                "titleEn": lesson_source.title_en,
                "titleVi": lesson_source.title_vi,
                "descriptionEn": f"{lesson_source.title_en}. Source location: {page_label}.",
                "descriptionVi": f"{lesson_source.title_vi}. Vị trí nguồn: {page_label}.",
                "lessonType": lesson_source.lesson_type,
                "estimatedMinutes": 35,
                "prerequisiteIds": [previous] if previous else [],
                "learningObjectiveEn": f"Complete the {lesson_source.title_en.lower()} tasks and reflect on the topic.",
                "learningObjectiveVi": f"Hoàn thành nội dung {lesson_source.title_vi.lower()} và vận dụng vào chủ đề.",
            },
        )
        lesson_id = int(lesson["id"])
        previous = lesson_id
        lesson_ids.append(lesson_id)
        body = (
            f"# {lesson_source.title_en}\n\n{lesson_source.body}\n\n---\n\n"
            f"**Attribution:** {attribution()}"
        )
        activity = api.request(
            "POST",
            f"/api/admin/lessons/{lesson_id}/activities",
            {
                "activityType": "TEXT",
                "titleEn": lesson_source.title_en,
                "titleVi": lesson_source.title_vi,
                "instructionEn": "Study the source-based lesson. Add or adapt multimedia and graded exercises during editorial review.",
                "instructionVi": "Học nội dung theo nguồn; bổ sung hoặc điều chỉnh đa phương tiện và bài tập chấm điểm khi biên tập.",
                "contentJson": {
                    "body": body,
                    "sourcePage": start_page,
                    "captionEn": f"Source: {BOOK_TITLE}, Chapter {chapter.number}, {page_label}.",
                    "captionVi": f"Nguồn: {BOOK_TITLE}, Chương {chapter.number}, {page_label}.",
                },
                "isRequired": True,
                "maxScore": 0,
                "status": "DRAFT",
                "quizId": None,
                "grammarTopicIds": [],
            },
        )
        activity_ids.append(int(activity["id"]))

    return {
        "courseId": course_id,
        "code": course["code"],
        "slug": course["slug"],
        "titleVi": course["titleVi"],
        "titleEn": course["titleEn"],
        "status": course["status"],
        "moduleId": module_id,
        "lessonIds": lesson_ids,
        "activityIds": activity_ids,
        "lessonCount": len(lesson_ids),
        "activityCount": len(activity_ids),
    }


def report_for(chapters: list[ChapterSource]) -> dict[str, Any]:
    return {
        "book": BOOK_TITLE,
        "source": BOOK_URL,
        "license": LICENSE_URL,
        "chapterCount": len(chapters),
        "lessonCount": sum(len(chapter.lessons) for chapter in chapters),
        "chapters": [
            {
                "number": chapter.number,
                "titleEn": chapter.title_en,
                "titleVi": chapter.title_vi,
                "pdfPages": [min(chapter.pages), max(chapter.pages)],
                "lessons": [
                    {
                        "titleEn": lesson.title_en,
                        "titleVi": lesson.title_vi,
                        "lessonType": lesson.lesson_type,
                        "characters": len(lesson.body),
                        "pdfPages": [min(lesson.pages), max(lesson.pages)],
                    }
                    for lesson in chapter.lessons
                ],
            }
            for chapter in chapters
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--username", default=os.environ.get("MSU_CM_USERNAME", "contentmanager"))
    parser.add_argument("--password", default=os.environ.get("MSU_CM_PASSWORD"))
    parser.add_argument("--import", dest="do_import", action="store_true")
    args = parser.parse_args()

    chapters = parse_source(args.text)
    report = report_for(chapters)
    if args.do_import:
        if not args.password:
            raise ValueError("Set MSU_CM_PASSWORD before importing")
        api = ApiClient(args.api, args.username, args.password)
        report["ownerId"] = api.user_id
        report["importedAt"] = datetime.now(timezone.utc).isoformat()
        report["createdCourses"] = []
        for chapter in chapters:
            created = create_chapter(api, chapter)
            report["createdCourses"].append(created)
            print(
                f"Created chapter {chapter.number:02d}: course {created['courseId']} "
                f"with {created['lessonCount']} lessons",
                flush=True,
            )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output),
                "chapterCount": report["chapterCount"],
                "lessonCount": report["lessonCount"],
                "importedCourseCount": len(report.get("createdCourses", [])),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
