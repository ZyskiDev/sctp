// Creates one single-use verification link per mapart artist with at least
// MIN distinct maparts, and writes them to verification-links.txt / .csv.
//
//   ADMIN_TOKEN=<head admin session token or ADMIN_KEY> node scripts/make-verification-links.js [minMaparts=5] [days=31]
//
// PowerShell:  $env:ADMIN_TOKEN = "..."; node scripts/make-verification-links.js
//
// Get a session token by logging in to admin.html as a head admin and copying
// the value stored in localStorage (or use the master ADMIN_KEY secret).
// Two-artist pieces ("A & B") count for both artists; "distinct" = distinct
// image, so the same art in both worlds counts once.

const fs = require("fs");

const API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
const token = process.env.ADMIN_TOKEN;
const min = Number(process.argv[2]) || 5;
const days = Number(process.argv[3]) || 31;
if (!token) { console.error("Set ADMIN_TOKEN first."); process.exit(1); }

(async () => {
	const rows = await (await fetch(API + "/mapart")).json();
	const by = new Map();
	for (const m of rows) {
		if (!m.artist) continue;
		for (const a of m.artist.split(" & ")) {
			const k = a.toLowerCase();
			let e = by.get(k);
			if (!e) { e = { name: a, imgs: new Set() }; by.set(k, e); }
			e.imgs.add(m.imageHash);
		}
	}
	const artists = [...by.values()].map((e) => ({ name: e.name, n: e.imgs.size })).filter((e) => e.n >= min).sort((a, b) => b.n - a.n);
	console.log(`${artists.length} artists with ${min}+ maparts; creating ${days}-day links...`);

	const out = [];
	for (const e of artists) {
		const r = await fetch(API + "/admin/verification-links/create", {
			method: "POST",
			headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
			body: JSON.stringify({ mcUsername: e.name, days }),
		});
		const j = await r.json().catch(() => ({}));
		if (!r.ok) { console.error("FAILED", e.name, r.status, j.error || ""); if (r.status === 401 || r.status === 403) process.exit(1); continue; }
		out.push({ name: e.name, n: e.n, url: j.url, expiresAt: j.expiresAt });
	}
	fs.writeFileSync("verification-links.txt", out.map((o) => `${o.name} (${o.n} maparts): ${o.url}`).join("\n") + "\n");
	fs.writeFileSync("verification-links.csv", "username,maparts,link,expires\n" + out.map((o) => [o.name, o.n, o.url, o.expiresAt].join(",")).join("\n") + "\n");
	console.log(`Done: ${out.length} links written to verification-links.txt and verification-links.csv`);
})();
