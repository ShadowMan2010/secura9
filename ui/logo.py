"""
SECURA-9 Logo — renders a cyberpunk security badge using pygame primitives.
Call draw_logo(surface, cx, cy, size, alpha, pulse) to draw it.
"""
import math
import os
import pygame
import pygame.gfxdraw

COL_BG       = (10,   2,  16)
COL_CYAN     = (0,  255, 255)
COL_GREEN    = (57, 255,  20)
COL_RED      = (255,   0, 127)
COL_PURPLE   = (188,  19, 254)
COL_TEXT     = (200, 230, 255)
COL_MUTED    = (90,   80, 120)


def _aa_circle(scr, cx, cy, r, col, width=0):
    """Anti-aliased circle using gfxdraw."""
    if width == 0:
        pygame.gfxdraw.filled_circle(scr, int(cx), int(cy), int(r), col)
        pygame.gfxdraw.aacircle(scr, int(cx), int(cy), int(r), col)
    else:
        pygame.gfxdraw.aacircle(scr, int(cx), int(cy), int(r), col)
        if width == 1:
            pass
        else:
            for w in range(1, min(width, r)):
                a = max(0, col[3] - w * 20) if len(col) == 4 else col
                c = (*col[:3], a) if len(col) == 4 else col
                pygame.gfxdraw.aacircle(scr, int(cx), int(cy), int(r - w), c)


def _hexagon(cx, cy, r):
    """Return points of a regular hexagon centered at (cx, cy) with radius r."""
    pts = []
    for i in range(6):
        a = math.radians(60 * i - 90)
        pts.append((int(cx + r * math.cos(a)), int(cy + r * math.sin(a))))
    return pts


def draw_shield_logo(scr, cx, cy, size=1.0, alpha=255, time=0.0):
    """
    Draw the SECURA-9 shield logo at (cx, cy) scaled by `size`.
    Returns a rect of the drawn area.
    """
    s = size
    t = time

    # ── Outer hexagon shield ──────────────────────────────────────────────
    r_base = int(100 * s)
    hex_pts = _hexagon(cx, cy, r_base)

    # Glow behind shield
    for g in range(3, 0, -1):
        glow_r = r_base + g * 6
        glow_pts = _hexagon(cx, cy, glow_r)
        glow_a = int(15 * alpha / 255)
        pygame.draw.polygon(scr, (*COL_PURPLE, glow_a), glow_pts, 2)

    # Shield fill
    shield_fill = pygame.Surface((scr.get_width(), scr.get_height()), pygame.SRCALPHA)
    pygame.draw.polygon(shield_fill, (*COL_BG, int(200 * alpha / 255)), hex_pts)
    pygame.draw.polygon(shield_fill, (*COL_CYAN, int(180 * alpha / 255)), hex_pts, 2)
    scr.blit(shield_fill, (0, 0))

    # Inner hexagon ring
    inner_r = int(82 * s)
    inner_pts = _hexagon(cx, cy, inner_r)
    pulse_a = int(80 + 40 * math.sin(t * 2))
    pygame.draw.polygon(scr, (*COL_PURPLE, int(pulse_a * alpha / 255)), inner_pts, 1)

    # ── Corner decorative elements ─────────────────────────────────────────
    for angle_deg in [0, 60, 120, 180, 240, 300]:
        a = math.radians(angle_deg - 90)
        dot_x = int(cx + (r_base - 12) * s * math.cos(a))
        dot_y = int(cy + (r_base - 12) * s * math.sin(a))
        dot_r = max(1, int(2 * s))
        _aa_circle(scr, dot_x, dot_y, dot_r, (*COL_CYAN, int(200 * alpha / 255)))

    # ── Circuit lines ─────────────────────────────────────────────────────
    circuit_pts = [
        (cx - int(30 * s), cy - int(20 * s)),
        (cx - int(30 * s), cy + int(20 * s)),
        (cx - int(50 * s), cy + int(40 * s)),
        (cx + int(50 * s), cy + int(40 * s)),
        (cx + int(30 * s), cy + int(20 * s)),
        (cx + int(30 * s), cy - int(20 * s)),
    ]
    for i in range(len(circuit_pts) - 1):
        x1, y1 = circuit_pts[i]
        x2, y2 = circuit_pts[i + 1]
        ca = int(80 + 60 * math.sin(t * 1.5 + i))
        pygame.draw.line(scr, (*COL_CYAN, int(ca * alpha / 255)), (x1, y1), (x2, y2), 1)

    # Small dots at circuit junctions
    for px, py in circuit_pts:
        _aa_circle(scr, px, py, max(1, int(2 * s)), (*COL_GREEN, int(180 * alpha / 255)))

    # ── "S9" central text ─────────────────────────────────────────────────
    try:
        font_s9 = pygame.font.Font(
            '/usr/share/fonts/truetype/orbitron/Orbitron-Bold.ttf',
            int(48 * s),
        )
    except Exception:
        font_s9 = pygame.font.SysFont('monospace', int(48 * s), bold=True)

    s9_text = font_s9.render('S9', True, COL_CYAN)
    s9_text.set_alpha(int(255 * alpha / 255))
    s9_x = cx - s9_text.get_width() // 2
    s9_y = cy - s9_text.get_height() // 2 - int(5 * s)
    scr.blit(s9_text, (s9_x, s9_y))

    # S9 glow
    for i in range(3):
        glow_s = pygame.Surface(
            (s9_text.get_width() + 20 + i * 10, s9_text.get_height() + 10 + i * 6),
            pygame.SRCALPHA,
        )
        ga = int(20 * (1 - i * 0.25) * alpha / 255)
        pygame.draw.rect(
            glow_s, (*COL_CYAN, ga), glow_s.get_rect(), int(2 - i), 4,
        )
        scr.blit(glow_s, (s9_x - 10 - i * 5, s9_y - 5 - i * 3))

    return pygame.Rect(cx - r_base, cy - r_base, r_base * 2, r_base * 2)
