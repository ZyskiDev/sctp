package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiLinks;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.hunt.HuntClient;
import com.snailtools.shoplogger.hunt.HuntNotes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Golden Shell in-game: every open whorl and chapter on the left, the one
 * you pick on the right (the clue, your own puzzle, hints, your answer). The
 * same hunt as sctp.nl/hunt; pictures and the map squares are picked there.
 */
public class HuntScreen extends UiScreen {

	private static final String SITE = "https://sctp.nl/hunt/";
	private static final String[] ROMAN = { "", "I", "II", "III", "IV" };
	private static final int ROW_H = 12;
	private static final int KEY_ENTER = 257, KEY_KP_ENTER = 335;

	private record Row(String key, String label, boolean header, boolean done, boolean dim) {}
	private record Line(String text, int color) {}

	private HuntClient.State state;
	private boolean loading = true, busy;
	private String error, msg;
	private int msgColor = Theme.MUTED;
	private String selected;
	private int listScroll, detailScroll;
	private final Map<String, String> typed = new HashMap<>();
	private final List<Row> rows = new ArrayList<>();
	private final List<Line> lines = new ArrayList<>();
	private UiField answer;

	public HuntScreen(Screen parent) {
		super("The Golden Shell", parent, Section.HUNT);
		HuntClient.refreshSoon();
		load();
	}

	private static net.minecraft.client.Minecraft mc() { return net.minecraft.client.Minecraft.getInstance(); }

	private void load() {
		HuntClient.state().whenComplete((s, ex) -> mc().execute(() -> {
			loading = false;
			if (ex != null) error = HuntClient.messageOf(ex);
			else setState(s);
			rebuild();
		}));
	}

	private void setState(HuntClient.State s) {
		state = s;
		error = null;
		if (selected == null || find(selected) == null) selected = firstOpen();
	}

	// ---- layout ----

	private int listX() { return contentX(); }
	private int listW() { return Math.min(150, contentW() / 3); }
	private int detailX() { return listX() + listW() + 10; }
	private int detailW() { return contentW() - listW() - 10; }
	private int topY() { return contentY() + 16; }
	private int textBottom() { return contentBottom() - 62; }

	@Override
	protected void initContent() {
		answer = null;
		addRenderableWidget(new UiButton(contentX() + contentW() - 104, contentY() - 2, 104, 14, "Open on sctp.nl ↗", UiButton.Style.GHOST, () -> UiLinks.open(SITE)));
		if (state == null || state.player == null) return;
		buildRows();
		buildLines();

		int dx = detailX(), dw = detailW();
		int answerY = contentBottom() - 44, actionsY = contentBottom() - 20;
		String placeholder = answerPlaceholder();
		if (placeholder != null) {
			int btnW = 56;
			String key = answerRef();
			answer = new UiField(font, dx, answerY, dw - btnW - 6, 20, placeholder, false);
			answer.box.setValue(typed.getOrDefault(key, ""));
			answer.box.setResponder(v -> typed.put(key, v));
			addRenderableOnly(answer.frame());
			addRenderableWidget(answer.box);
			addRenderableWidget(new UiButton(dx + dw - btnW, answerY, btnW, 20, "Check", UiButton.Style.PRIMARY, this::submit));
			setInitialFocus(answer.box);
		}
		int ax = dx;
		for (Action a : actions()) {
			int w = font.width(a.label) + 14;
			if (ax + w > dx + dw) break;
			addRenderableWidget(new UiButton(ax, actionsY, w, 18, a.label, UiButton.Style.SECONDARY, a.run));
			ax += w + 4;
		}
	}

	private record Action(String label, Runnable run) {}

	// ---- what's selected ----

	private Object find(String key) {
		if (state == null || state.whorls == null || key == null) return null;
		if ("apex".equals(key)) return state.apex;
		for (HuntClient.Whorl w : state.whorls) {
			if (key.equals("w" + w.whorl)) return w;
			if (w.chapters != null) for (HuntClient.Chapter c : w.chapters) if (key.equals(c.id)) return c;
		}
		return null;
	}

	private HuntClient.Whorl whorlOf(HuntClient.Chapter c) {
		for (HuntClient.Whorl w : state.whorls) if (w.chapters != null && w.chapters.contains(c)) return w;
		return null;
	}

	private String firstOpen() {
		if (state == null || state.whorls == null) return null;
		for (HuntClient.Whorl w : state.whorls) {
			if (!w.unlocked) continue;
			if (w.chapters != null) for (HuntClient.Chapter c : w.chapters) if (!c.solved) return c.id;
			if (!w.solved) return "w" + w.whorl;
		}
		return state.apex != null ? "apex" : state.whorls.isEmpty() ? null : (state.whorls.get(0).chapters.isEmpty() ? null : state.whorls.get(0).chapters.get(0).id);
	}

	private void buildRows() {
		rows.clear();
		for (HuntClient.Whorl w : state.whorls) {
			rows.add(new Row(null, "Whorl " + ROMAN[w.whorl] + " · " + w.title, true, w.solved, !w.unlocked));
			if (!w.unlocked) continue;
			if (w.chapters != null) for (HuntClient.Chapter c : w.chapters) rows.add(new Row(c.id, c.title, false, c.solved, false));
			rows.add(new Row("w" + w.whorl, "Whorl puzzle", false, w.solved, !w.puzzleOpen));
		}
		if (state.apex != null) {
			rows.add(new Row(null, "The Apex", true, state.apex.solved, false));
			rows.add(new Row("apex", state.apex.title == null ? "The Apex" : state.apex.title, false, state.apex.vaultSolved, false));
		}
	}

	private void add(String text, int color) {
		for (String l : HuntNotes.wrap(font, text == null ? "" : text, detailW() - 4)) lines.add(new Line(l, color));
	}
	private void gap() { lines.add(new Line("", Theme.TEXT)); }

	private void buildLines() {
		lines.clear();
		Object o = find(selected);
		if (o instanceof HuntClient.Chapter c) {
			add(c.title, HuntClient.GOLD);
			gap();
			add("“" + c.clue + "”", Theme.TEXT);
			gap();
			if (c.content != null) for (HuntClient.Block b : c.content) {
				if ("list".equals(b.t) && b.items != null) {
					for (int i = 0; i < b.items.size(); i++) add((Boolean.FALSE.equals(b.ordered) ? "• " : (i + 1) + ". ") + b.items.get(i), Theme.TEXT);
				} else if ("image".equals(b.t)) add("[There's a picture here: open this chapter on sctp.nl to see it.]", Theme.MUTED);
				else if ("link".equals(b.t)) add("[" + (b.v != null ? b.v : b.href) + ": see the button below]", Theme.MUTED);
				else add(b.v, "code".equals(b.t) ? Theme.TEAL : Theme.TEXT);
				gap();
			}
			if (c.solved) add("Solved. Fragment: " + c.fragment, Theme.ACCENT);
			if (c.hints != null) for (HuntClient.Hint h : c.hints) {
				if (h.revealed) add(h.label + ": " + h.text, HuntClient.GOLD);
				else if (!h.available) add(h.label + " " + HuntClient.until(h.availableAt) + ".", Theme.FAINT);
			}
		} else if (o instanceof HuntClient.Whorl w) {
			add("Whorl " + ROMAN[w.whorl] + " puzzle", HuntClient.GOLD);
			gap();
			if (w.solved) {
				if (w.square != null) add("Solved: your square is " + w.square.size + " blocks from X " + w.square.x + ", Z " + w.square.z + ".", Theme.ACCENT);
				else add("Solved: you hold the key.", Theme.ACCENT);
			} else if (!w.puzzleOpen) {
				add("Solve all four chapters of this whorl to put the fragments together.", Theme.MUTED);
			} else {
				if (w.puzzle != null) add("“" + w.puzzle + "”", Theme.TEXT);
				gap();
				if (w.chapters != null) for (HuntClient.Chapter c : w.chapters) add("• " + c.fragment, HuntClient.GOLD);
				gap();
				add("square".equals(w.answerKind) ? "Pick your square on the world map on sctp.nl. Three wrong picks a day at most." : "Three wrong tries a day at most.", Theme.MUTED);
				if (w.nudge != null) {
					if (w.nudge.revealed) add("Nudge: " + w.nudge.text, HuntClient.GOLD);
					else if (!w.nudge.available) add("Nudge " + HuntClient.until(w.nudge.availableAt) + ".", Theme.FAINT);
				}
			}
		} else if (o instanceof HuntClient.Apex a) {
			add(a.title, HuntClient.GOLD);
			gap();
			add("“" + a.riddle + "”", Theme.TEXT);
			gap();
			add("The marks are drawn on the world map on sctp.nl.", Theme.MUTED);
			if (a.solved) add("That's the block. The rest happens on Firefly.", Theme.ACCENT);
			if (a.vaultSolved) add("You reached the vault.", Theme.ACCENT);
			if (state.shell != null && state.shell.found) add("The Golden Shell has been found" + (state.shell.by != null ? " by " + state.shell.by : "") + ".", HuntClient.GOLD);
		}
		detailScroll = Math.max(0, Math.min(detailScroll, maxDetailScroll()));
	}

	private int maxDetailScroll() {
		int visible = (textBottom() - topY()) / 10;
		return Math.max(0, lines.size() - visible);
	}

	/** The answer box's placeholder, or null when there's nothing to type here. */
	private String answerPlaceholder() {
		Object o = find(selected);
		if (o instanceof HuntClient.Chapter c) return !c.solved ? ("numbers".equals(c.answerKind) ? "Numbers" : "One word") : null;
		if (o instanceof HuntClient.Whorl w) return w.puzzleOpen && !w.solved && "key".equals(w.answerKind) ? "The key's numbers" : null;
		if (o instanceof HuntClient.Apex a) return !a.solved ? "x y z" : !a.vaultSolved ? "The lectern's word" : null;
		return null;
	}

	private String answerRef() {
		Object o = find(selected);
		if (o instanceof HuntClient.Apex a) return a.solved ? "vault" : "apex";
		return selected;
	}

	private List<Action> actions() {
		List<Action> out = new ArrayList<>();
		Object o = find(selected);
		if (o instanceof HuntClient.Chapter c) {
			if (c.content != null) for (HuntClient.Block b : c.content) {
				if ("link".equals(b.t) && b.href != null && b.href.contains("rare-dle")) out.add(new Action("Play your Rare-dle boards", () -> mc().setScreenAndShow(new RaredleScreen(this, 1))));
				else if ("link".equals(b.t) && b.href != null && b.href.startsWith("/")) out.add(new Action("Open link ↗", () -> UiLinks.open("https://sctp.nl" + b.href)));
				else if ("image".equals(b.t)) out.add(new Action("See the picture ↗", () -> UiLinks.open(SITE)));
			}
			if (!c.solved && c.hints != null) for (HuntClient.Hint h : c.hints) {
				if (h.available && !h.revealed) out.add(new Action("Show the " + h.label.toLowerCase(), () -> hint(c.id, h.tier)));
			}
		} else if (o instanceof HuntClient.Whorl w) {
			if (w.puzzleOpen && !w.solved && "square".equals(w.answerKind)) out.add(new Action("Pick it on the map ↗", () -> UiLinks.open("https://sctp.nl/world/?hunt=1")));
			if (w.nudge != null && w.nudge.available && !w.nudge.revealed && !w.solved) out.add(new Action("Show the nudge", () -> hint("w" + w.whorl, 3)));
		} else if (o instanceof HuntClient.Apex a) {
			out.add(new Action("The map ↗", () -> UiLinks.open("https://sctp.nl/world/?hunt=1")));
			if (a.solved && !a.claimed && (state.shell == null || !state.shell.found)) out.add(new Action("I took the shell ↗", () -> UiLinks.open(SITE)));
		}
		return out;
	}

	// ---- actions ----

	private void submit() {
		if (busy || answer == null) return;
		String ref = answerRef(), value = answer.box.getValue();
		if (value.isBlank()) return;
		busy = true;
		msg = "Checking…";
		msgColor = Theme.MUTED;
		HuntClient.answer(ref, value).whenComplete((r, ex) -> mc().execute(() -> {
			busy = false;
			if (ex != null) { msg = HuntClient.messageOf(ex); msgColor = Theme.WARN; }
			else {
				if (r.correct) { typed.remove(ref); msg = "Solved!"; msgColor = Theme.ACCENT; }
				else { msg = "Not quite."; msgColor = Theme.WARN; }
				if (r.state != null) setState(r.state);
			}
			rebuild();
		}));
	}

	private void hint(String id, int tier) {
		if (busy) return;
		busy = true;
		HuntClient.hint(id, tier).whenComplete((s, ex) -> mc().execute(() -> {
			busy = false;
			if (ex != null) { msg = HuntClient.messageOf(ex); msgColor = Theme.WARN; }
			else { setState(s); msg = null; }
			rebuild();
		}));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (answer != null && answer.box.isFocused() && (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER)) {
			submit();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (state != null && event.x() >= listX() && event.x() < listX() + listW() && event.y() >= topY()) {
			int i = (int) ((event.y() - topY()) / ROW_H) + listScroll;
			if (i >= 0 && i < rows.size() && rows.get(i).key() != null && !rows.get(i).key().equals(selected)) {
				selected = rows.get(i).key();
				detailScroll = 0;
				msg = null;
				rebuild();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = scrollY > 0 ? -1 : 1;
		if (mouseX < detailX()) {
			int visible = (contentBottom() - topY()) / ROW_H;
			listScroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), listScroll + step));
		} else {
			detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll + step * 2));
		}
		return true;
	}

	// ---- drawing ----

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (state == null || state.player == null) return;
		int solved = 0, total = 0;
		for (HuntClient.Whorl w : state.whorls) if (w.chapters != null) for (HuntClient.Chapter c : w.chapters) { total++; if (c.solved) solved++; }
		Draw.textShadow(g, font, "Ledger " + state.player.ledger, contentX(), contentY() + 1, HuntClient.GOLD);
		Draw.text(g, font, solved + " of " + total + " chapters", contentX() + font.width("Ledger " + state.player.ledger) + 10, contentY() + 1, Theme.MUTED);

		// the list
		int x = listX(), w = listW(), y = topY();
		Draw.card(g, x - 2, y - 3, w + 4, contentBottom() - y + 3, Theme.PANEL, Theme.LINE_SOFT);
		int visible = (contentBottom() - y) / ROW_H;
		for (int i = listScroll; i < rows.size() && i < listScroll + visible; i++) {
			Row r = rows.get(i);
			int ry = y + (i - listScroll) * ROW_H;
			boolean sel = r.key() != null && r.key().equals(selected);
			boolean hover = r.key() != null && mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
			if (sel || hover) Draw.rect(g, x, ry, w, ROW_H, sel ? Theme.ACCENT_TINT : Theme.PANEL_ALT);
			int color = r.header() ? (r.dim() ? Theme.FAINT : HuntClient.GOLD) : r.dim() ? Theme.FAINT : Theme.TEXT;
			int tx = x + (r.header() ? 3 : 10);
			String label = (r.done() ? "✔ " : "") + r.label();
			Draw.text(g, font, Draw.trim(font, label, w - (tx - x) - 2), tx, ry + 2, r.done() && !r.header() ? Theme.ACCENT : color);
		}

		// the detail
		int dx = detailX(), dy = topY();
		int maxLines = (textBottom() - dy) / 10;
		for (int i = 0; i < maxLines && i + detailScroll < lines.size(); i++) {
			Line l = lines.get(i + detailScroll);
			Draw.text(g, font, l.text(), dx, dy + i * 10, l.color());
		}
		if (detailScroll > 0) Draw.right(g, font, "↑", dx + detailW(), dy, Theme.FAINT);
		if (detailScroll < maxDetailScroll()) Draw.right(g, font, "↓ scroll", dx + detailW(), textBottom() - 10, Theme.FAINT);
		if (msg != null) Draw.text(g, font, Draw.trim(font, msg, detailW()), dx, contentBottom() - 56, msgColor);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (loading) { listState(g, contentX(), contentY(), contentW(), contentH(), true, false, false, null, null); return; }
		if (error != null && state == null) { Draw.emptyState(g, font, contentX(), contentY(), contentW(), contentH(), "The ledger couldn't be opened", error, Theme.WARN); return; }
		if (state != null && state.player == null) Draw.emptyState(g, font, contentX(), contentY(), contentW(), contentH(), "Not in the ledger yet", "Bring your code to sctp.nl/hunt first.", HuntClient.GOLD);
	}
}
