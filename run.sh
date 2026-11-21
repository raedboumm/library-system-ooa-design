#!/usr/bin/env sh
# Compile and run the Library OOA code example on macOS / Linux (JDK 17+).
set -e

rm -rf out
javac -encoding UTF-8 -d out $(find src -name '*.java')
java -cp out com.library.ui.LibraryDemo
