const fs=require('fs'),path=require('path'),cp=require('child_process');
const root=path.resolve(__dirname,'..'),cache=path.resolve(root,'../ocarina-build-cache'),src=path.join(cache,'liboot');
const rev='25208734c8ca388638f0ce63841e166fac9e5acb',gcc=process.env.OOT_GCC||'gcc';
const jdk=process.env.JAVA_HOME;if(!jdk)throw Error('Set JAVA_HOME to your x64 JDK 21 or newer.');
const out=path.join(root,'build/native');
fs.mkdirSync(out,{recursive:true});fs.mkdirSync(cache,{recursive:true});
const env={...process.env,PATH:path.dirname(gcc)+';'+process.env.PATH};
function run(cmd,args,cwd=root){const r=cp.spawnSync(cmd,args,{cwd,env,windowsHide:true,encoding:'utf8',maxBuffer:20*1024*1024});if(r.status!==0)throw Error(r.stderr||r.error||'command failed');return r.stdout;}
if(!fs.existsSync(path.join(src,'.git')))run('git',['-c','http.sslBackend=openssl','clone','https://github.com/Cycl0o0/liboot.git',src]);
if(run('git',['rev-parse','HEAD'],src).trim()!==rev)run('git',['checkout','--detach',rev],src);
const defs=['LIBOOT_HOST_BUILD=1','_LANGUAGE_C','NON_MATCHING','AVOID_UB','DEBUG_FEATURES=0','OOT_VERSION=PAL_1_1','OOT_REVISION=1','PLATFORM_N64=1','PLATFORM_GC=0','PLATFORM_IQUE=0','F3DEX_GBI_2','LIBOOT_MULTI_INSTANCE=1','OOT_LIB_EXPORT'];
const flags=['-std=gnu11','-O1','-fno-strict-aliasing','-funsigned-char','-fvisibility=hidden',...defs.map(x=>'-D'+x),'-Isrc','-Isrc/shim','-Isrc/decomp/include','-Isrc/decomp/src'];
const files=run('git',['ls-files','src'],src).trim().split('\n').filter(x=>x.endsWith('.c')&&!x.endsWith('.inc.c'));
const objects=[];
for(const file of files){const object=path.join(out,file.replaceAll('/','_')+'.o');run(gcc,[...flags,...(file.startsWith('src/decomp/')?['-Isrc/decomp/include/libc','-Isrc/decomp']:[]),'-c',file,'-o',object],src);objects.push(object);}
run(gcc,[...objects,'-shared','-static','-Wl,--out-implib,'+path.join(out,'liboot.dll.a'),'-o',path.join(out,'liboot.dll')],src);
run(gcc,['-std=c11','-shared','-static-libgcc','-I'+path.join(src,'src'),'-I'+path.join(jdk,'include'),'-I'+path.join(jdk,'include/win32'),path.join(root,'native/bridge.c'),path.join(src,'src/rom_util.c'),path.join(out,'liboot.dll.a'),'-o',path.join(out,'ocarina_bridge.dll')]);
console.log('Built native libraries: '+out);
