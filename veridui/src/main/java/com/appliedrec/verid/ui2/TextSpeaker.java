package com.appliedrec.verid.ui2;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Speaks text
 * @since 1.21.0
 */
public class TextSpeaker implements ITextSpeaker, AutoCloseable, TextToSpeech.OnInitListener {

    private static TextSpeaker instance;
    private static final Object INSTANCE_LOCK = new Object();

    /**
     * Set up an instance of text speaker
     * @param context Speaker context
     * @since 1.21.0
     */
    public static void setup(Context context) {
        synchronized (INSTANCE_LOCK) {
            instance = new TextSpeaker(context);
        }
    }

    /**
     * Destroy an instance of text speaker
     * @since 1.21.0
     */
    public static void destroy() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                instance.close();
                instance = null;
            }
        }
    }

    /**
     * Get a singleton instance of speaker
     * @return Speaker instance
     * @since 1.21.0
     */
    public static ITextSpeaker getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                return instance;
            } else {
                return (text, locale, interrupt) -> {};
            }
        }
    }

    public interface SpeechProgressListener {
        void onSpoken(TextSpeaker speaker, String text);
        void onError(TextSpeaker speaker, String text);
    }

    private TextToSpeech textToSpeech;
    private String lastSpokenText;
    private final Object textLock = new Object();
    private String text;
    private Locale locale;
    private ExecutorService consumerExecutor;
    private ExecutorService producerExecutor;
    private static final String UTTERANCE_ID = "com.appliedrec.UTTERANCE";
    private SpeechProgressListener listener;
    private final Object ttsLock = new Object();

    private final UtteranceProgressListener utteranceProgressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String s) {

        }

        @Override
        public void onDone(String s) {
            if (UTTERANCE_ID.equals(s)) {
                if (listener != null && lastSpokenText != null) {
                    listener.onSpoken(TextSpeaker.this, lastSpokenText);
                }
                speak();
            }
        }

        @Override
        public void onError(String s) {
            if (UTTERANCE_ID.equals(s)) {
                if (listener != null && lastSpokenText != null) {
                    listener.onError(TextSpeaker.this, lastSpokenText);
                }
                speak();
            }
        }
    };

    /**
     * Constructor
     * @param context Context for the speaker
     * @since 1.21.0
     */
    @SuppressWarnings("WeakerAccess")
    public TextSpeaker(Context context) {
        consumerExecutor = Executors.newSingleThreadExecutor();
        producerExecutor = Executors.newSingleThreadExecutor();
        synchronized (ttsLock) {
            textToSpeech = new TextToSpeech(context, this);
        }
    }

    /**
     * Speak text
     * @param text Text to be spoken
     * @param locale Locale of the spoken text
     * @param interrupt {@literal true} to interrupt current speech
     * @since 1.21.0
     */
    @Override
    public void speak(String text, Locale locale, boolean interrupt) {
        if (interrupt) {
            synchronized (ttsLock) {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                }
            }
            if (listener != null && lastSpokenText != null) {
                listener.onSpoken(TextSpeaker.this, lastSpokenText);
            }
            speak();
        }
        if (text != null) {
            if (!text.equalsIgnoreCase(lastSpokenText)) {
                synchronized (textLock) {
                    this.text = text;
                    this.locale = locale;
                    textLock.notifyAll();
                }
            } else if (listener != null) {
                listener.onSpoken(this, text);
            }
        }
    }

    public SpeechProgressListener getListener() {
        return listener;
    }

    public void setListener(SpeechProgressListener listener) {
        this.listener = listener;
    }

    private void speak() {
        consumerExecutor.submit(() -> {
            String toSpeak;
            synchronized (textLock) {
                while (text == null) {
                    try {
                        textLock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                toSpeak = text;
                lastSpokenText = toSpeak;
                text = null;
            }
            synchronized (ttsLock) {
                if (locale != null) {
                    textToSpeech.setLanguage(locale);
                }
                textToSpeech.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            }
        });
    }

    /**
     * Close the speaker and free its resources
     * @since 1.21.0
     */
    @Override
    public void close() {
        synchronized (ttsLock) {
            if (textToSpeech != null) {
                textToSpeech.shutdown();
                textToSpeech = null;
            }
        }
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            consumerExecutor = null;
        }
        if (producerExecutor != null) {
            producerExecutor.shutdown();
            producerExecutor = null;
        }
    }

    @Override
    public void onInit(int i) {
        if (i == TextToSpeech.SUCCESS && consumerExecutor != null) {
            synchronized (ttsLock) {
                textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener);
            }
            speak();
        }
    }
}
