package com.example.PTicketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Publishes QR codes as hosted PNGs in Supabase Storage.
 *
 * <p>The web checkout emails tickets, so an inline image suffices there. Message
 * channels need a fetchable URL instead, which is what this provides.
 *
 * <p>Uses Storage rather than an image CDN deliberately: a 512px QR needs no
 * transformations, and the project already exists, so this avoids a third-party
 * account holding one asset type.
 *
 * <p>The bucket must be <b>public</b>. Signed URLs expire, and a ticket bought
 * today has to open at the gate weeks later. That is not a downgrade — a ticket
 * is protected by its {@code qrCode} UUID being unguessable and by check-in
 * marking it used, not by the image being unreachable. Anyone holding the image
 * already holds the code, and anyone holding the code has no need of the image.
 *
 * <p>Failures return null rather than throwing: a ticket whose image failed to
 * upload is still a valid ticket, so this must never be why a paid order fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrHostingService {

    private static final int QR_SIZE_PX = 512;

    private final RestTemplate restTemplate;
    private final QrCodeService qrCodeService;

    @Value("${storage.supabase.url:}")
    private String storageUrl;

    @Value("${storage.supabase.service-key:}")
    private String serviceKey;

    @Value("${storage.supabase.bucket:tickets}")
    private String bucket;

    /**
     * Renders {@code qrText} as a PNG, uploads it, and returns its public URL.
     *
     * @param objectName stable name for the object, so a re-upload replaces
     *                   rather than duplicates
     * @return the public HTTPS URL, or null if unconfigured or the upload failed
     */
    public String publish(String qrText, String objectName) {
        if (storageUrl == null || storageUrl.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            log.warn("Supabase Storage is not configured (storage.supabase.url / .service-key) — " +
                     "ticket {} has no hosted QR image; delivery will fall back to sending the code as text",
                     objectName);
            return null;
        }

        String base = storageUrl.replaceAll("/+$", "");
        String path = objectName + ".png";

        try {
            byte[] png = qrCodeService.generateQrImage(qrText, QR_SIZE_PX, QR_SIZE_PX);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(serviceKey);
            headers.setContentType(MediaType.IMAGE_PNG);
            // Makes the upload idempotent — re-issuing the same ticket overwrites
            // its image instead of failing on "already exists".
            headers.add("x-upsert", "true");

            restTemplate.exchange(
                    base + "/storage/v1/object/" + bucket + "/" + path,
                    HttpMethod.POST,
                    new HttpEntity<>(png, headers),
                    String.class
            );

            return base + "/storage/v1/object/public/" + bucket + "/" + path;

        } catch (Exception e) {
            log.warn("QR upload failed for {} — ticket remains valid, image unavailable: {}",
                    objectName, e.getMessage());
            return null;
        }
    }
}
