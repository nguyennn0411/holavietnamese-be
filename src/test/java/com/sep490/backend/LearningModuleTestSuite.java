package com.sep490.backend;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/** One-click IntelliJ suite for every course and learning flow owned by this module. */
@Suite
@SuiteDisplayName("Learning module: unit and integration tests")
@SelectPackages("com.sep490.backend.modules.learning")
@SelectClasses({
  C1CourseCatalogIntegrationTests.class,
  CourseImportIntegrationTests.class,
  CourseReviewWorkflowIntegrationTests.class,
  CourseStudioWorkbookTests.class,
  CourseWorkbookFileTests.class,
  LearnerFeaturesIntegrationTests.class,
  LearningPathIntegrationTests.class,
  Member2LearningIntegrationTests.class,
  QuestionScoringTests.class
})
@IncludeClassNamePatterns(".*Tests?")
class LearningModuleTestSuite {}
