// Editorial source for the persisted foundation curriculum. Run with Node from any directory.
// All sentences and assessments below are newly authored for Hola Vietnamese.
const fs = require('node:fs');
const path = require('node:path');
const out = path.resolve(__dirname, '../src/main/resources/learning/foundation-course.json');
const pair = (textVi, textEn) => ({textVi,textEn});
const lessons=[];
function lesson(titleVi,titleEn,focusVi,focusEn,words,lines,questionVi,questionEn,choices,correct=0) {
  const vocabulary=words.trim().split('\n').map(row=>{
    const [wordVi,meaningEn,exampleVi,exampleEn]=row.split('|');
    if(!exampleEn) throw new Error(row);
    return {wordVi,meaningEn,exampleVi,exampleEn,pronunciation:null,wordType:null,audioUrl:null,imageUrl:null};
  });
  lessons.push({titleVi,titleEn,focusVi,focusEn,vocabulary,lines:lines.trim().split('\n').map(row=>pair(...row.split('|'))),questionVi,questionEn,choices:choices.split('|').map(x=>x.split('~')),correct});
}
lesson('Chào mừng đến với tiếng Việt','Welcome to Vietnamese',
'Tiếng Việt dùng chữ cái Latin có thêm các dấu. Dấu thanh làm thay đổi nghĩa của tiếng. Học theo thứ tự: đọc hướng dẫn, học từ, luyện tập rồi làm kiểm tra. Hoàn thành các hoạt động bắt buộc và đạt ít nhất 70% để mở bài tiếp theo. Bốn kỹ năng là nghe, nói, đọc, viết.',
'Vietnamese uses Latin letters with additional marks. Tone marks change syllable meaning. Read the guidance, learn words, practise, then take the quiz. Complete required activities and score at least 70% to unlock the next lesson. The four skills are listening, speaking, reading and writing.',
`học|learn|Tôi học mỗi ngày.|I learn every day.
nghe|listen|Tôi nghe cô nói.|I listen to the teacher.
nói|speak|Bạn nói chậm nhé.|Please speak slowly.
đọc|read|Tôi đọc một từ.|I read one word.
viết|write|Tôi viết tên tôi.|I write my name.
tiếng Việt|Vietnamese language|Tôi thích tiếng Việt.|I like Vietnamese.
bài học|lesson|Bài học này ngắn.|This lesson is short.
từ|word|Tôi biết từ này.|I know this word.
hiểu|understand|Tôi chưa hiểu.|I do not understand yet.
chậm|slow|Xin nói chậm hơn.|Please speak more slowly.`,
`Tôi học tiếng Việt ở nhà.|I study Vietnamese at home.
Tôi nghe, rồi tôi nói.|I listen, then I speak.
Tôi chưa hiểu. Xin nói chậm hơn.|I do not understand yet. Please speak more slowly.`,
'Bạn muốn người khác nói chậm lại. Chọn câu phù hợp.','You want someone to slow down. Choose a suitable request.',
'Xin nói chậm hơn.~Please speak more slowly.|Tạm biệt.~Goodbye.|Tôi đọc sách.~I read books.|Đây là nhà.~This is a house.');
lesson('Bảng chữ cái tiếng Việt','The Vietnamese Alphabet',
'Có 29 chữ: a ă â b c d đ e ê g h i k l m n o ô ơ p q r s t u ư v x y. Các chữ ă, â, ê, ô, ơ, ư là những chữ riêng; dấu trên chúng không phải dấu thanh. d và đ khác nhau. f, j, w, z không thuộc bảng chữ cái chuẩn, dù có thể xuất hiện trong tên nước ngoài.',
'The 29 letters are a ă â b c d đ e ê g h i k l m n o ô ơ p q r s t u ư v x y. The letters ă, â, ê, ô, ơ and ư are distinct letters; their vowel marks are not tone marks. d and đ are different. f, j, w and z are outside the standard alphabet but can occur in foreign names.',
`chữ cái|letter|Đây là chữ cái a.|This is the letter a.
bảng chữ cái|alphabet|Tôi đọc bảng chữ cái.|I read the alphabet.
ba|dad|Ba ở nhà.|Dad is at home.
cá|fish|Cá ở trong bát.|The fish is in the bowl.
đi|go|Tôi đi học.|I go to school.
mẹ|mother|Mẹ đọc sách.|Mother reads a book.
cô|female teacher; aunt|Cô dạy tiếng Việt.|The teacher teaches Vietnamese.
phở|pho noodle soup|Phở còn nóng.|The pho is still hot.
dấu thanh|tone mark|Chữ á có dấu thanh.|The letter á has a tone mark.`,
`Tôi đọc: a, ă, â.|I read: a, ă, â.
Tôi viết: o, ô, ơ.|I write: o, ô, ơ.
d và đ là hai chữ khác nhau.|d and đ are two different letters.`,
'Chữ nào thuộc bảng chữ cái tiếng Việt chuẩn?','Which letter belongs to the standard Vietnamese alphabet?', 'f~Letter f|j~Letter j|đ~Letter đ|z~Letter z',2);
lesson('Sáu thanh điệu','The Six Vietnamese Tones',
'Sáu thanh: ngang (ma), sắc (má), huyền (mà), hỏi (mả), ngã (mã), nặng (mạ). Theo giọng Bắc tham chiếu: ngang tương đối bằng; sắc đi lên; huyền đi xuống; hỏi hạ rồi nhấc; ngã có ngắt thanh và đi lên; nặng thấp, ngắn. Hỏi và ngã thường gần nhau trong giọng Nam. Không đổi nguyên âm khi luyện thanh.',
'Six tones: level (ma), rising (má), falling (mà), dipping (mả), broken rising (mã), and low heavy (mạ). In the Northern reference, level is fairly even; rising goes up; falling goes down; dipping falls then rises; broken rising has a voice interruption; heavy is low and short. Hỏi and ngã often merge in Southern speech. Keep the vowel stable as you practise.',
`ma|ghost|Trong tranh có một con ma.|There is a ghost in the picture.
má|mother; cheek|Má gọi tôi.|Mom calls me.
mà|but; that|Tôi muốn đi mà trời mưa.|I want to go but it is raining.
mả|grave|Đó là một ngôi mả cũ.|That is an old grave.
mã|code|Tôi ghi mã số.|I write down the code number.
mạ|rice seedling|Mạ xanh ngoài ruộng.|The rice seedlings are green in the field.
thanh ngang|level tone|Ma có thanh ngang.|Ma has a level tone.
thanh sắc|rising tone|Má có thanh sắc.|Má has a rising tone.
thanh huyền|falling tone|Mà có thanh huyền.|Mà has a falling tone.
thanh hỏi|dipping tone|Mả có thanh hỏi.|Mả has a dipping tone.
thanh ngã|broken rising tone|Mã có thanh ngã.|Mã has a broken rising tone.
thanh nặng|low heavy tone|Mạ có thanh nặng.|Mạ has a low heavy tone.`,
`Ma, má, mà: giữ nguyên âm a.|Ma, má, mà: keep the vowel a unchanged.
Mả, mã, mạ: chú ý dấu và độ cao.|Mả, mã, mạ: notice the marks and pitch.
Đọc chậm: má gọi tôi.|Read slowly: Mom calls me.`,
'Tiếng “mạ” mang thanh nào?','Which tone does “mạ” carry?', 'Thanh sắc~Rising tone|Thanh ngang~Level tone|Thanh nặng~Low heavy tone|Thanh hỏi~Dipping tone',2);
lesson('Ghép âm thành tiếng','Building Vietnamese Syllables',
'Một tiếng có phần âm đầu (có thể không có), vần và thanh. Vần gồm âm chính, có thể thêm âm đệm và âm cuối. Ghép b + a thành ba; thêm sắc vào ca thành cá. Không đọc tên chữ thay cho âm khi nối tiếng. Tiếng “an” không có phụ âm đầu; tiếng “cam” có âm cuối m.',
'A syllable has an optional initial, a rime and a tone. The rime contains the main vowel and may include a medial and a final sound. Blend b + a into ba; add the rising tone to ca to form cá. Blend sounds rather than letter names. “an” has no initial consonant; “cam” ends in m.',
`âm|sound|Tôi nghe một âm.|I hear one sound.
tiếng|syllable; speech|Từ này có hai tiếng.|This word has two syllables.
vần|rime|Cam có vần am.|Cam has the rime am.
ca|sing; shift|Cô ca một bài.|She sings a song.
cà|eggplant|Mẹ mua cà.|Mother buys eggplant.
bé|small child|Bé nhìn mẹ.|The child looks at mother.
lá|leaf|Lá màu xanh.|The leaf is green.
cá mè|silver carp|Đây là cá mè.|This is a silver carp.`,
`b + a → ba. Ba đọc.|b + a → ba. Dad reads.
c + a + sắc → cá. Cá bơi.|c + a + rising tone → cá. The fish swims.
m + e + nặng → mẹ. Mẹ cười.|m + e + heavy tone → mẹ. Mother smiles.`,
'Ghép c + a rồi thêm thanh sắc được tiếng nào?','Blend c + a and add the rising tone. Which syllable results?', 'cà~Eggplant|ca~Sing|cá~Fish|cạ~Rub against',2);
lesson('Âm a','The Vowel A',
'Đọc a với miệng mở rộng, lưỡi thấp. Giữ âm a khi thay phụ âm đầu: ba, ca, ga, la. Dấu thanh thay đổi đường giọng, không biến a thành ă hoặc â. Trong “nhà”, nh là âm đầu và a là âm chính.',
'Say a with the mouth open and the tongue low. Keep the vowel as you change the initial: ba, ca, ga, la. Tone changes pitch, not a into ă or â. In “nhà”, nh is the initial and a is the main vowel.',
`gà|chicken|Gà ở sân.|The chicken is in the yard.
nhà|house; home|Nhà tôi nhỏ.|My house is small.
xa|far|Nhà ga ở xa.|The station is far away.
ta|we; us|Ta cùng học.|Let us learn together.
cha|father|Cha đọc báo.|Father reads the newspaper.
bà|grandmother|Bà uống trà.|Grandmother drinks tea.
la|shout|Đừng la to.|Do not shout loudly.`,
`Nhà bà có gà.|Grandmother's house has chickens.
Bà chỉ cho tôi một chiếc lá.|Grandmother shows me a leaf.
Tôi nói: “Lá này to.”|I say, “This leaf is big.”`,
'Từ nào có âm chính a?','Which word has a as its main vowel?', 'mẹ~Mother|nhà~House|cô~Aunt|từ~Word',1);
lesson('Âm o và ô','The Vowels O and Ô',
'Cả o và ô đều tròn môi. Khi đọc ô, miệng khép hơn khi đọc o. Đọc chậm: o – ô, to – tô, co – cô. Dấu mũ trong ô thuộc chữ cái; dấu sắc trong ố thuộc thanh điệu.',
'Both o and ô use rounded lips. The mouth is more closed for ô than for o. Read slowly: o – ô, to – tô, co – cô. The circumflex in ô identifies the vowel; the acute mark in ố identifies the tone.',
`to|big|Cái ô này to.|This umbrella is big.
ô|umbrella|Tôi có một cái ô.|I have an umbrella.
bố|father|Bố mở cửa.|Father opens the door.
tô|large bowl|Tô phở còn nóng.|The bowl of pho is still hot.
no|full after eating|Tôi no rồi.|I am full now.
cổ|neck|Cổ tôi hơi đau.|My neck hurts a little.
bò|cow; beef|Bò ăn cỏ.|The cow eats grass.`,
`Bố có một cái ô to.|Father has a big umbrella.
Cô ăn một tô phở.|Aunt eats a bowl of pho.
Cô nói: “Tôi no rồi.”|Aunt says, “I am full now.”`,
'Trong cặp “to – tô”, điều gì thay đổi?','What changes in the pair “to – tô”?', 'Âm đầu~Initial consonant|Nguyên âm~Vowel|Số tiếng~Number of syllables|Thanh điệu~Tone',1);
lesson('Âm ơ','The Vowel Ơ',
'Đọc ơ với môi không tròn, lưỡi ở vị trí giữa. So sánh o – ô – ơ; đừng đọc ơ như ô. Trong “phở”, dấu móc tạo chữ ơ, còn dấu hỏi tạo thanh hỏi. Phở là món nước với bánh phở; đây cũng là từ hữu ích khi gọi món.',
'Say ơ with unrounded lips and a central tongue position. Compare o – ô – ơ; do not substitute ô for ơ. In “phở”, the horn identifies ơ while the hook above marks the hỏi tone. Pho is a noodle soup and a useful word when ordering food.',
`cờ|flag|Cờ ở trước nhà.|The flag is in front of the house.
vợ|wife|Vợ tôi học tiếng Việt.|My wife studies Vietnamese.
chợ|market|Tôi đi chợ.|I go to the market.
bơ|butter; avocado|Tôi mua quả bơ.|I buy an avocado.
mơ|dream; apricot|Tôi mơ về nhà.|I dream of home.
vở|notebook|Vở ở trên bàn.|The notebook is on the table.
thở|breathe|Tôi thở chậm.|I breathe slowly.`,
`Tôi và vợ đi chợ.|My wife and I go to the market.
Vợ mua bơ, tôi mua phở.|My wife buys avocado; I buy pho.
Chúng tôi về nhà.|We go home.`,
'Tiếng nào có chữ ơ?','Which syllable contains the letter ơ?', 'cô~Aunt|co~Contract|cờ~Flag|cá~Fish',2);
lesson('Âm e và ê','The Vowels E and Ê',
'Đọc e với miệng mở hơn ê. Cả hai không tròn môi. So sánh me – mê và be – bê, giữ nguyên âm đầu. Trong “ghế”, ê mang thanh sắc; gh đứng trước ê.',
'The mouth is more open for e than for ê. Neither vowel uses rounded lips. Compare me – mê and be – bê while keeping the initial unchanged. In “ghế”, ê carries the rising tone; gh precedes ê.',
`em|younger sibling|Em tôi ở nhà.|My younger sibling is at home.
ghế|chair|Ghế ở cạnh bàn.|The chair is next to the table.
me|tamarind|Me có vị chua.|Tamarind tastes sour.
mê|be fascinated by|Em mê đọc sách.|My sibling loves reading.
bê|calf; carry|Bê đứng cạnh bò.|The calf stands beside the cow.
chè|sweet soup; tea|Tôi ăn chè đậu.|I eat bean sweet soup.
lê|pear|Quả lê này ngọt.|This pear is sweet.`,
`Em ngồi trên ghế.|My younger sibling sits on a chair.
Mẹ cho em một quả lê.|Mother gives my sibling a pear.
Em nói: “Lê ngọt quá!”|My sibling says, “The pear is so sweet!”`,
'Từ nào có nguyên âm ê?','Which word has the vowel ê?', 'mẹ~Mother|bé~Child|me~Tamarind|ghế~Chair',3);
lesson('Âm i và ia','I and IA',
'i là nguyên âm cao, môi không tròn. ia bắt đầu gần i rồi chuyển về âm giữa trong một tiếng; không tách “kia” thành hai tiếng. Dùng “đây” cho vật gần, “kia” cho vật xa: Đây là xe. Kia là nhà.',
'i is a high, unrounded vowel. ia moves from an i-like position toward a central vowel within one syllable; do not split “kia” into two syllables. Use “đây” for something near and “kia” for something farther away: This is a vehicle. That is a house.',
`bi|marble|Bé chơi bi.|The child plays marbles.
kia|that; over there|Kia là nhà tôi.|That is my house.
chia|share; divide|Tôi chia bánh với bạn.|I share cake with you.
đây|this; here|Đây là vở của tôi.|This is my notebook.
thìa|spoon|Thìa ở trong bát.|The spoon is in the bowl.
bìa|cover; cardboard|Bìa sách màu đỏ.|The book cover is red.
tía|purple; dad in some southern usage|Lá tía tô có màu tía.|Perilla leaves are purple.`,
`Đây là thìa của tôi.|This is my spoon.
Kia là bát của bạn.|That is your bowl.
Tôi chia chè với bạn.|I share sweet soup with you.`,
'Bạn chỉ một ngôi nhà ở xa. Chọn từ điền: “___ là nhà.”','You point to a house far away. Choose the missing word: “___ là nhà.”', 'Kia~That over there|Đọc~Read|Viết~Write|Nghe~Listen');
lesson('b, c, d, đ','B, C, D and Đ',
'b khép hai môi rồi mở; c là âm tắc ở phía sau miệng; đ là âm tắc ở đầu lưỡi. d không phải đ: d thường gần “z” trong giọng Bắc, gần “y” trong giọng Nam. Luyện ba – ca – da – đa, rồi thêm thanh. c đứng trước a, o, ô, ơ, u, ư.',
'b closes then opens both lips; c is a stop toward the back of the mouth; đ is a tongue-tip stop. d differs from đ: d often resembles z in Northern speech and y in Southern speech. Practise ba – ca – da – đa, then add tones. c precedes a, o, ô, ơ, u and ư.',
`da|skin|Da tôi khô.|My skin is dry.
đá|stone; ice|Trong cốc có đá.|There is ice in the glass.
đa|banyan tree|Cây đa rất to.|The banyan tree is very large.
dê|goat|Dê ăn lá.|The goat eats leaves.
đỏ|red|Cờ màu đỏ.|The flag is red.
cáo|fox|Cáo có đuôi dài.|The fox has a long tail.
bò con|calf|Bò con ở cạnh mẹ.|The calf is beside its mother.`,
`Tôi lấy cốc đá.|I take a glass of ice.
Bạn chỉ vào da tay.|You point to the skin on your hand.
Tôi đọc rõ: da, đá.|I read clearly: skin, stone.`,
'Điền chữ đầu để viết từ chỉ hòn đá: “__á”.','Complete the initial of the word for a stone: “__á”.', 'd~Letter d|đ~Letter đ|b~Letter b|c~Letter c',1);
lesson('g và h','G and H',
'g tạo tiếng xát ở phía sau miệng và có rung giọng; h là hơi thoát nhẹ qua họng. Đọc ga – ha, gà – hà. Không thêm một nguyên âm giữa phụ âm và vần. “hoa” có âm đệm; hãy đọc liền một tiếng.',
'g is a voiced friction sound toward the back of the mouth; h is a gentle flow of breath through the throat. Read ga – ha, gà – hà. Do not insert a vowel between an initial and its rime. “hoa” includes a medial sound; say it as one syllable.',
`ga|station|Ga ở gần chợ.|The station is near the market.
hoa|flower|Hoa ở trên bàn.|The flowers are on the table.
hồ|lake|Nhà tôi gần hồ.|My house is near a lake.
gỗ|wood|Bàn làm bằng gỗ.|The table is made of wood.
hè|summer|Mùa hè trời nóng.|It is hot in summer.
họ|surname; they|Họ của tôi là Lê.|My surname is Lê.
gõ|knock; type|Bạn gõ cửa.|You knock on the door.`,
`Gần ga có một hồ nhỏ.|Near the station there is a small lake.
Hai bên hồ có hoa.|There are flowers on both sides of the lake.
Tôi ngồi trên ghế gỗ.|I sit on a wooden chair.`,
'Tiếng nào bắt đầu bằng h?','Which syllable begins with h?', 'ga~Station|gỗ~Wood|gà~Chicken|hồ~Lake',3);
lesson('gh','GH',
'Viết gh trước e, ê, i: ghe, ghế, ghi. g và gh ghi cùng một phụ âm đầu; lựa chọn theo chữ nguyên âm tiếp theo. Viết ga, gô, gù nhưng ghe, ghê, ghi. “ghe” là thuyền nhỏ, khác “nghe” là hoạt động lắng nghe.',
'Write gh before e, ê and i: ghe, ghế, ghi. g and gh represent the same initial sound; choose the spelling based on the following vowel letter. Write ga, gô, gù but ghe, ghê, ghi. “ghe” means a small boat; “nghe” means listen.',
`ghe|small boat|Ghe ở bên bờ.|The boat is by the bank.
ghi|write down; record|Tôi ghi tên bạn.|I write down your name.
ghé|drop by|Tôi ghé nhà Lan.|I drop by Lan's home.
ghê|intense; scary|Gió mạnh ghê!|The wind is really strong!
ghim|pin|Tôi ghim tờ giấy.|I pin up the sheet of paper.
ghế gỗ|wooden chair|Ghế gỗ ở ngoài hiên.|The wooden chair is on the porch.
ghi nhớ|remember|Tôi ghi nhớ chữ gh.|I remember the letters gh.`,
`Lan đi ghe qua hồ.|Lan takes a boat across the lake.
Lan ghé nhà tôi.|Lan drops by my home.
Tôi ghi tên Lan vào vở.|I write Lan's name in my notebook.`,
'Điền g hoặc gh: “__ế”.','Choose g or gh to complete “__ế”.','g~Spelling g|gh~Spelling gh',1);
lesson('gi và k','GI and K',
'gi là một cụm chữ ghi âm đầu; gi gần d trong nhiều giọng. k ghi cùng âm đầu với c nhưng thường đứng trước e, ê, i: kẻ, kể, kí. Học cụm “Bao nhiêu tiền?” để hỏi giá; không cần ghi nhớ mọi cách xưng hô ngay.',
'gi is an initial letter combination and sounds similar to d in many accents. k represents the same initial sound as c but normally precedes e, ê and i: kẻ, kể, kí. Learn “Bao nhiêu tiền?” to ask a price; you do not need every form of address yet.',
`giá|price|Giá này tốt.|This price is good.
giỏ|basket|Rau ở trong giỏ.|The vegetables are in the basket.
kể|tell|Bà kể một chuyện.|Grandmother tells a story.
kì|period; term|Kì học mới bắt đầu.|The new term begins.
giờ|hour; now|Bây giờ là tám giờ.|It is eight o'clock now.
kẻ|draw a line|Tôi kẻ một dòng.|I draw a line.
tiền|money|Tôi trả tiền.|I pay.
bao nhiêu|how much; how many|Cái giỏ bao nhiêu tiền?|How much is the basket?`,
`Cái giỏ này bao nhiêu tiền?|How much is this basket?
Hai mươi nghìn đồng.|Twenty thousand dong.
Cảm ơn. Tôi lấy cái giỏ này.|Thank you. I will take this basket.`,
'Chọn cách viết đúng trước ê.','Choose the correct spelling before ê.','cể~Spelling cể|kể~Tell',1);
lesson('kh và m','KH and M',
'kh là âm xát không rung giọng ở phía sau miệng, có luồng hơi liên tục; k là âm tắc, luồng hơi bị chặn rồi mở. m khép môi và cho hơi qua mũi. Đọc khi – mi; khế – mê. Tránh biến kh thành k.',
'kh is a voiceless friction sound at the back of the mouth with continuous airflow; k is a stop with airflow blocked and released. For m, close the lips and let air pass through the nose. Read khi – mi; khế – mê. Do not replace kh with k.',
`khế|starfruit|Khế có vị chua.|Starfruit tastes sour.
khi|when|Tôi đọc khi rảnh.|I read when I am free.
khô|dry|Áo đã khô.|The shirt is dry now.
khó|difficult|Âm này hơi khó.|This sound is a little difficult.
mua|buy|Tôi mua khế.|I buy starfruit.
mũ|hat|Mũ của mẹ màu đỏ.|Mother's hat is red.
mi|eyelash|Mi bảo vệ mắt.|Eyelashes protect the eyes.`,
`Mẹ mua khế ở chợ.|Mother buys starfruit at the market.
Tôi đội mũ rồi đi cùng mẹ.|I put on a hat and go with mother.
Tôi nói: “Âm kh hơi khó.”|I say, “The kh sound is a little difficult.”`,
'Âm nào có luồng hơi xát liên tục ở phía sau miệng?','Which sound has continuous friction at the back of the mouth?', 'k~Sound k|m~Sound m|kh~Sound kh|b~Sound b',2);
lesson('n và nh','N and NH',
'Cả n và nh đều là âm mũi. n dùng đầu lưỡi ở phía trước; nh dùng phần trước của mặt lưỡi gần vòm miệng. Đọc na – nha, no – nho. nh là một âm đầu, không đọc tách n rồi h.',
'Both n and nh are nasal sounds. n uses the tongue tip toward the front; nh uses the front of the tongue body near the palate. Read na – nha, no – nho. nh is a single initial sound, not separate n and h.',
`nho|grapes|Nho này ngọt.|These grapes are sweet.
nhỏ|small|Cái nhà nhỏ.|The house is small.
nhớ|remember; miss|Tôi nhớ nhà.|I miss home.
na|sugar apple|Na đã chín.|The sugar apple is ripe.
nơ|bow|Bé đeo nơ.|The child wears a bow.
nhẹ|light; gentle|Túi này nhẹ.|This bag is light.
nhờ|ask for help|Tôi nhờ bạn đọc.|I ask you to read.`,
`Nhà tôi nhỏ nhưng sáng.|My house is small but bright.
Trên bàn có nho và na.|There are grapes and sugar apples on the table.
Đi xa, tôi nhớ nhà.|When I travel far, I miss home.`,
'Tiếng nào bắt đầu bằng nh?','Which syllable starts with nh?', 'na~Sugar apple|nơ~Bow|no~Full|nhỏ~Small',3);
lesson('ng và ngh','NG and NGH',
'ng và ngh cùng ghi một âm mũi ở phía sau miệng. Viết ngh trước e, ê, i: nghe, nghề, nghỉ. Viết ng trước a, o, ô, ơ, u, ư. Khi nói “nghe”, không thêm âm ơ ở trước ng.',
'ng and ngh represent the same nasal sound at the back of the mouth. Write ngh before e, ê and i: nghe, nghề, nghỉ. Write ng before a, o, ô, ơ, u and ư. Do not add an extra vowel before the initial of “nghe”.',
`nghề|profession|Nghề của cô là dạy học.|Her profession is teaching.
nghỉ|rest; take a break|Tôi nghỉ một chút.|I rest for a moment.
ngà|ivory; tusk|Voi có ngà.|Elephants have tusks.
ngõ|alley|Nhà ở cuối ngõ.|The house is at the end of the alley.
ngô|corn|Tôi ăn ngô.|I eat corn.
ngủ|sleep|Bé đang ngủ.|The baby is sleeping.
nghĩ|think|Tôi nghĩ về bài học.|I think about the lesson.`,
`Tôi nghe cô đọc.|I listen to the teacher read.
Sau bài học, tôi nghỉ một chút.|After the lesson, I take a short break.
Nhà cô ở trong ngõ.|Her house is in an alley.`,
'Điền ng hoặc ngh: “__ỉ”.','Choose ng or ngh to complete “__ỉ”.','ngh~Spelling ngh|ng~Spelling ng');
lesson('p và ph','P and PH',
'ph là âm xát: môi dưới chạm nhẹ răng trên rồi thổi hơi. p khép hai môi; ở cuối tiếng p kết thúc ngắn, không bật thêm nguyên âm. p đầu tiếng ít gặp trong từ thuần Việt. Không đọc “phở” thành “pở”.',
'ph is a friction sound: lightly touch the lower lip to the upper teeth and release air. p closes both lips; syllable-final p ends abruptly without an extra vowel. Initial p is uncommon in native Vietnamese words. Do not read “phở” as “pở”.',
`phố|street|Phố này yên tĩnh.|This street is quiet.
phà|ferry|Tôi đi phà qua sông.|I take a ferry across the river.
cà phê|coffee|Cà phê của tôi nóng.|My coffee is hot.
phí|fee|Phí đi phà thấp.|The ferry fare is low.
phòng|room|Phòng này sáng.|This room is bright.
phải|right; must|Rẽ phải ở đây.|Turn right here.
pha|mix; brew|Tôi pha cà phê.|I brew coffee.`,
`Tôi đi phà vào phố.|I take the ferry into town.
Tôi gọi phở và cà phê.|I order pho and coffee.
Cà phê nóng, tôi uống chậm.|The coffee is hot, so I drink slowly.`,
'Khi đọc ph, môi dưới chạm nhẹ vào đâu?','When saying ph, what does the lower lip lightly touch?', 'Răng trên~Upper teeth|Lưỡi~Tongue|Môi trên khép kín~Closed upper lip|Mũi~Nose');
lesson('qu và r','QU and R',
'q luôn đi cùng u trong cách viết tiếng Việt thông thường: quà, quê. Hãy đọc qu liền với phần vần. r thay đổi theo vùng; có thể gần z, âm xát hoặc âm rung. Giữ một giọng tham chiếu nhất quán, nhưng nhận biết các giọng khác.',
'q is paired with u in ordinary Vietnamese spelling: quà, quê. Blend qu with the rime. r varies by region and may be z-like, a friction sound, or a trill. Use a consistent reference accent while recognising other accents.',
`quà|gift|Tôi có quà cho mẹ.|I have a gift for mother.
quê|hometown; countryside|Quê tôi có sông.|My hometown has a river.
rau|vegetables|Mẹ rửa rau.|Mother washes vegetables.
rổ|basket; colander|Rau ở trong rổ.|The vegetables are in the colander.
quả|fruit; classifier for round objects|Đây là một quả cam.|This is an orange.
ra|go out|Tôi ra chợ.|I go out to the market.
rẻ|cheap|Rau hôm nay rẻ.|Vegetables are cheap today.`,
`Tôi về quê thăm mẹ.|I return to my hometown to visit mother.
Quà của tôi là một rổ rau.|My gift is a basket of vegetables.
Mẹ cười và nhận quà.|Mother smiles and accepts the gift.`,
'Chữ q thường đi cùng chữ nào?','Which letter normally follows q?', 'a~Letter a|i~Letter i|u~Letter u|e~Letter e',2);
lesson('s và x','S and X',
's và x là hai cách viết khác nhau. Một số giọng phân biệt s với vị trí lưỡi lùi hơn x; nhiều người miền Bắc đọc gần giống nhau. Không đánh giá một giọng vùng là sai. Ghi nhớ chữ viết qua số, sẻ, xe, xô.',
's and x are different spellings. Some accents distinguish s with the tongue farther back than for x; many Northern speakers pronounce them similarly. A regional accent is not an error. Learn spelling through số, sẻ, xe and xô.',
`số|number|Tôi ghi số nhà.|I write down the house number.
sẻ|sparrow|Chim sẻ ở trên cây.|The sparrow is in the tree.
xe|vehicle|Xe ở trước nhà.|The vehicle is in front of the house.
xô|bucket|Xô có nước.|The bucket contains water.
xa lộ|highway|Xe chạy trên xa lộ.|Vehicles travel on the highway.
sân|yard|Bé chơi ở sân.|The child plays in the yard.
xanh|green; blue|Lá màu xanh.|The leaf is green.`,
`Xe của tôi ở ngoài sân.|My vehicle is in the yard.
Bên xe có một cái xô.|There is a bucket beside the vehicle.
Tôi ghi số nhà vào vở.|I write the house number in my notebook.`,
'Từ chỉ phương tiện đi lại viết thế nào?','How do you spell the word for a vehicle?', 'se~Twist fibres|xe~Vehicle',1);
lesson('t, th, tr, ch','T, TH, TR and CH',
't và th khác luồng hơi: th bật hơi rõ hơn t. tr và ch là hai cách viết; mức độ phân biệt tùy vùng. Đọc chậm ta – tha; trà – chà; tre – che. Dùng một tiếng mỗi lần, không thêm ơ sau phụ âm.',
't and th differ in airflow: th has a stronger puff of air. tr and ch are different spellings whose pronunciation contrast varies by region. Read ta – tha; trà – chà; tre – che. Produce one syllable at a time without adding ơ after the consonant.',
`thỏ|rabbit|Thỏ ăn rau.|The rabbit eats vegetables.
trà|tea|Tôi uống trà.|I drink tea.
tre|bamboo|Tre mọc cạnh nhà.|Bamboo grows beside the house.
chó|dog|Chó nằm ở sân.|The dog lies in the yard.
chị|older sister|Chị tôi đọc sách.|My older sister reads a book.
che|cover; shade|Cây che nắng.|The tree provides shade.
tay|hand; arm|Tôi rửa tay.|I wash my hands.`,
`Chị pha trà cho tôi.|My older sister brews tea for me.
Chó nằm dưới bụi tre.|The dog lies beneath the bamboo.
Thỏ ăn rau ở bên sân.|The rabbit eats vegetables by the yard.`,
'Âm nào bật hơi rõ hơn trong cặp t – th?','Which sound has stronger aspiration in t – th?', 't~Sound t|th~Sound th',1);
lesson('u và ư','U and Ư',
'Cả u và ư đều dùng vị trí lưỡi cao. u tròn môi; ư không tròn môi. Đọc tu – tư, thu – thư, giữ thanh ngang. Dấu móc ở ư thuộc chữ cái, không phải dấu thanh.',
'Both u and ư use a high tongue position. u has rounded lips; ư has unrounded lips. Read tu – tư and thu – thư with a level tone. The horn on ư is part of the vowel letter, not a tone mark.',
`tủ|cabinet|Tủ ở cạnh cửa.|The cabinet is beside the door.
thu|autumn; collect|Mùa thu trời mát.|Autumn is cool.
thư|letter|Tôi viết thư cho mẹ.|I write a letter to mother.
sư tử|lion|Sư tử nằm dưới cây.|The lion lies under the tree.
chú|uncle|Chú tôi ở Hà Nội.|My uncle is in Hanoi.
cũ|old; used|Đây là tủ cũ.|This is an old cabinet.
thứ tư|Wednesday|Tôi học vào thứ tư.|I study on Wednesday.`,
`Thứ tư, chú gửi thư.|On Wednesday, uncle sends a letter.
Tôi để thư trên tủ.|I put the letter on the cabinet.
Mùa thu, tôi về thăm chú.|In autumn, I visit uncle.`,
'Âm nào không tròn môi?','Which vowel has unrounded lips?', 'u~Vowel u|ư~Vowel ư',1);
lesson('ua và ưa','UA and ƯA',
'ua và ưa là những nguyên âm đôi đọc liền trong một tiếng. Bắt đầu ua với môi tròn, ưa với môi không tròn, rồi chuyển về âm giữa. So sánh cua – cưa, mua – mưa. Đừng tách thành hai tiếng.',
'ua and ưa are vowel sequences blended within one syllable. Start ua with rounded lips and ưa with unrounded lips, then move toward a central vowel. Compare cua – cưa and mua – mưa. Do not split them into two syllables.',
`cua|crab|Cua ở trong rổ.|The crab is in the basket.
ngựa|horse|Ngựa ăn cỏ.|The horse eats grass.
dưa|melon|Dưa này ngọt.|This melon is sweet.
sữa|milk|Tôi uống sữa.|I drink milk.
mưa|rain|Trời đang mưa.|It is raining.
cưa|saw|Bố cất cái cưa.|Father puts away the saw.
trưa|noon|Buổi trưa tôi nghỉ.|At noon I rest.`,
`Buổi trưa, trời mưa.|At noon, it rains.
Tôi ở nhà uống sữa.|I stay home and drink milk.
Mẹ chia dưa cho cả nhà.|Mother shares melon with the family.`,
'Từ nào có ưa?','Which word contains ưa?', 'cua~Crab|mua~Buy|sữa~Milk|vua~King',2);
lesson('Chữ hoa và chữ thường','Uppercase and Lowercase Letters',
'Viết hoa chữ đầu câu và các thành phần tên riêng: Lan, Việt Nam, Hà Nội. Viết “Tôi tên là Anna.”, không viết cả câu bằng chữ thường. V và v ghi cùng âm; y và i thường cùng âm nhưng không luôn thay thế được trong tên và chính tả.',
'Capitalise sentence beginnings and the parts of proper names: Lan, Việt Nam, Hà Nội. Write “Tôi tên là Anna.” with capitals. V and v represent the same sound; y and i often share a sound but are not freely interchangeable in names and spelling.',
`tên riêng|proper name|Lan là tên riêng.|Lan is a proper name.
chữ hoa|uppercase letter|Tôi viết chữ hoa V.|I write a capital V.
chữ thường|lowercase letter|Đây là chữ thường v.|This is a lowercase v.
Việt Nam|Vietnam|Tôi sống ở Việt Nam.|I live in Vietnam.
Hà Nội|Hanoi|Hà Nội có nhiều hồ.|Hanoi has many lakes.
Đà Nẵng|Da Nang|Bạn tôi ở Đà Nẵng.|My friend is in Da Nang.
Huế|Hue|Tôi muốn thăm Huế.|I want to visit Hue.
y tá|nurse|Vy là y tá.|Vy is a nurse.`,
`Tôi tên là Vy.|My name is Vy.
Tôi là y tá ở Huế.|I am a nurse in Hue.
Bạn tôi tên là Anna.|My friend's name is Anna.`,
'Chọn câu viết hoa đúng.','Choose the correctly capitalised sentence.', 'tôi tên là anna.~All lowercase|Tôi tên là Anna.~My name is Anna.|Tôi Tên Là Anna.~Every word capitalised|tôi Tên là Anna.~Incorrect sentence initial',1);
lesson('Gia đình','Family',
'Dùng “Đây là + người” để giới thiệu. “mẹ tôi” nghĩa là mẹ của tôi: từ chỉ người sở hữu đứng sau. bố thường gặp ở miền Bắc, ba thường gặp ở miền Nam. anh/chị/em còn dùng để xưng hô theo tuổi và quan hệ, không chỉ chỉ người trong gia đình.',
'Use “Đây là + person” to introduce someone. “mẹ tôi” means my mother: the possessor follows the noun. bố is common in the North and ba in the South. anh/chị/em also serve as forms of address based on age and relationship, beyond family members.',
`ông|grandfather|Ông tôi thích trà.|My grandfather likes tea.
anh|older brother|Anh tôi làm việc.|My older brother works.
con|child|Con của Lan còn nhỏ.|Lan's child is still young.
gia đình|family|Gia đình tôi có bốn người.|My family has four people.
người|person|Đây là người nhà tôi.|This is a member of my family.
em gái|younger sister|Em gái tôi học ở đây.|My younger sister studies here.
anh trai|older brother|Anh trai tôi nấu cơm.|My older brother cooks rice.
bố mẹ|parents|Bố mẹ tôi ở quê.|My parents live in the countryside.`,
`Đây là gia đình tôi.|This is my family.
Đây là mẹ tôi, còn đây là anh trai tôi.|This is my mother, and this is my older brother.
Anh tôi nấu cơm cho cả nhà.|My brother cooks rice for the family.`,
'Bạn giới thiệu mẹ mình. Chọn câu đúng.','You introduce your mother. Choose the correct sentence.', 'Đây mẹ là tôi.~Incorrect word order|Đây là mẹ tôi.~This is my mother.|Mẹ đây tôi là.~Incorrect word order|Tôi đây là mẹ.~Incorrect word order',1);
lesson('Trường học','School',
'Dùng “Tôi là + vai trò” để nói mình là ai. Dùng “của” để nói sở hữu: lớp của tôi, bút của bạn. “Ai?” hỏi người; “Gì?” hỏi vật hoặc thông tin. “Đây là gì?” — “Đây là bút.”',
'Use “Tôi là + role” to identify yourself. Use “của” for possession: my class, your pen. “Ai?” asks about a person; “Gì?” asks about a thing or information. “What is this?” — “This is a pen.”',
`học sinh|school pupil|Tôi là học sinh.|I am a school pupil.
giáo viên|teacher|Cô Mai là giáo viên.|Ms Mai is a teacher.
bàn|desk; table|Bút ở trên bàn.|The pen is on the desk.
bút|pen|Tôi có hai cái bút.|I have two pens.
lớp|class|Lớp của tôi nhỏ.|My class is small.
trường|school|Trường ở gần nhà.|The school is near home.
ai|who|Ai là giáo viên?|Who is the teacher?
gì|what|Đây là gì?|What is this?
của|of; belonging to|Đây là vở của bạn.|This is your notebook.`,
`Ai là giáo viên của lớp?|Who is the class teacher?
Cô Mai là giáo viên.|Ms Mai is the teacher.
Đây là bàn và ghế của tôi.|This is my desk and chair.`,
'Bạn hỏi về một người. Chọn từ hỏi.','You ask about a person. Choose the question word.', 'Gì~What|Ai~Who|Đâu~Where|Bao nhiêu~How many',1);
lesson('Giới thiệu tên','Introducing Your Name',
'Hỏi “Bạn tên là gì?” và trả lời “Tôi tên là + tên”. Sau câu trả lời, dùng “Còn bạn?” để hỏi lại. “Xin chào” lịch sự và dễ dùng; “Rất vui được gặp bạn” dùng khi làm quen.',
'Ask “Bạn tên là gì?” and answer “Tôi tên là + name”. Follow your answer with “Còn bạn?” to return the question. “Xin chào” is a polite greeting; “Rất vui được gặp bạn” is used when meeting someone.',
`xin chào|hello|Xin chào, tôi là Mai.|Hello, I am Mai.
tên|name|Tên của bạn là gì?|What is your name?
bạn|you; friend|Bạn học ở đâu?|Where do you study?
tôi|I; me|Tôi tên là Minh.|My name is Minh.
còn bạn|and you|Tôi tên là Mai. Còn bạn?|My name is Mai. And you?
rất vui|very pleased|Tôi rất vui được gặp bạn.|I am very pleased to meet you.
gặp|meet|Tôi gặp bạn ở lớp.|I meet you in class.
cảm ơn|thank you|Cảm ơn bạn đã giúp tôi.|Thank you for helping me.`,
`Xin chào, tôi tên là Mai. Bạn tên là gì?|Hello, my name is Mai. What is your name?
Tôi tên là Ben. Rất vui được gặp bạn.|My name is Ben. Nice to meet you.
Tôi cũng rất vui được gặp bạn.|I am pleased to meet you too.`,
'Điền từ: “Tôi ___ là Mai.”','Complete the introduction: “Tôi ___ là Mai.”', 'tên~Name|đọc~Read|ăn~Eat|ngủ~Sleep');
lesson('Tôi là...','Using Là',
'Dùng chủ ngữ + là + danh từ để nói danh tính, nghề nghiệp hoặc vai trò. Không dùng là trước tính từ thông thường: “Tôi mệt”, “Nhà nhỏ”. “Tôi không phải là giáo viên” phủ định vai trò. Muốn hỏi vị trí, dùng “ở đâu”, không dùng là.',
'Use subject + là + noun for identity, profession or role. Do not normally put là before an adjective: “I am tired”, “The house is small”. “Tôi không phải là giáo viên” denies a role. To ask a location, use “ở đâu”, not là.',
`sinh viên|university student|Tôi là sinh viên.|I am a university student.
bác sĩ|doctor|Anh tôi là bác sĩ.|My older brother is a doctor.
kĩ sư|engineer|Lan là kĩ sư.|Lan is an engineer.
mệt|tired|Hôm nay tôi mệt.|Today I am tired.
khỏe|well; healthy|Tôi khỏe, cảm ơn.|I am well, thank you.
không phải|not (identity)|Tôi không phải là bác sĩ.|I am not a doctor.
ở đâu|where|Trường ở đâu?|Where is the school?
nghề nghiệp|occupation|Nghề nghiệp của bạn là gì?|What is your occupation?`,
`Tôi là sinh viên, còn Lan là kĩ sư.|I am a university student, and Lan is an engineer.
Hôm nay tôi hơi mệt.|Today I am a little tired.
Lan hỏi: “Lớp của bạn ở đâu?”|Lan asks, “Where is your class?”`,
'Câu nào tự nhiên khi nói bạn đang mệt?','Which sentence naturally says you are tired?', 'Tôi là mệt.~Incorrect use of là|Tôi mệt.~I am tired.|Tôi mệt là.~Incorrect word order|Là tôi mệt là.~Incorrect word order',1);
lesson('Những hoạt động hằng ngày','Daily Activities',
'Câu cơ bản: chủ ngữ + động từ + tân ngữ, như “Tôi đọc sách”. Dùng đang trước động từ cho hành động đang diễn ra. Dùng “muốn + động từ” để nói nhu cầu: Tôi muốn uống nước. “Buổi sáng” có thể đứng đầu câu để chỉ thời gian.',
'A basic sentence is subject + verb + object, as in “I read books”. Put đang before a verb for an action in progress. Use “muốn + verb” for a wish: I want to drink water. “Buổi sáng” can begin a sentence to indicate time.',
`ăn|eat|Tôi ăn cơm.|I eat rice.
uống|drink|Bạn uống nước.|You drink water.
sách|book|Tôi đọc sách mới.|I read a new book.
nước|water|Nước ở trong cốc.|The water is in the glass.
muốn|want|Tôi muốn nghỉ.|I want to rest.
đang|in progress|Tôi đang viết.|I am writing.
buổi sáng|morning|Buổi sáng tôi đi học.|In the morning I go to class.
làm việc|work|Mẹ đang làm việc.|Mother is working.
ăn sáng|have breakfast|Tôi ăn sáng ở nhà.|I have breakfast at home.
mỗi ngày|every day|Tôi học mỗi ngày.|I study every day.`,
`Buổi sáng, tôi ăn sáng ở nhà.|In the morning, I have breakfast at home.
Tôi đi học rồi đọc sách.|I go to class and then read a book.
Bây giờ tôi muốn uống nước.|Now I want to drink water.`,
'Điền từ nói nhu cầu: “Tôi ___ uống nước.”','Fill the word expressing a wish: “Tôi ___ uống nước.”','là~Be (identity)|muốn~Want|tên~Name|của~Of',1);
// Rime lessons use newly written short readings, articulatory guidance and contrast tasks.
lesson('am / ap','AM and AP',
'a + m → am; a + p → ap. m khép môi nhưng vẫn có tiếng qua mũi; p khép môi và dừng ngắn, không bật hơi cuối. Tiếng kết thúc bằng p chỉ mang sắc hoặc nặng. So sánh cam và cạp: nguyên âm a giữ độ mở.',
'a + m → am; a + p → ap. m closes the lips while nasal sound continues; p closes the lips and stops without an audible final release. Syllables ending in p take only sắc or nặng. Compare cam and cạp while keeping the open vowel a.',
`cam|orange|Cam này ngọt.|This orange is sweet.
khám|examine|Bác sĩ khám cho tôi.|The doctor examines me.
đạp|pedal|Tôi đạp xe đi học.|I pedal my bicycle to class.
sạp|market stall|Sạp này bán cam.|This stall sells oranges.
làm|do; work|Tôi làm bài ở nhà.|I do my work at home.
tham gia|take part|Tôi tham gia lớp học.|I join the class.
xe đạp|bicycle|Xe đạp ở trước sạp.|The bicycle is in front of the stall.`,
`Tôi đạp xe ra chợ.|I cycle to the market.
Sạp của cô Lan bán cam.|Ms Lan's stall sells oranges.
Tôi mua cam rồi về làm bài.|I buy oranges and return home to study.`,
'Từ “đạp” có vần nào?','Which rime is in “đạp”?','am~Rime am|ap~Rime ap|om~Rime om|em~Rime em',1);
lesson('ăm / ăp','ĂM and ĂP',
'ă ngắn hơn a trong các vần này. Đọc năm – nằm – nắm rồi gặp – gắp. m cho âm mũi kéo dài nhẹ; p dừng bằng hai môi. Trong “gặp”, dấu nặng nằm dưới ă; dấu trăng vẫn là phần của chữ ă.',
'ă is shorter than a in these rimes. Read năm – nằm – nắm, then gặp – gắp. m allows a short nasal sustain; p stops at both lips. In “gặp”, the heavy-tone dot is below ă; the breve remains part of the vowel letter.',
`chăm|diligent; care for|Lan chăm học.|Lan studies diligently.
nằm|lie down|Tôi nằm nghỉ.|I lie down to rest.
gắp|pick up with chopsticks|Tôi gắp rau.|I pick up vegetables with chopsticks.
năm|five; year|Tôi có năm cái bút.|I have five pens.
nắm|hold; handful|Bé nắm tay mẹ.|The child holds mother's hand.
thăm|visit|Tôi thăm bà.|I visit grandmother.
sắp|soon; about to|Tôi sắp về nhà.|I am about to go home.`,
`Tôi thăm bà vào thứ năm.|I visit grandmother on Thursday.
Bà gắp rau cho tôi.|Grandmother serves me vegetables with chopsticks.
Ăn xong, bà nằm nghỉ.|After eating, grandmother lies down to rest.`,
'Chọn vần của “gắp”.','Choose the rime in “gắp”.','ăm~Rime ăm|am~Rime am|ăp~Rime ăp|ap~Rime ap',2);
lesson('âm / âp','ÂM and ÂP',
'â là nguyên âm giữa, ngắn; không đọc thành a. Ghép â với m và p: âm, âp. Đọc chậm ấm – nấm – tập – mập. “Tập nói” là động từ tập đi cùng hoạt động nói; “ấm” là tính từ nên nói “Nước ấm”, không thêm là.',
'â is a short central vowel; do not replace it with a. Combine â with m and p: âm, âp. Read ấm – nấm – tập – mập slowly. “Tập nói” combines practise with speaking; “ấm” is an adjective, so say “Nước ấm” without là.',
`ấm|warm|Nước này ấm.|This water is warm.
nấm|mushroom|Mẹ nấu nấm.|Mother cooks mushrooms.
tập|practise|Tôi tập nói tiếng Việt.|I practise speaking Vietnamese.
mập|chubby; fat|Con mèo hơi mập.|The cat is a little chubby.
cầm|hold|Tôi cầm bút.|I hold a pen.
chậm rãi|slowly; unhurriedly|Tôi đọc chậm rãi.|I read slowly.
tập vở|notebooks (southern usage)|Tập vở ở trong cặp.|The notebooks are in the schoolbag.`,
`Tôi cầm cốc nước ấm.|I hold a cup of warm water.
Tôi tập nói chậm rãi.|I practise speaking slowly.
Trong bếp, mẹ đang nấu nấm.|In the kitchen, mother is cooking mushrooms.`,
'Tiếng nào kết thúc bằng p?','Which syllable ends in p?','ấm~Warm|nấm~Mushroom|cầm~Hold|tập~Practise',3);
lesson('em / ep','EM and EP',
'Đọc e mở miệng rồi khép môi với m hoặc p: em, ep. Đừng thêm ơ sau p. Tiếng “đẹp” có thanh nặng. Tính từ thường đứng sau danh từ: dép đẹp; cả cụm “Đôi dép đẹp” có thể là một câu miêu tả.',
'Say the open vowel e, then close the lips for m or p: em, ep. Do not add ơ after final p. “đẹp” has the heavy tone. Adjectives normally follow nouns: beautiful sandals; “Đôi dép đẹp” can be a descriptive sentence.',
`kem|ice cream|Em ăn kem dừa.|My sibling eats coconut ice cream.
dép|sandals|Dép ở cạnh cửa.|The sandals are by the door.
đẹp|beautiful|Hoa này đẹp.|These flowers are beautiful.
xem|watch; look at|Tôi xem ảnh.|I look at a photo.
tem|postage stamp|Tôi dán tem lên thư.|I stick a stamp on the letter.
kẹp|clip; hold between|Tôi kẹp giấy vào vở.|I clip the paper into the notebook.
đôi|pair|Tôi mua một đôi dép.|I buy a pair of sandals.`,
`Em xem đôi dép mới.|My younger sibling looks at the new sandals.
Em nói: “Đôi dép đẹp.”|My sibling says, “The sandals are beautiful.”
Sau đó, em ăn kem dừa.|After that, my sibling eats coconut ice cream.`,
'Chọn vần của “đẹp”.','Choose the rime in “đẹp”.','em~Rime em|ep~Rime ep|êm~Rime êm|êp~Rime êp',1);
lesson('êm / êp','ÊM and ÊP',
'Khép miệng hơn e để đọc ê, rồi thêm m hoặc p. Phân biệt em – êm, ep – êp. “đêm” có vần êm; “bếp” và “xếp” có vần êp. Dấu sắc nằm trên ê trong bếp, không thay đổi ê thành e.',
'Close the mouth more than for e to say ê, then add m or p. Distinguish em – êm and ep – êp. “đêm” has êm; “bếp” and “xếp” have êp. The rising-tone mark above ê in bếp does not change ê into e.',
`đêm|night|Ban đêm phố yên tĩnh.|At night the street is quiet.
êm|smooth; soft|Ghế này ngồi êm.|This chair feels soft to sit on.
bếp|kitchen; stove|Mẹ ở trong bếp.|Mother is in the kitchen.
xếp|arrange|Tôi xếp sách lên bàn.|I arrange books on the table.
thêm|more; add|Tôi muốn thêm nước.|I would like more water.
nếp|glutinous rice|Bà nấu cơm nếp.|Grandmother cooks sticky rice.
đếm|count|Tôi đếm năm cái bát.|I count five bowls.`,
`Ban đêm, bếp vẫn sáng.|At night, the kitchen is still bright.
Tôi xếp bát, mẹ nấu cơm nếp.|I arrange the bowls while mother cooks sticky rice.
Bà muốn thêm một bát.|Grandmother would like one more bowl.`,
'“bếp” có nguyên âm nào?','Which vowel is in “bếp”?','e~Vowel e|ê~Vowel ê|â~Vowel â|a~Vowel a',1);
lesson('im / ip','IM and IP',
'Đọc i với lưỡi cao rồi kết thúc bằng m hoặc p. So sánh kim – kịp. im có thể đứng một mình; ip thường gặp trong kịp, nhịp. “Im lặng” là yêu cầu giữ yên, nên thêm “xin” hoặc “nhé” cho nhẹ nhàng khi phù hợp.',
'Say i with a high tongue position, then end with m or p. Compare kim – kịp. im can stand alone; ip occurs in kịp and nhịp. “Im lặng” asks for quiet; add “xin” or “nhé” for a gentler request when appropriate.',
`im|quiet; silent|Cả phòng im.|The whole room is quiet.
chim|bird|Chim bay qua nhà.|A bird flies past the house.
kim|needle|Kim ở trong hộp.|The needle is in the box.
kịp|in time|Tôi đến kịp giờ.|I arrive on time.
nhịp|beat; rhythm|Tôi vỗ tay theo nhịp.|I clap to the beat.
tìm|look for|Tôi tìm bút.|I look for a pen.
im lặng|be quiet; silence|Xin im lặng một chút.|Please be quiet for a moment.`,
`Tôi tìm chiếc kim trong hộp.|I look for the needle in the box.
Ngoài cửa, chim hót theo nhịp.|Outside, a bird chirps rhythmically.
Tôi đến lớp kịp giờ.|I arrive at class on time.`,
'Tiếng nào có vần ip?','Which syllable has the rime ip?','chim~Bird|kim~Needle|kịp~In time|im~Quiet',2);
lesson('iêm / yêm / iêp','IÊM, YÊM and IÊP',
'Trong các vần này, iê hoặc yê ghi nguyên âm đôi trước âm cuối. iêm thường theo phụ âm đầu: diêm, tiêm; yêm xuất hiện trong yếm. iêp kết thúc bằng p: thiếp, tiếp. Nối phần nguyên âm rồi khép môi, không chèn thêm tiếng.',
'Here iê or yê represents a vowel sequence before a final consonant. iêm usually follows an initial: diêm, tiêm; yêm appears in yếm. iêp ends in p: thiếp, tiếp. Blend the vowel sequence and close the lips without adding another syllable.',
`diêm|match for lighting|Hộp diêm ở trên kệ.|The matchbox is on the shelf.
yếm|bib; traditional bodice|Bé đeo yếm khi ăn.|The baby wears a bib while eating.
tiêm|inject|Y tá tiêm cho bệnh nhân.|The nurse gives the patient an injection.
thiếp|greeting card|Tôi viết thiếp cho mẹ.|I write a card for mother.
tiếp|continue; next|Tôi đọc tiếp trang này.|I continue reading this page.
kiếm|look for; earn|Tôi kiếm chỗ ngồi.|I look for a seat.
niềm vui|joy|Đọc sách là niềm vui của tôi.|Reading is a joy for me.`,
`Tôi viết thiếp cho mẹ.|I write a card for mother.
Bên cạnh có bé đang đeo yếm.|Beside me, the baby is wearing a bib.
Viết xong, tôi đọc tiếp một trang sách.|After writing, I read one more page.`,
'Chọn vần của “thiếp”.','Choose the rime in “thiếp”.','iêm~Rime iêm|yêm~Rime yêm|ip~Rime ip|iêp~Rime iêp',3);
lesson('om / op','OM and OP',
'Đọc o tròn môi và mở vừa, rồi chuyển sang m hoặc p. Trong “gom”, tiếng mũi còn nghe được; trong “họp”, môi khép và dừng. “xóm” là khu dân cư nhỏ; “hàng xóm” là người sống gần nhà.',
'Say o with rounded lips and a moderately open mouth, then move to m or p. In “gom”, nasal sound continues; in “họp”, the lips close and stop. “xóm” is a small neighbourhood; “hàng xóm” means a neighbour.',
`xóm|neighbourhood|Xóm tôi gần sông.|My neighbourhood is near a river.
lom khom|bent over|Bà lom khom nhặt lá.|Grandmother bends down to pick up leaves.
họp|meet; meeting|Cả lớp họp sáng nay.|The whole class meets this morning.
gom|gather|Tôi gom sách lại.|I gather the books together.
hàng xóm|neighbour|Hàng xóm chào tôi.|My neighbour greets me.
cọp|tiger (southern usage)|Cọp sống trong rừng.|Tigers live in forests.
nhóm|group|Nhóm tôi có ba người.|My group has three people.`,
`Sáng nay, nhóm tôi họp ở xóm.|This morning, my group meets in the neighbourhood.
Chúng tôi gom lá trong sân.|We gather leaves in the yard.
Hàng xóm mang nước cho cả nhóm.|A neighbour brings water for the group.`,
'Tiếng nào kết thúc bằng p?','Which syllable ends in p?','gom~Gather|xóm~Neighbourhood|nhóm~Group|họp~Meet',3);
lesson('ôm / ôp','ÔM and ÔP',
'ô khép hơn o và tròn môi. Ghép ô + m → ôm, ô + p → ôp. So sánh tôm và hộp. Đọc liền ôm, không thêm âm đầu. Với “Mẹ ôm em bé”, thứ tự là người làm + hành động + người nhận.',
'ô is more closed than o with rounded lips. Combine ô + m → ôm and ô + p → ôp. Compare tôm and hộp. Say ôm without adding an initial. In “Mother hugs the baby”, the order is actor + action + recipient.',
`ôm|hug|Mẹ ôm em bé.|Mother hugs the baby.
tôm|shrimp|Tôm ở trong bát.|The shrimp are in the bowl.
hộp|box|Tôi mở hộp.|I open the box.
lốp|tyre|Lốp xe còn mới.|The tyre is still new.
hôm nay|today|Hôm nay trời mát.|Today it is cool.
ốm|ill; thin|Tôi bị ốm nên nghỉ.|I am ill, so I rest.
đốm|spot; speck|Áo có một đốm màu.|The shirt has a spot of colour.`,
`Hôm nay mẹ mua tôm.|Today mother buys shrimp.
Tôi cất tôm vào hộp.|I put the shrimp into a box.
Em bé chạy tới ôm mẹ.|The baby runs over to hug mother.`,
'Chọn vần của “hộp”.','Choose the rime in “hộp”.','op~Rime op|ôm~Rime ôm|ôp~Rime ôp|ơp~Rime ơp',2);
lesson('ơm / ơp','ƠM and ƠP',
'Đọc ơ không tròn môi, rồi khép môi ở m hoặc p. Chú ý sự chuyển động môi trong cơm – lớp. “thơm” tả mùi dễ chịu; “rợp” thường gặp trong “rợp bóng”, nghĩa là được bóng cây che phủ.',
'Say ơ with unrounded lips, then close the lips for m or p. Notice the lip movement in cơm – lớp. “thơm” describes a pleasant smell; “rợp” often appears in “rợp bóng”, meaning covered in shade.',
`cơm|cooked rice; meal|Cơm đã chín.|The rice is cooked.
thơm|fragrant|Cơm mới rất thơm.|The fresh rice smells good.
rợp|covered; shaded|Sân rợp bóng cây.|The yard is covered in tree shade.
sớm|early|Tôi đến lớp sớm.|I arrive at class early.
rơm|straw|Rơm khô ở ngoài sân.|Dry straw is in the yard.
bơm|pump|Tôi bơm lốp xe.|I pump up the tyre.
chớp|lightning; blink|Có chớp ngoài trời.|There is lightning outside.`,
`Tôi đến lớp sớm.|I arrive at class early.
Sân trường rợp bóng cây.|The schoolyard is shaded by trees.
Buổi trưa, tôi ăn cơm thơm.|At noon, I eat fragrant rice.`,
'Từ nào có vần ơm?','Which word has the rime ơm?','tôm~Shrimp|gom~Gather|cơm~Rice|lớp~Class',2);
lesson('um / up','UM and UP',
'Đọc u tròn môi, sau đó kết thúc với m hoặc p. So sánh chum – chùm và búp – giúp. “Giúp tôi với” là lời nhờ giúp; có thể thêm “Bạn có thể...” để lịch sự hơn.',
'Round the lips for u, then end with m or p. Compare chum – chùm and búp – giúp. “Giúp tôi với” asks for help; adding “Bạn có thể...” makes it more polite.',
`chum|large jar|Chum đựng nước.|The large jar holds water.
chùm|bunch; cluster|Đây là một chùm nho.|This is a bunch of grapes.
giúp|help|Bạn giúp tôi với.|Please help me.
búp|bud|Búp chè còn non.|The tea buds are young.
cúm|flu|Bạn tôi bị cúm.|My friend has the flu.
chụp|take a photo|Tôi chụp ảnh hoa.|I take a photo of flowers.
túm|gather into a bunch; grasp|Tôi túm miệng túi lại.|I gather the opening of the bag.`,
`Tôi muốn chụp ảnh chùm nho.|I want to photograph the bunch of grapes.
Bạn có thể giúp tôi không?|Can you help me?
Bạn đặt chùm nho cạnh chum.|You place the grapes beside the large jar.`,
'Từ nào có âm cuối p?','Which word ends in p?','chum~Large jar|chùm~Bunch|cúm~Flu|giúp~Help',3);
lesson('uôm','UÔM',
'uô chuyển từ âm u sang âm giữa rồi khép môi với m: uôm. Đọc liền buồm, nhuộm. Từ muỗm chỉ một loại quả gần xoài, ít gặp hơn từ buồm; mục tiêu chính là nhận diện và phát âm vần, không học thuộc tên quả hiếm.',
'uô moves from a u-like position toward a central vowel, then closes with m: uôm. Blend buồm and nhuộm smoothly. Muỗm names a mango-like fruit and is less common than buồm; the main goal is recognising and pronouncing the rime, not memorising rare fruit names.',
`buồm|sail|Buồm căng trong gió.|The sail fills with wind.
muỗm|a mango-like fruit|Cây muỗm ở trong vườn.|The muỗm tree is in the garden.
nhuộm|dye|Cô nhuộm vải màu xanh.|She dyes the fabric blue.
cánh buồm|sail|Cánh buồm màu trắng.|The sail is white.
thuyền|boat|Thuyền đi trên sông.|The boat travels on the river.
gió|wind|Gió thổi nhẹ.|The wind blows gently.
sông|river|Sông ở gần làng.|The river is near the village.`,
`Trên sông có thuyền buồm.|There is a sailing boat on the river.
Cánh buồm trắng căng trong gió.|The white sail fills with the wind.
Bên bờ có một cây muỗm.|There is a muỗm tree on the bank.`,
'Tiếng “buồm” có âm cuối nào?','Which final consonant is in “buồm”?','n~Sound n|p~Sound p|m~Sound m|t~Sound t',2);
lesson('ươm / ươp','ƯƠM and ƯƠP',
'ươ bắt đầu không tròn môi rồi chuyển về âm giữa; kết thúc bằng m hoặc p. So sánh bướm – mướp. “ướp” không có phụ âm đầu; dấu sắc nằm trên ơ. Ướp thức ăn là cho gia vị ngấm trước khi nấu.',
'ươ starts with unrounded lips and moves toward a central vowel; end with m or p. Compare bướm – mướp. “ướp” has no initial consonant; the rising mark sits above ơ. Marinating means letting seasoning soak into food before cooking.',
`bướm|butterfly|Bướm bay bên hoa.|A butterfly flies beside the flowers.
mướp|luffa gourd|Mẹ nấu canh mướp.|Mother cooks luffa soup.
ướp|marinate|Tôi ướp cá với gừng.|I marinate fish with ginger.
ươm|raise seedlings|Bà ươm cây trong vườn.|Grandmother raises seedlings in the garden.
gươm|sword|Gươm được trưng bày ở bảo tàng.|The sword is displayed in a museum.
gia vị|seasoning|Tôi thêm gia vị vào cá.|I add seasoning to the fish.
canh|soup|Canh còn nóng.|The soup is still hot.`,
`Bướm bay quanh giàn mướp.|Butterflies fly around the luffa trellis.
Tôi hái mướp để nấu canh.|I pick luffa to make soup.
Mẹ ướp cá trong bếp.|Mother marinates fish in the kitchen.`,
'Từ nào có vần ươp?','Which word has the rime ươp?','bướm~Butterfly|ươm~Raise seedlings|gươm~Sword|mướp~Luffa gourd',3);
lesson('an / at','AN and AT',
'Ghép a với n hoặc t: an, at. n kết thúc ở đầu lưỡi và cho âm mũi; t chặn hơi, không bật thêm âm. Tiếng tận cùng t chỉ mang sắc hoặc nặng. “bát” thường dùng ở miền Bắc; “chén” phổ biến ở miền Nam và nhiều nơi miền Trung.',
'Combine a with n or t: an, at. n ends at the tongue tip with nasal sound; t blocks airflow without an extra release. Syllables ending in t take only sắc or nặng. “bát” is common in the North; “chén” is common in the South and parts of Central Vietnam.',
`nhãn|longan|Tôi mua một chùm nhãn.|I buy a bunch of longans.
hát|sing|Bạn hát rất hay.|You sing very well.
bát|bowl (common in the North)|Bát ở trên bàn.|The bowl is on the table.
chén|bowl (common in the South)|Tôi lấy một cái chén.|I take a bowl.
bán|sell|Cô bán rau.|She sells vegetables.
mát|cool|Nước này mát.|This water is cool.
quạt|fan|Quạt ở cạnh bàn.|The fan is beside the table.`,
`Trên bàn có nhãn và một cái bát.|There are longans and a bowl on the table.
Tôi bật quạt cho mát.|I turn on the fan to cool down.
Bạn vừa xếp bát vừa hát.|You sing while arranging bowls.`,
'Chọn vần của “hát”.','Choose the rime in “hát”.','an~Rime an|at~Rime at|ăn~Rime ăn|ăt~Rime ăt',1);
lesson('ăn / ăt','ĂN and ĂT',
'ă ngắn hơn a: so sánh an – ăn và at – ăt. Kết thúc n bằng âm mũi, t bằng chặn hơi. Trong mắt và mặt, vần giống nhau nhưng thanh khác. Dùng ăn + món ăn: ăn cơm, ăn phở.',
'ă is shorter than a: compare an – ăn and at – ăt. End n nasally and t with stopped airflow. Mắt and mặt share a rime but differ in tone. Use ăn + food: eat rice, eat pho.',
`chăn|blanket|Chăn ở trên giường.|The blanket is on the bed.
mặt|face|Tôi rửa mặt.|I wash my face.
mắt|eye|Mắt tôi màu nâu.|My eyes are brown.
khăn|towel|Tôi lấy khăn sạch.|I take a clean towel.
cắt|cut|Tôi cắt rau.|I cut vegetables.
tắt|turn off|Tôi tắt đèn.|I turn off the light.
giặt|wash clothes|Mẹ giặt khăn.|Mother washes the towels.`,
`Sáng nay tôi rửa mặt.|This morning I wash my face.
Tôi lấy khăn rồi gấp chăn.|I take a towel and then fold the blanket.
Sau đó tôi ăn phở.|After that I eat pho.`,
'“mắt” và “mặt” khác nhau ở đâu?','How do “mắt” and “mặt” differ?','Âm đầu~Initial consonant|Nguyên âm~Vowel|Thanh điệu~Tone|Âm cuối~Final consonant',2);
lesson('ân / ât','ÂN and ÂT',
'Đọc â ngắn ở giữa miệng rồi kết thúc bằng n hoặc t. So sánh cân – cất, chân – chật. Phân biệt â với ă trong cân – căn. “chân” có thể chỉ bàn chân hoặc cả chân tùy ngữ cảnh.',
'Say a short central â, then end with n or t. Compare cân – cất and chân – chật. Distinguish â from ă in cân – căn. “chân” can mean foot or leg depending on context.',
`chân|foot; leg|Chân tôi hơi đau.|My foot hurts a little.
cân|weigh; scale|Cô cân rau.|She weighs vegetables.
vật|object|Vật này rất nhẹ.|This object is very light.
đất|earth; land|Đất trong vườn ẩm.|The garden soil is damp.
cất|put away|Tôi cất bút.|I put away the pen.
gần|near|Nhà ở gần trường.|The house is near the school.
chật|tight; cramped|Giày này chật.|These shoes are tight.`,
`Nhà tôi ở gần một vườn rau.|My house is near a vegetable garden.
Tôi bước trên đất bằng chân trần.|I walk barefoot on the soil.
Về nhà, tôi cất giày cạnh cửa.|Back home, I put my shoes beside the door.`,
'Từ nào có vần ân?','Which word has the rime ân?','chăn~Blanket|chân~Foot|cất~Put away|đất~Earth',1);
lesson('en / et','EN and ET',
'en và et dùng e, không phải ê. Đọc đen – khen rồi nét – vẹt. “hết” và “mệt” thuộc vần êt: học như nhóm đối chiếu để không nhầm dấu mũ. Dùng “Tôi mệt” để nói trạng thái, không thêm là.',
'en and et use e, not ê. Read đen – khen, then nét – vẹt. “hết” and “mệt” belong to êt: use them as contrasts to avoid confusing the circumflex. Say “Tôi mệt” to describe a state, without là.',
`khen|praise|Cô khen tôi đọc rõ.|The teacher praises my clear reading.
đen|black|Bút này màu đen.|This pen is black.
nét|stroke; feature|Tôi viết một nét.|I write one stroke.
vẹt|parrot|Vẹt ở trên cành.|The parrot is on the branch.
hết|finished; none left|Nước đã hết.|There is no water left.
len|wool|Áo này làm bằng len.|This sweater is made of wool.
quét|sweep|Tôi quét sân.|I sweep the yard.`,
`Tôi viết bằng bút đen.|I write with a black pen.
Cô khen nét chữ của tôi.|The teacher praises my handwriting.
Tôi hơi mệt nên nghỉ một chút.|I am a little tired, so I rest for a moment.`,
'Từ nào có vần et, không phải êt?','Which word has et rather than êt?','hết~Finished|mệt~Tired|vẹt~Parrot|tết~Lunar New Year',2);
lesson('on / ot, ôn / ôt','ON, OT, ÔN and ÔT',
'So sánh o mở hơn với ô khép hơn trong on – ôn, ot – ôt. Giữ phụ âm cuối: n có tiếng mũi; t chặn hơi. “con” vừa chỉ con của ai đó, vừa là loại từ cho nhiều động vật: con chim, con chó.',
'Compare the more open o with the more closed ô in on – ôn and ot – ôt. Keep the final consonant accurate: n is nasal; t stops airflow. “con” means someone’s child and is also a classifier for many animals: a bird, a dog.',
`hót|chirp|Chim hót ngoài sân.|Birds chirp in the yard.
thôn|village|Thôn này có nhiều cây.|This village has many trees.
cột|pole; column|Cột ở trước nhà.|The pole is in front of the house.
ngon|delicious|Cơm này ngon.|This rice is delicious.
ngọt|sweet|Nhãn rất ngọt.|The longans are very sweet.
bốn|four|Tôi có bốn quyển vở.|I have four notebooks.
một|one|Tôi muốn một bát cơm.|I would like one bowl of rice.`,
`Trong thôn có bốn ngôi nhà mới.|There are four new houses in the village.
Một con chim hót trên cột.|A bird chirps on a pole.
Tôi ăn nhãn ngọt ở ngoài sân.|I eat sweet longans in the yard.`,
'“cột” có vần nào?','Which rime is in “cột”?','ot~Rime ot|on~Rime on|ôn~Rime ôn|ôt~Rime ôt',3);
lesson('un / ut / ưt','UN, UT and ƯT',
'Đọc u tròn môi trong un, ut; đọc ư không tròn môi trong ưt. So sánh cún – cút và bút – mứt. t cuối tiếng không bật thêm âm. Mứt là trái cây hoặc nguyên liệu được chế biến với đường, thường gặp dịp Tết.',
'Round the lips for u in un and ut; unround them for ư in ưt. Compare cún – cút and bút – mứt. Do not release final t with an extra vowel. Mứt is fruit or other ingredients preserved with sugar, often served at Tết.',
`mứt|candied fruit|Bà mời tôi ăn mứt.|Grandmother offers me candied fruit.
cún|puppy|Cún ngủ bên cửa.|The puppy sleeps by the door.
rút|pull out; withdraw|Tôi rút bút khỏi túi.|I take the pen out of my pocket.
run|tremble|Tay tôi run vì lạnh.|My hands tremble from the cold.
đứt|break; snap|Dây đã đứt.|The string has snapped.
Tết|Vietnamese Lunar New Year|Tết này tôi về quê.|I return home this Tết.
chút|a little; a moment|Xin chờ một chút.|Please wait a moment.`,
`Tết này, tôi mang mứt về quê.|This Tết, I bring candied fruit home.
Cún chạy ra cửa đón tôi.|The puppy runs to the door to greet me.
Tôi rút bút để viết thiếp cho bà.|I take out a pen to write a card for grandmother.`,
'Từ nào có nguyên âm ư?','Which word contains the vowel ư?','cún~Puppy|bút~Pen|rút~Pull out|mứt~Candied fruit',3);
lesson('Tổng ôn A0–A1','Final Foundation Review',
'Ôn 29 chữ, sáu thanh, d/đ, g/gh, ng/ngh, các vần m/p và n/t. Dùng Tôi là, Tôi tên là, Tôi muốn và câu hỏi Ai, Gì, Ở đâu, Bao nhiêu. Bài cuối gồm nghe 20%, nhận diện âm 15%, từ vựng 20%, đọc 20%, ngữ pháp 15%, giao tiếp 10%. Đạt từ 70% và hoàn thành mọi bài bắt buộc để hoàn thành khóa học.',
'Review the 29 letters, six tones, d/đ, g/gh, ng/ngh and m/p and n/t rimes. Use Tôi là, Tôi tên là, Tôi muốn and the questions Who, What, Where and How much. The final test weights listening 20%, sound recognition 15%, vocabulary 20%, reading 20%, grammar 15% and communication 10%. Score at least 70% and finish all required lessons to complete the course.',
`buổi chiều|afternoon|Buổi chiều tôi đọc sách.|In the afternoon I read books.
sau đó|after that|Sau đó tôi về nhà.|After that I go home.
thích|like|Tôi thích học cùng bạn.|I like studying with you.
văn hóa|culture|Tôi học về văn hóa Việt Nam.|I learn about Vietnamese culture.
cùng|together; with|Tôi học cùng Lan.|I study with Lan.
cần|need|Tôi cần một quyển vở.|I need a notebook.
xin lỗi|sorry; excuse me|Xin lỗi, trường ở đâu?|Excuse me, where is the school?
tạm biệt|goodbye|Tạm biệt, hẹn gặp lại.|Goodbye, see you again.`,
`Tôi tên là Linh. Tôi là sinh viên ở Huế.|My name is Linh. I am a university student in Hue.
Buổi sáng, tôi ăn phở rồi đi học tiếng Việt cùng Ben.|In the morning, I eat pho and then go to Vietnamese class with Ben.
Buổi chiều, tôi đọc sách ở nhà. Ben đến và mang một hộp sữa.|In the afternoon, I read at home. Ben arrives and brings a carton of milk.
Tôi nói: “Cảm ơn bạn. Ngày mai tôi muốn đi chợ mua vở.”|I say, “Thank you. Tomorrow I want to go to the market to buy a notebook.”
Ben hỏi: “Chợ ở đâu?” Tôi đáp: “Chợ ở gần trường.”|Ben asks, “Where is the market?” I reply, “It is near the school.”`,
'Trong đoạn đọc, Linh là ai?','In the reading, who is Linh?', 'Giáo viên~Teacher|Sinh viên~University student|Bác sĩ~Doctor|Y tá~Nurse',1);

if(lessons.length!==48) throw new Error('Expected 48 lessons');
// Revisit the complete core set in thematic lessons, even when a word appeared earlier.
for(const [number,words] of [[24,['bố','ba','mẹ','ông','bà','anh','chị','em','con']],[28,['ăn','uống','đọc','viết','nghe','nói','đi','học']]]) {
  for(const word of words) if(!lessons[number-1].vocabulary.some(w=>w.wordVi===word)) {
    const entry=lessons.flatMap(l=>l.vocabulary).find(w=>w.wordVi===word);
    if(entry) lessons[number-1].vocabulary.push({...entry});
    else if(word==='bố') throw Error('Missing family vocabulary');
  }
}
// Chunked sentence ordering is specific to every lesson. Chunks carry their own glosses.
const orderedSentences=`Tôi~I|học~study|tiếng Việt.~Vietnamese.
Tôi~I|viết~write|chữ đ.~the letter đ.
Má~Mom|gọi~calls|tôi.~me.
Mẹ~Mother|đọc~reads|một từ.~one word.
Nhà bà~Grandmother's house|có~has|gà.~chickens.
Bố~Father|có~has|một cái ô to.~a big umbrella.
Tôi và vợ~My wife and I|đi~go to|chợ.~the market.
Em~My sibling|ngồi~sits|trên ghế.~on a chair.
Đây~This|là~is|thìa của tôi.~my spoon.
Tôi~I|lấy~take|cốc đá.~a glass of ice.
Gần ga~Near the station|có~there is|một hồ nhỏ.~a small lake.
Tôi~I|ghi~write down|tên Lan.~Lan's name.
Cái giỏ này~This basket|bao nhiêu~how much|tiền?~money?
Mẹ~Mother|mua~buys|khế.~starfruit.
Tôi~I|nhớ~miss|nhà.~home.
Tôi~I|nghe~listen to|cô đọc.~the teacher read.
Tôi~I|pha~brew|cà phê.~coffee.
Tôi~I|về~return to|quê.~my hometown.
Xe của tôi~My vehicle|ở~is|ngoài sân.~in the yard.
Chị~My older sister|pha~brews|trà.~tea.
Tôi~I|viết~write|thư cho mẹ.~a letter to mother.
Tôi~I|uống~drink|sữa.~milk.
Tôi~I|tên là~am called|Vy.~Vy.
Đây~This|là~is|mẹ tôi.~my mother.
Cô Mai~Ms Mai|là~is|giáo viên.~a teacher.
Bạn~You|tên là~are called|gì?~what?
Lan~Lan|là~is|kĩ sư.~an engineer.
Tôi~I|muốn~want to|uống nước.~drink water.
Tôi~I|đạp xe~cycle|ra chợ.~to the market.
Bà~Grandmother|gắp~picks up|rau.~vegetables.
Tôi~I|tập nói~practise speaking|chậm rãi.~slowly.
Em~My sibling|ăn~eats|kem dừa.~coconut ice cream.
Tôi~I|xếp~arrange|bát.~bowls.
Tôi~I|tìm~look for|bút.~a pen.
Bé~The baby|đeo~wears|yếm.~a bib.
Nhóm tôi~My group|gom~gathers|lá.~leaves.
Mẹ~Mother|ôm~hugs|em bé.~the baby.
Tôi~I|đến lớp~arrive at class|sớm.~early.
Bạn~You|giúp~help|tôi với.~me please.
Buồm~The sail|căng~fills|trong gió.~in the wind.
Mẹ~Mother|ướp~marinates|cá.~fish.
Tôi~I|bật~turn on|quạt.~the fan.
Tôi~I|rửa~wash|mặt.~my face.
Tôi~I|cất~put away|giày.~my shoes.
Tôi~I|viết~write|bằng bút đen.~with a black pen.
Chim~The bird|hót~chirps|trên cột.~on the pole.
Tôi~I|mang mứt~bring candied fruit|về quê.~to my hometown.
Linh~Linh|là~is|sinh viên.~a university student.`.split('\n').map(row=>row.split('|').map(c=>c.split('~')));
function mc(vi,en,choices,correct,exVi,exEn) {
  return {questionType:'MULTIPLE_CHOICE',promptVi:vi,promptEn:en,explanationVi:exVi,explanationEn:exEn,difficulty:'EASY',status:'PUBLISHED',options:choices.map(([textVi,textEn],i)=>({id:String(i+1),textVi,textEn})),correctAnswerJson:{optionIds:[String(correct+1)]}};
}
function textQuestion(type,vi,en,answers,exVi,exEn) {
  return {questionType:type,promptVi:vi,promptEn:en,explanationVi:exVi,explanationEn:exEn,difficulty:'EASY',status:'PUBLISHED',options:[],correctAnswerJson:{acceptedAnswers:answers}};
}
function reorder(vi,en,tokens) {
  return {questionType:'REORDER_SENTENCE',promptVi:vi,promptEn:en,explanationVi:tokens.map(t=>t[0]).join(' '),explanationEn:en,difficulty:'EASY',status:'PUBLISHED',options:tokens.map(([textVi,textEn],i)=>({id:String(i+1),textVi,textEn})).reverse(),correctAnswerJson:{sequence:tokens.map((_,i)=>String(i+1))}};
}
const audio=[];
function recording(key,text) {audio.push({key,text});return '/media/foundation/'+key+'.wav';}
const grammarLessons=new Set([24,25,26,27,28,48]);
const structures={
  24:['Đây là + người; danh từ + (của) + người sở hữu','This is + person; noun + (of) + possessor'],
  25:['Tôi là + vai trò. Đây là + vật. Ai? Gì?','I am + role. This is + thing. Who? What?'],
  26:['Tôi tên là + tên. Bạn tên là gì?','My name is + name. What is your name?'],
  27:['Chủ ngữ + là + danh từ; chủ ngữ + tính từ','Subject + là + noun; subject + adjective'],
  28:['Chủ ngữ + (đang / muốn) + động từ + tân ngữ','Subject + (in progress / want) + verb + object'],
  48:['Tôi là…; Tôi tên là…; Tôi muốn…; Ai? Gì? Ở đâu? Bao nhiêu?','I am…; My name is…; I want…; Who? What? Where? How much?']
};
lessons.forEach((l,i)=>{
  const n=i+1;
  l.number=n; l.estimatedMinutes=n===48?60:40;
  l.descriptionVi=l.focusVi; l.descriptionEn=l.focusEn;
  l.objectiveVi=`${l.titleVi}: đọc, nhận biết và vận dụng trong các câu của bài.`;
  l.objectiveEn=`${l.titleEn}: read, recognise and use the lesson's forms in context.`;
  l.introduction=pair(`Bài ${n}: ${l.titleVi}. ${l.focusVi.split('. ')[0]}. Hãy đọc các ví dụ rồi tự trả lời trước khi xem kết quả.`,`Lesson ${n}: ${l.titleEn}. ${l.focusEn.split('. ')[0]}. Read the examples and answer independently before checking your result.`);
  l.pattern={...pair(l.focusVi,l.focusEn),type:grammarLessons.has(n)?'GRAMMAR':'SOUND_PATTERN'};
  if(structures[n]) [l.pattern.structureVi,l.pattern.structureEn]=structures[n];
  const v=l.vocabulary;
  l.audio={audioUrl:recording('lesson-'+n,l.lines.map(x=>x.textVi).join(' ')),transcriptVi:l.lines.map(x=>x.textVi).join('\n'),transcriptEn:l.lines.map(x=>x.textEn).join('\n'),captionVi:'Giọng tổng hợp tiếng Việt. Nghe, tạm dừng và nhắc lại từng câu.',captionEn:'Vietnamese synthetic speech. Listen, pause and repeat each sentence.'};
  let q=[mc(l.questionVi,l.questionEn,l.choices,l.correct,l.focusVi,l.focusEn)];
  // Dictation and meaningful sentence completion vary with each lesson's authored vocabulary.
  const w=v[0], w2=v[1];
  q.push(textQuestion('FILL_BLANK',`Điền một từ vào câu: ${w.exampleVi.replace(w.wordVi,'___').replace(w.wordVi[0].toUpperCase()+w.wordVi.slice(1),'___')}`,`Complete the Vietnamese sentence. Meaning: ${w.exampleEn}`,[w.wordVi],w.exampleVi,w.exampleEn));
  q.push(textQuestion('TRANSLATION',`Viết từ tiếng Việt có nghĩa “${w2.meaningEn}”.`,`Write the Vietnamese word meaning “${w2.meaningEn}”.`,[w2.wordVi],w2.exampleVi,w2.exampleEn));
  q.push(reorder('Sắp xếp các phần để tạo câu đúng.','Arrange the parts to make a correct sentence.',orderedSentences[i]));
  const heard=v[Math.min(3,v.length-1)];
  q.push({...textQuestion('LISTENING','Nghe rồi viết từ hoặc cụm từ bạn nghe được.','Listen and type the word or phrase you hear.',[heard.wordVi],heard.exampleVi,heard.exampleEn),audioUrl:recording('dictation-'+n,heard.wordVi)});
  // A real matching task: pair the visible word with its syllable rime for sound units,
  // or with an authored sentence containing a gap for communication units.
  const opts=[],pairs={};
  v.slice(0,3).forEach((word,j)=>{
    const id='w'+j, right='s'+j; pairs[id]=right;
    opts.push({id,textVi:word.wordVi,textEn:word.meaningEn,side:'left'});
    opts.push({id:right,textVi:word.exampleVi.replace(new RegExp(word.wordVi,'i'),'___'),textEn:word.exampleEn,side:'right'});
  });
  l.practice={questionType:'MATCHING',promptVi:'Ghép mỗi từ với câu còn thiếu từ đó.',promptEn:'Match each word to the sentence missing that word.',options:[...opts.filter(x=>x.side==='left'),...opts.filter(x=>x.side==='right').reverse()],correctAnswer:{pairs}};
  l.questions=q;
});
// First lesson: exactly five multiple-choice questions as specified.
lessons[0].questions=[lessons[0].questions[0],
 mc('Bạn muốn học bằng tai. Chọn kỹ năng.','You want to learn using your ears. Choose the skill.',[['Nghe','Listening'],['Viết','Writing'],['Đọc','Reading'],['Nói','Speaking']],0,'Nghe là nhận lời nói bằng tai.','Listening means receiving speech through your ears.'),
 mc('Tiếng Việt sử dụng hệ chữ nào?','Which writing system does Vietnamese use?',[['Chữ Latin có thêm dấu','Latin letters with additional marks'],['Chỉ hình vẽ','Pictures only'],['Chữ Cyrillic','Cyrillic'],['Chữ Hy Lạp','Greek']],0,'Tiếng Việt dùng chữ Latin và các dấu.','Vietnamese uses Latin letters and additional marks.'),
 mc('Điều kiện nào mở bài tiếp theo?','What unlocks the next lesson?',[['Chỉ mở trang bài học','Only opening the lesson'],['Hoàn thành hoạt động bắt buộc và đạt ít nhất 70%','Complete required activities and score at least 70%'],['Chỉ nghe một câu','Only listening to one sentence'],['Bỏ qua kiểm tra','Skipping the quiz']],1,'Cần cả hoạt động bắt buộc lẫn điểm đạt.','Both required activities and a passing score are needed.'),
 mc('Khi chưa hiểu, bạn nói gì?','What do you say when you do not understand?',[['Tôi chưa hiểu.','I do not understand yet.'],['Tôi no rồi.','I am full.'],['Tôi có bút.','I have a pen.'],['Tạm biệt.','Goodbye.']],0,'Nói “Tôi chưa hiểu” để xin giải thích thêm.','Say “Tôi chưa hiểu” to ask for further explanation.')];
// Ten tone questions, including listening recognition, without exposing transcripts before submission.
const toneNames=[['ngang','level'],['sắc','rising'],['huyền','falling'],['hỏi','dipping'],['ngã','broken rising'],['nặng','low heavy']];
const toneWords=['ma','má','mà','mả','mã','mạ'];
lessons[2].questions=toneWords.map((word,i)=>mc(`“${word}” mang thanh nào?`,`Which tone is in “${word}”?`,toneNames.map(([v,e])=>['Thanh '+v,e+' tone']),i,`${word} mang thanh ${toneNames[i][0]}.`,`${word} has the ${toneNames[i][1]} tone.`));
for(let i=0;i<4;i++) lessons[2].questions.push({...mc('Nghe và chọn tiếng đúng.','Listen and select the syllable.',toneWords.map((w,j)=>[w,'Tone '+toneNames[j][1]]),i+1,`Bạn nghe “${toneWords[i+1]}”.`,`You heard “${toneWords[i+1]}”.`),questionType:'LISTENING',audioUrl:recording('tone-'+i,toneWords[i+1])});

// Final assessment: twenty items, five points each, exactly the requested six weights.
const final=[];
function section(q,topic){final.push({...q,topic,points:5});}
for(const [i,text,answer,vi,en] of [
 [1,'Tôi muốn uống nước.','nước','Người nói muốn uống gì?','What does the speaker want to drink?'],
 [2,'Tôi tên là Mai.','Mai','Người nói tên là gì?','What is the speaker’s name?'],
 [3,'Tôi có bốn cái bút.','bốn','Người nói có bao nhiêu cái bút? Viết số bằng chữ.','How many pens does the speaker have? Write the number as a Vietnamese word.'],
 [4,'Nhà tôi ở gần chợ.','chợ','Nhà người nói ở gần đâu?','What is near the speaker’s house?']]) section({...textQuestion('LISTENING',vi,en,[answer],text,'The answer is the key detail in the recording.'),audioUrl:recording('final-listening-'+i,text)},'LISTENING');
for(const idx of [2,11,44]) section(lessons[idx].questions[0],'SOUND_RECOGNITION');
for(const [vi,en,ans] of [['mẹ của mẹ','your mother’s mother','bà'],['đồ dùng để viết','a tool used for writing','bút'],['món nước có bánh phở','a noodle soup made with flat rice noodles','phở'],['hành động đưa thức ăn vào miệng','the action of taking food into your mouth','ăn']]) section(textQuestion('FILL_BLANK',`Viết từ chỉ ${vi}.`,`Write the Vietnamese word for ${en}.`,[ans],`Từ cần điền là “${ans}”.`,`The required word is “${ans}”.`),'VOCABULARY');
const reading=lessons[47].lines;
for(const [vi,en,ans] of [['Linh học ở thành phố nào?','In which city does Linh study?','Huế'],['Buổi sáng Linh ăn gì?','What does Linh eat in the morning?','phở'],['Ben mang một hộp gì?','What does Ben bring a carton of?','sữa'],['Ngày mai Linh muốn mua gì?','What does Linh want to buy tomorrow?','vở']]) section(textQuestion('FILL_BLANK',reading.map(x=>x.textVi).join('\n')+'\n\n'+vi,reading.map(x=>x.textEn).join('\n')+'\n\n'+en,[ans],`Thông tin trong đoạn: ${ans}.`,`The detail in the passage is: ${ans}.`),'READING');
section(reorder('Sắp xếp câu giới thiệu nghề nghiệp.','Arrange a sentence introducing an occupation.',[['Lan','Lan'],['là','is'],['giáo viên.','a teacher.']]),'GRAMMAR');
section(lessons[26].questions[0],'GRAMMAR');
section(reorder('Sắp xếp câu nói nhu cầu.','Arrange a sentence expressing a wish.',[['Tôi','I'],['muốn','want'],['một bát phở.','a bowl of pho.']]),'GRAMMAR');
section(mc('Bạn muốn hỏi giá quyển vở. Chọn câu phù hợp.','You want to ask the price of a notebook. Choose a suitable sentence.',[['Bạn tên là gì?','What is your name?'],['Quyển vở này bao nhiêu tiền?','How much is this notebook?'],['Bạn là ai?','Who are you?'],['Tôi mệt.','I am tired.']],1,'Hỏi giá bằng “bao nhiêu tiền”.','Ask a price using “bao nhiêu tiền”.'),'COMMUNICATION');
section(mc('Bạn muốn tìm trường học. Chọn câu hỏi.','You want to find the school. Choose the question.',[['Trường ở đâu?','Where is the school?'],['Trường tên là ai?','Incorrect question for a place'],['Tôi là trường.','I am a school.'],['Trường bao nhiêu người tên?','Incorrect question structure']],0,'Hỏi vị trí bằng “ở đâu”.','Ask a location using “ở đâu”.'),'COMMUNICATION');
lessons[47].questions=final;
const groups=[
 [1,4,'Khởi đầu với tiếng Việt','Getting Started with Vietnamese'],
 [5,9,'Nguyên âm và âm cơ bản','Essential Vowels and Sounds'],
 [10,16,'Phụ âm: b đến ngh','Consonants: B through NGH'],
 [17,22,'Phụ âm mở rộng và nguyên âm đôi','More Consonants and Vowel Sequences'],
 [23,28,'Con người và cuộc sống','People and Everyday Life'],
 [29,35,'Vần cơ bản: m/p','Basic Rimes Ending in M/P'],
 [36,38,'Vần m/p: o, ô, ơ','M/P Rimes with O, Ô and Ơ'],
 [39,41,'Vần m/p: u, uô, ươ','M/P Rimes with U, UÔ and ƯƠ'],
 [42,45,'Vần n/t và đời sống','N/T Rimes and Daily Life'],
 [46,48,'Vần mở rộng và tổng ôn A0–A1','Extended Rimes and Final Foundation Review']
];
const data={schemaVersion:1,editorial:{source:'User-supplied 48-lesson progression; original Hola Vietnamese sentences and assessments. No textbook passages, illustrations or exercises reproduced.',moduleDecision:'Ten modules preserve lesson order; split consonants and extended rimes.',corrections:['hết and mệt are êt; en/et uses đen, khen, nét, vẹt.'],audio:'Locally rendered Microsoft An Vietnamese synthetic speech; not a human/native-speaker recording.'},course:{code:'VI-A0-A1-FOUNDATION',slug:'vietnamese-foundations-a0-a1',titleVi:'Tiếng Việt nền tảng A0–A1',titleEn:'Vietnamese Foundations A0–A1',descriptionVi:'48 bài học từ chữ, âm và thanh điệu đến vần, câu và giao tiếp cơ bản. Học từng bước với từ vựng, hướng dẫn phát âm, đoạn đọc mới, luyện tập và kiểm tra.',descriptionEn:'48 lessons from letters, sounds and tones to rimes, sentences and basic communication. Learn step by step with vocabulary, pronunciation guidance, original readings, practice and assessment.',level:'A1',estimatedMinutes:1940,isFree:true,learningOutcomes:'Recognise letters and six tones; read basic syllables and sentences; use core A1 patterns and everyday vocabulary.'},modules:groups.map(([a,b,titleVi,titleEn])=>({metadata:{titleVi,titleEn,descriptionVi:`Bài ${a}–${b}: ${titleVi.toLowerCase()}.`,descriptionEn:`Lessons ${a}–${b}: ${titleEn.toLowerCase()}.`},lessons:lessons.slice(a-1,b)})),checkpoints:[{afterLesson:4,fromLesson:1,count:15},{afterLesson:22,fromLesson:10,count:20},{afterLesson:35,fromLesson:29,count:20}]};
// Content validation catches editorial omissions before the application seed touches the database.
for(const l of lessons){
  for(const q of l.questions)if(!q.promptVi||!q.promptEn||!q.explanationVi||!q.explanationEn)throw Error('Incomplete question '+l.number);
  for(const w of l.vocabulary)if(!w.exampleVi.toLowerCase().includes(w.wordVi.toLowerCase()))throw Error('Word missing from example: '+w.wordVi);
  for(const q of l.questions.filter(q=>q.questionType==='FILL_BLANK'&&q.promptVi.startsWith('Điền một từ')))if(!q.promptVi.includes('___'))throw Error('Missing gap '+l.number);
}
fs.mkdirSync(path.dirname(out),{recursive:true});
fs.writeFileSync(out,JSON.stringify(data,null,2)+'\n');
fs.writeFileSync(path.join(path.dirname(out),'foundation-audio-manifest.json'),JSON.stringify(audio,null,2)+'\n');
console.log(JSON.stringify({modules:10,lessons:lessons.length,vocabularyEntries:lessons.reduce((n,l)=>n+l.vocabulary.length,0),uniqueVocabulary:new Set(lessons.flatMap(l=>l.vocabulary.map(w=>w.wordVi))).size,questions:lessons.reduce((n,l)=>n+l.questions.length,0),audioFiles:audio.length}));
