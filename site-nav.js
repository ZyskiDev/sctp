// Shared site navigation — the second deliberate "shared JS" exception
// alongside account-widget.js (see that file's own comment for the
// rationale). Every page needed the exact same link set, but each one kept
// its own hand-copied version — different link orders, a mix of "../",
// bare-relative, and root-absolute hrefs, some pages missing links others
// had, and one broken link to a "docs.html" that doesn't exist (the real
// docs site lives at /docs/). Copies drifting out of sync like that is
// exactly what made the nav "a mess" — one shared source fixes it for good.
//
// Usage: a page just needs an empty <nav id="siteNavMount"></nav> in its
// header, plus this script (before account-widget.js, both deferred) — this
// renders the link list AND leaves an #accountWidgetMount div inside it for
// account-widget.js to find, same as it always has.
//
// Below MOBILE_BREAKPOINT the link list collapses behind a hamburger button
// that opens a floating dropdown (not an in-flow block) — the nav always
// stays on the same line as the page's title/logo, never pushed below it,
// same as it naturally does on wider screens. The account button/login state
// stays visible either way; only the link list itself collapses.
(function () {
	"use strict";

	var LINKS = [
		{ href: "/", label: "Home" },
		{ href: "/items/", label: "Items" },
		{ href: "/list/", label: "Build List" },
		{ href: "/marketplace/", label: "Marketplace" },
		{ href: "/stats/", label: "Stats" },
		{ href: "/roadmap/", label: "Roadmap" },
		{ href: "/docs/", label: "Info" },
	];
	var MOBILE_BREAKPOINT = 760;

	var style = document.createElement("style");
	style.textContent =
		// flex-shrink:0 keeps the nav cluster itself always full-size — the
		// page's own title/brand block is what should shrink/wrap first, not
		// this (see the min-width:0 note on .brand-row et al in each page).
		// position:relative anchors the mobile dropdown below (see .site-nav-links).
		"#siteNavMount{display:flex;align-items:center;gap:16px;flex-shrink:0;position:relative;}" +
		"#siteNavMount a{color:var(--shell,#D9C89A);text-decoration:none;font-size:14px;font-weight:600;}" +
		"#siteNavMount a:hover,#siteNavMount a.active{color:var(--accent,#B7E23D);}" +
		// Base (desktop) layout for the link list — the mobile media query
		// below overrides this to a floating dropdown instead.
		".site-nav-links{display:flex;align-items:center;gap:16px;}" +
		".site-nav-row{display:flex;align-items:center;gap:12px;}" +
		".site-nav-toggle{display:none;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;width:38px;height:38px;font-size:17px;line-height:1;cursor:pointer;align-items:center;justify-content:center;flex-shrink:0;}" +
		"@media (max-width:" + MOBILE_BREAKPOINT + "px){" +
			".site-nav-toggle{display:inline-flex;}" +
			// A floating dropdown (same treatment as account-widget.js's own
			// .acct-menu) rather than an in-flow block — this is what keeps the
			// header row itself always on one line, nav staying to the right of
			// the title instead of ever pushing below it.
			".site-nav-links{display:none;position:absolute;top:calc(100% + 8px);right:0;flex-direction:column;align-items:stretch;min-width:200px;background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:10px;padding:6px;z-index:90;box-shadow:0 8px 24px rgba(0,0,0,.35);}" +
			".site-nav-links.open{display:flex;}" +
			".site-nav-links a{padding:10px 12px;border-radius:6px;font-size:14px;}" +
			".site-nav-links a:hover{background:var(--panel-alt,#22332A);}" +
		"}";
	document.head.appendChild(style);

	// "/foo", "/foo/", and "/foo/index.html" are all the same page for
	// highlighting purposes.
	function normalize(path) {
		return path.replace(/index\.html$/, "").replace(/\/+$/, "") || "/";
	}
	function isActive(href) {
		var here = normalize(location.pathname);
		var target = normalize(href);
		if (target === "/") return here === "/";
		return here === target || here.indexOf(target + "/") === 0;
	}

	function render() {
		var mount = document.getElementById("siteNavMount");
		if (!mount) return;
		var linksHtml = LINKS.map(function (l) {
			var attrs = l.requireAuth ? " data-require-auth hidden" : "";
			return '<a href="' + l.href + '"' + (isActive(l.href) ? ' class="active"' : "") + attrs + ">" + l.label + "</a>";
		}).join("");

		mount.innerHTML =
			'<div class="site-nav-links" id="siteNavLinks">' + linksHtml + '</div>' +
			'<div class="site-nav-row">' +
				'<button type="button" class="site-nav-toggle" aria-label="Toggle menu" aria-expanded="false">&#9776;</button>' +
				'<div id="accountWidgetMount"></div>' +
			'</div>';

		var toggle = mount.querySelector(".site-nav-toggle");
		var linksEl = document.getElementById("siteNavLinks");
		function setOpen(open) {
			linksEl.classList.toggle("open", open);
			toggle.setAttribute("aria-expanded", open ? "true" : "false");
		}
		toggle.addEventListener("click", function () { setOpen(!linksEl.classList.contains("open")); });
		// Tapping a link closes the drawer immediately rather than leaving it
		// open behind the page that just navigated (matters most for same-page
		// anchors, and avoids a jarring "still open" flash on slow loads).
		linksEl.querySelectorAll("a").forEach(function (a) {
			a.addEventListener("click", function () { setOpen(false); });
		});
		// Resizing past the breakpoint (rotating a tablet, restoring a
		// dev-tools panel) shouldn't leave the drawer stuck open once it's
		// no longer collapsed.
		window.addEventListener("resize", function () {
			if (window.innerWidth > MOBILE_BREAKPOINT) setOpen(false);
		});
	}

	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", render);
	else render();
})();
