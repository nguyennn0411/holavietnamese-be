ALTER TABLE regional_vocabularies ADD COLUMN meaning_en VARCHAR(1000) NULL;
ALTER TABLE regional_vocabularies ADD COLUMN example_sentence_en VARCHAR(2000) NULL;
ALTER TABLE regional_vocabularies ADD COLUMN usage_note_en VARCHAR(2000) NULL;

ALTER TABLE ai_scenarios ADD COLUMN title_vi VARCHAR(255) NULL;
ALTER TABLE ai_scenarios ADD COLUMN title_en VARCHAR(255) NULL;
ALTER TABLE ai_scenarios ADD COLUMN overview_vi TEXT NULL;
ALTER TABLE ai_scenarios ADD COLUMN overview_en TEXT NULL;

UPDATE ai_scenarios
SET title_vi = COALESCE(title_vi, title),
    overview_vi = COALESCE(overview_vi, overview);

UPDATE ai_scenarios SET
    title_en = 'Ordering at a Traditional Hanoi Pho Shop',
    overview_en = 'You have just arrived in Hanoi Old Quarter on a chilly early morning and walked into a crowded traditional beef pho restaurant on Bat Dan Street.'
WHERE id = 'goi-mon-pho-ha-noi';

UPDATE ai_scenarios SET
    title_en = 'Bargaining for Souvenirs at Ben Thanh Market',
    overview_en = 'You visit Saigon Ben Thanh Market to buy souvenirs for friends: hand-painted conical hats and fragrant roasted Robusta coffee.'
WHERE id = 'tra-gia-cho-ben-thanh';

UPDATE ai_scenarios SET
    title_en = 'Catching a Ride-Hailing Motorbike & Giving Directions',
    overview_en = 'You have just booked a ride-hailing motorbike trip on the app and the driver calls to confirm your pickup spot.'
WHERE id = 'bat-xe-om-cong-nghe';

UPDATE ai_scenarios SET
    title_en = 'Hotel Check-in & Inquiring Services in Da Nang',
    overview_en = 'You have just arrived at a 4-star hotel along My Khe beach in Da Nang after a long flight and are checking in at reception.'
WHERE id = 'checkin-khach-san-da-nang';

UPDATE ai_scenarios SET
    title_en = 'Meeting & Greeting a Vietnamese Friend''s Family',
    overview_en = 'You are invited by a close friend to visit their family for weekend lunch. This is your first time meeting your friend''s parents.'
WHERE id = 'ra-mat-gia-dinh-ban';

UPDATE ai_scenarios SET
    title_en = 'Job Interview at a Vietnamese Enterprise',
    overview_en = 'You are attending an in-depth interview for an International Cooperation Specialist position at a major tech corporation in Vietnam.'
WHERE id = 'phong-van-xin-viec-cong-so';
