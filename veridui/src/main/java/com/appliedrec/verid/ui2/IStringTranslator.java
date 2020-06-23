package com.appliedrec.verid.ui2;

@SuppressWarnings("WeakerAccess")
public interface IStringTranslator extends ILocaleProvider {
    String getTranslatedString(String original, Object ...args);
}
