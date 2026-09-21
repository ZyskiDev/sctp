// SCTP image studio — makes shareable pictures (Discord / Twitter / Instagram /
// YouTube / stories ...) for mapart, rare items, collections, profiles,
// Rare-dle results and announcements. Every picture can be re-rendered in any
// of the sizes, styles, layouts and background patterns below.
//
//   sctpShare.open(spec)                      full popup with live preview + downloads
//   sctpShare.render(canvas, spec, opts)      draw one picture (Promise) — opts: {format, style, layout, pattern, accent, logo, url, ...}
//   sctpShare.FORMATS / STYLES / PATTERNS / LAYOUTS
//   sctpShare.controls(host, onChange)        just the format/style controls, returns {get()} (used by the collection "need list" builder)
//
// spec.type:
//   "feature"  one picture + text      {eyebrow, title, subtitle, details[], imageUrl, pixel, footerUrl}
//   "grid"     many small pictures     {eyebrow, title, subtitle, items:[{name, sub, imageUrl, pixel}], footerUrl}
//   "tiles"    Rare-dle style squares  {eyebrow, title, subtitle, rows:[[state...]], stat, statLabel, footerUrl}
//   "announce" headline + chips        {eyebrow, title, subtitle, chips[], footerUrl}
//   "text"     one big statement       {eyebrow, title, subtitle, footerUrl}
//   "list"     headline + bullet list  {eyebrow, title, subtitle, bullets[], footerUrl}
//   "stats"    headline + big numbers  {eyebrow, title, subtitle, stats:[{value, label}], footerUrl}
(function () {
	"use strict";

	// ---------------- catalogue of options ----------------
	var FORMATS = [
		{ id: "discord",  label: "Discord / link preview — 1200×630", w: 1200, h: 630 },
		{ id: "twitter",  label: "X / Twitter post — 1600×900",       w: 1600, h: 900 },
		{ id: "square",   label: "Square (Instagram) — 1080×1080",    w: 1080, h: 1080 },
		{ id: "portrait", label: "Portrait 4:5 (Instagram) — 1080×1350", w: 1080, h: 1350 },
		{ id: "story",    label: "Story / Reel / TikTok — 1080×1920", w: 1080, h: 1920 },
		{ id: "youtube",  label: "YouTube thumbnail — 1280×720",      w: 1280, h: 720 },
		{ id: "hd",       label: "Wallpaper 16:9 — 1920×1080",        w: 1920, h: 1080 },
		{ id: "banner",   label: "Wide banner / Twitter header — 1500×500", w: 1500, h: 500 },
		{ id: "dbanner",  label: "Discord server banner — 960×540",   w: 960, h: 540 },
		{ id: "pin",      label: "Pinterest pin — 1000×1500",         w: 1000, h: 1500 },
		{ id: "ultra",    label: "Ultrawide — 2560×1080",             w: 2560, h: 1080 },
		{ id: "small",    label: "Small preview — 600×315",           w: 600, h: 315 },
		{ id: "custom",   label: "Custom size…",                      w: 1200, h: 630 }
	];
	var PATTERNS = [
		{ id: "none", label: "No pattern" }, { id: "grid", label: "Grid" }, { id: "dots", label: "Dots" },
		{ id: "diagonal", label: "Diagonal stripes" }, { id: "checker", label: "Checkerboard" }, { id: "scan", label: "Scanlines" }
	];
	var LAYOUTS = [
		{ id: "auto", label: "Auto" }, { id: "left", label: "Picture left" }, { id: "right", label: "Picture right" },
		{ id: "top", label: "Picture on top" }, { id: "poster", label: "Poster (picture fills)" }
	];
	var SERIF = "Fraunces, Georgia, 'Times New Roman', serif";
	var SANS = "Inter, 'Segoe UI', Arial, sans-serif";
	var MONO = "'JetBrains Mono', Consolas, 'Courier New', monospace";
	var HEAVY = "Impact, 'Arial Black', Bahnschrift, sans-serif";
	var STYLES = [
		{ id: "sctp",      label: "SCTP dark",     bg: "#101B14", panel: "#1B2A20", line: "#33453A", text: "#EAEFE7", muted: "#8FA593", accent: "#B7E23D", ink: "#16210F", display: SERIF, body: SANS, radius: 14, border: 2, shadow: 0, pattern: "grid" },
		{ id: "paper",     label: "Paper",         bg: "#F4EFE2", panel: "#FFFDF6", line: "#D8D0BC", text: "#1F2A21", muted: "#6B7468", accent: "#3F7A34", ink: "#FFFFFF", display: SERIF, body: SANS, radius: 10, border: 2, shadow: 0, pattern: "dots" },
		{ id: "sticker",   label: "Sticker pop",   bg: "#FFD84A", panel: "#FFFFFF", line: "#111111", text: "#111111", muted: "#3A3A3A", accent: "#FF4D4D", ink: "#FFFFFF", display: HEAVY, body: SANS, radius: 6, border: 6, shadow: 10, pattern: "diagonal" },
		{ id: "pixel",     label: "Retro pixel",   bg: "#1B1030", panel: "#2A1B4A", line: "#4DFFB4", text: "#F2E9FF", muted: "#A692D6", accent: "#4DFFB4", ink: "#1B1030", display: MONO, body: MONO, radius: 0, border: 4, shadow: 8, pattern: "checker" },
		{ id: "blueprint", label: "Blueprint",     bg: "#1E4E8C", panel: "rgba(255,255,255,0.08)", line: "#9FC5F0", text: "#EAF3FF", muted: "#B8D3F2", accent: "#FFE066", ink: "#1E4E8C", display: MONO, body: SANS, radius: 2, border: 2, shadow: 0, pattern: "grid" },
		{ id: "mono",      label: "Minimal mono",  bg: "#F5F5F5", panel: "#FFFFFF", line: "#111111", text: "#111111", muted: "#666666", accent: "#111111", ink: "#FFFFFF", display: SERIF, body: SANS, radius: 0, border: 2, shadow: 0, pattern: "none" },
		{ id: "sunset",    label: "Sunset",        bg: ["#FF7E5F", "#B23A8E", "#4B2A8A"], panel: "rgba(0,0,0,0.26)", line: "rgba(255,255,255,0.35)", text: "#FFFFFF", muted: "#FFE3D6", accent: "#FFE66D", ink: "#3A1F5C", display: SERIF, body: SANS, radius: 18, border: 2, shadow: 0, pattern: "none" },
		{ id: "ocean",     label: "Ocean",         bg: ["#0B3C5D", "#146C94", "#1CA9C9"], panel: "rgba(0,0,0,0.25)", line: "rgba(255,255,255,0.3)", text: "#F0FBFF", muted: "#B6E3F0", accent: "#FFD166", ink: "#0B3C5D", display: SANS, body: SANS, radius: 16, border: 2, shadow: 0, pattern: "dots" },
		{ id: "midnight",  label: "Midnight",      bg: ["#0D0D1A", "#1E1747", "#2B1B5A"], panel: "rgba(255,255,255,0.06)", line: "rgba(199,125,255,0.5)", text: "#EEE8FF", muted: "#A99BD6", accent: "#C77DFF", ink: "#12082A", display: SERIF, body: SANS, radius: 14, border: 2, shadow: 0, pattern: "none" },
		{ id: "forest",    label: "Deep forest",   bg: ["#0E2A1A", "#1F5130"], panel: "rgba(0,0,0,0.25)", line: "rgba(242,193,78,0.45)", text: "#F3F0E0", muted: "#B9C9A8", accent: "#F2C14E", ink: "#1B2A10", display: SERIF, body: SANS, radius: 12, border: 2, shadow: 0, pattern: "diagonal" },
		{ id: "candy",     label: "Candy",         bg: "#FFE0F0", panel: "#FFFFFF", line: "#3A1030", text: "#3A1030", muted: "#8A4A78", accent: "#FF3D8B", ink: "#FFFFFF", display: HEAVY, body: SANS, radius: 22, border: 5, shadow: 8, pattern: "dots" },
		{ id: "terminal",  label: "Terminal",      bg: "#050805", panel: "#0B140B", line: "#1F7A1F", text: "#4AF626", muted: "#2E9E2E", accent: "#4AF626", ink: "#050805", display: MONO, body: MONO, radius: 0, border: 2, shadow: 0, pattern: "scan" }
	];
	var LS_KEY = "sctp_share_prefs";

	function byId(list, id) { for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i]; return list[0]; }
	function loadPrefs() { try { return JSON.parse(localStorage.getItem(LS_KEY) || "{}") || {}; } catch (e) { return {}; } }
	function savePrefs(p) { try { localStorage.setItem(LS_KEY, JSON.stringify(p)); } catch (e) { /* ignore */ } }
	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

	// ---------------- images ----------------
	var imgCache = {};
	function loadImg(url) {
		if (!url) return Promise.resolve(null);
		if (imgCache[url]) return imgCache[url];
		imgCache[url] = new Promise(function (resolve) {
			var im = new Image();
			im.crossOrigin = "anonymous";
			im.onload = function () { resolve(im); };
			im.onerror = function () { resolve(null); };
			im.src = url;
		});
		return imgCache[url];
	}

	// ---------------- drawing helpers ----------------
	function fillBackground(g, W, H, st, patternId, accent) {
		if (Array.isArray(st.bg)) {
			var gr = g.createLinearGradient(0, 0, W * 0.6, H);
			st.bg.forEach(function (c, i) { gr.addColorStop(i / (st.bg.length - 1), c); });
			g.fillStyle = gr;
		} else g.fillStyle = st.bg;
		g.fillRect(0, 0, W, H);

		var line = st.line, step = Math.max(24, Math.round(Math.min(W, H) / 22));
		g.save();
		g.globalAlpha = 0.5;
		if (patternId === "grid") {
			g.strokeStyle = st.line; g.lineWidth = 1;
			g.globalAlpha = st.id === "blueprint" ? 0.45 : 0.28;
			g.beginPath();
			for (var x = 0; x <= W; x += step) { g.moveTo(x + 0.5, 0); g.lineTo(x + 0.5, H); }
			for (var y = 0; y <= H; y += step) { g.moveTo(0, y + 0.5); g.lineTo(W, y + 0.5); }
			g.stroke();
		} else if (patternId === "dots") {
			g.fillStyle = st.line; g.globalAlpha = 0.35;
			for (var dx = step / 2; dx < W; dx += step) for (var dy = step / 2; dy < H; dy += step) { g.beginPath(); g.arc(dx, dy, Math.max(1.5, step / 12), 0, 6.2832); g.fill(); }
		} else if (patternId === "diagonal") {
			g.strokeStyle = st.line; g.globalAlpha = 0.16; g.lineWidth = Math.max(6, step / 3);
			g.beginPath();
			for (var d = -H; d < W + H; d += step * 1.6) { g.moveTo(d, 0); g.lineTo(d + H, H); }
			g.stroke();
		} else if (patternId === "checker") {
			var cs = step;
			g.fillStyle = st.line; g.globalAlpha = 0.10;
			for (var cx = 0; cx < W; cx += cs) for (var cy = 0; cy < H; cy += cs) if (((cx / cs) + (cy / cs)) % 2 === 0) g.fillRect(cx, cy, cs, cs);
		} else if (patternId === "scan") {
			g.fillStyle = st.line; g.globalAlpha = 0.16;
			for (var sy = 0; sy < H; sy += 4) g.fillRect(0, sy, W, 1);
		}
		g.restore();
	}

	function box(g, x, y, w, h, st, opts) {
		opts = opts || {};
		var r = Math.min(st.radius, Math.min(w, h) / 2), b = opts.border != null ? opts.border : st.border;
		function path() {
			g.beginPath();
			if (r > 0 && g.roundRect) g.roundRect(x, y, w, h, r); else g.rect(x, y, w, h);
		}
		if (st.shadow && !opts.noShadow) { g.save(); g.translate(st.shadow, st.shadow); path(); g.fillStyle = "rgba(0,0,0,0.85)"; g.fill(); g.restore(); }
		path(); g.fillStyle = opts.fill || st.panel; g.fill();
		if (b > 0) { g.lineWidth = b; g.strokeStyle = opts.stroke || st.line; path(); g.stroke(); }
	}

	// Wraps text into at most maxLines lines that fit maxW; shrinks the font until it fits.
	function fitText(g, text, fontFamily, weight, maxW, maxLines, startSize, minSize) {
		text = String(text || "");
		for (var size = startSize; size >= minSize; size -= 2) {
			g.font = weight + " " + size + "px " + fontFamily;
			var words = text.split(" "), lines = [], line = "";
			var ok = true;
			for (var i = 0; i < words.length; i++) {
				var t = line ? line + " " + words[i] : words[i];
				if (g.measureText(t).width > maxW && line) { lines.push(line); line = words[i]; if (g.measureText(line).width > maxW) ok = false; }
				else line = t;
			}
			if (line) lines.push(line);
			if (ok && lines.length <= maxLines) return { size: size, lines: lines, lh: Math.round(size * 1.14) };
		}
		g.font = weight + " " + minSize + "px " + fontFamily;
		var all = text.split(" "), out = [], cur = "";
		all.forEach(function (w) { var t = cur ? cur + " " + w : w; if (g.measureText(t).width > maxW && cur) { out.push(cur); cur = w; } else cur = t; });
		if (cur) out.push(cur);
		var cut = out.slice(0, maxLines);
		if (out.length > maxLines) cut[maxLines - 1] = cut[maxLines - 1].replace(/\s*\S*$/, "") + "…";
		return { size: minSize, lines: cut, lh: Math.round(minSize * 1.14) };
	}
	function textBlock(g, fit, x, y, color, align) {
		g.fillStyle = color; g.textAlign = align || "left"; g.textBaseline = "alphabetic";
		g.font = fit.font || g.font;
		fit.lines.forEach(function (l, i) { g.fillText(l, x, y + i * fit.lh); });
		return y + (fit.lines.length - 1) * fit.lh + Math.round(fit.size * 0.3); // y is the first baseline; return just below the last line
	}
	function fitFont(g, fit, weight, family) { g.font = weight + " " + fit.size + "px " + family; fit.font = g.font; return fit; }

	function drawLogo(g, x, y, s, st, accent) {
		g.save();
		g.fillStyle = accent; g.fillRect(x, y, s, s);
		g.strokeStyle = st.ink; g.lineWidth = Math.max(2, s / 10); g.lineCap = "round";
		g.beginPath();
		var cx = x + s * 0.52, cy = y + s * 0.5, a = 0;
		for (var t = 0; t <= 5.2 * Math.PI; t += 0.15) { var r = s * 0.035 * t; var px = cx + Math.cos(t + a) * r, py = cy + Math.sin(t + a) * r; if (t === 0) g.moveTo(px, py); else g.lineTo(px, py); }
		g.stroke();
		g.beginPath(); g.moveTo(x + s * 0.12, y + s * 0.86); g.lineTo(x + s * 0.82, y + s * 0.86); g.stroke();
		g.restore();
	}
	function footer(g, W, H, spec, o, st, accent, pad) {
		var y = H - pad;
		var size = Math.max(14, Math.round(Math.min(W, H) / 40));
		var ls = Math.round(size * 1.9);
		g.textBaseline = "alphabetic"; g.textAlign = "left";
		var x = pad;
		if (o.logo) { drawLogo(g, x, y - ls + 4, ls, st, accent); x += ls + 12; }
		if (o.url) {
			g.fillStyle = accent; g.font = "700 " + size + "px " + st.body;
			g.fillText(spec.footerUrl || "sctp.nl", x, y - ls / 2 + size / 3 - 2);
			g.fillStyle = st.muted; g.font = "500 " + Math.round(size * 0.78) + "px " + st.body;
			g.fillText("Snailcraft Trading Post", x, y + Math.round(size * 0.35));
		}
		return ls;
	}
	function drawPicture(g, im, x, y, w, h, pixel, st, framed) {
		if (framed) box(g, x - 14, y - 14, w + 28, h + 28, st, { fill: st.id === "paper" || st.id === "mono" || st.id === "candy" || st.id === "sticker" ? "#0C140F" : "rgba(0,0,0,0.35)" });
		if (!im) return;
		var s = Math.min(w / im.width, h / im.height);
		var dw = im.width * s, dh = im.height * s;
		g.imageSmoothingEnabled = !pixel;
		g.drawImage(im, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
	}

	// ---------------- renderers ----------------
	function drawFeature(g, W, H, spec, o, st, accent, img) {
		var pad = Math.round(Math.min(W, H) * 0.075), ar = W / H;
		var layout = o.layout === "auto" || !o.layout ? (ar < 0.85 ? "top" : ar > 2.2 ? "left" : "left") : o.layout;
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 10 : 0;
		var picBox;
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var details = spec.details || [];
		var eyebrow = spec.eyebrow || "";

		function textAt(x, y, w, hAvail, align) {
			var cx = align === "center" ? x + w / 2 : x, cy = y;
			var es = Math.max(14, Math.round(Math.min(W, H) / 32));
			if (eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = align; g.fillText(eyebrow.toUpperCase().split("").join(st.display === MONO ? "" : " "), cx, cy + es); cy += es * 2.2; }
			var startSize = Math.round(Math.min(hAvail * 0.3, w / 6, Math.min(W, H) / 7)), maxLines = Math.max(2, Math.floor(hAvail / 130));
			var tf = fitText(g, title, st.display, st.display === HEAVY ? "400" : "700", w, Math.min(4, maxLines + 1), Math.max(startSize, 32), 24);
			fitFont(g, tf, st.display === HEAVY ? "400" : "700", st.display);
			cy = textBlock(g, tf, cx, cy + tf.size * 0.95, st.text, align);
			cy += 10;
			if (sub) {
				var ss = Math.max(16, Math.round(tf.size * 0.42));
				var sf = fitText(g, sub, st.body, "500", w, 2, ss, 14); fitFont(g, sf, "500", st.body);
				cy = textBlock(g, sf, cx, cy + sf.size, st.muted, align) + 6;
			}
			var ds = Math.max(14, Math.round(tf.size * 0.34));
			details.slice(0, 4).forEach(function (d) {
				g.font = "500 " + ds + "px " + st.body; g.fillStyle = st.muted; g.textAlign = align;
				var lines = fitText(g, d, st.body, "500", w, 2, ds, 12); fitFont(g, lines, "500", st.body);
				cy = textBlock(g, lines, cx, cy + lines.size + 4, st.muted, align);
			});
			if (spec.stat) {
				var big = Math.round(tf.size * 1.2);
				g.font = "700 " + big + "px " + st.display; g.fillStyle = accent; g.textAlign = align;
				g.fillText(spec.stat, cx, Math.min(y + hAvail - 4, cy + big * 1.05));
				if (spec.statLabel) { g.font = "500 " + ds + "px " + st.body; g.fillStyle = st.muted; g.fillText(spec.statLabel, cx + (align === "center" ? 0 : g.measureText(spec.stat).width + 14), Math.min(y + hAvail - 4, cy + big * 1.05)); }
			}
		}

		var areaY = pad, areaH = H - pad * 2 - footH;
		if (layout === "poster") {
			// picture fills the whole card, text on a panel strip at the bottom
			if (img) {
				var s = Math.max(W / img.width, (H) / img.height);
				g.save(); g.beginPath(); g.rect(0, 0, W, H); g.clip();
				g.imageSmoothingEnabled = !spec.pixel;
				g.drawImage(img, (W - img.width * s) / 2, (H - img.height * s) / 2, img.width * s, img.height * s);
				g.restore();
			}
			var stripH = Math.round(H * (ar < 0.85 ? 0.34 : 0.42));
			g.fillStyle = "rgba(0,0,0,0.62)"; g.fillRect(0, H - stripH, W, stripH);
			g.fillStyle = accent; g.fillRect(0, H - stripH, W, Math.max(4, Math.round(H / 120)));
			var oldText = st.text, oldMuted = st.muted;
			var tmp = Object.assign({}, st, { text: "#FFFFFF", muted: "#D8DED5" });
			var saveSt = st; st = tmp;
			textAtPoster(g, W, H, stripH, pad, spec, o, st, accent, title, sub, details, eyebrow);
			st = saveSt;
			footer(g, W, H, spec, o, Object.assign({}, st, { muted: "#D8DED5" }), accent, pad);
			return;
		}
		if (layout === "top") {
			var picH = Math.round(areaH * 0.5), picW = W - pad * 2;
			var side = Math.min(picW, picH);
			drawPicture(g, img, (W - side) / 2, areaY, side, picH, spec.pixel, st, true);
			g.textAlign = "center";
			textAt(pad, areaY + picH + pad * 0.7, W - pad * 2, areaH - picH - pad * 0.7, "center");
		} else {
			var picSide = Math.min(areaH - 28, Math.round((W - pad * 2) * 0.42));
			var picX = layout === "right" ? W - pad - picSide - 14 : pad + 14;
			var textX = layout === "right" ? pad : pad + picSide + 28 + 40;
			var textW = W - pad * 2 - picSide - 28 - 40;
			drawPicture(g, img, picX, areaY + (areaH - picSide) / 2, picSide, picSide, spec.pixel, st, true);
			textAt(textX, areaY + Math.max(0, (areaH - Math.min(areaH, 460)) / 2), textW, Math.min(areaH, 460), "left");
		}
		footer(g, W, H, spec, o, st, accent, pad);
	}
	function textAtPoster(g, W, H, stripH, pad, spec, o, st, accent, title, sub, details, eyebrow) {
		var y = H - stripH + Math.round(pad * 0.55);
		var es = Math.max(14, Math.round(Math.min(W, H) / 34));
		if (eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = "left"; g.fillText(eyebrow.toUpperCase(), pad, y + es); y += es * 1.9; }
		var tf = fitText(g, title, st.display, st.display === HEAVY ? "400" : "700", W - pad * 2, 2, Math.round(stripH * 0.3), 24); fitFont(g, tf, st.display === HEAVY ? "400" : "700", st.display);
		y = textBlock(g, tf, pad, y + tf.size * 0.9, "#FFFFFF", "left");
		var line = [sub].concat(details.slice(0, 1)).filter(Boolean).join("  ·  ");
		if (line) { var sf = fitText(g, line, st.body, "500", W - pad * 2, 1, Math.round(tf.size * 0.42), 13); fitFont(g, sf, "500", st.body); textBlock(g, sf, pad, y + sf.size + 4, "#D8DED5", "left"); }
	}

	function drawGrid(g, W, H, spec, o, st, accent, imgs) {
		var pad = Math.round(Math.min(W, H) * 0.05);
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 14 : 0;
		var y = pad;
		var es = Math.max(13, Math.round(Math.min(W, H) / 46));
		if (spec.eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = "left"; g.fillText(spec.eyebrow.toUpperCase(), pad, y + es); y += es * 1.9; }
		var tf = fitText(g, title, st.display, st.display === HEAVY ? "400" : "700", W - pad * 2, 2, Math.round(Math.min(W, H) / 11), 22); fitFont(g, tf, st.display === HEAVY ? "400" : "700", st.display);
		y = textBlock(g, tf, pad, y + tf.size * 0.9, st.text, "left");
		if (sub) { var sf = fitText(g, sub, st.body, "500", W - pad * 2, 1, Math.round(tf.size * 0.4), 13); fitFont(g, sf, "500", st.body); y = textBlock(g, sf, pad, y + sf.size + 2, st.muted, "left"); }
		y += Math.round(pad * 0.5);

		var items = spec.items || [], availW = W - pad * 2, availH = H - y - pad - footH;
		var MIN = 78, best = null;
		function layoutFor(n) {
			var pick = null;
			for (var cols = 1; cols <= n; cols++) {
				var cw = availW / cols, ch = cw * (spec.gridAspect || 0.86), rows = Math.ceil(n / cols);
				if (rows * ch <= availH) { if (!pick || cw > pick.cw) pick = { cols: cols, cw: cw, ch: ch, rows: rows }; }
			}
			return pick;
		}
		var n = Math.min(items.length, o.maxItems || 400), shown = n;
		best = layoutFor(shown);
		while ((!best || best.cw < MIN) && shown > 1) { shown = Math.floor(shown * 0.85) || 1; best = layoutFor(shown); }
		if (!best) best = { cols: 1, cw: availW, ch: availH, rows: 1 };
		var extra = items.length - shown;
		if (extra > 0 && shown > best.cols) { shown -= 1; best = layoutFor(shown) || best; extra = items.length - shown; }
		var gx = pad + (availW - best.cols * best.cw) / 2;
		for (var i = 0; i < shown; i++) {
			var cx = gx + (i % best.cols) * best.cw, cy = y + Math.floor(i / best.cols) * best.ch;
			var m = Math.max(3, best.cw * 0.04);
			box(g, cx + m, cy + m, best.cw - m * 2, best.ch - m * 2, Object.assign({}, st, { shadow: 0, border: Math.min(st.border, 2) }));
			var im = imgs[i], ih = (best.ch - m * 2) * 0.55, iw = best.cw - m * 2 - 14;
			if (im) { var sc = Math.min(iw / im.width, ih / im.height); if (!items[i].pixel) sc = Math.min(sc, 99); else if (im.width < 64) sc = Math.min(sc, Math.max(1, Math.floor(iw / im.width))); g.imageSmoothingEnabled = !items[i].pixel; g.drawImage(im, cx + best.cw / 2 - im.width * sc / 2, cy + m + 7 + (ih - im.height * sc) / 2, im.width * sc, im.height * sc); }
			var nf = Math.max(9, Math.round(best.cw / 12));
			var name = fitText(g, items[i].name, st.body, "600", best.cw - m * 2 - 12, 2, nf, 8); fitFont(g, name, "600", st.body);
			textBlock(g, name, cx + best.cw / 2, cy + m + 7 + ih + name.size + 4, st.text, "center");
		}
		var by = y + best.rows * best.ch;
		if (extra > 0) { g.fillStyle = st.muted; g.font = "600 " + es + "px " + st.body; g.textAlign = "left"; g.fillText("…and " + extra + " more", pad, Math.min(H - pad - footH, by + es * 1.6)); }
		footer(g, W, H, spec, o, st, accent, pad);
	}

	var TILE_COLORS = { correct: "#4FA34F", close: "#D9962B", wrong: "#3A4A40", none: "#5A6A60" };
	function drawTiles(g, W, H, spec, o, st, accent) {
		var pad = Math.round(Math.min(W, H) * 0.07), ar = W / H;
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 14 : 0;
		var rows = spec.rows || [], cols = rows.length ? rows[0].length : 6;
		var wide = ar > 1.3;
		var tilesArea = wide ? { x: W * 0.52, y: pad, w: W * 0.42, h: H - pad * 2 - footH } : { x: pad, y: H * 0.42, w: W - pad * 2, h: H * 0.5 - footH };
		var cell = Math.min(tilesArea.w / cols, tilesArea.h / Math.max(1, rows.length)) * 0.94;
		var gx = tilesArea.x + (tilesArea.w - cell * cols) / 2, gy = tilesArea.y + (tilesArea.h - cell * rows.length) / 2;
		rows.forEach(function (row, r) {
			row.forEach(function (s, c) {
				var m = cell * 0.06;
				g.fillStyle = TILE_COLORS[s] || TILE_COLORS.none;
				g.fillRect(gx + c * cell + m, gy + r * cell + m, cell - m * 2, cell - m * 2);
			});
		});
		var tx = wide ? pad : pad, tw = wide ? W * 0.46 - pad : W - pad * 2, y = pad;
		var es = Math.max(14, Math.round(Math.min(W, H) / 30));
		if (spec.eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = "left"; g.fillText(spec.eyebrow.toUpperCase(), tx, y + es); y += es * 2; }
		var tf = fitText(g, title, st.display, st.display === HEAVY ? "400" : "700", tw, 2, Math.round(Math.min(W, H) / 7.5), 26); fitFont(g, tf, st.display === HEAVY ? "400" : "700", st.display);
		y = textBlock(g, tf, tx, y + tf.size * 0.9, st.text, "left");
		if (sub) { var sf = fitText(g, sub, st.body, "500", tw, 2, Math.round(tf.size * 0.42), 14); fitFont(g, sf, "500", st.body); y = textBlock(g, sf, tx, y + sf.size + 4, st.muted, "left"); }
		if (spec.stat) { var big = Math.round(tf.size * 1.3); g.font = "700 " + big + "px " + st.display; g.fillStyle = accent; g.fillText(spec.stat, tx, y + big * 1.2); if (spec.statLabel) { g.font = "500 " + Math.round(big * 0.34) + "px " + st.body; g.fillStyle = st.muted; g.fillText(spec.statLabel, tx + g.measureText(spec.stat).width + 14, y + big * 1.2); } }
		footer(g, W, H, spec, o, st, accent, pad);
	}

	function drawAnnounce(g, W, H, spec, o, st, accent) {
		var pad = Math.round(Math.min(W, H) * 0.07), ar = W / H;
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 14 : 0;
		var wide = ar > 1.6, y = pad;
		var es = Math.max(15, Math.round(Math.min(W, H) / 26));
		if (spec.eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = "left"; g.fillText(spec.eyebrow.toUpperCase(), pad, y + es); y += es * 2.2; }
		var maxW = wide ? W * 0.44 : W - pad * 2;
		var startT = Math.round(Math.min(W * (wide ? 0.16 : 0.3), H * (wide ? 0.42 : 0.2)));
		var wt = st.display === HEAVY ? "400" : "800";
		var tf = fitText(g, title, st.display, wt, maxW, 1, startT, Math.max(40, Math.round(startT * 0.45)));
		if (tf.lines.length > 1 || g.measureText(tf.lines[0] || "").width > maxW) tf = fitText(g, title, st.display, wt, maxW, 2, startT, 30);
		fitFont(g, tf, wt, st.display);
		// hard offset shadow behind the headline
		g.save(); g.translate(Math.max(4, tf.size / 16), Math.max(4, tf.size / 16)); textBlock(g, tf, pad, y + tf.size * 0.9, st.id === "sctp" ? "#5D7A17" : "rgba(0,0,0,0.35)", "left"); g.restore();
		y = textBlock(g, tf, pad, y + tf.size * 0.9, accent, "left");
		if (sub) { var sf = fitText(g, sub, st.body, "500", maxW, 2, Math.round(tf.size * 0.28), 15); fitFont(g, sf, "500", st.body); y = textBlock(g, sf, pad, y + sf.size + 8, st.text, "left"); }
		var chips = spec.chips || [];
		var cx0 = wide ? W * 0.55 : pad, cy0 = wide ? pad + 10 : y + pad * 0.6, cw = wide ? W * 0.39 : W - pad * 2;
		var chipH = Math.round(Math.min(H * (wide ? 0.17 : 0.085), 96)), gap = Math.round(chipH * 0.28);
		var perRow = wide ? 1 : (ar > 0.9 ? 2 : 1);
		var chipW = (cw - gap * (perRow - 1)) / perRow;
		var maxRows = Math.max(1, Math.floor(((H - footH - pad) - cy0 + gap) / (chipH + gap)));
		chips.slice(0, maxRows * perRow).forEach(function (t, i) {
			var col = i % perRow, row = Math.floor(i / perRow);
			var bx = cx0 + col * (chipW + gap), by = cy0 + row * (chipH + gap) + (wide ? Math.max(0, ((H - footH - pad * 2) - Math.min(chips.length, maxRows * perRow) * (chipH + gap)) / 2) : 0);
			var tilt = ((i % 3) - 1) * 0.6;
			g.save(); g.translate(bx + chipW / 2, by + chipH / 2); g.rotate(tilt * Math.PI / 180);
			box(g, -chipW / 2, -chipH / 2, chipW, chipH, st, i === 0 ? { fill: accent, stroke: st.line } : {});
			var f = fitText(g, t, st.display, st.display === HEAVY ? "400" : "700", chipW - chipH * 0.5, 1, Math.round(chipH * 0.42), 12); fitFont(g, f, st.display === HEAVY ? "400" : "700", st.display);
			g.fillStyle = i === 0 ? st.ink : st.text; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(f.lines[0], 0, 2); g.textBaseline = "alphabetic";
			g.restore();
		});
		footer(g, W, H, spec, o, st, accent, pad);
	}

	function drawText(g, W, H, spec, o, st, accent) {
		var pad = Math.round(Math.min(W, H) * 0.09);
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 14 : 0;
		var wt = st.display === HEAVY ? "400" : "800";
		var boxH = H - pad * 2 - footH, maxW = W - pad * 2;
		var es = Math.max(15, Math.round(Math.min(W, H) / 28));
		var tf = fitText(g, title, st.display, wt, maxW, 5, Math.round(Math.min(W * 0.16, boxH * 0.5)), 28); fitFont(g, tf, wt, st.display);
		var sf = sub ? fitText(g, sub, st.body, "500", maxW * 0.92, 3, Math.round(tf.size * 0.34), 15) : null;
		if (sf) fitFont(g, sf, "500", st.body);
		var total = (spec.eyebrow ? es * 2.2 : 0) + (tf.lines.length - 1) * tf.lh + tf.size + (sf ? 24 + (sf.lines.length - 1) * sf.lh + sf.size : 0);
		var y = pad + Math.max(0, (boxH - total) / 2);
		g.textAlign = "center";
		if (spec.eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.fillText(spec.eyebrow.toUpperCase(), W / 2, y + es); y += es * 2.2; }
		g.save(); g.translate(Math.max(3, tf.size / 18), Math.max(3, tf.size / 18)); textBlock(g, tf, W / 2, y + tf.size * 0.9, st.id === "sctp" ? "#5D7A17" : "rgba(0,0,0,0.3)", "center"); g.restore();
		y = textBlock(g, tf, W / 2, y + tf.size * 0.9, accent, "center");
		if (sf) textBlock(g, sf, W / 2, y + 24 + sf.size, st.text, "center");
		g.textAlign = "left";
		footer(g, W, H, spec, o, st, accent, pad);
	}

	function drawList(g, W, H, spec, o, st, accent) {
		var pad = Math.round(Math.min(W, H) * 0.07), ar = W / H, wide = ar > 1.5;
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 14 : 0;
		var wt = st.display === HEAVY ? "400" : "800";
		var bullets = (spec.bullets || []).filter(Boolean).slice(0, 12);
		var headW = wide ? W * 0.38 : W - pad * 2, y = pad;
		var es = Math.max(14, Math.round(Math.min(W, H) / 30));
		if (spec.eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = "left"; g.fillText(spec.eyebrow.toUpperCase(), pad, y + es); y += es * 2.1; }
		var tf = fitText(g, title, st.display, wt, headW, wide ? 4 : 3, Math.round(Math.min(W * (wide ? 0.09 : 0.13), H * 0.16)), 26); fitFont(g, tf, wt, st.display);
		y = textBlock(g, tf, pad, y + tf.size * 0.9, st.text, "left");
		if (sub) { var sf = fitText(g, sub, st.body, "500", headW, 3, Math.round(tf.size * 0.4), 14); fitFont(g, sf, "500", st.body); y = textBlock(g, sf, pad, y + sf.size + 8, st.muted, "left"); }
		var bx = wide ? W * 0.47 : pad, bw = wide ? W - bx - pad : W - pad * 2;
		var by = wide ? pad : y + pad * 0.6, bh = H - footH - pad - by;
		if (!bullets.length) { footer(g, W, H, spec, o, st, accent, pad); return; }
		var gap = Math.round(Math.min(W, H) * 0.018), rowH = Math.min(120, (bh - gap * (bullets.length - 1)) / bullets.length);
		var fs = Math.max(13, Math.round(rowH * 0.34));
		bullets.forEach(function (t, i) {
			var ry = by + i * (rowH + gap) + (wide ? Math.max(0, (bh - bullets.length * (rowH + gap) + gap) / 2) : 0);
			box(g, bx, ry, bw, rowH, st, { noShadow: false });
			g.fillStyle = accent; g.fillRect(bx + rowH * 0.22, ry + rowH * 0.3, rowH * 0.4, rowH * 0.4);
			var f = fitText(g, t, st.body, "600", bw - rowH * 1.1, 2, fs, 11); fitFont(g, f, "600", st.body);
			var ty = ry + rowH / 2 - ((f.lines.length - 1) * f.lh) / 2 + f.size * 0.35;
			textBlock(g, f, bx + rowH * 0.9, ty, st.text, "left");
		});
		footer(g, W, H, spec, o, st, accent, pad);
	}

	function drawStats(g, W, H, spec, o, st, accent) {
		var pad = Math.round(Math.min(W, H) * 0.07), ar = W / H;
		var title = o.headline || spec.title || "", sub = o.subline != null && o.subline !== "" ? o.subline : (spec.subtitle || "");
		var footH = (o.logo || o.url) ? Math.round(Math.min(W, H) / 40 * 1.9) + 14 : 0;
		var wt = st.display === HEAVY ? "400" : "800", y = pad;
		var es = Math.max(14, Math.round(Math.min(W, H) / 30));
		if (spec.eyebrow) { g.fillStyle = accent; g.font = "700 " + es + "px " + st.body; g.textAlign = "left"; g.fillText(spec.eyebrow.toUpperCase(), pad, y + es); y += es * 2.1; }
		var tf = fitText(g, title, st.display, wt, W - pad * 2, 2, Math.round(Math.min(W * 0.1, H * 0.14)), 26); fitFont(g, tf, wt, st.display);
		y = textBlock(g, tf, pad, y + tf.size * 0.9, st.text, "left");
		if (sub) { var sf = fitText(g, sub, st.body, "500", W - pad * 2, 2, Math.round(tf.size * 0.4), 14); fitFont(g, sf, "500", st.body); y = textBlock(g, sf, pad, y + sf.size + 8, st.muted, "left"); }
		var stats = (spec.stats || []).slice(0, 8);
		if (!stats.length) { footer(g, W, H, spec, o, st, accent, pad); return; }
		var top = y + pad * 0.6, availH = H - footH - pad - top, availW = W - pad * 2;
		var cols = stats.length <= 2 ? stats.length : (ar > 1.2 ? Math.min(4, stats.length) : 2);
		var rows = Math.ceil(stats.length / cols), gap = Math.round(Math.min(W, H) * 0.025);
		var cw = (availW - gap * (cols - 1)) / cols, ch = Math.min(availH / rows - gap, cw * 0.9);
		var gy = top + Math.max(0, (availH - rows * (ch + gap) + gap) / 2);
		stats.forEach(function (s, i) {
			var cx = pad + (i % cols) * (cw + gap), cy = gy + Math.floor(i / cols) * (ch + gap);
			box(g, cx, cy, cw, ch, st, i === 0 ? { fill: accent, stroke: st.line } : {});
			var vf = fitText(g, String(s.value), st.display, wt, cw - 24, 1, Math.round(ch * 0.42), 16); fitFont(g, vf, wt, st.display);
			g.fillStyle = i === 0 ? st.ink : accent; g.textAlign = "center"; g.fillText(vf.lines[0], cx + cw / 2, cy + ch * 0.52);
			var lf = fitText(g, String(s.label), st.body, "600", cw - 24, 2, Math.round(ch * 0.15), 10); fitFont(g, lf, "600", st.body);
			textBlock(g, lf, cx + cw / 2, cy + ch * 0.52 + lf.size * 1.6, i === 0 ? st.ink : st.muted, "center");
		});
		g.textAlign = "left";
		footer(g, W, H, spec, o, st, accent, pad);
	}

	// ---------------- public render ----------------
	function sizeFor(o) {
		var f = byId(FORMATS, o.format);
		if (f.id === "custom") return { w: Math.min(4000, Math.max(200, o.customW || 1200)), h: Math.min(4000, Math.max(200, o.customH || 630)) };
		return { w: f.w, h: f.h };
	}
	function render(canvas, spec, opts) {
		var prefs = loadPrefs();
		var o = Object.assign({ format: "discord", style: "sctp", layout: "auto", pattern: "auto", logo: true, url: true }, prefs, opts || {});
		var st = byId(STYLES, o.style), sz = sizeFor(o);
		var accent = o.accent && o.accent !== "auto" ? o.accent : st.accent;
		var pattern = o.pattern === "auto" || !o.pattern ? st.pattern : o.pattern;
		var loads = [];
		if (spec.type === "feature") loads.push(loadImg(spec.imageUrl));
		if (spec.type === "grid") { (spec.items || []).slice(0, o.maxItems || 400).forEach(function (it) { loads.push(loadImg(it.imageUrl)); }); }
		var fontsReady = document.fonts && document.fonts.load ? Promise.all([document.fonts.load("700 40px Fraunces"), document.fonts.load("600 20px Inter")]).catch(function () {}) : Promise.resolve();
		return Promise.all([fontsReady].concat(loads)).then(function (res) {
			canvas.width = sz.w; canvas.height = sz.h;
			var g = canvas.getContext("2d");
			fillBackground(g, sz.w, sz.h, st, pattern, accent);
			var imgs = res.slice(1);
			if (spec.type === "feature") drawFeature(g, sz.w, sz.h, spec, o, st, accent, imgs[0]);
			else if (spec.type === "grid") drawGrid(g, sz.w, sz.h, spec, o, st, accent, imgs);
			else if (spec.type === "tiles") drawTiles(g, sz.w, sz.h, spec, o, st, accent);
			else if (spec.type === "text") drawText(g, sz.w, sz.h, spec, o, st, accent);
			else if (spec.type === "list") drawList(g, sz.w, sz.h, spec, o, st, accent);
			else if (spec.type === "stats") drawStats(g, sz.w, sz.h, spec, o, st, accent);
			else drawAnnounce(g, sz.w, sz.h, spec, o, st, accent);
			return canvas;
		});
	}

	// ---------------- controls ----------------
	var CSS = document.createElement("style");
	CSS.textContent =
		".ss-bg{position:fixed;inset:0;background:rgba(0,0,0,0.72);z-index:500;display:flex;align-items:flex-start;justify-content:center;padding:18px;box-sizing:border-box;overflow:auto;}" +
		".ss-modal{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:14px;padding:18px 20px;width:100%;max-width:1120px;margin:auto;color:var(--text,#EAEFE7);font-family:var(--font-body,Inter,sans-serif);box-sizing:border-box;}" +
		".ss-modal h2{margin:0 0 2px;font-size:20px;font-family:var(--font-display,inherit);}" +
		".ss-sub{color:var(--muted,#8FA593);font-size:13px;margin:0 0 12px;}" +
		".ss-layout{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(260px,1fr);gap:18px;align-items:start;}" +
		"@media (max-width:860px){.ss-layout{grid-template-columns:1fr;}}" +
		".ss-prev{background:repeating-conic-gradient(#222 0 25%,#2c2c2c 0 50%) 0 0/20px 20px;border-radius:10px;padding:10px;text-align:center;min-height:140px;}" +
		".ss-prev canvas{max-width:100%;max-height:70vh;height:auto;border-radius:6px;box-shadow:0 8px 30px rgba(0,0,0,0.5);}" +
		".ss-ctl{display:grid;gap:9px;}" +
		".ss-ctl label{display:block;font-size:11px;color:var(--muted,#8FA593);font-weight:600;text-transform:uppercase;letter-spacing:.04em;margin-bottom:3px;}" +
		".ss-ctl select,.ss-ctl input[type=text],.ss-ctl input[type=number]{width:100%;box-sizing:border-box;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 10px;font-size:13.5px;font-family:inherit;}" +
		".ss-row{display:grid;grid-template-columns:1fr 1fr;gap:8px;}" +
		".ss-checks{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:13px;}" +
		".ss-checks label{text-transform:none;letter-spacing:0;font-size:13px;color:var(--text,#EAEFE7);font-weight:500;display:flex;align-items:center;gap:6px;margin:0;}" +
		".ss-styles{display:flex;flex-wrap:wrap;gap:6px;}" +
		".ss-sw{width:38px;height:26px;border-radius:6px;border:2px solid var(--line,#33453A);cursor:pointer;padding:0;position:relative;}" +
		".ss-sw.on{border-color:var(--accent,#B7E23D);box-shadow:0 0 0 2px var(--accent,#B7E23D);}" +
		".ss-btns{display:flex;flex-wrap:wrap;gap:8px;margin-top:6px;}" +
		".ss-btns button,.ss-btns a{background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px 14px;font-size:13.5px;cursor:pointer;font-family:inherit;text-decoration:none;display:inline-block;}" +
		".ss-btns .primary{background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border-color:transparent;font-weight:700;}" +
		".ss-note{font-size:12px;color:var(--muted,#8FA593);}";
	document.head.appendChild(CSS);

	function swatch(st) {
		var bg = Array.isArray(st.bg) ? "linear-gradient(135deg," + st.bg.join(",") + ")" : st.bg;
		return "background:" + bg + ";";
	}

	// Builds the shared controls into `host`; returns {get(): opts}.
	function controls(host, onChange, cfg) {
		cfg = cfg || {};
		var p = Object.assign({ format: "discord", style: "sctp", layout: "auto", pattern: "auto", accent: "auto", logo: true, url: true, customW: 1200, customH: 630 }, loadPrefs());
		host.innerHTML =
			'<div class="ss-ctl">' +
			'<div><label>Format</label><select data-k="format">' + FORMATS.map(function (f) { return '<option value="' + f.id + '">' + esc(f.label) + "</option>"; }).join("") + "</select></div>" +
			'<div class="ss-row" data-custom hidden><div><label>Width</label><input type="number" data-k="customW" min="200" max="4000"></div><div><label>Height</label><input type="number" data-k="customH" min="200" max="4000"></div></div>' +
			'<div><label>Style</label><div class="ss-styles" data-styles>' + STYLES.map(function (s) { return '<button type="button" class="ss-sw" data-style="' + s.id + '" title="' + esc(s.label) + '" style="' + swatch(s) + '"></button>'; }).join("") + '</div><div class="ss-note" data-stylename></div></div>' +
			(cfg.noLayout ? "" : '<div class="ss-row"><div><label>Layout</label><select data-k="layout">' + LAYOUTS.map(function (l) { return '<option value="' + l.id + '">' + l.label + "</option>"; }).join("") + "</select></div>") +
			'<div' + (cfg.noLayout ? ' class="ss-row"' : "") + '><label>Pattern</label><select data-k="pattern"><option value="auto">Style default</option>' + PATTERNS.map(function (q) { return '<option value="' + q.id + '">' + q.label + "</option>"; }).join("") + "</select></div>" + (cfg.noLayout ? "" : "</div>") +
			'<div class="ss-row"><div><label>Accent colour</label><input type="color" data-k="accentPick" style="width:100%;height:36px;border:1px solid var(--line,#33453A);border-radius:8px;background:transparent;padding:2px;"></div><div><label>&nbsp;</label><label style="display:flex;align-items:center;gap:6px;text-transform:none;font-size:13px;color:var(--text,#EAEFE7);"><input type="checkbox" data-k="accentAuto"> use style colour</label></div></div>' +
			'<div class="ss-checks"><label><input type="checkbox" data-k="logo"> Logo</label><label><input type="checkbox" data-k="url"> sctp.nl line</label></div>' +
			(cfg.textOverrides === false ? "" : '<div><label>Headline (optional)</label><input type="text" data-k="headline" maxlength="90" placeholder="Leave empty for the default"></div><div><label>Subline (optional)</label><input type="text" data-k="subline" maxlength="120"></div>') +
			"</div>";
		function q(k) { return host.querySelector('[data-k="' + k + '"]'); }
		var state = { headline: "", subline: "" };
		function sync() {
			q("format").value = p.format; if (q("layout")) q("layout").value = p.layout; q("pattern").value = p.pattern;
			q("customW").value = p.customW; q("customH").value = p.customH;
			host.querySelector("[data-custom]").hidden = p.format !== "custom";
			var st = byId(STYLES, p.style);
			q("accentAuto").checked = p.accent === "auto";
			q("accentPick").value = p.accent === "auto" ? st.accent.replace(/^(#[0-9a-fA-F]{6}).*$/, "$1") : p.accent;
			q("logo").checked = !!p.logo; q("url").checked = !!p.url;
			host.querySelectorAll("[data-style]").forEach(function (b) { b.classList.toggle("on", b.getAttribute("data-style") === p.style); });
			host.querySelector("[data-stylename]").textContent = st.label;
		}
		function get() {
			var o = { format: p.format, style: p.style, layout: p.layout, pattern: p.pattern, accent: p.accent, logo: !!p.logo, url: !!p.url, customW: +p.customW, customH: +p.customH };
			if (q("headline")) { o.headline = q("headline").value.trim(); o.subline = q("subline").value.trim(); }
			return o;
		}
		function changed() { savePrefs({ format: p.format, style: p.style, layout: p.layout, pattern: p.pattern, accent: p.accent, logo: p.logo, url: p.url, customW: p.customW, customH: p.customH }); if (onChange) onChange(get()); }
		host.addEventListener("input", function (e) {
			var k = e.target.getAttribute && e.target.getAttribute("data-k"); if (!k) return;
			if (k === "accentPick") { p.accent = e.target.value; q("accentAuto").checked = false; }
			else if (k === "accentAuto") { p.accent = e.target.checked ? "auto" : q("accentPick").value; }
			else if (k === "logo" || k === "url") p[k] = e.target.checked;
			else if (k === "headline" || k === "subline") { changed(); return; }
			else p[k] = e.target.value;
			sync(); changed();
		});
		host.querySelector("[data-styles]").addEventListener("click", function (e) {
			var b = e.target.closest ? e.target.closest("[data-style]") : null; if (!b) return;
			p.style = b.getAttribute("data-style"); p.pattern = "auto"; sync(); changed();
		});
		sync();
		return { get: get, randomize: function () { p.style = STYLES[Math.floor(Math.random() * STYLES.length)].id; p.pattern = PATTERNS[Math.floor(Math.random() * PATTERNS.length)].id; if (Math.random() < 0.5) p.pattern = "auto"; sync(); changed(); }, set: function (o) { Object.assign(p, o); sync(); changed(); } };
	}

	function open(spec) {
		var bg = document.createElement("div");
		bg.className = "ss-bg";
		bg.innerHTML = '<div class="ss-modal"><h2>' + esc(spec.modalTitle || "Make an image") + '</h2><p class="ss-sub">Pick a size and a style — it updates live. Everything is made in your browser.</p>' +
			'<div class="ss-layout"><div><div class="ss-prev" id="ssPrev"><canvas id="ssCanvas"></canvas></div><div class="ss-note" id="ssInfo" style="margin-top:6px;"></div></div><div><div id="ssCtl"></div>' +
			'<div class="ss-btns"><a class="primary" id="ssPng" download>Download PNG</a><a id="ssJpg" download>Download JPG</a><button type="button" id="ssCopy">Copy image</button><button type="button" id="ssDice">&#127922; Random style</button><button type="button" id="ssAll">Download every size</button><button type="button" id="ssAllStyles">Download every style</button><button type="button" id="ssClose">Close</button></div>' +
			'<div class="ss-note" id="ssNote"></div></div></div></div>';
		document.body.appendChild(bg);
		bg.addEventListener("mousedown", function (e) { if (e.target === bg) bg.remove(); });
		var canvas = bg.querySelector("#ssCanvas"), note = bg.querySelector("#ssNote"), timer = null, ctl, seq = 0;
		function draw() {
			var my = ++seq, o = ctl.get();
			render(canvas, spec, o).then(function () {
				if (my !== seq) return;
				bg.querySelector("#ssInfo").textContent = canvas.width + " × " + canvas.height + " px";
				var base = (spec.filename || "sctp-image") + "-" + o.format + "-" + o.style;
				try { bg.querySelector("#ssPng").href = canvas.toDataURL("image/png"); bg.querySelector("#ssPng").setAttribute("download", base + ".png"); bg.querySelector("#ssJpg").href = canvas.toDataURL("image/jpeg", 0.93); bg.querySelector("#ssJpg").setAttribute("download", base + ".jpg"); note.textContent = ""; }
				catch (e) { note.textContent = "The picture is blocked from export by the browser (cross-origin image)."; }
			});
		}
		ctl = controls(bg.querySelector("#ssCtl"), function () { clearTimeout(timer); timer = setTimeout(draw, 120); });
		bg.querySelector("#ssClose").onclick = function () { bg.remove(); };
		bg.querySelector("#ssDice").onclick = function () { ctl.randomize(); };
		bg.querySelector("#ssCopy").onclick = function () {
			var b = this;
			canvas.toBlob(function (blob) { try { navigator.clipboard.write([new ClipboardItem({ "image/png": blob })]).then(function () { b.textContent = "Copied!"; }, function () { b.textContent = "Copy failed"; }); } catch (e) { b.textContent = "Copy not supported"; } });
		};
		bg.querySelector("#ssAll").onclick = function () {
			var b = this, o = ctl.get(), list = FORMATS.filter(function (f) { return f.id !== "custom"; }), i = 0;
			b.disabled = true;
			(function next() {
				if (i >= list.length) { b.disabled = false; b.textContent = "Download every size"; draw(); return; }
				var f = list[i++]; b.textContent = "Rendering " + i + "/" + list.length + "…";
				var c = document.createElement("canvas");
				render(c, spec, Object.assign({}, o, { format: f.id })).then(function () {
					var a = document.createElement("a"); a.download = (spec.filename || "sctp-image") + "-" + f.id + "-" + o.style + ".png"; a.href = c.toDataURL("image/png"); document.body.appendChild(a); a.click(); a.remove();
					setTimeout(next, 350);
				});
			})();
		};
		bg.querySelector("#ssAllStyles").onclick = function () {
			var b = this, o = ctl.get(), i = 0;
			b.disabled = true;
			(function next() {
				if (i >= STYLES.length) { b.disabled = false; b.textContent = "Download every style"; draw(); return; }
				var sty = STYLES[i++]; b.textContent = "Rendering " + i + "/" + STYLES.length + "…";
				var c = document.createElement("canvas");
				render(c, spec, Object.assign({}, o, { style: sty.id, pattern: "auto", accent: "auto" })).then(function () {
					var a = document.createElement("a"); a.download = (spec.filename || "sctp-image") + "-" + o.format + "-" + sty.id + ".png"; a.href = c.toDataURL("image/png"); document.body.appendChild(a); a.click(); a.remove();
					setTimeout(next, 350);
				});
			})();
		};
		draw();
		return { close: function () { bg.remove(); } };
	}

	window.sctpShare = { FORMATS: FORMATS, STYLES: STYLES, PATTERNS: PATTERNS, LAYOUTS: LAYOUTS, render: render, open: open, controls: controls };
})();
