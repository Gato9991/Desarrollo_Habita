"""Pruebas de integración de la GUI: HTTP, motor real y exportación."""
import json
from pathlib import Path
import threading
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import pytest
from habita_gui import AppState, LocalServer, validate_query

ROOT = Path(__file__).resolve().parent

@pytest.mark.parametrize("changes", [
    {"lat": "NaN"}, {"lon": "Infinity"}, {"lat": 91}, {"lon": -181},
    {"date": "2026-02-30"}, {"date": "23/09/2026"},
])
def test_reject_invalid_inputs(changes):
    data = {"lat": -33.5922368, "lon": -70.704706, "date": "2026-09-23"}
    data.update(changes)
    with pytest.raises(ValueError):
        validate_query(data)

@pytest.fixture(scope="module")
def live_app():
    state = AppState()
    server = LocalServer(("127.0.0.1", 0), state)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield state, f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()
    server.server_close()
    thread.join(timeout=3)

def request(base, path, body=None, token=None, origin=None):
    headers = {}
    if token: headers["X-Habita-Token"] = token
    if origin: headers["Origin"] = origin
    data = None if body is None else json.dumps(body).encode()
    if data is not None: headers["Content-Type"] = "application/json"
    return urlopen(Request(base + path, data=data, headers=headers), timeout=30)

def test_local_http_boundaries(live_app):
    state, base = live_app
    for path in ("/", "/app.js", "/style.css", "/assets/referencia_visual.png"):
        with request(base, path) as response:
            assert response.status == 200
    with pytest.raises(HTTPError) as error:
        request(base, "/api/analyze", {"lat": 0})
    assert error.value.code == 403
    with pytest.raises(HTTPError) as error:
        request(base, "/api/analyze", {}, token=state.token, origin="https://example.invalid")
    assert error.value.code == 403
    with pytest.raises(HTTPError) as error:
        urlopen(Request(base+"/api/config",headers={"Host":"example.invalid"}))
    assert error.value.code == 403
    with pytest.raises(HTTPError) as error:
        request(base, "/../habita_gui.py")
    assert error.value.code == 404

@pytest.mark.parametrize("index,filename,stop_id", [
    (0, "analysis_Plaza_de_Armas_-_San_Bernardo_2026-09-23_v1_2_1.json", "PG1599"),
    (1, "analysis_Estadio_Nacional_Julio_Martínez_Prádanos_2026-09-23_v1_2_1.json", "PD561"),
])
def test_gui_api_matches_original_reports(live_app, index, filename, stop_id):
    state, base = live_app
    from habita_gui import EXAMPLES
    with request(base, "/api/analyze", {**EXAMPLES[index], "date": "2026-09-23"}, state.token) as r:
        job_id = json.load(r)["job_id"]
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        with request(base, "/api/jobs/"+job_id) as r:
            job = json.load(r)
        if job["status"] != "running":
            break
        time.sleep(.1)
    assert job["status"] == "done", job
    result = job["result"]
    reference = json.loads((ROOT/"resultados"/filename).read_text(encoding="utf-8"))
    assert result["nearest_active_stop"]["stop_id"] == stop_id
    for key in ("nearest_physical_stop", "nearest_active_stop", "area_analysis"):
        assert result[key] == reference[key]
    with request(base, "/api/jobs/"+job_id+"/download") as r:
        assert "attachment" in r.headers["Content-Disposition"]
        assert json.load(r) == result

def test_invalid_date_returns_readable_job_error(live_app):
    state, base = live_app
    from habita_gui import EXAMPLES
    with request(base, "/api/analyze", {**EXAMPLES[0], "date": "2030-01-01"}, state.token) as r:
        job_id = json.load(r)["job_id"]
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        with request(base, "/api/jobs/"+job_id) as r:
            job = json.load(r)
        if job["status"] != "running": break
        time.sleep(.05)
    assert job["status"] == "error"
    assert job["error"]
