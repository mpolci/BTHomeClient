"""Compose the reviewed wireframes with the local navigation controller."""

from pathlib import Path

directory = Path(__file__).resolve().parent
shell = (directory / "navigation-shell.html").read_text()
for marker, filename in (
    ("<!-- LIVE_FRAGMENT -->", "live.html"),
    ("<!-- HISTORY_FRAGMENT -->", "history.html"),
):
    assert shell.count(marker) == 1
    shell = shell.replace(marker, (directory / filename).read_text())
(directory / "prototype.html").write_text(shell)
clean_shell = shell.replace(
    '<div id="boat-navigation-prototype">',
    '<div id="boat-navigation-prototype" data-layout="clean">',
    1,
)
(directory / "prototype-clean.html").write_text(clean_shell)
print("Built prototype.html")
print("Built prototype-clean.html")
