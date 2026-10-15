package com.krugworld.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Plain-text email over whatever SMTP server SPRING_MAIL_* points at (Gmail
 * with an app password works). Plain text on purpose: nothing to render, and
 * the link can't be hidden behind misleading link text.
 */
@Component
public class SmtpResetMailer implements ResetMailer {

    private final JavaMailSender sender;
    private final String from;
    private final long ttlMinutes;

    public SmtpResetMailer(ObjectProvider<JavaMailSender> sender,
                           @Value("${krugworld.mail.from:}") String from,
                           @Value("${krugworld.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.sender = sender.getIfAvailable();
        this.from = from;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public boolean available() {
        // A sender bean exists even when SPRING_MAIL_HOST is set to an empty
        // string, so look at the host itself.
        return StringUtils.hasText(from) && sender instanceof JavaMailSenderImpl impl
                && StringUtils.hasText(impl.getHost());
    }

    @Override
    public void sendReset(String to, String username, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset your KrugWorld passphrase");
        message.setText("""
                Greetings, %s.

                Someone asked to reset the passphrase for your KrugWorld account.
                To choose a new one, open this link within %d minutes:

                %s

                If that wasn't you, ignore this email: nothing changes until the link is used.
                """.formatted(username, ttlMinutes, link));
        sender.send(message);
    }
}
