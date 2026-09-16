-- Jobs/tasks marketplace: a "hiring" post (someone wants to pay for a task
-- done) or a "forHire" post (someone offering their labor). Deliberately
-- simpler than item listings/bids: responding is a single "I'm interested"
-- action that reveals contact info to both sides immediately — no
-- accept/reject step, no price negotiation. A job stays active (so multiple
-- people can express interest) until the poster manually closes it.

CREATE TABLE marketplaceJobs (
	id TEXT PRIMARY KEY,
	accountId TEXT NOT NULL,
	type TEXT NOT NULL, -- 'hiring' | 'forHire'
	title TEXT NOT NULL,
	description TEXT,
	rewardAmount REAL,
	rewardCurrency TEXT,
	world TEXT NOT NULL,
	deadline TEXT, -- optional "needed by" date, ISO string
	status TEXT NOT NULL DEFAULT 'active', -- 'active' | 'fulfilled' | 'cancelled' | 'expired'
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL,
	closedAt TEXT,
	closedReason TEXT
);
CREATE INDEX idx_marketplaceJobs_status ON marketplaceJobs(status);
CREATE INDEX idx_marketplaceJobs_account ON marketplaceJobs(accountId);

CREATE TABLE marketplaceJobInterests (
	id TEXT PRIMARY KEY,
	jobId TEXT NOT NULL,
	interestedAccountId TEXT NOT NULL,
	message TEXT,
	createdAt TEXT NOT NULL
);
CREATE INDEX idx_marketplaceJobInterests_job ON marketplaceJobInterests(jobId);
CREATE UNIQUE INDEX idx_marketplaceJobInterests_unique ON marketplaceJobInterests(jobId, interestedAccountId);
