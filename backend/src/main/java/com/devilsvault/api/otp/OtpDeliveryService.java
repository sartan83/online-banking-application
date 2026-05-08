package com.devilsvault.api.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pluggable OTP delivery seam.
 *
 * <p>The legacy stack hard-coded SMTP through {@code OtpDaoImpl#sendEmailToUser}. Phase 3 swaps
 * this default implementation for a real {@code MailHog}/SMTP sender; for Phase 2 the code is
 * logged so integration tests and local dev can extract it without configuring SMTP.
 */
@Component
public class OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);

    public void deliver(String email, String code, Otp.Purpose purpose) {
        log.info("OTP {} for {} (purpose={})", code, email, purpose);
    }
}
