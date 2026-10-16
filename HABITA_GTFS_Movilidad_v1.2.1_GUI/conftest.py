"""Ejecuta también los controles con el feed real incluido en la entrega."""
import os
from pathlib import Path
import tempfile
import zipfile
from habita_gui import DEFAULT_FEED, ensure_bundled_feed

_test_feed_directory = None

def pytest_sessionstart(session):
    global _test_feed_directory
    if os.environ.get("GTFS_TEST_PATH"):
        return
    ensure_bundled_feed()
    _test_feed_directory = tempfile.TemporaryDirectory(prefix="habita_gtfs_tests_")
    target = Path(_test_feed_directory.name)
    with zipfile.ZipFile(DEFAULT_FEED) as archive:
        for member in archive.infolist():
            name = Path(member.filename).name
            if name.endswith(".txt"):
                (target / name).write_bytes(archive.read(member))
    os.environ["GTFS_TEST_PATH"] = str(target)

def pytest_sessionfinish(session, exitstatus):
    if _test_feed_directory is not None:
        _test_feed_directory.cleanup()
