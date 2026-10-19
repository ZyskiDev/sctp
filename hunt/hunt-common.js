// Shared bits for /hunt/ and /hunt/admin/: the API, the sctp.nl session (from
// account-widget.js), escaping and dates.
(function(){
	"use strict";
	var API = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	// account-widget.js loads deferred, so read its saved session directly until it's there.
	function session(){
		if(window.sctpAccount) return window.sctpAccount.getSession();
		try { var s = JSON.parse(localStorage.getItem("sctp_session") || "null"); return s && s.token && Date.parse(s.expiresAt) >= Date.now() ? s : null; } catch(e){ return null; }
	}
	function api(path, opts){
		opts = opts || {};
		var s = session(), headers = Object.assign({}, opts.headers || {});
		if(s) headers.Authorization = "Bearer " + s.token;
		if(opts.json !== undefined){ headers["Content-Type"] = "application/json"; opts.body = JSON.stringify(opts.json); }
		return fetch(API + path, { method: opts.method || (opts.body ? "POST" : "GET"), headers: headers, body: opts.body }).then(function(r){
			return r.json().catch(function(){ return {}; }).then(function(d){
				if(!r.ok){ var e = new Error(d.error || ("HTTP " + r.status)); e.status = r.status; e.data = d; throw e; }
				return d;
			});
		});
	}
	function esc(s){ return String(s == null ? "" : s).replace(/[&<>"']/g, function(c){ return { "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]; }); }
	function $(id){ return document.getElementById(id); }
	function when(iso){
		if(!iso) return "";
		var d = new Date(iso);
		return d.toLocaleDateString(undefined, { day: "numeric", month: "short" }) + " " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
	}
	function until(iso){
		var ms = Date.parse(iso) - Date.now();
		if(ms <= 0) return "now";
		var h = Math.floor(ms / 3600000), m = Math.ceil((ms % 3600000) / 60000);
		if(h >= 48) return "in " + Math.round(h / 24) + " days";
		if(h >= 1) return "in " + h + " h " + m + " min";
		return "in " + m + " min";
	}
	function duration(ms){
		var h = Math.floor(ms / 3600000), d = Math.floor(h / 24);
		if(d >= 1) return d + "d " + (h % 24) + "h";
		var m = Math.floor((ms % 3600000) / 60000);
		return h + "h " + m + "m";
	}
	function fmt(n){ return (n < 0 ? "−" : "") + Math.abs(n).toLocaleString("en-US"); }
	window.HUNT = { API: API, session: session, api: api, esc: esc, $: $, when: when, until: until, duration: duration, fmt: fmt };
})();
