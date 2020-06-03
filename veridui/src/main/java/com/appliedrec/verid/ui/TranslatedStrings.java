package com.appliedrec.verid.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

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
import java.util.Objects;

import static com.appliedrec.verid.ui.VerIDSessionActivity.EXTRA_LOCALE;
import static com.appliedrec.verid.ui.VerIDSessionActivity.EXTRA_TRANSLATION_ASSET_PATH;
import static com.appliedrec.verid.ui.VerIDSessionActivity.EXTRA_TRANSLATION_FILE_PATH;

@SuppressWarnings("WeakerAccess")
public class TranslatedStrings implements IStringTranslator, ILocaleProvider, Parcelable {

    private Map<String,String> strings = new HashMap<>();
    private boolean loaded = false;
    private Locale locale = Locale.ENGLISH;
    private final Object loadLock = new Object();

    public TranslatedStrings(Context context, String assetPath, Locale locale) {
        this.locale = locale;
        AsyncTask.execute(() -> {
            try (InputStream inputStream = context.getAssets().open(assetPath)) {
                loadTranslatedStrings(inputStream);
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
                synchronized (loadLock) {
                    loaded = true;
                    loadLock.notifyAll();
                }
            }
        });
    }

    public TranslatedStrings(Context context, Uri translationFileUri, Locale locale) {
        this.locale = locale;
        AsyncTask.execute(() -> {
            try (InputStream inputStream = context.getContentResolver().openInputStream(translationFileUri)) {
                loadTranslatedStrings(inputStream);
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
                synchronized (loadLock) {
                    loaded = true;
                    loadLock.notifyAll();
                }
            }
        });
    }

    public TranslatedStrings(final Context context, Intent intent) {
        if (intent != null) {
            final String translationFilePath = intent.getStringExtra(EXTRA_TRANSLATION_FILE_PATH);
            final String translationAssetPath = intent.getStringExtra(EXTRA_TRANSLATION_ASSET_PATH);
            Locale intentLocale = (Locale) intent.getSerializableExtra(EXTRA_LOCALE);
            if (intentLocale != null) {
                this.locale = intentLocale;
            }
            if (translationFilePath != null) {
                if (intentLocale == null) {
                    setLocaleFromPath(translationFilePath);
                }
                AsyncTask.execute(() -> {
                    try {
                        loadTranslatedStrings(translationFilePath);
                    } catch (XmlPullParserException | IOException e) {
                        e.printStackTrace();
                        synchronized (loadLock) {
                            loaded = true;
                            loadLock.notifyAll();
                        }
                    }
                });
                return;
            }
            if (translationAssetPath != null) {
                if (intentLocale == null) {
                    setLocaleFromPath(translationFilePath);
                }
                AsyncTask.execute(() -> {
                    try (InputStream inputStream = context.getAssets().open(translationAssetPath)) {
                        loadTranslatedStrings(inputStream);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                        synchronized (loadLock) {
                            loaded = true;
                            loadLock.notifyAll();
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
                    String[] assets = Objects.requireNonNull(context.getAssets().list(""));
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
                        // Check if there is an asset whose name starts with the locale language
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
                    if (assetFile == null && "en".equalsIgnoreCase(locale.getLanguage())) {
                        // If the current language is English stop looking for a translation
                        break;
                    }
                    if (assetFile != null) {
                        final String asset = assetFile;
                        this.locale = locale;
                        AsyncTask.execute(() -> {
                            try (InputStream inputStream = context.getAssets().open(asset)) {
                                loadTranslatedStrings(inputStream);
                            } catch (IOException | XmlPullParserException e) {
                                e.printStackTrace();
                                synchronized (loadLock) {
                                    loaded = true;
                                    loadLock.notifyAll();
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
        synchronized (loadLock) {
            loaded = true;
            loadLock.notifyAll();
        }
    }

    private void setLocaleFromPath(String path) {
        String filename = new File(path).getName();
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex > -1) {
            filename = filename.substring(0, dotIndex);
        }
        int underscoreIndex = filename.indexOf("_");
        String language;
        String country = null;
        if (underscoreIndex > -1) {
            language = filename.substring(0, underscoreIndex);
            if (filename.length() > underscoreIndex+1) {
                country = filename.substring(underscoreIndex + 1);
            }
        } else {
            language = filename;
        }
        if (country != null) {
            this.locale = new Locale(language, country);
        } else {
            this.locale = new Locale(language);
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
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(inputStream, null);
        loadTranslatedStrings(parser);
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
                    strings.put(entry.getKey(), entry.getValue());
                } else {
                    skip(parser);
                }
            }
        } finally {
            synchronized (loadLock) {
                loaded = true;
                loadLock.notifyAll();
            }
        }
    }

    public void setTranslatedStrings(Map<String,String> translations) {
        strings = translations;
    }

    @Override
    public  @NonNull String getTranslatedString(@NonNull String original, Object ...args) {
        synchronized (loadLock) {
            while (!loaded) {
                try {
                    loadLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (strings.containsKey(original)) {
            return String.format(Objects.requireNonNull(strings.get(original)), args);
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
            return new AbstractMap.SimpleEntry<>(original, translation);
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

    @Override
    public Locale getLocale() {
        return locale;
    }

    //endregion

    //region Parcelable

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        synchronized (loadLock) {
            while (!loaded) {
                try {
                    loadLock.wait();
                } catch (InterruptedException ignore) {
                }
            }
        }
        parcel.writeSerializable(locale);
        parcel.writeInt(strings.size());
        for (Map.Entry<String,String> entry : strings.entrySet()) {
            parcel.writeString(entry.getKey());
            parcel.writeString(entry.getValue());
        }
    }

    protected TranslatedStrings(Parcel in) {
        locale = (Locale) in.readSerializable();
        int stringCount = in.readInt();
        for (int i=0; i<stringCount; i++) {
            String key = in.readString();
            String value = in.readString();
            strings.put(key, value);
        }
        synchronized (loadLock) {
            loaded = true;
            loadLock.notifyAll();
        }
    }

    public static final Creator<TranslatedStrings> CREATOR = new Creator<TranslatedStrings>() {
        @Override
        public TranslatedStrings createFromParcel(Parcel in) {
            return new TranslatedStrings(in);
        }

        @Override
        public TranslatedStrings[] newArray(int size) {
            return new TranslatedStrings[size];
        }
    };

    //endregion
}
