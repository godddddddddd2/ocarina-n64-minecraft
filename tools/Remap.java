import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;
public final class Remap {
 public static void main(String[] args)throws Exception {
  Map<String,String> classes=new HashMap<>(),methods=new HashMap<>(),fields=new HashMap<>();
  TinyUtils.createTinyMappingProvider(Path.of(args[0]),"named","intermediary").load(new IMappingProvider.MappingAcceptor(){
   public void acceptClass(String a,String b){classes.put(a,b);}
   public void acceptMethod(IMappingProvider.Member m,String b){methods.put(m.owner+"."+m.name+m.desc,b);}
   public void acceptField(IMappingProvider.Member m,String b){fields.put(m.owner+"."+m.name,b);}
   public void acceptMethodArg(IMappingProvider.Member m,int i,String b){}
   public void acceptMethodVar(IMappingProvider.Member m,int a,int b,int c,String d){}
  });
  List<ZipFile> jars=new ArrayList<>();
  for(String p:Files.readString(Path.of(args[1])).split(";"))jars.add(new ZipFile(p));
  try{
   Map<String,List<String>> parents=new HashMap<>();
   Remapper mapper=new Remapper(Opcodes.ASM9){
    public String map(String name){return classes.getOrDefault(name,name);}
    private String inherited(String owner,String suffix,Map<String,String> table,Set<String> visited){
     if(owner==null||!visited.add(owner))return null;
     String direct=table.get(owner+suffix);if(direct!=null)return direct;
     if(!parents.containsKey(owner)){
      List<String> result=new ArrayList<>();ClassReader reader=null;
      Path local=Path.of(args[2]).resolve(owner+".class");
      try{
       if(Files.isRegularFile(local))reader=new ClassReader(Files.readAllBytes(local));
       else for(ZipFile jar:jars){var e=jar.getEntry(owner+".class");if(e!=null){try(var s=jar.getInputStream(e)){reader=new ClassReader(s);}break;}}
       if(reader!=null){if(reader.getSuperName()!=null)result.add(reader.getSuperName());result.addAll(Arrays.asList(reader.getInterfaces()));}
      }catch(IOException e){throw new UncheckedIOException(e);}
      parents.put(owner,result);
     }
     for(String p:parents.get(owner)){String found=inherited(p,suffix,table,visited);if(found!=null)return found;}return null;
    }
    public String mapMethodName(String owner,String name,String descriptor){
     if(name.startsWith("<"))return name;String mapped=inherited(owner,"."+name+descriptor,methods,new HashSet<>());return mapped==null?name:mapped;
    }
    public String mapFieldName(String owner,String name,String descriptor){
     String mapped=inherited(owner,"."+name,fields,new HashSet<>());return mapped==null?name:mapped;
    }
   };
   int count=0;
   try(var stream=Files.walk(Path.of(args[2]))){
    for(var file:stream.filter(p->p.toString().endsWith(".class")).toList()){
     String relative=Path.of(args[2]).relativize(file).toString().replace('\\','/');
     if(!relative.startsWith("dev/ocarina/"))continue;
     var reader=new ClassReader(Files.readAllBytes(file));var writer=new ClassWriter(0);
     reader.accept(new ClassRemapper(writer,mapper),0);
     Path target=Path.of(args[3]).resolve(relative);Files.createDirectories(target.getParent());Files.write(target,writer.toByteArray());count++;
    }
   }
   if(count==0)throw new IllegalStateException("No classes remapped");
   System.out.println("Remapped "+count+" mod classes; dependency classes excluded.");
  }finally{for(ZipFile jar:jars)jar.close();}
 }
}