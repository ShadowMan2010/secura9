# SECURA-9 Enclosure

Parametric OpenSCAD enclosure for N150 mini PC with 5" display, USB camera, and
door strike relay. Designed for FDM printing (0.4mm nozzle, PLA/PETG).

## Parts

| Part | Example | Price |
|------|---------|-------|
| N150 mini PC | Mele Quieter 4C / Beelink Mini S12 / Minisforum UN100D | $110-160 |
| 5" HDMI display | Waveshare 5" IPS HDMI (800×480) | $45 |
| USB camera | ELP 2MP USB bullet with IR LEDs | $25 |
| USB relay | HID USB relay (Digital Loggers) | $15 |
| 12V PSU | for door strike (if not using battery) | $10 |
| **Total** | | **~$200-230** |

## Printing

Open `enclosure.scad` in OpenSCAD, tweak `BOARD_W` / `BOARD_D` / `BOARD_H`
to match your exact N150 model, then export STLs for:

1. `back_box()` — main body with wall-mount holes, vents, standoffs, relay compartment
2. `front_bezel()` — faceplate with display cutout, camera hole, power/USB passthroughs

**Settings**: 0.2mm layer height, 3 perimeters, 15% infill, no supports needed.

## Assembly

1. Mount N150 onto standoffs with M2.5 screws
2. Mount relay board in side compartment
3. Install 5" display into bezel cutout, secure from behind
4. Run USB camera cable through barrel hole + USB bulkhead
5. Close front bezel onto back box, secure with M3 screws (6-8mm length)
6. Mount to wall via keyhole slots in back

## Wiring

```
N150 USB port ── USB relay ── door strike (12V)
N150 USB port ── camera
N150 HDMI ────── 5" display
N150 USB or DC ── barrel jack passthrough
```

## Customization

Edit the USER PARAMETERS section in `enclosure.scad`:

- **BOARD_W/D/H** — your N150's outer dimensions (measure or check specs)
- **DISPLAY_W/H** — your display's total bezel size
- **CAM_DIAM** — your camera barrel diameter
- **CAM_OFFSET_Y** — negative = above display, positive = below
- **WALL** — increase to 3mm for PETG, keep at 2.4mm for PLA
