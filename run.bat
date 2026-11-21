@echo off
REM Compile and run the Library OOA code example on Windows (JDK 17+).

if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
    del sources.txt
    exit /b 1
)
del sources.txt

java -cp out com.library.ui.LibraryDemo
