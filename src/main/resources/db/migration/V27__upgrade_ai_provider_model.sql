-- Move legacy Gemini settings to the current configured AI provider model.
-- The migration is intentionally idempotent for existing installations.
UPDATE ai_settings
SET active_model = 'gemini-3.8-flash'
WHERE active_model IN ('gemini-1.5-flash', 'gemini-2.5-flash');
