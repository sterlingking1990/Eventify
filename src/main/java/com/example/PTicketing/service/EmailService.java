package com.example.PTicketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * The address recipients see, which is not always the SMTP login.
     *
     * <p>With Gmail the two are the same. With a relay such as Resend the login is
     * a fixed literal ({@code resend}) and the API key is the password, so using
     * the username as the sender would produce an invalid From and every send would
     * be rejected.
     *
     * <p>The fallback to MAIL_USERNAME lives in application.properties rather than
     * here: the property is always defined, so a default on this annotation would
     * never fire. Accepts a bare address or a display form —
     * {@code Eventify <tickets@example.com>}.
     */
    @Value("${mail.from:}")
    private String fromAddress;

    /**
     * Where replies go. A ticket email invites questions — "I didn't get my QR",
     * "can I transfer this?" — and sending from an address nobody monitors means
     * those bounce or vanish. Leave unset to let replies follow the From address.
     */
    @Value("${mail.reply-to:}")
    private String replyTo;

    /**
     * Sends a plain-text email.
     *
     * <p>Returns false rather than throwing when mail is unconfigured or the send
     * fails. Callers issue tickets first and notify second: a payment has already
     * succeeded by this point, so a mail problem must never unwind it.
     *
     * @return true if the message was handed to the mail server
     */
    public boolean sendSimpleEmail(String to, String subject, String body) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("Mail is not configured (mail.from / spring.mail.username are empty) — " +
                     "skipping '{}' to {}", subject, to);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            if (replyTo != null && !replyTo.isBlank()) {
                message.setReplyTo(replyTo);
            }

            mailSender.send(message);
            log.info("Sent '{}' to {}", subject, to);
            return true;

        } catch (Exception e) {
            // Loud, because the buyer is now expecting an email that will not arrive.
            // Tickets remain valid and retrievable, so this is recoverable by resend.
            log.error("Failed to send '{}' to {}: {}", subject, to, e.getMessage());
            return false;
        }
    }
}
