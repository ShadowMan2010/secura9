// SECURA-9 Enclosure — Parametric N150 Mini PC Case
// With 5" HDMI display mount, USB camera, relay space
//
// All dimensions in mm. Tweak the USER PARAMETERS section
// to match your exact N150 size, display bezel, etc.

// ── USER PARAMETERS ───────────────────────────────────────────────────────

// N150 board inner cavity (add 5-10mm clearance over raw board)
BOARD_W     = 126;  // Beelink Mini S13 / Minisforum UN100D
BOARD_D     = 126;  // (adjust for your exact model)
BOARD_H     = 48;   // board height (incl. cooling)

// Wall thickness
WALL        = 2.4;  // 3 perimeters at 0.4mm nozzle

// 5" HDMI display
DISPLAY_W   = 118;  // total bezel width
DISPLAY_H   = 78;   // total bezel height
DISPLAY_DEPTH = 12; // how far the display + driver protrudes inward

// Camera hole (bullet-style USB camera, e.g. ELP)
CAM_DIAM    = 14;   // hole diameter for camera barrel
CAM_OFFSET_X = 0;   // horizontal offset from center (0 = center)
CAM_OFFSET_Y = -45; // vertical offset from display center (negative = above)

// Relay board compartment (door strike control)
RELAY_W     = 55;
RELAY_D     = 35;
RELAY_H     = 22;

// Wall-mount holes
MTG_DIAM    = 5;    // screw diameter
MTG_SPACING = 90;   // distance between mounting holes

// Ventilation
VENT_COUNT  = 8;    // slots per side
VENT_W      = 3;    // slot width

// Standoffs (height from back wall to board)
STANDOFF_H  = 6;
STANDOFF_D  = 6;    // outer diameter

// ── DERIVED ───────────────────────────────────────────────────────────────

INNER_W  = BOARD_W + 6;
INNER_D  = BOARD_D + 6;
INNER_H  = BOARD_H + 6;

OUTER_W  = INNER_W + WALL * 2;
OUTER_D  = INNER_D + WALL * 2;
OUTER_H  = INNER_H + WALL;

// Front bezel thickness
BEZEL    = 3;

$fn = 45;

// ── MODULES ───────────────────────────────────────────────────────────────

module rounded_rect(w, d, h, r=3) {
    hull() {
        for (x = [-1, 1]) for (y = [-1, 1])
            translate([x * (w/2 - r), y * (d/2 - r), 0])
                cylinder(h, r=r);
    }
}

module vent_slot(len) {
    cube([VENT_W, len, WALL], center=true);
}

// ── BACK BOX ──────────────────────────────────────────────────────────────

module back_box() {
    difference() {
        // Outer shell
        rounded_rect(OUTER_W, OUTER_D, OUTER_H, 4);

        // Inner cavity
        translate([0, 0, WALL])
            rounded_rect(INNER_W, INNER_D, OUTER_H, 3);

        // Bottom cable passthrough (wide slot)
        translate([0, 0, -1])
            cube([30, INNER_D - 20, WALL + 2], center=true);

        // Wall mount holes
        for (x = [-1, 1]) for (z = [-1, 1])
            translate([x * MTG_SPACING / 2, -INNER_D / 2, OUTER_H / 2 + z * MTG_SPACING / 2])
                rotate([90, 0, 0])
                    cylinder(h = WALL + 4, d = MTG_DIAM, center=true);

        // Vent slots - top
        for (i = [0:VENT_COUNT - 1])
            translate([-INNER_W/2 + INNER_W/(VENT_COUNT+1) * (i+1), 0, OUTER_H - WALL/2])
                vent_slot(INNER_D * 0.7);

        // Vent slots - sides
        for (i = [0:VENT_COUNT/2 - 1])
            translate([-OUTER_W/2 + WALL/2, -INNER_D/2 + INNER_D/(VENT_COUNT/2+1) * (i+1), INNER_H/2])
                rotate([0, 0, 90])
                    vent_slot(INNER_W * 0.4);
        for (i = [0:VENT_COUNT/2 - 1])
            translate([OUTER_W/2 - WALL/2, -INNER_D/2 + INNER_D/(VENT_COUNT/2+1) * (i+1), INNER_H/2])
                rotate([0, 0, 90])
                    vent_slot(INNER_W * 0.4);
    }

    // Internal standoffs for N150 motherboard
    for (x = [-1, 1]) for (y = [-1, 1])
        translate([x * BOARD_W/2 - x * 10, y * BOARD_D/2 - y * 10, WALL])
            difference() {
                cylinder(h = STANDOFF_H, d = STANDOFF_D);
                cylinder(h = STANDOFF_H + 1, d = 2.8); // M2.5 screw hole
            }

    // Relay compartment wall
    translate([INNER_W/2 - RELAY_W / 2 - WALL, -INNER_D / 2 + RELAY_D / 2 + 5, WALL])
        difference() {
            cube([RELAY_W + 2, RELAY_D + 2, RELAY_H + WALL]);
            translate([1, 1, WALL])
                cube([RELAY_W, RELAY_D, RELAY_H + 1]);
        }
}

// ── FRONT BEZEL ───────────────────────────────────────────────────────────

module front_bezel() {
    difference() {
        rounded_rect(OUTER_W, OUTER_D, BEZEL, 4);

        // Display cutout
        translate([0, 0, -1])
            rounded_rect(DISPLAY_W, DISPLAY_H, BEZEL + 2, 3);

        // Camera hole
        translate([CAM_OFFSET_X, CAM_OFFSET_Y, -1])
            cylinder(h = BEZEL + 2, d = CAM_DIAM);

        // Screw holes (M3)
        for (x = [-1, 1]) for (y = [-1, 1])
            translate([x * INNER_W/2 - x * 8, y * INNER_D/2 - y * 8, -1])
                cylinder(h = BEZEL + 2, d = 3.2);

        // USB bulkhead passthrough (bottom edge)
        translate([0, -INNER_D/2 + 6, -1])
            cube([20, 10, BEZEL + 2], center=true);

        // Barrel jack passthrough
        translate([-OUTER_W/2 + 12, -INNER_D/2 + 6, -1])
            cylinder(h = BEZEL + 2, d = 8);
    }
}

// ── RENDER ────────────────────────────────────────────────────────────────

// Render both parts
translate([0, 0, OUTER_H - BEZEL])
    front_bezel();

back_box();
