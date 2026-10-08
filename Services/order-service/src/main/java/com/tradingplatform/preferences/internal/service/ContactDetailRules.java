package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.domain.exception.InvalidPreferenceException;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks a requested set of channels and their contact details, and normalises them. Messages
 * never echo a contact detail, because the exception handler logs them.
 */
final class ContactDetailRules {

    static final int MAX_LENGTH = 254;

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-().]");

    private ContactDetailRules() {
    }

    /**
     * @return one normalised target per requested channel
     * @throws InvalidPreferenceException (PREF-422) when the list is empty, names a channel twice,
     *         or a contact detail does not fit its channel
     */
    static List<AlertTarget> normalise(List<AlertChannelEntry> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new InvalidPreferenceException("Choose at least one alert channel");
        }
        if (requested.size() > AlertChannel.values().length) {
            throw new InvalidPreferenceException("Choose at most " + AlertChannel.values().length + " alert channels");
        }
        Set<AlertChannel> seen = EnumSet.noneOf(AlertChannel.class);
        List<AlertTarget> targets = new ArrayList<>(requested.size());
        for (AlertChannelEntry r : requested) {
            if (r == null || r.channel() == null) {
                throw new InvalidPreferenceException("Every alert channel entry needs a channel");
            }
            if (!seen.add(r.channel())) {
                throw new InvalidPreferenceException(r.channel() + " is listed more than once");
            }
            targets.add(new AlertTarget(r.channel(), normalise(r.channel(), r.contactDetail())));
        }
        return targets;
    }

    /**
     * @return the value to store: trimmed email, separator-free E.164 number, or null for IN_APP
     */
    static String normalise(AlertChannel channel, String raw) {
        String value = raw == null ? null : raw.trim();
        boolean blank = value == null || value.isEmpty();

        switch (channel) {
            case IN_APP -> {
                if (!blank) {
                    throw new InvalidPreferenceException("IN_APP does not take a contact detail");
                }
                return null;
            }
            case EMAIL -> {
                if (blank) {
                    throw new InvalidPreferenceException("EMAIL requires an email address");
                }
                if (value.length() > MAX_LENGTH || !EMAIL.matcher(value).matches()) {
                    throw new InvalidPreferenceException("Contact detail is not a valid email address");
                }
                return value;
            }
            case SMS -> {
                if (blank) {
                    throw new InvalidPreferenceException("SMS requires a telephone number");
                }
                String number = PHONE_SEPARATORS.matcher(value).replaceAll("");
                if (!E164.matcher(number).matches()) {
                    throw new InvalidPreferenceException(
                            "Contact detail is not an E.164 telephone number, for example +447700900123");
                }
                return number;
            }
            default -> throw new InvalidPreferenceException("Unsupported alert channel");
        }
    }
}
