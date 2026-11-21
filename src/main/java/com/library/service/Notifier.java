package com.library.service;

import com.library.domain.Member;

/**
 * Port to the external Notification Gateway (email / SMS).
 * EmailNotifier or SmsNotifier would implement this in production.
 */
public interface Notifier {
    void send(Member member, String message);
}
