# Ocarina N64 in Minecraft — 0.5.4-preview

Play as Link in Minecraft Java 1.21.11 with original ROM-driven movement, rendering, sounds, sword/shield combat, and a gold Z-target marker. The target marker has been confirmed working in Minecraft by the project owner.

This experimental release also includes adapters for ROM-scene exploration and the optional original-story engine with Minecraft's camera, pause-menu fixes, and loaded-room GPU caching. The complete story and all interiors are not yet verified.

**Windows x64 · Fabric 0.19.5 · Fabric API 0.141.6+1.21.11 · Java 21+ · unshared single-player only.**

## Downloads and setup

- **ocarina-n64-minecraft-0.5.4-preview.jar** — Minecraft client mod. Install in mods after following the native dependency setup.
- **ocarina-n64-minecraft-0.5.4-preview-source.zip** — adapter source, licenses, patches, configuration example, and build/install instructions.
- **SHA256SUMS.txt** — download checksums.

**A JAR-only installation will not run Link.** Build the native liboot engine and JNI bridge locally following README.md. The optional story runtime requires a separate patched Shipwright build. Bring your own compatible ROM. No ROMs, extracted game assets, native game engines, Minecraft files, credentials, or saves are included.

Controls: O enables regular Link; WASD move; Space roll; J sword; K shield; Z target. /ootscene explores Kokiri Forest; /ootreturn exits. Story controls and setup are documented separately.

Native targeting/combat tests and client startup checks passed. Portable setup instructions have not yet been verified on a fresh second computer. Please treat this as a developer preview.

Built with OpenAI Codex and Universal Modder. Thanks to Cycl0o0/liboot, HarbourMasters/Shipwright and LibUltraShip, and Fabric. See THIRD_PARTY_NOTICES.md for upstream credits and licensing.
