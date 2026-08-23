package com.example.PTicketing.service;

import com.example.PTicketing.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Mints and verifies event-bound QR codes.
 *
 * <p>A signed code is {@code V1.<eventId>.<nonce>.<hmac>} where the HMAC is
 * SHA-256 over everything before it, keyed by {@code qr.signing-secret}. The
 * event binding means a code leaked at one gate cannot pass at another: the
 * scan path checks the signature and the event match before it trusts the
 * value, so a stolen token is only ever worth what it was minted for.
 *
 * <p>Codes minted before signing existed are bare UUIDs with no structure.
 * They remain valid — parse() classifies anything not in V1 form as legacy,
 * and those are verified by exact database match, which was always their only
 * protection. New and old codes can coexist on the same ticket type because
 * the full string is stored either way; nothing is re-encoded after the fact.
 *
 * <p>An unset secret degrades to unsigned legacy codes rather than failing
 * startup, so local development works out of the box. That degradation is
 * logged loudly once per instance; deployment is expected to set
 * QR_SIGNING_SECRET (see DEPLOY.md).
 */
@Slf4j
@Service
public class QrSigningService {

    private static final String PREFIX = "V1";
    private static final int TOKEN_PARTS = 4;

    @Value("${qr.signing-secret:}")
    private String secret;

    private volatile boolean warnedUnset;

    @PostConstruct
    void warnIfUnconfigured() {
        if (isUnconfigured()) {
            warnedUnset = true;
            log.warn("qr.signing-secret is not configured — new QR codes will be "
                    + "unsigned random UUIDs with no event binding. Set QR_SIGNING_SECRET.");
        }
    }

    /** What a scanned string turned out to be. */
    public record ParsedQr(Long eventId, boolean signed) {
        public static ParsedQr legacy() {
            return new ParsedQr(null, false);
        }
    }

    /**
     * Produces the opaque value stored on a ticket and encoded into its image.
     *
     * <p>The nonce is the unguessable part; the signature exists to prove the
     * pair (event, nonce) was issued by this system and for that event only.
     */
    public String mint(Long eventId) {
        if (isUnconfigured()) {
            if (!warnedUnset) {
                warnedUnset = true;
                log.warn("Minting an UNSIGNED QR code for event {} — qr.signing-secret is not set", eventId);
            }
            return UUID.randomUUID().toString();
        }

        String nonce = UUID.randomUUID().toString().replace("-", "");
        return sign(eventId, nonce);
    }

    /**
     * Classifies a scanned code without touching the database.
     *
     * @throws BadRequestException when the string claims the signed format but
     *         fails any part of it — wrong prefix, wrong field count, unparsable
     *         event id, or a signature that does not verify. A tampered token is
     *         refused here so it never reaches a ticket lookup.
     */
    public ParsedQr parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Invalid QR code");
        }

        String[] parts = raw.split("\\.", -1);
        if (!PREFIX.equals(parts[0])) {
            return ParsedQr.legacy();
        }
        if (parts.length != TOKEN_PARTS) {
            throw new BadRequestException("Invalid QR code");
        }

        long eventId;
        try {
            eventId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid QR code");
        }

        if (!MessageDigest.isEqual(
                hmac(eventId, parts[2]).getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequestException("Invalid QR code");
        }

        return new ParsedQr(eventId, true);
    }

    private String sign(Long eventId, String nonce) {
        return PREFIX + "." + eventId + "." + nonce + "." + hmac(eventId, nonce);
    }

    private String hmac(long eventId, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((PREFIX + "." + eventId + "." + nonce).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 ships with every JDK; a bad key length is the only
            // realistic failure and HMAC accepts any key size, so neither happens.
            throw new IllegalStateException("QR signing failed", e);
        }
    }

    private boolean isUnconfigured() {
        return secret == null || secret.isBlank();
    }
}
