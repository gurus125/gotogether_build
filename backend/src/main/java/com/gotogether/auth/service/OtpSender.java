package com.gotogether.auth.service;

/** Sends a one-time code to a phone number over SMS. */
public interface OtpSender {

    void send(String phoneNumber, String code);
}
