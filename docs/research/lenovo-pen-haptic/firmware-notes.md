# PARKER.bin Firmware Analysis Notes

Preliminary IDA findings for the pen firmware (`firmware/PARKER.bin`, v1.74, Cortex-M,
303,576 bytes). IDB: `PARKER.bin.i64` (base 0x0, code segment 0x27000-0x711D8, SRAM
0x20000000-0x2000D000, PPB 0xE0000000-0xE0100000). 1855 functions, hex-rays works.

Note: the `?? % 1` bytes under `PPB:E00EC000` are normal — PPB is the Cortex-M private
peripheral bus (NVIC/SCB/SysTick MMIO), not code. Ignore that segment.

## Descriptor table: SRAM g_haptic[19] @ 0x20005268

Global array of 19 slots x 92 bytes (0x5C), filled at init by `sub_2B33C`
(registered via `sub_38C2C(id, blobPtr, blobLen)`, 18 registrations + STOP).

- Registration parser `sub_38C2C` verifies the blob starts with magic
  **`LenovoHapticFile`** (16 bytes), then parses a header (`sub_377C6` varint reads:
  version, offsets to sections) and copies per-slot metadata into the 92-byte record.
- Slot selection at runtime: global `g_haptic_id` **0x20005940** (the hapticId byte from
  the BLE command) indexes `&g_haptic[23*id]` (23 dwords = 92 bytes).
- `sub_2B564(id)` validates: id >= 19 -> err 7; slot first byte 0 -> err 5; else ok.
  So firmware-side id range is 0..18 (7 impact + 10 continuous + stop + spare).
- The blob payloads themselves live in flash (0x49888..0x65xxx region referenced in
  `sub_2B33C`, sizes ~1.4KB..21KB each): pressure/velocity curves and waveforms.

## Level handling — the answer to "can we go past level 5"

**Firmware clamps level to 5.** `sub_36B24(id, level, friction)`:

```
n5 = level; if (level > 5) n5 = 5;      // 0x36B2E saturation
...
sub_2B54C( *(u8*)0x49304 + n5 );        // strength lookup, sub_2B54C additionally
                                        // rejects > 15 with err 7 (0x2B54C)
```

Strength byte table `0x49304` (indexed 0..5; a secondary index base at `0x4930A`):

| level | 0 | 1 | 2 | 3 | 4 | 5 |
|-------|---|---|---|---|---|---|
| byte  | 0 | 7 | 0xB | 0xF | 0xF | 0xF |

i.e. firmware saturation already flattens levels 3-5 (0xF = max drive 15). The same
table is used by the impact path `sub_37168` (`n15 = 0x49304[level]`, passed through
`sub_2B54C` as the drive value to `sub_2B8B4` -> actuator control) and by the state
machine `sub_374A8` (deferred re-trigger).

Additional level consumers (all read `g_haptic[id]` fields, none re-clamp):

- `sub_2B19C` — effective amplitude: per-slot gain table `u16[1]`/`u16[2]` scaled by
  global `g_level` **0x20005941**; branch on mode byte **0x20005943**:
  0 -> `level >> 1`; 1 -> `n256 * (level*gain/100 + level*press/1500) / 512`;
  2 -> `16 * press% / 100`.
- `sub_45A1C` — pressure->strength percent (slot offset 60: gain + bias), clamps 0..100.
- `sub_45ABC` — sample-rate computation from slot fields, cap 88200 Hz.
- `sub_45C50` — angular direction modulation (slot offset 52), 256=neutral.
- `sub_34F40` / `sub_34FE0` — stroke-down detection with per-slot velocity thresholds
  (slot words 3..7), used by the continuous (writing) haptic.

`sub_2B54C(levelByte)` is the final actuator gate: >15 -> err 7.

## Implication for the strength-limit goal

The clamp is **threefold and redundant**: penservice UI (1..5) -> services.jar check
(0..5) -> firmware saturation (`>5 -> 5`, table max 0xF, actuator gate >15 -> err).
Sending level 6+ from the tablet would land on firmware level 5 — **identical
vibration**. Hooking `ZuiPenHapticUtils` alone therefore cannot raise strength; the only
effective levers would be firmware patching (extend `0x49304` table / remove the 0x36B30
clamp) or the DFU transport, both out of LSPosed reach. A tablet-side experiment is
still possible via a patched payload in the DFU flow, but the flash layer (see below)
makes casual patching risky.

## Firmware update / self-healing (preliminary reconnaissance)

- Boot log strings (`Gp:...`): "Pwr on err!", "Rst succ!", "Otp crc err!", "Otp succ!",
  "Pll start err!", "PLL succ!", "Sys clk err!" — the ROM/boot stage validates OTP CRC
  and PLL/clock bring-up, implying OTP-stored config drives early boot; "Otp crc err!"
  has a paired success path (self-healing retry), decompile TBD.
- "CRC success" at 0x2A5C4 — image/CRC check on boot.
- Nordic SoftDevice S140 + peer manager strings (`PM_EVT_PEER_DATA_UPDATE_*`) confirm
  Nordic DFU transport; the app side uses `no.nordicsemi.android.dfu`.
- No explicit anti-rollback strings in the application image; if rollback protection
  exists it is likely in the boot/OTP layer (offsets below 0x27000, outside the app
  segment). Firmware ZIP carries a DFU init packet (`PARKER.dat`) — typically holds
  CRC + device/ver fields; deeper check TBD.

## Open items

- [ ] Decompile `Gp:` boot chain: OTP CRC error handling, PLL retry, clock fallback.
- [ ] DFU receipt handling in-firmware (version compare? disable-other? bond sharing).
- [ ] Full waveform blob parsing (the 18 flash blobs, varint header of `sub_377C6`).
- [ ] `sub_2B8B4` actuator driver: LRA vs ERM, drive register, closed-loop sensing.
