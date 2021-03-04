package com.appliedrec.verid.ui2;

import androidx.annotation.Keep;

import java.util.Locale;

@Keep
@SuppressWarnings("WeakerAccess")
public interface ILocaleProvider {
    @Keep
    Locale getLocale();
}
