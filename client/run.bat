@echo off
rem Guess Market client. Needs Java 21+ on the PATH, and the server running at
rem http://localhost:8080/guess-market (guess-market.war deployed in Tomcat).
rem %~dp0 = this file's own folder, so it works from anywhere. lib\ must stay next to the jar.
java -jar "%~dp0guess-market-client.jar"
rem Keep the window open if Java failed to start, so the error can be read.
if errorlevel 1 pause
