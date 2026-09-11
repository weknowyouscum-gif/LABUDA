from pathlib import Path

# LABUDA 1.0.0.0 compatibility step.
# IMPORTANT: never delete an entire source line — MainActivity.kt intentionally
# contains several compact Kotlin declarations on single lines.
p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = p.read_text()

# Open the new settings hub instead of routing directly.
s = s.replace(
    'activity.startActivity(Intent(activity,RoutingSettingsActivity::class.java))',
    'activity.startActivity(Intent(activity,SettingsActivity::class.java))',
    1,
)

# Remove only the legacy Telegram/airplane button from the top bar.
# Do not remove the whole line because the UI is minified onto a single line.
airplane = 'IconButton(onClick={activityLaunchTelegram()},modifier=Modifier.size(36.dp)){Text("✈",fontSize=20.sp)};'
s = s.replace(airplane, '', 1)

p.write_text(s)
print("LABUDA 1.0.0.0: applied safe settings navigation and airplane-icon cleanup")
