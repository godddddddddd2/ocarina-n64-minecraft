#pragma once
#include <cstdint>
#include <cstddef>
namespace OotStory {
bool Enabled();
bool WorldMode();
bool WorldDraw();
uint32_t CullFlags();
void Culling(uint32_t flags);
void TriangleWorld(const float* a,const float* b,const float* c);
bool Camera(float* values);
// Returns validated guest-unit AABBs for the current collision-context generation.
int Blocks(uint32_t epoch, int scene, float* boxes, size_t capacity);
bool ProjectionChanged(const float* matrix,float aspect,bool world);
void Projection(const float* matrix,float aspect,bool world);
// Returns false after an explicit stop or a 10 second host heartbeat timeout.
bool WaitForHost();
bool Input(uint32_t& buttons, int8_t& x, int8_t& y);
void State(const float* values, size_t count);
void MainFramebuffer(int id);
void BeginFrame();
void EndFrame();
void Shader(uint32_t id, const char* vertex, const char* fragment, const int* locations, const char* const* names, size_t count);
void Texture(uint32_t id, const uint8_t* pixels, uint32_t width, uint32_t height);
void Sampler(uint32_t id, uint32_t s, uint32_t t, bool linear);
void Viewport(int x,int y,int width,int height);
void Scissor(int x,int y,int width,int height);
void Draw(uint32_t shader, const uint32_t* textures, const uint8_t* sizes, const int* locations,
          uint32_t attributes, uint32_t stride, uint32_t flags, uint32_t framebuffer,
          float noise, float depth, const float* vertices, size_t count, size_t triangles);
}
