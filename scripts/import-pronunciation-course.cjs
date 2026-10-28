// Import Pronunciation Course (VI-PRON-A0) into the database
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const source = require('../src/main/resources/learning/pronunciation-course.json');
const base = process.env.DEMO_API_URL || 'http://127.0.0.1:8080';
const demoPassword = process.env.APP_DEMO_PASSWORD;
assert.ok(demoPassword && demoPassword.length >= 12, 'APP_DEMO_PASSWORD must contain at least 12 characters');
const code = source.course.code;

async function api(route, token, method = 'GET', body) {
  const r = await fetch(base + route, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(45000)
  });
  const raw = await r.text();
  let data;
  try {
    data = raw ? JSON.parse(raw) : null;
  } catch {
    throw Error(`${route}: invalid HTTP ${r.status} response: ${raw}`);
  }
  assert.ok(r.ok && (!data || typeof data.code !== 'number' || data.code === 1000), `${route}: HTTP ${r.status}: ${data?.message || 'rejected'}`);
  return data?.result ?? data;
}

async function login(username, password) {
  const r = await api('/api/auth/token', null, 'POST', { username, password });
  return r.token;
}

async function list(route, token) {
  const result = [];
  for (let page = 0; ; page++) {
    const r = await api(`${route}${route.includes('?') ? '&' : '?'}size=100&page=${page}`, token);
    result.push(...r.content);
    if (page + 1 >= r.totalPages || !r.content.length) return result;
  }
}

async function main() {
  console.log(`Starting import for course ${code}...`);
  const manager = await login('contentmanager', demoPassword);
  const admin = await login('admin', demoPassword);

  const existingCourses = await list('/api/admin/courses', manager);
  let course = existingCourses.find(c => c.code === code);
  if (!course) {
    course = await api('/api/admin/courses', manager, 'POST', source.course);
    console.log(`Created course: ${course.id} (${course.code})`);
  } else {
    course = await api(`/api/admin/courses/${course.id}`, manager);
    console.log(`Using existing course: ${course.id} (${course.code})`);
  }

  const existingQuizzes = await list(`/api/admin/quizzes?courseId=${course.id}`, manager);

  let lessonIndex = 0;
  for (let mIdx = 0; mIdx < source.modules.length; mIdx++) {
    const mData = source.modules[mIdx];
    let module = (course.modules || []).find(m => m.title === mData.metadata.titleEn || m.titleVi === mData.metadata.titleVi);
    if (!module) {
      module = await api(`/api/admin/courses/${course.id}/modules`, manager, 'POST', mData.metadata);
      console.log(`Created module ${mIdx + 1}: ${module.titleVi || module.title}`);
      if (!course.modules) course.modules = [];
      course.modules.push(module);
    }

    let lastLessonId = null;
    for (const lData of mData.lessons) {
      lessonIndex++;
      const lessonCode = `${code}-L${String(lessonIndex).padStart(2, '0')}`;
      const slug = `pronunciation-a0-${lessonIndex}`;

      let lesson = (module.lessons || []).find(l => l.code === lessonCode);
      if (!lesson) {
        lesson = await api(`/api/admin/modules/${module.id}/lessons`, manager, 'POST', {
          code: lessonCode,
          slug,
          titleEn: lData.titleEn,
          titleVi: lData.titleVi,
          descriptionEn: lData.descriptionEn,
          descriptionVi: lData.descriptionVi,
          lessonType: lData.lessonType || 'VOCABULARY',
          estimatedMinutes: lData.estimatedMinutes || 18,
          prerequisiteIds: lastLessonId ? [lastLessonId] : [],
          learningObjectiveEn: lData.objectiveEn,
          learningObjectiveVi: lData.objectiveVi
        });
        if (!module.lessons) module.lessons = [];
        module.lessons.push(lesson);
      }
      lastLessonId = lesson.id;

      const preview = await api(`/api/admin/lessons/${lesson.id}/preview`, manager);
      const knownActs = preview.activities || [];

      // Add activities
      for (const act of (lData.activities || [])) {
        if (!knownActs.some(a => a.activityType === act.activityType && a.titleVi === act.titleVi)) {
          await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
            activityType: act.activityType || 'TEXT',
            titleVi: act.titleVi,
            titleEn: act.titleEn,
            instructionVi: act.instructionVi || 'Đọc và luyện tập theo hướng dẫn.',
            instructionEn: act.instructionEn || 'Read and practise as instructed.',
            contentJson: act.contentJson || {},
            isRequired: act.isRequired !== false,
            maxScore: act.maxScore || 0,
            status: 'PUBLISHED',
            grammarTopicIds: []
          });
        }
      }

      // Quiz if questions exist
      if (lData.questions && lData.questions.length > 0) {
        let quiz = existingQuizzes.find(q => Number(q.lessonId) === Number(lesson.id));
        if (!quiz) {
          quiz = await api('/api/admin/quizzes', manager, 'POST', {
            titleEn: `Quiz: ${lData.titleEn}`,
            titleVi: `Kiểm tra: ${lData.titleVi}`,
            descriptionVi: 'Đạt từ 70% để vượt qua bài kiểm tra.',
            descriptionEn: 'Score at least 70% to pass this quiz.',
            quizType: 'LESSON',
            courseId: course.id,
            lessonId: lesson.id,
            passingScore: 70,
            randomizeQuestions: false
          });
          existingQuizzes.push(quiz);
        }

        const quizDetail = await api(`/api/admin/quizzes/${quiz.id}`, manager);
        const existingQIds = new Set((quizDetail.questions || []).map(q => Number(q.questionId)));

        for (const qData of lData.questions) {
          const createdQ = await api('/api/admin/questions', manager, 'POST', {
            questionType: qData.questionType || 'MULTIPLE_CHOICE',
            prompt: qData.promptEn || qData.promptVi || 'Câu hỏi',
            promptVi: qData.promptVi,
            explanation: qData.explanationEn,
            explanationVi: qData.explanationVi,
            difficulty: qData.difficulty || 'EASY',
            status: 'PUBLISHED',
            topic: `PRON_L${lessonIndex}`,
            options: (qData.options || []).map((o, idx) => ({
              id: o.id || String(idx + 1),
              text: o.textEn || o.textVi || '',
              textVi: o.textVi || '',
              side: o.side || null
            })),
            correctAnswerJson: qData.correctAnswerJson || {}
          });

          if (!existingQIds.has(Number(createdQ.id))) {
            await api(`/api/admin/quizzes/${quiz.id}/questions`, manager, 'POST', {
              questionId: createdQ.id,
              points: 1
            });
            existingQIds.add(Number(createdQ.id));
          }
        }

        await api(`/api/admin/quizzes/${quiz.id}/status`, manager, 'PATCH', { status: 'PUBLISHED' });

        if (!knownActs.some(a => a.activityType === 'QUIZ' && Number(a.quizId) === Number(quiz.id))) {
          await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
            activityType: 'QUIZ',
            titleVi: 'Kiểm tra phát âm',
            titleEn: 'Pronunciation Quiz',
            instructionVi: 'Hoàn thành quiz để mở khóa bài tiếp theo.',
            instructionEn: 'Complete the quiz to unlock the next lesson.',
            contentJson: {},
            isRequired: true,
            maxScore: 10,
            status: 'PUBLISHED',
            quizId: quiz.id,
            grammarTopicIds: []
          });
        }
      }

      await api(`/api/admin/lessons/${lesson.id}/status`, manager, 'PATCH', { status: 'PUBLISHED' });
    }
  }

  // Publish course via direct MySQL update
  console.log(`Course ${course.code} (ID: ${course.id}) seeded with ${lessonIndex} lessons!`);
}

main().catch(err => {
  console.error('Import failed:', err);
  process.exit(1);
});
