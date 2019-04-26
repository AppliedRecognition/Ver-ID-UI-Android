package com.appliedrec.verid.ui;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
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
import java.util.Locale;
import java.util.Map;

import static com.appliedrec.verid.ui.VerIDSessionActivity.EXTRA_TRANSLATION_ASSET_PATH;
import static com.appliedrec.verid.ui.VerIDSessionActivity.EXTRA_TRANSLATION_FILE_PATH;

public class TranslatedStrings implements IStringTranslator {

    private Map<String,String> strings = new HashMap<>();
    private boolean loaded = false;

    public TranslatedStrings(final Context context, Intent intent) {
        if (intent != null) {
            final String translationFilePath = intent.getStringExtra(EXTRA_TRANSLATION_FILE_PATH);
            final String translationAssetPath = intent.getStringExtra(EXTRA_TRANSLATION_ASSET_PATH);
            if (translationFilePath != null) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            loadTranslatedStrings(translationFilePath);
                        } catch (XmlPullParserException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                return;
            }
            if (translationAssetPath != null) {
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputStream inputStream = context.getAssets().open(translationAssetPath);
                            loadTranslatedStrings(inputStream);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (XmlPullParserException e) {
                            e.printStackTrace();
                        }
                    }
                });
                return;
            }
        }
        if (context != null) {
            Locale[] locales;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locales = new Locale[context.getResources().getConfiguration().getLocales().size()];
                for (int i = 0; i < locales.length; i++) {
                    locales[i] = context.getResources().getConfiguration().getLocales().get(i);
                }
            } else {
                locales = new Locale[]{context.getResources().getConfiguration().locale};
            }
            for (Locale locale : locales) {
                try {
                    String assetFile = null;
                    String[] assets = context.getAssets().list("");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // Check if there is an asset whose name matches exactly the language tag
                        for (String asset : assets) {
                            if (asset.equalsIgnoreCase(locale.toLanguageTag() + ".xml")) {
                                assetFile = asset;
                                break;
                            }
                        }
                    }
                    if (assetFile == null) {
                        // Check if there is an asset whose name matches exactly the language and country
                        for (String asset : assets) {
                            if (asset.equalsIgnoreCase(locale.getLanguage() + "-" + locale.getCountry() + ".xml")) {
                                assetFile = asset;
                                break;
                            }
                        }
                    }
                    if (assetFile == null) {
                        // Check if there is an asset whose name starts withe the locale language
                        for (String asset : assets) {
                            if (asset.toLowerCase().startsWith(locale.getLanguage().toLowerCase() + "-") && asset.toLowerCase().endsWith(".xml")) {
                                assetFile = asset;
                                break;
                            }
                        }
                    }
                    if (assetFile == null) {
                        // Check if there is an asset whose name matches the locale language
                        for (String asset : assets) {
                            if (asset.equalsIgnoreCase(locale.getLanguage() + ".xml")) {
                                assetFile = asset;
                                break;
                            }
                        }
                    }
                    if (assetFile != null) {
                        final String asset = assetFile;
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    InputStream inputStream = context.getAssets().open(asset);
                                    loadTranslatedStrings(inputStream);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (XmlPullParserException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        synchronized (this) {
            loaded = true;
            notifyAll();
        }
    }

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
