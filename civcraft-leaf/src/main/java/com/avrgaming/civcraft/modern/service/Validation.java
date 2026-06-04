package com.avrgaming.civcraft.modern.service;

import java.util.regex.Pattern;

public final class Validation {
    private static final Pattern NAME = Pattern.compile("^[A-Za-zА-Яа-я0-9_ -]{3,32}$");

    private Validation() {
    }

    public static boolean isValidName(String input) {
        return input != null && NAME.matcher(input).matches();
    }
}
