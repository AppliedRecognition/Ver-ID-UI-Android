from os import walk
import re

def unique(list_items):
    """Remove duplicates from a list while preserving order."""
    seen = set()
    return [x for x in list_items if not (x in seen or seen.add(x))]

def get_java_files(directory, java_files):
    """Recursively find all Java files in the given directory."""
    for (dirpath, dirnames, filenames) in walk(directory):
        for name in filenames:
            if name.endswith(".java"):
                java_files.append(dirpath + "/" + name)
        for dirname in dirnames:
            if dirname != "build":
                get_java_files(dirpath + "/" + dirname, java_files)

def strings():
    """Extract all translatable strings from Java source files."""
    java_files = []
    get_java_files("./veridui/src/", java_files)
    
    all_strings = []
    for java_file in java_files:
        try:
            with open(java_file, "r", encoding="utf-8") as f:
                content = f.read()
                matches = re.findall(r'getTranslatedString\("([^"]+)"\)', content)
                all_strings.extend(matches)
        except Exception as e:
            print(f"Error reading {java_file}: {e}")
    
    return unique(all_strings)
