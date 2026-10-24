// Import A1 Course (HOLA-A1-MVP) into the database
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const source = require('../src/main/resources/learning/a1-course.json');
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
  assert.ok(r.token, 'Login failed');
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

    const checkpointQuestions = [];
    let lastLessonId = null;

    for (const lData of mData.lessons) {
      lessonIndex++;
      const lessonCode = `${code}-L${lessonIndex}`;
      const slug = `hola-a1-${lessonIndex}`;

      let lesson = (module.lessons || []).find(l => l.code === lessonCode);
      if (!lesson) {
        lesson = await api(`/api/admin/modules/${module.id}/lessons`, manager, 'POST', {
          code: lessonCode,
          slug,
          titleEn: lData.titleEn,
          titleVi: lData.titleVi,
          descriptionEn: lData.descriptionEn,
          descriptionVi: lData.descriptionVi,
          lessonType: 'NORMAL',
          estimatedMinutes: 20,
          prerequisiteIds: lastLessonId ? [lastLessonId] : [],
          learningObjectiveEn: lData.objectiveEn,
          learningObjectiveVi: lData.objectiveVi
        });
        console.log(`  Created lesson ${lessonIndex}: ${lesson.titleVi || lesson.title}`);
        if (!module.lessons) module.lessons = [];
        module.lessons.push(lesson);
      }
      lastLessonId = lesson.id;

      const preview = await api(`/api/admin/lessons/${lesson.id}/preview`, manager);
      const knownActs = preview.activities || [];

      // Activity 1: Introduction (TEXT)
      if (!knownActs.some(a => a.activityType === 'TEXT' && a.titleVi === 'Giới thiệu')) {
        await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
          activityType: 'TEXT',
          titleVi: 'Giới thiệu',
          titleEn: 'Introduction',
          instructionVi: 'Đọc phần giới thiệu trước khi bắt đầu.',
          instructionEn: 'Read the introduction before starting.',
          contentJson: lData.introduction || { body: lData.descriptionVi || '' },
          isRequired: true,
          maxScore: 0,
          status: 'PUBLISHED',
          grammarTopicIds: []
        });
      }

      // Activity 2: Vocabulary (VOCABULARY)
      if (lData.vocabulary && lData.vocabulary.length > 0 && !knownActs.some(a => a.activityType === 'VOCABULARY')) {
        await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
          activityType: 'VOCABULARY',
          titleVi: 'Từ vựng trọng tâm',
          titleEn: 'Key Vocabulary',
          instructionVi: 'Nghe phát âm và ghi nhớ từ vựng.',
          instructionEn: 'Listen to pronunciation and learn vocabulary.',
          contentJson: { items: lData.vocabulary },
          isRequired: true,
          maxScore: 0,
          status: 'PUBLISHED',
          grammarTopicIds: []
        });
      }

      // Activity 3: Dialogue (DIALOGUE)
      if (lData.dialogue && lData.dialogue.length > 0 && !knownActs.some(a => a.activityType === 'DIALOGUE')) {
        await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
          activityType: 'DIALOGUE',
          titleVi: 'Hội thoại thực tế',
          titleEn: 'Everyday Dialogue',
          instructionVi: 'Luyện nghe và đọc theo đoạn hội thoại.',
          instructionEn: 'Listen and follow along with the dialogue.',
          contentJson: { lines: lData.dialogue },
          isRequired: true,
          maxScore: 0,
          status: 'PUBLISHED',
          grammarTopicIds: []
        });
      }

      // Activity 4: Practice (PRACTICE)
      if (lData.practice && !knownActs.some(a => a.activityType === 'PRACTICE')) {
        await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
          activityType: 'PRACTICE',
          titleVi: 'Luyện tập tình huống',
          titleEn: 'Situational Practice',
          instructionVi: 'Áp dụng kiến thức vừa học vào bài tập.',
          instructionEn: 'Apply your knowledge to exercises.',
          contentJson: lData.practice,
          isRequired: true,
          maxScore: 10,
          status: 'PUBLISHED',
          grammarTopicIds: []
        });
      }

      // Questions & Quiz
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

      if (lData.questions && lData.questions.length > 0) {
        for (let qIdx = 0; qIdx < lData.questions.length; qIdx++) {
          const qData = lData.questions[qIdx];
          const createdQ = await api('/api/admin/questions', manager, 'POST', {
            questionType: qData.questionType || 'MULTIPLE_CHOICE',
            prompt: qData.promptEn || qData.promptVi || 'Câu hỏi',
            promptVi: qData.promptVi,
            explanation: qData.explanationEn,
            explanationVi: qData.explanationVi,
            difficulty: qData.difficulty || 'EASY',
            status: 'PUBLISHED',
            topic: `A1_L${lessonIndex}`,
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

          if (qIdx === 0 || qIdx === 2) {
            checkpointQuestions.push(createdQ.id);
          }
        }
      }

      // Publish quiz
      await api(`/api/admin/quizzes/${quiz.id}/status`, manager, 'PATCH', { status: 'PUBLISHED' });

      // Activity: Quiz Activity
      if (!knownActs.some(a => a.activityType === 'QUIZ' && Number(a.quizId) === Number(quiz.id))) {
        await api(`/api/admin/lessons/${lesson.id}/activities`, manager, 'POST', {
          activityType: 'QUIZ',
          titleVi: 'Kiểm tra cuối bài',
          titleEn: 'Lesson Quiz',
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

      // Publish lesson
      await api(`/api/admin/lessons/${lesson.id}/status`, manager, 'PATCH', { status: 'PUBLISHED' });
    }
  }

  // Publish course
  await api(`/api/admin/courses/${course.id}/status`, admin, 'PATCH', { status: 'PUBLISHED' });
  console.log(`Course ${course.code} (ID: ${course.id}) PUBLISHED successfully!`);
}

main().catch(err => {
  console.error('Import failed:', err);
  process.exit(1);
});
