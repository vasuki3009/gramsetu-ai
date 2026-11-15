package com.gramsetu.service;

import com.gramsetu.config.TwilioConfig;
import com.gramsetu.util.LogSafe;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

/**
 * Sends WhatsApp messages through the Twilio API.
 *
 * Kept separate from the webhook controller so the controller can be tested with a mock and so the
 * "never throw" rule lives in one place: Twilio must always receive 200 for the inbound webhook,
 * so a failed outbound message is logged (with a masked number) and swallowed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsAppMessageSender {

    private final TwilioConfig twilioConfig;

    /**
     * @param to       recipient in Twilio format, e.g. {@code whatsapp:+91...}
     * @param text     message body (may be empty when sending media only)
     * @param mediaUrl public HTTPS URL of an attachment, or null/blank for a text-only message
     */
    public void send(String to, String text, String mediaUrl) {
        try {
            var creator = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(twilioConfig.getWhatsappFrom()),
                    text);
            boolean hasMedia = mediaUrl != null && !mediaUrl.isBlank();
            if (hasMedia) {
                creator.setMediaUrl(List.of(URI.create(mediaUrl)));
            }
            Message message = creator.create();
            log.info("[WhatsApp] Reply{}sent to {} — SID: {}",
                     hasMedia ? " + audio " : " ", LogSafe.phone(to), message.getSid());
        } catch (Exception e) {
            log.error("[WhatsApp] Failed to send reply to {}: {}", LogSafe.phone(to), LogSafe.error(e));
        }
    }
}
