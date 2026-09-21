-- Rare-dle "no peek" bonus: set once the player opened a Rare Items page during today's game.
ALTER TABLE raredleGames ADD COLUMN usedRares INTEGER NOT NULL DEFAULT 0;
