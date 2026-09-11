from pathlib import Path

# Compatibility step for the 1.0.0.0 build. Keep it deliberately small and safe:
# patch only the navigation that must be applied at build time to the existing UI.
p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = p.read_text()
old = 'activity.startActivity(Intent(activity,RoutingSettingsActivity::class.java))'
new = 'activity.startActivity(Intent(activity,SettingsActivity::class.java))'
if old in s:
    s = s.replace(old, new, 1)

# Remove the old airplane/flight visual from the top bar without touching VPN logic.
lines = []
for line in s.splitlines():
    low = line.lower()
    if "airplane" in low or "flight" in low or "✈" in line:
        continue
    lines.append(line)
s = "\n".join(lines) + "\n"
p.write_text(s)
print("LABUDA 1.0.0.0: applied safe settings navigation and airplane-icon cleanup")
