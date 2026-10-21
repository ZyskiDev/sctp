#!/usr/bin/env node
"use strict";
/**
 * Refreshes the data snapshots the puzzle generator reads (scripts/hunt/data/):
 *   price-history.jsonl  lowest prices of plain vanilla items, the last 6 weeks (both worlds)
 *   maparts.jsonl        the whole mapart catalog
 *   base-items.json      rare id -> base item (the same list the rare library filters on)
 * Rares come straight from data/rare-items.json (the site's own copy).
 *
 *   node scripts/hunt/export-data.js      (needs wrangler logged in, like a deploy)
 *
 * Run it right before generating the real sets.
 */
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
const WRANGLER = process.env.WRANGLER || path.join(process.env.APPDATA || "", "npm/node_modules/wrangler/bin/wrangler.js");
const WORKER_DIR = path.join(__dirname, "../../trading-post-api/trading-post-api");
const DATA = path.join(__dirname, "data");
const HISTORY_DAYS = 42;

function query(sql) {
	const out = execFileSync(process.execPath, [WRANGLER, "d1", "execute", "snailcraft-trading-post-db", "--remote", "--json", "--command", sql], {
		cwd: WORKER_DIR, encoding: "utf8", maxBuffer: 1 << 30, stdio: ["ignore", "pipe", "ignore"],
	});
	const json = JSON.parse(out.slice(out.search(/^\[/m)));
	return json[json.length - 1].results || [];
}
/** "v:minecraft:dead_bush|dead bush": a plain item, not a renamed one */
const isPlain = (k) => { const [b, n] = k.slice(2).split("|"); return n === b.replace("minecraft:", "").replace(/_/g, " "); };
const jsonl = (rows) => rows.map((r) => JSON.stringify(r)).join("\n") + "\n";

(async () => {
	// price history, one day at a time (keeps each answer small)
	const dates = query("SELECT DISTINCT date FROM itemDailyStats ORDER BY date DESC LIMIT " + (HISTORY_DAYS + 1)).map((r) => r.date).slice(1).reverse(); // today is still changing
	const hist = [];
	for (const d of dates) {
		const rows = query(`SELECT itemKey, world, date, lowestPrice FROM itemDailyStats WHERE date = '${d}' AND itemKey LIKE 'v:minecraft:%' AND listingCount >= 3`);
		hist.push(...rows.filter((r) => isPlain(r.itemKey)));
		process.stdout.write(".");
	}
	fs.writeFileSync(path.join(DATA, "price-history.jsonl"), jsonl(hist));
	console.log("\nprice history: " + hist.length + " rows, " + dates[0] + " to " + dates[dates.length - 1]);

	const maparts = [];
	for (let off = 0; ; off += 1000) {
		const rows = query(`SELECT slug, title, artist, category, width, height, world, locked FROM maparts ORDER BY slug LIMIT 1000 OFFSET ${off}`);
		maparts.push(...rows);
		if (rows.length < 1000) break;
	}
	fs.writeFileSync(path.join(DATA, "maparts.jsonl"), jsonl(maparts));
	console.log("maparts: " + maparts.length);

	const res = await fetch(API + "/rares/base-items");
	if (!res.ok) throw new Error("base items: " + res.status);
	const base = await res.json();
	fs.writeFileSync(path.join(DATA, "base-items.json"), JSON.stringify(base));
	console.log("base items: " + Object.keys(base).length);
})().catch((e) => { console.error(e.message); process.exit(1); });
