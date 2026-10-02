# Minecraft 1.21.1 NeoForge extender binding acceptance

The tested runtime source is `419c7de4d85f80906f08f882a941378974ebe337`, a compatibility
backport of upstream `26.3-neoforge` (`b263e409e3e3ec43870add7e195b31892082db11`).
The one-file binding fix applies to the byte-identical `setScreen` method on both
branches. This evidence does **not** claim a 26.3 runtime/build test or a complete
Minecraft version port in the proposed upstream PR.

Runtime: Minecraft 1.21.1, NeoForge 21.1.209, Java 21, Gradle 9.2.1; Windows.
Production jar SHA-256:
`9f1e9f06f0d1ba97e2d077793a6d90cc16a5d7bd069e84f1cfb579ad46e88676`.
The production jar contains no QA classes or temporary binding trace code.
Build log records `BUILD SUCCESSFUL`; built-in `test` is `NO-SOURCE`.
Real game tests use ModDevGradle production output plus a separate self-authored
QA mod. The QA mod is not a production dependency.

## Failure and cause

On reopening a world, the core has a two-block screen but its extender renders
a standalone border. The unfixed test logs core texture 14 / extender texture 15,
and client `partOfScreen=false` despite server `partOfScreen=true`.
The diagnostic trace shows `setScreen(validScreen)` called from the core update
tag while `LevelChunk.replaceWithPacketData` is still loading the chunk.
At that point the core block state is not yet visible through the level. The
nested block-state check fails, and the old method falls through to clearing the
membership flag and core coordinates. The next extender tick detaches it.

Move the existing return outside that optional block-state check. Membership is
kept when a valid core is known, while the light-state update remains conditional
on a visible compatible block. Normal `setScreen(null)` behavior is unchanged.
No extra packets, client retries, renderer changes or test-only protection are
part of the final fix. Two attempted wider fixes and a diagnostic build failed;
they were removed and are not claimed as passed results.

## Actual results

| Run | Purpose | Client / server assertions | Exit codes | Result |
|---|---|---|---|---|
| 20261002-155451-eb9f30 | Unfixed 1.21.1 saved-world visual check | Render-membership assertion fails | 0 / 0 | FAIL |
| 20261002-160433-386b63 | Temporary diagnostic tracing | Same assertion fails; call path recorded | 0 / 0 | FAIL |
| 20261002-160643-b84684 | Final fix opens the same older world | 10 / 34 | 0 / 0 | PASS |
| 20261002-160813-ca1106 | Final fix, fresh world and full interaction | 30 / 38 | 0 / 0 | PASS |
| 20261002-161048-4758af | New processes reopen the previous saved world | 10 / 34 | 0 / 0 | PASS |

The three final runs contain 156 successful assertion executions (including
repeated checks) and 17 raw game screenshots. Core/extender border indices are
14/13, both share screen bounds, and the internal border is absent after reload.
Coverage includes native BlockItem placement, basic/advanced/holographic text,
portable GUI pages and actual slot packets, production FE capability readings
3200 -> 4800, screen GUI open/close, ordinary extender destruction/replacement,
client geometry synchronization, four power modes with both redstone states,
saving, process exit and complete server/client restart.
The 29-row pagination card is explicitly a QA fixture. Actual FE reads use the
production kit assembler and NeoForge capability, not hardcoded production data.
No external industrial mod is required or asserted as tested here.

Not covered: Fabric, 26.3, all orientations/slopes, concurrent two-player edits,
long-running stress or every industrial integration. Existing disabled modern
upstream integrations remain disabled in this test backport.

## Reproduction

Check out the tested compatibility branch as `Energy-Control-neoforge-1.21.1`.
Copy this directory to the sibling `neoforge-1.21.1-game-qa` directory (or preserve
the same paths from the local evidence archive). Use Java 21; the Windows runner
uses `C:\Program Files\Zulu\zulu-21`. Run `gradlew.bat build --console=plain` in
the repository, then `py -3 neoforge-1.21.1-game-qa/run.py` from the parent folder.
Pass the resulting run ID as an argument to replay its saved world in new
processes. The script writes a localhost-only server on port 25587 and fresh game
directories; it assumes the official Minecraft EULA has been accepted. Do not
point it at an existing personal game instance.

The logs here contain selected original QA/diagnostic/shutdown lines; original
complete process logs and worlds remain in the task's local evidence archive.
Result JSON removes only the host directory field and records the original file
hash. Screenshots are unedited native game captures. QA and changes were authored
with Codex assistance; upstream GPLv3 and copyrights are preserved.
