// Shared collection view: the checklist + stats for one account's rare items
// and mapart. Editable on the account page (Collections tab), read-only on the
// public /collection/<username> page — shared like account-widget.js since
// both need the identical UI.
//
//   window.sctpCollection.mount(hostEl, { username?, editable })
//     editable -> the logged-in account's own collection (checkboxes, privacy switch)
//     otherwise -> public view of `username` (404.html routes /collection/<username> here)
//
// Collections are per world (Firefly / Honeybee). The catalogs come from
// /data/rare-items.json and GET /mapart; ownership from the /collection API.
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var WORLDS = ["Firefly", "Honeybee"];
	var PAGE = 120;
	var CHUNK = 1500; // ids per /collection/set call (server max 2000)

	var style = document.createElement("style");
	style.textContent =
		".col-wrap{font-family:inherit;color:var(--text,#EAEFE7);}" +
		".col-top{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;margin-bottom:14px;}" +
		".col-pills{display:flex;gap:8px;flex-wrap:wrap;}" +
		".col-pill{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--muted,#8FA593);border-radius:999px;padding:6px 16px;font-size:13.5px;font-weight:600;cursor:pointer;font-family:inherit;}" +
		".col-pill.on{background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border-color:transparent;}" +
		".col-privacy{display:flex;align-items:center;gap:8px;font-size:13px;color:var(--muted,#8FA593);flex-wrap:wrap;}" +
		".col-privacy a{color:var(--accent,#B7E23D);}" +
		".col-cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin-bottom:18px;}" +
		".col-card{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:12px;padding:14px 16px;}" +
		".col-card .k{font-size:11.5px;color:var(--muted,#8FA593);text-transform:uppercase;letter-spacing:.04em;font-weight:600;}" +
		".col-card .v{font-size:26px;font-weight:700;font-family:var(--font-display,inherit);margin:2px 0 6px;}" +
		".col-card .v small{font-size:14px;color:var(--muted,#8FA593);font-weight:500;}" +
		".col-card .sub{font-size:12.5px;color:var(--muted,#8FA593);margin-top:6px;}" +
		".col-bar{height:7px;background:var(--panel-alt,#22332A);border-radius:999px;overflow:hidden;}" +
		".col-bar>i{display:block;height:100%;background:var(--accent,#B7E23D);border-radius:999px;}" +
		".col-h{font-family:var(--font-display,inherit);font-size:16px;margin:22px 0 10px;}" +
		".col-break{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:8px 22px;margin-bottom:6px;}" +
		".col-brow .top{display:flex;justify-content:space-between;font-size:13px;margin-bottom:3px;gap:8px;}" +
		".col-brow .top span:last-child{color:var(--muted,#8FA593);font-family:var(--font-mono,monospace);font-size:12px;white-space:nowrap;}" +
		".col-tools{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 12px;align-items:center;}" +
		".col-tools input,.col-tools select{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 11px;font-size:13.5px;font-family:inherit;}" +
		".col-tools input[type=search]{flex:1;min-width:150px;}" +
		".col-tools button{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 12px;font-size:12.5px;cursor:pointer;font-family:inherit;}" +
		".col-tools button:hover{border-color:var(--accent-dim,#87AE29);}" +
		".col-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px;}" +
		".col-item{position:relative;background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:10px;padding:8px;text-align:center;font-size:12.5px;}" +
		".col-item.own{border-color:var(--accent-dim,#87AE29);background:rgba(183,226,61,0.07);}" +
		".col-item.editable{cursor:pointer;user-select:none;}" +
		".col-item .im{height:74px;display:flex;align-items:center;justify-content:center;margin-bottom:6px;}" +
		".col-item .im img{max-width:100%;max-height:74px;image-rendering:pixelated;}" +
		".col-item.miss .im img{opacity:.55;filter:grayscale(.6);}" +
		".col-item .nm{line-height:1.25;overflow-wrap:anywhere;}" +
		".col-item .nm small{display:block;color:var(--muted,#8FA593);font-size:11px;margin-top:1px;}" +
		".col-item .ck{position:absolute;top:6px;right:6px;width:20px;height:20px;border-radius:50%;border:1px solid var(--line,#33453A);background:var(--panel-alt,#22332A);font-size:12px;line-height:18px;color:transparent;}" +
		".col-item.own .ck{background:var(--accent,#B7E23D);border-color:transparent;color:var(--accent-ink,#16210F);font-weight:700;}" +
		".col-more{display:block;margin:14px auto 0;background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px 20px;cursor:pointer;font-family:inherit;}" +
		".col-msg{color:var(--muted,#8FA593);font-size:13.5px;padding:10px 0;}" +
		".col-msg.err{color:#E27D6B;}" +
		".col-recent{display:flex;flex-wrap:wrap;gap:8px;}" +
		".col-recent span{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);border-radius:999px;padding:3px 11px;font-size:12.5px;}";
	document.head.appendChild(style);

	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
	function pct(a, b) { return b ? Math.round((a / b) * 1000) / 10 : 0; }
	function bar(a, b) { return '<div class="col-bar"><i style="width:' + pct(a, b) + '%"></i></div>'; }
	function session() { return window.sctpAccount ? window.sctpAccount.getSession() : null; }
	function api(path, opts) {
		opts = opts || {};
		var headers = {};
		var s = session();
		if (s) headers["Authorization"] = "Bearer " + s.token;
		if (opts.body) headers["Content-Type"] = "application/json";
		return fetch(API_BASE + path, { method: opts.method || "GET", headers: headers, body: opts.body })
			.then(function (r) { return r.json().catch(function () { return {}; }).then(function (d) { return { ok: r.ok, status: r.status, data: d }; }); });
	}
	function yearOf(rd) {
		var m = /(\d{4})/.exec(String(rd || ""));
		return m ? m[1] : "Unknown";
	}

	var catalogPromise = null;
	function loadCatalogs() {
		if (!catalogPromise) {
			catalogPromise = Promise.all([
				fetch("/data/rare-items.json").then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
				fetch(API_BASE + "/mapart").then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
			]).then(function (a) { return { rare: a[0], mapart: a[1] }; });
		}
		return catalogPromise;
	}

	function mount(host, opts) {
		var editable = !!opts.editable;
		var st = {
			world: "Firefly", kind: "rare", status: "all", cat: "", q: "", shown: PAGE,
			owned: {},       // "kind|world|id" -> addedAt
			private: false, username: opts.username || "", cat_: null,
		};
		function key(kind, world, id) { return kind + "|" + world + "|" + id; }
		host.innerHTML = '<div class="col-wrap"><div class="col-msg">Loading collection…</div></div>';

		var load = editable ? api("/collection/mine") : api("/collection/public?username=" + encodeURIComponent(opts.username));
		Promise.all([load, loadCatalogs()]).then(function (res) {
			var r = res[0], cat = res[1];
			if (!r.ok) { host.innerHTML = '<div class="col-wrap"><div class="col-msg err">' + esc(r.data.error || "Couldn't load this collection.") + "</div></div>"; return; }
			if (r.data.private && !editable) {
				host.innerHTML = '<div class="col-wrap"><div class="col-msg">' + esc(r.data.username) + "'s collection is private.</div></div>";
				return;
			}
			st.cat_ = cat;
			st.username = r.data.username || st.username;
			st.private = !!r.data.private;
			(r.data.items || []).forEach(function (i) { st.owned[key(i.kind, i.world, i.itemId)] = i.addedAt || ""; });
			// Mapart ids never seen in the catalog (deleted since) just don't count.
			st.mapartById = {};
			cat.mapart.forEach(function (m) { st.mapartById[m.id] = m; });
			render();
		});

		function rareList() { return st.cat_.rare; }
		function mapartList() { return st.cat_.mapart.filter(function (m) { return m.world === st.world; }); }
		function isOwned(kind, id) { return Object.prototype.hasOwnProperty.call(st.owned, key(kind, st.world, id)); }
		function catOfRare(it) { return it.category || "Other"; }
		function catOfMapart(m) { return m.category || "No category"; }

		function counts(kind) {
			var list = kind === "rare" ? rareList() : mapartList();
			var have = 0;
			list.forEach(function (x) { if (isOwned(kind, x.id)) have++; });
			return { have: have, total: list.length };
		}

		function breakdown(list, kind, keyFn, sortByTotal) {
			var map = {};
			list.forEach(function (x) {
				var k = keyFn(x);
				if (!map[k]) map[k] = { have: 0, total: 0 };
				map[k].total++;
				if (isOwned(kind, x.id)) map[k].have++;
			});
			var keys = Object.keys(map);
			keys.sort(function (a, b) { return sortByTotal ? map[b].total - map[a].total || a.localeCompare(b) : a.localeCompare(b); });
			return keys.map(function (k) { return { k: k, have: map[k].have, total: map[k].total }; });
		}
		function breakHtml(rows) {
			return '<div class="col-break">' + rows.map(function (r) {
				return '<div class="col-brow"><div class="top"><span>' + esc(r.k) + "</span><span>" + r.have + " / " + r.total + " &middot; " + pct(r.have, r.total) + "%</span></div>" + bar(r.have, r.total) + "</div>";
			}).join("") + "</div>";
		}

		function statsHtml() {
			var rc = counts("rare"), mc = counts("mapart");
			var have = rc.have + mc.have, total = rc.total + mc.total;
			var other = WORLDS.filter(function (w) { return w !== st.world; })[0];
			var otherHave = 0;
			Object.keys(st.owned).forEach(function (k) { if (k.split("|")[1] === other && (k.split("|")[0] === "rare" || st.mapartById[k.split("|").slice(2).join("|")])) otherHave++; });
			var rareList_ = rareList(), mapList = mapartList();

			// Mapart artists: how much of each artist's work you own (top 12 by pieces in this world).
			var artistRows = breakdown(mapList.filter(function (m) { return m.artist; }), "mapart", function (m) { return m.artist; }, true).slice(0, 12);
			var sizeRows = breakdown(mapList, "mapart", function (m) { return m.width * m.height > 1 ? m.width + "×" + m.height : "Single map"; }, true);
			var recent = Object.keys(st.owned).map(function (k) {
				var p = k.split("|"); return { kind: p[0], world: p[1], id: p.slice(2).join("|"), at: st.owned[k] };
			}).filter(function (x) { return x.world === st.world; }).sort(function (a, b) { return a.at < b.at ? 1 : -1; }).slice(0, 8).map(function (x) {
				var it = x.kind === "rare" ? rareList_.filter(function (r) { return r.id === x.id; })[0] : st.mapartById[x.id];
				return it ? (x.kind === "rare" ? it.name : it.title) : null;
			}).filter(Boolean);

			return '<div class="col-cards">' +
				'<div class="col-card"><div class="k">Rare items</div><div class="v">' + rc.have + " <small>/ " + rc.total + "</small></div>" + bar(rc.have, rc.total) + '<div class="sub">' + pct(rc.have, rc.total) + "% &middot; " + (rc.total - rc.have) + " still needed</div></div>" +
				'<div class="col-card"><div class="k">Mapart (' + esc(st.world) + ')</div><div class="v">' + mc.have + " <small>/ " + mc.total + "</small></div>" + bar(mc.have, mc.total) + '<div class="sub">' + pct(mc.have, mc.total) + "% &middot; " + (mc.total - mc.have) + " still needed</div></div>" +
				'<div class="col-card"><div class="k">Overall (' + esc(st.world) + ')</div><div class="v">' + pct(have, total) + "<small>%</small></div>" + bar(have, total) + '<div class="sub">' + have + " of " + total + " collected</div></div>" +
				'<div class="col-card"><div class="k">' + esc(other) + '</div><div class="v">' + otherHave + ' <small>collected</small></div><div class="sub">Switch world above to see its full breakdown.</div></div>' +
				"</div>" +
				(recent.length ? '<div class="col-h">Recently added</div><div class="col-recent">' + recent.map(function (n) { return "<span>" + esc(n) + "</span>"; }).join("") + "</div>" : "") +
				'<div class="col-h">Rare items by category</div>' + breakHtml(breakdown(rareList_, "rare", catOfRare, false)) +
				'<div class="col-h">Rare items by release year</div>' + breakHtml(breakdown(rareList_, "rare", function (x) { return yearOf(x.releaseDate); }, false)) +
				'<div class="col-h">Mapart by category</div>' + (mapList.length ? breakHtml(breakdown(mapList, "mapart", catOfMapart, false)) : '<div class="col-msg">No mapart in this world yet.</div>') +
				(mapList.length ? '<div class="col-h">Mapart by size</div>' + breakHtml(sizeRows) : "") +
				(artistRows.length ? '<div class="col-h">Mapart by artist <span style="font-size:12px;color:var(--muted,#8FA593);font-family:inherit;">(top 12 by pieces)</span></div>' + breakHtml(artistRows) : "");
		}

		function filtered() {
			var list = st.kind === "rare" ? rareList() : mapartList();
			var q = st.q.trim().toLowerCase();
			return list.filter(function (x) {
				var own = isOwned(st.kind, x.id);
				if (st.status === "owned" && !own) return false;
				if (st.status === "missing" && own) return false;
				var c = st.kind === "rare" ? catOfRare(x) : catOfMapart(x);
				if (st.cat && c !== st.cat) return false;
				if (q) {
					var hay = st.kind === "rare" ? (x.name + " " + (x.effect || "")) : (x.title + " " + (x.artist || ""));
					if (hay.toLowerCase().indexOf(q) === -1) return false;
				}
				return true;
			});
		}

		function itemHtml(x) {
			var own = isOwned(st.kind, x.id);
			var img = st.kind === "rare" ? x.texture : API_BASE + "/mapart/image?id=" + encodeURIComponent(x.id) + "&v=" + encodeURIComponent(x.imageHash || "");
			var name = st.kind === "rare" ? x.name : x.title;
			var sub = st.kind === "rare" ? (x.category || "") : (x.artist ? "by " + x.artist : "");
			return '<div class="col-item ' + (own ? "own" : "miss") + (editable ? " editable" : "") + '" data-id="' + esc(x.id) + '" title="' + esc(name) + '">' +
				'<span class="ck">&#10003;</span><div class="im"><img src="' + esc(img) + '" alt="" loading="lazy"></div>' +
				'<div class="nm">' + esc(name) + (sub ? "<small>" + esc(sub) + "</small>" : "") + "</div></div>";
		}

		function render() {
			var s = session();
			var link = location.origin + "/collection/" + encodeURIComponent(st.username);
			var cats = st.kind === "rare"
				? breakdown(rareList(), "rare", catOfRare, false).map(function (r) { return r.k; })
				: breakdown(mapartList(), "mapart", catOfMapart, false).map(function (r) { return r.k; });
			var list = filtered();
			host.innerHTML =
				'<div class="col-wrap">' +
				(editable ? "" : '<h2 style="font-family:var(--font-display,inherit);margin:0 0 4px;">' + esc(st.username) + "'s collection</h2>") +
				'<div class="col-top"><div class="col-pills">' +
					WORLDS.map(function (w) { return '<button type="button" class="col-pill' + (w === st.world ? " on" : "") + '" data-world="' + w + '">' + w + "</button>"; }).join("") +
				"</div>" +
				(editable ? '<label class="col-privacy"><input type="checkbox" id="colPrivate"' + (st.private ? " checked" : "") + '> Keep my collection private' +
					(st.private ? "" : ' &middot; <a href="' + esc(link) + '" target="_blank" rel="noopener">public page</a> <button type="button" id="colCopy" class="col-pill" style="padding:3px 10px;font-size:12px;">Copy link</button>') + "</label>" : "") +
				"</div>" +
				(editable && !s ? "" : "") +
				'<div id="colStats">' + statsHtml() + "</div>" +
				'<div class="col-h">Checklist</div>' +
				'<div class="col-pills" style="margin-bottom:10px;">' +
					'<button type="button" class="col-pill' + (st.kind === "rare" ? " on" : "") + '" data-kind="rare">Rare items</button>' +
					'<button type="button" class="col-pill' + (st.kind === "mapart" ? " on" : "") + '" data-kind="mapart">Mapart</button>' +
				"</div>" +
				'<div class="col-tools"><input type="search" id="colQ" placeholder="Search…" value="' + esc(st.q) + '">' +
					'<select id="colCat"><option value="">All categories</option>' + cats.map(function (c) { return '<option value="' + esc(c) + '"' + (st.cat === c ? " selected" : "") + ">" + esc(c) + "</option>"; }).join("") + "</select>" +
					'<select id="colStatus">' + [["all", "All"], ["owned", "Have"], ["missing", "Need"]].map(function (o) { return '<option value="' + o[0] + '"' + (st.status === o[0] ? " selected" : "") + ">" + o[1] + "</option>"; }).join("") + "</select>" +
					(editable ? '<button type="button" id="colAll">Mark shown as owned</button><button type="button" id="colNone">Unmark shown</button>' : "") +
				"</div>" +
				'<div class="col-msg" id="colCount"></div>' +
				'<div class="col-grid" id="colGrid"></div><div id="colMoreHost"></div><div class="col-msg err" id="colErr" hidden></div>' +
				"</div>";
			bind();
			paintGrid(list);
		}

		function paintGrid(list) {
			list = list || filtered();
			var grid = host.querySelector("#colGrid");
			grid.innerHTML = list.slice(0, st.shown).map(itemHtml).join("");
			host.querySelector("#colCount").textContent = list.length + " " + (st.kind === "rare" ? "rare item" : "mapart") + (list.length === 1 ? "" : "s") + (list.length > st.shown ? " (showing " + st.shown + ")" : "");
			var more = host.querySelector("#colMoreHost");
			more.innerHTML = list.length > st.shown ? '<button type="button" class="col-more" id="colMore">Show more</button>' : "";
			var mb = host.querySelector("#colMore");
			if (mb) mb.onclick = function () { st.shown += PAGE; paintGrid(); };
		}

		function refreshStats() { host.querySelector("#colStats").innerHTML = statsHtml(); }
		function showErr(t) { var e = host.querySelector("#colErr"); e.textContent = t; e.hidden = !t; }

		// Optimistic: flip locally, tell the server, roll back if it refuses.
		function setOwned(ids, own) {
			var kind = st.kind, world = st.world, now = new Date().toISOString();
			var before = {};
			ids.forEach(function (id) {
				var k = key(kind, world, id);
				before[id] = Object.prototype.hasOwnProperty.call(st.owned, k) ? st.owned[k] : undefined;
				if (own) st.owned[k] = st.owned[k] || now; else delete st.owned[k];
			});
			showErr("");
			var chunks = [];
			for (var i = 0; i < ids.length; i += CHUNK) chunks.push(ids.slice(i, i + CHUNK));
			return chunks.reduce(function (p, chunk) {
				return p.then(function (ok) {
					if (!ok) return false;
					return api("/collection/set", { method: "POST", body: JSON.stringify({ kind: kind, world: world, ids: chunk, owned: own }) }).then(function (r) {
						if (!r.ok) showErr(r.data.error || "Couldn't save that change.");
						return r.ok;
					});
				});
			}, Promise.resolve(true)).then(function (ok) {
				if (!ok) {
					ids.forEach(function (id) {
						var k = key(kind, world, id);
						if (before[id] === undefined) delete st.owned[k]; else st.owned[k] = before[id];
					});
				}
				refreshStats();
				paintGrid();
			});
		}

		function bind() {
			host.querySelectorAll("[data-world]").forEach(function (b) {
				b.onclick = function () { st.world = b.getAttribute("data-world"); st.cat = ""; st.shown = PAGE; render(); };
			});
			host.querySelectorAll("[data-kind]").forEach(function (b) {
				b.onclick = function () { st.kind = b.getAttribute("data-kind"); st.cat = ""; st.shown = PAGE; render(); };
			});
			host.querySelector("#colQ").oninput = function (e) { st.q = e.target.value; st.shown = PAGE; paintGrid(); };
			host.querySelector("#colCat").onchange = function (e) { st.cat = e.target.value; st.shown = PAGE; paintGrid(); };
			host.querySelector("#colStatus").onchange = function (e) { st.status = e.target.value; st.shown = PAGE; paintGrid(); };
			if (!editable) return;
			host.querySelector("#colGrid").onclick = function (e) {
				var el = e.target.closest ? e.target.closest(".col-item") : null;
				if (!el) return;
				var id = el.getAttribute("data-id");
				setOwned([id], !isOwned(st.kind, id));
			};
			function bulk(own) {
				var ids = filtered().filter(function (x) { return isOwned(st.kind, x.id) !== own; }).map(function (x) { return x.id; });
				if (!ids.length) return;
				if (!confirm((own ? "Mark " : "Unmark ") + ids.length + " item" + (ids.length === 1 ? "" : "s") + (own ? " as owned" : " as not owned") + " in " + st.world + "?")) return;
				setOwned(ids, own);
			}
			host.querySelector("#colAll").onclick = function () { bulk(true); };
			host.querySelector("#colNone").onclick = function () { bulk(false); };
			host.querySelector("#colPrivate").onchange = function (e) {
				var want = e.target.checked;
				api("/collection/privacy", { method: "POST", body: JSON.stringify({ private: want }) }).then(function (r) {
					if (r.ok) { st.private = want; render(); } else { e.target.checked = !want; showErr(r.data.error || "Couldn't change privacy."); }
				});
			};
			var copy = host.querySelector("#colCopy");
			if (copy) copy.onclick = function () {
				var link = location.origin + "/collection/" + encodeURIComponent(st.username);
				if (navigator.clipboard) navigator.clipboard.writeText(link).then(function () { copy.textContent = "Copied!"; });
			};
		}
	}

	window.sctpCollection = { mount: mount };
})();
