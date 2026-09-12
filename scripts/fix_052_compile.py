from pathlib import Path

# LABUDA 1.0.0.1 build-time compatibility and stabilization patch.
p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = p.read_text()

# Keep only imports that are actually available in the project's Material3/Compose stack.
# Older revisions injected androidx.compose.material.LocalTextStyle, which is not
# available in the current dependency set and caused the build to fail before parsing
# the rest of MainActivity.kt.
s = s.replace("import androidx.compose.material.LocalTextStyle\n", "")
s = s.replace("import androidx.compose.material3.LocalTextStyle\n", "")

if "import androidx.compose.ui.text.font.FontFamily" not in s:
    s = s.replace("import androidx.compose.ui.text.font.FontWeight\n", "import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight\n")
if "import androidx.compose.runtime.CompositionLocalProvider" not in s:
    s = s.replace("import androidx.compose.runtime.Composable\n", "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.CompositionLocalProvider\n")

start = s.find('@Composable private fun LabudaApp')
end = s.find('\n\nprivate fun normalizeSubscriptionTitle', start)
if start < 0 or end < 0:
    raise SystemExit('LabudaApp block boundaries not found')

p.write_text(s)
print("LABUDA 1.0.0.1: Compose imports normalized")
