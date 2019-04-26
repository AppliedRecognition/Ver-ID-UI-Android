from os import walk
import re

def unique(list):
    ulist = []
    for val in list:
        if val not in ulist:
            ulist.append(val)
    return ulist

def getJavaFiles(dir, javafiles):
    for (dirpath, dirnames, filenames) in walk(dir):
        for name in filenames:
            if name.endswith(".java"):
                javafiles.append(dirpath+"/"+name)
        for dirname in dirnames:
            if dirname != "build":
                getJavaFiles(dirpath+dirname, javafiles)
javafiles = []
getJavaFiles("./veridui/src/", javafiles)

javafiles = unique(javafiles)

def strings():
    words = []
    for file in javafiles:
        f = open(file, "r")
        src = f.read()
        matchlist = re.findall(r"getTranslatedString\(\"(.+?)\"", src)
        for word in matchlist:
            if word not in words:
                words.append(word)
    return words
