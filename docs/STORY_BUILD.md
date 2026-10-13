# Optional original-story engine (advanced)

The prebuilt Minecraft JAR supports this bridge, but the story engine and game assets are not part of the download. These Windows build instructions describe the pinned engine used in development. A fresh end-to-end installation on another computer is not yet verified.

## Tools and layout

Use Visual Studio 2022 with the x64 C++ workload, Git, Node.js, Python 3, and CMake 3.31.x. Run commands from an x64 Visual Studio developer PowerShell. vcpkg downloads additional dependencies; this can take substantial time and disk space.

Keep the source project beside a directory called ocarina-runtime-cache. From the source directory:

~~~powershell
git clone https://github.com/HarbourMasters/Shipwright.git ../ocarina-runtime-cache/Shipwright
git -C ../ocarina-runtime-cache/Shipwright checkout --detach 7be5a4feacd1db1474bec97bb3ba7ad08de89d28
git -C ../ocarina-runtime-cache/Shipwright submodule update --init --recursive
node tools/patch-world-runtime.cjs
~~~

Apply native/runtime/extractor.patch to the Shipwright checkout using git apply with the patch's absolute path. It adds the extractor arguments used below. Apply native/runtime/stb-download.patch from inside Shipwright/libultraship. The patches contain adapter changes and context, not a bundled engine source tree.

The latter patch prevents CMake from overwriting a supplied stb header. Before configuring, place stb_image.h from the pinned URL below in ../ocarina-runtime-cache/build/_deps/stb/stb_image.h:

https://raw.githubusercontent.com/nothings/stb/0bc88af4de5fb022db643c2d8e549a0927749354/stb_image.h

Configure and build from the source directory:

~~~powershell
cmake -S ../ocarina-runtime-cache/Shipwright -B ../ocarina-runtime-cache/build -G "Visual Studio 17 2022" -T v143 -A x64 -DVCPKG_OVERLAY_TRIPLETS="$PWD/tools/triplets"
cmake --build ../ocarina-runtime-cache/build --config Release --target soh-torch soh GenerateSohOtr --parallel 2 -- /p:CL_MPCount=4 /p:PreferredToolArchitecture=x64
~~~

The patch disables MSVC interprocedural optimization and repeated metadata copying to reduce build memory and unnecessary file traversal. If Python cannot be found, pass -DPython3_EXECUTABLE with the full path to your Python executable when configuring. Source downloads remain the responsibility of their upstream projects.

## Assemble a private runtime

Create a new directory for the runtime. Copy your locally built soh.exe and soh.o2r there from Shipwright/x64/Release. Copy the contents of Shipwright/soh/assets/yml into a directory named assets inside the runtime. This supplies the metadata that the patched build no longer copies automatically. These files stay on your computer.

Run the locally built build/Release/soh-torch.exe to extract your own supported ROM:

~~~text
soh-torch.exe --src PATH_TO_SHIPWRIGHT/soh/assets/yml --dest PATH_TO_RUNTIME --version 9.3.0 PATH_TO_YOUR_ROM
~~~

The private runtime must contain soh.exe, soh.o2r, oot.o2r, and the assets metadata directory. Set storyRuntimeDirectory in Minecraft's config/ocarina.properties to that directory. A stock soh.exe will not work with this bridge.

Use /ootworld or /ootstory in an unshared single-player world. The runtime creates its own Save, logs and sessions directories; do not share them in a public release. Never run two instances against the same runtime directory. Back up Save before replacing a runtime build.

## Evidence and limits

The pinned local runtime has passed original-engine menu, geometry, collision-box, shader, texture, and caching tests. The full child campaign is unverified. Frame simulation and geometry transport still run every native frame; GPU caching reduces repeated attribute uploads, not the need to draw frames.
