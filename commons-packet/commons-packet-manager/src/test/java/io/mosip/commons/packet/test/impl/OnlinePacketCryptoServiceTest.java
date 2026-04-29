package io.mosip.commons.packet.test.impl;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.commons.packet.dto.ClientPublicKeyResponseDto;
import io.mosip.commons.packet.dto.TpmSignVerifyResponseDto;
import io.mosip.commons.packet.dto.packet.CryptomanagerResponseDto;
import io.mosip.commons.packet.dto.packet.DecryptResponseDto;
import io.mosip.commons.packet.exception.ApiNotAccessibleException;
import io.mosip.commons.packet.exception.PacketDecryptionFailureException;
import io.mosip.commons.packet.exception.SignatureException;
import io.mosip.commons.packet.impl.OnlinePacketCryptoServiceImpl;
import io.mosip.commons.packet.util.ZipUtils;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.cryptomanager.constant.CryptomanagerConstant;
import org.apache.commons.io.IOUtils;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeParseException;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.not;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ZipUtils.class, IOUtils.class, JsonUtils.class})
@PropertySource("classpath:application-test.properties")
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
public class OnlinePacketCryptoServiceTest {

    private static final String ID = "10001100770000320200720092256";

    @InjectMocks
    private OnlinePacketCryptoServiceImpl onlinePacketCryptoService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper mapper;

    @Before
    public void setup() {
        ReflectionTestUtils.setField(onlinePacketCryptoService, "DATETIME_PATTERN", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ReflectionTestUtils.setField(onlinePacketCryptoService, "APPLICATION_VERSION", "v1");
        ReflectionTestUtils.setField(onlinePacketCryptoService, "cryptomanagerDecryptUrl", "http://localhost");
        ReflectionTestUtils.setField(onlinePacketCryptoService, "cryptomanagerEncryptUrl", "http://localhost");
        ReflectionTestUtils.setField(onlinePacketCryptoService, "DATETIME_PATTERN", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ReflectionTestUtils.setField(onlinePacketCryptoService, "syncdataGetTpmKeyUrl", "http://localhost/");

    }

    @Test
    public void signTest() throws IOException {
        String expected = "signature";
        LinkedHashMap submap = new LinkedHashMap();
        submap.put("data", CryptoUtil.encodeToURLSafeBase64(expected.getBytes(StandardCharsets.UTF_8)));
        LinkedHashMap responseMap = new LinkedHashMap();
        responseMap.put("response", submap);
        ReflectionTestUtils.setField(onlinePacketCryptoService, "keymanagerCsSignUrl", "localhost");
        ResponseEntity<String> response = new ResponseEntity<>("hello", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class))).thenReturn(response);
        when(mapper.readValue(anyString(), any(Class.class))).thenReturn(responseMap);

        byte[] result = onlinePacketCryptoService.sign("packet".getBytes());
        assertTrue(Arrays.equals(expected.getBytes(), result));
    }

    @Test(expected = SignatureException.class)
    public void signExceptionTest() throws IOException {
        String expected = "signature";
        byte[] packet = "packet".getBytes();
        LinkedHashMap submap = new LinkedHashMap();
        submap.put("signature", expected);
        LinkedHashMap responseMap = new LinkedHashMap();
        responseMap.put("response", submap);
        ReflectionTestUtils.setField(onlinePacketCryptoService, "keymanagerCsSignUrl", "localhost");
        ResponseEntity<String> response = new ResponseEntity<>("hello", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class))).thenReturn(response);
        when(mapper.readValue(anyString(), any(Class.class))).thenThrow(new JsonMappingException("exception"));

        byte[] result = onlinePacketCryptoService.sign(packet);
    }

    @Test
    public void encryptTest() throws IOException {
        byte[] packet = "10001100770000320200720092256_packetwithsignatureandaad".getBytes();
        CryptomanagerResponseDto cryptomanagerResponseDto = new CryptomanagerResponseDto();
        cryptomanagerResponseDto.setErrors(null);
        DecryptResponseDto decryptResponseDto = new DecryptResponseDto("packet");
        cryptomanagerResponseDto.setResponse(decryptResponseDto);


        ReflectionTestUtils.setField(onlinePacketCryptoService, "cryptomanagerEncryptUrl", "localhost");
        ResponseEntity<String> response = new ResponseEntity<>("hello", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class))).thenReturn(response);
        when(mapper.readValue(anyString(), any(Class.class))).thenReturn(cryptomanagerResponseDto);

        byte[] result = onlinePacketCryptoService.encrypt(ID, packet);
        assertNotNull(result);
    }

    @Test(expected = PacketDecryptionFailureException.class)
    public void encryptExceptionTest() throws IOException {
        String expected = "signature";
        byte[] packet = "packet".getBytes();

        ReflectionTestUtils.setField(onlinePacketCryptoService, "cryptomanagerEncryptUrl", "localhost");

        when(restTemplate.exchange(anyString(), any(HttpMethod.class),
                any(HttpEntity.class), any(Class.class))).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        onlinePacketCryptoService.encrypt(ID, packet);
    }

    @Test
    public void decryptTest() throws IOException {
        byte[] packet = "10001100770000320200720092256_packetwithsignatureandaad".getBytes();
        CryptomanagerResponseDto cryptomanagerResponseDto = new CryptomanagerResponseDto();
        cryptomanagerResponseDto.setErrors(null);
        DecryptResponseDto decryptResponseDto = new DecryptResponseDto(CryptoUtil.encodeToURLSafeBase64("packet".getBytes()));
        cryptomanagerResponseDto.setResponse(decryptResponseDto);


        ReflectionTestUtils.setField(onlinePacketCryptoService, "cryptomanagerDecryptUrl", "localhost");
        ResponseEntity<String> response = new ResponseEntity<>("hello", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class))).thenReturn(response);
        when(mapper.readValue(anyString(), any(Class.class))).thenReturn(cryptomanagerResponseDto);

        byte[] result = onlinePacketCryptoService.decrypt(ID, packet);
        assertNotNull(result);
    }

    @Test(expected = PacketDecryptionFailureException.class)
    public void decryptExceptionTest() throws IOException {
        String expected = "signature";
        byte[] packet = "packet".getBytes();

        ReflectionTestUtils.setField(onlinePacketCryptoService, "cryptomanagerDecryptUrl", "localhost");

        when(restTemplate.exchange(anyString(), any(HttpMethod.class),
                any(HttpEntity.class), any(Class.class))).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        onlinePacketCryptoService.decrypt(ID, packet);
    }

    @Test
    public void verifyTest() throws IOException {
        String expected = "signature";
        byte[] packet = "packet".getBytes();
        
        LinkedHashMap submap = new LinkedHashMap();
        submap.put("verified", true);
        submap.put("encryptionPublicKey", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkK7cfIRc"
        		+ "b18uvtrQwajS9NElOzB6BRDZgy1BiumpAasKIf2kzUZfnctZqlIX1zkB1p6RDEaLeRoXHlPflz92kqMhfz5yZaDZFm7fV"
        		+ "mMO4TVjZXy2+8OmWW1EQTEFa7SQ9V8MTYWlaBSheWfUqCaCPiUjX0B8n8y1j4f8GdLagso/DBPc+zcqItmNTPbKhb606Jc"
        		+ "v6sSbu6N3HhhlnqGdsxmTradTnYYRYBNgRZ+tkmKlDjSAhOgnYpkRRvGBFI0hUYvm6fOgA7nUrqjc7xc8tSlk0ZJxr"
        		+ "ic++DZYEEigypYE+CWpQXlkmioMnMwi/WEwQfg88LNoxrrY238kE9nRbwIDAQAB");
        LinkedHashMap responseMap = new LinkedHashMap();
        responseMap.put("response", submap);
        
        ReflectionTestUtils.setField(onlinePacketCryptoService, "keymanagerCsverifysignUrl", "localhost");
        ReflectionTestUtils.setField(onlinePacketCryptoService, "syncdataGetTpmKeyUrl", "localhost");
        ResponseEntity<String> response = new ResponseEntity<>("hello", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class))).thenReturn(response);
        when(restTemplate.exchange("localhost"+"10077", HttpMethod.GET, null, String.class)).thenReturn(response);
        
        when(mapper.readValue(anyString(), any(Class.class))).thenReturn(responseMap);

        boolean result = onlinePacketCryptoService.verify("10077_10077",packet, expected.getBytes());
        assertTrue(result);
    }

    /**
     * Tests encrypt method when cryptomanager returns error response - should throw PacketDecryptionFailureException
     */
    @Test
    public void testEncrypt_WhenCryptomanagerReturnsError_ThrowsPacketDecryptionFailureException() throws IOException {
        byte[] packet = "test-packet".getBytes();
        String refId = "test-ref-id";

        CryptomanagerResponseDto errorResponse = new CryptomanagerResponseDto();
        List<ServiceError> errors = new ArrayList<>();
        ServiceError serviceError = new ServiceError();
        serviceError.setMessage("Encryption failed due to invalid data");
        errors.add(serviceError);
        errorResponse.setErrors(errors);

        ResponseEntity<String> response = new ResponseEntity<>("error-response", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(response);
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenReturn(errorResponse);

        assertThrows(PacketDecryptionFailureException.class, () -> {
            onlinePacketCryptoService.encrypt(refId, packet);
        });
    }

    /**
     * Tests encrypt method when response is null - should throw PacketDecryptionFailureException
     */
    @Test
    public void testEncrypt_WhenResponseIsNull_ThrowsPacketDecryptionFailureException() throws IOException {
        byte[] packet = "test-packet".getBytes();
        String refId = "test-ref-id";

        CryptomanagerResponseDto nullResponse = new CryptomanagerResponseDto();
        nullResponse.setResponse(null);
        nullResponse.setErrors(null);

        ResponseEntity<String> response = new ResponseEntity<>("null-response", HttpStatus.OK);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(response);
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenReturn(nullResponse);

        assertThrows(PacketDecryptionFailureException.class, () -> {
            onlinePacketCryptoService.encrypt(refId, packet);
        });
    }

    /**
     * Tests encrypt method when HTTP client error occurs - should throw PacketDecryptionFailureException
     */
    @Test
    public void testEncrypt_WhenHttpClientError_ThrowsPacketDecryptionFailureException() throws IOException {
        byte[] packet = "test-packet".getBytes();
        String refId = "test-ref-id";

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

        assertThrows(PacketDecryptionFailureException.class, () -> {
            onlinePacketCryptoService.encrypt(refId, packet);
        });
    }

    /**
     * Tests verify method when REST client exception occurs - should throw SignatureException
     */
    @Test(expected = SignatureException.class)
    public void testVerify_WhenRestClientException_ThrowsSignatureException() throws IOException {
        String refId = "10077_10077";
        byte[] packet = "packet".getBytes();
        byte[] signature = "signature".getBytes();

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), any(Class.class)))
                .thenThrow(new RestClientException("Rest client error"));

        onlinePacketCryptoService.verify(refId, packet, signature);
    }

    /**
     * Tests decrypt method when date time parse exception occurs - should throw PacketDecryptionFailureException
     */
    @Test(expected = PacketDecryptionFailureException.class)
    public void testDecrypt_WhenDateTimeParseException_ThrowsPacketDecryptionFailureException() throws IOException {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        doThrow(new DateTimeParseException("Invalid date", "2023-13-45", 0)).when(mapper).readValue(anyString(), any(Class.class));

        onlinePacketCryptoService.decrypt(ID, "packet".getBytes());
    }

    /**
     * Tests decrypt method when cryptomanager returns error response - should throw PacketDecryptionFailureException
     */
    @Test
    public void testDecrypt_WhenCryptomanagerReturnsError_ThrowsPacketDecryptionFailureException() throws IOException {
        CryptomanagerResponseDto errorResponse = new CryptomanagerResponseDto();
        List<ServiceError> errors = new ArrayList<>();
        ServiceError error = new ServiceError();
        error.setMessage("Decryption failed");
        errors.add(error);
        errorResponse.setErrors(errors);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenReturn(errorResponse);

        assertThrows(PacketDecryptionFailureException.class, () -> {
            onlinePacketCryptoService.decrypt(ID, "packet".getBytes());
        });
    }

    /**
     * Tests encrypt method when IO exception occurs - should throw PacketDecryptionFailureException
     */
    @Test(expected = PacketDecryptionFailureException.class)
    public void testEncrypt_WhenIOException_ThrowsPacketDecryptionFailureException() throws Exception {
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenThrow(new RuntimeException(new IOException("IO error")));

        onlinePacketCryptoService.encrypt("refId", "test".getBytes());
    }

    /**
     * Tests encrypt method when date time parse exception occurs - should throw PacketDecryptionFailureException
     */
    @Test(expected = PacketDecryptionFailureException.class)
    public void testEncrypt_WhenDateTimeParseException_ThrowsPacketDecryptionFailureException() throws Exception {
        ReflectionTestUtils.setField(onlinePacketCryptoService, "DATETIME_PATTERN", "invalid-pattern");

        onlinePacketCryptoService.encrypt("refId", "test".getBytes());
    }

    /**
     * Tests encrypt method when cryptomanager returns error in response - should throw PacketDecryptionFailureException
     */
    @Test(expected = PacketDecryptionFailureException.class)
    public void testEncrypt_WhenErrorInResponse_ThrowsPacketDecryptionFailureException() throws Exception {
        CryptomanagerResponseDto responseDto = new CryptomanagerResponseDto();
        ServiceError error = new ServiceError("ERROR_CODE", "Error message");
        responseDto.setErrors(Lists.newArrayList(error));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenReturn(responseDto);

        onlinePacketCryptoService.encrypt("refId", "test".getBytes());
    }

    /**
     * Tests encrypt method when HTTP client error exception occurs - should throw ApiNotAccessibleException
     */
    @Test(expected = ApiNotAccessibleException.class)
    public void testEncrypt_WhenHttpClientErrorException_ThrowsApiNotAccessibleException() throws Exception {
        HttpClientErrorException clientException = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");
        RuntimeException wrapperException = new RuntimeException(clientException);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(wrapperException);

        onlinePacketCryptoService.encrypt("refId", "test".getBytes());
    }

    /**
     * Tests encrypt method when HTTP server error exception occurs - should throw ApiNotAccessibleException
     */
    @Test(expected = ApiNotAccessibleException.class)
    public void testEncrypt_WhenHttpServerErrorException_ThrowsApiNotAccessibleException() throws Exception {
        HttpServerErrorException serverException = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error");
        RuntimeException wrapperException = new RuntimeException(serverException);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(wrapperException);

        onlinePacketCryptoService.encrypt("refId", "test".getBytes());
    }

    /**
     * Tests decrypt method when IO exception occurs - should throw PacketDecryptionFailureException
     */
    @Test(expected = PacketDecryptionFailureException.class)
    public void testDecrypt_WhenIOException_ThrowsPacketDecryptionFailureException() throws Exception {
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenThrow(new RuntimeException(new IOException("IO error")));

        byte[] packet = new byte[32];
        onlinePacketCryptoService.decrypt("refId", packet);
    }

    /**
     * Tests decrypt method when cryptomanager returns error in response - should throw PacketDecryptionFailureException
     */
    @Test(expected = PacketDecryptionFailureException.class)
    public void testDecrypt_WhenErrorInResponse_ThrowsPacketDecryptionFailureException() throws Exception {
        CryptomanagerResponseDto responseDto = new CryptomanagerResponseDto();
        ServiceError error = new ServiceError("ERROR_CODE", "Error message");
        responseDto.setErrors(Lists.newArrayList(error));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenReturn(responseDto);

        byte[] packet = new byte[32];
        onlinePacketCryptoService.decrypt("refId", packet);
    }

    /**
     * Tests verify method when public key response is empty - should throw SignatureException
     */
    @Test(expected = SignatureException.class)
    public void testVerify_WhenPublicKeyResponseEmpty_ThrowsSignatureException() throws Exception {
        LinkedHashMap<String, Object> emptyResponse = new LinkedHashMap<>();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(LinkedHashMap.class)))
                .thenReturn(emptyResponse);

        onlinePacketCryptoService.verify("center_machine", "data".getBytes(), "signature".getBytes());
    }

    /**
     * Tests encrypt method when response data is null - should return null
     */
    @Test
    public void testEncrypt_WhenResponseDataIsNull_ReturnsNull() throws IOException {
        CryptomanagerResponseDto responseDto = new CryptomanagerResponseDto();
        DecryptResponseDto decryptResponse = new DecryptResponseDto();
        decryptResponse.setData(null);
        responseDto.setResponse(decryptResponse);
        responseDto.setErrors(null);

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        when(mapper.readValue(anyString(), eq(CryptomanagerResponseDto.class)))
                .thenReturn(responseDto);

        byte[] result = onlinePacketCryptoService.encrypt("refId", "test".getBytes());
        assertNull(result);
    }

    /**
     * Tests verify method when verify response is empty - should throw SignatureException
     */
    @Test(expected = SignatureException.class)
    public void testVerify_WhenVerifyResponseEmpty_ThrowsSignatureException() throws IOException {
        LinkedHashMap<String, Object> publicKeyResponse = new LinkedHashMap<>();
        publicKeyResponse.put("signingPublicKey", "publicKey");

        LinkedHashMap<String, Object> emptyVerifyResponse = new LinkedHashMap<>();

        when(restTemplate.exchange(contains("machine"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));
        when(restTemplate.exchange(not(contains("machine")).toString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>("response", HttpStatus.OK));

        when(mapper.readValue(anyString(), eq(LinkedHashMap.class)))
                .thenReturn(publicKeyResponse)
                .thenReturn(emptyVerifyResponse);

        onlinePacketCryptoService.verify("center_machine", "data".getBytes(), "signature".getBytes());
    }
}
