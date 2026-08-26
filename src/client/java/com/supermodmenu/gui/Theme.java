package com.supermodmenu.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

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
    public static final int PAD       = 12;
    public static final int GAP       = 6;
    public static final int RADIUS    = 1;   // visual hint only (flat UI)
    public static final int BTN_H     = 20;

    // ── Surface colours (ARGB) ──────────────────────────────────────────────────
    public static final int BG_APP      = 0xFA080D13;  // deep navy app backdrop
    public static final int BG_HEADER   = 0xFF0D141D;  // top navigation surface
    public static final int BG_SIDEBAR  = 0xFF101822;  // library rail
    public static final int BG_PANEL    = 0xFF121B26;  // panels / footer
    public static final int BG_CARD     = 0xFF17222E;  // list cards
    public static final int BG_CARD_HOV = 0xFF1D2D3B;  // hover
    public static final int BG_SELECTED = 0xFF17372F;  // emerald-tinted selection
    public static final int BG_ELEVATED = 0xFF1C2936;  // inputs, chips
    public static final int BG_SCRIM    = 0xC0000000;  // modal dim
    public static final int SHADOW       = 0x66000000;

    // ── Lines ────────────────────────────────────────────────────────────────--
    public static final int BORDER       = 0xFF263543;
    public static final int BORDER_LIGHT = 0xFF3A4D5D;
    public static final int DIVIDER      = 0x335B7183;

    // ── Brand / accents ─────────────────────────────────────────────────────────
    public static final int ACCENT       = 0xFF34D399;  // emerald primary
    public static final int ACCENT_HOVER = 0xFF6EE7B7;
    public static final int ACCENT_DIM   = 0x4934D399;
    public static final int GREEN        = ACCENT;      // success / install
    public static final int GREEN_DIM    = ACCENT_DIM;
    public static final int BLUE         = 0xFF60A5FA;  // updates / info
    public static final int BLUE_DIM     = 0x3D60A5FA;
    public static final int RED          = 0xFFF87171;  // danger / disabled
    public static final int RED_DIM      = 0x3DF87171;
    public static final int GOLD         = 0xFFFBBF24;  // favourite star
    public static final int GOLD_DIM     = 0x3DFBBF24;

    // ── Text ─────────────────────────────────────────────────────────────────--
    public static final int TEXT         = 0xFFF4F7FA;
    public static final int TEXT_MUTED   = 0xFFB6C2CE;
    public static final int TEXT_DIM     = 0xFF718294;

    // ── Primitives ───────────────────────────────────────────────────────────--

    /** Filled panel with a subtle outline. */
    public static void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        panel(ctx, x, y, w, h, BG_PANEL, BORDER);
    }

    public static void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.outline(x, y, w, h, border);
    }

    /** Elevated panel with a narrow semantic accent along its top edge. */
    public static void accentedPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int accent) {
        panel(ctx, x, y, w, h, BG_PANEL, BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 3, accent);
    }

    /** Quiet uppercase label used to establish hierarchy between screen regions. */
    public static void sectionLabel(GuiGraphicsExtractor ctx, Font tr, String label, int x, int y, int color) {
        ctx.text(tr, Component.literal(label.toUpperCase()), x, y, color, true);
    }

    /** A coloured "chip"/badge with centred label. */
    public static void chip(GuiGraphicsExtractor ctx, Font tr, String label,
                            int x, int y, int w, int h, int bg, int fg) {
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.centeredText(tr, Component.literal(label),
            x + w / 2, y + (h - tr.lineHeight) / 2 + 1, fg);
    }

    /** Full-width status banner (e.g. "update available"). */
    public static void banner(GuiGraphicsExtractor ctx, Font tr, String label,
                              int x, int y, int w, int accent, int fillTint) {
        int h = 18;
        ctx.fill(x, y, x + w, y + h, fillTint);
        ctx.fill(x, y, x + 2, y + h, accent);          // left accent bar
        ctx.text(tr, Component.literal(label), x + 8, y + (h - tr.lineHeight) / 2 + 1, TEXT, true);
    }

    public static void divider(GuiGraphicsExtractor ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + 1, DIVIDER);
    }

    /** Vertical accent bar used to mark the selected list entry. */
    public static void accentBar(GuiGraphicsExtractor ctx, int x, int y, int h, int color) {
        ctx.fill(x, y, x + 3, y + h, color);
    }

    /** Small environment pill (e.g. "Client"/"Server"), Mod Menu style. Returns its width. */
    public static int envBadge(GuiGraphicsExtractor ctx, Font tr, String label, int x, int y, int bg) {
        int w = tr.width(label) + 8;
        ctx.fill(x, y, x + w, y + 13, bg);
        ctx.text(tr, Component.literal(label), x + 4, y + 3, 0xFFEAF1FF, true);
        return w;
    }

    /**
     * A classic 8-dot rotating spinner centred on (cx, cy). Self-animating from the
     * system clock, so it just needs to be drawn every frame (which screens already do).
     */
    public static void spinner(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int baseColor) {
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
    public static String clip(Font tr, String s, int maxW) {
        if (s == null) return "";
        if (tr.width(s) <= maxW) return s;
        while (!s.isEmpty() && tr.width(s + "…") > maxW) {
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
