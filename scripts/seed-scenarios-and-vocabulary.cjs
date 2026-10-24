// Seed AI Scenarios and Master Vocabularies into the database
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.DEMO_API_URL || 'http://127.0.0.1:8080';
const demoPassword = process.env.APP_DEMO_PASSWORD;
if (!demoPassword || demoPassword.length < 12) {
  throw Error('APP_DEMO_PASSWORD must contain at least 12 characters');
}

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
  if (!r.ok || (data && typeof data.code === 'number' && data.code !== 1000)) {
    throw Error(`${route}: HTTP ${r.status}: ${data?.message || 'rejected'}`);
  }
  return data?.result ?? data;
}

async function login(username, password) {
  const r = await api('/api/auth/token', null, 'POST', { username, password });
  return r.token;
}

async function seedScenarios(token) {
  console.log('--- Seeding AI Scenarios ---');
  // Load scenarios from frontend definition
  const feContent = fs.readFileSync(path.resolve(__dirname, '../../holavietnamese_fe/src/services/aiRoleplayService.js'), 'utf8');
  const scIdx = feContent.indexOf('export const SCENARIOS = [');
  const endIdx = feContent.indexOf('export const aiRoleplayService');
  if (scIdx === -1 || endIdx === -1) {
    throw new Error('Could not parse SCENARIOS from aiRoleplayService.js');
  }

  const rawArray = feContent.slice(scIdx + 'export const SCENARIOS ='.length, endIdx).trim().replace(/;$/, '');
  const fn = new Function(`return ${rawArray};`);
  const scenarios = fn();

  console.log(`Found ${scenarios.length} scenarios to seed into database.`);

  for (const sc of scenarios) {
    const payload = {
      id: sc.id,
      title: sc.title,
      level: sc.level || 'A1',
      topic: sc.topic || 'dining',
      destination: sc.destination || 'Hà Nội',
      difficulty: sc.difficulty || 'Dễ',
      duration: sc.duration || '5-10 phút',
      thumbnail: sc.thumbnail,
      coverImage: sc.coverImage,
      overview: sc.overview,
      aiRoleName: sc.aiRole?.name || 'Gia sư AI',
      aiRoleTitle: sc.aiRole?.role || sc.aiRole?.title || '',
      aiRoleAvatar: sc.aiRole?.avatar || '🤖',
      aiRoleTone: sc.aiRole?.tone || 'Thân thiện, tự nhiên',
      aiRoleInitialGreeting: sc.aiRole?.initialGreeting || 'Xin chào bạn!',
      learnerRoleTitle: sc.learnerRole?.role || sc.learnerRole?.title || 'Người học tiếng Việt',
      learnerRoleContext: sc.learnerRole?.context || '',
      objectives: sc.objectives || [],
      vocabularyHints: sc.vocabularyHints || [],
      culturalTip: sc.culturalTip || '',
      sampleHints: sc.sampleHints || [],
      maxTurns: sc.maxTurns || 10,
      status: 'ACTIVE'
    };

    try {
      const res = await api('/api/admin/ai/scenarios', token, 'POST', payload);
      console.log(`  ✓ Saved scenario: ${res.id} - ${res.title}`);
    } catch (err) {
      console.warn(`  ! Scenario ${sc.id} error:`, err.message);
    }
  }
}

async function seedVocabulary(token) {
  console.log('--- Seeding Master Vocabulary ---');
  const a1 = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../src/main/resources/learning/a1-course.json'), 'utf8'));

  const seenWords = new Set();
  const vocabList = [];

  // 1. Gather all words from a1-course.json
  a1.modules.forEach(m => {
    m.lessons.forEach(l => {
      (l.vocabulary || []).forEach(v => {
        const norm = (v.wordVi || '').trim().toLowerCase();
        if (norm && !seenWords.has(norm)) {
          seenWords.add(norm);
          vocabList.push({
            word: v.wordVi.trim(),
            meaningEn: v.meaningEn,
            exampleVi: v.exampleVi,
            exampleEn: v.exampleEn,
            pronunciation: v.pronunciation || null,
            partOfSpeech: v.wordType || 'NOUN',
            cefrLevel: 'A1'
          });
        }
      });
    });
  });

  // 2. Add extra interactive vocabulary items
  const extraWords = [
    { word: 'Phở', pronunciation: '/fəː˧˩˨/', partOfSpeech: 'NOUN', meaningEn: 'Vietnamese Traditional Noodle Soup', exampleVi: 'Buổi sáng tôi thường ăn một bát phở bò.', exampleEn: 'In the morning I usually eat a bowl of beef pho.' },
    { word: 'Cà phê sữa đá', pronunciation: '/kaː˨˩ fe˧˧ sɨə˦ˀ˥ daː˧˥/', partOfSpeech: 'NOUN', meaningEn: 'Iced coffee with condensed milk', exampleVi: 'Một ly cà phê sữa đá mang đi nhé!', exampleEn: 'An iced milk coffee to go please!' },
    { word: 'Cảm ơn', pronunciation: '/kaːm˧˩˨ ʔəːn˧˧/', partOfSpeech: 'INTERJECTION', meaningEn: 'Thank you', exampleVi: 'Cảm ơn bạn rất nhiều!', exampleEn: 'Thank you very much!' },
    { word: 'Xin chào', pronunciation: '/siŋ˧˧ t͡ɕaːw˨˩/', partOfSpeech: 'INTERJECTION', meaningEn: 'Hello / Greetings', exampleVi: 'Xin chào, tôi là học viên mới.', exampleEn: 'Hello, I am a new student.' },
    { word: 'Bánh mì', pronunciation: '/ɓajŋ˧˦ mi˨˩/', partOfSpeech: 'NOUN', meaningEn: 'Vietnamese Baguette Sandwich', exampleVi: 'Cho tôi một ổ bánh mì thịt nguội pate.', exampleEn: 'Give me a cold cut pate baguette please.' },
    { word: 'Bao nhiêu', pronunciation: '/ɓaːw˧˧ ɲiəw˧˧/', partOfSpeech: 'QUESTION_WORD', meaningEn: 'How much / How many', exampleVi: 'Cái áo dài này giá bao nhiêu tiền?', exampleEn: 'How much is this ao dai?' },
    { word: 'Áo dài', pronunciation: '/ʔaːw˧˦ zaːj˨˩/', partOfSpeech: 'NOUN', meaningEn: 'Traditional Vietnamese dress', exampleVi: 'Cô ấy mặc áo dài truyền thống rất duyên dáng.', exampleEn: 'She wears the traditional ao dai very gracefully.' },
    { word: 'Nón lá', pronunciation: '/nɔn˧˦ laː˧˦/', partOfSpeech: 'NOUN', meaningEn: 'Conical leaf hat', exampleVi: 'Nón lá che nắng mưa và là biểu tượng của người phụ nữ Việt.', exampleEn: 'The conical hat shields from sun and rain and is a symbol of Vietnamese women.' }
  ];

  for (const ew of extraWords) {
    const norm = ew.word.trim().toLowerCase();
    if (!seenWords.has(norm)) {
      seenWords.add(norm);
      vocabList.unshift(ew);
    }
  }

  console.log(`Prepared ${vocabList.length} vocabulary words to seed.`);

  let createdCount = 0;
  for (const v of vocabList) {
    const payload = {
      word: v.word,
      pronunciation: v.pronunciation || null,
      partOfSpeech: v.partOfSpeech || 'NOUN',
      cefrLevel: v.cefrLevel || 'A1',
      status: 'PUBLISHED',
      meanings: [
        {
          definitionVi: v.word,
          translationEn: v.meaningEn || v.word,
          definitionEn: v.meaningEn || null,
          orderIndex: 0,
          examples: v.exampleVi ? [
            {
              exampleVi: v.exampleVi,
              translationEn: v.exampleEn || null,
              orderIndex: 0
            }
          ] : []
        }
      ]
    };

    try {
      await api('/api/admin/vocabulary', token, 'POST', payload);
      createdCount++;
      if (createdCount % 20 === 0 || createdCount === vocabList.length) {
        console.log(`  ✓ Seeded ${createdCount}/${vocabList.length} words`);
      }
    } catch (err) {
      // Ignored if already exists
    }
  }
  console.log(`Completed seeding ${createdCount} vocabulary words into database.`);
}

async function main() {
  const admin = await login('admin', demoPassword);
  await seedScenarios(admin);
  await seedVocabulary(admin);
}

main().catch(err => {
  console.error('Seed failed:', err);
  process.exit(1);
});
