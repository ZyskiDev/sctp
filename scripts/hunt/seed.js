#!/usr/bin/env node
"use strict";
/**
 * Uploads the Golden Shell's shared content (content.json) and, for testing, a
 * test puzzle set plus test settings (vault at 0, 0; key 1 2 3 4; ...).
 *
 *   HUNT_ADMIN_TOKEN=<an sctp.nl session token with the hunt permission> node scripts/hunt/seed.js --content
 *   ... --test-set        one test puzzle set (answers: see TEST_ANSWERS below)
 *   ... --test-settings   vault 0,64,0, key, lectern word, Apex, the fireflies door (always open)
 *
 * The real personal sets come from the generator (scripts/hunt/generate.js) later.
 */
const fs = require("fs");
const path = require("path");

const API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
const TOKEN = process.env.HUNT_ADMIN_TOKEN;
if (!TOKEN) { console.error("Set HUNT_ADMIN_TOKEN first."); process.exit(1); }

async function post(p, body) {
	const res = await fetch(API + p, { method: "POST", headers: { "Content-Type": "application/json", Authorization: "Bearer " + TOKEN }, body: JSON.stringify(body) });
	const d = await res.json().catch(() => ({}));
	if (!res.ok) throw new Error(p + ": " + (d.error || res.status));
	return d;
}
const text = (v) => ({ t: "text", v });

// One test set: every chapter's answer is known, and every mechanism gets used once.
const TEST_SET = {
	chapters: {
		c1: { kind: "word", answer: "ARCHIVE", content: [text("Test set. The real chapter has nine rare descriptions here."), text("Answer: the name of this whorl.")] },
		c2: { kind: "word", answer: "BONES", content: [text("Test set. The real chapter lists four rares with the same base item."), text("Answer: the last word of this chapter's title.")] },
		c3: { kind: "word", answer: "SPIN", content: [text("Test set: hold a clock and press X+I. The notes lead you through four items, one letter each.")] },
		c4: { kind: "word", answer: "CH", content: [text("Test set: win both of your Rare-dle boards. The answer is their first letters."), { t: "link", href: "/rare-dle/?mode=hunt", v: "Play your boards" }] },
		c5: { kind: "word", answer: "PRICES", content: [text("Test set. The real chapter sends you to the listings for the Shellkeeper's junk."), text("Answer: the last word of the title.")] },
		c6: { kind: "word", answer: "GHOST", content: [text("Test set: with stock holograms on (Settings), a gold line appears on the test chest (see the answer sheet for where).")] },
		c7: { kind: "word", answer: "HISTORY", content: [text("Test set. The real chapter asks about prices on past days."), { t: "list", items: ["Answer: what the item pages call the price chart (one word)."] }] },
		c8: { kind: "word", answer: "READER", content: [text("Test set. The real chapter has a marketplace job."), text("Answer: the last word of the title.")] },
		c9: { kind: "numbers", answer: "0, 0", content: [text("Test set. The real chapter shows a torn piece of the map."), text("Answer: the region of block 0, 0 on the world map.")] },
		c10: { kind: "word", answer: "GALLERY", content: [text("Test set. The real chapter describes five maparts."), text("Answer: the last word of the title.")] },
		c11: { kind: "word", answer: "SHELF", content: [text("Test set. The real chapter uses the Shellkeeper's collection."), text("Answer: the last word of the title.")] },
		c12: { kind: "word", answer: "DUSK", content: [text("Test set: the door is open all day while testing.")] },
		c13: { kind: "word", answer: "WINGS", content: [text("Test set: open the Butterfly Exchange. A gold note sits above it.")] },
		c14: { kind: "word", answer: "PAPER", content: [text("Test set: open a quest paper. A gold note sits above it.")] },
		c15: { kind: "word", answer: "CRAWL", content: [text("Test set: open Sefi's Creepy Crawly Exchange. A gold note sits above it.")] },
		c16: { kind: "word", answer: "POCKET", content: [text("Test set: your backpack on Firefly holds the first half, your ender chest on Honeybee the second.")] },
	},
	notes: [
		{ context: "item:minecraft:clock", chapterId: "c3", text: "Letter 1: S. Next, hold what always points home." },
		{ context: "item:minecraft:compass", chapterId: "c3", text: "Letter 2: P. Next, hold what a chicken leaves behind when it flies." },
		{ context: "item:minecraft:feather", chapterId: "c3", text: "Letter 3: I. Next, hold what a skeleton is made of." },
		{ context: "item:minecraft:bone", chapterId: "c3", text: "Letter 4: N. That's the last one." },
		{ context: "secret:raredle:1", chapterId: "c4", text: "rare-creepy-crawly-coin" },
		{ context: "secret:raredle:2", chapterId: "c4", text: "rare-halloween-2026-crate-key" },
		{ context: "chest:Firefly:0,64,0", chapterId: "c6", text: "Your word is GHOST" },
		{ context: "door:fireflies", chapterId: "c12", text: "You found the door. Your word is DUSK." },
		{ context: "butterfly", chapterId: "c13", text: "Your word is WINGS." },
		{ context: "quest", chapterId: "c14", text: "Your word is PAPER." },
		{ context: "sefi", chapterId: "c15", text: "Your word is CRAWL." },
		{ context: "bp:Firefly", chapterId: "c16", text: "First half: POC" },
		{ context: "ec:Honeybee", chapterId: "c16", text: "Second half: KET" },
	],
};

const TEST_SETTINGS = {
	vault: { x: 0, y: 64, z: 0 }, vaultTolerance: 2, key: "1 2 3 4", vaultWord: "PEARL",
	apex: { title: "The Apex", riddle: "Test riddle: the vault is at the gold mark 1, at height 64.\nAnswer with x y z.", marks: [{ n: "1", x: 0, z: 0 }, { n: "2", x: 300, z: 200 }] },
	doors: [{ slug: "fireflies", title: "A door with no sign", from: null, to: null, closed: "Closed. Come back when the fireflies are out." }],
};

(async () => {
	const args = new Set(process.argv.slice(2));
	if (args.has("--content")) {
		const c = JSON.parse(fs.readFileSync(path.join(__dirname, "content.json"), "utf8"));
		await post("/admin/hunt/content", { chapters: c.chapters, whorls: c.whorls });
		console.log("content: " + c.chapters.length + " chapters, " + c.whorls.length + " whorls");
	}
	if (args.has("--test-settings")) {
		await post("/admin/hunt/settings", TEST_SETTINGS);
		console.log("test settings saved");
	}
	if (args.has("--test-set")) {
		const r = await post("/admin/hunt/sets", { sets: [TEST_SET] });
		console.log("test set added: " + r.added.join(", "));
	}
})().catch((e) => { console.error(e.message); process.exit(1); });
