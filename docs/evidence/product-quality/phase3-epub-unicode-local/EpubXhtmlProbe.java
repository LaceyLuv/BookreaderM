import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
public class EpubXhtmlProbe {
  public static void main(String[] args) throws Exception {
    DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
    Document document=factory.newDocumentBuilder().parse(new File(args[0]));
    Element head=(Element)document.getElementsByTagNameNS("*","head").item(0);
    Element meta=document.createElementNS(head.getNamespaceURI(),"meta");
    meta.setAttribute("http-equiv","Content-Security-Policy");meta.setAttribute("content","default-src 'none'; script-src https://readium/assets/readium/scripts/;");
    head.insertBefore(meta,head.getFirstChild());
    ByteArrayOutputStream output=new ByteArrayOutputStream();
    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document),new StreamResult(output));
    String value=output.toString("UTF-8");
    System.out.println("Transformer="+TransformerFactory.newInstance().getClass().getName());
    System.out.println("Protected prefix="+value.substring(0,Math.min(700,value.length())));
    factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
    System.out.println("PASS protected XML reparses");
    for(String encoding:new String[]{"UTF-8","UTF-16"}) {
      Transformer transformer=TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING,encoding);
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"yes");
      StringWriter text=new StringWriter();transformer.transform(new DOMSource(document),new StreamResult(text));
      String result=text.toString();
      System.out.println("StringWriter "+encoding+" prefix="+result.substring(0,Math.min(430,result.length())));
    }
  }
}
