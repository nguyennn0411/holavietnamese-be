-- Add api_key column to ai_settings for persistent LLM provider integration
ALTER TABLE ai_settings ADD COLUMN api_key VARCHAR(255);
