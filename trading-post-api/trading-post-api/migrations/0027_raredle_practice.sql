-- Rare-dle practice rounds (testing mode): one open random round per account,
-- kept apart from the daily game so it never touches points, streaks or leaderboards.
CREATE TABLE raredlePractice (
	accountId TEXT PRIMARY KEY,
	answerId TEXT NOT NULL,
	guesses TEXT NOT NULL DEFAULT '[]',
	status TEXT NOT NULL DEFAULT 'playing',
	guessCount INTEGER NOT NULL DEFAULT 0,
	updatedAt TEXT NOT NULL
);
