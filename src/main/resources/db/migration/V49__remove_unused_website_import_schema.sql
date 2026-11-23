-- The website course-import feature was never wired into the runtime application.
-- Drop the child table first because it owns the foreign key to the import job.
DROP TABLE IF EXISTS website_import_provenance;
DROP TABLE IF EXISTS website_import_jobs;
