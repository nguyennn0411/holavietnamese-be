// Author the reviewed 83-lesson syllabus; all original book pages remain authoritative.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname,'..');
const assets = require('../src/main/resources/learning/tv1-tap1-assets.json');
const readings = require('../src/main/resources/learning/tv1-tap1-readings.json');
const output = path.join(root,'src/main/resources/learning/tv1-tap1-course.json');
// number | exact lesson focus in the printed contents | keyword=English gloss
const syllabus = `
1|e|e=letter e
2|b|b=letter b
3|Dấu sắc|be=syllable be;bé=small
4|Dấu hỏi, dấu nặng|bẻ=break or bend; bẹ=leaf sheath
5|Dấu huyền, dấu ngã|bè=raft;bẽ=syllable bẽ
6|be, bè, bé, bẻ, bẽ, bẹ|be=syllable be;bè=raft;bé=small;bẻ=break or bend;bẽ=syllable bẽ;bẹ=leaf sheath
7|ê, v|bê=calf;ve=cicada
8|l, h|lê=pear;hè=summer
9|o, c|bò=cow;cỏ=grass
10|ô, ơ|cô=woman or female teacher;cờ=flag
11|Ôn tập|bê=calf;ve=cicada;lê=pear;hè=summer;bò=cow;cỏ=grass;cô=woman or female teacher;cờ=flag
12|i, a|bi=marble;cá=fish
13|n, m|nơ=ribbon bow;me=tamarind
14|d, đ|dê=goat;đò=ferry
15|t, th|tổ=nest;thỏ=rabbit
16|Ôn tập|bi=marble;cá=fish;nơ=ribbon bow;me=tamarind;dê=goat;đò=ferry;tổ=nest;thỏ=rabbit
17|u, ư|nụ=bud;thư=letter
18|x, ch|xe=vehicle;chó=dog
19|s, r|sẻ=sparrow;rễ=root
20|k, kh|kẻ=rule a line;khế=star fruit
21|Ôn tập|nụ=bud;thư=letter;xe=vehicle;chó=dog;sẻ=sparrow;rễ=root;kẻ=rule a line;khế=star fruit
22|p, ph, nh|phố=street;nhà=house
23|g, gh|gà=chicken;ghế=chair
24|q, qu, gi|quê=hometown;già=old
25|ng, ngh|ngừ=tuna;nghệ=turmeric
26|y, tr|y=letter y;tre=bamboo
27|Ôn tập|phố=street;nhà=house;gà=chicken;ghế=chair;quê=hometown;già=old;ngừ=tuna;nghệ=turmeric;tre=bamboo
28|Chữ thường - Chữ hoa|a=lowercase a;A=uppercase A;b=lowercase b;B=uppercase B
29|ia|tía=purple
30|ua, ưa|cua=crab;ngựa=horse
31|Ôn tập|tía=purple;cua=crab;ngựa=horse
32|oi, ai|ngói=roof tile;gái=girl
33|ôi, ơi|ổi=guava;bơi=swim
34|ui, ưi|núi=mountain;gửi=send
35|uôi, ươi|chuối=banana;bưởi=pomelo
36|ay, â, ây|bay=fly;dây=string
37|Ôn tập|ngói=roof tile;gái=girl;ổi=guava;bơi=swim;núi=mountain;gửi=send;chuối=banana;bưởi=pomelo;bay=fly;dây=string
38|eo, ao|mèo=cat;sao=star
39|au, âu|cau=areca palm;cầu=bridge
40|iu, êu|rìu=axe;phễu=funnel
41|iêu, yêu|diều=kite;yêu=love
42|ưu, ươu|lựu=pomegranate;hươu=deer
43|Ôn tập|mèo=cat;sao=star;cau=areca palm;cầu=bridge;rìu=axe;phễu=funnel;diều=kite;yêu=love;lựu=pomegranate;hươu=deer
44|on, an|con=child;sàn=floor
45|ân, ă, ăn|cân=scales;trăn=python
46|ôn, ơn|chồn=civet;sơn=paint
47|en, ên|sen=lotus;nhện=spider
48|in, un|pin=battery;giun=earthworm
49|iên, yên|điện=electricity;yến=swiftlet
50|uôn, ươn|chuồn=word in chuồn chuồn (dragonfly);vươn=stretch
51|Ôn tập|con=child;sàn=floor;cân=scales;trăn=python;chồn=civet;sơn=paint;sen=lotus;nhện=spider;pin=battery;giun=earthworm;điện=electricity;yến=swiftlet
52|ong, ông|võng=hammock;sông=river
53|ăng, âng|măng=bamboo shoot;tầng=floor or storey
54|ung, ưng|súng=word in bông súng (water lily);sừng=horn
55|eng, iêng|xẻng=shovel;chiêng=gong
56|uông, ương|chuông=bell;đường=road
57|ang, anh|bàng=tropical almond tree;chanh=lime
58|inh, ênh|tính=calculation;kênh=canal
59|Ôn tập|võng=hammock;sông=river;măng=bamboo shoot;tầng=floor or storey;súng=word in bông súng (water lily);sừng=horn;xẻng=shovel;chiêng=gong;chuông=bell;đường=road;bàng=tropical almond tree;chanh=lime;kênh=canal
60|om, am|xóm=neighbourhood;tràm=melaleuca tree
61|ăm, âm|tằm=silkworm;nấm=mushroom
62|ôm, ơm|tôm=shrimp;rơm=straw
63|em, êm|tem=stamp;đêm=night
64|im, um|chim=bird;trùm=cover over
65|iêm, yêm|xiêm=word in hồng xiêm (sapodilla);yếm=bib
66|uôm, ươm|buồm=sail;bướm=butterfly
67|Ôn tập|xóm=neighbourhood;tràm=melaleuca tree;tằm=silkworm;nấm=mushroom;tôm=shrimp;rơm=straw;tem=stamp;đêm=night;chim=bird;trùm=cover over;yếm=bib;buồm=sail;bướm=butterfly
68|ot, at|hót=sing (of a bird);hát=sing
69|ăt, ât|mặt=face;vật=object
70|ôt, ơt|cột=pillar;vợt=racket
71|et, êt|tét=word in bánh tét (cylindrical sticky rice cake);dệt=weave
72|ut, ưt|bút=pen;mứt=candied fruit
73|it, iêt|mít=jackfruit;viết=write
74|uôt, ươt|chuột=mouse;lướt=glide
75|Ôn tập|hót=sing (of a bird);hát=sing;mặt=face;vật=object;cột=pillar;vợt=racket;dệt=weave;bút=pen;mứt=candied fruit;mít=jackfruit;viết=write;chuột=mouse;lướt=glide
76|oc, ac|sóc=squirrel;bác=older adult (form of address)
77|ăc, âc|mắc=hang or get caught;gấc=gac fruit
78|uc, ưc|trục=axle;lực=strength
79|ôc, uôc|mộc=woodwork;đuốc=torch
80|iêc, ươc|xiếc=circus;rước=escort in a procession
81|ach|sách=book
82|ich, êch|lịch=calendar;ếch=frog
83|Ôn tập|sóc=squirrel;bác=older adult (form of address);mắc=hang or get caught;gấc=gac fruit;trục=axle;lực=strength;mộc=woodwork;đuốc=torch;xiếc=circus;rước=escort in a procession;sách=book;lịch=calendar;ếch=frog
`.trim().split('\n').map(line=>{
  const [n,focus,pairs]=line.trim().split('|');
  return {number:Number(n),focus,words:pairs.split(';').map(pair=>{const [wordVi,meaningEn]=pair.trim().split('=');return {wordVi,meaningEn};})};
});
assert.equal(syllabus.length,83);
const moduleDefinitions=[
  [1,11,'Chữ đầu tiên và sáu thanh điệu','First letters and six tones'],
  [12,16,'Âm i, a và các phụ âm đầu','Letters i, a and initial consonants'],
  [17,21,'Âm u, ư và các nhóm phụ âm','Letters u, ư and consonant groups'],
  [22,28,'Âm ghép và chữ hoa','Consonant combinations and uppercase letters'],
  [29,31,'Vần ia, ua, ưa','Rimes ia, ua and ưa'],
  [32,37,'Vần kết thúc bằng i, y','Rimes ending in i and y'],
  [38,43,'Vần kết thúc bằng o, u','Rimes ending in o and u'],
  [44,51,'Các vần kết thúc bằng n','Rimes ending in n'],
  [52,59,'Các vần kết thúc bằng ng, nh','Rimes ending in ng and nh'],
  [60,67,'Các vần kết thúc bằng m','Rimes ending in m'],
  [68,75,'Các vần kết thúc bằng t','Rimes ending in t'],
  [76,83,'Các vần kết thúc bằng c, ch','Rimes ending in c and ch'],
];
const storyTitles=Object.fromEntries(readings.lessons.filter(l=>l.story).map(l=>[l.number,l.speakingVi]));
const readingChecks=`
7|Bé vẽ con gì?|bê|cá|gà
8|Mùa nào được nhắc đến trong câu đọc?|hè|đông|xuân
9|Bò bê có gì?|bó cỏ|quyển vở|chiếc ghế
10|Bé có gì?|vở vẽ|bó cỏ|rổ cá
11|Bé vẽ những gì?|cô và cờ|cá và ghế|nhà và núi
12|Bé Hà có loại vở nào?|vở ô li|vở nhạc|sổ địa chỉ
13|Sau khi có cỏ, bò bê thế nào?|no nê|đi học|thi vẽ
14|Bé và mẹ đi bằng cách nào?|đi bộ|đi máy bay|đi tàu hỏa
15|Bố thả cá gì?|cá mè|cá ngừ|cá chép
16|Cò mẹ tha cá về đâu?|tổ|chợ|trường
17|Bé Hà thi vẽ vào thứ mấy?|thứ tư|thứ hai|thứ sáu
18|Xe ô tô chở gì về thị xã?|cá|đàn bò|bàn ghế
19|Bé tô cho rõ những gì?|chữ và số|cây và núi|mặt trời và trăng
20|Ai kẻ vở cho bé Hà và bé Lê?|chị Kha|dì Na|bà
21|Xe ô tô chở khỉ và sư tử về đâu?|sở thú|bãi biển|bệnh viện
22|Nhà dì Na có con vật gì?|chó xù|mèo mun|chim sáo
23|Nhà bà có những đồ vật gì?|tủ gỗ và ghế gỗ|xe lu và xe bò|diều và sáo
24|Chú Tư cho bé thứ gì?|giỏ cá|chùm nho|cuốn lịch
25|Chị Kha ra nhà ai khi nghỉ hè?|bé Nga|bé Hà|bác sĩ
26|Vì sao mẹ cho bé ra y tế xã?|bé bị ho|bé đi mua quà|bé thi vẽ
27|Phố bé Nga có nghề gì?|giã giò|đánh cá|dệt vải
28|Bố mẹ cho bé và chị Kha nghỉ hè ở đâu?|Sa Pa|Ba Vì|Huế
29|Chị Kha làm gì?|tỉa lá|hái nấm|thổi sáo
30|Mẹ mua những gì cho bé?|khế, mía, dừa, thị|cá, tôm, cua|vở, bút, thước
31|Bé đang làm gì?|ngủ trưa|nhảy dây|đọc sách
32|Chú Bói Cá nghĩ về điều gì?|bữa trưa|ngày Tết|một chiếc xe
33|Bé trai, bé gái đi chơi phố với ai?|bố mẹ|thầy giáo|bác sĩ
34|Ai vừa gửi thư về?|dì Na|chị Kha|bé Lê
35|Chị Kha rủ bé chơi trò gì?|đố chữ|đá bóng|đuổi bắt
36|Bé gái thi gì vào giờ ra chơi?|nhảy dây|bơi lội|thả diều
37|Gió từ tay mẹ giúp bé làm gì?|ngủ say|đi chợ|chơi bóng
38|Bé ngồi làm gì?|thổi sáo|viết thư|nấu cơm
39|Chào Mào có áo màu gì?|màu nâu|màu tím|màu trắng
40|Cây bưởi, cây táo nhà bà thế nào?|sai trĩu quả|không có lá|vừa được trồng
41|Tiếng tu hú báo hiệu mùa quả gì?|vải thiều|sầu riêng|măng cụt
42|Cừu chạy theo mẹ ra đâu?|bờ suối|sân ga|thư viện
43|Nhà Sáo Sậu ở đâu?|sau dãy núi|giữa biển|trong lớp học
44|Gấu mẹ dạy con làm gì?|chơi đàn|bơi lội|nhảy múa
45|Bố bạn Lê làm nghề gì?|thợ lặn|thợ mộc|lái tàu
46|Sau cơn mưa, cả nhà cá làm gì?|bơi đi bơi lại|bay lên trời|đi học
47|Nhà Sên ở đâu?|trên tàu lá chuối|dưới đáy biển|trên nóc nhà
48|Cả đàn lợn làm gì sau khi ăn no?|đi ngủ|đá bóng|đọc sách
49|Cả đàn kiến chở gì về tổ mới?|lá khô|cát biển|quả dừa
50|Con vật nào bay lượn trên giàn thiên lí?|chuồn chuồn|con cò|con gà
51|Gà con chờ mẹ làm gì?|rẽ cỏ, bới giun|viết thư|đọc truyện
52|Sóng nối nhau đến đâu?|chân trời|sân trường|ngọn núi
53|Vầng trăng hiện lên sau đâu?|rặng dừa cuối bãi|tủ sách|mái lều trong rừng
54|Trong câu đố, hiện tượng nào không gõ mà kêu?|sấm|mưa phùn|ánh nắng
55|Lòng ta vững như đồ vật nào?|kiềng ba chân|chiếc diều|quả bóng
56|Trai gái bản mường cùng làm gì?|vui vào hội|chở cá|xây nhà
57|Bài thơ nhắc đến con gì dù không có chân, cánh?|con sông|con gà|con chim
58|Đáp án của câu đố về vật cao lớn cần tựa là gì?|cái thang|chiếc bút|quyển vở
59|Mấy cô đội thứ gì về làng?|bông|rơm|cá
60|Theo câu đọc, nắng tháng tám làm gì?|rám trái bòng|gãy cành trám|làm tuyết rơi
61|Đàn dê đang làm gì?|gặm cỏ|bơi trong ao|bay theo hàng
62|Đường tới trường thế nào trong câu thơ?|xôn xao|vắng lặng|ngập tuyết
63|Con cò đậu phải cành thế nào?|cành mềm|cành sắt|cành có quả gấc
64|Khi về, em làm gì?|chào|im lặng bỏ đi|đi chợ
65|Sẻ âu yếm đàn con vào lúc nào?|tối đến|giữa buổi sáng|khi đang kiếm ăn
66|Hoa cải nhuộm cánh đồng màu gì?|màu vàng|màu tím|màu xanh lam
67|Bà giữ chùm quả gì đến cuối mùa?|cam|dừa|ổi
68|Trên vòm cây, chim làm gì?|hót|viết|dệt
69|Chú gà có lông màu gì?|vàng|tím|xanh lam
70|Cây dang tay lá để làm gì?|che bóng râm|đánh thức người|bắt cá
71|Chim tránh rét bay về phương nào?|phương nam|phương bắc|phương đông
72|Sau khi chim biến mất, còn lại gì?|tiếng hót|chiếc thuyền|tấm lịch
73|Con vật có cánh, biết bơi và đẻ trứng trong câu đố là gì?|con vịt|con mèo|con dê
74|Chú Chuột đi đâu?|đi chợ đường xa|đi học|đi nghỉ hè
75|Đáp án câu đố “Một đàn cò trắng phau phau” là gì?|bát đĩa|đàn ngỗng|đàn gà
76|Quả có vỏ như da cóc, cùi trắng và hạt đen trong câu đố là gì?|quả nhãn|quả chuối|quả mít
77|Đàn chim ngói mặc áo màu gì?|màu nâu|màu trắng|màu xanh
78|Con gì mào đỏ gọi người thức dậy?|gà trống|con ếch|con hươu
79|Mái nhà của ốc thế nào?|tròn vo bên mình|cao như núi|đỏ như gấc
80|Quê hương được ví với những gì trong đoạn đọc?|con diều biếc và con đò nhỏ|chiếc bút và quyển vở|cái thang và chiếc ghế
81|Bàn tay bẩn làm những gì cũng bẩn ngay?|sách, áo|cây, lá|mây, trời
82|Chim chích giúp cây chanh bằng cách nào?|bắt sâu|tưới nước|cắt cành
83|Theo bài thơ, lời chào làm con đường thế nào?|bớt xa|xa hơn|không có lối đi
`.trim().split('\n').map(line=>{const [number,prompt,answer,wrong1,wrong2]=line.split('|');return {number:Number(number),prompt,answer,wrong:[wrong1,wrong2]};});
const noTone = value => value.normalize('NFD').replace(/[\u0300\u0301\u0303\u0309\u0323]/g,'').normalize('NFC').toLowerCase();
const allWords = syllabus.flatMap(l=>l.words);
function mcq(lesson, promptVi,promptEn,correct,wrong,explanationVi,explanationEn,index=0) {
  const offset=(lesson.number+index)%3;
  const choices=[correct,...wrong].slice(0,3);
  assert.equal(new Set(choices).size,3);
  const ids=['a','b','c'];
  return {questionType:'MULTIPLE_CHOICE',promptVi:`Bài ${lesson.number}: ${promptVi}`,promptEn:`Lesson ${lesson.number}: ${promptEn}`,
    options:ids.map((id,k)=>({id,text:choices[(k-offset+3)%3],textVi:choices[(k-offset+3)%3],textEn:choices[(k-offset+3)%3]})),
    correctAnswerJson:{optionIds:[ids[offset]]},explanationVi,explanationEn,difficulty:'EASY',status:'PUBLISHED',topic:`TV1_T1_L${String(lesson.number).padStart(3,'0')}`};
}
function questions(lesson) {
  const first=lesson.words[0],second=lesson.words[1]||first;
  const isReview=lesson.focus==='Ôn tập';
  const firstIsLetter=lesson.number<=2;
  const distractors=allWords.filter(w=>!lesson.words.some(x=>x.wordVi===w.wordVi)).map(w=>w.wordVi);
  const unique=[...new Set(distractors)];
  const vocabulary=mcq(lesson,`Chọn ${firstIsLetter?'chữ':'tiếng'} được giới thiệu trong bài “${lesson.focus}”.`,`Choose a ${firstIsLetter?'letter':'keyword'} introduced in “${lesson.focus}”.`,first.wordVi,unique.slice(15,17),
    `Trang gốc giới thiệu ${firstIsLetter?'chữ':'tiếng'} “${first.wordVi}”. Quan sát lại chữ và dấu trước khi chọn.`,`The source page introduces “${first.wordVi}”. Check its letters and tone marks.`,0);
  const meaning=mcq(lesson,`Theo thẻ từ của bài, “${second.wordVi}” có nghĩa tiếng Anh nào?`,`According to this lesson’s word card, what is the English gloss of “${second.wordVi}”?`,second.meaningEn,
    [...new Set(allWords.map(w=>w.meaningEn))].filter(m=>m!==second.meaningEn).slice(20,22),
    `Trong ngữ cảnh bài học, “${second.wordVi}” được chú giải là “${second.meaningEn}”.`,`In this lesson’s context, “${second.wordVi}” is glossed as “${second.meaningEn}”.`,1);
  const copy={questionType:'FILL_BLANK',promptVi:`Bài ${lesson.number}: Nhìn thẻ từ rồi gõ lại chính xác “${second.wordVi}”, giữ đúng chữ và dấu.`,
    promptEn:`Lesson ${lesson.number}: Type “${second.wordVi}” exactly as shown on the word card, including its marks.`,options:[],
    correctAnswerJson:{acceptedAnswers:[second.wordVi],caseSensitive:lesson.number===28},explanationVi:`Đáp án: ${second.wordVi}. Dấu thanh và dấu của chữ phải giữ nguyên.`,
    explanationEn:`Answer: ${second.wordVi}. Preserve tone and letter marks.`,difficulty:'EASY',status:'PUBLISHED',topic:`TV1_T1_L${String(lesson.number).padStart(3,'0')}`};
  let discrimination;
  if(lesson.number===28) {
    discrimination=mcq(lesson,'Chữ hoa tương ứng với chữ “b” là chữ nào?','Which uppercase letter corresponds to “b”?','B',['A','D'],'b và B là một cặp chữ thường - chữ hoa.','b and B form a lowercase/uppercase pair.',2);
  } else if(lesson.number>=3 && lesson.number<=6) {
    const targets={3:['bé','dấu sắc','acute tone mark'],4:['bẻ','dấu hỏi','hook-above tone mark'],5:['bè','dấu huyền','grave tone mark'],6:['bẹ','dấu nặng','dot-below tone mark']};
    const [answer,tone,toneEn]=targets[lesson.number];
    discrimination=mcq(lesson,`Tiếng nào có ${tone}?`,`Which syllable has the ${toneEn}?`,answer,['be',answer==='bé'?'bè':'bé'],`“${answer}” mang ${tone}.`,`“${answer}” carries the ${toneEn}.`,2);
  } else if(firstIsLetter) {
    discrimination=mcq(lesson,`Chọn chữ in hoa của “${first.wordVi}”.`,`Choose the uppercase form of “${first.wordVi}”.`,first.wordVi.toUpperCase(),first.wordVi==='e'?['B','A']:['E','D'],`Chữ in hoa của ${first.wordVi} là ${first.wordVi.toUpperCase()}.`,`The uppercase form is ${first.wordVi.toUpperCase()}.`,2);
  } else {
    const keyword=lesson.words[Math.min(lesson.words.length-1,isReview?3:0)];
    const base=noTone(keyword.wordVi);
    const firstLetter=base[0];
    const otherWords=[...new Set(allWords.map(w=>w.wordVi))].filter(w=>noTone(w)[0]!==firstLetter && !lesson.words.some(x=>x.wordVi===w));
    discrimination=mcq(lesson,`Tiếng nào bắt đầu bằng chữ “${firstLetter}” và nằm trong nhóm từ của bài?`,`Which lesson keyword starts with “${firstLetter}”?`,keyword.wordVi,otherWords.slice(0,2),
      `Tiếng “${keyword.wordVi}” bắt đầu bằng chữ ${firstLetter}. Đọc cả tiếng và đối chiếu trang gốc.`,`“${keyword.wordVi}” starts with ${firstLetter}. Read the whole syllable and compare it with the source page.`,2);
  }
  const result=[vocabulary,meaning,copy,discrimination];
  const check=readingChecks.find(q=>q.number===lesson.number);
  if(check) result.push(mcq(lesson,`Đọc hiểu: ${check.prompt}`,`Reading check: ${check.prompt}`,check.answer,check.wrong,
    `Đối chiếu phần đọc ứng dụng: ${check.answer}. Với câu đố, quan sát cả tranh đi kèm.`,`Compare the application reading and, for a riddle, its illustration: ${check.answer}.`,3));
  return result;
}
function activity(type,titleVi,titleEn,contentJson,isRequired=true,instructionVi='',instructionEn='') {
  return {activityType:type,titleVi,titleEn,contentJson,isRequired,maxScore:0,status:'PUBLISHED',instructionVi,instructionEn,quizId:null,grammarTopicIds:[]};
}
function pageActivity(page,titleVi,titleEn,required=true) {
  return activity('IMAGE',titleVi,titleEn,{imageUrl:page.image,captionVi:`Tiếng Việt 1, tập một – trang sách ${page.printedPage ?? 'bìa'}; trang PDF ${page.pdfPage}/173.`,
    captionEn:`Vietnamese 1, Volume 1 – book page ${page.printedPage ?? 'cover'}; PDF page ${page.pdfPage}/173.`,sourcePdfPage:page.pdfPage},required,
    'Bấm vào ảnh để phóng to. Đọc và thực hiện đầy đủ các mục trong trang gốc.','Open the image to zoom. Read and complete every section of the original page.');
}
const modules=moduleDefinitions.map(([start,end,titleVi,titleEn],moduleIndex)=>({
  metadata:{titleVi,titleEn,descriptionVi:`Bài ${start}–${end}: ${titleVi.toLowerCase()}. Giữ đúng thứ tự của sách.`,descriptionEn:`Lessons ${start}–${end}, following the textbook order.`},
  lessons:syllabus.filter(l=>l.number>=start&&l.number<=end).map(lesson=>{
    const review=lesson.focus==='Ôn tập';
    const firstPage=2*lesson.number+3,lastPage=firstPage+1;
    const originals=assets.pages.filter(p=>p.pdfPage>=firstPage&&p.pdfPage<=lastPage);
    const words=lesson.words.map(w=>({...w,wordType:lesson.number<=2?'LETTER':'WORD',exampleVi:`Luyện đọc và viết: ${w.wordVi}.`,exampleEn:`Practise reading and writing: ${w.wordVi}.`}));
    const titleVi=`Bài ${lesson.number}: ${lesson.focus}`,titleEn=`Lesson ${lesson.number}: ${review?'Review':lesson.focus}`;
    const objectiveVi=review?`Ôn lại các chữ, vần và tiếng trong học phần; đọc trang ${firstPage-1}–${lastPage-1}, viết các tiếng có dấu và kể lại nội dung tranh theo thứ tự.`:
      `Nhận biết ${lesson.focus}; đọc và viết ${lesson.words.map(w=>w.wordVi).join(', ')}; đọc phần ứng dụng và thực hành nói theo tranh ở trang ${firstPage-1}–${lastPage-1}.`;
    const introVi=`NỘI DUNG BÀI ${lesson.number}: ${lesson.focus}\n\n1. Quan sát trang sách ${firstPage-1}. Chỉ vào từng chữ hoặc vần, nhận diện dấu và đọc chậm. ${review?'Dùng bảng ôn trong sách để ghép và đọc lại các tiếng đã học.':'Đối chiếu chữ in với mẫu chữ viết ở cuối trang.'}\n\n2. Từ khóa cần luyện: ${lesson.words.map(w=>w.wordVi).join(' • ')}. Đọc từng tiếng hai lần; lần thứ nhất đọc rõ từng phần, lần thứ hai đọc liền tiếng. Giữ đúng dấu của chữ (ă, â, ê, ô, ơ, ư nếu có) và dấu thanh.\n\n3. Mở trang sách ${lastPage-1}. Đọc toàn bộ từ, câu hoặc đoạn ứng dụng trên trang, không chỉ những từ khóa của thẻ. Chỉ vào từng từ khi đọc; đọc lại câu sau khi đã đọc đúng từng tiếng.\n\n4. ${storyTitles[lesson.number]?`Quan sát các tranh của truyện “${storyTitles[lesson.number]}” trong trang gốc. Nêu nhân vật, kể lần lượt việc xảy ra trong từng tranh rồi nói điều em hiểu từ câu chuyện.`:'Quan sát các tranh luyện nói trong trang gốc. Gọi tên người, vật hoặc hoạt động nhìn thấy; trả lời câu hỏi trên trang rồi nói thêm một câu liên quan đến tranh.'}\n\n5. Tập viết trên giấy theo mẫu của sách. Cuối bài, làm quiz để kiểm tra nhận diện chữ, từ khóa và dấu. Các câu hỏi số hóa và phần hướng dẫn học được biên soạn thêm; trang gốc giữ trọn nội dung sách.\n\nGợi ý cho người học nước ngoài: phần tiếng Anh là chú giải ngữ cảnh. Tiếng Việt là nội dung chính; một tiếng có thể có nhiều nghĩa khi đi cùng tiếng khác.`;
    const introEn=`LESSON ${lesson.number}: ${review?'Review':lesson.focus}\n\nStudy book pages ${firstPage-1}–${lastPage-1}. First identify the letters/rimes and tone marks, then read these keywords: ${lesson.words.map(w=>w.wordVi).join(', ')}. Read slowly and then fluently. Compare printed letters with the handwriting models.\n\nRead every word, sentence or passage on the second source page. Describe its illustrations and follow the printed speaking or storytelling task. ${storyTitles[lesson.number]?`This page includes the story “${storyTitles[lesson.number]}”; use the pictures to identify characters and retell events in order.`:''}\n\nPractise handwriting on paper, then take the digital quiz. The guidance, English glosses and digital assessments are supplementary teaching material; the complete source pages preserve the book’s original content. English glosses are contextual, not exhaustive dictionary definitions.`;
    const activities=[activity('TEXT','Hướng dẫn học và mục tiêu','Learning guide and objectives',{textVi:introVi,textEn:introEn})];
    if(lesson.number===1) {
      for(const p of assets.pages.filter(p=>p.pdfPage<=4)) activities.push(pageActivity(p,`Làm quen sách – trang PDF ${p.pdfPage}`,`Book introduction – PDF page ${p.pdfPage}`,false));
    }
    activities.push(pageActivity(originals[0],`Trang ${firstPage-1}: Chữ, vần và từ khóa`,`Page ${firstPage-1}: Letters, rimes and keywords`));
    activities.push(activity('VOCABULARY','Thẻ chữ và từ khóa','Letter and keyword cards',{items:words},true,'Đọc chữ, tiếng rồi xem nghĩa và viết lại. Chú giải tiếng Anh được biên soạn thêm.','Read each item, check its contextual gloss and write it. English glosses are supplementary.'));
    activities.push(pageActivity(originals[1],`Trang ${lastPage-1}: Đọc, nói và kể chuyện`,`Page ${lastPage-1}: Reading, speaking and storytelling`));
    const reading=readings.lessons.find(r=>r.number===lesson.number);
    assert.ok(reading);
    if(reading.readingVi) activities.push(activity('TEXT','Bài đọc ứng dụng trong sách','Application reading from the textbook',{
      textVi:reading.readingVi,textEn:`Read the Vietnamese application text above from book page ${lastPage-1}. Keep the printed line breaks when reading a poem. The original page remains available for comparison.`,sourcePdfPage:lastPage},true,
      `Đọc chậm, rõ dấu; sau đó đọc liền câu. Bản chép phần đọc ứng dụng ở trang ${lastPage-1}.`,`Read carefully, then fluently. Transcribed from the application reading on book page ${lastPage-1}.`));
    activities.push(activity('TEXT',`${reading.story?'Kể chuyện':'Luyện nói'}: ${reading.speakingVi}`,`${reading.story?'Storytelling':'Speaking'}: ${reading.speakingVi}`,{
      textVi:`CHỦ ĐỀ TRONG SÁCH: ${reading.speakingVi}\n\n${reading.story?`1. Quan sát đầy đủ các tranh của “${reading.speakingVi}” ở trang ${lastPage-1}. Chỉ nhân vật và nêu điều đang xảy ra trong mỗi tranh.\n2. Kể từng tranh bằng một đến hai câu, theo thứ tự đánh số.\n3. Nối các tranh bằng các từ “đầu tiên”, “sau đó”, “cuối cùng”.\n4. Trao đổi với người hướng dẫn về cách ứng xử của nhân vật. Nếu chỉ học một mình, mô tả các chi tiết nhìn thấy; trang sách không cung cấp toàn bộ lời kể thành văn.`:`1. Đọc tên chủ đề “${reading.speakingVi}” và tìm tranh tương ứng ở trang ${lastPage-1}.\n2. Chỉ và gọi tên các nhân vật, sự vật hoặc hoạt động liên quan tới chủ đề.\n3. Nói hai câu về nội dung tranh, rồi đặt một câu hỏi cho người cùng học.\n4. Liên hệ một điều gần gũi trong đời sống với chủ đề này; trả lời bằng một câu rõ ý.`}\n\nĐây là hướng dẫn luyện tập bổ sung. Nội dung và các hình minh họa gốc của bài được giữ trong hai trang sách.`,
      textEn:`Textbook topic: ${reading.speakingVi}. Study the corresponding source-page illustration. ${reading.story?'Name the characters and retell the numbered panels in order, using one or two sentences per panel. The book provides picture prompts rather than a full written narration.':'Name what you see, make two statements and ask a partner a question about the picture. Relate it to one everyday experience.'} The practice instructions are supplementary.`,sourcePdfPage:lastPage}));
    activities.push(activity('TEXT','Tập viết và thực hành theo tranh','Handwriting and picture practice',{
      textVi:`TẬP VIẾT\nChuẩn bị giấy có dòng kẻ. Quan sát mẫu viết ở trang ${firstPage-1}, sau đó viết ${lesson.words.map(w=>w.wordVi).join(', ')}. Mỗi tiếng viết một dòng; tự kiểm tra vị trí dấu, khoảng cách và độ cao chữ. Không chỉ chép thẻ in mà cần đối chiếu mẫu viết trong sách.\n\nLUYỆN ĐỌC\nĐọc lại toàn bộ phần ứng dụng ở trang ${lastPage-1}. Chọn một câu để đọc liền mạch; nếu trang chỉ có tiếng/từ, đọc theo từng dòng. Nhờ người hướng dẫn nghe và góp ý nếu có thể.\n\n${review?'KỂ THEO TRANH':'LUYỆN NÓI'}\n${storyTitles[lesson.number]?`Với truyện “${storyTitles[lesson.number]}”, chỉ từng tranh, nêu nhân vật và kể một đến hai câu về sự việc; sau đó nối các tranh thành câu chuyện.`:`Chọn một tranh ở trang ${lastPage-1}. Nói điều nhìn thấy, trả lời câu hỏi in trong sách rồi hỏi lại người cùng học. Dùng ít nhất một tiếng đang luyện khi phù hợp với tranh.`}\n\nTỰ KIỂM TRA\nTôi đã đọc đủ hai trang; tôi giữ đúng dấu; tôi đã viết trên giấy; tôi đã thực hành nói. Nút hoàn thành xác nhận việc tự học, không tự động chấm chữ viết hoặc phát âm.`,
      textEn:`HANDWRITING\nUse lined paper. Follow the handwriting model on book page ${firstPage-1} and write the lesson keywords, one line per item. Check letter height, spacing and placement of marks.\n\nREADING AND SPEAKING\nRead the entire application section on page ${lastPage-1}. Describe a picture, respond to the book’s printed prompt and exchange roles with a partner. For a review story, name the characters and retell the picture sequence.\n\nSELF-CHECK\nConfirm that you studied both pages, kept the marks, wrote on paper and practised speaking. Completion records self-study; it does not automatically assess handwriting or pronunciation.`}));
    const illustrations=originals.flatMap(page=>page.illustrations.map((crop,index)=>({imageUrl:crop.image,sourcePage:page.printedPage,
      captionVi:`Vùng hình ${index+1}, trang sách ${page.printedPage}`,captionEn:`Source region ${index+1}, book page ${page.printedPage}`})));
    if(illustrations.length) activities.push(activity('IMAGE','Bộ tranh và chữ đã tách từ bài học','Extracted picture and letter gallery',{
      illustrations,captionVi:`Toàn bộ ${illustrations.length} vùng hình đã tách từ trang ${firstPage-1}–${lastPage-1}. Bấm từng hình để phóng to.`,
      captionEn:`All ${illustrations.length} extracted visual regions from book pages ${firstPage-1}–${lastPage-1}. Open any image to zoom.`},false,
      'Quan sát các hình đã tách và đối chiếu với trang đầy đủ; đây là phần tham khảo tự chọn.','Compare the extracted pictures with the complete pages. This reference gallery is optional.'));
    if(lesson.number===83) for(const p of assets.pages.filter(p=>p.pdfPage>=171))
      activities.push(pageActivity(p,`Phụ lục và mục lục – trang PDF ${p.pdfPage}`,`Contents and publication details – PDF page ${p.pdfPage}`,false));
    return {...lesson,titleVi,titleEn,estimatedMinutes:25,lessonType:review?'REVIEW':'NORMAL',sourcePdfPages:[firstPage,lastPage],
      descriptionVi:`Học ${lesson.focus.toLowerCase()} theo trang sách ${firstPage-1}–${lastPage-1}: quan sát tranh, đọc chữ/từ, tập viết và thực hành nói.`,
      descriptionEn:`Study ${review?'the review':lesson.focus} on book pages ${firstPage-1}–${lastPage-1}: pictures, literacy, handwriting and speaking.`,objectiveVi,
      objectiveEn:`Recognise the lesson focus, read the source pages and keywords, practise handwriting and respond to picture prompts.`,activities,questions:questions(lesson)};
  })
}));
const course={schemaVersion:1,source:{filename:assets.sourceName,sha256:assets.sourceSha256,pdfPages:173,printedLessonCount:83,
  coverage:`Every PDF page is retained as a source image; ${assets.pages.reduce((n,p)=>n+p.illustrations.length,0)} supplementary visual-region crops are mapped to their lessons. The application readings and speaking headings are transcribed against the source images. OCR is used only for checking.`,
  attribution:'Bộ Giáo dục và Đào tạo; Tiếng Việt 1, tập một – Học vần; Nhà xuất bản Giáo dục Việt Nam. Source supplied by the user.',
  supplemental:'Learning guides, contextual English glosses and digital quizzes are supplementary, not claimed to be verbatim book exercises.'},
  course:{code:'VI1-TAP1-TEXTBOOK',slug:'tieng-viet-1-tap-1-hoc-van',titleVi:'Tiếng Việt 1 – Tập 1: Học vần',titleEn:'Vietnamese 1 – Volume 1: Literacy',
    descriptionVi:'Toàn bộ 83 bài của Tiếng Việt 1, tập một – Học vần, theo đúng thứ tự mục lục. Học chữ, thanh điệu và vần qua đầy đủ trang sách gốc, tranh minh họa, thẻ chữ/từ, tập viết, luyện đọc – nói và quiz từng bài. Có mục lục, trang hướng dẫn và thông tin xuất bản để đối chiếu nguồn.',
    descriptionEn:'All 83 lessons of the user-supplied Vietnamese 1, Volume 1 literacy textbook, in printed order. Complete source pages and extracted visual regions are paired with keyword cards, handwriting/speaking guidance and lesson quizzes. Introductory pages, contents and publication details are retained.',
    level:'A0',estimatedMinutes:2075,isFree:true,thumbnailUrl:assets.pages[0].image,
    learningOutcomes:'Nhận biết chữ in, chữ viết và sáu thanh điệu; đọc các tiếng, từ, câu và đoạn ứng dụng trong 83 bài; luyện viết theo mẫu; nói và kể chuyện theo tranh; ôn tập các nhóm vần; hoàn thành quiz từng bài và đánh giá cuối khóa.'},modules};
const lessons=modules.flatMap(m=>m.lessons);
const used=new Set(lessons.flatMap(l=>l.activities.filter(a=>a.contentJson.imageUrl?.includes('/pages/')).map(a=>a.contentJson.sourcePdfPage)));
assert.equal(used.size,173);assert.ok(assets.pages.every(p=>used.has(p.pdfPage)));
assert.equal(lessons.length,83);
for(const [i,l] of lessons.entries()){assert.equal(l.number,i+1);assert.equal(l.questions.length,l.number>=7?5:4);assert.ok(l.activities.every(a=>a.contentJson));}
fs.writeFileSync(output,JSON.stringify(course,null,2)+'\n');
console.log(JSON.stringify({course:course.course.code,modules:modules.length,lessons:lessons.length,sourcePages:used.size,activities:lessons.reduce((n,l)=>n+l.activities.length,0),questions:lessons.reduce((n,l)=>n+l.questions.length,0)}));
