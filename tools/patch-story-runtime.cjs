const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..'),cache=path.resolve(root,'../ocarina-runtime-cache'),source=cache+'/Shipwright';
const stateFile=root+'/evidence/runtime-active.json';
const active=fs.existsSync(stateFile)?JSON.parse(fs.readFileSync(stateFile,'utf8')):{};
if(active.status==='running')throw Error('Wait for the active build before patching its source tree.');
function patch(relative,changes){const file=source+'/'+relative;let s=fs.readFileSync(file,'utf8').replace(/\r\n/g,'\n');for(const [before,after]of changes){if(relative==='soh/soh/OTRGlobals.cpp'&&before==='extern "C" void Graph_ProcessGfxCommands(Gfx* commands) {'&&s.includes('s[47]=2.0f'))continue;if(s.includes(after))continue;if(!s.includes(before))throw Error('Patch context missing: '+relative+' '+before.slice(0,60));s=s.replace(before,after);}fs.writeFileSync(file,s);}
for(const file of ['StoryBridge.h','StoryBridge.cpp'])fs.copyFileSync(root+'/native/runtime/'+file,source+'/libultraship/src/fast/'+file);
patch('libultraship/src/fast/CMakeLists.txt', [['#=================== Top-Level ===================','# OOT_STORY_BRIDGE: local controller and native mesh transport\n#=================== Top-Level ===================']]);
patch('libultraship/src/libultraship/libultra/os.cpp',[
 ['#include <ratio>','#include <ratio>\n#include "fast/StoryBridge.h"'],
 ['Ship::Context::GetRawInstance()->GetControlDeck()->WriteToPad(pad);','if (!OotStory::Input(pad[0].button, pad[0].stick_x, pad[0].stick_y)) {\n        Ship::Context::GetRawInstance()->GetControlDeck()->WriteToPad(pad);\n    }'],
 ['Ship::Context::GetRawInstance()->GetControlDeck()->Init(controllerBits);','Ship::Context::GetRawInstance()->GetControlDeck()->Init(controllerBits);\n    if (OotStory::Enabled()) *controllerBits |= 1;']]);
patch('libultraship/src/fast/Fast3dWindow.cpp',[
 ['#include <fstream>','#include <fstream>\n#include "fast/StoryBridge.h"'],
 ['SetWindowBackend(GetSavedWindowBackend());','SetWindowBackend(OotStory::Enabled() ? WindowBackend::FAST3D_SDL_OPENGL : GetSavedWindowBackend());']]);
patch('libultraship/src/fast/backends/gfx_sdl2.cpp',[
 ['#include "fast/backends/gfx_sdl.h"','#include "fast/backends/gfx_sdl.h"\n#include "fast/StoryBridge.h"'],
 ['mWnd = SDL_CreateWindow(title, posX, posY, mWindowWidth, mWindowHeight, flags);','if (OotStory::Enabled()) flags = (flags & ~SDL_WINDOW_SHOWN) | SDL_WINDOW_HIDDEN;\n    mWnd = SDL_CreateWindow(title, posX, posY, mWindowWidth, mWindowHeight, flags);'],
 ['void GfxWindowBackendSDL2::SetFullscreenImpl(bool on, bool call_callback) {','void GfxWindowBackendSDL2::SetFullscreenImpl(bool on, bool call_callback) {\n    if (OotStory::Enabled()) return;']]);
patch('libultraship/src/fast/backends/gfx_opengl.cpp',[
 ['#include <fstream>','#include <fstream>\n#include "fast/StoryBridge.h"'],
 ['    return prg;\n}\n\nstruct ShaderProgram* GfxRenderingAPIOGL::LookupShader',
  '    if (OotStory::Enabled()) {\n        const char* names[] = {"aVtxPos","aTexCoord0","aTexClampS0","aTexClampT0","aTexCoord1","aTexClampS1","aTexClampT1","aFog","aGrayscaleColor","aInput1","aInput2","aInput3","aInput4","aInput5","aInput6","aInput7"};\n        int locations[16];\n        for (int i=0;i<16;i++) locations[i]=glGetAttribLocation(shader_program,names[i]);\n        OotStory::Shader(shader_program,vs_buf.c_str(),fs_buf.c_str(),locations,names,16);\n    }\n    return prg;\n}\n\nstruct ShaderProgram* GfxRenderingAPIOGL::LookupShader'],
 ['    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba32_buf);','    OotStory::Texture(mCurrentTextureIds[mCurrentTile],rgba32_buf,width,height);\n    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba32_buf);'],
 ['void GfxRenderingAPIOGL::SetSamplerParameters(int tile, bool linear_filter, uint32_t cms, uint32_t cmt) {','void GfxRenderingAPIOGL::SetSamplerParameters(int tile, bool linear_filter, uint32_t cms, uint32_t cmt) {\n    OotStory::Sampler(mCurrentTextureIds[tile],cms,cmt,linear_filter);'],
 ['    glViewport(x, y, width, height);','    OotStory::Viewport(x,y,width,height);\n    glViewport(x, y, width, height);'],
 ['    glScissor(x, y, width, height);','    OotStory::Scissor(x,y,width,height);\n    glScissor(x, y, width, height);'],
 ['void GfxRenderingAPIOGL::DrawTriangles(float buf_vbo[], size_t buf_vbo_len, size_t buf_vbo_num_tris) {','void GfxRenderingAPIOGL::DrawTriangles(float buf_vbo[], size_t buf_vbo_len, size_t buf_vbo_num_tris) {\n    OotStory::Draw(mCurrentShaderProgram->openglProgramId,mCurrentTextureIds,mCurrentShaderProgram->attribSizes,mCurrentShaderProgram->attribLocations,\n        mCurrentShaderProgram->numAttribs,mCurrentShaderProgram->numFloats,\n        (mCurrentDepthTest?1:0)|(mCurrentDepthMask?2:0)|(mLastBlendEnabled==1?4:0)|(mCurrentZmodeDecal?8:0),\n        mCurrentFrameBuffer,mCurrentNoiseScale,mCurrentPrimDepth,buf_vbo,buf_vbo_len,buf_vbo_num_tris);'],
 ['void GfxRenderingAPIOGL::StartFrame() {','void GfxRenderingAPIOGL::StartFrame() {\n    OotStory::BeginFrame();'],
 ['void GfxRenderingAPIOGL::EndFrame() {','void GfxRenderingAPIOGL::EndFrame() {\n    OotStory::EndFrame();']]);
patch('soh/src/code/graph.c',[
 ['void Graph_ThreadEntry(void* arg0) {','extern int OotStory_WaitForHost(void);\n\nvoid Graph_ThreadEntry(void* arg0) {'],
 ['while (WindowIsRunning()) {\n        RunFrame();','while (WindowIsRunning() && OotStory_WaitForHost()) {\n        RunFrame();']]);
patch('soh/soh/OTRGlobals.cpp',[
 ['void OTRGlobals::RunExtract(int argc, char* argv[]) {','#include \"fast/StoryBridge.h\"\nvoid OTRGlobals::RunExtract(int argc, char* argv[]) {'],
 ['    while (!extractDone) {','    while (!extractDone) {\n        if (OotStory::Enabled() && !OotStory::WaitForHost()) exit(0);'],
 ['extern "C" void Graph_ProcessGfxCommands(Gfx* commands) {',
  '#include "fast/StoryBridge.h"\nextern "C" int OotStory_WaitForHost(void) { return OotStory::WaitForHost() ? 1 : 0; }\nextern "C" void Graph_ProcessGfxCommands(Gfx* commands) {\n    if (OotStory::Enabled()) {\n        float s[48]{};\n        s[0]=gPlayState?1.0f:0.0f;\n        s[2]=static_cast<float>(gSaveContext.entranceIndex);\n        s[3]=static_cast<float>(gSaveContext.health);\n        s[4]=static_cast<float>(gSaveContext.healthCapacity);\n        s[5]=static_cast<float>(gSaveContext.linkAge);\n        if(gPlayState) {\n            s[1]=static_cast<float>(gPlayState->sceneNum);\n            s[6]=static_cast<float>(gPlayState->gameplayFrames);\n            s[7]=gPlayState->view.fovy;\n            auto* player=GET_PLAYER(gPlayState);\n            if(player){s[8]=player->actor.world.pos.x;s[9]=player->actor.world.pos.y;s[10]=player->actor.world.pos.z;s[11]=static_cast<float>(player->actor.shape.rot.y);}\n            s[12]=gPlayState->view.eye.x;s[13]=gPlayState->view.eye.y;s[14]=gPlayState->view.eye.z;\n            s[15]=gPlayState->view.lookAt.x;s[16]=gPlayState->view.lookAt.y;s[17]=gPlayState->view.lookAt.z;\n            s[18]=gPlayState->view.up.x;s[19]=gPlayState->view.up.y;s[20]=gPlayState->view.up.z;\n            memcpy(s+21,&gPlayState->viewProjectionMtxF,16*sizeof(float));\n        }\n        OotStory::State(s,48);\n    }']]);
console.log('Patched original engine for an opt-in hidden mesh and controller bridge.');

const cmakeFile=source+'/soh/CMakeLists.txt';let cmake=fs.readFileSync(cmakeFile,'utf8');
if(!cmake.includes('option(SOH_COPY_EXTRACTOR_METADATA')){
 const start=cmake.indexOf('if (CMAKE_GENERATOR MATCHES "Visual Studio")',cmake.indexOf('# Pre build events'));
 const end=cmake.indexOf('################################################################################\n# Dependencies',start);
 if(start<0||end<0)throw Error('Metadata copy block not found');
 cmake=cmake.slice(0,start)+'option(SOH_COPY_EXTRACTOR_METADATA "Copy extractor metadata beside the engine" OFF)\nif(SOH_COPY_EXTRACTOR_METADATA)\n'+cmake.slice(start,end)+'endif()\n'+cmake.slice(end);fs.writeFileSync(cmakeFile,cmake);
}

// Disable MSVC whole-program optimization to keep preview builds within memory limits.
fs.writeFileSync(cmakeFile,fs.readFileSync(cmakeFile,'utf8').replaceAll('INTERPROCEDURAL_OPTIMIZATION_RELEASE "TRUE"','INTERPROCEDURAL_OPTIMIZATION_RELEASE "FALSE"'));
