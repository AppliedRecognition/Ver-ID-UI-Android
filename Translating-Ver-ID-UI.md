# Translating Ver-ID UI

Ver-ID UI allows you to supply a language translation when starting a Ver-ID session.

The Ver-ID-UI project provides [Python 2.7](https://www.python.org/download/releases/2.7/) scripts to generate an empty translation XML and to verify that a given file is not missing any translations.

## Generating a translation XML

~~~shell
python translation_xml.py
~~~
The command will collect all strings used in the source code and output a string like this:

~~~shell
<?xml version="1.0" encoding="utf-8"?><strings>
    <string>
        <original>Camera used for face authentication</original>
        <translation></translation>
    </string>
    <string>
        <original>You may have turned too far</original>
        <translation></translation>
    </string>
    <string>
        <original>Turn your head in the direction of the arrow</original>
        <translation></translation>
    </string>
    <string>
        <original>Please turn slowly</original>
        <translation></translation>
    </string>
    <string>
        <original>Success</original>
        <translation></translation>
    </string>
    <string>
        <original>Failed</original>
        <translation></translation>
    </string>
    <string>
        <original>Done</original>
        <translation></translation>
    </string>
    <string>
        <original>Next</original>
        <translation></translation>
    </string>
    <string>
        <original>Tip 2 of 3</original>
        <translation></translation>
    </string>
    <string>
        <original>Tip 3 of 3</original>
        <translation></translation>
    </string>
    <string>
        <original>Tip 1 of 3</original>
        <translation></translation>
    </string>
    <string>
        <original>If you can, take off your glasses.</original>
        <translation></translation>
    </string>
    <string>
        <original>Avoid standing in front of busy backgrounds.</original>
        <translation></translation>
    </string>
    <string>
        <original>Avoid standing in a light that throws sharp shadows like in sharp sunlight or directly under a lamp.</original>
        <translation></translation>
    </string>
</strings>
~~~
Enter the translation of the string inside the `<original>` tag in the `<translation>` tag.

To save the generated XML as **es.xml**:

~~~shell
python translation_xml.py > es.xml
~~~

## Checking that your translation is complete
Once you translate all the strings in the XML run:

~~~shell
python test_translation.py es.xml
~~~
If all strings are translated the command will output:

~~~shell
All translated
~~~
Otherwise it will output the text `Missing:` followed by the missing translations, each on one line:

~~~shell
Missing:
Turn your head in the direction of the arrow
If you can, take off your glasses.
~~~

## Running a Ver-ID session with your translation

To use a translation saved as **es.xml** in your app's assets add the following extra in the Ver-ID session intent:

~~~java
LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
Intent intent = new VerIDSessionIntent<>(this, verID, settings);
intent.putExtra(VerIDSessionActivity.EXTRA_TRANSLATION_ASSET_PATH, "es.xml");
~~~

Alternatively, if your translation resides elsewhere on the file system, you can specify a path to the XML file.

~~~java
// Let's say we have "es.xml" in the files directory
File translation = new File(getFilesDir(), "es.xml");

LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
Intent intent = new VerIDSessionIntent<>(this, verID, settings);
intent.putExtra(VerIDSessionActivity.EXTRA_TRANSLATION_FILE_PATH, translation.getPath());
~~~

## Using the system locale when choosing your translation

The Ver-ID translation system gives you the flexibility to start Ver-ID sessions with different languages without having to change the system locale. However, if you wish Ver-ID to use the system locale, you will need to map your translations to the system locales. For example, say you translated Ver-ID to French and Spanish and you put the translations in your app's **assets** folder as **fr.xml** and **es.xml** respectively:

~~~java
Intent intent; // Ver-ID session intent
// Map locale languages to your translations
HashMap<String,String> translationMap = new HashMap<>();
translationMap.put(new Locale("fr").getLanguage(), "fr.xml");
translationMap.put(new Locale("es").getLanguage(), "es.xml");
// Use the translation if available
if (translationMap.containsKey(currentLocale.getLanguage())) {
    intent.putExtra(VerIDSessionActivity.EXTRA_TRANSLATION_ASSET_PATH, translationMap.get(currentLocale.getLanguage()));
}
~~~

If the system locale is set to another language the session will default to English.

You can build more elaborate language resolution system if you need to take into account regional language variations.
