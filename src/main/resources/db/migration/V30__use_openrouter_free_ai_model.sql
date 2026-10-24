-- Keep the persisted AI setting aligned with the learner provider configuration.
UPDATE ai_settings
SET active_model = 'openrouter/free'
WHERE id = 1
  AND (active_model IS NULL OR active_model LIKE 'gemini-%' OR active_model LIKE 'gpt-%');
