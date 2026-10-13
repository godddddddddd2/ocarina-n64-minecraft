set(VCPKG_TARGET_ARCHITECTURE x64)
set(VCPKG_CRT_LINKAGE static)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_PROVIDED_FORTRAN ON)

# Hash long object paths to stay below Windows resource compiler limits.
set(VCPKG_CMAKE_CONFIGURE_OPTIONS "-DCMAKE_OBJECT_PATH_MAX=128")

# Use direct RC compilation: short-path resolution produced an empty cmcldeps launcher.
list(APPEND VCPKG_CMAKE_CONFIGURE_OPTIONS "-DCMAKE_NINJA_CMCLDEPS_RC=OFF")
