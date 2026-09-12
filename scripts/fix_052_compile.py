from pathlib import Path

# Keep committed MainActivity.kt as-is. Rewriting LabudaApp here desyncs ImportScreen and breaks release builds.
p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
if not p.exists():
    raise SystemExit("MainActivity.kt not found")
print("LABUDA: compile patch skipped, using source MainActivity.kt")
