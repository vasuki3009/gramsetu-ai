package com.gramsetu.config;

import com.twilio.Twilio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Configuration
@Component
public class TwilioConfig {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.from}")
    private String whatsappFrom;

    @Bean
    public TwilioConfig twilioClient() {
        Twilio.init(accountSid, authToken);
        return this;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getWhatsappFrom() {
        return whatsappFrom;
    }

    public String getAccountSid() {
        return accountSid;
    }
}
