package com.gramsetu.service;

import com.gramsetu.config.TwilioConfig;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The sender must build the right Twilio request and must never throw. */
class WhatsAppMessageSenderTest {

    private TwilioConfig twilioConfig;
    private WhatsAppMessageSender sender;

    @BeforeEach
    void setUp() {
        twilioConfig = mock(TwilioConfig.class);
        when(twilioConfig.getWhatsappFrom()).thenReturn("whatsapp:+10000000000");
        sender = new WhatsAppMessageSender(twilioConfig);
    }

    @Test
    void textOnlyMessage_isCreatedWithoutMedia() {
        try (MockedStatic<Message> twilio = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            Message created = mock(Message.class);
            when(created.getSid()).thenReturn("SMtest");
            when(creator.create()).thenReturn(created);
            twilio.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                  .thenReturn(creator);

            sender.send("whatsapp:+10000000001", "hello", null);

            twilio.verify(() -> Message.creator(
                    new PhoneNumber("whatsapp:+10000000001"), new PhoneNumber("whatsapp:+10000000000"), "hello"));
            verify(creator, never()).setMediaUrl(ArgumentMatchers.<List<URI>>any());
            verify(creator).create();
        }
    }

    @Test
    void mediaMessage_attachesTheUrl() {
        try (MockedStatic<Message> twilio = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            Message created = mock(Message.class);
            when(creator.create()).thenReturn(created);
            twilio.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                  .thenReturn(creator);

            sender.send("whatsapp:+10000000001", "", "https://cdn.example.test/a.mp3");

            verify(creator).setMediaUrl(List.of(URI.create("https://cdn.example.test/a.mp3")));
            verify(creator).create();
        }
    }

    @Test
    void blankMediaUrl_isTreatedAsTextOnly() {
        try (MockedStatic<Message> twilio = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            when(creator.create()).thenReturn(mock(Message.class));
            twilio.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                  .thenReturn(creator);

            sender.send("whatsapp:+10000000001", "hi", "  ");

            verify(creator, never()).setMediaUrl(ArgumentMatchers.<List<URI>>any());
        }
    }

    @Test
    void twilioFailure_isSwallowed() {
        try (MockedStatic<Message> twilio = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            when(creator.create()).thenThrow(new RuntimeException("Twilio 503"));
            twilio.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                  .thenReturn(creator);

            assertDoesNotThrow(() -> sender.send("whatsapp:+10000000001", "hi", null));
        }
    }

    @Test
    void badRecipientOrConfig_isSwallowed() {
        when(twilioConfig.getWhatsappFrom()).thenThrow(new IllegalStateException("not configured"));
        assertDoesNotThrow(() -> sender.send("whatsapp:+10000000001", "hi", null));
    }
}
