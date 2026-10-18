# Lenovo Pen Haptic Strength Research

Research on the ZUI stylus-pen haptic feedback chain. `haptic-chain.md` follows the
vibration-strength limit and what would be needed to lift it; `waveform-kinds.md`
follows which waveform the default writing feedback plays and how to select it. This is
analysis material only — nothing here is wired into the ZTool hook modules yet.

## Directory contents

```text
docs/research/lenovo-pen-haptic/
  README.md            This file
  haptic-chain.md      Level setting chain: UI -> Settings -> system service -> BLE -> firmware
  waveform-kinds.md    Waveform kind chain: pen_haptic_brush -> default feedback id (32..41)
  firmware-ota.md      Reproducible pipeline to fetch pen firmware from ota.lenovo.com
  firmware/            Downloaded OTA package contents (version 1.74)
    manifest.json      Nordic DFU package manifest
    PARKER.bin         ARM Cortex-M firmware image (303,576 bytes)
    PARKER.dat         Nordic DFU init packet (141 bytes)
```

## Status

- [x] App-side scope of `com.lenovo.penservice` mapped
- [x] Haptic level chain traced end-to-end on the tablet side (app + services.jar)
- [x] Default waveform kind chain traced end-to-end (`pen_haptic_brush`, live-confirmed)
- [x] Firmware download pipeline reproduced locally (SHA256 verified)
- [ ] IDA analysis of `PARKER.bin`: level -> waveform table, saturation behavior above level 5
- [ ] Firmware id -> waveform slot mapping for the 32..41 continuous ids
- [ ] Prototype hook decision (services.jar parameter check vs. firmware-only outcome)
