package dev.psyche.client;

import dev.psyche.Psyche;
import dev.psyche.core.Content;
import dev.psyche.core.Content.Challenge;
import dev.psyche.core.Grader;
import dev.psyche.core.Profile;
import dev.psyche.core.Profile.Mode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One revision question, always typed. Gameplay questions can't be skipped or escaped: you answer correctly,
 * or you look at the model answer and type it out yourself before you can carry on. A correct answer may lead
 * straight into a follow-up question that builds on it.
 */
final class ChallengeScreen extends Screen {
    private enum State { ASK, COPY, DONE }
    /** The hint ladder: each rung gives a bit more away (and costs a bit more XP). */
    private enum Rung { QUESTION, CLUE, SHAPE, BANK }
    private static final int FIELD_H = 20;

    private final Challenge c;
    private final String source, prev; // prev: the answer a follow-up builds on
    private final int depth;
    private final List<String> bank;
    private final List<Rung> rungs = new ArrayList<>();
    private Mode mode;
    private State state = State.ASK;
    private boolean retried, ok;
    private int hints, earned;
    private String draft = "", given = "";
    private Grader.Result result;
    private Challenge follow;
    private final String speaker; // villager name and job, for villager questions
    private final float pitch;
    private final Runnable onSolved;

    private int px, py, pw, ph;
    private final List<Block> top = new ArrayList<>(), bottom = new ArrayList<>();
    private interface Draw { void at(DrawContext ctx, int x, int y); }
    private record Block(int h, Draw d) {}

    ChallengeScreen(Challenge c, String source) { this(c, source, null, 0); }

    ChallengeScreen(Challenge c, String source, String prev, int depth) { this(c, source, prev, depth, null, 1f, null); }

    ChallengeScreen(Challenge c, String source, String speaker, float pitch, Runnable onSolved) {
        this(c, source, null, 0, speaker, pitch, onSolved);
    }

    private ChallengeScreen(Challenge c, String source, String prev, int depth, String speaker, float pitch, Runnable onSolved) {
        super(Text.literal("Question"));
        this.c = c;
        this.source = source;
        this.prev = prev;
        this.depth = depth;
        this.speaker = speaker;
        this.pitch = pitch;
        this.onSolved = onSolved;
        this.mode = PsycheClient.profile.mode(c);
        this.bank = c.tiles(PsycheClient.RND);
        ladder();
    }

    private void ladder() {
        rungs.clear();
        if (mode == Mode.APPLY) rungs.add(Rung.QUESTION);
        if (!c.hint.isEmpty()) rungs.add(Rung.CLUE);
        rungs.add(Rung.SHAPE);
        if (mode != Mode.GUIDED) rungs.add(Rung.BANK);
        hints = Math.min(hints, rungs.size());
    }

    private boolean shown(Rung r) { int i = rungs.indexOf(r); return i >= 0 && i < hints; }

    private boolean forced() { return !source.equals("review") && speaker == null; }

    /** Hints used, plus 2 if the first try was wrong. */
    private int help() { return hints + (retried ? 2 : 0); }

    @Override
    protected void applyBlur(float delta) { if (speaker == null) super.applyBlur(delta); } // keep the villager visible

    @Override
    protected void init() {
        pw = Math.min(360, width - 24);
        px = (width - pw) / 2;
        int iw = pw - 24;
        layout(iw);
        boolean field = state != State.DONE;
        int sum = 26;
        for (Block b : top) sum += b.h;
        int fieldAt = sum;
        if (field) sum += FIELD_H + 8;
        for (Block b : bottom) sum += b.h;
        ph = sum + 4 + 20 + 8;
        py = Math.max(6, (height - ph) / 2);
        int ix = px + 12;

        if (field) {
            TextFieldWidget f = addDrawableChild(new TextFieldWidget(textRenderer, ix, py + fieldAt, iw, FIELD_H, Text.empty()));
            f.setMaxLength(300);
            f.setText(draft);
            f.setChangedListener(s -> draft = s);
            f.setPlaceholder(UI.color(state == State.COPY ? "Type out the model answer…" : mode == Mode.FILL ? "Type the missing word(s)…"
                    : c.shortAnswer() ? "Type your answer…" : "Type your answer in a sentence or two…", UI.MUTED));
            setInitialFocus(f);
        }
        List<Map.Entry<String, Runnable>> row = new ArrayList<>();
        switch (state) {
            case ASK -> {
                if (hints < rungs.size()) row.add(Map.entry("Hint " + (hints + 1) + "/" + rungs.size(), () -> { hints++; rebuild(); }));
                Mode down = Profile.easier(mode, c);
                if (down != null) row.add(Map.entry("Easier", () -> { mode = down; draft = ""; ladder(); rebuild(); }));
                if (result != null) row.add(Map.entry("Show answer", this::reveal));
                if (!forced()) row.add(Map.entry("Close", this::close));
                row.add(Map.entry("Check ✔", this::check));
            }
            case COPY -> row.add(Map.entry("Check ✔", this::check));
            case DONE -> row.add(Map.entry(follow != null ? "Go deeper ➜" : onSolved != null ? (ok ? "Trade ➜" : "Leave")
                    : PsycheClient.QUEUE.isEmpty() ? "Continue" : "Next question (" + PsycheClient.QUEUE.size() + ")", this::next));
        }
        int bw = (iw - 4 * (row.size() - 1)) / row.size();
        for (int i = 0; i < row.size(); i++) {
            Runnable r = row.get(i).getValue();
            addDrawableChild(ButtonWidget.builder(Text.literal(row.get(i).getKey()), b -> r.run()).dimensions(ix + i * (bw + 4), py + ph - 28, bw, 20).build());
        }
    }

    /** What the player is asked, in the form this stage of the ladder uses. */
    private Text prompt() {
        return switch (mode) {
            case APPLY -> Text.literal(c.apply);
            case FILL -> Text.literal("Complete: " + c.prefix()).append(UI.color("_____", UI.AMBER)).append(c.suffix());
            default -> Text.literal(c.ask);
        };
    }

    /** Builds what sits above the text box (top) and below it (bottom), so the panel fits it. */
    private void layout(int w) {
        top.clear();
        bottom.clear();
        if (speaker != null && c.npc != null) {
            top.add(text(Text.literal(speaker + ":"), UI.AMBER, w, 2));
            top.add(text(Text.literal("“" + c.npc + "”").styled(s -> s.withItalic(true)), UI.WHITE, w, 6));
        }
        if (prev != null) top.add(text(Text.literal("You said: “" + prev + "”").styled(s -> s.withItalic(true)), UI.MUTED, w, 4));
        top.add(text(prompt(), speaker != null ? UI.VIOLET_LIGHT : UI.WHITE, w, 8));

        switch (state) {
            case ASK -> {
                if (shown(Rung.QUESTION)) top.add(text(Text.literal("In other words: " + c.ask), UI.MUTED, w, 4));
                if (shown(Rung.CLUE)) top.add(text(Text.literal("Hint: " + c.hint), UI.AMBER, w, 4));
                if (shown(Rung.SHAPE)) top.add(text(Text.literal(shape()), UI.AMBER, w, 4));
                if (mode == Mode.GUIDED || shown(Rung.BANK)) top.add(words(w));
                if (result != null) feedback(w);
            }
            case COPY -> {
                top.add(text(Text.literal("Model answer. Type it out (the key ideas are enough):"), UI.MUTED, w, 2));
                top.add(text(Text.literal(c.answer), UI.GREEN, w, 4));
                top.add(text(Text.literal("Key ideas: " + ideas()), UI.MUTED, w, 8));
                if (result.trap() != null) bottom.add(text(Text.literal("⚠ " + result.trap().why), UI.AMBER, w, 3));
                else if (!c.why.isEmpty()) bottom.add(text(Text.literal(c.why), UI.AMBER, w, 3));
            }
            case DONE -> {
                String head;
                int color;
                if (!ok) {
                    head = speaker != null ? "The villager looks puzzled. No trade this time." : "Noted. This one will come back soon.";
                    color = UI.MUTED;
                } else if (result.verdict() == Grader.Verdict.TYPO) {
                    StringBuilder sb = new StringBuilder("Correct! Check the spelling: ");
                    for (int i = 0; i < result.fixes().size(); i++) {
                        Grader.Fix f = result.fixes().get(i);
                        sb.append(i > 0 ? ", " : "").append(f.typed()).append(" → ").append(f.meant());
                    }
                    head = sb.append("  +").append(earned).append(" XP").toString();
                    color = UI.AMBER;
                } else {
                    head = (hints == 0 && !retried ? "Spot on!" : "Correct!") + "  +" + earned + " XP";
                    color = UI.GREEN;
                }
                top.add(text(Text.literal(head), color, w, 4));
                top.add(text(Text.literal("Model answer: ").styled(s -> s.withColor(UI.MUTED)).append(UI.color(c.answer, UI.WHITE)), UI.WHITE, w, 4));
                if (!c.why.isEmpty()) top.add(text(Text.literal(c.why), UI.MUTED, w, 4));
                String src = src();
                if (!src.isEmpty()) top.add(text(Text.literal("Source: " + src), UI.VIOLET_LIGHT, w, 4));
                if (follow != null) top.add(text(Text.literal("Now take it one step further!"), UI.TEAL, w, 4));
            }
        }
    }

    /** What a wrong try gets back: the misconception if we recognise it, the ideas already there, and what other terms mean. */
    private void feedback(int w) {
        if (result.trap() != null) bottom.add(text(Text.literal("⚠ " + result.trap().why), UI.AMBER, w, 3));
        else if (!result.order() && result.count() >= result.needed())
            bottom.add(text(Text.literal("The right ideas, but not in the right order. Try again."), UI.RED, w, 3));
        else if (c.shortAnswer()) bottom.add(text(Text.literal("Not quite. Try again."), UI.RED, w, 3));
        else {
            int missing = result.needed() - result.count();
            bottom.add(text(Text.literal("Not quite: " + missing + " more key idea" + (missing == 1 ? "" : "s") + " needed."), UI.RED, w, 3));
        }
        if (!c.shortAnswer()) {
            List<Content.Idea> all = c.ideas();
            for (int i = 0; i < all.size(); i++)
                if (result.hits()[i]) bottom.add(text(Text.literal("✓ " + all.get(i).idea()), UI.GREEN, w, 1));
        }
        for (Content.Term t : result.mixed())
            bottom.add(text(Text.literal("“" + t.name() + "” means: " + t.def), UI.MUTED, w, 2));
    }

    /** Hint: the first letters of a one-word answer, or how many ideas are needed and one of them. */
    private String shape() {
        if (c.shortAnswer() || mode == Mode.FILL) {
            String a = mode == Mode.FILL ? c.blank() : c.ideas().get(0).any().get(0);
            int n = Math.max(1, Math.min(3, a.length() / 3));
            String words = a.trim().contains(" ") ? a.trim().split("\\s+").length + " words, " : "";
            return "Starts with “" + a.substring(0, n) + "…” (" + words + a.replace(" ", "").length() + " letters)";
        }
        List<Content.Idea> all = c.ideas();
        String one = all.get(0).idea();
        if (result != null) for (int i = 0; i < all.size(); i++) if (!result.hits()[i]) { one = all.get(i).idea(); break; }
        return "Cover " + c.needed() + " key idea" + (c.needed() == 1 ? "" : "s") + (c.order ? ", in order" : "") + ". One of them: " + one;
    }

    private String ideas() {
        StringBuilder sb = new StringBuilder();
        for (Content.Idea i : c.ideas()) sb.append(sb.length() > 0 ? " · " : "").append(i.idea());
        return sb.toString();
    }

    private String src() {
        if (c.src != null) return c.src;
        var t = PsycheClient.CONTENT.topics.get(c.topic);
        return t != null ? t.src() : "";
    }

    private Block text(Text t, int color, int w, int gap) {
        int h = textRenderer.wrapLines(t, w).size() * 9;
        return new Block(h + gap, (ctx, x, y) -> ctx.drawTextWrapped(textRenderer, t, x, y, w, color));
    }

    /** The word bank: the key terms you need (plus a couple you don't), shuffled. */
    private Block words(int w) {
        int cx = 0, rows = 1;
        for (String s : bank) {
            int tw = textRenderer.getWidth(s) + 10;
            if (cx + tw > w && cx > 0) { cx = 0; rows++; }
            cx += tw + 3;
        }
        return new Block(rows * 15 + 6, (ctx, x, y) -> {
            int bx = x, by = y;
            for (String s : bank) {
                int tw = textRenderer.getWidth(s) + 10;
                if (bx + tw > x + w && bx > x) { bx = x; by += 15; }
                UI.round(ctx, bx, by, tw, 13, UI.TILE);
                ctx.drawText(textRenderer, s, bx + 5, by + 3, UI.WHITE, false);
                bx += tw + 3;
            }
        });
    }

    private void rebuild() { clearAndInit(); }

    private void next() {
        if (follow != null) {
            client.setScreen(new ChallengeScreen(follow, source.equals("review") ? "review" : "suite", given, depth + 1));
            return;
        }
        close();
        if (onSolved != null) {
            if (ok) onSolved.run();
            return;
        }
        if (!PsycheClient.QUEUE.isEmpty()) PsycheClient.openNext(client);
    }

    /** Gives up on this one: it counts as a miss, and the player must type out the model answer to move on. */
    private void reveal() {
        PsycheClient.profile.record(c, false, help(), given, mode, result.trap());
        state = State.COPY;
        draft = "";
        rebuild();
    }

    private void check() {
        String input = draft.trim();
        if (input.isEmpty()) return;
        Grader.Result r = Grader.check(input, c, PsycheClient.CONTENT, state == State.ASK && mode == Mode.FILL);
        if (state == State.COPY) {
            if (!r.ok()) { sound(false); return; }
            ok = false;
            state = State.DONE;
        } else {
            given = mode == Mode.FILL ? c.prefix() + input + c.suffix() : input;
            result = r;
            if (!r.ok()) {
                if (!retried && hints == 0 && rungs.contains(Rung.CLUE) && r.trap() == null) hints = rungs.indexOf(Rung.CLUE) + 1; // the clue comes free after a miss
                retried = true;
                sound(false);
                rebuild();
                return;
            }
            ok = true;
            int xp = mode.xp + 3 * (c.level - 1);
            double scale = Math.max(0.25, 1 - 0.25 * hints) * (retried ? 0.5 : 1);
            earned = Math.max(1, (int) Math.round(xp * scale));
            PsycheClient.profile.record(c, true, help(), given, mode, null);
            if (ClientPlayNetworking.canSend(Psyche.Reward.ID)) ClientPlayNetworking.send(new Psyche.Reward(earned));
            if (speaker == null && depth < 2) follow = PsycheClient.CONTENT.followUp(c, PsycheClient.profile, PsycheClient.RND);
            sound(true);
            state = State.DONE;
        }
        rebuild();
    }

    private void sound(boolean good) {
        var s = speaker != null ? (good ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_NO) : good ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.ENTITY_VILLAGER_NO;
        client.getSoundManager().play(PositionedSoundInstance.master(s, speaker != null ? pitch : good ? 1.3f : 1f, 0.6f));
    }

    @Override
    public boolean shouldCloseOnEsc() { return state == State.DONE && follow == null || state != State.DONE && !forced(); }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        UI.panel(ctx, px, py, pw, ph);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int ix = px + 12;
        var topic = PsycheClient.CONTENT.topics.get(c.topic);
        String chip = state == State.COPY || state == State.DONE && !ok ? "Copy out · 0 XP" : mode.label + " · " + Profile.levelName(c.level);
        int cw = textRenderer.getWidth(chip) + 8;
        String head = (topic != null ? topic.name() : c.topic) + " · " + PsycheClient.label(source);
        ctx.drawText(textRenderer, textRenderer.trimToWidth(head, pw - 32 - cw), ix, py + 10, UI.MUTED, false);
        UI.round(ctx, px + pw - 12 - cw, py + 7, cw, 13, state == State.COPY ? UI.RED : UI.VIOLET);
        ctx.drawText(textRenderer, chip, px + pw - 8 - cw, py + 10, UI.WHITE, false);
        int y = py + 26;
        for (Block b : top) { b.d.at(ctx, ix, y); y += b.h; }
        if (state != State.DONE) y += FIELD_H + 8;
        for (Block b : bottom) { b.d.at(ctx, ix, y); y += b.h; }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (state == State.DONE) next(); else check();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
