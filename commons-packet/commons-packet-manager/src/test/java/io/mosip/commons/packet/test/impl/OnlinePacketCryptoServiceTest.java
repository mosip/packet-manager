package io.mosip.commons.packet.test.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.commons.packet.dto.packet.CryptomanagerResponseDto;
import io.mosip.commons.packet.dto.packet.DecryptResponseDto;
import io.mosip.commons.packet.exception.ApiNotAccessibleException;
import io.mosip.commons.packet.exception.PacketDecryptionFailureException;
import io.mosip.commons.packet.exception.SignatureException;
import io.mosip.commons.packet.impl.OnlinePacketCryptoServiceImpl;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.util.CryptoUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OnlinePacketCryptoServiceTest {

    private static final String ID = "10001100770000320200720092256";

    @InjectMocks
    private OnlinePacketCryptoServiceImpl service;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper mapper; // only used in some tests; we swap a real one when needed

    @Before
    public void setup() {
        ReflectionTestUtils.setField(service, "DATETIME_PATTERN", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ReflectionTestUtils.setField(service, "APPLICATION_VERSION", "v1");
        ReflectionTestUtils.setField(service, "cryptomanagerDecryptUrl", "http://localhost");
        ReflectionTestUtils.setField(service, "cryptomanagerEncryptUrl", "http://localhost");
        ReflectionTestUtils.setField(service, "keymanagerCsSignUrl", "http://localhost");
        ReflectionTestUtils.setField(service, "keymanagerCsverifysignUrl", "http://localhost");
        ReflectionTestUtils.setField(service, "syncdataGetTpmKeyUrl", "http://localhost/");
    }

    @Test
    public void signSuccessReturnsData() throws Exception {
        String expected = "my-sign";
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("data", CryptoUtil.encodeToURLSafeBase64(expected.getBytes()));
        response.put("response", inner);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(LinkedHashMap.class))).thenReturn((LinkedHashMap) response);

        byte[] out = service.sign("payload".getBytes());
        assertArrayEquals(expected.getBytes(), out);
    }

    @Test(expected = SignatureException.class)
    public void signWhenResponseEmptyThrows() throws Exception {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        // ensure inner response is a LinkedHashMap so casting in production code succeeds
        wrapper.put("response", new LinkedHashMap<>());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(LinkedHashMap.class))).thenReturn((LinkedHashMap) wrapper);

        service.sign("payload".getBytes());
    }

    @Test(expected = SignatureException.class)
    public void signWhenMapperParsesInvalidJsonThrowsSignatureException() throws Exception {
        // Return invalid JSON and swap real ObjectMapper to cause IOException in readValue
        ResponseEntity<String> resp = new ResponseEntity<>("{ invalid json", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class))).thenReturn(resp);

        ObjectMapper original = (ObjectMapper) ReflectionTestUtils.getField(service, "mapper");
        ObjectMapper real = new ObjectMapper();
        ReflectionTestUtils.setField(service, "mapper", real);
        try {
            service.sign("payload".getBytes());
        } finally {
            ReflectionTestUtils.setField(service, "mapper", original);
        }
    }

    @Test
    public void encryptSuccessReturnsMergedBytes() throws Exception {
        byte[] packet = (ID + "_packet").getBytes();
        CryptomanagerResponseDto dto = new CryptomanagerResponseDto();
        dto.setErrors(null);
        DecryptResponseDto dr = new DecryptResponseDto("data");
        dto.setResponse(dr);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class))).thenReturn(dto);

        byte[] out = service.encrypt(ID, packet);
        // If merge succeeds, out should contain encrypted data; verify it's not null
        assertNotNull("Encrypted output should not be null", out);
    }

    @Test(expected = PacketDecryptionFailureException.class)
    public void encryptWhenCryptomanagerReturnsErrorThrows() throws Exception {
        byte[] packet = "p".getBytes();
        String ref = "ref";
        CryptomanagerResponseDto dto = new CryptomanagerResponseDto();
        List<ServiceError> errors = new ArrayList<>();
        ServiceError e = new ServiceError();
        e.setMessage("err");
        errors.add(e);
        dto.setErrors(errors);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class))).thenReturn(dto);

        service.encrypt(ref, packet);
    }

    @Test(expected = PacketDecryptionFailureException.class)
    public void encryptWhenHttpClientErrorThrows() throws Exception {
        byte[] packet = "p".getBytes();
        String ref = "ref";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        service.encrypt(ref, packet);
    }

    @Test
    public void decryptSuccessReturnsBytes() throws Exception {
        // craft a packet long enough to have nonce/aad/encrypted
        byte[] packet = new byte[128];
        CryptomanagerResponseDto dto = new CryptomanagerResponseDto();
        dto.setErrors(null);
        DecryptResponseDto dr = new DecryptResponseDto(CryptoUtil.encodeToURLSafeBase64("plain".getBytes()));
        dto.setResponse(dr);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class))).thenReturn(dto);

        byte[] out = service.decrypt("ref", packet);
        assertNotNull("Decrypted output should not be null", out);
    }

    @Test(expected = PacketDecryptionFailureException.class)
    public void decryptWhenCryptomanagerReturnsErrorThrows() throws Exception {
        byte[] packet = new byte[128];
        CryptomanagerResponseDto dto = new CryptomanagerResponseDto();
        List<ServiceError> errors = new ArrayList<>();
        ServiceError se = new ServiceError();
        se.setMessage("err");
        errors.add(se);
        dto.setErrors(errors);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class))).thenReturn(dto);

        service.decrypt("ref", packet);
    }

    @Test(expected = PacketDecryptionFailureException.class)
    public void decryptWhenDateTimeParseFailsThrows() throws Exception {
        // cause DateTimeParseException by setting invalid pattern
        ReflectionTestUtils.setField(service, "DATETIME_PATTERN", "invalid-pattern");
        byte[] packet = new byte[128];
        service.decrypt("ref", packet);
    }

    @Test
    public void verifySuccessAndFalseHandled() throws Exception {
        // getPublicKey
        Map<String, Object> pkResp = new LinkedHashMap<>();
        Map<String, Object> pkBody = new LinkedHashMap<>();
        pkBody.put("signingPublicKey", "pub");
        pkResp.put("response", pkBody);

        // verify response true
        Map<String, Object> verifyResp = new LinkedHashMap<>();
        Map<String, Object> verifyBody = new LinkedHashMap<>();
        verifyBody.put("verified", true);
        verifyResp.put("response", verifyBody);

        when(restTemplate.exchange(contains("localhost"), eq(HttpMethod.GET), isNull(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        when(mapper.readValue(anyString(), eq(LinkedHashMap.class))).thenReturn((LinkedHashMap) pkResp).thenReturn((LinkedHashMap) verifyResp);

        boolean ok = service.verify("10077_10077", "data".getBytes(), "sig".getBytes());
        assertTrue(ok);

        // now verified=false
        Map<String, Object> verifyFalseResp = new LinkedHashMap<>();
        Map<String, Object> vb2 = new LinkedHashMap<>();
        vb2.put("verified", false);
        verifyFalseResp.put("response", vb2);
        when(mapper.readValue(anyString(), eq(LinkedHashMap.class))).thenReturn((LinkedHashMap) pkResp).thenReturn((LinkedHashMap) verifyFalseResp);

        boolean res = service.verify("10077_10077", "data".getBytes(), "sig".getBytes());
        assertFalse(res);
    }

    @Test(expected = SignatureException.class)
    public void verifyWhenGetPublicKeyFailsThrows() throws Exception {
        ResponseEntity<String> resp = new ResponseEntity<>("hello", HttpStatus.OK);
        when(restTemplate.exchange(contains("localhost"), eq(HttpMethod.GET), isNull(), eq(String.class))).thenReturn(resp);
        when(mapper.readValue(anyString(), eq(LinkedHashMap.class))).thenReturn(new LinkedHashMap<>());

        service.verify("10077_10077", "data".getBytes(), "sig".getBytes());
    }

    @Test(expected = SignatureException.class)
    public void verifyWhenRestClientExceptionThrows() throws Exception {
        // Provide a valid refId with machineId to satisfy getPublicKey split()
        String refId = "10077_10077";

        // Stub GET call to return a response containing signingPublicKey so getPublicKey succeeds
        ResponseEntity<String> publicKeyResponse = new ResponseEntity<>("{}", HttpStatus.OK);
        when(restTemplate.exchange(contains("localhost"), eq(HttpMethod.GET), isNull(), eq(String.class)))
                .thenReturn(publicKeyResponse);
        LinkedHashMap<String, Object> pkResp = new LinkedHashMap<>();
        LinkedHashMap<String, Object> pkBody = new LinkedHashMap<>();
        pkBody.put("signingPublicKey", "pub");
        pkResp.put("response", pkBody);
        when(mapper.readValue(anyString(), eq(LinkedHashMap.class))).thenReturn(pkResp);

        // Now stub the POST verify call to throw RestClientException which should be wrapped as SignatureException
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("err"));

        service.verify(refId, "d".getBytes(), "s".getBytes());
    }

    @Test(expected = ApiNotAccessibleException.class)
    public void decryptWhenRestThrowsWrappedHttpClientThrowsApiNotAccessible() throws Exception {
        byte[] packet = new byte[128];
        RuntimeException wrapper = new RuntimeException(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class))).thenThrow(wrapper);
        service.decrypt("ref", packet);
    }

    @Test(expected = ApiNotAccessibleException.class)
    public void decryptWhenRestThrowsWrappedHttpServerThrowsApiNotAccessible() throws Exception {
        byte[] packet = new byte[128];
        RuntimeException wrapper = new RuntimeException(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class))).thenThrow(wrapper);
        service.decrypt("ref", packet);
    }
}
