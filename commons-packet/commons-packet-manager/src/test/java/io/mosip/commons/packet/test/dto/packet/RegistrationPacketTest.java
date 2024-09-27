package io.mosip.commons.packet.test.dto.packet;

import io.mosip.commons.packet.dto.packet.RegistrationPacket;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RegistrationPacketTest {

    @Autowired
    private RegistrationPacket registrationPacket;

    @Before
    public void before(){
        registrationPacket =new RegistrationPacket("yyyy-MM-dd'T'HH:mm:ss.SSS");
    }

    @Test
    public void setFields_withArrayOfString_thenPass()
    {
        String arrayOfString="[\"handle1\", \"handle2\", \"handle3\"]";
        registrationPacket.setField("handle",arrayOfString);
        Map<String,Object> mapObj=registrationPacket.getDemographics();
        List<String> handleList= (List<String>) mapObj.get("handle");
        assertEquals(3, handleList.size());
        assertEquals("handle1", handleList.get(0));
        assertEquals("handle2", handleList.get(1));
        assertEquals("handle3", handleList.get(2));
    }

    @Test
    public void setFields_withObjectofMap_thenPass()
    {
        String arrayofMapAsString="[\n" +
                "        {\n" +
                "          \"language\": \"eng\",\n" +
                "          \"value\": \"NFR\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"language\": \"ara\",\n" +
                "          \"value\": \"NFR\"\n" +
                "        }\n" +
                "      ]";
        registrationPacket.setField("residenceStatus",arrayofMapAsString);
        Map<String,Object> mapObj=registrationPacket.getDemographics();
        List<Object> handleList= (List<Object>) mapObj.get("residenceStatus");
        HashMap<String,Object> has1= (HashMap<String, Object>) handleList.get(0);
        assertEquals("eng",has1.get("language"));
        assertEquals("NFR",has1.get("value"));
    }

    @Test
    public void setFields_withHashmap_thenPass()
    {
        String mapAsString="{\n" +
                "        \"format\": \"pdf\",\n" +
                "        \"type\": \"Reference Identity Card\",\n" +
                "        \"value\": \"proofOfIdentity\"\n" +
                "      }";
        registrationPacket.setField("proofOfIdentity",mapAsString);
        Map<String,Object> mapObj=registrationPacket.getDemographics();
        HashMap<String,String> handleList= (HashMap<String, String>) mapObj.get("proofOfIdentity");
        assertEquals("pdf",handleList.get("format"));
        assertEquals("Reference Identity Card",handleList.get("type"));
    }

    @Test
    public void setFields_withString_thenPass()
    {
        String string="ExpString";
        registrationPacket.setField("StringExp",string);
        Map<String,Object> mapObj=registrationPacket.getDemographics();
        String responceString= (String) mapObj.get("StringExp");
        assertEquals("ExpString",responceString);
    }

}
