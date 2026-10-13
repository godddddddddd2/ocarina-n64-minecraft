#define WIN32_LEAN_AND_MEAN
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include "StoryBridge.h"
#include <algorithm>
#include <array>
#include <cstdlib>
#include <cmath>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <string>
#include <unordered_map>
#include <vector>

namespace OotStory {
namespace {
constexpr uint32_t MAGIC=0x53544f4f, VERSION=1, CAPACITY=16*1024*1024, HEADER=320;
struct Resource {uint32_t revision=0,width=0,height=0,s=0,t=0,linear=0;};
struct Bridge {
    bool enabled=false, initialized=false, overflow=false, worldMode=false, worldDraw=false;
    std::array<float,16> projection{};std::array<double,16> inverse{};float aspect=1;
    HANDLE file=INVALID_HANDLE_VALUE, mapping=nullptr;
    uint8_t* memory=nullptr;
    std::filesystem::path directory;
    std::vector<uint8_t> frame;
    std::unordered_map<uint32_t,Resource> resources;
    std::array<float,48> state{};
    std::array<int32_t,4> viewport{},scissor{};
    uint64_t frameNumber=0,lastHeartbeat=0,lastSeen=0;
    uint32_t draws=0,cullFlags=0;
    std::vector<float> worldPositions;
    int mainFramebuffer=0;bool mainFramebufferKnown=false;
    struct RetiredTexture {uint64_t frame;std::filesystem::path path;};
    std::vector<RetiredTexture> retiredTextures;
    ~Bridge(){if(memory)UnmapViewOfFile(memory);if(mapping)CloseHandle(mapping);if(file!=INVALID_HANDLE_VALUE)CloseHandle(file);}
};
Bridge bridge;
void Initialize(){
    if(bridge.initialized)return;
    bridge.initialized=true;
    const wchar_t* dir=_wgetenv(L"OOT_STORY_BRIDGE");
    if(!dir||!*dir)return;
    bridge.directory=std::filesystem::path(dir);
    std::error_code error;
    std::filesystem::create_directories(bridge.directory/"resources",error);
    if(error)return;
    bridge.file=CreateFileW((bridge.directory/L"channel.bin").c_str(),GENERIC_READ|GENERIC_WRITE,
      FILE_SHARE_READ|FILE_SHARE_WRITE,nullptr,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,nullptr);
    if(bridge.file==INVALID_HANDLE_VALUE)return;
    bridge.mapping=CreateFileMappingW(bridge.file,nullptr,PAGE_READWRITE,0,CAPACITY+4096,nullptr);
    if(!bridge.mapping)return;
    bridge.memory=static_cast<uint8_t*>(MapViewOfFile(bridge.mapping,FILE_MAP_ALL_ACCESS,0,0,CAPACITY+4096));
    if(!bridge.memory)return;
    // Input occupies bytes 0..4095. Output begins at 4096 and is protected by a seqlock.
    std::memset(bridge.memory+4096,0,HEADER);
    bridge.lastSeen=GetTickCount64();
    bridge.frame.reserve(CAPACITY);
    bridge.enabled=true;
}
template<class T>void Append(const T& value){const auto* p=reinterpret_cast<const uint8_t*>(&value);bridge.frame.insert(bridge.frame.end(),p,p+sizeof(T));}
void Bytes(const void* data,size_t count){const auto* p=static_cast<const uint8_t*>(data);bridge.frame.insert(bridge.frame.end(),p,p+count);}
void Store(std::vector<uint8_t>& output,const void* data,size_t count){const auto* p=static_cast<const uint8_t*>(data);output.insert(output.end(),p,p+count);}
void Save(const std::string& name,const std::vector<uint8_t>& bytes){
    auto dest=bridge.directory/"resources"/name;
    auto temp=dest;temp+=".tmp";
    std::ofstream stream(temp,std::ios::binary|std::ios::trunc);
    stream.write(reinterpret_cast<const char*>(bytes.data()),static_cast<std::streamsize>(bytes.size()));
    stream.close();
    if(!stream){bridge.overflow=true;return;}
    // Windows readers can briefly hold the destination without delete sharing.
    // Keep the previous complete resource until its replacement can be published.
    for(int attempt=0;attempt<50;attempt++){
        if(MoveFileExW(temp.c_str(),dest.c_str(),MOVEFILE_REPLACE_EXISTING))return;
        DWORD error=GetLastError();
        if(error!=ERROR_SHARING_VIOLATION&&error!=ERROR_ACCESS_DENIED)break;
        Sleep(1);
    }
    bridge.overflow=true;
}
struct Control {uint32_t magic,version,seq,flags;uint64_t heartbeat;uint32_t buttons;int32_t x,y;uint32_t reserved;float camera[10];uint32_t collisionVersion,epoch;int32_t scene;uint32_t boxCount;float boxes[64*6];};
static_assert(sizeof(Control)<=4096);
bool ReadControl(Control& control){
    if(!Enabled())return false;
    auto* seq=reinterpret_cast<volatile LONG*>(bridge.memory+8);
    for(int i=0;i<3;i++){
        LONG before=InterlockedCompareExchange(seq,0,0);
        if(before&1)continue;
        std::memcpy(&control,bridge.memory,sizeof(control));MemoryBarrier();
        if(before==InterlockedCompareExchange(seq,0,0))return control.magic==MAGIC&&control.version==VERSION;
    }
    return false;
}
}
bool Enabled(){Initialize();return bridge.enabled;}
bool WorldMode(){return Enabled()&&bridge.worldMode&&bridge.state[0]>0&&bridge.state[37]==0;}
bool WorldDraw(){return bridge.worldDraw;}
uint32_t CullFlags(){return bridge.worldDraw?bridge.cullFlags:0;}
void Culling(uint32_t flags){bridge.cullFlags=flags&96;}
void TriangleWorld(const float* a,const float* b,const float* c){if(!Enabled()||!bridge.worldDraw)return;for(const float* p:{a,b,c})bridge.worldPositions.insert(bridge.worldPositions.end(),p,p+4);}
bool Camera(float* values){Control c{};if(!WorldMode()||!ReadControl(c))return false;for(float v:c.camera)if(!std::isfinite(v))return false;if(c.camera[9]<10||c.camera[9]>150)return false;std::copy_n(c.camera,10,values);return true;}
int Blocks(uint32_t epoch,int scene,float* boxes,size_t capacity){
 Control c{};if(!WorldMode()||!ReadControl(c)||(c.flags&3)||!(c.flags&4)||c.collisionVersion!=1||c.epoch!=epoch||c.scene!=scene||c.boxCount>64)return 0;
 size_t count=std::min<size_t>(c.boxCount,capacity);
 for(size_t i=0;i<count;i++)for(int a=0;a<3;a++){
  float lo=c.boxes[i*6+a],hi=c.boxes[i*6+a+3];
  if(!std::isfinite(lo)||!std::isfinite(hi)||lo < -32000||hi > 32000||hi-lo<1||hi-lo>1024)return 0;
 }
 std::copy_n(c.boxes,count*6,boxes);return static_cast<int>(count);
}
bool ProjectionChanged(const float* matrix,float aspect,bool world){return bridge.worldDraw!=world||bridge.aspect!=aspect||std::memcmp(matrix,bridge.projection.data(),64)!=0;}
void Projection(const float* matrix,float aspect,bool world){
 std::copy_n(matrix,16,bridge.projection.begin());bridge.aspect=aspect;bridge.worldDraw=world;
 if(!world)return;
 double a[4][8]{};for(int r=0;r<4;r++){for(int c=0;c<4;c++)a[r][c]=matrix[c*4+r];a[r][r+4]=1;}
 for(int c=0;c<4;c++){int pivot=c;for(int r=c+1;r<4;r++)if(std::abs(a[r][c])>std::abs(a[pivot][c]))pivot=r;
  if(std::abs(a[pivot][c])<1e-12){bridge.worldDraw=false;return;}
  for(int k=0;k<8;k++)std::swap(a[c][k],a[pivot][k]);double scale=a[c][c];for(int k=0;k<8;k++)a[c][k]/=scale;
  for(int r=0;r<4;r++)if(r!=c){double f=a[r][c];for(int k=0;k<8;k++)a[r][k]-=f*a[c][k];}
 }
 for(int r=0;r<4;r++)for(int c=0;c<4;c++)bridge.inverse[c*4+r]=a[r][c+4];
}
bool WaitForHost(){
    if(!Enabled())return true;
    for(;;){
        Control control{};
        bool valid=ReadControl(control);
        uint64_t now=GetTickCount64();
        if(valid&&control.heartbeat!=bridge.lastHeartbeat){bridge.lastHeartbeat=control.heartbeat;bridge.lastSeen=now;}
        if(valid&&(control.flags&2))return false;
        if(now-bridge.lastSeen>10000)return false;
        if(valid&&!(control.flags&1)&&now-bridge.lastSeen<500){bridge.worldMode=(control.flags&4)!=0;return true;}
        Sleep(10);
    }
}
bool Input(uint32_t& buttons,int8_t& x,int8_t& y){
    if(!Enabled())return false;
    Control control{};buttons=0;x=0;y=0;
    if(ReadControl(control)&&!(control.flags&3)){
        buttons=static_cast<uint16_t>(control.buttons);
        x=static_cast<int8_t>(std::clamp(control.x,-85,85));
        y=static_cast<int8_t>(std::clamp(control.y,-85,85));
    }
    return true;
}
void State(const float* values,size_t count){if(Enabled()){bridge.state.fill(0);std::copy_n(values,std::min(count,bridge.state.size()),bridge.state.begin());}}
void MainFramebuffer(int id){if(Enabled()){bridge.mainFramebuffer=id;bridge.mainFramebufferKnown=true;}}
void BeginFrame(){if(!Enabled())return;bridge.frame.assign(HEADER,0);bridge.draws=0;bridge.overflow=false;bridge.worldPositions.clear();}
void EndFrame(){
    if(!Enabled()||bridge.frame.size()<HEADER)return;
    auto* header=reinterpret_cast<uint32_t*>(bridge.frame.data());
    header[0]=MAGIC;header[1]=VERSION;header[3]=static_cast<uint32_t>(bridge.frame.size());
    const uint64_t number=++bridge.frameNumber;std::memcpy(bridge.frame.data()+16,&number,8);
    header[6]=bridge.draws;header[7]=bridge.overflow?1:0;
    bridge.state[43]=static_cast<float>(bridge.mainFramebuffer);bridge.state[44]=bridge.mainFramebufferKnown?1.0f:0.0f;bridge.state[45]=1.0f;
    std::memcpy(bridge.frame.data()+64,bridge.state.data(),bridge.state.size()*4);
    auto* target=bridge.memory+4096;
    auto* seq=reinterpret_cast<volatile LONG*>(target+8);
    InterlockedIncrement(seq);
    std::memcpy(target,bridge.frame.data(),8);
    std::memcpy(target+12,bridge.frame.data()+12,bridge.frame.size()-12);
    MemoryBarrier();InterlockedIncrement(seq);
    if(number%60==0){auto& files=bridge.retiredTextures;files.erase(std::remove_if(files.begin(),files.end(),[&](const auto& f){if(f.frame+120>=number)return false;std::error_code e;std::filesystem::remove(f.path,e);return !e;}),files.end());}
}
void Shader(uint32_t id,const char* vertex,const char* fragment,const int* locations,const char* const* names,size_t count){
    if(!Enabled()||count>32)return;
    uint32_t lengths[]={0x52444853,VERSION,static_cast<uint32_t>(std::strlen(vertex)),static_cast<uint32_t>(std::strlen(fragment)),static_cast<uint32_t>(count)};
    std::vector<uint8_t> bytes;Store(bytes,lengths,sizeof(lengths));
    for(size_t i=0;i<count;i++){Store(bytes,&locations[i],4);char name[64]{};strncpy_s(name,names[i],63);Store(bytes,name,64);}
    Store(bytes,vertex,lengths[2]);Store(bytes,fragment,lengths[3]);Save("shader-"+std::to_string(id)+".bin",bytes);
}
void Texture(uint32_t id,const uint8_t* pixels,uint32_t width,uint32_t height){
    if(!Enabled()||!pixels||!width||!height||width>4096||height>4096)return;
    auto& resource=bridge.resources[id];resource.width=width;resource.height=height;++resource.revision;
    uint32_t header[]={0x52545854,VERSION,width,height,resource.revision};
    std::vector<uint8_t> bytes;Store(bytes,header,sizeof(header));Store(bytes,pixels,static_cast<size_t>(width)*height*4);
    std::string name="texture-"+std::to_string(id)+"-"+std::to_string(resource.revision)+".bin";
    if(resource.revision>1)bridge.retiredTextures.push_back({bridge.frameNumber,bridge.directory/"resources"/("texture-"+std::to_string(id)+"-"+std::to_string(resource.revision-1)+".bin")});
    Save(name,bytes);
}
void Sampler(uint32_t id,uint32_t s,uint32_t t,bool linear){if(Enabled()){auto& r=bridge.resources[id];r.s=s;r.t=t;r.linear=linear?1:0;}}
void Viewport(int x,int y,int width,int height){if(Enabled())bridge.viewport={x,y,width,height};}
void Scissor(int x,int y,int width,int height){if(Enabled())bridge.scissor={x,y,width,height};}
void Draw(uint32_t shader,const uint32_t* textures,const uint8_t* sizes,const int* locations,
 uint32_t attributes,uint32_t stride,uint32_t flags,uint32_t framebuffer,float noise,float depth,
 const float* vertices,size_t count,size_t triangles){
    if(!Enabled()||bridge.frame.size()<HEADER||bridge.overflow)return;
    constexpr uint32_t DRAW_HEADER=384;
    if(attributes>16||stride<4||stride>64||count!=triangles*3*stride||bridge.draws>=8192||
       count>(CAPACITY-DRAW_HEADER)/4||bridge.frame.size()+DRAW_HEADER+count*4>CAPACITY){bridge.overflow=true;return;}
    const size_t start=bridge.frame.size();
    Append(DRAW_HEADER+static_cast<uint32_t>(count)*4);Append(shader);Append(flags|(bridge.worldDraw?(16u|bridge.cullFlags):0u));Append(framebuffer);
    Append(stride);Append(attributes);Append(static_cast<uint32_t>(triangles));Append(static_cast<uint32_t>(count));
    Append(noise);Append(depth);Bytes(bridge.viewport.data(),16);Bytes(bridge.scissor.data(),16);
    for(size_t i=0;i<16;i++)Append(i<attributes?static_cast<uint32_t>(sizes[i]):0u);
    for(size_t i=0;i<16;i++)Append(i<attributes?static_cast<int32_t>(locations[i]):-1);
    for(size_t i=0;i<6;i++){const auto& r=bridge.resources[textures[i]];Append(textures[i]);Append(r.revision);Append(r.width);Append(r.height);Append(r.s);Append(r.t);Append(r.linear);}
    bridge.frame.resize(start+DRAW_HEADER,0);Bytes(vertices,count*4);
    if(bridge.worldDraw){
      auto* output=reinterpret_cast<float*>(bridge.frame.data()+start+DRAW_HEADER);
      if(bridge.worldPositions.size()==triangles*12){
       for(size_t v=0;v<triangles*3;v++)std::copy_n(bridge.worldPositions.data()+v*4,4,output+v*stride);
      }else for(size_t i=0;i<count;i+=stride){double clip[4]={vertices[i]/bridge.aspect,vertices[i+1],vertices[i+2],vertices[i+3]};double world[4]{};
        for(int r=0;r<4;r++)for(int c=0;c<4;c++)world[r]+=bridge.inverse[c*4+r]*clip[c];
        if(std::abs(world[3])<1e-9){bridge.overflow=true;return;}
        for(int r=0;r<3;r++)output[i+r]=static_cast<float>(world[r]/world[3]);output[i+3]=1;
      }
    }
    bridge.worldPositions.clear();
    ++bridge.draws;
}
}
