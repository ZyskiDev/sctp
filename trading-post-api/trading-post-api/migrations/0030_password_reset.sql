-- Admin-generated, single-use, expiring links that let an account holder set
-- a new password — same mechanic as verificationLinks (see 0017_mapart.sql),
-- just keyed to an existing accountId instead of an mcUsername.
CREATE TABLE passwordResetLinks (
	token TEXT PRIMARY KEY,
	accountId TEXT NOT NULL,
	username TEXT NOT NULL, -- snapshot for the admin list; the account's own username is authoritative
	createdBy TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL,
	usedAt TEXT
);
