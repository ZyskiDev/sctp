-- Rare-dle: a daily "guess the rare" game. One secret rare per UTC day
-- (raredleAnswers, never sent to the client until a player's game is over) and
-- one game row per account per day.
CREATE TABLE raredleAnswers (
	date TEXT PRIMARY KEY,          -- YYYY-MM-DD (UTC)
	itemId TEXT NOT NULL            -- id from data/rare-items.json
);

CREATE TABLE raredleGames (
	accountId TEXT NOT NULL,
	date TEXT NOT NULL,
	guesses TEXT NOT NULL DEFAULT '[]',   -- JSON array of guessed item ids, in order
	status TEXT NOT NULL DEFAULT 'playing', -- playing | won | lost
	guessCount INTEGER NOT NULL DEFAULT 0,
	score INTEGER NOT NULL DEFAULT 0,      -- points earned (0 until won)
	startedAt TEXT NOT NULL,
	finishedAt TEXT,
	PRIMARY KEY (accountId, date)
);
CREATE INDEX idx_raredleGames_date ON raredleGames(date, status);
