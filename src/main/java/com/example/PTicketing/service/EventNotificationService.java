package com.example.PTicketing.service;

import com.example.PTicketing.entity.Event;
import com.example.PTicketing.entity.User;
import com.example.PTicketing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventNotificationService {

    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void notifyUsersAboutEvent(Event event, Long organizerId) {
        List<User> users = userRepository.findAll();

        List<String> recipientEmails = users.stream()
                .filter(u -> u.getId() != null && !u.getId().equals(organizerId))
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .toList();

        if (recipientEmails.isEmpty()) {
            log.info("No users to notify for event: {}", event.getTitle());
            return;
        }

        String subject = "New Event: " + event.getTitle();
        String body = buildEmailBody(event);

        int sent = 0;
        for (String email : recipientEmails) {
            try {
                emailService.sendSimpleEmail(email, subject, body);
                sent++;
                log.info("Notification email sent to {} for event {}", email, event.getTitle());
            } catch (Exception e) {
                log.error("Failed to send notification email to {} for event {}: {}",
                        email, event.getTitle(), e.getMessage());
            }
        }

        log.info("Finished sending {}/{} notification emails for event: {}",
                sent, recipientEmails.size(), event.getTitle());
    }

    private String buildEmailBody(Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi there,\n\n");
        sb.append("Eventify has a new event for you!\n\n");
        sb.append("Event: ").append(event.getTitle()).append("\n");

        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            String desc = event.getDescription();
            if (desc.length() > 200) {
                desc = desc.substring(0, 200) + "...";
            }
            sb.append("Description: ").append(desc).append("\n");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a");
        if (event.getStartDate() != null) {
            sb.append("Date: ").append(event.getStartDate().format(formatter)).append("\n");
        }

        if (event.getVenue() != null && !event.getVenue().isBlank()) {
            sb.append("Venue: ").append(event.getVenue()).append("\n");
        }

        sb.append("\nView event: ").append(frontendUrl).append("/events/").append(event.getSlug()).append("\n\n");
        sb.append("Log in to find out if it's close to you!\n\n");
        sb.append("You're receiving this because you have an account on Eventify.\n");
        return sb.toString();
    }
}
