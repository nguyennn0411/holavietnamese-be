package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.learning.course.CourseWorkbookService;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ValidateTainguyenWorkbooksTest {
  @Autowired CourseWorkbookService workbooks;

  @Test
  void validateAll15TainguyenWorkbooks() throws Exception {
    File dir = new File("C:/tainguyen");
    File[] files = dir.listFiles((d, name) -> name.endsWith(".xlsx"));
    assertThat(files).isNotNull().isNotEmpty();
    Arrays.sort(files);

    System.out.println("Found " + files.length + " workbooks to validate in C:/tainguyen");

    int validCount = 0;
    for (File f : files) {
      byte[] bytes = Files.readAllBytes(f.toPath());
      Map<String, Object> preview = workbooks.validate(bytes, f.getName());
      boolean valid = Boolean.TRUE.equals(preview.get("valid"));
      @SuppressWarnings("unchecked")
      List<String> errors = (List<String>) preview.get("errors");
      System.out.println("FILE: " + f.getName() + " -> valid: " + valid);
      if (!valid) {
        System.out.println("  ERRORS for " + f.getName() + ":");
        for (String err : errors) {
          System.out.println("    - " + err);
        }
      } else {
        validCount++;
      }
    }

    System.out.println("VALID COUNT: " + validCount + " / " + files.length);
  }
}
