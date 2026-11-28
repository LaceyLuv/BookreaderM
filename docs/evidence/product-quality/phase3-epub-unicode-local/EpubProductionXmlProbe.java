import java.nio.file.*;
public class EpubProductionXmlProbe {
 public static void main(String[] args) throws Exception {
  byte[] input=Files.readAllBytes(Paths.get(args[0]));
  Class<?> policy=Class.forName("org.bookreader.mobile.epub.EpubContentPolicy");
  byte[] output=(byte[])policy.getMethod("protectXhtml",byte[].class,String.class).invoke(policy.getField("INSTANCE").get(null),input,"OPS/one.xhtml");
  String text=new String(output,"UTF-8");
  if(!text.contains("😀 alpha same same") || text.contains("&#55357;&#56832;") || text.contains("encoding=\"UTF-16\""))throw new AssertionError("Unicode or encoding mismatch");
  System.out.println("PASS actual production EpubContentPolicy on Android API26, UTF-8 supplementary Unicode and CSP preserved, bytes="+output.length);
  System.out.println(text.substring(0,Math.min(1300,text.length())));
 }
}
