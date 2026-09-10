from pathlib import Path
import re

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")

if "private fun PingBars(" not in text:
    pattern = re.compile(r'@Composable private fun ServerCard\(.*?\n@Composable private fun VpnStatsCard', re.S)
    replacement = '''@Composable
private fun ServerCard(
    p: VlessProfile,
    selected: VlessProfile?,
    onSelect: (VlessProfile) -> Unit,
    onFavorite: (VlessProfile) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onSelect(p) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected?.id == p.id) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    p.latencyMs?.let { "$it мс" } ?: "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                PingBars(p.latencyMs)
            }
        }
    }
}

@Composable
private fun PingBars(latencyMs: Long?) {
    val level = when {
        latencyMs == null -> 0
        latencyMs <= 60 -> 4
        latencyMs <= 120 -> 3
        latencyMs <= 220 -> 2
        latencyMs <= 400 -> 1
        else -> 1
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            Box(
                Modifier
                    .size(width = 4.dp, height = (4 + i * 2).dp)
                    .background(
                        if (i <= level) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@Composable private fun VpnStatsCard'''
    updated, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit("ServerCard block not found")
    path.write_text(updated, encoding="utf-8")
