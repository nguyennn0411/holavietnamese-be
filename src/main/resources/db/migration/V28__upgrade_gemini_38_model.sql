-- Gemini 2.5 Flash is unavailable for new users. Move existing settings to
-- the current Gemini Flash model.
UPDATE ai_settings
SET active_model = 'gemini-3.8-flash'
WHERE active_model IN ('gemini-1.5-flash', 'gemini-2.5-flash');
