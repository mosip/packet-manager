package io.mosip.commons.packet.test.impl;

import io.mosip.commons.packet.constants.CryptomanagerConstant;
import io.mosip.commons.packet.impl.OfflinePacketCryptoServiceImpl;
import io.mosip.kernel.clientcrypto.dto.TpmSignResponseDto;
import io.mosip.kernel.clientcrypto.dto.TpmSignVerifyResponseDto;
import io.mosip.kernel.clientcrypto.service.spi.ClientCryptoManagerService;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.cryptomanager.dto.CryptomanagerResponseDto;
import io.mosip.kernel.cryptomanager.service.impl.CryptomanagerServiceImpl;
import io.mosip.kernel.signature.service.SignatureService;
import io.mosip.kernel.signature.service.impl.SignatureServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.Silent.class)
@PropertySource("classpath:application-test.properties")
public class OfflinePacketCryptoServiceTest {

    @InjectMocks
    private OfflinePacketCryptoServiceImpl offlinePacketCryptoService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private CryptomanagerServiceImpl cryptomanagerService;

    @Mock
    private ClientCryptoManagerService clientCryptoManagerService;

    @Mock
    private SignatureServiceImpl signature_service;

    @Before
    public void setup() {
        Mockito.when(applicationContext.getBean(CryptomanagerServiceImpl.class)).thenReturn(cryptomanagerService);
        Mockito.when(applicationContext.getBean(ClientCryptoManagerService.class)).thenReturn(clientCryptoManagerService);
        Mockito.when(applicationContext.getBean(SignatureService.class)).thenReturn(signature_service);
        ReflectionTestUtils.setField(offlinePacketCryptoService, "DATETIME_PATTERN", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    }

    @Test
    public void signTest() {
        String packetSignature = "signature";
        TpmSignResponseDto signatureResponse = new TpmSignResponseDto();
        signatureResponse.setData(CryptoUtil.encodeToURLSafeBase64(packetSignature.getBytes(StandardCharsets.UTF_8)));

        Mockito.when(clientCryptoManagerService.csSign(any())).thenReturn(signatureResponse);

        byte[] result = offlinePacketCryptoService.sign(packetSignature.getBytes());
        assertArrayEquals(packetSignature.getBytes(), result);
    }

    @Test(expected = NullPointerException.class)
    public void signWhenCsSignReturnsNullThrowsNullPointerException() {
        Mockito.when(clientCryptoManagerService.csSign(any())).thenReturn(null);
        offlinePacketCryptoService.sign("packet".getBytes());
    }

    @Test
    public void signWhenCsSignReturnsNullDataThrowsException() {
        TpmSignResponseDto signatureResponse = new TpmSignResponseDto();
        signatureResponse.setData(null);
        Mockito.when(clientCryptoManagerService.csSign(any())).thenReturn(signatureResponse);
        try {
            offlinePacketCryptoService.sign("packet".getBytes());
            org.junit.Assert.fail("Expected NullPointerException or IllegalArgumentException");
        } catch (Exception e) {
            // Accept either NPE or IllegalArgumentException depending on CryptoUtil behaviour
            // (catch kept intentionally broad for test tolerance)
            assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void encryptTest() {
        String id = "10001100770000320200720092256";
        String response = "packet";
        byte[] packet = "packet".getBytes();
        CryptomanagerResponseDto cryptomanagerResponseDto = new CryptomanagerResponseDto();
        cryptomanagerResponseDto.setData(response);
        Mockito.when(cryptomanagerService.encrypt(any())).thenReturn(cryptomanagerResponseDto);

        byte[] result = offlinePacketCryptoService.encrypt(id, packet);
        assertNotNull(result);
    }

    @Test
    public void encryptReturnsMergedBytesWhenCryptomanagerReturnsData() {
        String id = "refId";
        byte[] packet = "plain".getBytes();
        byte[] encrypted = "encryptedBytes".getBytes();
        String encBase64 = CryptoUtil.encodeToURLSafeBase64(encrypted);

        CryptomanagerResponseDto cryptomanagerResponseDto = new CryptomanagerResponseDto();
        cryptomanagerResponseDto.setData(encBase64);
        Mockito.when(cryptomanagerService.encrypt(any())).thenReturn(cryptomanagerResponseDto);

        // call real mergeEncryptedData from khazana (library present on classpath) and assert not null
        byte[] result = offlinePacketCryptoService.encrypt(id, packet);
        assertNotNull(result);
    }

    @Test(expected = NullPointerException.class)
    public void encryptWhenCryptomanagerReturnsNullThrowsNullPointerException() {
        Mockito.when(cryptomanagerService.encrypt(any())).thenReturn(null);
        offlinePacketCryptoService.encrypt("id", "p".getBytes());
    }

    @Test
    public void decryptTest() {
        String id = "10001100770000320200720092256";
        String response = "10001100770000320200720092256_packetwithsignatureandaad";
        byte[] packet = "10001100770000320200720092256_packetwithsignatureandaad".getBytes();
        CryptomanagerResponseDto cryptomanagerResponseDto = new CryptomanagerResponseDto();
        cryptomanagerResponseDto.setData(response);
        Mockito.when(cryptomanagerService.decrypt(any())).thenReturn(cryptomanagerResponseDto);

        byte[] result = offlinePacketCryptoService.decrypt(id, packet);
        assertNotNull(result);
    }

    @Test
    public void decryptWithShortPacketCallsDecryptWithEmptyEncryptedData() {
        String id = "10001100770000320200720092256";
        // create packet with exactly nonce + aad (no encrypted data)
        int len = CryptomanagerConstant.GCM_NONCE_LENGTH + CryptomanagerConstant.GCM_AAD_LENGTH;
        byte[] shortPacket = new byte[len];

        CryptomanagerResponseDto cryptomanagerResponseDto = new CryptomanagerResponseDto();
        cryptomanagerResponseDto.setData(CryptoUtil.encodeToURLSafeBase64("result".getBytes()));
        Mockito.when(cryptomanagerService.decrypt(any())).thenReturn(cryptomanagerResponseDto);

        byte[] result = offlinePacketCryptoService.decrypt(id, shortPacket);
        assertArrayEquals("result".getBytes(), result);
    }

    @Test(expected = NullPointerException.class)
    public void decryptWhenCryptomanagerReturnsNullThrowsNullPointerException() {
        Mockito.when(cryptomanagerService.decrypt(any())).thenReturn(null);
        byte[] packet = new byte[CryptomanagerConstant.GCM_NONCE_LENGTH + CryptomanagerConstant.GCM_AAD_LENGTH + 5];
        offlinePacketCryptoService.decrypt("id", packet);
    }

    @Test
    public void verifyTest() {
        String packetSignature = "signature";

        TpmSignVerifyResponseDto tpmSignVerifyResponseDto = new TpmSignVerifyResponseDto();
        tpmSignVerifyResponseDto.setVerified(true);
        Mockito.when(clientCryptoManagerService.csVerify(any())).thenReturn(tpmSignVerifyResponseDto);

        byte[] pkt = packetSignature.getBytes();
        boolean result = offlinePacketCryptoService.verify("12345", pkt, packetSignature.getBytes());
        assertTrue(result);
    }

    @Test
    public void verifyReturnsFalseWhenCsVerifyReturnsVerifiedFalse() {
        TpmSignVerifyResponseDto tpmSignVerifyResponseDto = new TpmSignVerifyResponseDto();
        tpmSignVerifyResponseDto.setVerified(false);
        Mockito.when(clientCryptoManagerService.csVerify(any())).thenReturn(tpmSignVerifyResponseDto);

        boolean result = offlinePacketCryptoService.verify("12345","packet".getBytes(), "sig".getBytes());
        assertFalse(result);
    }

    @Test(expected = NullPointerException.class)
    public void verifyWhenCsVerifyReturnsNullThrowsNullPointerException() {
        Mockito.when(clientCryptoManagerService.csVerify(any())).thenReturn(null);
        offlinePacketCryptoService.verify("12345","packet".getBytes(), "sig".getBytes());
    }

    /**
     * Tests getCryptomanagerService method when service is null - should create and return new instance
     */
    @Test
    public void testGetCryptomanagerServiceWhenServiceIsNullCreatesAndReturnsNewInstance() {
        ReflectionTestUtils.setField(offlinePacketCryptoService, "cryptomanagerService", null);
        CryptomanagerServiceImpl result = ReflectionTestUtils.invokeMethod(offlinePacketCryptoService, "getCryptomanagerService");
        assertNotNull(result);
        assertEquals(cryptomanagerService, result);

        result = ReflectionTestUtils.invokeMethod(offlinePacketCryptoService, "getCryptomanagerService");
        assertEquals(cryptomanagerService, result);
    }

    /**
     * Tests getSignatureService method when service is null - should create and return new instance
     */
    @Test
    public void testGetSignatureServiceWhenServiceIsNullCreatesAndReturnsNewInstance() {
        ReflectionTestUtils.setField(offlinePacketCryptoService, "signatureService", null);
        SignatureService result = ReflectionTestUtils.invokeMethod(offlinePacketCryptoService, "getSignatureService");
        assertNotNull(result);
        assertEquals(signature_service, result);

        result = ReflectionTestUtils.invokeMethod(offlinePacketCryptoService, "getSignatureService");
        assertEquals(signature_service, result);
    }

    /**
     * Tests getTpmCryptoService method when service is null - should create and return new instance
     */
    @Test
    public void testGetTpmCryptoServiceWhenServiceIsNullCreatesAndReturnsNewInstance() {
        ReflectionTestUtils.setField(offlinePacketCryptoService, "tpmCryptoService", null);
        ClientCryptoManagerService result = ReflectionTestUtils.invokeMethod(offlinePacketCryptoService, "getTpmCryptoService");
        assertNotNull(result);
        assertEquals(clientCryptoManagerService, result);

        result = ReflectionTestUtils.invokeMethod(offlinePacketCryptoService, "getTpmCryptoService");
        assertEquals(clientCryptoManagerService, result);
    }
}
