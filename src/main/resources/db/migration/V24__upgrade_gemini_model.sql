-- Gemini 1.5 Flash is no longer available on the Gemini API.
UPDATE ai_settings
SET active_model = 'gemini-2.5-flash'
WHERE active_model = 'gemini-1.5-flash';
