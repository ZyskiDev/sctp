-- Thumbs up/down reviews on job posts (marketplaceJobs). One vote per
-- (job, reviewer), upserted via ON CONFLICT so changing your mind just
-- flips the existing row instead of creating a second one. Aggregated two
-- ways on the client: per-job on the listing card/detail page, and summed
-- across every job a poster has ever put up for their profile's overall
-- "seller rating".

CREATE TABLE marketplaceJobReviews (
	id TEXT PRIMARY KEY,
	jobId TEXT NOT NULL,
	reviewerAccountId TEXT NOT NULL,
	vote INTEGER NOT NULL, -- 1 (up) or -1 (down)
	createdAt TEXT NOT NULL
);
CREATE INDEX idx_marketplaceJobReviews_job ON marketplaceJobReviews(jobId);
CREATE UNIQUE INDEX idx_marketplaceJobReviews_unique ON marketplaceJobReviews(jobId, reviewerAccountId);
