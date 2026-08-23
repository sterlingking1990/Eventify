package com.example.PTicketing.service;

import com.example.PTicketing.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests — no Spring context, no database. The signing service is
 * pure logic over one injected string, so that is all it needs.
 */
class QrSigningServiceTest {

    private static final String SECRET = "test-signing-secret";
    private static final Long EVENT_ID = 42L;

    private QrSigningService service;

    @BeforeEach
    void setUp() {
        service = new QrSigningService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
    }

    @Test
    void mintProducesVerifiableTokenBoundToItsEvent() {
        String token = service.mint(EVENT_ID);

        QrSigningService.ParsedQr parsed = service.parse(token);

        assertTrue(parsed.signed());
        assertEquals(EVENT_ID, parsed.eventId());
    }

    @Test
    void mintIsUniquePerCall() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(service.mint(EVENT_ID));
        }
        assertEquals(100, tokens.size());
    }

    @Test
    void tamperedEventIdFailsSignature() {
        String token = service.mint(42L);
        String forged = token.replaceFirst("^V1\\.42\\.", "V1.43.");

        // The event id is inside the signed payload: flipping it must break the
        // signature rather than silently rebind the code to another event.
        assertThrows(BadRequestException.class, () -> service.parse(forged));
    }

    @Test
    void wrongSecretMintsTokensTheRightSecretRejects() {
        QrSigningService impostor = new QrSigningService();
        ReflectionTestUtils.setField(impostor, "secret", "another-secret");
        String foreignToken = impostor.mint(EVENT_ID);

        assertThrows(BadRequestException.class, () -> service.parse(foreignToken));
    }

    @Test
    void malformedSignedFormatIsRejectedNotTreatedAsLegacy() {
        assertThrows(BadRequestException.class, () -> service.parse("V1.not-a-number.nonce.deadbeef"));
        assertThrows(BadRequestException.class, () -> service.parse("V1.42.only-three-parts"));
        assertThrows(BadRequestException.class, () -> service.parse("V1."));
        assertThrows(BadRequestException.class, () -> service.parse(""));
        assertThrows(BadRequestException.class, () -> service.parse(null));
    }

    @Test
    void bareUuidClassifiesAsLegacyWithoutThrowing() {
        QrSigningService.ParsedQr parsed = service.parse("550e8400-e29b-41d4-a716-446655440000");

        assertFalse(parsed.signed());
        assertNull(parsed.eventId());
    }

    @Test
    void unsetSecretFallsBackToLegacyUuid() {
        QrSigningService unconfigured = new QrSigningService();
        ReflectionTestUtils.setField(unconfigured, "secret", "");

        String code = unconfigured.mint(EVENT_ID);
        QrSigningService.ParsedQr parsed = service.parse(code);

        assertFalse(parsed.signed());
        assertNull(parsed.eventId());
        assertTrue(code.matches("[0-9a-f-]{36}"));
    }
}
