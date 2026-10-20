import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/learning/tv123-courses.json"
MEDIA = "/media/tv123/images"
HN = "https://123vietnamese.com/wp-content/uploads/2019/01/2018_hn_{}.mp3"
HCM = "https://123vietnamese.com/wp-content/uploads/2019/01/2018_hcm_{}.mp3"
PDF_PAGES = {1: 18, 2: 25, 3: 37, 4: 43, 5: 52, 6: 71, 7: 79, 8: 87,
             9: 94, 10: 103, 11: 119, 12: 126, 13: 134, 14: 143, 15: 154, 16: 159}


def vocab(word, meaning, example, translation, word_type="Từ vựng"):
    return {
        "wordVi": word,
        "meaningEn": meaning,
        "exampleVi": example,
        "exampleEn": translation,
        "wordType": word_type,
        "pronunciation": None,
        "audioUrl": None,
        "imageUrl": None,
    }


def line(speaker, vi, en):
    return {"speaker": speaker, "textVi": vi, "textEn": en, "audioUrl": None}


def question_mcq(prompt_vi, prompt_en, choices, answer, explanation_vi, explanation_en):
    return {
        "questionType": "MULTIPLE_CHOICE",
        "promptVi": prompt_vi,
        "promptEn": prompt_en,
        "options": [
            {"id": str(i + 1), "textVi": vi, "textEn": en}
            for i, (vi, en) in enumerate(choices)
        ],
        "correctAnswerJson": {"optionIds": [str(answer + 1)]},
        "explanationVi": explanation_vi,
        "explanationEn": explanation_en,
        "difficulty": "EASY",
        "status": "PUBLISHED",
    }


def question_fill(prompt_vi, prompt_en, answers, explanation_vi, explanation_en):
    return {
        "questionType": "FILL_BLANK",
        "promptVi": prompt_vi,
        "promptEn": prompt_en,
        "options": [],
        "correctAnswerJson": {"acceptedAnswers": answers},
        "explanationVi": explanation_vi,
        "explanationEn": explanation_en,
        "difficulty": "EASY",
        "status": "PUBLISHED",
    }


def lesson(number, title_vi, title_en, objective_vi, objective_en, vocabulary,
           grammar_title_vi, grammar_title_en, pattern, explanation_vi, explanation_en,
           examples, dialogue, fill_prompt, fill_answer, fill_explanation,
           image=None, audio_key=None, minutes=35):
    image = image or f"lesson-{number:02d}"
    audio_key = audio_key or f"studentbook{number}"
    activities = [
        {
            "activityType": "TEXT",
            "titleVi": "Trang bài học trong giáo trình",
            "titleEn": "Textbook lesson page",
            "instructionVi": "Quan sát tranh, đọc tiêu đề và đoán tình huống giao tiếp.",
            "instructionEn": "Study the illustration, read the title and predict the situation.",
            "contentJson": {
                "textVi": title_vi,
                "textEn": objective_en,
                "imageUrl": f"{MEDIA}/{image}.jpg",
                "captionVi": f"Trang mở đầu Bài {number:02d} - Tiếng Việt 123, Trình độ A.",
                "captionEn": f"Opening page of Lesson {number:02d} - Tieng Viet 123, Level A.",
                "sourcePage": PDF_PAGES[number],
            },
        },
        {
            "activityType": "AUDIO",
            "titleVi": "Nghe giọng Bắc",
            "titleEn": "Northern Vietnamese audio",
            "instructionVi": "Nghe toàn bài một lần, sau đó nghe lại và nhắc theo từng lượt lời.",
            "instructionEn": "Listen once for meaning, then replay and shadow each turn.",
            "contentJson": {"audioUrl": HN.format(audio_key), "accent": "Hà Nội", "sourceLesson": number},
        },
        {
            "activityType": "AUDIO",
            "titleVi": "Nghe giọng Nam",
            "titleEn": "Southern Vietnamese audio",
            "instructionVi": "Nghe và nhận biết những điểm khác nhau về phát âm giữa hai vùng.",
            "instructionEn": "Listen and notice pronunciation differences between the two accents.",
            "contentJson": {"audioUrl": HCM.format(audio_key), "accent": "TP. Hồ Chí Minh", "sourceLesson": number},
        },
        {
            "activityType": "VOCABULARY",
            "titleVi": "Từ vựng trọng tâm",
            "titleEn": "Core vocabulary",
            "instructionVi": "Đọc, nghe phát âm hỗ trợ và ghi nhớ từ trong câu.",
            "instructionEn": "Read, use pronunciation support and learn each word in context.",
            "contentJson": {"items": vocabulary},
        },
        {
            "activityType": "DIALOGUE",
            "titleVi": "Hội thoại tình huống",
            "titleEn": "Situational dialogue",
            "instructionVi": "Đổi vai và luyện nói. Dùng nút nghe để kiểm tra từng câu.",
            "instructionEn": "Switch roles and practise. Use the audio button to check each line.",
            "contentJson": {"lines": dialogue},
        },
    ]
    meanings = [(vocabulary[i]["wordVi"], vocabulary[i]["meaningEn"]) for i in range(min(3, len(vocabulary)))]
    while len(meanings) < 3:
        meanings.append(("khác", "other"))
    questions = [
        question_mcq(
            f"Từ nào có nghĩa là “{vocabulary[0]['meaningEn']}”?",
            f"Which word means “{vocabulary[0]['meaningEn']}”?",
            meanings,
            0,
            f"“{vocabulary[0]['wordVi']}” có nghĩa là “{vocabulary[0]['meaningEn']}”.",
            f"“{vocabulary[0]['wordVi']}” means “{vocabulary[0]['meaningEn']}”.",
        ),
        question_mcq(
            "Mẫu câu trọng tâm của bài là gì?",
            "Which pattern is central to this lesson?",
            [(pattern, pattern), ("Ai + cũng + không", "Ai + cũng + không"), ("Đã + sẽ + đang", "Đã + sẽ + đang")],
            0,
            explanation_vi,
            explanation_en,
        ),
        question_fill(fill_prompt, f"Complete the Vietnamese sentence: {fill_prompt}", [fill_answer],
                      fill_explanation, "Complete the sentence with the lesson's target word."),
        question_mcq(
            "Hoạt động nào phù hợp nhất sau khi nghe hội thoại?",
            "What is the best follow-up after listening to the dialogue?",
            [("Đổi vai và nói lại", "Switch roles and repeat"), ("Bỏ qua phần nói", "Skip speaking"), ("Chỉ chép từng từ", "Only copy words")],
            0,
            "Đổi vai giúp luyện phản xạ trong đúng tình huống của bài.",
            "Role switching builds speaking fluency in the lesson situation.",
        ),
    ]
    return {
        "number": number,
        "titleVi": title_vi,
        "titleEn": title_en,
        "descriptionVi": objective_vi,
        "descriptionEn": objective_en,
        "objectiveVi": objective_vi,
        "objectiveEn": objective_en,
        "estimatedMinutes": minutes,
        "activities": activities,
        "grammar": {
            "titleVi": grammar_title_vi,
            "titleEn": grammar_title_en,
            "structurePattern": pattern,
            "structurePatternEn": pattern,
            "explanationVi": explanation_vi,
            "explanationEn": explanation_en,
            "commonMistakesVi": "Giữ đúng trật tự từ và luyện cả câu thay vì dịch từng từ.",
            "commonMistakesEn": "Keep Vietnamese word order and practise the whole sentence rather than translating word by word.",
            "examples": [
                {"vietnameseText": vi, "translation": en, "explanation": None}
                for vi, en in examples
            ],
        },
        "questions": questions,
    }


def pronunciation_lesson(number, title_vi, title_en, objective_vi, objective_en, image, audio_pairs, examples):
    activities = [{
        "activityType": "TEXT",
        "titleVi": "Bảng minh họa từ giáo trình",
        "titleEn": "Illustrated textbook chart",
        "instructionVi": "Quan sát chữ, tranh và đọc to các ví dụ.",
        "instructionEn": "Study the letters and illustrations, then read the examples aloud.",
        "contentJson": {
            "textVi": title_vi,
            "textEn": objective_en,
            "imageUrl": f"{MEDIA}/{image}.jpg",
            "captionVi": "Trích trang phát âm của Tiếng Việt 123, Trình độ A.",
            "captionEn": "From the pronunciation section of Tieng Viet 123, Level A.",
        },
    }]
    for accent, url in audio_pairs:
        activities.append({
            "activityType": "AUDIO",
            "titleVi": f"Nghe {accent}",
            "titleEn": f"Listen - {accent}",
            "instructionVi": "Nghe, dừng sau mỗi nhóm âm và nhắc lại hai lần.",
            "instructionEn": "Pause after each sound group and repeat twice.",
            "contentJson": {"audioUrl": url, "accent": accent},
        })
    activities.append({
        "activityType": "VOCABULARY",
        "titleVi": "Ví dụ phát âm",
        "titleEn": "Pronunciation examples",
        "instructionVi": "Đọc các ví dụ ngắn và chú ý hình dạng chữ.",
        "instructionEn": "Read the short examples and notice each letter form.",
        "contentJson": {"items": examples},
    })
    return {
        "number": number,
        "titleVi": title_vi,
        "titleEn": title_en,
        "descriptionVi": objective_vi,
        "descriptionEn": objective_en,
        "objectiveVi": objective_vi,
        "objectiveEn": objective_en,
        "estimatedMinutes": 25,
        "activities": activities,
        "questions": [
            question_mcq("Cách luyện nghe phù hợp là gì?", "What is a useful listening routine?", [("Nghe - dừng - nhắc lại", "Listen - pause - repeat"), ("Chỉ nhìn chữ", "Only look at letters"), ("Bỏ qua thanh điệu", "Ignore tones")], 0, "Nghe, dừng và nhắc lại giúp nối âm thanh với chữ viết.", "Listen, pause and repeat to connect sounds with spelling."),
            question_fill("Điền từ: Nghe, dừng và ___ lại.", "Complete: Listen, pause and ___.", ["nhắc"], "Cụm đúng là “nhắc lại”.", "The phrase means repeat."),
            question_mcq("Tài liệu có audio theo giọng nào?", "Which accents are available?", [("Giọng Bắc và giọng Nam", "Northern and Southern"), ("Chỉ giọng Bắc", "Northern only"), ("Chỉ giọng Nam", "Southern only")], 0, "Mỗi phần có bản ghi giọng Hà Nội và TP. Hồ Chí Minh.", "Each section has Hanoi and Ho Chi Minh City recordings."),
            question_mcq("Khi luyện phát âm nên làm gì?", "What should you do when practising pronunciation?", [("Đọc cả ví dụ thành tiếng", "Read examples aloud"), ("Chỉ dịch nghĩa", "Only translate"), ("Không cần nghe", "Do not listen")], 0, "Đọc thành tiếng giúp hình thành thói quen phát âm.", "Reading aloud builds pronunciation habits."),
        ],
    }


def course(code, slug, title_vi, title_en, description_vi, description_en, level, thumbnail, lessons):
    return {
        "course": {
            "code": code,
            "slug": slug,
            "titleVi": title_vi,
            "titleEn": title_en,
            "descriptionVi": description_vi,
            "descriptionEn": description_en,
            "level": level,
            "estimatedMinutes": sum(x["estimatedMinutes"] for x in lessons),
            "thumbnailUrl": f"{MEDIA}/{thumbnail}.jpg",
            "learningOutcomes": f"{description_vi}\n{description_en}",
            "isFree": True,
        },
        "modules": [{
            "metadata": {
                "titleVi": title_vi,
                "titleEn": title_en,
                "descriptionVi": description_vi,
                "descriptionEn": description_en,
            },
            "lessons": lessons,
        }],
    }


pronunciation = [
    pronunciation_lesson(1, "Bảng chữ cái tiếng Việt", "Vietnamese alphabet", "Nhận biết 29 chữ cái và đọc các ví dụ quen thuộc.", "Recognise the 29 Vietnamese letters and read familiar examples.", "alphabet", [("giọng Bắc", HN.format("bang_chu_cai")), ("giọng Nam", HCM.format("bang_chu_cai"))], [vocab("a", "letter A", "áo", "shirt"), vocab("ă", "letter Ă", "ăn", "eat"), vocab("đ", "letter Đ", "đủ", "enough")]),
    pronunciation_lesson(2, "Nguyên âm đơn", "Monophthongs", "Phân biệt các nguyên âm đơn qua từ mẫu và tranh.", "Distinguish Vietnamese monophthongs through model words and pictures.", "vowels", [("giọng Bắc", HN.format("nguyen_am_don")), ("giọng Nam", HCM.format("nguyen_am_don"))], [vocab("a", "a sound", "ba", "three"), vocab("ơ", "ơ sound", "chợ", "market"), vocab("ư", "ư sound", "mưa", "rain")]),
    pronunciation_lesson(3, "Phụ âm đơn và phụ âm đôi", "Single and compound consonants", "Nhận biết phụ âm đầu đơn, phụ âm ghép và cách kết hợp với nguyên âm.", "Recognise single and compound initial consonants and combine them with vowels.", "consonants", [("phụ âm đơn - giọng Bắc", HN.format("phu_am_don")), ("phụ âm đôi - giọng Bắc", HN.format("phu_am_doi")), ("phụ âm đơn - giọng Nam", HCM.format("phu_am_don")), ("phụ âm đôi - giọng Nam", HCM.format("phu_am_doi"))], [vocab("ph", "ph sound", "phở", "pho"), vocab("th", "th sound", "thư", "letter"), vocab("ng", "ng sound", "ngủ", "sleep")]),
    pronunciation_lesson(4, "Sáu thanh điệu", "Six tones", "Nghe và phân biệt ngang, huyền, sắc, hỏi, ngã và nặng.", "Hear and distinguish the six Vietnamese tones.", "tones", [("giọng Bắc", HN.format("thanh_dieu_tieng_viet")), ("giọng Nam", HCM.format("thanh_dieu_tieng_viet"))], [vocab("ma", "level tone", "ma", "ghost"), vocab("má", "rising tone", "má", "mother/cheek"), vocab("mạ", "heavy tone", "mạ", "rice seedling")]),
]


lessons = [
    lesson(1, "Tên tôi là Peter", "My name is Peter", "Chào hỏi, giới thiệu tên và hỏi quốc tịch.", "Greet people, introduce your name and ask about nationality.", [vocab("chào", "hello", "Chào anh.", "Hello."), vocab("tên", "name", "Tên tôi là Peter.", "My name is Peter."), vocab("người", "person / nationality marker", "Tôi là người Nhật Bản.", "I am Japanese."), vocab("nước", "country", "Chị là người nước nào?", "What country are you from?"), vocab("rất vui được gặp", "nice to meet", "Rất vui được gặp anh.", "Nice to meet you.")], "Giới thiệu và hỏi quốc tịch", "Introducing yourself and asking nationality", "Tôi tên là + tên; Tôi là người + quốc gia", "Dùng “tên tôi là/tôi tên là” để giới thiệu; dùng “người nước nào?” để hỏi quốc tịch.", "Use tên tôi là/tôi tên là to introduce yourself and người nước nào to ask nationality.", [("Tên tôi là Yumi.", "My name is Yumi."), ("Tôi là người Anh.", "I am British.")], [line("Peter", "Chào chị. Xin lỗi, xin hỏi chị tên là gì?", "Hello. Excuse me, what is your name?"), line("Yumi", "Tôi tên là Yumi. Còn anh?", "My name is Yumi. And you?"), line("Peter", "Tôi tên là Peter. Rất vui được gặp chị.", "My name is Peter. Nice to meet you.")], "Tên tôi ___ Peter.", "là", "Dùng động từ “là” để nối tên với chủ thể."),
    lesson(2, "Tôi là giáo viên", "I am a teacher", "Hỏi và nói nghề nghiệp; xác nhận thông tin bằng “phải không”.", "Ask and state occupations; confirm information with phải không.", [vocab("giáo viên", "teacher", "Tôi là giáo viên.", "I am a teacher."), vocab("bác sĩ", "doctor", "Chị Lan là bác sĩ.", "Lan is a doctor."), vocab("dạy", "teach", "Tôi dạy ở trung tâm.", "I teach at a centre."), vocab("làm việc", "work", "Anh làm việc ở đâu?", "Where do you work?"), vocab("phải không", "is that right", "Anh là người Hàn Quốc phải không?", "You are Korean, right?")], "Nghề nghiệp với “là”", "Occupations with là", "Chủ ngữ + là + nghề nghiệp; Có phải ... không?", "“Là” nối chủ ngữ với nghề nghiệp. “Có phải...không?” dùng để xác nhận.", "Use là between a subject and an occupation. Use có phải...không to confirm.", [("Tôi là bác sĩ.", "I am a doctor."), ("Có phải anh là giáo viên không?", "Are you a teacher?")], [line("Kim", "Chào chị. Tôi tên là Kim.", "Hello. My name is Kim."), line("Thu", "Chào anh. Rất vui được gặp anh.", "Hello. Nice to meet you."), line("Kim", "Chị làm nghề gì?", "What do you do?"), line("Thu", "Tôi là giáo viên.", "I am a teacher.")], "Tôi ___ giáo viên.", "là", "“Là” đứng trước danh từ chỉ nghề nghiệp."),
    lesson(3, "Anh bao nhiêu tuổi?", "How old are you?", "Hỏi tuổi, hỏi tình trạng và trả lời câu hỏi có/không.", "Ask age and status questions and answer yes/no questions.", [vocab("tuổi", "years old", "Anh bao nhiêu tuổi?", "How old are you?"), vocab("khỏe", "well / healthy", "Anh có khỏe không?", "Are you well?"), vocab("cảm ơn", "thank you", "Cảm ơn anh.", "Thank you."), vocab("đi làm", "go to work", "Tôi đi làm.", "I go to work."), vocab("bình thường", "normal", "Tôi vẫn bình thường.", "I am still fine.")], "Hỏi tuổi và câu hỏi có/không", "Age and yes/no questions", "Chủ ngữ + bao nhiêu tuổi?; Chủ ngữ + có + ... + không?", "Dùng “bao nhiêu tuổi” để hỏi tuổi; đặt “có...không” quanh vị ngữ để hỏi có/không.", "Use bao nhiêu tuổi for age; frame the predicate with có...không for yes/no questions.", [("Anh bao nhiêu tuổi?", "How old are you?"), ("Anh có khỏe không?", "Are you well?")], [line("Lan", "Anh có khỏe không?", "Are you well?"), line("Jim", "Cảm ơn chị. Tôi khỏe.", "Thank you. I am well."), line("Lan", "Cháu anh bao nhiêu tuổi?", "How old is your nephew?"), line("Jim", "Cháu 12 tuổi.", "He is 12 years old.")], "Anh bao nhiêu ___?", "tuổi", "Cụm hỏi tuổi là “bao nhiêu tuổi”."),
    lesson(4, "Nhà của anh rất đẹp!", "Your house is beautiful", "Miêu tả nhà và dùng đại từ chỉ định.", "Describe a home and use demonstratives.", [vocab("nhà", "house", "Nhà này rất đẹp.", "This house is beautiful."), vocab("phòng khách", "living room", "Đây là phòng khách.", "This is the living room."), vocab("phòng ngủ", "bedroom", "Kia là phòng ngủ.", "That is the bedroom."), vocab("bếp", "kitchen", "Bếp ở bên kia.", "The kitchen is over there."), vocab("rộng", "spacious", "Nhà rất rộng.", "The house is spacious.")], "Đại từ và tính từ chỉ định", "Demonstratives", "Đây/Kia là + danh từ; Danh từ + này/kia/ấy", "Đây và kia đứng độc lập; này, kia, ấy theo sau danh từ.", "Đây and kia can stand alone; này, kia and ấy follow a noun.", [("Đây là phòng khách.", "This is the living room."), ("Quyển sách này rất hay.", "This book is very good.")], [line("Lan", "Chào anh. Dạo này anh có khỏe không?", "Hello. How have you been?"), line("Mike", "Tôi khỏe. Cảm ơn anh.", "I am well. Thank you."), line("Lan", "Nhà này của anh rất đẹp và rộng.", "Your house is beautiful and spacious."), line("Mike", "Đây là phòng khách, kia là phòng ngủ.", "This is the living room; that is the bedroom.")], "Đây ___ phòng khách.", "là", "Dùng “Đây là” để giới thiệu vật hoặc nơi ở gần."),
    lesson(5, "Cái này bao nhiêu tiền?", "How much is this?", "Hỏi giá, màu sắc và mua đồ trong cửa hàng.", "Ask prices and colours and buy items in a shop.", [vocab("bao nhiêu tiền", "how much", "Cái này bao nhiêu tiền?", "How much is this?"), vocab("màu", "colour", "Chị mua màu gì?", "What colour are you buying?"), vocab("đắt", "expensive", "Cái mũ này đắt.", "This hat is expensive."), vocab("rẻ", "cheap", "Áo này rất rẻ.", "This shirt is cheap."), vocab("đồng", "Vietnamese dong", "Hai trăm nghìn đồng.", "Two hundred thousand dong."), vocab("mua", "buy", "Tôi muốn mua một cái mũ.", "I want to buy a hat.")], "Hỏi giá và yêu cầu mua hàng", "Prices and shopping requests", "Cái này bao nhiêu tiền?; Cho tôi + số lượng + danh từ", "Dùng “bao nhiêu tiền” để hỏi giá và “cho tôi” khi gọi hoặc mua món.", "Use bao nhiêu tiền to ask price and cho tôi to request an item.", [("Cái này bao nhiêu tiền?", "How much is this?"), ("Cho tôi một cái mũ.", "Give me one hat, please.")], [line("Khách", "Anh ơi, cho tôi xem cái mũ kia.", "Excuse me, please show me that hat."), line("Người bán", "Vâng. Cái mũ đây.", "Certainly. Here is the hat."), line("Khách", "Cái mũ này bao nhiêu tiền?", "How much is this hat?"), line("Người bán", "Mũ này hai trăm nghìn đồng.", "This hat is two hundred thousand dong.")], "Cái này bao nhiêu ___?", "tiền", "Cụm hỏi giá là “bao nhiêu tiền”."),
    lesson(6, "Bây giờ là mấy giờ?", "What time is it now?", "Hỏi giờ, nói giờ hơn/kém và thời điểm diễn ra hoạt động.", "Ask and tell time, including minutes past/to and event times.", [vocab("bây giờ", "now", "Bây giờ là mấy giờ?", "What time is it now?"), vocab("mấy giờ", "what time", "Mấy giờ bắt đầu học?", "What time does class start?"), vocab("bắt đầu", "start", "Lớp bắt đầu lúc 7 giờ.", "Class starts at 7."), vocab("kết thúc", "finish", "Cuộc họp kết thúc lúc 9 giờ.", "The meeting ends at 9."), vocab("phút", "minute", "Bảy giờ mười lăm phút.", "Seven fifteen.")], "Cách hỏi và nói thời gian", "Asking and telling time", "Mấy giờ rồi?; Số giờ + giờ + số phút", "Có thể nói giờ chính xác hoặc dùng “hơn/kém” theo ngữ cảnh.", "Tell exact time or use hơn/kém for minutes past/to.", [("Bây giờ là mấy giờ?", "What time is it now?"), ("Bảy giờ kém mười lăm.", "A quarter to seven.")], [line("Lan", "Chị An ơi! Bây giờ là mấy giờ?", "An, what time is it now?"), line("An", "Bây giờ là 8 giờ rưỡi.", "It is 8:30."), line("Lan", "Mấy giờ cuộc họp sẽ kết thúc?", "When will the meeting finish?"), line("An", "9 giờ 10 phút.", "At 9:10.")], "Bây giờ là mấy ___?", "giờ", "Cụm hỏi thời gian là “mấy giờ”."),
    lesson(7, "Hôm nay là ngày bao nhiêu?", "What is today's date?", "Nói ngày, tháng, năm và kế hoạch sắp tới.", "Say dates and talk about upcoming plans.", [vocab("ngày", "day / date", "Hôm nay là ngày 15.", "Today is the 15th."), vocab("tháng", "month", "Sinh nhật vào tháng 5.", "The birthday is in May."), vocab("năm", "year", "Năm nay là năm 2018.", "This year is 2018."), vocab("sắp", "soon / about to", "Sắp đến sinh nhật tôi.", "My birthday is coming soon."), vocab("tuần sau", "next week", "Tuần sau tôi sẽ đi.", "I will go next week."), vocab("tặng", "give as a gift", "Tôi tặng bạn một quyển sách.", "I give my friend a book.")], "Ngày tháng năm và tương lai", "Dates and future plans", "Ngày + số + tháng + số + năm + số; sắp/sẽ + động từ", "“Sắp” nhấn mạnh việc rất gần; “sẽ” nói chung về tương lai.", "Sắp means something is imminent; sẽ marks a future action more generally.", [("Hôm nay là ngày bao nhiêu?", "What is today's date?"), ("Tôi sẽ làm bánh vào tuần sau.", "I will make a cake next week.")], [line("Minh", "Hôm nay là ngày bao nhiêu?", "What is today's date?"), line("Lan", "Hôm nay là ngày 15 tháng 5.", "Today is May 15."), line("Minh", "Sắp đến sinh nhật tôi.", "My birthday is coming soon."), line("Lan", "Tôi sẽ làm bánh và tặng bạn một món quà.", "I will make a cake and give you a gift.")], "Hôm nay là ___ bao nhiêu?", "ngày", "Dùng “ngày bao nhiêu” để hỏi ngày trong tháng."),
    lesson(8, "Anh đã có nhà riêng chưa?", "Do you have your own house yet?", "Hỏi trải nghiệm với đã...chưa và nói về nơi ở.", "Ask about completed experiences with đã...chưa and discuss housing.", [vocab("đã...chưa", "yet", "Anh đã có nhà riêng chưa?", "Do you have your own house yet?"), vocab("dạo này", "recently", "Dạo này anh thế nào?", "How have you been recently?"), vocab("vẫn", "still", "Tôi vẫn khỏe.", "I am still well."), vocab("nhà hàng", "restaurant", "Tôi ăn ở nhà hàng.", "I eat at a restaurant."), vocab("công ty", "company", "Công ty ở gần đây.", "The company is nearby."), vocab("lâu", "long time", "Lâu lắm rồi.", "It has been a long time.")], "Cấu trúc đã...chưa", "The đã...chưa pattern", "Chủ ngữ + đã + động từ + chưa?", "Dùng để hỏi một việc đã xảy ra tính đến hiện tại hay chưa.", "Use this pattern to ask whether something has happened by now.", [("Anh đã có nhà riêng chưa?", "Do you have your own house yet?"), ("Chưa, tôi chưa ăn.", "No, I have not eaten yet.")], [line("Minh", "Lâu lắm không gặp. Dạo này anh thế nào?", "Long time no see. How are you?"), line("Conan", "Tôi khỏe. Còn anh?", "I am well. And you?"), line("Minh", "Anh đã có nhà riêng chưa?", "Do you have your own house yet?"), line("Conan", "Chưa. Tôi vẫn sống cùng bố mẹ.", "Not yet. I still live with my parents.")], "Anh ___ có nhà riêng chưa?", "đã", "Cấu trúc hoàn chỉnh là “đã...chưa”."),
    lesson(9, "Khi rỗi, chị thường làm gì?", "What do you usually do in your free time?", "Nói về thói quen, sở thích và thời lượng.", "Discuss habits, interests and duration.", [vocab("rảnh rỗi", "free", "Khi rảnh rỗi, tôi đọc sách.", "I read when I am free."), vocab("thường", "usually", "Tôi thường nghe nhạc.", "I usually listen to music."), vocab("phim hành động", "action film", "Tôi thích phim hành động.", "I like action films."), vocab("hy vọng", "hope", "Tôi hy vọng sẽ có cơ hội.", "I hope I will have a chance."), vocab("bao lâu", "how long", "Anh làm việc ở đây bao lâu rồi?", "How long have you worked here?"), vocab("chưa bao giờ", "never", "Tôi chưa bao giờ đi Huế.", "I have never been to Hue.")], "Tần suất và thời lượng", "Frequency and duration", "thường + động từ; ... bao lâu rồi?", "Trạng từ tần suất đứng trước động từ; “bao lâu” hỏi khoảng thời gian.", "Frequency adverbs precede the verb; bao lâu asks about duration.", [("Tôi thường nghe nhạc.", "I usually listen to music."), ("Anh làm ở đây bao lâu rồi?", "How long have you worked here?")], [line("Lan", "Khi rỗi, chị thường làm gì?", "What do you usually do when free?"), line("Mai", "Tôi thường nghe nhạc, xem phim hoặc đi chơi với bạn.", "I usually listen to music, watch films or go out with friends."), line("Lan", "Chị thích loại phim gì?", "What kind of films do you like?"), line("Mai", "Tôi thích phim hành động.", "I like action films.")], "Tôi ___ nghe nhạc khi rảnh.", "thường", "“Thường” diễn tả thói quen."),
    lesson(10, "Anh đi thẳng đường này", "Go straight on this road", "Hỏi đường, chỉ hướng, nói phương tiện và khoảng cách.", "Ask for directions, give routes, name transport and discuss distance.", [vocab("đi thẳng", "go straight", "Anh đi thẳng đường này.", "Go straight on this road."), vocab("rẽ trái", "turn left", "Rẽ trái ở ngã tư.", "Turn left at the crossroads."), vocab("rẽ phải", "turn right", "Sau đó rẽ phải.", "Then turn right."), vocab("bờ hồ", "lakeside", "Nhà ở gần bờ hồ.", "The house is near the lake."), vocab("đi bộ", "walk", "Tôi đi bộ năm phút.", "I walk for five minutes."), vocab("bao xa", "how far", "Từ đây đến đó bao xa?", "How far is it from here?")], "Chỉ đường và khoảng cách", "Directions and distance", "Từ A đến B bao xa?; đi bằng + phương tiện", "Dùng “từ...đến” cho hai điểm và “bằng” trước phương tiện.", "Use từ...đến for endpoints and bằng before a means of transport.", [("Từ đây đến bờ hồ bao xa?", "How far is it from here to the lake?"), ("Tôi đi bằng taxi.", "I go by taxi.")], [line("Khách", "Chị có biết trung tâm 123Vietnamese ở đâu không?", "Do you know where the centre is?"), line("Người địa phương", "Anh đi thẳng đường này rồi rẽ trái.", "Go straight and then turn left."), line("Khách", "Từ đây đến đó bao lâu?", "How long does it take?"), line("Người địa phương", "Đi bộ khoảng 5 phút.", "About five minutes on foot.")], "Anh đi ___ đường này.", "thẳng", "Cụm chỉ hướng là “đi thẳng”."),
    lesson(11, "Anh có thể nói tiếng Việt được không?", "Can you speak Vietnamese?", "Nói về khả năng, xin phép và mức độ hiểu.", "Talk about ability, ask permission and discuss understanding.", [vocab("có thể", "can", "Tôi có thể nói tiếng Việt.", "I can speak Vietnamese."), vocab("tìm hiểu", "learn about", "Tôi muốn tìm hiểu văn hóa Việt Nam.", "I want to learn about Vietnamese culture."), vocab("ý kiến", "opinion", "Ý kiến của anh rất hay.", "Your opinion is good."), vocab("thú vị", "interesting", "Bài học rất thú vị.", "The lesson is interesting."), vocab("văn hóa", "culture", "Tôi thích văn hóa Việt Nam.", "I like Vietnamese culture."), vocab("bảo tàng", "museum", "Chúng tôi đi bảo tàng.", "We go to the museum.")], "Khả năng với có thể/được", "Ability with có thể/được", "Chủ ngữ + có thể + động từ + được không?", "Dùng “có thể” hoặc “được” để nói khả năng; kết hợp “được không” để hỏi.", "Use có thể or được for ability, and được không to form a question.", [("Anh có thể nói tiếng Việt được không?", "Can you speak Vietnamese?"), ("Tôi không ăn được món này.", "I cannot eat this dish.")], [line("Peter", "Anh có thể nói tiếng Việt được không?", "Can you speak Vietnamese?"), line("Nam", "Vâng. Tôi nói được một chút.", "Yes. I can speak a little."), line("Peter", "Anh đã học tiếng Việt bao lâu rồi?", "How long have you studied Vietnamese?"), line("Nam", "Hai năm rồi.", "For two years.")], "Tôi nói ___ một chút.", "được", "“Nói được” diễn tả khả năng nói."),
    lesson(12, "Quyển sách ở trên bàn", "The book is on the table", "Miêu tả vị trí và sự hiện hữu của đồ vật.", "Describe locations and say that objects exist in a place.", [vocab("trên", "on / above", "Sách ở trên bàn.", "The book is on the table."), vocab("trong", "inside", "Mèo ở trong hộp.", "The cat is in the box."), vocab("dưới", "under", "Bóng ở dưới bàn.", "The ball is under the table."), vocab("giữa", "between / middle", "Tủ ở giữa hai bàn.", "The cabinet is between two tables."), vocab("bên cạnh", "next to", "Ghế ở bên cạnh bàn.", "The chair is next to the table."), vocab("học viên", "student", "Trong lớp có học viên mới.", "There is a new student in class.")], "Giới từ chỉ vị trí", "Prepositions of place", "Danh từ + ở + vị trí; Ở + nơi chốn + có + danh từ", "Đặt “ở” trước cụm vị trí; dùng “có” để giới thiệu sự hiện hữu.", "Use ở before the location phrase and có to introduce existence.", [("Quyển sách ở trên bàn.", "The book is on the table."), ("Trong lớp có mười học viên.", "There are ten students in class.")], [line("Mai", "Chị có biết quyển sách tiếng Việt của em ở đâu không?", "Do you know where my Vietnamese book is?"), line("Lan", "Ở trên bàn, trong phòng khách.", "It is on the table in the living room."), line("Mai", "Em đang học bài bao nhiêu hả chị?", "Which lesson are you studying?"), line("Lan", "Em học xong bài 10 rồi ạ.", "I have finished lesson 10.")], "Quyển sách ở ___ bàn.", "trên", "“Trên bàn” chỉ vị trí ở bề mặt phía trên."),
    lesson(13, "Cho chúng tôi xem thực đơn", "Please show us the menu", "Gọi món, dùng từ chỉ loại và diễn tả lựa chọn.", "Order food, use classifiers and express choices.", [vocab("thực đơn", "menu", "Cho tôi xem thực đơn.", "Please show me the menu."), vocab("món", "dish", "Món này rất ngon.", "This dish is delicious."), vocab("nem rán", "fried spring rolls", "Cho một đĩa nem rán.", "One plate of spring rolls, please."), vocab("nước chanh", "lemon drink", "Tôi uống nước chanh.", "I drink lemon juice."), vocab("sa lát", "salad", "Cho một đĩa sa lát.", "One plate of salad, please."), vocab("chè sen", "lotus sweet soup", "Tôi muốn chè sen.", "I would like lotus sweet soup.")], "Gọi món và từ chỉ loại", "Ordering and classifiers", "Cho + số lượng + từ chỉ loại + danh từ", "Chọn từ chỉ loại phù hợp như đĩa, bát, cốc hoặc chai.", "Choose a suitable classifier such as plate, bowl, glass or bottle.", [("Cho tôi một đĩa nem rán.", "One plate of spring rolls, please."), ("Món nào cũng ngon.", "Every dish is delicious.")], [line("Khách", "Cho chúng tôi xem thực đơn.", "Please show us the menu."), line("Phục vụ", "Vâng. Đây ạ.", "Certainly. Here it is."), line("Khách", "Cho tôi một đĩa nem rán và một cốc nước chanh.", "One plate of spring rolls and one glass of lemon drink."), line("Phục vụ", "Anh có muốn dùng tráng miệng không?", "Would you like dessert?")], "Cho tôi một ___ nem rán.", "đĩa", "Nem rán thường được gọi theo “đĩa”."),
    lesson(14, "Hôm nay trời nắng và nóng", "Today is sunny and hot", "Hỏi thời tiết, nói về mùa và nối các ý tương phản.", "Ask about weather, discuss seasons and connect contrasting ideas.", [vocab("thời tiết", "weather", "Thời tiết hôm nay thế nào?", "How is the weather today?"), vocab("nắng", "sunny", "Trời nắng.", "It is sunny."), vocab("nóng", "hot", "Hôm nay rất nóng.", "It is hot today."), vocab("mưa phùn", "drizzle", "Mùa xuân có mưa phùn.", "There is drizzle in spring."), vocab("mát", "cool", "Mùa thu rất mát.", "Autumn is cool."), vocab("tuy nhiên", "however", "Trời nắng, tuy nhiên không nóng.", "It is sunny; however, it is not hot.")], "Hỏi thời tiết và nối ý", "Weather questions and connectors", "Hôm nay thời tiết thế nào?; A, nhưng/tuy nhiên B", "Dùng “thế nào” để hỏi tình trạng và “nhưng/tuy nhiên” cho ý đối lập.", "Use thế nào to ask conditions and nhưng/tuy nhiên for contrast.", [("Hôm nay thời tiết thế nào?", "How is the weather today?"), ("Trời nắng nhưng không nóng.", "It is sunny but not hot.")], [line("John", "Hôm nay thời tiết thế nào?", "How is the weather today?"), line("Minh", "Hôm nay trời nắng và nóng.", "It is sunny and hot today."), line("John", "Mùa nào ở Hà Nội đẹp nhất?", "Which season is nicest in Hanoi?"), line("Minh", "Tôi thích mùa thu vì trời mát và khô.", "I like autumn because it is cool and dry.")], "Hôm nay trời nắng ___ nóng.", "và", "“Và” nối hai đặc điểm cùng hướng."),
    lesson(15, "Cho tôi một phòng đơn", "A single room, please", "Đặt phòng khách sạn, hỏi tiện nghi và mô tả điều kiện.", "Book a hotel room, ask about facilities and describe conditions.", [vocab("đặt", "book / reserve", "Tôi muốn đặt phòng.", "I want to book a room."), vocab("phòng đơn", "single room", "Cho tôi một phòng đơn.", "A single room, please."), vocab("phòng đôi", "double room", "Phòng đôi còn trống.", "A double room is available."), vocab("đầy đủ", "fully equipped", "Phòng có đầy đủ tiện nghi.", "The room is fully equipped."), vocab("giường", "bed", "Phòng có hai giường.", "The room has two beds."), vocab("giá", "price", "Giá phòng là bao nhiêu?", "How much is the room?")], "Điều kiện và nhượng bộ", "Conditions and concession", "Nếu/Hễ A thì B; Tuy/Dù A nhưng B", "Dùng “nếu...thì” cho điều kiện và “tuy/dù...nhưng” cho hai ý trái chiều.", "Use nếu...thì for a condition and tuy/dù...nhưng for concession.", [("Nếu còn phòng thì tôi đặt hai đêm.", "If a room is available, I will book two nights."), ("Phòng nhỏ nhưng rất tiện nghi.", "The room is small but well equipped.")], [line("Lễ tân", "Xin chào! Tôi có thể giúp gì cho anh?", "Hello! How can I help you?"), line("Khách", "Tôi muốn đặt phòng.", "I would like to book a room."), line("Lễ tân", "Anh muốn phòng đơn hay phòng đôi?", "Would you like a single or double room?"), line("Khách", "Cho tôi một phòng đơn.", "A single room, please.")], "Tôi muốn đặt một phòng ___.", "đơn", "“Phòng đơn” là phòng dành cho một người."),
    lesson(16, "Hôm nay trông anh có vẻ mệt", "You look tired today", "Nói về sức khỏe, triệu chứng, lời khuyên và trình tự thời gian.", "Discuss health and symptoms, give advice and order events in time.", [vocab("trông", "look / appear", "Trông anh có vẻ mệt.", "You look tired."), vocab("đau đầu", "headache", "Tôi bị đau đầu.", "I have a headache."), vocab("cảm thấy", "feel", "Tôi cảm thấy mệt.", "I feel tired."), vocab("nghỉ ngơi", "rest", "Anh nên nghỉ ngơi.", "You should rest."), vocab("khám", "medical examination", "Tôi đi khám bác sĩ.", "I see a doctor."), vocab("thuốc", "medicine", "Uống thuốc sau khi ăn.", "Take medicine after eating.")], "Triệu chứng và lời khuyên", "Symptoms and advice", "Chủ ngữ + bị/đau + triệu chứng; nên + động từ; sau khi/trước khi", "Dùng “bị” cho tình trạng không mong muốn, “nên” để khuyên và cụm thời gian để sắp xếp hành động.", "Use bị for unwanted conditions, nên for advice and time clauses to order actions.", [("Tôi bị đau đầu.", "I have a headache."), ("Anh nên nghỉ ngơi sau khi uống thuốc.", "You should rest after taking medicine.")], [line("Nam", "Hôm nay trông anh có vẻ mệt. Anh làm sao thế?", "You look tired today. What is wrong?"), line("John", "Tôi bị đau đầu và sốt.", "I have a headache and a fever."), line("Nam", "Anh nên đi khám bác sĩ.", "You should see a doctor."), line("John", "Cảm ơn anh. Tôi sẽ đi khám.", "Thank you. I will see a doctor.")], "Anh nên ___ ngơi.", "nghỉ", "Cụm đúng là “nghỉ ngơi”."),
]


payload = {
    "source": {
        "title": "Tiếng Việt 123 - Tiếng Việt cho người nước ngoài - Trình độ A",
        "pdfFile": "734298750-tiengviet123.pdf",
        "audioPage": "https://123vietnamese.com/audio/tieng-viet-123-student-book-2018/",
        "edition": "2018 audio edition",
        "levelMappingNote": "The book labels the material as Level A. The A0/A1.1/A1.2/A2 split is an instructional adaptation for this platform.",
    },
    "courses": [
        course("TV123-A0", "tieng-viet-123-phat-am-a0", "Tiếng Việt 123 - Phát âm A0", "Tieng Viet 123 - Pronunciation A0", "Làm quen bảng chữ cái, nguyên âm, phụ âm và sáu thanh điệu với hình minh họa và hai giọng vùng miền.", "Learn the alphabet, vowels, consonants and six tones with textbook visuals and two regional recordings.", "A0", "cover", pronunciation),
        course("TV123-A1-1", "tieng-viet-123-giao-tiep-a1-1", "Tiếng Việt 123 - Giao tiếp nền tảng A1.1", "Tieng Viet 123 - Foundation Communication A1.1", "Bài 1-5: giới thiệu, nghề nghiệp, tuổi, nhà ở và mua sắm; kết thúc bằng checkpoint học phần.", "Lessons 1-5: introductions, occupations, age, housing and shopping, followed by a module checkpoint.", "A1", "lesson-01", lessons[:5]),
        course("TV123-A1-2", "tieng-viet-123-doi-song-a1-2", "Tiếng Việt 123 - Đời sống hằng ngày A1.2", "Tieng Viet 123 - Daily Life A1.2", "Bài 6-10: thời gian, ngày tháng, trải nghiệm, thói quen và chỉ đường; kết thúc bằng checkpoint học phần.", "Lessons 6-10: time, dates, experiences, habits and directions, followed by a module checkpoint.", "A1", "lesson-06", lessons[5:10]),
        course("TV123-A2", "tieng-viet-123-giao-tiep-mo-rong-a2", "Tiếng Việt 123 - Giao tiếp mở rộng A2", "Tieng Viet 123 - Extended Communication A2", "Bài 11-16: khả năng, vị trí, gọi món, thời tiết, khách sạn và sức khỏe; kết thúc bằng đánh giá cuối lộ trình.", "Lessons 11-16: ability, location, dining, weather, hotels and health, followed by a final pathway assessment.", "A2", "lesson-11", lessons[10:]),
    ],
}

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {OUTPUT}")
print(f"Courses: {len(payload['courses'])}; lessons: {sum(len(c['modules'][0]['lessons']) for c in payload['courses'])}")
