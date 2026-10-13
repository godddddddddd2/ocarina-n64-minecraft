# Third-party notices

This package contains the project's Java/JNI adapters and patches, not the original game engines or extracted assets.

## liboot

Copyright (C) 2026 Cycl0o0. Original integration code: AGPL-3.0-or-later. Upstream source and notices: https://github.com/Cycl0o0/liboot/tree/25208734c8ca388638f0ce63841e166fac9e5acb

The upstream NOTICE.md distinguishes its integration code from vendored zeldaret/oot decompilation material and states that the latter is not relicensed by liboot. This release does not bundle that source subtree or its compiled engine. The local build obtains upstream source separately. Preserve its notices when working with it. liboot also acknowledges MIT-licensed LibUltraShip mixer code.

## Optional story engine

Shipwright and LibUltraShip are separate upstream projects. The adapter patches target Shipwright commit 7be5a4feacd1db1474bec97bb3ba7ad08de89d28. No Shipwright executable, source tree, resource archive, or game asset is bundled. Consult the upstream repositories for their notices and dependencies.

## Host dependencies

Minecraft, Fabric Loader/API, LWJGL, JOML, and the JDK are acquired separately. The Gradle wrapper is standard Gradle build tooling under Apache-2.0; its license is in LICENSES/Gradle-Apache-2.0.txt.

All Zelda models, textures, animation, sound, and story data used at runtime come from the user's own game files. No such data is present in this release.
