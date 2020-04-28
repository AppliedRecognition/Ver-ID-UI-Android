package com.appliedrec.verid.ui;

import java.util.Locale;

public interface ITextSpeaker {

    void speak(String text, Locale locale, boolean interrupt);
}
