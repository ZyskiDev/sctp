// Secret Santa: shared bits for /secret-santa/, /secret-santa/reveal/ and
// /secret-santa/admin/ — the API helpers, date/price formatting and the
// wishlist editor (rares, vanilla items, Firefly mapart and written wishes,
// each with SCTP's suggested value).
(function () {
	"use strict";
	var API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var MAX_WISHES = 20;
	var MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

	function $(id) { return document.getElementById(id); }
	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
	function session() {
		if (window.sctpAccount) return window.sctpAccount.getSession();
		try { var s = JSON.parse(localStorage.getItem("sctp_session") || "null"); return s && s.token && Date.parse(s.expiresAt) >= Date.now() ? s : null; } catch (e) { return null; }
	}
	function api(path, opts) {
		opts = opts || {};
		var headers = {};
		if (opts.body !== undefined) headers["Content-Type"] = "application/json";
		var s = session();
		if (s && opts.auth !== false) headers.Authorization = "Bearer " + s.token;
		return fetch(API + path, { method: opts.method || (opts.body !== undefined ? "POST" : "GET"), headers: headers, body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined })
			.then(function (r) {
				return r.json().catch(function () { return {}; }).then(function (d) {
					if (!r.ok) { var err = new Error(d.error || ("Something went wrong (" + r.status + ")")); err.status = r.status; throw err; }
					return d;
				});
			});
	}
	/** 351 -> "39 DB", 352 -> "39 DB 1 dia"; estimates (not whole) -> "12.4 DB". 1 STX = 64 DB = 576 dia. */
	function fmtDia(d) {
		if (d == null) return "";
		if (Math.round(d) !== d) return d >= 576 ? (Math.round(d / 576 * 10) / 10) + " STX" : d >= 9 ? (Math.round(d / 9 * 10) / 10) + " DB" : (Math.round(d * 10) / 10) + " dia";
		var stx = Math.floor(d / 576), db = Math.floor((d % 576) / 9), dia = d % 9, parts = [];
		if (stx) parts.push(stx + " STX");
		if (db) parts.push(db + " DB");
		if (dia || !parts.length) parts.push(dia + " dia");
		return parts.join(" ");
	}
	/** A wishlist value: per item, or per 64 when one is worth less than a diamond (e.g. "≈ 2 dia per 64"). */
	function fmtValue(w, v) {
		if (v == null) return "";
		if (w.kind === "item" && v < 1) return "≈ " + fmtDia(Math.round(v * 64 * 10) / 10) + " per 64";
		return "≈ " + fmtDia(v) + (w.kind === "item" ? " each" : "");
	}
	/** "2026-11-30" -> "30 November" (add the year with withYear). */
	function fmtDay(date, withYear) {
		var p = String(date || "").split("-");
		if (p.length !== 3) return "";
		return (+p[2]) + " " + MONTHS[+p[1] - 1] + (withYear ? " " + p[0] : "");
	}
	function fmtStamp(iso) {
		var d = new Date(iso);
		if (isNaN(d)) return "";
		return d.toLocaleDateString(undefined, { day: "numeric", month: "short" }) + " " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
	}
	var CURRENCY_LABEL = { diamond: "dia", diamondblock: "DB", diamondstack: "STX" };
	function fmtMoney(amount, currency) {
		if (amount == null) return "";
		return amount + " " + (CURRENCY_LABEL[String(currency || "").toLowerCase()] || currency || "");
	}
	var CATEGORY = {
		high: { label: "High value", short: "48 DB+", text: "a gift worth 48 DB or more" },
		low: { label: "Low value", short: "16 DB+", text: "a gift worth 16 DB or more" },
		both: { label: "Both", short: "48 DB+ and 16 DB+", text: "two gifts: one worth 48 DB or more and one worth 16 DB or more" }
	};
	function avatar(name, size) {
		return '<img class="ss-head" src="https://mc-heads.net/avatar/' + encodeURIComponent(String(name || "").replace(/^[.*]/, "")) + '/' + (size || 40) + '" width="' + (size || 40) + '" height="' + (size || 40) + '" alt="" loading="lazy">';
	}
	function mapartImage(id, hash) { return API + "/mapart/image?id=" + encodeURIComponent(id) + "&v=" + encodeURIComponent(hash || ""); }
	function wishIcon(w) {
		if (w.kind === "rare" || w.kind === "item") return w.texture ? '<img class="ss-ico" src="' + esc(w.texture) + '" alt="" loading="lazy">' : '<span class="ss-ico ss-ico-blank"></span>';
		if (w.kind === "mapart") return '<img class="ss-ico ss-pixel" src="' + esc(mapartImage(w.id, w.imageHash)) + '" alt="" loading="lazy">';
		return '<span class="ss-ico ss-ico-text" aria-hidden="true">&#9998;</span>';
	}
	function wishName(w) { return w.kind === "text" ? w.text : w.name; }
	var KIND_LABEL = { rare: "Rare", item: "Item", mapart: "Mapart", text: "Written wish" };

	// ---------------- catalog (loaded once, when the wishlist search is first used) ----------------
	var catalogPromise = null;
	function loadCatalog() {
		if (catalogPromise) return catalogPromise;
		var get = function (url) { return fetch(url).then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }); };
		catalogPromise = Promise.all([get("/data/rare-items.json"), get("/data/vanilla-items.json"), get(API + "/mapart")]).then(function (res) {
			var all = [];
			(res[0] || []).forEach(function (r) { all.push({ kind: "rare", id: r.id, name: r.name, texture: r.texture || null, sub: r.category || "Rare" }); });
			(res[1] || []).forEach(function (v) { all.push({ kind: "item", baseItem: v.baseItem, name: v.name, texture: v.texture || null, sub: "Item" }); });
			(Array.isArray(res[2]) ? res[2] : []).forEach(function (m) {
				if (m.world === "Firefly") all.push({ kind: "mapart", id: m.id, name: m.title, imageHash: m.imageHash || null, sub: "Mapart" + (m.artist ? " by " + m.artist : "") });
			});
			all.forEach(function (x) { x.low = x.name.toLowerCase(); });
			var rareById = {}, mapartById = {};
			all.forEach(function (x) { if (x.kind === "rare") rareById[x.id] = x; if (x.kind === "mapart") mapartById[x.id] = x; });
			return { all: all, rareById: rareById, mapartById: mapartById };
		});
		return catalogPromise;
	}

	// SCTP's suggested value per wishlist item (rares and items only)
	var valueCache = {};
	function valueOf(w) {
		var q = w.kind === "rare" ? "kind=rare&id=" + encodeURIComponent(w.id)
			: w.kind === "item" ? "kind=item&baseItem=" + encodeURIComponent(w.baseItem) + "&name=" + encodeURIComponent(w.name) : null;
		if (!q) return Promise.resolve({ value: null, basis: null });
		if (!valueCache[q]) valueCache[q] = api("/secret-santa/value?" + q, { auth: false }).catch(function () { return { value: null, basis: null }; });
		return valueCache[q];
	}
	function wishKey(w) { return w.kind + "|" + (w.id || w.baseItem || "") + "|" + String(wishName(w) || "").toLowerCase(); }

	/**
	 * The wishlist editor. opts: {list, onImport (function returning a promise
	 * of items, or null to hide the import button), onChange}.
	 * Returns {get(), set(list)}.
	 */
	function wishEditor(root, opts) {
		opts = opts || {};
		var list = (opts.list || []).slice();
		var uid = "ssw" + Math.random().toString(36).slice(2, 8);
		root.classList.add("ss-wish");
		root.innerHTML =
			'<div class="ss-wish-top">' +
				'<div class="ss-wish-search"><input type="search" id="' + uid + 'q" autocomplete="off" placeholder="Search rares, items and mapart, or type any wish" aria-label="Add to your wishlist">' +
				'<div class="ss-wish-results" id="' + uid + 'r" hidden></div></div>' +
				(opts.onImport ? '<button type="button" class="btn-ghost" id="' + uid + 'i">Import my SCTP wishlist</button>' : '') +
			'</div>' +
			'<p class="ss-wish-hint" id="' + uid + 'h"></p>' +
			'<ul class="ss-wish-list" id="' + uid + 'l"></ul>';
		var q = $(uid + "q"), results = $(uid + "r"), ul = $(uid + "l"), hint = $(uid + "h");
		var shown = [], active = -1;

		function changed() { render(); if (opts.onChange) opts.onChange(list); }
		function add(w) {
			if (list.length >= MAX_WISHES) { hint.textContent = "Your wishlist is full (" + MAX_WISHES + " items)."; hint.classList.add("bad"); return false; }
			var k = wishKey(w);
			if (list.some(function (x) { return wishKey(x) === k; })) { hint.textContent = wishName(w) + " is already on your wishlist."; hint.classList.add("bad"); return false; }
			var item = { kind: w.kind };
			if (w.kind === "rare") { item.id = w.id; item.name = w.name; item.texture = w.texture; }
			else if (w.kind === "item") { item.baseItem = w.baseItem; item.name = w.name; item.texture = w.texture; }
			else if (w.kind === "mapart") { item.id = w.id; item.name = w.name; item.imageHash = w.imageHash; }
			else item.text = String(w.text || "").trim().slice(0, 100);
			list.push(item);
			changed();
			return true;
		}
		function render() {
			hint.classList.remove("bad");
			hint.textContent = list.length ? list.length + " / " + MAX_WISHES + " items" : "Nothing on your wishlist yet. Add what you'd love to get: rares, items, mapart, or anything else in your own words.";
			ul.innerHTML = list.map(function (w, i) {
				return '<li data-i="' + i + '">' + wishIcon(w) +
					'<div class="ss-wish-main"><div class="ss-wish-name">' + esc(wishName(w)) + '</div>' +
					'<div class="ss-wish-meta"><span class="ss-kind">' + KIND_LABEL[w.kind] + '</span><span class="ss-val" data-val="' + i + '"></span></div></div>' +
					'<button type="button" class="ss-x" data-rm="' + i + '" aria-label="Remove ' + esc(wishName(w)) + '">&times;</button></li>';
			}).join("");
			list.forEach(function (w, i) {
				if (w.kind !== "rare" && w.kind !== "item") return;
				valueOf(w).then(function (v) {
					var el = ul.querySelector('[data-val="' + i + '"]');
					if (!el || list[i] !== w) return;
					if (v.value != null) { el.textContent = fmtValue(w, v.value); el.title = "SCTP's suggested value, from " + (v.basis || "Firefly listings"); }
					else { el.textContent = "no SCTP value yet"; el.classList.add("none"); }
				});
			});
		}
		ul.addEventListener("click", function (e) {
			var b = e.target.closest("[data-rm]");
			if (!b) return;
			list.splice(+b.getAttribute("data-rm"), 1);
			changed();
		});

		function search() {
			var text = q.value.trim();
			if (!text) { results.hidden = true; return; }
			loadCatalog().then(function (cat) {
				if (q.value.trim() !== text) return;
				var low = text.toLowerCase(), starts = [], has = [];
				for (var i = 0; i < cat.all.length && starts.length < 40; i++) {
					var x = cat.all[i];
					if (x.low.indexOf(low) === 0) starts.push(x);
					else if (has.length < 40 && x.low.indexOf(low) > 0) has.push(x);
				}
				var order = { rare: 0, item: 1, mapart: 2 };
				var sort = function (a, b) { return order[a.kind] - order[b.kind] || a.name.length - b.name.length; };
				shown = starts.sort(sort).concat(has.sort(sort)).slice(0, 10);
				shown.push({ kind: "text", text: text, name: text, sub: "Written wish" });
				active = 0;
				drawResults();
			});
		}
		function drawResults() {
			results.innerHTML = shown.map(function (x, i) {
				var label = x.kind === "text" ? 'Add &ldquo;' + esc(x.text) + '&rdquo; as a written wish' : esc(x.name);
				return '<button type="button" class="ss-res' + (i === active ? " on" : "") + '" data-res="' + i + '">' + wishIcon(x) +
					'<span class="ss-res-name">' + label + '</span><span class="ss-res-sub">' + esc(x.kind === "text" ? "" : x.sub) + '</span></button>';
			}).join("");
			results.hidden = !shown.length;
		}
		function pick(i) {
			var x = shown[i];
			if (!x) return;
			if (add(x)) { q.value = ""; results.hidden = true; }
			q.focus();
		}
		var timer = null;
		q.addEventListener("input", function () { clearTimeout(timer); timer = setTimeout(search, 120); });
		q.addEventListener("focus", function () { loadCatalog(); if (q.value.trim()) search(); });
		q.addEventListener("keydown", function (e) {
			if (results.hidden) { if (e.key === "Enter") e.preventDefault(); return; }
			if (e.key === "ArrowDown") { e.preventDefault(); active = Math.min(shown.length - 1, active + 1); drawResults(); }
			else if (e.key === "ArrowUp") { e.preventDefault(); active = Math.max(0, active - 1); drawResults(); }
			else if (e.key === "Enter") { e.preventDefault(); pick(active); }
			else if (e.key === "Escape") { results.hidden = true; }
		});
		results.addEventListener("mousedown", function (e) { e.preventDefault(); }); // keep focus in the box
		results.addEventListener("click", function (e) { var b = e.target.closest("[data-res]"); if (b) pick(+b.getAttribute("data-res")); });
		document.addEventListener("click", function (e) { if (!root.contains(e.target)) results.hidden = true; });

		if (opts.onImport) {
			$(uid + "i").addEventListener("click", function () {
				var btn = this;
				btn.disabled = true;
				hint.classList.remove("bad");
				hint.textContent = "Importing your Firefly wishlist…";
				opts.onImport().then(function (items) {
					var added = 0;
					items.forEach(function (w) { if (list.length < MAX_WISHES && !list.some(function (x) { return wishKey(x) === wishKey(w); })) { list.push(w); added++; } });
					changed();
					hint.textContent = added ? "Imported " + added + " item" + (added === 1 ? "" : "s") + " from your SCTP wishlist." : items.length ? "Everything on your SCTP wishlist is already here." : "Your Firefly wishlist on SCTP is empty. Star items on My Collection to add them.";
				}).catch(function (err) { hint.textContent = err.message; hint.classList.add("bad"); })
					.then(function () { btn.disabled = false; });
			});
		}
		render();
		return { get: function () { return list.slice(); }, set: function (l) { list = (l || []).slice(); render(); } };
	}

	/** The signed-in account's Firefly wishlist from My Collection, as wishlist items. */
	function importAccountWishlist() {
		return Promise.all([api("/collection/mine"), loadCatalog()]).then(function (res) {
			var cat = res[1];
			return (res[0].wishes || []).filter(function (w) { return w.world === "Firefly"; }).map(function (w) {
				var x = w.kind === "rare" ? cat.rareById[w.itemId] : cat.mapartById[w.itemId];
				if (!x) return null;
				return x.kind === "rare" ? { kind: "rare", id: x.id, name: x.name, texture: x.texture } : { kind: "mapart", id: x.id, name: x.name, imageHash: x.imageHash };
			}).filter(Boolean);
		});
	}

	/** "Don't match me with": up to `max` Minecraft names as removable chips. */
	function nameChips(root, opts) {
		opts = opts || {};
		var max = opts.max || 5, names = (opts.list || []).slice();
		var NAME = /^[.*]?[A-Za-z0-9_]{2,16}$/;
		var uid = "ssn" + Math.random().toString(36).slice(2, 8);
		root.classList.add("ss-chips");
		root.innerHTML = '<div class="ss-chip-row" id="' + uid + 'c"></div>' +
			'<div class="ss-chip-add"><input type="text" id="' + uid + 'i" maxlength="17" autocomplete="off" spellcheck="false" placeholder="Full Minecraft username" aria-label="Minecraft username not to be matched with">' +
			'<button type="button" class="btn-ghost" id="' + uid + 'b">Add</button></div><p class="ss-chip-msg" id="' + uid + 'm"></p>';
		var row = $(uid + "c"), input = $(uid + "i"), msg = $(uid + "m");
		function render() {
			row.innerHTML = names.map(function (n, i) { return '<span class="ss-chip">' + esc(n) + '<button type="button" data-rm="' + i + '" aria-label="Remove ' + esc(n) + '">&times;</button></span>'; }).join("");
			input.disabled = names.length >= max;
			$(uid + "b").disabled = names.length >= max;
			input.placeholder = names.length >= max ? "That's the maximum (" + max + ")" : "Full Minecraft username";
		}
		function add() {
			var v = input.value.trim();
			msg.textContent = "";
			if (!v) return;
			if (!NAME.test(v)) { msg.textContent = "That doesn't look like a Minecraft username."; return; }
			if (opts.self && v.toLowerCase() === opts.self().toLowerCase()) { msg.textContent = "That's you!"; return; }
			if (names.some(function (n) { return n.toLowerCase() === v.toLowerCase(); })) { msg.textContent = v + " is already on the list."; return; }
			if (names.length >= max) return;
			names.push(v);
			input.value = "";
			render();
		}
		$(uid + "b").addEventListener("click", add);
		input.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); add(); } });
		row.addEventListener("click", function (e) { var b = e.target.closest("[data-rm]"); if (b) { names.splice(+b.getAttribute("data-rm"), 1); render(); } });
		render();
		return {
			get: function () { var v = input.value.trim(); if (v && NAME.test(v) && names.length < max && !names.some(function (n) { return n.toLowerCase() === v.toLowerCase(); })) { names.push(v); input.value = ""; render(); } return names.slice(); },
			set: function (l) { names = (l || []).slice(); render(); }
		};
	}

	function modal(html) {
		var wrap = document.createElement("div");
		wrap.className = "ss-modal-wrap";
		wrap.innerHTML = '<div class="ss-modal" role="dialog" aria-modal="true">' + html + '</div>';
		document.body.appendChild(wrap);
		document.documentElement.style.overflow = "hidden";
		var close = function () { wrap.remove(); document.documentElement.style.overflow = ""; };
		wrap.addEventListener("click", function (e) { if (e.target === wrap) close(); });
		document.addEventListener("keydown", function onKey(e) { if (e.key === "Escape" && document.body.contains(wrap)) { close(); document.removeEventListener("keydown", onKey); } });
		return { el: wrap, close: close };
	}

	window.SANTA = {
		API: API, $: $, esc: esc, api: api, session: session, fmtDia: fmtDia, fmtValue: fmtValue, fmtDay: fmtDay, fmtStamp: fmtStamp, fmtMoney: fmtMoney,
		CATEGORY: CATEGORY, KIND_LABEL: KIND_LABEL, avatar: avatar, mapartImage: mapartImage, wishIcon: wishIcon, wishName: wishName,
		loadCatalog: loadCatalog, valueOf: valueOf, wishEditor: wishEditor, importAccountWishlist: importAccountWishlist, nameChips: nameChips, modal: modal,
		MAX_WISHES: MAX_WISHES
	};
})();
