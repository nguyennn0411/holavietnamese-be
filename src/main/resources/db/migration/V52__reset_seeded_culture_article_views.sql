-- V38 seeded demo articles with fabricated view counts.
-- Remove only that fabricated baseline so the column keeps the real views
-- recorded since the seed (each detail read increments culture_articles.views).
UPDATE culture_articles SET views = GREATEST(views - 1540, 0) WHERE id = 'van-hoa-ca-phe-viet-nam';
UPDATE culture_articles SET views = GREATEST(views - 2980, 0) WHERE id = 'bi-quyet-pho-ha-noi';
UPDATE culture_articles SET views = GREATEST(views - 1850, 0) WHERE id = 'nghe-thuat-xung-ho-viet-nam';
UPDATE culture_articles SET views = GREATEST(views - 3210, 0) WHERE id = 'phong-tuc-tet-nguyen-dan';
UPDATE culture_articles SET views = GREATEST(views - 2190, 0) WHERE id = 'kham-pha-hoi-an';
UPDATE culture_articles SET views = GREATEST(views - 1720, 0) WHERE id = 'cho-noi-mientay';
