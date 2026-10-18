-- Short links: sctp.nl/<slug> redirects to a page on the site (admin "shortLinks").
CREATE TABLE shortLinks (
	slug TEXT PRIMARY KEY,
	target TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	createdBy TEXT NOT NULL,
	updatedAt TEXT NOT NULL,
	clicks INTEGER NOT NULL DEFAULT 0
);
