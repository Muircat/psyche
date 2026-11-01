package dev.psyche.client;

import dev.psyche.core.Content;
import dev.psyche.core.Profile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The notebook: mastery per course topic, recent mistakes to revisit, and how often questions come. */
final class NotebookScreen extends Screen {
    private static final int[] COOLDOWNS = {30, 60, 120, 300};
    private final List<Content.Topic> topics = new ArrayList<>(PsycheClient.CONTENT.topics.values());
    private int px, py, pw, ph;

    NotebookScreen() { super(Text.literal("Revision notebook")); }

    private Profile p() { return PsycheClient.profile; }

    @Override
    protected void init() {
        pw = Math.min(460, width - 24);
        ph = Math.min(40 + topics.size() * 12 + 38, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int bw = (pw - 24 - 8) / 3, by = py + ph - 28, x = px + 12;
        addDrawableChild(ButtonWidget.builder(Text.literal("A question every " + p().cooldown + " s"), b -> {
            int i = 0;
            while (i < COOLDOWNS.length && COOLDOWNS[i] <= p().cooldown) i++;
            p().cooldown = COOLDOWNS[i % COOLDOWNS.length]; p().save(); clearAndInit();
        }).dimensions(x, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Revise"), b -> PsycheClient.openNext(client)).dimensions(x + bw + 4, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close()).dimensions(x + 2 * (bw + 4), by, bw, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        UI.panel(ctx, px, py, pw, ph);
    }

    private int split() { return pw >= 380 ? pw * 11 / 20 : pw - 24; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int x = px + 12, split = split();
        ctx.drawText(textRenderer, title, x, py + 10, UI.WHITE, false);
        String stats = Profile.levelName(p().level()) + " · " + p().solved + " solved · streak " + p().streak + " · best " + p().best;
        ctx.drawText(textRenderer, stats, px + pw - 12 - textRenderer.getWidth(stats), py + 10, UI.MUTED, false);
        ctx.drawText(textRenderer, "Topics: click one to practise it", x, py + 26, UI.MUTED, false);

        Content.Topic hover = null;
        for (int i = 0; i < topics.size(); i++) {
            Content.Topic t = topics.get(i);
            int y = py + 42 + i * 12, s = p().score(t.id());
            if (y > py + ph - 44) break;
            int[] row = {x - 4, y - 2, split - 4, 12};
            if (UI.in(row, mx, my)) { UI.round(ctx, row[0], row[1], row[2], row[3], UI.TILE); hover = t; }
            ctx.drawText(textRenderer, textRenderer.trimToWidth(t.name(), split - 136), x, y, UI.WHITE, false);
            int bx = x + split - 128;
            for (int k = 0; k < 5; k++) ctx.fill(bx + k * 12, y + 1, bx + k * 12 + 10, y + 7, s >= (k + 1) * 2 ? UI.VIOLET : s > k * 2 ? UI.VIOLET_DARK : UI.INSET);
            ctx.drawText(textRenderer, Profile.Mode.of(s).label, bx + 64, y, UI.MUTED, false);
        }

        if (split < pw - 24) {
            int rx = px + split + 16, rw = pw - split - 28, y = py + 26;
            ctx.drawText(textRenderer, "To revisit", rx, y, UI.MUTED, false);
            if (p().mistakes.isEmpty()) ctx.drawText(textRenderer, "Nothing yet. Nice work!", rx, y + 14, UI.GREEN, false);
            for (Profile.Mistake m : p().mistakes) {
                Content.Challenge c = PsycheClient.CONTENT.byId(m.id);
                if (c == null) continue;
                if ((y += 12) > py + ph - 62) break;
                ctx.drawText(textRenderer, textRenderer.trimToWidth("✗ " + (m.given == null || m.given.isBlank() ? "(no answer)" : m.given), rw), rx, y, UI.RED, false);
                ctx.drawText(textRenderer, textRenderer.trimToWidth("✓ " + c.answer, rw), rx, y += 10, UI.GREEN, false);
                if (m.why != null && y + 22 <= py + ph - 62)
                    ctx.drawText(textRenderer, textRenderer.trimToWidth("⚠ " + m.why, rw), rx, y += 10, UI.AMBER, false);
            }
        }
        if (hover != null) {
            String tip = hover.rule() + (hover.src().isEmpty() ? "" : "\n(" + hover.src() + ")");
            List<net.minecraft.text.OrderedText> lines = new ArrayList<>();
            for (String part : tip.split("\n")) lines.addAll(textRenderer.wrapLines(Text.literal(part), 230));
            ctx.drawOrderedTooltip(textRenderer, lines, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int split = split();
        for (int i = 0; i < topics.size(); i++) {
            int y = py + 42 + i * 12;
            if (y <= py + ph - 44 && UI.in(new int[]{px + 8, y - 2, split - 4, 12}, mx, my)) {
                Content.Challenge c = PsycheClient.CONTENT.pick(null, topics.get(i).id(), p(), PsycheClient.RND);
                if (c != null) client.setScreen(new ChallengeScreen(c, "review"));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
