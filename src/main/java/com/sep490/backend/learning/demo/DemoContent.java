package com.sep490.backend.learning.demo;

import com.sep490.backend.entity.User;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.grammar.GrammarService;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.quiz.*;
import com.sep490.backend.learning.shared.*;
import com.sep490.backend.repository.jpa.UserJpaRepository;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in development data only. Uses the same application services as the builders. */
@Component
@Profile("dev-demo")
@org.springframework.core.annotation.Order(10)
@RequiredArgsConstructor
public class DemoContent implements CommandLineRunner {
  private final ContentStore db;
  private final CourseContentService courses;
  private final ActivityService activities;
  private final GrammarService grammar;
  private final QuestionBankService questions;
  private final QuizService quizzes;
  private final UserJpaRepository users;
  private final PasswordEncoder passwords;
  private final Environment env;

  @Override
  @Transactional
  public void run(String... args) {
    if (db.count("SELECT count(*) FROM courses") > 0) return;
    String password = env.getRequiredProperty("APP_DEMO_PASSWORD");
    if (password.length() < 12)
      throw new IllegalArgumentException("Demo password needs at least 12 characters.");
    long admin = user("admin@hola.test", "ADMIN", password);
    user("learner@hola.test", "LEARNER", password);
    long course =
        courses.create(
            new ContentRequests.Course(
                "HV-BEGINNER-A1",
                "vietnamese-for-beginners",
                "Vietnamese for Beginners",
                "Build confidence in everyday Vietnamese, from your first greeting to ordering your"
                    + " favorite bowl of phở.",
                "A1",
                null,
                120,
                "Recognize Vietnamese letters and tones.\n"
                    + "Introduce yourself and greet people naturally.\n"
                    + "Order food, shop and ask for directions.",
                true),
            admin);
    long first =
        courses.addModule(
            course,
            new ContentRequests.Module("Getting Started", "Your first steps in Vietnamese."));
    long second =
        courses.addModule(
            course,
            new ContentRequests.Module(
                "Daily Communication", "Practical language for everyday moments."));
    String[] titles = {
      "Vietnamese Alphabet",
      "Vietnamese Tones",
      "Greetings",
      "Ordering Food",
      "Shopping",
      "Asking Directions"
    };
    long[] lessons = new long[titles.length];
    for (int i = 0; i < titles.length; i++) {
      String slug = titles[i].toLowerCase(Locale.ROOT).replace(' ', '-');
      lessons[i] =
          courses.addLesson(
              i < 3 ? first : second,
              new ContentRequests.Lesson(
                  "DEMO-" + (i + 1),
                  slug,
                  titles[i],
                  "Practice Vietnamese for " + titles[i].toLowerCase(Locale.ROOT) + ".",
                  "NORMAL",
                  20,
                  i == 4 ? List.of(lessons[3]) : List.of()));
    }
    long g =
        grammar.create(
            new ContentRequests.Grammar(
                "CLASSIFIERS-FOOD",
                "classifiers-with-food",
                "Using classifiers with food",
                "Number + classifier + food",
                "Use tô for a bowl and ly for a glass. Vietnamese classifiers describe the serving"
                    + " or object being counted.",
                "Do not use ly for a bowl of soup. Keep the classifier before the food noun.",
                "A1",
                List.of(
                    new ContentRequests.Example(
                        "Tôi muốn một tô phở.",
                        "I would like a bowl of phở.",
                        "Một = one; tô = bowl."),
                    new ContentRequests.Example(
                        "Cho tôi hai ly nước.",
                        "Two glasses of water, please.",
                        "Hai = two; ly = glass."))));
    grammar.status(g, "PUBLISHED");
    long quiz =
        quizzes.create(
            new ContentRequests.Quiz(
                "Ordering Food Quiz",
                "Put your food vocabulary and grammar into practice.",
                "LESSON",
                course,
                lessons[3],
                BigDecimal.valueOf(70),
                15,
                5,
                true));
    addQuestion(
        quiz,
        "MULTIPLE_CHOICE",
        "Choose the classifier for a bowl of phở.",
        "Tô refers to a bowl.",
        "[{\"id\":\"a\",\"text\":\"tô\"},{\"id\":\"b\",\"text\":\"ly\"}]",
        "{\"optionIds\":[\"a\"]}");
    addQuestion(
        quiz,
        "MULTIPLE_SELECT",
        "Select the two food or drink words.",
        "Phở is noodle soup; nước is water.",
        "[{\"id\":\"a\",\"text\":\"phở\"},{\"id\":\"b\",\"text\":\"nước\"},{\"id\":\"c\",\"text\":\"xin"
            + " chào\"}]",
        "{\"optionIds\":[\"a\",\"b\"]}");
    addQuestion(
        quiz,
        "TRUE_FALSE",
        "Một means one.",
        "Một is the number one.",
        "[{\"id\":\"true\",\"text\":\"True\"},{\"id\":\"false\",\"text\":\"False\"}]",
        "{\"optionIds\":[\"true\"]}");
    addQuestion(
        quiz,
        "FILL_BLANK",
        "Tôi muốn một ___ phở.",
        "A bowl is tô.",
        "[]",
        "{\"acceptedAnswers\":[\"tô\"]}");
    addQuestion(
        quiz,
        "TRANSLATION",
        "Translate: Thank you.",
        "Cảm ơn is thank you.",
        "[]",
        "{\"acceptedAnswers\":[\"Cảm ơn\",\"Cảm ơn.\"]}");
    addQuestion(
        quiz,
        "REORDER_SENTENCE",
        "Put the words in order: I want a bowl of phở.",
        "Subject + muốn + quantity + food.",
        "[{\"id\":\"c\",\"text\":\"một tô"
            + " phở\"},{\"id\":\"a\",\"text\":\"Tôi\"},{\"id\":\"b\",\"text\":\"muốn\"}]",
        "{\"sequence\":[\"a\",\"b\",\"c\"]}");
    addQuestion(
        quiz,
        "MATCHING",
        "Match each Vietnamese word to its meaning.",
        "Tô = bowl, ly = glass.",
        "[{\"id\":\"a\",\"text\":\"tô\",\"side\":\"left\"},{\"id\":\"b\",\"text\":\"ly\",\"side\":\"left\"},{\"id\":\"x\",\"text\":\"glass\",\"side\":\"right\"},{\"id\":\"y\",\"text\":\"bowl\",\"side\":\"right\"}]",
        "{\"pairs\":{\"a\":\"y\",\"b\":\"x\"}}");
    addQuestion(
        quiz,
        "FILL_BLANK",
        "How do you write the number two in Vietnamese?",
        "Hai means two.",
        "[]",
        "{\"acceptedAnswers\":[\"hai\"]}");
    addQuestion(
        quiz,
        "MULTIPLE_CHOICE",
        "Which phrase means to order food?",
        "Gọi món means to order food.",
        "[{\"id\":\"a\",\"text\":\"gọi món\"},{\"id\":\"b\",\"text\":\"đi ngủ\"}]",
        "{\"optionIds\":[\"a\"]}");
    addQuestion(
        quiz,
        "TRANSLATION",
        "Translate: water.",
        "Nước means water.",
        "[]",
        "{\"acceptedAnswers\":[\"nước\"]}");
    quizzes.status(quiz, "PUBLISHED");
    for (int i = 0; i < lessons.length; i++) {
      long lesson = lessons[i];
      if (i != 3) {
        activity(
            lesson,
            "TEXT",
            titles[i],
            Map.of(
                "body",
                switch (i) {
                  case 0 ->
                      "Vietnamese uses the Latin alphabet with additional letters: ă, â, đ, ê, ô,"
                          + " ơ, ư. Read each letter aloud and notice the marks.";
                  case 1 ->
                      "Vietnamese has six tones. Compare: ma, má, mà, mả, mã, mạ. A tone changes"
                          + " the meaning of a word.";
                  case 2 ->
                      "Xin chào! Tôi tên là Nguyên. Rất vui được gặp bạn.\n"
                          + "Hello! My name is Nguyên. Nice to meet you.";
                  case 4 ->
                      "Cái này bao nhiêu tiền?\n"
                          + "How much is this?\n"
                          + "Tôi muốn mua cái này.\n"
                          + "I would like to buy this.";
                  default ->
                      "Nhà ga ở đâu? Đi thẳng rồi rẽ trái.\n"
                          + "Where is the station? Go straight, then turn left.";
                }),
            null,
            List.of());
      } else {
        activity(
            lesson,
            "TEXT",
            "A table for one, a world of flavor",
            Map.of(
                "body",
                "Welcome to your first Vietnamese restaurant conversation. You will learn to order"
                    + " phở, ask for water and thank your server.\n"
                    + "Read each activity, practice, then pass the quiz to unlock Shopping."),
            null,
            List.of());
        activity(
            lesson,
            "VOCABULARY",
            "Words on the menu",
            Map.of("vocabularyIds", List.of(9001, 9002, 9003)),
            null,
            List.of());
        activity(
            lesson,
            "LISTEN_AND_CHOOSE",
            "Listen to the order",
            Map.of(
                "audioUrl",
                "/media/ordering-food.wav",
                "transcript",
                "Tôi muốn một tô phở.",
                "prompt",
                "What does the customer order?",
                "options",
                List.of(
                    Map.of("id", "a", "text", "A bowl of phở"),
                    Map.of("id", "b", "text", "A glass of water")),
                "correctAnswer",
                Map.of("optionIds", List.of("a"))),
            null,
            List.of());
        activity(
            lesson,
            "DIALOGUE",
            "At the restaurant",
            Map.of(
                "lines",
                List.of(
                    Map.of(
                        "speaker",
                        "Waiter",
                        "text",
                        "Bạn muốn gọi món gì?",
                        "translation",
                        "What would you like to order?"),
                    Map.of(
                        "speaker",
                        "You",
                        "text",
                        "Tôi muốn một tô phở.",
                        "translation",
                        "I would like a bowl of phở."),
                    Map.of(
                        "speaker",
                        "Waiter",
                        "text",
                        "Bạn muốn uống gì?",
                        "translation",
                        "What would you like to drink?"),
                    Map.of(
                        "speaker",
                        "You",
                        "text",
                        "Cho tôi một ly nước. Cảm ơn!",
                        "translation",
                        "A glass of water, please. Thank you!"))),
            null,
            List.of());
        activity(
            lesson, "GRAMMAR", "A bowl, a glass, a little grammar", Map.of(), null, List.of(g));
        activity(
            lesson,
            "FILL_BLANK",
            "Your turn to order",
            Map.of(
                "prompt",
                "Tôi muốn một ___ phở.",
                "options",
                List.of(),
                "correctAnswer",
                Map.of("acceptedAnswers", List.of("tô"))),
            null,
            List.of());
        activity(lesson, "QUIZ", "Ordering Food Quiz", Map.of(), quiz, List.of());
      }
      courses.lessonStatus(lesson, "PUBLISHED");
    }
    courses.status(course, "PUBLISHED");
  }

  private long user(String email, String role, String password) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> {
              var u = new User();
              u.setUsername(email);
              u.setEmail(email);
              u.setPasswordHash(passwords.encode(password));
              u.setRole(role);
              u.setEnabled(true);
              return users.saveAndFlush(u);
            })
        .getId();
  }

  private void activity(
      long lesson, String type, String title, Object content, Long quiz, List<Long> grammar) {
    activities.create(
        lesson,
        new ContentRequests.Activity(
            type,
            title,
            "",
            db.json(db.encode(content)),
            true,
            BigDecimal.TEN,
            "PUBLISHED",
            quiz,
            grammar));
  }

  private void addQuestion(
      long quiz, String type, String prompt, String explanation, String options, String correct) {
    long id =
        questions.create(
            new ContentRequests.Question(
                type,
                prompt,
                explanation,
                "EASY",
                "Food and drink",
                null,
                null,
                "PUBLISHED",
                db.json(correct),
                db.json(options)));
    quizzes.addQuestion(quiz, new ContentRequests.QuizQuestion(id, BigDecimal.TEN));
  }
}
