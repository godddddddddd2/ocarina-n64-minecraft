#include "StoryBridge.h"
#include <chrono>
#include <thread>
int main(){
 if(!OotStory::Enabled())return 2;
 uint32_t textures[6]{};uint8_t sizes[2]={4,4};int locations[2]={0,1};
 float triangle[]={-1,-1,0,1,1,0,0,1, 1,-1,0,1,0,1,0,1, 0,1,0,1,0,0,1,1};
 while(OotStory::WaitForHost()){
  uint32_t buttons;int8_t x,y;OotStory::Input(buttons,x,y);
  float state[48]{};state[0]=buttons;state[1]=x;state[2]=y;OotStory::State(state,48);
  OotStory::BeginFrame();OotStory::Viewport(0,0,640,480);OotStory::Scissor(0,0,640,480);
  OotStory::Draw(1,textures,sizes,locations,2,8,3,0,1,0,triangle,24,1);OotStory::EndFrame();
  std::this_thread::sleep_for(std::chrono::milliseconds(5));
 }
 return 0;
}
