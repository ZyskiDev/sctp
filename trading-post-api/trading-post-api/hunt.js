// ---------------- The Golden Shell (treasure hunt) ----------------
//
// A solo hunt across the mod and the site: four whorls of four chapters, each
// whorl closed by a whorl puzzle (whorls I-III pick a square on the world map,
// down to 1,024 blocks; whorl IV gives the vault key), then the Apex: a riddle
// that names the vault's exact block. See migrations/0058_hunt.sql.
//
// Everything stays dark until the head admin turns it on: while siteSettings
// 'hunt' isn't enabled, every public hunt route answers 404 like a route that
// doesn't exist, and /hunt/status only says {enabled:false}.
//
// Shared content (clues, hints, fragments, whorl puzzles) is the same for every
// hunter. Personal content (the puzzle behind each clue, its answer, and the
// notes the mod shows on certain screens) comes in sets made by
// scripts/hunt/generate.js; each hunter gets one set at sign-up. Answers are
// stored as HMACs only.
//
// Two ways in, same data: the website (an sctp.nl session) and the mod (a hunt
// token the mod gets by pairing: it shows a code, the player redeems it on the
// site with an account whose verified Minecraft name matches).
//
// The worker passes in the helpers it already has (see makeHunt in worker.js).

export const HUNT_WORLD_MIN = -32768;
export const HUNT_WORLD_SIZE = 65536;
const SETTINGS_KEY = "hunt";
const PAIR_TTL_MS = 15 * 60 * 1000;
const HOUR = 3600 * 1000;
const HINT_DELAYS = [48 * HOUR, 96 * HOUR, 168 * HOUR]; // nudge, push, shove after the whorl opens
const HINT_COMMUNITY = [0.25, 0.5, Infinity];           // ...or once this share of hunters solved it
const WHORL_NUDGE_DELAY = 120 * HOUR;
const LIMITS = { chapter: [10, HOUR], whorl: [3, 24 * HOUR], apex: [10, HOUR], vault: [10, HOUR] };
const RAREDLE_RESET_MS = HOUR;
// Notes the mod may see (everything else, like door and Rare-dle answers, stays on the server).
const MOD_NOTE_PREFIXES = ["item:", "chest:", "butterfly", "quest", "sefi", "bp:", "ec:"];
const PAIR_ABC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const IMAGE_KEY = /^img\/[A-Za-z0-9_-]{8,80}\.(webp|png)$/;

export function makeHunt(d) {
	const { json, requireAnyAdmin, requireAdminAuth, newToken, bufToHex, modUserPingHash, MC_USERNAME } = d;

	// ---- settings ----

	const DEFAULT_SETTINGS = {
		enabled: false, enabledAt: null, signupOpen: true, requirePriorModUse: true,
		vault: null, vaultTolerance: 2, keyHash: null, vaultWordHash: null,
		apex: { title: "The Apex", riddle: "", marks: [] },
		doors: [], hintRelease: {}, broadcast: null, shell: { found: null, claims: [] },
		testers: [], // Minecraft names that see the hunt while it's off (for testing)
	};
	async function getSettings(env) {
		const row = await env.DB.prepare("SELECT value FROM siteSettings WHERE key = ?").bind(SETTINGS_KEY).first();
		let s = {};
		try { s = row ? JSON.parse(row.value) : {}; } catch (e) { /* fall back to defaults */ }
		return { ...DEFAULT_SETTINGS, ...s, apex: { ...DEFAULT_SETTINGS.apex, ...(s.apex || {}) }, shell: { ...DEFAULT_SETTINGS.shell, ...(s.shell || {}) } };
	}
	async function saveSettings(env, s) {
		await env.DB.prepare("INSERT OR REPLACE INTO siteSettings (key, value) VALUES (?, ?)").bind(SETTINGS_KEY, JSON.stringify(s)).run();
	}
	const hidden = () => json({ error: "Not found" }, 404);

	// ---- testers: the hunt is fully on for these Minecraft names while it's off for everyone else ----
	function isTester(s, name) {
		const k = mcKey(name);
		return !!k && (s.testers || []).some((t) => mcKey(t) === k);
	}
	/** Who's asking: the mod's hunt token, an sctp.nl session (verified name only), or ?mc= (the mod's status check). */
	async function requesterName(request, env) {
		const auth = request.headers.get("Authorization") || "";
		const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
		if (token) {
			const t = await env.DB.prepare("SELECT mcUsername FROM huntTokens WHERE token = ?").bind(token).first();
			if (t) return t.mcUsername;
			const a = await env.DB.prepare("SELECT a.mcUsername, a.mcVerified FROM adminSessions s JOIN admins a ON a.id = s.adminId WHERE s.token = ? AND s.expiresAt > ?").bind(token, nowIso()).first();
			if (a && a.mcVerified) return a.mcUsername;
		}
		return new URL(request.url).searchParams.get("mc") || null;
	}
	async function canSee(request, env, s) {
		if (s.enabled) return true;
		if (!(s.testers || []).length) return false;
		return isTester(s, await requesterName(request, env));
	}

	// ---- small helpers ----

	async function huntHash(env, scope, value) {
		const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.MOD_USER_HASH_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
		return bufToHex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode("hunt:" + scope + ":" + value)));
	}
	function normAnswer(kind, s) {
		s = String(s == null ? "" : s);
		if (kind === "numbers") return (s.match(/-?\d+/g) || []).map((n) => String(parseInt(n, 10))).join(",");
		return s.normalize("NFKD").toLowerCase().replace(/[^a-z0-9]/g, "");
	}
	function squareAt(x, z, size) {
		return {
			x: HUNT_WORLD_MIN + Math.floor((x - HUNT_WORLD_MIN) / size) * size,
			z: HUNT_WORLD_MIN + Math.floor((z - HUNT_WORLD_MIN) / size) * size,
			size,
		};
	}
	const whorlSize = (k) => HUNT_WORLD_SIZE / Math.pow(4, k); // whorl 1 -> 16,384
	const ledger = (n) => "L-" + String(n).padStart(4, "0");
	const mcKey = (n) => String(n || "").toLowerCase().replace(/^\.+/, "");
	const nowIso = () => new Date().toISOString();
	async function body(request) { try { return await request.json(); } catch (e) { return null; } }

	// ---- auth ----

	/** A hunt token (the mod) or a normal sctp.nl session (the site) -> {accountId} | {response}. */
	async function huntAuth(request, env, { modOnly = false, siteOnly = false } = {}) {
		const auth = request.headers.get("Authorization") || "";
		const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
		if (!token) return { response: json({ error: "Log in to sctp.nl first.", code: "login" }, 401) };
		if (!siteOnly) {
			const t = await env.DB.prepare("SELECT * FROM huntTokens WHERE token = ?").bind(token).first();
			if (t) return { accountId: t.accountId, via: "mod" };
			if (modOnly) return { response: json({ error: "This mod isn't paired with the hunt yet.", code: "pair" }, 401) };
		}
		const a = await requireAnyAdmin(request, env);
		if (!a.ok) return { response: a.response };
		return { accountId: a.admin.id, admin: a.admin, via: "site" };
	}
	async function playerOf(env, accountId) {
		const p = await env.DB.prepare("SELECT * FROM huntPlayers WHERE accountId = ?").bind(accountId).first();
		return p && !p.removed ? p : null;
	}

	// ---- progress ----

	async function loadContent(env) {
		const [ch, wh] = await Promise.all([
			env.DB.prepare("SELECT * FROM huntChapters ORDER BY whorl, ord").all(),
			env.DB.prepare("SELECT * FROM huntWhorls ORDER BY whorl").all(),
		]);
		return { chapters: ch.results, whorls: wh.results };
	}
	async function solvesOf(env, accountId) {
		const { results } = await env.DB.prepare("SELECT ref, solvedAt FROM huntSolves WHERE accountId = ?").bind(accountId).all();
		return new Map(results.map((r) => [r.ref, r.solvedAt]));
	}
	/** When each whorl opened for this player (whorl k opens when whorl k-1's puzzle is solved). */
	function unlockTimes(player, solves) {
		const out = { 1: player.signedUpAt };
		for (let k = 2; k <= 4; k++) out[k] = solves.get("w" + (k - 1)) || null;
		return out;
	}
	async function communityShares(env) {
		const [{ results }, total] = await Promise.all([
			env.DB.prepare("SELECT s.ref, COUNT(*) AS c FROM huntSolves s JOIN huntPlayers p ON p.accountId = s.accountId AND p.removed = 0 GROUP BY s.ref").all(),
			env.DB.prepare("SELECT COUNT(*) AS c FROM huntPlayers WHERE removed = 0").first(),
		]);
		const n = Math.max(1, total.c);
		return { total: total.c, share: new Map(results.map((r) => [r.ref, r.c / n])), count: new Map(results.map((r) => [r.ref, r.c])) };
	}
	function hintAvailableAt(tier, unlockedAt, chapterId, shares, settings) {
		if ((settings.hintRelease[chapterId] || 0) > tier) return unlockedAt;
		if ((shares.share.get(chapterId) || 0) >= HINT_COMMUNITY[tier]) return unlockedAt;
		return new Date(Date.parse(unlockedAt) + HINT_DELAYS[tier]).toISOString();
	}
	async function tooManyWrong(env, accountId, ref, kind) {
		const [max, win] = LIMITS[kind];
		const since = new Date(Date.now() - win).toISOString();
		const { results } = await env.DB.prepare("SELECT at FROM huntGuesses WHERE accountId = ? AND ref = ? AND correct = 0 AND at >= ? ORDER BY at").bind(accountId, ref, since).all();
		if (results.length < max) return null;
		return new Date(Date.parse(results[0].at) + win).toISOString(); // when the oldest wrong guess stops counting
	}
	async function logGuess(env, accountId, ref, guess, correct) {
		await env.DB.prepare("INSERT INTO huntGuesses (accountId, ref, guess, correct, at) VALUES (?, ?, ?, ?, ?)").bind(accountId, ref, String(guess).slice(0, 200), correct ? 1 : 0, nowIso()).run();
	}
	async function solve(env, accountId, ref) {
		await env.DB.prepare("INSERT OR IGNORE INTO huntSolves (accountId, ref, solvedAt) VALUES (?, ?, ?)").bind(accountId, ref, nowIso()).run();
	}

	/** Everything a hunter sees: the same payload for the site and the mod. */
	async function statePayload(env, settings, player) {
		const [content, solves, shares, setRows, hintRows] = await Promise.all([
			loadContent(env),
			solvesOf(env, player.accountId),
			communityShares(env),
			env.DB.prepare("SELECT chapterId, content, answerKind FROM huntSetChapters WHERE setNo = ?").bind(player.setNo).all(),
			env.DB.prepare("SELECT chapterId, tier FROM huntHints WHERE accountId = ?").bind(player.accountId).all(),
		]);
		const personal = new Map(setRows.results.map((r) => [r.chapterId, r]));
		const revealed = new Set(hintRows.results.map((r) => r.chapterId + ":" + r.tier));
		const unlocks = unlockTimes(player, solves);
		const now = Date.now();

		const whorls = content.whorls.map((w) => {
			const k = w.whorl, unlockedAt = unlocks[k], unlocked = !!unlockedAt;
			const chapters = content.chapters.filter((c) => c.whorl === k).map((c) => {
				const mine = personal.get(c.id);
				let hints = [];
				try { hints = JSON.parse(c.hints || "[]"); } catch (e) { /* none */ }
				return {
					id: c.id, ord: c.ord, title: c.title,
					clue: unlocked ? c.clue : null,
					content: unlocked && mine ? JSON.parse(mine.content) : null,
					answerKind: mine ? mine.answerKind : "word",
					solved: solves.has(c.id), solvedAt: solves.get(c.id) || null,
					fragment: solves.has(c.id) ? c.fragment : null,
					hints: unlocked ? hints.map((text, tier) => {
						const at = hintAvailableAt(tier, unlockedAt, c.id, shares, settings);
						const isRevealed = revealed.has(c.id + ":" + tier);
						return { tier, label: ["Nudge", "Push", "Shove"][tier] || "Hint", availableAt: at, available: Date.parse(at) <= now, revealed: isRevealed, text: isRevealed ? text : null };
					}) : [],
				};
			});
			const allSolved = chapters.length > 0 && chapters.every((c) => c.solved);
			const out = {
				whorl: k, title: w.title, intro: unlocked ? w.intro : null,
				unlocked, unlockedAt, solved: solves.has("w" + k), solvedAt: solves.get("w" + k) || null,
				answerKind: k <= 3 ? "square" : "key",
				puzzleOpen: unlocked && allSolved,
				puzzle: unlocked && allSolved ? w.puzzle : null,
				chapters,
			};
			if (unlocked && w.nudge) {
				const at = new Date(Date.parse(unlockedAt) + WHORL_NUDGE_DELAY).toISOString();
				const isRevealed = revealed.has("w" + k + ":3");
				out.nudge = { availableAt: at, available: Date.parse(at) <= now, revealed: isRevealed, text: isRevealed ? w.nudge : null };
			}
			if (k <= 3 && settings.vault) {
				const size = whorlSize(k);
				if (out.solved) out.square = squareAt(settings.vault.x, settings.vault.z, size);
				else if (unlocked) out.target = { size, parent: k === 1 ? { x: HUNT_WORLD_MIN, z: HUNT_WORLD_MIN, size: HUNT_WORLD_SIZE } : squareAt(settings.vault.x, settings.vault.z, whorlSize(k - 1)) };
			}
			return out;
		});

		let apex = null;
		if (solves.has("w4")) {
			apex = {
				title: settings.apex.title, riddle: settings.apex.riddle, marks: settings.apex.marks || [],
				solved: solves.has("apex"), solvedAt: solves.get("apex") || null,
				vaultSolved: solves.has("vault"), vaultSolvedAt: solves.get("vault") || null,
				claimed: (settings.shell.claims || []).some((c) => c.accountId === player.accountId),
			};
		}
		return {
			enabled: true,
			player: { ledger: ledger(player.ledgerNo), ledgerNo: player.ledgerNo, mcUsername: player.mcUsername, signedUpAt: player.signedUpAt, anonymous: !!player.anonymous, via: player.via },
			whorls, apex,
			shell: publicShell(settings),
			hunters: shares.total,
		};
	}
	function publicShell(settings) {
		const f = settings.shell.found;
		return f ? { found: true, by: f.showName ? f.name : null, at: f.at } : { found: false };
	}

	// ---- public status (the mod polls this) ----

	async function handleStatus(request, env, ctx) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return json({ enabled: false });
		const [shares, content] = await Promise.all([communityShares(env), loadContent(env)]);
		const milestones = [];
		for (const c of content.chapters) {
			const share = shares.share.get(c.id) || 0;
			if (share >= 0.25) milestones.push({ id: c.id + "-25", text: "A quarter of all hunters solved “" + c.title + "”. Its nudge is out for everyone." });
			if (share >= 0.5) milestones.push({ id: c.id + "-50", text: "Half of all hunters solved “" + c.title + "”. Its push is out for everyone." });
		}
		return json({
			enabled: true, testing: !s.enabled, signupOpen: !!s.signupOpen, hunters: shares.total,
			funnel: { w1: shares.count.get("w1") || 0, w2: shares.count.get("w2") || 0, w3: shares.count.get("w3") || 0, w4: shares.count.get("w4") || 0, apex: shares.count.get("apex") || 0 },
			broadcast: s.broadcast, milestones, shell: publicShell(s),
		});
	}

	// ---- trailhead: pairing the mod ----

	async function handlePairStart(request, env) {
		const s = await getSettings(env);
		const b = await body(request);
		const name = String((b && b.mcUsername) || "").trim();
		if (!s.enabled && !isTester(s, name)) return hidden();
		if (!MC_USERNAME.test(name)) return json({ error: "Bad request." }, 400);
		// a handful of open codes per name at most
		await env.DB.prepare("DELETE FROM huntPairings WHERE expiresAt < ? OR (lower(mcUsername) = lower(?) AND token IS NULL AND code NOT IN (SELECT code FROM huntPairings WHERE lower(mcUsername) = lower(?) ORDER BY createdAt DESC LIMIT 4))")
			.bind(nowIso(), name, name).run();
		const bytes = crypto.getRandomValues(new Uint8Array(6));
		const raw = [...bytes].map((x) => PAIR_ABC[x % PAIR_ABC.length]).join("");
		const code = raw.slice(0, 3) + "-" + raw.slice(3);
		const pollKey = newToken();
		const now = new Date();
		const expiresAt = new Date(now.getTime() + PAIR_TTL_MS).toISOString();
		await env.DB.prepare("INSERT INTO huntPairings (code, pollKey, mcUsername, createdAt, expiresAt) VALUES (?, ?, ?, ?, ?)").bind(code, pollKey, name, now.toISOString(), expiresAt).run();
		return json({ code, pollKey, expiresAt });
	}

	async function handlePairPoll(request, env) {
		const s = await getSettings(env);
		const key = new URL(request.url).searchParams.get("key") || "";
		const p = await env.DB.prepare("SELECT * FROM huntPairings WHERE pollKey = ?").bind(key).first();
		if (!s.enabled && !(p && isTester(s, p.mcUsername))) return hidden();
		if (!p) return json({ status: "expired" });
		if (p.token) {
			const player = await playerOf(env, p.accountId);
			await env.DB.prepare("DELETE FROM huntPairings WHERE pollKey = ?").bind(key).run();
			return json({ status: "done", token: p.token, ledger: player ? ledger(player.ledgerNo) : null });
		}
		if (Date.parse(p.expiresAt) < Date.now()) return json({ status: "expired" });
		return json({ status: "waiting" });
	}

	/** POST /hunt/signup {code, via?}: redeem a code from the mod with a verified account. */
	async function handleSignup(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env, { siteOnly: true });
		if (auth.response) return auth.response;
		const b = await body(request);
		const code = String((b && b.code) || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
		const pretty = code.slice(0, 3) + "-" + code.slice(3);
		const p = await env.DB.prepare("SELECT * FROM huntPairings WHERE code = ?").bind(pretty).first();
		if (!p || Date.parse(p.expiresAt) < Date.now() || p.token) return json({ error: "That code isn't valid (any more). Get a fresh one from the mod." }, 400);
		const admin = auth.admin;
		if (!admin.mcVerified || !admin.mcUsername) return json({ error: "Verify your Minecraft name on your account page first, then try the code again.", code: "verify" }, 403);
		if (mcKey(admin.mcUsername) !== mcKey(p.mcUsername)) return json({ error: "This code came from " + p.mcUsername + "'s game, but your account is verified as " + admin.mcUsername + "." }, 403);

		let player = await env.DB.prepare("SELECT * FROM huntPlayers WHERE accountId = ?").bind(admin.id).first();
		if (player && player.removed) return json({ error: "You've been removed from the hunt." }, 403);
		if (!player) {
			if (!s.signupOpen) return json({ error: "Sign-up is closed right now." }, 403);
			// one hunter per Minecraft account
			const taken = await env.DB.prepare("SELECT 1 AS x FROM huntPlayers WHERE lower(ltrim(mcUsername, '.')) = ?").bind(mcKey(admin.mcUsername)).first();
			if (taken) return json({ error: "Another account already hunts as " + admin.mcUsername + "." }, 403);
			if (s.requirePriorModUse) {
				const ping = await env.DB.prepare("SELECT firstSeenAt FROM modUserPings WHERE usernameHash = ?").bind(await modUserPingHash(env, admin.mcUsername)).first();
				if (!ping || (s.enabledAt && ping.firstSeenAt > s.enabledAt)) return json({ error: "The hunt is for players who already used the mod before it started." }, 403);
			}
			const set = await env.DB.prepare("SELECT setNo FROM huntSets WHERE accountId IS NULL ORDER BY setNo LIMIT 1").first();
			if (!set) return json({ error: "The hunt is full for the moment. Try again later today." }, 503);
			const max = await env.DB.prepare("SELECT MAX(ledgerNo) AS m FROM huntPlayers").first();
			const now = nowIso();
			const res = await env.DB.prepare("UPDATE huntSets SET accountId = ?, assignedAt = ? WHERE setNo = ? AND accountId IS NULL").bind(admin.id, now, set.setNo).run();
			if (!res.meta.changes) return json({ error: "Busy, try again." }, 409);
			await env.DB.prepare("INSERT INTO huntPlayers (accountId, mcUsername, ledgerNo, setNo, signedUpAt, via) VALUES (?, ?, ?, ?, ?, ?)")
				.bind(admin.id, admin.mcUsername, (max.m || 0) + 1, set.setNo, now, b && b.via === "spiral" ? "spiral" : "mod").run();
			player = await env.DB.prepare("SELECT * FROM huntPlayers WHERE accountId = ?").bind(admin.id).first();
		}
		const token = newToken();
		await env.DB.batch([
			env.DB.prepare("INSERT INTO huntTokens (token, accountId, mcUsername, createdAt) VALUES (?, ?, ?, ?)").bind(token, admin.id, p.mcUsername, nowIso()),
			env.DB.prepare("UPDATE huntPairings SET accountId = ?, token = ? WHERE code = ?").bind(admin.id, token, pretty),
		]);
		return json(await statePayload(env, s, player));
	}

	// ---- playing ----

	async function handleState(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env);
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		if (!player) return json({ enabled: true, player: null, signupOpen: !!s.signupOpen });
		return json(await statePayload(env, s, player));
	}

	/** POST /hunt/answer {ref, answer} — ref: a chapter id, 'w1'..'w4', 'apex' or 'vault'. Squares: answer {x, z, size}. */
	async function handleAnswer(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env);
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		if (!player) return json({ error: "Sign up for the hunt first." }, 403);
		const b = await body(request);
		const ref = String((b && b.ref) || "");
		const answer = b ? b.answer : null;
		const solves = await solvesOf(env, player.accountId);
		if (solves.has(ref)) return json({ error: "You already solved this one." }, 409);
		const unlocks = unlockTimes(player, solves);
		let correct = false, kind, shown = "";

		if (/^c\d+$/.test(ref)) {
			kind = "chapter";
			const ch = await env.DB.prepare("SELECT * FROM huntChapters WHERE id = ?").bind(ref).first();
			if (!ch || !unlocks[ch.whorl]) return json({ error: "That chapter isn't open for you yet." }, 403);
			const mine = await env.DB.prepare("SELECT answerKind, answerHash FROM huntSetChapters WHERE setNo = ? AND chapterId = ?").bind(player.setNo, ref).first();
			if (!mine) return json({ error: "This chapter isn't ready yet. The Shellkeeper has been told." }, 503);
			shown = normAnswer(mine.answerKind, answer);
			if (!shown) return json({ error: "Type an answer first." }, 400);
			const wait = await tooManyWrong(env, player.accountId, ref, kind);
			if (wait) return json({ error: "Too many wrong answers. Try again later.", retryAt: wait }, 429);
			correct = (await huntHash(env, player.setNo + ":" + ref, shown)) === mine.answerHash;
		} else if (/^w[1-4]$/.test(ref)) {
			kind = "whorl";
			const k = Number(ref.slice(1));
			if (!unlocks[k]) return json({ error: "That whorl isn't open for you yet." }, 403);
			const { results } = await env.DB.prepare("SELECT id FROM huntChapters WHERE whorl = ?").bind(k).all();
			if (!results.length || results.some((c) => !solves.has(c.id))) return json({ error: "Solve all four chapters of this whorl first." }, 403);
			if (k <= 3 && !s.vault) return json({ error: "This puzzle isn't ready yet." }, 503);
			if (k === 4 && !s.keyHash) return json({ error: "This puzzle isn't ready yet." }, 503);
			const wait = await tooManyWrong(env, player.accountId, ref, kind);
			if (wait) return json({ error: "That's three wrong tries today. Try again later.", retryAt: wait }, 429);
			if (k <= 3) {
				const size = whorlSize(k);
				const gx = Number(answer && answer.x), gz = Number(answer && answer.z), gs = Number(answer && answer.size);
				if (!Number.isFinite(gx) || !Number.isFinite(gz) || gs !== size) return json({ error: "Pick one " + size.toLocaleString("en-US") + "-block square on the map." }, 400);
				const want = squareAt(s.vault.x, s.vault.z, size), got = squareAt(gx, gz, size);
				shown = got.x + "," + got.z + "," + size;
				correct = want.x === got.x && want.z === got.z;
			} else {
				shown = normAnswer("numbers", answer);
				if (!shown) return json({ error: "Type the key's numbers." }, 400);
				correct = (await huntHash(env, "key", shown)) === s.keyHash;
			}
		} else if (ref === "apex") {
			kind = "apex";
			if (!solves.has("w4")) return json({ error: "Not yet." }, 403);
			if (!s.vault) return json({ error: "This puzzle isn't ready yet." }, 503);
			const nums = (String(answer || "").match(/-?\d+/g) || []).map(Number);
			if (nums.length !== 3) return json({ error: "Give the block as three numbers: x y z." }, 400);
			const wait = await tooManyWrong(env, player.accountId, ref, kind);
			if (wait) return json({ error: "Too many wrong answers. Try again later.", retryAt: wait }, 429);
			shown = nums.join(",");
			const t = Number(s.vaultTolerance) || 0;
			correct = Math.abs(nums[0] - s.vault.x) <= t && Math.abs(nums[1] - (s.vault.y ?? nums[1])) <= t && Math.abs(nums[2] - s.vault.z) <= t;
		} else if (ref === "vault") {
			kind = "vault";
			if (!solves.has("apex")) return json({ error: "Not yet." }, 403);
			if (!s.vaultWordHash) return json({ error: "This isn't ready yet." }, 503);
			shown = normAnswer("word", answer);
			if (!shown) return json({ error: "Type the word from the lectern." }, 400);
			const wait = await tooManyWrong(env, player.accountId, ref, kind);
			if (wait) return json({ error: "Too many wrong answers. Try again later.", retryAt: wait }, 429);
			correct = (await huntHash(env, "vault", shown)) === s.vaultWordHash;
		} else {
			return json({ error: "Unknown puzzle." }, 400);
		}

		await logGuess(env, player.accountId, ref, shown, correct);
		if (correct) await solve(env, player.accountId, ref);
		const state = await statePayload(env, s, player);
		return json({ correct, state });
	}

	/** POST /hunt/hint {chapterId, tier} — reveals a hint that's available (tier 3 + 'wN' = the whorl puzzle nudge). */
	async function handleHint(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env);
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		if (!player) return json({ error: "Sign up for the hunt first." }, 403);
		const b = await body(request);
		const id = String((b && b.chapterId) || ""), tier = Number(b && b.tier);
		const state = await statePayload(env, s, player);
		let ok = false;
		for (const w of state.whorls) {
			if (id === "w" + w.whorl && tier === 3 && w.nudge && w.nudge.available) ok = true;
			for (const c of w.chapters) if (c.id === id && c.hints[tier] && c.hints[tier].available) ok = true;
		}
		if (!ok) return json({ error: "That hint isn't available yet." }, 403);
		await env.DB.prepare("INSERT OR IGNORE INTO huntHints (accountId, chapterId, tier, at) VALUES (?, ?, ?, ?)").bind(player.accountId, id, tier, nowIso()).run();
		return json(await statePayload(env, s, player));
	}

	/** GET /hunt/notes (mod only): the notes the mod shows on its screens, for whorls that are open. */
	async function handleNotes(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env, { modOnly: true });
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		if (!player) return json({ notes: {} });
		const [solves, chapters, rows] = await Promise.all([
			solvesOf(env, player.accountId),
			env.DB.prepare("SELECT id, whorl FROM huntChapters").all(),
			env.DB.prepare("SELECT context, chapterId, text FROM huntSetNotes WHERE setNo = ?").bind(player.setNo).all(),
		]);
		const unlocks = unlockTimes(player, solves);
		const whorlOf = new Map(chapters.results.map((c) => [c.id, c.whorl]));
		const notes = {};
		for (const r of rows.results) {
			if (!MOD_NOTE_PREFIXES.some((p) => r.context.startsWith(p))) continue;
			if (!unlocks[whorlOf.get(r.chapterId)]) continue;
			notes[r.context] = r.text;
		}
		return json({ notes, ledger: ledger(player.ledgerNo) });
	}

	/** GET /hunt/door?slug= — a hidden page on sctp.nl (404.html asks before its short-link lookup). */
	async function handleDoor(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const slug = String(new URL(request.url).searchParams.get("slug") || "").toLowerCase().replace(/^\/+|\/+$/g, "");
		// the second way in: the What's new page spells SPIRAL
		if (slug === "spiral") return json({ redirect: "/hunt/?via=spiral" });
		const door = (s.doors || []).find((x) => String(x.slug).toLowerCase() === slug);
		if (!door) return hidden();
		const closed = { door: true, open: false, title: door.title || "", text: door.closed || "Closed." };
		const inWindow = (() => {
			if (!door.from || !door.to) return true;
			const now = new Date(), mins = now.getUTCHours() * 60 + now.getUTCMinutes();
			const toMin = (t) => { const [h, m] = String(t).split(":").map(Number); return h * 60 + (m || 0); };
			const a = toMin(door.from), b = toMin(door.to);
			return a <= b ? mins >= a && mins < b : mins >= a || mins < b;
		})();
		if (!inWindow) return json(closed);
		const auth = await huntAuth(request, env, { siteOnly: true });
		if (auth.response) return json({ ...closed, text: door.loggedOut || closed.text });
		const player = await playerOf(env, auth.accountId);
		if (!player) return json(closed);
		const note = await env.DB.prepare("SELECT n.text, c.whorl FROM huntSetNotes n JOIN huntChapters c ON c.id = n.chapterId WHERE n.setNo = ? AND n.context = ?").bind(player.setNo, "door:" + slug).first();
		if (!note) return json(closed);
		const unlocks = unlockTimes(player, await solvesOf(env, player.accountId));
		if (!unlocks[note.whorl]) return json(closed);
		return json({ door: true, open: true, title: door.title || "", text: note.text });
	}

	/** GET /hunt/image?key=img/<name> — personal pictures (R2, under hunt/), only for the hunter whose puzzles use them. */
	async function handleImage(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env);
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		const key = String(new URL(request.url).searchParams.get("key") || "");
		if (!player || !IMAGE_KEY.test(key)) return hidden();
		const mine = await env.DB.prepare("SELECT 1 AS x FROM huntSetChapters WHERE setNo = ? AND instr(content, ?) > 0 LIMIT 1").bind(player.setNo, JSON.stringify(key)).first();
		if (!mine) return hidden();
		const obj = await env.SNAPSHOTS.get("hunt/" + key);
		if (!obj) return hidden();
		return new Response(obj.body, { headers: { "Content-Type": obj.httpMetadata?.contentType || "image/webp", "Cache-Control": "private, max-age=3600", "Access-Control-Allow-Origin": "*" } });
	}

	/** POST /hunt/shell-claim {showName} — "I took the shell": you confirm it in admin. */
	async function handleShellClaim(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env);
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		if (!player) return json({ error: "Sign up for the hunt first." }, 403);
		const solves = await solvesOf(env, player.accountId);
		if (!solves.has("apex")) return json({ error: "Not yet." }, 403);
		const b = await body(request) || {};
		s.shell.claims = (s.shell.claims || []).filter((c) => c.accountId !== player.accountId);
		s.shell.claims.push({ accountId: player.accountId, name: player.mcUsername, showName: !!b.showName, at: nowIso() });
		s.shell.claims = s.shell.claims.slice(-20);
		await saveSettings(env, s);
		return json(await statePayload(env, s, player));
	}

	async function handlePrefs(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const auth = await huntAuth(request, env);
		if (auth.response) return auth.response;
		const player = await playerOf(env, auth.accountId);
		if (!player) return json({ error: "Sign up for the hunt first." }, 403);
		const b = await body(request) || {};
		await env.DB.prepare("UPDATE huntPlayers SET anonymous = ? WHERE accountId = ?").bind(b.anonymous ? 1 : 0, player.accountId).run();
		return json(await statePayload(env, s, { ...player, anonymous: b.anonymous ? 1 : 0 }));
	}

	// ---- leaderboard / Hall of Fame ----

	async function handleLeaderboard(request, env) {
		const s = await getSettings(env);
		if (!(await canSee(request, env, s))) return hidden();
		const [{ results: players }, { results: solves }, content] = await Promise.all([
			env.DB.prepare("SELECT accountId, mcUsername, ledgerNo, signedUpAt, anonymous FROM huntPlayers WHERE removed = 0").all(),
			env.DB.prepare("SELECT accountId, ref, solvedAt FROM huntSolves").all(),
			loadContent(env),
		]);
		const byId = new Map(players.map((p) => [p.accountId, p]));
		const name = (p) => (p.anonymous ? "Anonymous hunter" : p.mcUsername);
		const per = new Map();
		for (const r of solves) {
			if (!byId.has(r.accountId)) continue;
			if (!per.has(r.accountId)) per.set(r.accountId, new Map());
			per.get(r.accountId).set(r.ref, r.solvedAt);
		}
		const firsts = content.chapters.map((c) => {
			let best = null;
			for (const r of solves) if (r.ref === c.id && byId.has(r.accountId) && (!best || r.solvedAt < best.solvedAt)) best = r;
			return { chapterId: c.id, whorl: c.whorl, title: c.title, by: best ? name(byId.get(best.accountId)) : null, at: best ? best.solvedAt : null };
		});
		const fastest = [1, 2, 3, 4].map((k) => {
			const rows = [];
			for (const [id, m] of per) {
				const p = byId.get(id), open = k === 1 ? p.signedUpAt : m.get("w" + (k - 1)), done = m.get("w" + k);
				if (open && done) rows.push({ name: name(p), ms: Date.parse(done) - Date.parse(open) });
			}
			return { whorl: k, top: rows.sort((a, b) => a.ms - b.ms).slice(0, 10) };
		});
		const order = (ref) => solves.filter((r) => r.ref === ref && byId.has(r.accountId)).sort((a, b) => a.solvedAt.localeCompare(b.solvedAt))
			.map((r) => ({ name: name(byId.get(r.accountId)), ledger: ledger(byId.get(r.accountId).ledgerNo), at: r.solvedAt }));
		const count = (ref) => solves.filter((r) => r.ref === ref && byId.has(r.accountId)).length;
		return json({
			hunters: players.length,
			funnel: { w1: count("w1"), w2: count("w2"), w3: count("w3"), w4: count("w4"), apex: count("apex"), vault: count("vault") },
			firsts, fastest, apex: order("apex"), vault: order("vault"), shell: publicShell(s),
		});
	}

	// ---- Rare-dle boards (chapter 4): /raredle/state?mode=hunt&board=N, /raredle/guess {mode:'hunt', board} ----

	async function raredleHuntRow(env, player, board, cat) {
		let row = await env.DB.prepare("SELECT * FROM huntRaredle WHERE accountId = ? AND board = ?").bind(player.accountId, board).first();
		if (!row) {
			const note = await env.DB.prepare("SELECT text FROM huntSetNotes WHERE setNo = ? AND context = ?").bind(player.setNo, "secret:raredle:" + board).first();
			if (!note || !cat.byId.get(note.text)) return null;
			await env.DB.prepare("INSERT OR IGNORE INTO huntRaredle (accountId, board, answerId, updatedAt) VALUES (?, ?, ?, ?)").bind(player.accountId, board, note.text, nowIso()).run();
			row = await env.DB.prepare("SELECT * FROM huntRaredle WHERE accountId = ? AND board = ?").bind(player.accountId, board).first();
		}
		// a lost board comes back after an hour with another rare of the same first letter
		if (row.status === "lost" && row.resetAt && Date.parse(row.resetAt) <= Date.now()) {
			const old = cat.byId.get(row.answerId);
			const letter = (old ? old.name : "?").trim().charAt(0).toLowerCase();
			const pool = d.raredlePracticePool(cat).filter((i) => i.id !== row.answerId && i.name.trim().charAt(0).toLowerCase() === letter);
			const next = pool.length ? pool[crypto.getRandomValues(new Uint32Array(1))[0] % pool.length].id : row.answerId;
			await env.DB.prepare("UPDATE huntRaredle SET answerId = ?, guesses = '[]', status = 'playing', resetAt = NULL, updatedAt = ? WHERE accountId = ? AND board = ?").bind(next, nowIso(), player.accountId, board).run();
			row = await env.DB.prepare("SELECT * FROM huntRaredle WHERE accountId = ? AND board = ?").bind(player.accountId, board).first();
		}
		return row;
	}
	async function raredleHuntBoards(env, player) {
		const { results } = await env.DB.prepare("SELECT context FROM huntSetNotes WHERE setNo = ? AND context LIKE 'secret:raredle:%'").bind(player.setNo).all();
		return results.map((r) => Number(r.context.split(":")[2])).filter((n) => n > 0).sort((a, b) => a - b);
	}
	async function raredleHuntPayload(env, player, board, cat) {
		const boards = await raredleHuntBoards(env, player);
		const row = await raredleHuntRow(env, player, board, cat);
		if (!row) return null;
		const answer = cat.byId.get(row.answerId);
		const guesses = JSON.parse(row.guesses).map((id) => {
			const g = cat.byId.get(id);
			if (!g) return null;
			const feedback = d.raredleCompare(g, answer);
			return { itemId: id, name: g.name, texture: g.texture, feedback, note: d.raredleTwinNote(feedback, id, row.answerId) };
		}).filter(Boolean);
		const { results } = await env.DB.prepare("SELECT board, status FROM huntRaredle WHERE accountId = ?").bind(player.accountId).all();
		const statusOf = new Map(results.map((r) => [r.board, r.status]));
		const out = {
			date: new Date().toISOString().slice(0, 10), mode: "hunt", board, boards: boards.map((n) => ({ board: n, status: statusOf.get(n) || "playing" })),
			maxGuesses: d.RAREDLE_MAX_GUESSES, status: row.status, guesses,
			pixelHint: row.status === "playing" ? await d.raredlePixelHint(answer, guesses.length) : null,
			pixelSteps: d.RAREDLE_PIXEL_STEPS.slice().reverse(),
			noPeek: null, score: 0, resetAt: row.resetAt,
			stats: { played: 0, wins: 0, winRate: 0, avgGuesses: null, streak: 0, bestStreak: 0, points: 0 },
		};
		if (row.status === "won") out.answer = { id: answer.id, name: answer.name, texture: answer.texture, effect: answer.effect || "", category: answer.category, releaseDate: answer.releaseDate, obtainedFrom: answer.obtainedFrom };
		return out;
	}
	/** Shared gate for the Rare-dle hunt mode: a signed-up hunter whose chapter-4 whorl is open. */
	async function raredleHuntPlayer(env, accountId) {
		const s = await getSettings(env);
		const player = await playerOf(env, accountId);
		if (!s.enabled && !(player && isTester(s, player.mcUsername))) return { error: json({ error: "Not found" }, 404) };
		if (!player) return { error: json({ error: "Sign up for the hunt first." }, 403) };
		const boards = await raredleHuntBoards(env, player);
		if (!boards.length) return { error: json({ error: "You have no hunt boards." }, 404) };
		const note = await env.DB.prepare("SELECT c.whorl FROM huntSetNotes n JOIN huntChapters c ON c.id = n.chapterId WHERE n.setNo = ? AND n.context LIKE 'secret:raredle:%' LIMIT 1").bind(player.setNo).first();
		const unlocks = unlockTimes(player, await solvesOf(env, player.accountId));
		if (!note || !unlocks[note.whorl]) return { error: json({ error: "Not yet." }, 403) };
		return { player, boards };
	}
	async function raredleHuntState(env, accountId, board, cat) {
		const g = await raredleHuntPlayer(env, accountId);
		if (g.error) return g.error;
		const b = g.boards.includes(board) ? board : g.boards[0];
		return json(await raredleHuntPayload(env, g.player, b, cat));
	}
	async function raredleHuntGuess(env, accountId, board, itemId, cat) {
		const g = await raredleHuntPlayer(env, accountId);
		if (g.error) return g.error;
		if (!g.boards.includes(board)) return json({ error: "Unknown board." }, 400);
		if (!cat.byId.get(itemId)) return json({ error: "Pick a rare from the list." }, 400);
		const row = await raredleHuntRow(env, g.player, board, cat);
		if (row.status !== "playing") return json({ error: row.status === "won" ? "You already won this board." : "This board resets an hour after it was lost." }, 409);
		const ids = JSON.parse(row.guesses);
		if (ids.includes(itemId)) return json({ error: "You already guessed that one." }, 409);
		ids.push(itemId);
		const status = itemId === row.answerId ? "won" : ids.length >= d.RAREDLE_MAX_GUESSES ? "lost" : "playing";
		const resetAt = status === "lost" ? new Date(Date.now() + RAREDLE_RESET_MS).toISOString() : null;
		await env.DB.prepare("UPDATE huntRaredle SET guesses = ?, status = ?, resetAt = ?, updatedAt = ? WHERE accountId = ? AND board = ? AND status = 'playing'")
			.bind(JSON.stringify(ids), status, resetAt, nowIso(), g.player.accountId, board).run();
		return json(await raredleHuntPayload(env, g.player, board, cat));
	}

	// ---- admin ----

	async function adminGate(request, env) {
		const a = await requireAdminAuth(request, env, "hunt");
		return a.ok ? null : a.response;
	}

	async function handleAdminGet(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const s = await getSettings(env);
		const [content, sets, players, solveCounts, wrongCounts, hintCounts] = await Promise.all([
			loadContent(env),
			env.DB.prepare("SELECT COUNT(*) AS total, SUM(CASE WHEN accountId IS NULL THEN 0 ELSE 1 END) AS assigned FROM huntSets").first(),
			env.DB.prepare("SELECT p.accountId, p.mcUsername, p.ledgerNo, p.setNo, p.signedUpAt, p.via, p.anonymous, p.removed, (SELECT COUNT(*) FROM huntSolves s WHERE s.accountId = p.accountId) AS solved, (SELECT GROUP_CONCAT(ref) FROM huntSolves s WHERE s.accountId = p.accountId) AS refs FROM huntPlayers p ORDER BY p.ledgerNo").all(),
			env.DB.prepare("SELECT ref, COUNT(*) AS c FROM huntSolves GROUP BY ref").all(),
			env.DB.prepare("SELECT ref, COUNT(*) AS c FROM huntGuesses WHERE correct = 0 GROUP BY ref").all(),
			env.DB.prepare("SELECT chapterId, tier, COUNT(*) AS c FROM huntHints GROUP BY chapterId, tier").all(),
		]);
		const { keyHash, vaultWordHash, ...safe } = s;
		return json({
			settings: { ...safe, keySet: !!keyHash, vaultWordSet: !!vaultWordHash },
			chapters: content.chapters, whorls: content.whorls,
			sets: { total: sets.total || 0, assigned: sets.assigned || 0 },
			players: players.results,
			solves: Object.fromEntries(solveCounts.results.map((r) => [r.ref, r.c])),
			wrong: Object.fromEntries(wrongCounts.results.map((r) => [r.ref, r.c])),
			hints: hintCounts.results,
		});
	}

	/** POST /admin/hunt/settings — partial: only the fields sent change. key / vaultWord come in plain and are stored hashed. */
	async function handleAdminSettings(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const b = await body(request);
		if (!b) return json({ error: "Invalid JSON body" }, 400);
		const s = await getSettings(env);
		if ("enabled" in b) {
			s.enabled = !!b.enabled;
			if (s.enabled && !s.enabledAt) s.enabledAt = nowIso();
		}
		for (const k of ["signupOpen", "requirePriorModUse"]) if (k in b) s[k] = !!b[k];
		if (Array.isArray(b.testers)) s.testers = b.testers.map((n) => String(n).trim()).filter((n) => MC_USERNAME.test(n)).slice(0, 50);
		if ("vault" in b) {
			const v = b.vault;
			if (v === null) s.vault = null;
			else {
				const x = Number(v.x), y = v.y === "" || v.y == null ? null : Number(v.y), z = Number(v.z);
				if (!Number.isInteger(x) || !Number.isInteger(z) || (y !== null && !Number.isInteger(y))) return json({ error: "Vault coordinates must be whole numbers." }, 400);
				if (Math.abs(x) > 27000 || Math.abs(z) > 27000) return json({ error: "The vault must be inside the mapped area." }, 400);
				s.vault = { x, y, z };
			}
		}
		if ("vaultTolerance" in b) s.vaultTolerance = Math.max(0, Math.min(10, Number(b.vaultTolerance) || 0));
		if (typeof b.key === "string" && b.key.trim()) s.keyHash = await huntHash(env, "key", normAnswer("numbers", b.key));
		if (typeof b.vaultWord === "string" && b.vaultWord.trim()) s.vaultWordHash = await huntHash(env, "vault", normAnswer("word", b.vaultWord));
		if (b.apex) s.apex = { title: String(b.apex.title || "The Apex"), riddle: String(b.apex.riddle || ""), marks: Array.isArray(b.apex.marks) ? b.apex.marks.map((m) => ({ n: String(m.n || ""), x: Number(m.x), z: Number(m.z) })).filter((m) => Number.isFinite(m.x) && Number.isFinite(m.z)) : [] };
		if (Array.isArray(b.doors)) s.doors = b.doors.map((x) => ({ slug: String(x.slug || "").toLowerCase().replace(/[^a-z0-9-]/g, ""), title: String(x.title || ""), from: x.from || null, to: x.to || null, closed: String(x.closed || "Closed."), loggedOut: x.loggedOut ? String(x.loggedOut) : null })).filter((x) => x.slug);
		if (b.hintRelease && typeof b.hintRelease === "object") s.hintRelease = Object.fromEntries(Object.entries(b.hintRelease).map(([k, v]) => [k, Math.max(0, Math.min(3, Number(v) || 0))]));
		await saveSettings(env, s);
		return handleAdminGet(request, env);
	}

	/** POST /admin/hunt/content {chapters:[...], whorls:[...]} — the shared texts. */
	async function handleAdminContent(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const b = await body(request);
		if (!b) return json({ error: "Invalid JSON body" }, 400);
		const stmts = [];
		for (const c of b.chapters || []) {
			if (!/^c\d+$/.test(String(c.id)) || !(c.whorl >= 1 && c.whorl <= 4)) return json({ error: "Bad chapter: " + c.id }, 400);
			stmts.push(env.DB.prepare("INSERT OR REPLACE INTO huntChapters (id, whorl, ord, title, clue, hints, fragment) VALUES (?, ?, ?, ?, ?, ?, ?)")
				.bind(c.id, c.whorl, Number(c.ord) || 0, String(c.title || c.id), String(c.clue || ""), JSON.stringify((c.hints || []).map(String).slice(0, 3)), String(c.fragment || "")));
		}
		for (const w of b.whorls || []) {
			if (!(w.whorl >= 1 && w.whorl <= 4)) return json({ error: "Bad whorl: " + w.whorl }, 400);
			stmts.push(env.DB.prepare("INSERT OR REPLACE INTO huntWhorls (whorl, title, intro, puzzle, nudge) VALUES (?, ?, ?, ?, ?)")
				.bind(w.whorl, String(w.title || "Whorl " + w.whorl), String(w.intro || ""), String(w.puzzle || ""), String(w.nudge || "")));
		}
		if (stmts.length) await env.DB.batch(stmts);
		return handleAdminGet(request, env);
	}

	/**
	 * POST /admin/hunt/sets {sets:[{chapters:{c1:{content, answer, kind}}, notes:[{context, chapterId, text}]}]}
	 * — appends personal sets from the generator (at most 25 per call). Answers are hashed here.
	 */
	async function handleAdminSets(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const b = await body(request);
		if (!b || !Array.isArray(b.sets) || !b.sets.length || b.sets.length > 25) return json({ error: "Send 1 to 25 sets." }, 400);
		const max = await env.DB.prepare("SELECT MAX(setNo) AS m FROM huntSets").first();
		let setNo = (max.m || 0);
		const added = [];
		for (const set of b.sets) {
			setNo++;
			const stmts = [env.DB.prepare("INSERT INTO huntSets (setNo) VALUES (?)").bind(setNo)];
			for (const [chapterId, c] of Object.entries(set.chapters || {})) {
				const kind = c.kind === "numbers" ? "numbers" : "word";
				const norm = normAnswer(kind, c.answer);
				if (!norm) return json({ error: "Set " + (added.length + 1) + ", " + chapterId + ": empty answer." }, 400);
				stmts.push(env.DB.prepare("INSERT INTO huntSetChapters (setNo, chapterId, content, answerKind, answerHash) VALUES (?, ?, ?, ?, ?)")
					.bind(setNo, chapterId, JSON.stringify(c.content || []), kind, await huntHash(env, setNo + ":" + chapterId, norm)));
			}
			for (const n of set.notes || []) {
				stmts.push(env.DB.prepare("INSERT INTO huntSetNotes (setNo, context, chapterId, text) VALUES (?, ?, ?, ?)").bind(setNo, String(n.context), String(n.chapterId), String(n.text)));
			}
			await env.DB.batch(stmts);
			added.push(setNo);
		}
		return json({ added });
	}

	/** POST /admin/hunt/sets/clear — removes every set nobody has yet (to regenerate). */
	async function handleAdminSetsClear(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		await env.DB.batch([
			env.DB.prepare("DELETE FROM huntSetChapters WHERE setNo IN (SELECT setNo FROM huntSets WHERE accountId IS NULL)"),
			env.DB.prepare("DELETE FROM huntSetNotes WHERE setNo IN (SELECT setNo FROM huntSets WHERE accountId IS NULL)"),
			env.DB.prepare("DELETE FROM huntSets WHERE accountId IS NULL"),
		]);
		return handleAdminGet(request, env);
	}

	/** POST /admin/hunt/player {accountId, action: 'remove'|'restore'|'solve'|'unsolve'|'reassign'|'reset-raredle', ref?, board?} */
	async function handleAdminPlayer(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const b = await body(request) || {};
		const p = await env.DB.prepare("SELECT * FROM huntPlayers WHERE accountId = ?").bind(String(b.accountId || "")).first();
		if (!p) return json({ error: "No such hunter." }, 404);
		const now = nowIso();
		switch (b.action) {
			case "remove":
			case "restore":
				await env.DB.prepare("UPDATE huntPlayers SET removed = ? WHERE accountId = ?").bind(b.action === "remove" ? 1 : 0, p.accountId).run();
				if (b.action === "remove") await env.DB.prepare("DELETE FROM huntTokens WHERE accountId = ?").bind(p.accountId).run();
				break;
			case "solve":
				await env.DB.prepare("INSERT OR IGNORE INTO huntSolves (accountId, ref, solvedAt) VALUES (?, ?, ?)").bind(p.accountId, String(b.ref), now).run();
				break;
			case "unsolve":
				await env.DB.prepare("DELETE FROM huntSolves WHERE accountId = ? AND ref = ?").bind(p.accountId, String(b.ref)).run();
				break;
			case "reassign": {
				const set = await env.DB.prepare("SELECT setNo FROM huntSets WHERE accountId IS NULL ORDER BY setNo LIMIT 1").first();
				if (!set) return json({ error: "No spare sets left. Upload more first." }, 409);
				await env.DB.batch([
					env.DB.prepare("UPDATE huntSets SET accountId = ?, assignedAt = ? WHERE setNo = ?").bind(p.accountId, now, set.setNo),
					env.DB.prepare("UPDATE huntPlayers SET setNo = ? WHERE accountId = ?").bind(set.setNo, p.accountId),
					env.DB.prepare("DELETE FROM huntRaredle WHERE accountId = ?").bind(p.accountId),
				]);
				break;
			}
			case "delete":
				// wipes a (test) hunter completely, including their puzzle set
				await env.DB.batch([
					env.DB.prepare("DELETE FROM huntSolves WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntGuesses WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntHints WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntRaredle WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntTokens WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntPairings WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntSetChapters WHERE setNo IN (SELECT setNo FROM huntSets WHERE accountId = ?)").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntSetNotes WHERE setNo IN (SELECT setNo FROM huntSets WHERE accountId = ?)").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntSets WHERE accountId = ?").bind(p.accountId),
					env.DB.prepare("DELETE FROM huntPlayers WHERE accountId = ?").bind(p.accountId),
				]);
				break;
			case "reset-raredle":
				await env.DB.prepare("DELETE FROM huntRaredle WHERE accountId = ? AND board = ?").bind(p.accountId, Number(b.board)).run();
				break;
			default:
				return json({ error: "Unknown action." }, 400);
		}
		return handleAdminGet(request, env);
	}

	/** POST /admin/hunt/broadcast {text} — shown once to every hunter (mod toast, site banner). Empty text clears it. */
	async function handleAdminBroadcast(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const b = await body(request) || {};
		const s = await getSettings(env);
		const text = String(b.text || "").trim().slice(0, 300);
		s.broadcast = text ? { id: newToken().slice(0, 12), text, at: nowIso() } : null;
		await saveSettings(env, s);
		return handleAdminGet(request, env);
	}

	/** POST /admin/hunt/shell {action:'confirm'|'reset', accountId?} */
	async function handleAdminShell(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const b = await body(request) || {};
		const s = await getSettings(env);
		if (b.action === "confirm") {
			const c = (s.shell.claims || []).find((x) => x.accountId === b.accountId);
			if (!c) return json({ error: "No such claim." }, 404);
			s.shell.found = { accountId: c.accountId, name: c.name, showName: c.showName, at: nowIso() };
		} else if (b.action === "reset") {
			s.shell = { found: null, claims: [] };
		} else return json({ error: "Unknown action." }, 400);
		await saveSettings(env, s);
		return handleAdminGet(request, env);
	}

	/** POST /admin/hunt/image?key=img/<name> (raw body) — a personal picture from the generator. */
	async function handleAdminImage(request, env) {
		const deny = await adminGate(request, env);
		if (deny) return deny;
		const key = String(new URL(request.url).searchParams.get("key") || "");
		if (!IMAGE_KEY.test(key)) return json({ error: "Bad key: use img/<letters, digits, - or _>.webp|png" }, 400);
		await env.SNAPSHOTS.put("hunt/" + key, await request.arrayBuffer(), { httpMetadata: { contentType: request.headers.get("Content-Type") || "image/webp" } });
		return json({ ok: true, key });
	}

	return {
		raredleHuntState, raredleHuntGuess,
		routes: [
			["GET", "/hunt/status", handleStatus],
			["GET", "/hunt/state", handleState],
			["POST", "/hunt/signup", handleSignup],
			["POST", "/hunt/answer", handleAnswer],
			["POST", "/hunt/hint", handleHint],
			["GET", "/hunt/notes", handleNotes],
			["GET", "/hunt/door", handleDoor],
			["GET", "/hunt/image", handleImage],
			["POST", "/hunt/shell-claim", handleShellClaim],
			["POST", "/hunt/prefs", handlePrefs],
			["GET", "/hunt/leaderboard", handleLeaderboard],
			["POST", "/mod/hunt/pair/start", handlePairStart],
			["GET", "/mod/hunt/pair/poll", handlePairPoll],
			["GET", "/admin/hunt", handleAdminGet],
			["POST", "/admin/hunt/settings", handleAdminSettings],
			["POST", "/admin/hunt/content", handleAdminContent],
			["POST", "/admin/hunt/sets", handleAdminSets],
			["POST", "/admin/hunt/sets/clear", handleAdminSetsClear],
			["POST", "/admin/hunt/player", handleAdminPlayer],
			["POST", "/admin/hunt/broadcast", handleAdminBroadcast],
			["POST", "/admin/hunt/shell", handleAdminShell],
			["POST", "/admin/hunt/image", handleAdminImage],
		],
	};
}
