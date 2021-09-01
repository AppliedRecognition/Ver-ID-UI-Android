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

## Using the system locale when choosing your translation

The Ver-ID translation system gives you the flexibility to start Ver-ID sessions with different languages without having to change the system locale. However, if you want Ver-ID to use the system locale, simply include the translated strings in your app bundle's **assets** folder.

## Running a Ver-ID session with a specific translation

To use a translation saved as **es.xml** in your app's assets add the following parameter in the Ver-ID session constructor:

~~~java
// Session settings
LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();

// Create an instance of TranslatedStrings pointing to the translation file in the assets folder
TranslatedStrings translation = new TranslatedStrings(context, "es.xml", Locale.ES);

// Set the translation as a parameter of the VerIDSession constructor
VerIDSession session = new VerIDSession(verID, settings, translation);
// ...
~~~
