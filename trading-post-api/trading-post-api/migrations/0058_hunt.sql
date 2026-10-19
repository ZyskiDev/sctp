-- The Golden Shell: a solo treasure hunt across the mod and the site (see hunt.js).
-- Nothing here is visible until siteSettings 'hunt' says enabled (admin panel).

-- Shared content: the same for every hunter.
CREATE TABLE huntChapters (
	id TEXT PRIMARY KEY,              -- 'c1'..'c16'
	whorl INTEGER NOT NULL,           -- 1..4
	ord INTEGER NOT NULL,             -- order inside the whorl
	title TEXT NOT NULL,
	clue TEXT NOT NULL,
	hints TEXT NOT NULL DEFAULT '[]', -- JSON [nudge, push, shove]
	fragment TEXT NOT NULL DEFAULT '' -- shown once solved: one piece of the whorl puzzle
);
CREATE TABLE huntWhorls (
	whorl INTEGER PRIMARY KEY,        -- 1..4
	title TEXT NOT NULL,
	intro TEXT NOT NULL DEFAULT '',
	puzzle TEXT NOT NULL DEFAULT '',  -- shown once all four chapters are solved
	nudge TEXT NOT NULL DEFAULT ''
);

-- Personal content: one set per hunter, made by scripts/hunt/generate.js and
-- handed out at sign-up. Answers are stored as HMACs only.
CREATE TABLE huntSets (
	setNo INTEGER PRIMARY KEY,
	accountId TEXT UNIQUE,            -- null until handed out
	assignedAt TEXT
);
CREATE TABLE huntSetChapters (
	setNo INTEGER NOT NULL,
	chapterId TEXT NOT NULL,
	content TEXT NOT NULL,            -- JSON blocks shown with the chapter
	answerKind TEXT NOT NULL DEFAULT 'word', -- 'word' | 'numbers'
	answerHash TEXT NOT NULL,
	PRIMARY KEY (setNo, chapterId)
);
CREATE TABLE huntSetNotes (
	setNo INTEGER NOT NULL,
	context TEXT NOT NULL,            -- 'item:minecraft:clock', 'chest:Firefly:...', 'door:fireflies', 'secret:raredle:1'...
	chapterId TEXT NOT NULL,          -- only shown once this chapter's whorl is open
	text TEXT NOT NULL,
	PRIMARY KEY (setNo, context)
);

CREATE TABLE huntPlayers (
	accountId TEXT PRIMARY KEY,
	mcUsername TEXT NOT NULL,
	ledgerNo INTEGER NOT NULL UNIQUE,
	setNo INTEGER,
	signedUpAt TEXT NOT NULL,
	via TEXT,                         -- 'mod' | 'spiral'
	anonymous INTEGER NOT NULL DEFAULT 0,
	removed INTEGER NOT NULL DEFAULT 0
);
-- The trailhead: the mod shows a code, the player redeems it on sctp.nl with a
-- verified account, and the mod gets a hunt token (so nobody can play as someone else).
CREATE TABLE huntPairings (
	code TEXT PRIMARY KEY,
	pollKey TEXT NOT NULL UNIQUE,
	mcUsername TEXT NOT NULL,
	createdAt TEXT NOT NULL,
	expiresAt TEXT NOT NULL,
	accountId TEXT,
	token TEXT
);
CREATE TABLE huntTokens (
	token TEXT PRIMARY KEY,
	accountId TEXT NOT NULL,
	mcUsername TEXT NOT NULL,
	createdAt TEXT NOT NULL
);
CREATE INDEX idx_huntTokens_account ON huntTokens(accountId);

CREATE TABLE huntSolves (
	accountId TEXT NOT NULL,
	ref TEXT NOT NULL,                -- 'c1'..'c16', 'w1'..'w4', 'apex', 'vault'
	solvedAt TEXT NOT NULL,
	PRIMARY KEY (accountId, ref)
);
CREATE INDEX idx_huntSolves_ref ON huntSolves(ref, solvedAt);
CREATE TABLE huntGuesses (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	accountId TEXT NOT NULL,
	ref TEXT NOT NULL,
	guess TEXT NOT NULL,
	correct INTEGER NOT NULL,
	at TEXT NOT NULL
);
CREATE INDEX idx_huntGuesses_account ON huntGuesses(accountId, ref, at);
CREATE TABLE huntHints (
	accountId TEXT NOT NULL,
	chapterId TEXT NOT NULL,
	tier INTEGER NOT NULL,            -- 0 nudge, 1 push, 2 shove, 3 whorl puzzle nudge
	at TEXT NOT NULL,
	PRIMARY KEY (accountId, chapterId, tier)
);
-- Hunt Rare-dle boards (chapter 4): personal answers, a lost board resets after an hour.
CREATE TABLE huntRaredle (
	accountId TEXT NOT NULL,
	board INTEGER NOT NULL,
	answerId TEXT NOT NULL,
	guesses TEXT NOT NULL DEFAULT '[]',
	status TEXT NOT NULL DEFAULT 'playing',
	resetAt TEXT,
	updatedAt TEXT NOT NULL,
	PRIMARY KEY (accountId, board)
);
