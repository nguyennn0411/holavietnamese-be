-- Additive dictionary support. Existing IDs, content, notebook and review links are retained.
ALTER TABLE vocabularies MODIFY cefr_level VARCHAR(10) NULL;
ALTER TABLE vocabularies ADD COLUMN search_word VARCHAR(200) NULL;
ALTER TABLE vocabularies ADD COLUMN source_metadata LONGTEXT NULL;
ALTER TABLE vocabularies ADD COLUMN source_raw LONGTEXT NULL;
ALTER TABLE vocabularies ADD COLUMN lexical_data LONGTEXT NULL;
UPDATE vocabularies SET search_word = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(word),'à','a'),'á','a'),'ả','a'),'ã','a'),'ạ','a'),'ă','a'),'ằ','a'),'ắ','a'),'ẳ','a'),'ẵ','a'),'ặ','a'),'â','a'),'ầ','a'),'ấ','a'),'ẩ','a'),'ẫ','a'),'ậ','a'),'è','e'),'é','e'),'ẻ','e'),'ẽ','e'),'ẹ','e'),'ê','e'),'ề','e'),'ế','e'),'ể','e'),'ễ','e'),'ệ','e'),'ì','i'),'í','i'),'ỉ','i'),'ĩ','i'),'ị','i'),'ò','o'),'ó','o'),'ỏ','o'),'õ','o'),'ọ','o'),'ô','o'),'ồ','o'),'ố','o'),'ổ','o'),'ỗ','o'),'ộ','o'),'ơ','o'),'ờ','o'),'ớ','o'),'ở','o'),'ỡ','o'),'ợ','o'),'ù','u'),'ú','u'),'ủ','u'),'ũ','u'),'ụ','u'),'ư','u'),'ừ','u'),'ứ','u'),'ử','u'),'ữ','u'),'ự','u'),'ỳ','y'),'ý','y'),'ỷ','y'),'ỹ','y'),'ỵ','y'),'đ','d'), updated_at=updated_at;
CREATE INDEX idx_vocabulary_search_word_status ON vocabularies(search_word,status);
ALTER TABLE vocabulary_meanings MODIFY translation_en VARCHAR(500) NULL;
ALTER TABLE vocabulary_meanings ADD COLUMN definition_vi TEXT NULL;
ALTER TABLE vocabulary_meanings ADD COLUMN labels LONGTEXT NULL;
ALTER TABLE vocabulary_examples MODIFY translation_en TEXT NULL;
ALTER TABLE content_import_jobs ADD COLUMN summary LONGTEXT NULL;
