// Owned bridge glue. Included by OTRGlobals.cpp; game assets stay in the user's runtime.
#include <array>
#include <cmath>
#include <cstring>
namespace {
constexpr int OOT_HOST_BOXES=64;
DynaPolyActor ootHostActor{};
CollisionHeader ootHostHeader{};
Vec3s ootHostVertices[OOT_HOST_BOXES*8]{};
CollisionPoly ootHostPolygons[OOT_HOST_BOXES*12]{};
SurfaceType ootHostSurface[1]{};
CamData ootHostCamera[1]{};
std::array<float,OOT_HOST_BOXES*6> ootHostPrevious{};
uint32_t ootHostEpoch=0;
int ootHostId=-1,ootHostCount=0,ootHostRequested=0;
void OotHostActorUpdate(Actor*,PlayState*){}
}
extern "C" void OotStory_ReserveCollision(CollisionContext* context){
 if(!OotStory::Enabled())return;
 context->dyna.polyListMax+=OOT_HOST_BOXES*12;
 context->dyna.vtxListMax+=OOT_HOST_BOXES*8;
 context->dyna.polyNodesMax+=OOT_HOST_BOXES*12;
 context->memSize+=OOT_HOST_BOXES*(12*sizeof(CollisionPoly)+8*sizeof(Vec3s)+12*sizeof(SSNode));
}
extern "C" void OotStory_ResetCollision(void){
 // No old PlayState pointer is retained or dereferenced across scene destruction.
 ootHostId=-1;ootHostCount=ootHostRequested=0;
 ootHostEpoch=ootHostEpoch>=16000000?1:ootHostEpoch+1;
}
extern "C" void OotStory_UpdateCollision(PlayState* play,DynaCollisionContext* dyna){
 if(!OotStory::Enabled())return;
 std::array<float,OOT_HOST_BOXES*6> boxes{};
 int count=OotStory::Blocks(ootHostEpoch,play->sceneNum,boxes.data(),OOT_HOST_BOXES);
 ootHostRequested=count;
 if(ootHostId>=0&&(!(dyna->bgActorFlags[ootHostId]&1)||dyna->bgActors[ootHostId].actor!=&ootHostActor.actor))ootHostId=-1;
 int usedPoly=0,usedVertex=0;
 for(int i=0;i<BG_ACTOR_MAX;i++)if(i!=ootHostId&&(dyna->bgActorFlags[i]&1)&&!(dyna->bgActorFlags[i]&6)&&dyna->bgActors[i].colHeader){
  usedPoly+=dyna->bgActors[i].colHeader->numPolygons;usedVertex+=dyna->bgActors[i].colHeader->numVertices;
 }
 count=std::max(0,std::min({count,(dyna->polyListMax-usedPoly)/12,(dyna->vtxListMax-usedVertex)/8,(dyna->polyNodesMax-usedPoly)/12}));
 if(count==0){if(ootHostId>=0)DynaPoly_DeleteBgActor(play,dyna,ootHostId);ootHostId=-1;ootHostCount=0;return;}
 if(ootHostId>=0&&count==ootHostCount&&std::memcmp(boxes.data(),ootHostPrevious.data(),count*6*sizeof(float))==0)return;
 // Outward-facing triangles: -Z,+Z,-X,+X,-Y,+Y. The engine computes normals and plane distances.
 constexpr unsigned faces[12][3]={{0,2,1},{0,3,2},{4,5,6},{4,6,7},{0,4,7},{0,7,3},{1,2,6},{1,6,5},{0,1,5},{0,5,4},{3,7,6},{3,6,2}};
 for(int b=0;b<count;b++){
  auto* v=ootHostVertices+b*8;auto* f=boxes.data()+b*6;
  s16 x0=static_cast<s16>(std::floor(f[0])),y0=static_cast<s16>(std::floor(f[1])),z0=static_cast<s16>(std::floor(f[2]));
  s16 x1=static_cast<s16>(std::ceil(f[3])),y1=static_cast<s16>(std::ceil(f[4])),z1=static_cast<s16>(std::ceil(f[5]));
  v[0]={x0,y0,z0};v[1]={x1,y0,z0};v[2]={x1,y1,z0};v[3]={x0,y1,z0};
  v[4]={x0,y0,z1};v[5]={x1,y0,z1};v[6]={x1,y1,z1};v[7]={x0,y1,z1};
  for(int t=0;t<12;t++){auto& p=ootHostPolygons[b*12+t];p={};p.flags_vIA=b*8+faces[t][0];p.flags_vIB=b*8+faces[t][1];p.vIC=b*8+faces[t][2];}
 }
 ootHostHeader={};ootHostHeader.minBounds={-32000,-32000,-32000};ootHostHeader.maxBounds={32000,32000,32000};
 ootHostHeader.numVertices=count*8;ootHostHeader.vtxList=ootHostVertices;
 ootHostHeader.numPolygons=count*12;ootHostHeader.polyList=ootHostPolygons;
 ootHostHeader.surfaceTypeList=ootHostSurface;ootHostHeader.cameraDataList=ootHostCamera;ootHostHeader.cameraDataListLen=1;
 if(ootHostId<0){
  ootHostActor={};ootHostActor.actor.id=-1;ootHostActor.actor.update=OotHostActorUpdate;ootHostActor.actor.scale={1,1,1};
  ootHostId=DynaPoly_SetBgActor(play,dyna,&ootHostActor.actor,&ootHostHeader);
  if(ootHostId>=BG_ACTOR_MAX){ootHostId=-1;ootHostCount=0;return;}
  ootHostActor.bgId=ootHostId;
 }
 ootHostCount=count;ootHostPrevious=boxes;dyna->bitFlag|=DYNAPOLY_INVALIDATE_LOOKUP;
}
