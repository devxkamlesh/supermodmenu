package com.supermodmenu.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * Centralised design tokens and small reusable draw helpers for Super Mod Menu.
 *
 * Keeping every colour, spacing value and primitive shape in one place is the
 * backbone of the new UI architecture: screens describe <i>layout</i>, the Theme
 * owns <i>appearance</i>. Tweaking the look of the whole mod menu now means
 * editing this single file.
 */
public final class Theme {

    private Theme() {}

    // ── Spacing ────────────────────────────────────────────────────────────────
    public static final int PAD       = 10;
    public static final int GAP       = 6;
    public static final int RADIUS    = 1;   // visual hint only (flat UI)
    public static final int BTN_H     = 20;

    // ── Surface colours (ARGB) ──────────────────────────────────────────────────
    public static final int BG_APP      = 0xF00E0E14;  // app backdrop
    public static final int BG_PANEL    = 0xFF15151E;  // panels / sidebar
    public static final int BG_CARD     = 0xFF1C1C28;  // list cards
    public static final int BG_CARD_HOV = 0xFF262634;  // hover
    public static final int BG_ELEVATED = 0xFF222230;  // inputs, chips
    public static final int BG_SCRIM    = 0xC0000000;  // modal dim

    // ── Lines ────────────────────────────────────────────────────────────────--
    public static final int BORDER       = 0xFF2A2A3A;
    public static final int BORDER_LIGHT = 0xFF3A3A4E;
    public static final int DIVIDER      = 0x22FFFFFF;

    // ── Brand / accents ─────────────────────────────────────────────────────────
    public static final int ACCENT       = 0xFFFF9F1C;  // primary (amber)
    public static final int ACCENT_DIM   = 0x55FF9F1C;
    public static final int GREEN        = 0xFF22C55E;  // get mods / success
    public static final int GREEN_DIM    = 0x5522C55E;
    public static final int BLUE         = 0xFF3B82F6;  // updates / info
    public static final int BLUE_DIM     = 0x553B82F6;
    public static final int RED          = 0xFFEF4444;  // danger / disabled
    public static final int RED_DIM      = 0x55EF4444;
    public static final int GOLD         = 0xFFFFD54A;  // favourite star

    // ── Text ─────────────────────────────────────────────────────────────────--
    public static final int TEXT         = 0xFFF2F2F7;
    public static final int TEXT_MUTED   = 0xFFA8A8B8;
    public static final int TEXT_DIM     = 0xFF6A6A78;

    // ── Primitives ───────────────────────────────────────────────────────────--

    /** Filled panel with a subtle outline. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        panel(ctx, x, y, w, h, BG_PANEL, BORDER);
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.drawBorder(x, y, w, h, border);
    }

    /** A coloured "chip"/badge with centred label. */
    public static void chip(DrawContext ctx, TextRenderer tr, String label,
                            int x, int y, int w, int h, int bg, int fg) {
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(label),
                x + w / 2, y + (h - tr.fontHeight) / 2 + 1, fg);
    }

    /** Full-width status banner (e.g. "update available"). */
    public static void banner(DrawContext ctx, TextRenderer tr, String label,
                              int x, int y, int w, int accent, int fillTint) {
        int h = 18;
        ctx.fill(x, y, x + w, y + h, fillTint);
        ctx.fill(x, y, x + 2, y + h, accent);          // left accent bar
        ctx.drawTextWithShadow(tr, Text.literal(label), x + 8, y + (h - tr.fontHeight) / 2 + 1, TEXT);
    }

    public static void divider(DrawContext ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + 1, DIVIDER);
    }

    /** Vertical accent bar used to mark the selected list entry. */
    public static void accentBar(DrawContext ctx, int x, int y, int h, int color) {
        ctx.fill(x, y, x + 3, y + h, color);
    }

    /** Small environment pill (e.g. "Client"/"Server"), Mod Menu style. Returns its width. */
    public static int envBadge(DrawContext ctx, TextRenderer tr, String label, int x, int y, int bg) {
        int w = tr.getWidth(label) + 8;
        ctx.fill(x, y, x + w, y + 13, bg);
        ctx.drawTextWithShadow(tr, Text.literal(label), x + 4, y + 3, 0xFFEAF1FF);
        return w;
    }

    /**
     * A classic 8-dot rotating spinner centred on (cx, cy). Self-animating from the
     * system clock, so it just needs to be drawn every frame (which screens already do).
     */
    public static void spinner(DrawContext ctx, int cx, int cy, int radius, int baseColor) {
        final int dots = 8;
        int head = (int) ((System.currentTimeMillis() / 90) % dots);
        for (int i = 0; i < dots; i++) {
            double ang = (Math.PI * 2 * i) / dots - Math.PI / 2;
            int dx = cx + (int) Math.round(Math.cos(ang) * radius);
            int dy = cy + (int) Math.round(Math.sin(ang) * radius);
            int trail = (head - i + dots) % dots;        // 0 = leading dot
            int alpha = Math.max(0x30, 0xFF - trail * 0x24);
            int color = (alpha << 24) | (baseColor & 0xFFFFFF);
            ctx.fill(dx - 2, dy - 2, dx + 2, dy + 2, color);
        }
    }

    /** Animated "…" (1–3 dots) for in-progress button labels. */
    public static String workingDots() {
        return ".".repeat(1 + (int) ((System.currentTimeMillis() / 350) % 3));
    }

    /** Clip a string with an ellipsis so it fits within {@code maxW} pixels. */
    public static String clip(TextRenderer tr, String s, int maxW) {
        if (s == null) return "";
        if (tr.getWidth(s) <= maxW) return s;
        while (!s.isEmpty() && tr.getWidth(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    /** Strip the build metadata ("+1.21.4" etc.) from a version string. */
    public static String shortVersion(String v) {
        int plus = v.indexOf('+');
        return plus > 0 ? v.substring(0, plus) : v;
    }
}
