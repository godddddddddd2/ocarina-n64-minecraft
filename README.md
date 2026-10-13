# Ocarina N64 in Minecraft

Experimental Windows x64 mod for **Minecraft Java 1.21.11**. Play as Link in a Minecraft single-player world, explore ROM scenes, and try the separate original-story runtime with Minecraft's camera.

**This is a developer preview, not a completed Ocarina of Time campaign port.** The release does not include a Zelda ROM, extracted Nintendo assets, native game engines, Minecraft, or saved games. The JAR alone is not a complete installation: build the native dependencies locally using the instructions below.

## Features

- Original Link model, movement, sword, shield, textures, sounds, and swimming, read from your own ROM.
- Sword contacts damage nearby Minecraft mobs; frontal shield blocking and a gold native Z-target marker.
- Original scene geometry, collision, and supported Kokiri doorway transitions through /ootscene.
- Optional /ootworld and /ootstory runtime adapters, including pause-menu fixes and GPU geometry caching for loaded rooms.

## Requirements

- Windows x64, Minecraft Java **1.21.11**, Java **21 or newer**.
- [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5** and [Fabric API](https://modrinth.com/mod/fabric-api) **0.141.6+1.21.11** (tested versions).
- Your own compatible Ocarina of Time ROM. Tested: USA Revision B / NTSC-U 1.2, SHA-1 41b3bdc48d98c48529219919015a1af22f5057c2.
- For the local native build: Git, Node.js, a full x64 JDK, and MSYS2 UCRT64 GCC.
- Unshared single-player worlds only. Multiplayer and LAN sharing are unsupported.

## Install regular Link and exploration mode

1. Create a separate Fabric 1.21.11 Minecraft instance and install the Fabric API version above. Keep a backup of any worlds you use.
2. Download and extract the source ZIP from this release. Put MSYS2 UCRT64 on PATH and set JAVA_HOME to a JDK 21+ directory. From the source directory run:

   ~~~powershell
   node tools/build-native.cjs
   ~~~

   This downloads pinned upstream liboot source to a sibling directory named ocarina-build-cache and compiles liboot.dll and ocarina_bridge.dll locally. These native engines are deliberately not redistributed here.
3. Download the release JAR and place it in your instance's mods directory. Place the two generated DLLs from build/native in an ocarina-native directory inside the instance.
4. Create config/ocarina.properties inside that instance using config/ocarina.properties.example as a guide. Use full paths with forward slashes. Set rom to your own ROM and nativeDirectory to the directory containing both DLLs. No ROM is downloaded by this project.
5. Start the instance and open an unshared single-player world. Press **O** to become Link, then **Z** facing a nearby mob to target it. **/ootscene** opens the original Kokiri exploration scene. **/ootreturn** exits.

## Controls

| Action | Regular Link / exploration | Optional story runtime |
| --- | --- | --- |
| Move | WASD | WASD |
| Action | Space: roll | Space: A / dialogue / confirm |
| Sword | J | J: B |
| Shield | K | K: R |
| Target | Z; gold marker follows native lock | Z |
| Pause | Minecraft pause | Enter |
| C buttons | — | Arrow keys |
| Exit | O or /ootreturn | O or /ootreturn |

The regular marker follows the native locked target, clears when the lock is lost, and respects F1. It is separate from the story runtime's targeting display.

## Optional original-story runtime

See [docs/STORY_BUILD.md](docs/STORY_BUILD.md). This is an advanced local build of a pinned Shipwright engine with the supplied adapter patches. Stock Shipwright releases do not provide this Minecraft bridge. You must build it locally and extract your own ROM assets before enabling storyRuntimeDirectory.

/ootworld uses Minecraft's camera in supported 3D scenes; /ootstory uses the original camera. Image-based interiors retain the original view. Story saves are separate from Minecraft worlds. Save through the original Zelda pause menu before exiting.

## Status and limitations

The user confirmed regular-mode sounds, house entrances, and the 0.5.4 target marker in Minecraft. Native tests cover target acquisition/movement/removal, sword contacts, shield posture, scene loading, water, and sound output. Client initialization tests passed. Independent original-runtime GPU tests cover pause menus, textures, shared depth, and room geometry caching.

The full campaign, all buildings, all bosses, and every physics interaction have **not** been verified. Regular /ootscene mode has no original story actors, quests, or cutscenes. Minecraft mob combat is implemented in regular Link mode; it is not yet fully integrated into story mode. Alternative renderers, shaders, and other mods are untested. The measured 97% geometry-upload reduction is a test result, not an FPS improvement claim.

## Uninstall and troubleshooting

Close Minecraft before changing files. Remove the Ocarina JAR from mods to disable the mod. Keep your ROM and saves. You may then remove the dedicated configuration/native directories if no longer needed; story saves live in the configured runtime's Save directory.

- Missing ROM/config: verify config/ocarina.properties and use forward slashes in paths.
- Native load error: use x64 Java and both DLLs built from this source version; do not mix old JNI bridge DLLs with this JAR.
- Missing story runtime: complete the optional build instructions, or use O and /ootscene.
- Controls conflict: change the Ocarina key bindings in Minecraft's Controls menu.
- Report crashes with Minecraft's logs/latest.log and crash-reports, after removing personal paths and account details. Never upload your ROM, extracted assets, saves, or access tokens.

## Build the Java mod

Use a JDK 21+ and run ./gradlew build (gradlew.bat build on Windows). The Fabric-remapped JAR is generated under build/libs. Source and build configuration are supplied with this release. The release binary was built against the pinned Minecraft/Fabric dependencies with the repository's Java/ASM packaging workflow; the portable Gradle and native setup instructions have not been tested on a fresh second computer.

## Credits and license

- Project direction and in-game testing: the repository owner.
- Implementation and release preparation: OpenAI Codex, with the Universal Modder toolkit. AI-assisted code; no fal-generated artwork or audio is bundled.
- [Cycl0o0/liboot](https://github.com/Cycl0o0/liboot), pinned at 25208734c8ca388638f0ce63841e166fac9e5acb, supplies the regular Link runtime when built locally.
- [HarbourMasters/Shipwright](https://github.com/HarbourMasters/Shipwright), pinned at 7be5a4feacd1db1474bec97bb3ba7ad08de89d28, and LibUltraShip supply the optional story runtime when built locally.
- [Fabric](https://fabricmc.net/), LWJGL, and JOML provide host integration and graphics support; they are separate dependencies.
- Nintendo and Mojang/Microsoft own their respective games, assets, and trademarks. This independent project is not endorsed by them.

Project adapter code is distributed under the GNU Affero General Public License v3 or later; see LICENSE and THIRD_PARTY_NOTICES.md. Third-party engines and assets retain their own terms.
