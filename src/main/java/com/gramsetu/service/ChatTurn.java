package com.gramsetu.service;

/** One earlier message of a conversation. {@code role} is "user" or "assistant". */
public record ChatTurn(String role, String text) {
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
}
