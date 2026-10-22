#!/usr/bin/env python3
"""Create one draft HolaVietnamese course for each numbered chapter in Basic Vietnamese.

The source book is Tung Hoang's *Basic Vietnamese* (2023), licensed CC BY-NC 4.0.
This importer uses the text extracted from the official Pressbooks PDF and preserves
the chapter's section order as lessons. Imported activities remain drafts so a content
manager can review formatting and add media before submission.
"""

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


BOOK_TITLE = "Basic Vietnamese"
BOOK_AUTHOR = "Tung Hoang"
BOOK_YEAR = "2023"
BOOK_URL = "https://openbooks.lib.msu.edu/vietnamese/"
LICENSE_NAME = "Creative Commons Attribution-NonCommercial 4.0 International"
LICENSE_URL = "https://creativecommons.org/licenses/by-nc/4.0/"

CHAPTERS = [
    (1, "Greetings and Introduction", "Chào hỏi và giới thiệu"),
    (2, "Countries and Languages", "Quốc gia và ngôn ngữ"),
    (3, "Numbers and Counting", "Số và cách đếm"),
    (4, "Telling the Time", "Xem giờ"),
    (5, "Days and Dates", "Ngày và tháng"),
    (6, "Family", "Gia đình"),
    (7, "Accommodation", "Chỗ ở"),
    (8, "Weather", "Thời tiết"),
    (9, "School and Study", "Trường học và học tập"),
    (10, "Occupations", "Nghề nghiệp"),
    (11, "Colors and Descriptions", "Màu sắc và miêu tả"),
    (12, "Food and Drinks", "Đồ ăn và thức uống"),
    (13, "Shopping", "Mua sắm"),
    (14, "Traffic and Transport", "Giao thông và phương tiện"),
    (15, "News and the Media", "Tin tức và truyền thông"),
]

SECTION_META = {
    "LEARNING OBJECTIVES": ("Learning Objectives", "Mục tiêu học tập", "NORMAL"),
    "CONVERSATION": ("Conversation", "Hội thoại", "CONVERSATION"),
    "CONVERSATION 1": ("Conversation 1", "Hội thoại 1", "CONVERSATION"),
    "CONVERSATION 2": ("Conversation 2", "Hội thoại 2", "CONVERSATION"),
    "VOCABULARY": ("Vocabulary", "Từ vựng", "VOCABULARY"),
    "GRAMMAR": ("Grammar", "Ngữ pháp", "GRAMMAR"),
    "CULTURE NOTES": ("Culture Notes", "Ghi chú văn hóa", "CULTURE"),
    "CULTURE NOTES (FOR REFERENCE)": (
        "Culture Notes (Reference)",
        "Ghi chú văn hóa (tham khảo)",
        "CULTURE",
    ),
    "FURTHER PRACTICE": ("Further Practice", "Luyện tập mở rộng", "REVIEW"),
}


@dataclass
class Section:
    key: str
    title_en: str
    title_vi: str
    lesson_type: str
    body: str
    source_pages: list[int]


@dataclass
class Chapter:
    number: int
    title_en: str
    title_vi: str
    sections: list[Section]
    source_pages: list[int]


def split_pdf_pages(text: str) -> dict[int, str]:
    parts = re.split(r"===== PDF PAGE (\d+) =====", text)
    return {int(parts[index]): parts[index + 1] for index in range(1, len(parts), 2)}


def chapter_starts(pages: dict[int, str]) -> dict[int, int]:
    starts: dict[int, int] = {}
    for number, _, _ in CHAPTERS:
        pattern = re.compile(rf"(?m)^CHAPTER {number}:")
        matches = [page for page, content in pages.items() if pattern.search(content)]
        if not matches:
            raise ValueError(f"Cannot locate chapter {number} in extracted PDF text")
        starts[number] = min(matches)
    return starts


def clean_line(line: str) -> str | None:
    value = re.sub(r"\s+", " ", line.strip())
    if not value:
        return ""
    if re.match(r"^\d+\s*\|\s*CHAPTER \d+:", value, re.I):
        return None
    if re.match(r"^CHAPTER \d+:.*\|\s*\d+$", value, re.I):
        return None
    if re.match(r"^CHAPTER \d+:", value, re.I):
        return None
    if re.match(r"^PART [IVXLCDM]+$", value, re.I):
        return None
    if re.match(
        r"^(LEARNING OBJECTIVES|CONVERSATION(?: [12])?|VOCABULARY|GRAMMAR|"
        r"CULTURE NOTES(?: \(FOR REFERENCE\))?|FURTHER PRACTICE)\s*\|\s*\d+$",
        value,
        re.I,
    ):
        return None
    return value


def tidy_body(lines: list[str]) -> str:
    output: list[str] = []
    blank = False
    for line in lines:
        value = line.strip()
        if not value:
            if output and not blank:
                output.append("")
            blank = True
            continue
        blank = False
        # Repair URLs that Pressbooks wraps immediately after the domain slash.
        if output and output[-1].startswith("https://") and output[-1].endswith("/"):
            output[-1] += value
            continue
        output.append(value)
    while output and not output[-1]:
        output.pop()
    body = "\n".join(output)
    body = re.sub(r"\n{3,}", "\n\n", body)
    return body.strip()


def parse_chapters(text_path: Path) -> list[Chapter]:
    pages = split_pdf_pages(text_path.read_text(encoding="utf-8"))
    starts = chapter_starts(pages)
    result: list[Chapter] = []
    max_page = max(pages)

    for index, (number, title_en, title_vi) in enumerate(CHAPTERS):
        start = starts[number]
        end = starts[number + 1] - 1 if number < len(CHAPTERS) else max_page
        section_buffers: list[dict[str, Any]] = []
        current: dict[str, Any] | None = None

        for page_number in range(start, end + 1):
            for raw_line in pages.get(page_number, "").splitlines():
                line = clean_line(raw_line)
                if line is None:
                    continue
                normalized = line.upper() if line else ""
                if normalized in SECTION_META:
                    if current is None or current["key"] != normalized:
                        current = {"key": normalized, "lines": [], "pages": []}
                        section_buffers.append(current)
                    if page_number not in current["pages"]:
                        current["pages"].append(page_number)
                    continue
                if current is not None:
                    current["lines"].append(line)
                    if line and page_number not in current["pages"]:
                        current["pages"].append(page_number)

        sections: list[Section] = []
        for buffer in section_buffers:
            body = tidy_body(buffer["lines"])
            if not body:
                continue
            section_title_en, section_title_vi, lesson_type = SECTION_META[buffer["key"]]
            sections.append(
                Section(
                    key=buffer["key"],
                    title_en=section_title_en,
                    title_vi=section_title_vi,
                    lesson_type=lesson_type,
                    body=body,
                    source_pages=buffer["pages"],
                )
            )
        if len(sections) < 5:
            raise ValueError(
                f"Chapter {number} extraction produced only {len(sections)} sections"
            )
        result.append(
            Chapter(
                number=number,
                title_en=title_en,
                title_vi=title_vi,
                sections=sections,
                source_pages=list(range(start, end + 1)),
            )
        )
    return result


class ApiClient:
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

    token: str | None = None
    user_id: int

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
                body = response.read()
                return json.loads(body.decode("utf-8")) if body else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} failed ({error.code}): {detail}") from error


def attribution() -> str:
    return (
        f"Adapted from {BOOK_TITLE} by {BOOK_AUTHOR} ({BOOK_YEAR}), {LICENSE_NAME}. "
        f"Source: {BOOK_URL} License: {LICENSE_URL}"
    )


def vietnamese_attribution() -> str:
    return (
        f"Biên soạn từ {BOOK_TITLE} của {BOOK_AUTHOR} ({BOOK_YEAR}), theo giấy phép "
        f"{LICENSE_NAME}. Nguồn: {BOOK_URL} Giấy phép: {LICENSE_URL}"
    )


def lesson_slug(chapter: int, section: Section, sequence: int) -> str:
    key = re.sub(r"[^a-z0-9]+", "-", section.title_en.lower()).strip("-")
    return f"msu-basic-vietnamese-ch{chapter:02d}-{sequence:02d}-{key}"


def course_payload(chapter: Chapter) -> dict[str, Any]:
    objectives = next(
        (section.body for section in chapter.sections if section.key == "LEARNING OBJECTIVES"),
        "Complete the chapter's conversation, vocabulary, grammar, culture, and practice sections.",
    )
    objective_lines = [
        line.lstrip("•- ").strip()
        for line in objectives.splitlines()
        if line.strip().startswith(("•", "-"))
    ]
    learning_outcomes = "\n".join(objective_lines[:8]) or objectives[:1500]
    return {
        "code": f"MSU-BV-CH{chapter.number:02d}",
        "slug": f"msu-basic-vietnamese-chapter-{chapter.number:02d}",
        "titleEn": f"Basic Vietnamese - Chapter {chapter.number}: {chapter.title_en}",
        "titleVi": f"Tiếng Việt cơ bản - Chương {chapter.number}: {chapter.title_vi}",
        "descriptionEn": (
            f"A structured draft course based on Chapter {chapter.number}, “{chapter.title_en}”. "
            f"It preserves the source sequence as {len(chapter.sections)} lessons. {attribution()}"
        ),
        "descriptionVi": (
            f"Khóa học nháp có cấu trúc dựa trên Chương {chapter.number}, “{chapter.title_vi}”. "
            f"Nội dung được giữ đúng thứ tự nguồn trong {len(chapter.sections)} bài học. "
            f"{vietnamese_attribution()}"
        ),
        "level": "A1" if chapter.number <= 8 else "A2",
        "thumbnailUrl": "",
        "estimatedMinutes": len(chapter.sections) * 20,
        "learningOutcomes": learning_outcomes,
        "isFree": True,
    }


def create_chapter(api: ApiClient, chapter: Chapter) -> dict[str, Any]:
    course = api.request("POST", "/api/admin/courses", course_payload(chapter))
    course_id = int(course["id"])
    module = api.request(
        "POST",
        f"/api/admin/courses/{course_id}/modules",
        {
            "titleEn": f"Chapter {chapter.number}: {chapter.title_en}",
            "titleVi": f"Chương {chapter.number}: {chapter.title_vi}",
            "descriptionEn": f"Source-ordered lessons from Chapter {chapter.number} of {BOOK_TITLE}.",
            "descriptionVi": f"Các bài học theo đúng thứ tự Chương {chapter.number} của {BOOK_TITLE}.",
        },
    )
    module_id = int(module["id"])
    lesson_ids: list[int] = []
    activity_ids: list[int] = []
    previous_lesson_id: int | None = None

    for sequence, section in enumerate(chapter.sections, start=1):
        source_range = (
            f"PDF pages {min(section.source_pages)}-{max(section.source_pages)}"
            if len(section.source_pages) > 1
            else f"PDF page {section.source_pages[0]}"
        )
        lesson = api.request(
            "POST",
            f"/api/admin/modules/{module_id}/lessons",
            {
                "code": f"MSU-BV-CH{chapter.number:02d}-L{sequence:02d}",
                "slug": lesson_slug(chapter.number, section, sequence),
                "titleEn": section.title_en,
                "titleVi": section.title_vi,
                "descriptionEn": (
                    f"{section.title_en} from Chapter {chapter.number}: {chapter.title_en}. "
                    f"Source location: {source_range}."
                ),
                "descriptionVi": (
                    f"{section.title_vi} thuộc Chương {chapter.number}: {chapter.title_vi}. "
                    f"Vị trí nguồn: {source_range}."
                ),
                "lessonType": section.lesson_type,
                "estimatedMinutes": 20,
                "prerequisiteIds": [previous_lesson_id] if previous_lesson_id else [],
                "learningObjectiveEn": f"Study and practise the chapter's {section.title_en.lower()} content.",
                "learningObjectiveVi": f"Học và luyện tập nội dung {section.title_vi.lower()} của chương.",
            },
        )
        lesson_id = int(lesson["id"])
        lesson_ids.append(lesson_id)
        previous_lesson_id = lesson_id
        body = (
            f"# {section.title_en}\n\n{section.body}\n\n---\n\n"
            f"**Attribution:** {attribution()}"
        )
        activity = api.request(
            "POST",
            f"/api/admin/lessons/{lesson_id}/activities",
            {
                "activityType": "TEXT",
                "titleEn": section.title_en,
                "titleVi": section.title_vi,
                "instructionEn": "Read the source-based lesson, then add or adapt media and exercises during editorial review.",
                "instructionVi": "Đọc bài học theo nguồn, sau đó bổ sung hoặc điều chỉnh media và bài tập khi biên tập.",
                "contentJson": {
                    "body": body,
                    "sourcePage": min(section.source_pages),
                    "captionEn": f"Source: {BOOK_TITLE}, Chapter {chapter.number}, {source_range}.",
                    "captionVi": f"Nguồn: {BOOK_TITLE}, Chương {chapter.number}, {source_range}.",
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


def extraction_summary(chapters: list[Chapter]) -> dict[str, Any]:
    return {
        "book": BOOK_TITLE,
        "source": BOOK_URL,
        "license": LICENSE_URL,
        "chapterCount": len(chapters),
        "lessonCount": sum(len(chapter.sections) for chapter in chapters),
        "chapters": [
            {
                "number": chapter.number,
                "titleEn": chapter.title_en,
                "titleVi": chapter.title_vi,
                "pdfPages": [min(chapter.source_pages), max(chapter.source_pages)],
                "sections": [
                    {
                        "titleEn": section.title_en,
                        "titleVi": section.title_vi,
                        "lessonType": section.lesson_type,
                        "characters": len(section.body),
                        "pdfPages": [min(section.source_pages), max(section.source_pages)],
                    }
                    for section in chapter.sections
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

    chapters = parse_chapters(args.text)
    report = extraction_summary(chapters)
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
    print(json.dumps({
        "output": str(args.output),
        "chapterCount": report["chapterCount"],
        "lessonCount": report["lessonCount"],
        "importedCourseCount": len(report.get("createdCourses", [])),
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
