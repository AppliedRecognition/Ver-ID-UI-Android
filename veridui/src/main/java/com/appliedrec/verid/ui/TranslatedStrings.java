package com.appliedrec.verid.ui;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class TranslatedStrings implements IStringTranslator {

    private Map<String,String> strings = new HashMap<>();
    private boolean loaded = true;

    @WorkerThread
    public void loadTranslatedStrings(final String sourcePath) throws IOException, XmlPullParserException {
        File file = new File(sourcePath);
        FileInputStream inputStream = new FileInputStream(file);
        loadTranslatedStrings(inputStream);
    }

    @WorkerThread
    public void loadTranslatedStrings(InputStream inputStream) throws IOException, XmlPullParserException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            loadTranslatedStrings(parser);
        } finally {
            inputStream.close();
        }
    }

    @WorkerThread
    public void loadTranslatedStrings(XmlPullParser parser) throws XmlPullParserException, IOException {
        synchronized (this) {
            loaded = false;
            notifyAll();
        }
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, null, "strings");
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if ("string".equalsIgnoreCase(name)) {
                    Map.Entry<String, String> entry = readTranslation(parser);
                    synchronized (this) {
                        strings.put(entry.getKey(), entry.getValue());
                    }
                } else {
                    skip(parser);
                }
            }
        } finally {
            synchronized (this) {
                loaded = true;
                notifyAll();
            }
        }
    }

    public void setTranslatedStrings(Map<String,String> translations) {
        strings = translations;
    }

    @Override
    public synchronized  @NonNull String getTranslatedString(@NonNull String original, Object ...args) {
        while (!loaded) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        if (strings.containsKey(original)) {
            return String.format(strings.get(original), args);
        } else {
            return String.format(original, args);
        }
    }

    //region XML parser methods

    private Map.Entry<String,String> readTranslation(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, "string");
        String original = null;
        String translation = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if ("original".equalsIgnoreCase(name)) {
                original = readTextTag("original", parser);
            } else if ("translation".equalsIgnoreCase(name)) {
                translation = readTextTag("translation", parser);
            } else {
                skip(parser);
            }
        }
        if (original != null && translation != null) {
            return new AbstractMap.SimpleEntry<String, String>(original, translation);
        }
        throw new XmlPullParserException("Invalid string entry");
    }

    private String readTextTag(String tag, XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, tag);
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        parser.require(XmlPullParser.END_TAG, null, tag);
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    //endregion
}
