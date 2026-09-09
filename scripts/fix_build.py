from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")
old = ".trimEnd(',', ';', '\\\\r', '\\\\n')"
new = ".trimEnd(',', ';', '\\r', '\\n')"
if old not in text:
    raise SystemExit("Expected broken trimEnd escape sequence was not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Fixed Kotlin trimEnd escape sequence")
