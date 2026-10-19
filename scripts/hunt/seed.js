#!/usr/bin/env node
"use strict";
/**
 * Uploads the Golden Shell's shared content (content.json) and, for testing, a
 * test puzzle set plus the real settings (vault, key, lectern word, Apex, doors).
 *
 *   HUNT_ADMIN_TOKEN=<an sctp.nl session token with the hunt permission> node scripts/hunt/seed.js --content
 *   ... --test-set        one test puzzle set (test-set.json, made by make-test-set.js)
 *   ... --test-settings   the real vault, key, lectern word, Apex and the fireflies door (20:00-23:00 in each hunter's own time)
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

// One test set with known answers: built (and checked) by make-test-set.js.
const TEST_SET = JSON.parse(fs.readFileSync(path.join(__dirname, "test-set.json"), "utf8"));

// The real hunt's settings: the vault at 26380, 66, -23633 (see content.json for the squares).
const TEST_SETTINGS = {
	vault: { x: 26380, y: 66, z: -23633 }, vaultTolerance: 2, key: "2 5 7 3", vaultWord: "NACRE",
	apex: {
		title: "The Apex",
		riddle: "Five marks I left on your map, and three of them are liars. The honest two keep their feet wet.\n" +
			"Of the honest pair, the one nearer the sunrise gives you its column; the other gives you its row.\n" +
			"Where column meets row, three blocks above the sea, my door is waiting.",
		// 2 and 4 are in the water (2 is further east: X 26380; 4 gives Z -23633); 1, 3 and 5 stand on land
		marks: [{ n: "1", x: 25700, z: -24400 }, { n: "2", x: 26380, z: -24300 }, { n: "3", x: 26088, z: -24088 }, { n: "4", x: 25750, z: -23633 }, { n: "5", x: 26150, z: -23600 }],
	},
	doors: [{ slug: "fireflies", title: "A door with no sign", from: "20:00", to: "23:00", closed: "Closed. Come back when the fireflies are out." }],
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
	if (args.has("--images")) {
		// every picture in scripts/hunt/data/ goes up as img/<file name> (the content refers to it by that key)
		for (const f of fs.readdirSync(path.join(__dirname, "data")).filter((n) => /\.(webp|png)$/.test(n))) {
			const res = await fetch(API + "/admin/hunt/image?key=" + encodeURIComponent("img/" + f), {
				method: "POST", headers: { Authorization: "Bearer " + TOKEN, "Content-Type": f.endsWith(".png") ? "image/png" : "image/webp" },
				body: fs.readFileSync(path.join(__dirname, "data", f)),
			});
			console.log("image img/" + f + ": " + (res.ok ? "uploaded" : "failed " + res.status));
		}
	}
	if (args.has("--test-set")) {
		const r = await post("/admin/hunt/sets", { sets: [TEST_SET] });
		console.log("test set added: " + r.added.join(", "));
	}
})().catch((e) => { console.error(e.message); process.exit(1); });
