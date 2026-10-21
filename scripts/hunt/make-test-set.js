#!/usr/bin/env node
"use strict";
/**
 * Builds scripts/hunt/test-set.json: ONE complete puzzle set exactly like a real
 * hunter would get, made from live data (rares, base items, junk listings, the
 * ghost barrels, price history, maparts, TheShellkeeper's shelf and job, the
 * torn map piece). Every answer is checked here. The words are fixed; the
 * generator later picks them at random per hunter.
 *
 *   node scripts/hunt/make-test-set.js      -> writes test-set.json, prints the answer sheet
 *   HUNT_ADMIN_TOKEN=... node scripts/hunt/seed.js --test-set   -> uploads it
 *
 * Data snapshots live in scripts/hunt/data/ (price history, maparts, base items, the torn piece).
 */
const fs = require("fs");
const path = require("path");

const DATA = path.join(__dirname, "data");
const rares = JSON.parse(fs.readFileSync(path.join(__dirname, "../../data/rare-items.json"), "utf8"));
const shelf = JSON.parse(fs.readFileSync(path.join(__dirname, "shellkeeper.json"), "utf8"));
const baseItems = JSON.parse(fs.readFileSync(path.join(DATA, "base-items.json"), "utf8"));
const history = fs.readFileSync(path.join(DATA, "price-history.jsonl"), "utf8").trim().split("\n").map((l) => JSON.parse(l));
const maparts = fs.readFileSync(path.join(DATA, "maparts.jsonl"), "utf8").trim().split("\n").map((l) => JSON.parse(l));

const text = (v) => ({ t: "text", v });
const list = (items, ordered = true) => ({ t: "list", items, ordered });
const assert = (cond, msg) => { if (!cond) throw new Error("check failed: " + msg); };
const ok = (v) => v && v !== "null";
const letters = (s) => String(s).replace(/[^A-Za-z]/g, "");
/** Letter n (1-based) of a name, counting letters only. */
const letterAt = (s, n) => letters(s)[n - 1];
const cap = (s) => s.replace(/\b\w/g, (c) => c.toUpperCase());
const fmtDate = (d) => new Date(d + "T12:00:00Z").toLocaleDateString("en-GB", { day: "numeric", month: "long", timeZone: "UTC" });
const sheet = []; // the answer sheet, printed at the end

// ---- c1 The Impostor: rare descriptions by sheet fields, one fits no rare ----
function impostor(word) {
	const key = (r) => [r.category, r.releaseDate, r.obtainedFrom, r.typeSlot].join("|").toLowerCase();
	const counts = new Map();
	for (const r of rares) counts.set(key(r), (counts.get(key(r)) || 0) + 1);
	const usable = rares.filter((r) => ok(r.category) && ok(r.releaseDate) && ok(r.obtainedFrom) && ok(r.typeSlot) && counts.get(key(r)) === 1 && !/^\d/.test(r.name));
	const used = new Set(), lines = [];
	for (const ch of word.toLowerCase()) {
		const pick = usable.find((r) => !used.has(r.id) && letters(r.name).toLowerCase().indexOf(ch) >= 0 && letters(r.name).toLowerCase().indexOf(ch) < 10);
		assert(pick, "c1 letter " + ch);
		used.add(pick.id);
		const n = letters(pick.name).toLowerCase().indexOf(ch) + 1;
		lines.push({ n, r: pick, text: `[${n}] ${pick.category}, from ${pick.obtainedFrom}, released ${pick.releaseDate}, ${pick.typeSlot}.` });
	}
	let fake = null;
	outer: for (const a of usable) for (const b of usable) {
		const k = [a.category, b.releaseDate, b.obtainedFrom, a.typeSlot].join("|").toLowerCase();
		if (a.id !== b.id && !counts.has(k)) { fake = `[4] ${a.category}, from ${b.obtainedFrom}, released ${b.releaseDate}, ${a.typeSlot}.`; break outer; }
	}
	assert(fake, "c1 impostor");
	assert(lines.map((l) => letterAt(l.r.name, l.n)).join("").toLowerCase() === word.toLowerCase(), "c1 word");
	const items = lines.map((l) => l.text);
	items.splice(3, 0, fake);
	sheet.push("c1 " + word + ": " + lines.map((l) => `${l.r.name} [${l.n}] ${letterAt(l.r.name, l.n).toUpperCase()}`).join(" · ") + " · the impostor is line 4");
	return items;
}

// ---- c2 Same Bones: rares on one base item; positions in the A-Z list ----
function sameBones(base, word) {
	const byId = new Map(rares.map((r) => [r.id, r]));
	const all = Object.entries(baseItems).filter(([, b]) => b === base).map(([id]) => byId.get(id)).filter(Boolean)
		.map((r) => r.name).sort((a, b) => a.localeCompare(b));
	const positions = [...word.toLowerCase()].map((ch) => all.findIndex((n) => n.toLowerCase().startsWith(ch)) + 1);
	assert(positions.every((p) => p > 0), "c2 letters");
	assert(positions.map((p) => all[p - 1][0]).join("").toLowerCase() === word.toLowerCase(), "c2 word");
	const shown = all.filter((_, i) => !positions.includes(i + 1)).slice(0, 4); // four of them, not the answers
	sheet.push(`c2 ${word}: base ${base}, A-Z: ${all.join(" | ")}, positions ${positions.join(" ")}`);
	return { shown, positions };
}

// ---- c4 Daily Bread: board rares, first letters ----
// Same rule as Rare-dle (raredleDerivedIds in worker.js): never a rare that comes from
// another rare (its "obtained from" names a rare) or from a hand-picked chance box.
const RAREDLE_EXCLUDED_SOURCES = ["Pocket Pals Claw Machine", "Gills' Fishing Rod", "May the 4th Plushie Chance Box", "Easter 2025 Event", "D6",
	"Menagerie Sword", "White Cat & Associates Chance Box", "May the 4th Chance Box", "Archangel Armor Set"];
function derivedIds(items) {
	const norm = (s) => String(s == null ? "" : s).toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
	const names = new Map();
	for (const i of items) { const n = norm(i.name); if (n.length < 4) continue; if (!names.has(n)) names.set(n, []); names.get(n).push(i.id); }
	const out = new Set();
	for (const i of items) {
		const src = norm(i.obtainedFrom);
		if (!src || src === "null") continue;
		const words = src.split(" ");
		let found = false;
		for (let a = 0; a < words.length && !found; a++) for (let b = a; b < Math.min(words.length, a + 8) && !found; b++) {
			const ids = names.get(words.slice(a, b + 1).join(" "));
			if (ids && ids.some((id) => id !== i.id)) found = true;
		}
		if (found) out.add(i.id);
	}
	const excluded = new Set(RAREDLE_EXCLUDED_SOURCES.map(norm));
	for (const i of items) if (String(i.obtainedFrom == null ? "" : i.obtainedFrom).split(/s*[/,]s*/).some((p) => excluded.has(norm(p)))) out.add(i.id);
	return out;
}
function boards(word) {
	const derived = derivedIds(rares);
	const used = new Set();
	const picks = [...word.toLowerCase()].map((ch) => {
		const r = rares.find((x) => !used.has(x.id) && ok(x.texture) && ok(x.category) && ok(x.releaseDate) && ok(x.obtainedFrom) && ok(x.typeSlot) && !derived.has(x.id) && x.name.toLowerCase().startsWith(ch) && !/plush|token|key/i.test(x.name));
		assert(r, "c4 " + ch);
		used.add(r.id);
		return r;
	});
	sheet.push("c4 " + word + ": boards " + picks.map((r) => r.name).join(", "));
	return picks;
}

// ---- c5 Strange Prices: the junk listings, price = letter ----
const JUNK = ["Dead Bush", "Poisonous Potato", "Rotten Flesh", "Spider Eye", "Bone", "String", "Stick", "Dirt", "Gravel", "Cobblestone", "Wheat Seeds", "Kelp", "Pufferfish",
	"Lily Pad", "Clay Ball", "Feather", "Leather", "Ink Sac", "Flint", "Snowball", "Egg", "Sugar Cane", "Cactus", "Brown Mushroom", "Red Mushroom", "Fern"];
function strangePrices(word) {
	const items = [...word.toUpperCase()].map((c) => JUNK[c.charCodeAt(0) - 65]);
	assert(items.map((i) => String.fromCharCode(65 + JUNK.indexOf(i))).join("") === word.toUpperCase(), "c5");
	sheet.push("c5 " + word + ": " + items.map((i) => i + " " + (JUNK.indexOf(i) + 1)).join(", "));
	return items;
}

// ---- c7 Days Gone By: which of four was cheapest that day (lowest price, on both worlds) ----
function daysGoneBy(word) {
	const isPlain = (k) => { const [b, n] = k.slice(2).split("|"); return n === b.replace("minecraft:", "").replace(/_/g, " "); };
	const byDate = new Map();
	for (const h of history) {
		if (!isPlain(h.itemKey)) continue;
		const k = h.date + "|" + h.itemKey.split("|")[1];
		if (!byDate.has(k)) byDate.set(k, {});
		byDate.get(k)[h.world] = h.lowestPrice;
	}
	const dates = [...new Set(history.map((h) => h.date))].sort();
	const pool = (date) => [...byDate.entries()].filter(([k, v]) => k.startsWith(date + "|") && v.Firefly > 0 && v.Honeybee > 0).map(([k, v]) => ({ name: k.split("|")[1], v }));
	const used = new Set(), qs = [];
	[...word.toLowerCase()].forEach((ch, qi) => {
		const date = dates[qi % dates.length];
		const items = pool(date).filter((x) => !used.has(x.name));
		const cheap = items.filter((x) => letters(x.name).toLowerCase().indexOf(ch) >= 0 && letters(x.name).toLowerCase().indexOf(ch) < 8)
			.sort((a, b) => Math.max(a.v.Firefly, a.v.Honeybee) - Math.max(b.v.Firefly, b.v.Honeybee))[0];
		assert(cheap, "c7 " + ch);
		const top = Math.max(cheap.v.Firefly, cheap.v.Honeybee);
		const others = items.filter((x) => x !== cheap && Math.min(x.v.Firefly, x.v.Honeybee) >= top * 4).sort((a, b) => a.name.localeCompare(b.name)).slice(qi * 3, qi * 3 + 3);
		assert(others.length === 3, "c7 others " + ch);
		[cheap, ...others].forEach((x) => used.add(x.name));
		const n = letters(cheap.name).toLowerCase().indexOf(ch) + 1;
		qs.push({ date, group: [cheap, ...others].map((x) => cap(x.name)).sort(), n, answer: cap(cheap.name) });
	});
	assert(qs.map((q) => letterAt(q.answer, q.n)).join("").toLowerCase() === word.toLowerCase(), "c7 word");
	sheet.push("c7 " + word + ": " + qs.map((q) => `${q.date} cheapest ${q.answer} [${q.n}]`).join(" · "));
	return qs;
}

// ---- c8 Wanted: A Reader: the job's code, a personal key gives the word ----
function readerKey(word) {
	const code = shelf.job.code;
	assert(code.length === word.length, "c8 length");
	const key = [...word.toUpperCase()].map((c, i) => (code.charCodeAt(i) - c.charCodeAt(0) + 26) % 26);
	const back = [...code].map((c, i) => String.fromCharCode(((c.charCodeAt(0) - 65 - key[i] + 26) % 26) + 65)).join("");
	assert(back === word.toUpperCase(), "c8 decode");
	sheet.push(`c8 ${word}: job code ${code}, key ${key.join(" ")}`);
	return key;
}

// ---- c10 The Gallery: five maparts by size, category, artist and world ----
function gallery(word) {
	const uniq = new Map();
	for (const m of maparts) {
		if (!m.title || /^\//.test(m.title) || !m.artist || !m.category) continue;
		const k = [m.artist, m.category, m.width + "x" + m.height, m.world].join("|").toLowerCase();
		uniq.set(k, uniq.has(k) ? null : m);
	}
	// a description must point at one mapart in the whole catalog; only locked ones are used (no takedowns mid-hunt)
	const usable = [...uniq.values()].filter((m) => m && m.locked);
	const used = new Set(), out = [];
	for (const ch of word.toLowerCase()) {
		const m = usable.find((x) => !used.has(x.slug) && letters(x.title).toLowerCase().indexOf(ch) >= 0 && letters(x.title).toLowerCase().indexOf(ch) < 8);
		assert(m, "c10 " + ch);
		used.add(m.slug);
		out.push({ m, n: letters(m.title).toLowerCase().indexOf(ch) + 1 });
	}
	assert(out.map((o) => letterAt(o.m.title, o.n)).join("").toLowerCase() === word.toLowerCase(), "c10 word");
	sheet.push("c10 " + word + ": " + out.map((o) => `${o.m.title} (${o.m.artist}) [${o.n}]`).join(" · "));
	return out.map((o) => `[${o.n}] A ${o.m.width}×${o.m.height} ${o.m.category.toLowerCase()} piece by ${o.m.artist}, on ${o.m.world}.`);
}

// ---- c11 The Shelf: H = what I have, W = what I want, each A to Z ----
function theShelf(word) {
	const have = shelf.collection.map((x) => x.name).sort((a, b) => a.localeCompare(b));
	const want = shelf.wishlist.map((x) => x.name).sort((a, b) => a.localeCompare(b));
	const refs = [...word.toLowerCase()].map((ch, i) => {
		const lists = i % 2 ? [["W", want], ["H", have]] : [["H", have], ["W", want]];
		for (const [tag, l] of lists) { const p = l.findIndex((n) => n.toLowerCase().startsWith(ch)); if (p >= 0) return tag + (p + 1); }
		assert(false, "c11 " + ch);
	});
	const read = refs.map((r) => (r[0] === "H" ? have : want)[Number(r.slice(1)) - 1][0]).join("");
	assert(read.toLowerCase() === word.toLowerCase(), "c11 word");
	sheet.push("c11 " + word + ": " + refs.join(" ") + "  (have A-Z: " + have.join(" | ") + " / want A-Z: " + want.join(" | ") + ")");
	return refs;
}

// ---- c13 Wings for Coin: every 9th letter (9 kinds of butterfly for sale) ----
function wings(word, step = 9) {
	const abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	let seed = 7, out = "";
	const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
	for (let i = 0; i < word.length * step; i++) out += i % step === 0 ? word[i / step].toUpperCase() : abc[Math.floor(rnd() * 26)];
	assert([...out].filter((_, i) => i % step === 0).join("") === word.toUpperCase(), "c13");
	sheet.push(`c13 ${word}: every ${step}th letter, starting with the first`);
	return out.match(/.{1,5}/g).join(" ");
}

// ---- build ----
// Two hand-made sets so testers can start over with different answers:
//   node scripts/hunt/make-test-set.js     -> set "a", test-set.json
//   node scripts/hunt/make-test-set.js b   -> set "b", test-set-b.json
const VARIANTS = {
	a: {
		W: { c1: "NAUTILUS", c2: "SWAN", c3: "ANCHOR", c4: "CREEK", c5: "BEACON", c6: "CASTAWAY", c7: "OTTER", c8: "SEASHORE",
			c10: "INLET", c11: "COAST", c12: "TWILIGHT", c13: "GLADE", c14: "FROST", c15: "SNAIL", c16: "LANTERN" },
		c9: { region: [-20, 13], image: "img/torn-30e48936d7b8d031c5.webp" }, // 3x3 regions, turned a quarter and mirrored
		c14: "It comes after dusk and before the dew, and paints your windows white. Five letters.",
		c15: "Always home, never still. It carries its house and leaves a silver trail. Five letters.",
		c16: ["The first half of a light you carry: LAN", "The second half: TERN"],
	},
	b: {
		W: { c1: "PELICANS", c2: "SNUB", c3: "MARINE", c4: "SHOAL", c5: "HARBOR", c6: "SEAFARER", c7: "STORM", c8: "MAINSAIL",
			c10: "WAVES", c11: "BOATS", c12: "MOONRISE", c13: "REEDS", c14: "STARS", c15: "PEARL", c16: "LIGHTHOUSE" },
		c9: { region: [14, 22], image: "img/torn-fe29fc817f35f09f8e.webp" }, // 3x3 regions, turned three quarters and flipped
		c14: "They come out at night without being called, and are lost by day without being stolen. Five letters.",
		c15: "Born of a grain of sand inside a shell, worn round a neck. Five letters.",
		c16: ["Before the house: LIGHT", "The house itself: HOUSE"],
	},
};
const VARIANT = (process.argv[2] || "a").toLowerCase();
const V = VARIANTS[VARIANT];
assert(V, "unknown set " + VARIANT);
const W = V.W;
const C9_REGION = V.c9.region;
const C9_IMAGE = V.c9.image;

const c1 = impostor(W.c1);
const c2 = sameBones("minecraft:bow", W.c2);
const C3_ITEMS = ["clock", "compass", "feather", "bone", "paper", "string"];
assert(W.c3.length === C3_ITEMS.length, "c3 length");
sheet.push("c3 " + W.c3 + ": " + C3_ITEMS.map((it, i) => it + " " + W.c3[i]).join(", ") + " (X+I while holding each)");
const c4 = boards(W.c4);
const c5 = strangePrices(W.c5);
const BARRELS = shelf.ghostBarrels.barrels.slice().sort((a, b) => a.stock - b.stock);
assert(BARRELS.length === W.c6.length && new Set(BARRELS.map((b) => b.stock)).size === BARRELS.length, "c6 barrels");
sheet.push(`c6 ${W.c6}: barrels by stock ` + BARRELS.map((b, i) => `${b.stock}=${W.c6[i]}`).join(" "));
const c7 = daysGoneBy(W.c7);
const c8 = readerKey(W.c8);
sheet.push(`c9 ${C9_REGION.join(", ")}: the torn piece is the 3x3 regions around ${C9_REGION.join(", ")} (its centre), turned and mirrored`);
const c10 = gallery(W.c10);
const c11 = theShelf(W.c11);
const c12 = [...W.c12].sort().join(" ");
sheet.push(`c12 ${W.c12}: sctp.nl/fireflies (20:00-23:00 the hunter's own time, always open while testing; logged in) shows the letters ${c12}`);
const c13 = wings(W.c13);
sheet.push(`c14 ${W.c14}: watch the Dead Bush, the riddle sits in the Watchlist screen`);
sheet.push(`c15 ${W.c15}: search "Shellkeeper" in the mod, the riddle sits in the corner`);
sheet.push(`c16 ${W.c16}: the mod's home screen: "${V.c16[0]}" on Firefly, "${V.c16[1]}" on Honeybee`);

const set = {
	chapters: {
		c1: { kind: "word", answer: W.c1, content: [text("Nine treasures, in my own hand."), list(c1)] },
		c2: { kind: "word", answer: W.c2, content: [text("Four of mine: " + c2.shown.join(", ") + "."), text("Numbers: " + c2.positions.join(" · "))] },
		c3: { kind: "word", answer: W.c3, content: [text("Six letters, six things to hold. The first is the hour.")] },
		c4: { kind: "word", answer: W.c4, content: [text("Five boards. Their secret rares' first letters, board by board."), { t: "link", href: "/rare-dle/?mode=hunt", v: "Play your boards" }] },
		c5: { kind: "word", answer: W.c5, content: [text("What nobody wants, in the order I want it:"), list(c5)] },
		c6: { kind: "word", answer: W.c6, content: [text("Eight ghosts on Firefly. Eight letters, if you ask them in the right order.")] },
		c7: { kind: "word", answer: W.c7, content: [text("Five questions, five days, five letters."), list(c7.map((q) => `${fmtDate(q.date)}: which was cheapest: ${q.group.join(", ")}? [${q.n}]`))] },
		c8: { kind: "word", answer: W.c8, content: [text("Somebody is hiring on the marketplace. Apply, and read what comes back.")] },
		c9: { kind: "numbers", answer: C9_REGION.join(", "), content: [{ t: "image", key: C9_IMAGE, alt: "A torn piece of the Firefly map" }, text("Where does this belong? The region at its centre, please: two numbers.")] },
		c10: { kind: "word", answer: W.c10, content: [text("Five pieces I remember, and one letter from each name."), list(c10)] },
		c11: { kind: "word", answer: W.c11, content: [text(c11.join(" · "))] },
		c12: { kind: "word", answer: W.c12, content: [text("Not every page is signposted.")] },
		c13: { kind: "word", answer: W.c13, content: [text("Where wings are sold, I left you something to read.")] },
		c14: { kind: "word", answer: W.c14, content: [text("I sell what nobody wants. Keep an eye on the first of it.")] },
		c15: { kind: "word", answer: W.c15, content: [text("Ask the ledger in your game for me.")] },
		c16: { kind: "word", answer: W.c16, content: [text("Two worlds, one word.")] },
	},
	notes: [
		...C3_ITEMS.map((it, i) => ({ context: "item:minecraft:" + it, chapterId: "c3", text: ["First", "Second", "Third", "Fourth", "Fifth", "Sixth"][i] + ": " + W.c3[i] + ". " +
			["Next, hold what always points home.", "Next, hold what a chicken leaves behind when it flies.", "Next, hold what a skeleton is made of.",
				"Next, hold what you write on but never sign.", "Next, hold what a spider spins.", "That was the last."][i] })),
		...c4.map((r, i) => ({ context: "secret:raredle:" + (i + 1), chapterId: "c4", text: r.id })),
		...BARRELS.map((b, i) => ({ context: "chest:Firefly:" + b.pos.replace(/ /g, ""), chapterId: "c6", text: W.c6[i] })),
		{ context: "secret:job", chapterId: "c8", text: "Thank you for applying. Shift each letter of my description back by: " + c8.join(" ") },
		{ context: "door:fireflies", chapterId: "c12", text: "You found a door with no sign. Behind it, scattered letters: " + c12 },
		{ context: "butterfly", chapterId: "c13", text: c13 },
		{ context: "watch:dead bush", chapterId: "c14", text: V.c14 },
		{ context: "search:shellkeeper", chapterId: "c15", text: V.c15 },
		{ context: "search:theshellkeeper", chapterId: "c15", text: V.c15 },
		{ context: "home:Firefly", chapterId: "c16", text: V.c16[0] },
		{ context: "home:Honeybee", chapterId: "c16", text: V.c16[1] },
	],
};

fs.writeFileSync(path.join(__dirname, VARIANT === "a" ? "test-set.json" : "test-set-" + VARIANT + ".json"), JSON.stringify(set, null, "\t") + "\n");
console.log((VARIANT === "a" ? "test-set.json" : "test-set-" + VARIANT + ".json") + " written.\n");
for (const l of sheet.sort((a, b) => Number(a.split(" ")[0].slice(1)) - Number(b.split(" ")[0].slice(1)))) console.log(l);
