-- Keep legacy AI settings aligned with the OpenRouter provider configured by
-- the backend environment. Only replace the retired model value so explicit
-- administrator selections remain untouched.
UPDATE ai_settings
SET active_model = 'openrouter/free'
WHERE id = 1
  AND active_model = 'gemini-3.8-flash';
