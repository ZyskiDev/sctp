-- The Golden Shell: accounts without a verified Minecraft name can hunt too. Their
-- name comes from the game that showed the sign-up code; a verified account with
-- the same name later takes it over (see handleSignup in hunt.js).
ALTER TABLE huntPlayers ADD COLUMN verified INTEGER NOT NULL DEFAULT 1;
