-- Normalize any legacy A0 / STARTER proficiency levels to A1
-- according to the 6-level Vietnamese language competency framework (Circular 17/2015/TT-BGDĐT: A1, A2, B1, B2, C1, C2)

UPDATE courses
SET level = 'A1'
WHERE UPPER(TRIM(level)) IN ('A0', 'STARTER');

UPDATE grammar_topics
SET level = 'A1'
WHERE UPPER(TRIM(level)) IN ('A0', 'STARTER');

UPDATE users
SET target_level = 'A1'
WHERE UPPER(TRIM(target_level)) IN ('A0', 'STARTER');

UPDATE learning_path_preferences
SET confirmed_level = CASE WHEN UPPER(TRIM(confirmed_level)) IN ('A0', 'STARTER') THEN 'A1' ELSE confirmed_level END,
    placement_level = CASE WHEN UPPER(TRIM(placement_level)) IN ('A0', 'STARTER') THEN 'A1' ELSE placement_level END,
    onboarding_level = CASE WHEN UPPER(TRIM(onboarding_level)) IN ('A0', 'STARTER') THEN 'A1' ELSE onboarding_level END
WHERE UPPER(TRIM(confirmed_level)) IN ('A0', 'STARTER')
   OR UPPER(TRIM(placement_level)) IN ('A0', 'STARTER')
   OR UPPER(TRIM(onboarding_level)) IN ('A0', 'STARTER');
