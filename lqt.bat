@echo off
REM Convenience lqt driver script.
 
set dir=%~dp0
for /f "delims=" %%i in (%dir%target\classpath.txt) do set tmp_cp=%%i
set CP=%dir%;%dir%target\classes;%tmp_cp%
 
if exist %dir%\target\classes\com\basistech\lucene\tools\LuceneQueryTool.class (
"%JAVA_HOME%bin\java.exe" %JVM_ARGS% -Dfile.encoding=UTF-8 -cp %CP% com.basistech.lucene.tools.LuceneQueryTool %*
) else (
echo Please run 'mvn compile' first.
)
