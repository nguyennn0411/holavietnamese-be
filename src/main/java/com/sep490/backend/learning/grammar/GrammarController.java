package com.sep490.backend.learning.grammar;

import com.sep490.backend.learning.shared.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GrammarController {
  private final GrammarService grammar;

  @GetMapping("/api/grammar")
  public Object list(@RequestParam Map<String, String> p) {
    return grammar.list(p, false);
  }

  @GetMapping("/api/grammar/{id}")
  public Object detail(@PathVariable long id) {
    return grammar.detail(id, false);
  }

  @GetMapping("/api/admin/grammar")
  public Object adminList(@RequestParam Map<String, String> p) {
    return grammar.list(p, true);
  }

  @GetMapping("/api/admin/grammar/{id}")
  public Object adminDetail(@PathVariable long id) {
    return grammar.detail(id, true);
  }

  @PostMapping("/api/admin/grammar")
  public Object create(@Valid @RequestBody ContentRequests.Grammar r) {
    return grammar.detail(grammar.create(r), true);
  }

  @PutMapping("/api/admin/grammar/{id}")
  public Object edit(@PathVariable long id, @Valid @RequestBody ContentRequests.Grammar r) {
    grammar.edit(id, r);
    return grammar.detail(id, true);
  }

  @PatchMapping("/api/admin/grammar/{id}/status")
  public Object status(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r) {
    grammar.status(id, r.status());
    return grammar.detail(id, true);
  }
}
