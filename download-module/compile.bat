@echo off
set "CLASSPATH=lib/*;target/classes"
set "SOURCE_DIR=src/main/java"
set "OUTPUT_DIR=target/classes"

:: 创建输出目录
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

:: 编译所有Java文件
for /r "%SOURCE_DIR%" %%f in (*.java) do (
    javac -encoding UTF-8 -d "%OUTPUT_DIR%" -cp "%CLASSPATH%" "%%f"
)

echo 编译完成！
