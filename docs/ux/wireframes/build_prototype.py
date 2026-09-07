"""Compose the reviewed wireframes with the local navigation controller."""

from pathlib import Path

directory = Path(__file__).resolve().parent
shell = (directory / "prototype-v2-shell.html").read_text()
for marker, filename in (
    ("<!-- LIVE_V2 -->", "live-v2.html"),
    ("<!-- SENSOR_V2 -->", "sensor-detail-v2.html"),
    ("<!-- HISTORY -->", "history.html"),
):
    assert shell.count(marker) == 1
    fragment = (directory / filename).read_text().replace('<div id=', '<div data-embedded id=', 1)
    shell = shell.replace(marker, fragment)
(directory / "prototype.html").write_text(shell)
print("Built prototype.html")
