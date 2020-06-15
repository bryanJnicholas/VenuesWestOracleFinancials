package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPHeader;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

public class SecurityHandler implements SOAPHandler<SOAPMessageContext> {
  private String user;
  
  private String pass;
  
  public SecurityHandler(String username, String password) {
    this.user = username;
    this.pass = password;
  }
  
  public boolean handleMessage(SOAPMessageContext msgCtx) {
    Boolean outInd = (Boolean)msgCtx.get("javax.xml.ws.handler.message.outbound");
    if (outInd.booleanValue())
      try {
        SOAPEnvelope envelope = msgCtx.getMessage().getSOAPPart().getEnvelope();
        SOAPHeader header = envelope.getHeader();
        if (header == null)
          header = envelope.addHeader(); 
        SOAPElement security = header.addChildElement("Security", "wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"); 
        SOAPElement timestamp = header.addChildElement("Timestamp", "wsu", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd");
        LocalDateTime ldt = LocalDateTime.now();
        
        SOAPElement created = timestamp.addChildElement("Created");
        created.addTextNode(ldt.format(format) + ".587Z");
        SOAPElement expires = timestamp.addChildElement("Expires");
        expires.addTextNode(ldt.plusHours(1).format(format) + ".587Z");
        
        SOAPElement usernameToken = security.addChildElement("UsernameToken", "wsse");
        SOAPElement username = usernameToken.addChildElement("Username", "wsse");
        username.addTextNode(this.user);
        SOAPElement password = usernameToken.addChildElement("Password", "wsse");
        password.setAttribute("Type", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText");
        password.addTextNode(this.pass);
      } catch (Exception _exc) {
        _exc.printStackTrace();
        return false;
      }  
    return true;
  }
  
  public boolean handleFault(SOAPMessageContext context) {
    return false;
  }
  
  public void close(MessageContext context) {}
  
  public Set<QName> getHeaders() {
    return Collections.emptySet();
  }
}
