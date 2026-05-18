/* Live GPS dashboard — 7-section admin UI, vanilla JS.
   Routes are road-snapped via OSRM map-matching, so the track follows streets
   instead of drawing straight lines between raw GPS points. */
(function () {
  "use strict";

  // ---- state ----
  var devices = {};            // id -> device DTO
  var alerts = [];             // alert DTOs
  var markers = {};            // id -> L.circleMarker (dashboard map)
  var routes = {};             // id -> { pts:[[lat,lng]], line:L.polyline, lastMatch:ms, busy:bool }
  var accuracyHistory = [];    // recent accuracy samples for the bar chart
  var section = "dashboard";
  var routesSeeded = false;
  var map, historyMap, historyLayer, geoMap, geoLayer;
  var geoPending = null, geoPendingMarker = null;
  var ws = null, reconnectDelay = 1000, reconnectTimer = null;

  var PALETTE = ["#6750A4", "#386A20", "#B3261E", "#00658F", "#7D5260", "#8C4A00"];
  var DONUT_C = 2 * Math.PI * 50;
  var OSRM = "https://router.project-osrm.org/match/v1/driving/";
  var MATCH_THROTTLE = 4500;   // ms between map-match calls per device
  var MAX_MATCH_PTS = 95;      // OSRM /match accepts up to 100 coordinates

  // ---- helpers ----
  function el(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c];
    });
  }
  function timeAgo(iso) {
    var t = new Date(iso).getTime();
    if (isNaN(t)) return "—";
    var s = Math.max(0, (Date.now() - t) / 1000);
    if (s < 60) return Math.floor(s) + "s ago";
    if (s < 3600) return Math.floor(s / 60) + "m ago";
    if (s < 86400) return Math.floor(s / 3600) + "h ago";
    return Math.floor(s / 86400) + "d ago";
  }
  function colorFor(id) {
    var h = 0;
    for (var i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0;
    return PALETTE[Math.abs(h) % PALETTE.length];
  }
  function api(path) {
    return fetch(path).then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); });
  }
  function apiSend(method, path, body) {
    return fetch(path, {
      method: method,
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  // ---- OSRM road snapping (map matching) ----
  // Converts a noisy GPS point list into a polyline that follows real roads.
  // Falls back to the raw points if the routing service is unavailable.
  function snapToRoads(latlngs) {
    if (!latlngs || latlngs.length < 2) return Promise.resolve(latlngs || []);
    var pts = latlngs.slice(-MAX_MATCH_PTS);
    var coords = pts.map(function (p) {
      return p[1].toFixed(6) + "," + p[0].toFixed(6);
    }).join(";");
    var url = OSRM + coords + "?geometries=geojson&overview=full&tidy=true";
    return fetch(url)
      .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
      .then(function (data) {
        if (!data.matchings || !data.matchings.length) return pts;
        var out = [];
        data.matchings.forEach(function (m) {
          ((m.geometry && m.geometry.coordinates) || []).forEach(function (c) {
            out.push([c[1], c[0]]); // [lng,lat] -> [lat,lng]
          });
        });
        return out.length ? out : pts;
      })
      .catch(function () { return pts; });
  }

  // Re-draws a device's road-snapped route on the dashboard map.
  function drawRoute(id) {
    var r = routes[id];
    if (!r || r.busy || r.pts.length < 2) return;
    r.busy = true;
    r.lastMatch = Date.now();
    snapToRoads(r.pts).then(function (snapped) {
      var rr = routes[id];
      if (!rr) return;
      rr.busy = false;
      if (rr.line) map.removeLayer(rr.line);
      rr.line = L.polyline(snapped, {
        color: colorFor(id), weight: 5, opacity: 0.9, lineJoin: "round", lineCap: "round",
      }).addTo(map);
      if (markers[id]) markers[id].bringToFront();
    });
  }

  // Seeds each device's route once from its stored history.
  function seedRoutes() {
    if (routesSeeded) return;
    routesSeeded = true;
    Object.keys(devices).forEach(function (id) {
      api("/api/devices/" + encodeURIComponent(id) + "/track?limit=300").then(function (data) {
        var pts = (data.points || []).map(function (p) { return [p.lat, p.lng]; });
        if (pts.length < 2) return;
        routes[id] = routes[id] || { pts: [], line: null, lastMatch: 0, busy: false };
        routes[id].pts = pts;
        drawRoute(id);
      }).catch(function () {});
    });
  }

  // ---- theme ----
  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("gps-theme", theme);
    el("theme-toggle").textContent = theme === "dark" ? "☾" : "☀";
    [map, historyMap, geoMap].forEach(refreshTiles);
  }
  function refreshTiles(m) {
    if (!m) return;
    m.eachLayer(function (l) { if (l instanceof L.TileLayer) m.removeLayer(l); });
    L.tileLayer(tileUrl(), {
      maxZoom: 19, subdomains: "abcd", attribution: "&copy; OpenStreetMap, &copy; CARTO",
    }).addTo(m);
  }
  function tileUrl() {
    var dark = document.documentElement.getAttribute("data-theme") === "dark";
    return "https://{s}.basemaps.cartocdn.com/" + (dark ? "dark_all" : "light_all") +
      "/{z}/{x}/{y}{r}.png";
  }

  // ---- section routing ----
  function showSection(name) {
    section = name;
    document.querySelectorAll(".section").forEach(function (s) { s.classList.remove("active"); });
    var target = el("section-" + name);
    if (target) target.classList.add("active");
    document.querySelectorAll(".nav-item").forEach(function (n) {
      n.classList.toggle("active", n.dataset.section === name);
    });
    if (name === "devices") renderDevices();
    else if (name === "history") renderHistory();
    else if (name === "geofences") renderGeofences();
    else if (name === "alerts") renderAlerts();
    else if (name === "reports") renderReports();
    else if (name === "settings") renderSettings();
    if (map && name === "dashboard") setTimeout(function () { map.invalidateSize(); }, 100);
  }

  // ---- WebSocket ----
  function connect() {
    var proto = location.protocol === "https:" ? "wss:" : "ws:";
    setConn("connecting");
    ws = new WebSocket(proto + "//" + location.host + "/ws/dashboard");
    ws.onopen = function () { reconnectDelay = 1000; setConn("connected"); };
    ws.onmessage = function (ev) {
      var msg;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      if (msg.type === "snapshot") (msg.devices || []).forEach(applyLocation);
      else if (msg.type === "location") applyLocation(msg);
      el("status-updated").textContent = "Last update " + new Date().toLocaleTimeString();
    };
    ws.onerror = function () { ws.close(); };
    ws.onclose = function () { setConn("disconnected"); scheduleReconnect(); };
  }
  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectTimer = setTimeout(function () {
      reconnectTimer = null; connect();
    }, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, 30000);
  }
  function setConn(state) {
    el("status-led").setAttribute("data-state", state);
    el("status-text").textContent = state === "connected" ? "Live — receiving updates"
      : state === "connecting" ? "Connecting…" : "Disconnected — retrying…";
  }

  function applyLocation(msg) {
    if (typeof msg.lat !== "number") return;
    if (typeof msg.accuracy === "number") {
      accuracyHistory.push({ t: msg.timestamp, v: msg.accuracy });
      if (accuracyHistory.length > 6) accuracyHistory.shift();
    }
    var id = msg.device_id;
    var ll = [msg.lat, msg.lng];

    // Live marker (the moving dot at the head of the route).
    var m = markers[id];
    if (!m) {
      markers[id] = L.circleMarker(ll, {
        radius: 8, color: "#fff", weight: 3,
        fillColor: colorFor(id), fillOpacity: 1,
      }).addTo(map).bindPopup(esc(id));
      var pts = Object.keys(markers).map(function (k) { return markers[k].getLatLng(); });
      if (pts.length === 1) map.setView(ll, 16);
      else map.fitBounds(L.latLngBounds(pts).pad(0.25));
    } else {
      m.setLatLng(ll);
      m.bringToFront();
    }

    // Extend this device's route and re-snap it to roads (throttled).
    var r = routes[id] || (routes[id] = { pts: [], line: null, lastMatch: 0, busy: false });
    var last = r.pts[r.pts.length - 1];
    if (!last || last[0] !== ll[0] || last[1] !== ll[1]) {
      r.pts.push(ll);
      if (r.pts.length > 400) r.pts.shift();
    }
    if (Date.now() - r.lastMatch > MATCH_THROTTLE) drawRoute(id);

    if (section === "dashboard") renderDashboardCards();
  }

  // ---- data refresh ----
  function refreshData() {
    api("/api/devices").then(function (list) {
      devices = {};
      list.forEach(function (d) { devices[d.id] = d; });
      if (!routesSeeded && list.length) seedRoutes();
      if (section === "dashboard") renderDashboardCards();
      if (section === "devices") renderDevices();
    }).catch(function () {});
    api("/api/alerts").then(function (list) {
      alerts = list || [];
      el("bell-dot").hidden = alerts.filter(function (a) { return !a.acknowledged; }).length === 0;
      if (section === "dashboard") renderRecentAlerts();
      if (section === "alerts") renderAlerts();
    }).catch(function () {});
  }

  // ---- DASHBOARD cards ----
  function renderDashboardCards() {
    var list = Object.keys(devices).map(function (k) { return devices[k]; });
    var online = list.filter(function (d) { return d.status === "online"; }).length;
    var offline = list.filter(function (d) { return d.status === "offline"; }).length;
    var inactive = list.filter(function (d) { return d.status === "inactive"; }).length;

    el("ls-online").textContent = online;
    el("ls-total").textContent = list.length;
    el("lg-online").textContent = online;
    el("lg-offline").textContent = offline;
    el("lg-inactive").textContent = inactive;
    var frac = list.length ? online / list.length : 0;
    el("donut-value").setAttribute("stroke-dasharray", (frac * DONUT_C) + " " + DONUT_C);

    var recent = list.slice().sort(function (a, b) {
      return new Date(b.last_seen_at) - new Date(a.last_seen_at);
    }).slice(0, 3);
    el("recent-devices").innerHTML = recent.map(function (d) {
      var loc = d.last_location;
      return '<div class="recent-row">' +
        '<span class="dot dot-' + d.status + '" style="margin-top:4px"></span>' +
        '<div style="flex:1;min-width:0">' +
          '<div class="rr-id">' + esc(d.id.slice(0, 18)) + '…</div>' +
          '<div class="rr-meta">' + (loc ? "Lat " + loc.lat.toFixed(5) + ", Lng " + loc.lng.toFixed(5) : "No data") + '</div>' +
          '<div style="margin-top:4px">' + motionPill(d) +
            ' <span class="rr-meta">' + (loc && loc.accuracy != null ? "Accuracy ±" + Math.round(loc.accuracy) + " m" : "") + '</span></div>' +
        '</div>' +
        '<span class="rr-meta">' + timeAgo(d.last_seen_at) + '</span></div>';
    }).join("") || '<div class="muted-sm">No devices yet.</div>';

    var latestAcc = accuracyHistory.length ? accuracyHistory[accuracyHistory.length - 1].v : null;
    el("acc-value").textContent = latestAcc != null ? "±" + Math.round(latestAcc) + " m" : "—";
    el("acc-label").textContent = latestAcc != null && latestAcc <= 15 ? "High accuracy" : "Tracking";
    renderBars();
  }

  function renderBars() {
    var max = 1;
    accuracyHistory.forEach(function (a) { max = Math.max(max, a.v); });
    el("acc-bars").innerHTML = accuracyHistory.map(function (a, i) {
      var h = Math.max(8, (a.v / max) * 58);
      var peak = i === accuracyHistory.length - 1;
      return '<div class="bar">' +
        '<div class="bar-fill ' + (peak ? "peak" : "") + '" style="height:' + h + 'px"></div>' +
        '<span class="bar-label">±' + Math.round(a.v) + '</span></div>';
    }).join("");
  }

  function renderRecentAlerts() {
    el("recent-alerts").innerHTML = (alerts.slice(0, 6).map(alertRow).join("")) ||
      '<tr><td colspan="4" class="muted-sm">No alerts.</td></tr>';
  }
  function alertRow(a) {
    return '<tr><td><div class="alert-type">' + alertIcon(a) +
      esc(humanType(a.type)) + '</div></td>' +
      '<td>' + esc(a.device_id.slice(0, 16)) + '…</td>' +
      '<td>' + esc(a.message) + '</td>' +
      '<td>' + timeAgo(a.created_at) + '</td></tr>';
  }
  function alertIcon(a) {
    var c = a.severity === "high" ? "var(--sev-high)" : a.severity === "medium" ? "var(--sev-medium)" : "var(--primary)";
    var glyph = a.type === "low_battery" ? "🔋" : a.type === "offline" ? "📶" : "⚠";
    return '<span class="alert-ico" style="background:' + c + '22;color:' + c + '">' + glyph + '</span>';
  }
  function humanType(t) {
    return ({ low_battery: "Low Battery", offline: "No Signal", gps_off: "GPS Disabled" }[t]) ||
      t.replace(/_/g, " ").replace(/\b\w/g, function (m) { return m.toUpperCase(); });
  }
  function motionPill(d) {
    if (d.status === "inactive") return '<span class="pill pill-inactive">Inactive</span>';
    var moving = d.last_location && (d.last_location.speed || 0) >= 1;
    return moving ? '<span class="pill pill-moving">Moving</span>'
      : '<span class="pill pill-stationary">Stationary</span>';
  }
  function statusPill(s) {
    return '<span class="pill pill-' + s + '">' + s.charAt(0).toUpperCase() + s.slice(1) + '</span>';
  }

  // ---- DEVICES section ----
  function renderDevices() {
    var q = (el("search").value || "").toLowerCase();
    var list = Object.keys(devices).map(function (k) { return devices[k]; })
      .filter(function (d) { return !q || d.id.toLowerCase().indexOf(q) >= 0; });
    var rows = list.map(function (d) {
      var loc = d.last_location;
      return '<tr><td>' + statusPill(d.status) + '</td>' +
        '<td>' + esc(d.id) + '</td>' +
        '<td>' + (loc ? loc.lat.toFixed(5) + ", " + loc.lng.toFixed(5) : "—") + '</td>' +
        '<td>' + (d.battery != null ? d.battery + "%" : "—") + '</td>' +
        '<td>' + (loc && loc.accuracy != null ? "±" + Math.round(loc.accuracy) + " m" : "—") + '</td>' +
        '<td>every ' + d.effective_interval_sec + 's</td>' +
        '<td>' + timeAgo(d.last_seen_at) + '</td></tr>';
    }).join("");
    el("devices-body").innerHTML =
      '<div class="m3-card list-card"><table class="data-table"><thead><tr>' +
      '<th>Status</th><th>Device</th><th>Position</th><th>Battery</th>' +
      '<th>Accuracy</th><th>Interval</th><th>Last seen</th></tr></thead><tbody>' +
      (rows || '<tr><td colspan="7" class="empty">No devices yet.</td></tr>') +
      '</tbody></table></div>';
  }

  // ---- ALERTS section ----
  function renderAlerts() {
    var rows = alerts.map(function (a) {
      return '<tr><td><div class="alert-type">' + alertIcon(a) + esc(humanType(a.type)) + '</div></td>' +
        '<td><span class="pill pill-' + (a.severity || "low") + '">' + esc(a.severity) + '</span></td>' +
        '<td>' + esc(a.device_id.slice(0, 18)) + '…</td>' +
        '<td>' + esc(a.message) + '</td>' +
        '<td>' + timeAgo(a.created_at) + '</td>' +
        '<td>' + (a.acknowledged ? '<span class="muted-sm">Acknowledged</span>'
          : '<button class="btn btn-ghost" data-ack="' + a.id + '">Acknowledge</button>') + '</td></tr>';
    }).join("");
    el("alerts-body").innerHTML =
      '<div class="m3-card list-card"><table class="data-table"><thead><tr>' +
      '<th>Type</th><th>Severity</th><th>Device</th><th>Message</th><th>Time</th><th></th>' +
      '</tr></thead><tbody>' +
      (rows || '<tr><td colspan="6" class="empty">No alerts.</td></tr>') +
      '</tbody></table></div>';
    el("alerts-body").querySelectorAll("[data-ack]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        apiSend("POST", "/api/alerts/" + btn.dataset.ack + "/ack").then(refreshData);
      });
    });
  }

  // ---- HISTORY section ----
  function renderHistory() {
    var ids = Object.keys(devices);
    el("history-body").innerHTML =
      '<div class="toolbar"><select id="hist-device">' +
      ids.map(function (id) { return '<option value="' + esc(id) + '">' + esc(id) + '</option>'; }).join("") +
      '</select><button class="btn btn-tonal" id="hist-load">Load track</button></div>' +
      '<div id="map-history"></div>' +
      '<div class="m3-card list-card" id="hist-summary"><span class="muted-sm">Pick a device and load its track.</span></div>';
    if (!historyMap) {
      historyMap = L.map("map-history").setView([20.5937, 78.9629], 5);
      refreshTiles(historyMap);
    } else {
      setTimeout(function () { historyMap.invalidateSize(); }, 100);
    }
    el("hist-load").addEventListener("click", loadHistory);
    if (ids.length) loadHistory();
  }
  function loadHistory() {
    var id = el("hist-device").value;
    if (!id) return;
    api("/api/devices/" + encodeURIComponent(id) + "/track?limit=5000").then(function (data) {
      var pts = (data.points || []).map(function (p) { return [p.lat, p.lng]; });
      el("hist-summary").innerHTML =
        '<div class="kv"><span>Total points</span><b>' + pts.length + '</b></div>' +
        '<div class="kv"><span>Distance</span><b>' + distanceKm(data.points || []).toFixed(2) + ' km</b></div>';
      if (!pts.length) return;
      // Snap the history track to roads too.
      snapToRoads(pts).then(function (snapped) {
        if (historyLayer) historyMap.removeLayer(historyLayer);
        historyLayer = L.polyline(snapped, {
          color: colorFor(id), weight: 4, opacity: 0.9, lineJoin: "round",
        }).addTo(historyMap);
        historyMap.fitBounds(historyLayer.getBounds().pad(0.2));
      });
    }).catch(function () {});
  }
  function distanceKm(pts) {
    var d = 0;
    for (var i = 1; i < pts.length; i++) d += haversine(pts[i - 1], pts[i]);
    return d / 1000;
  }
  function haversine(a, b) {
    var R = 6371000, toRad = Math.PI / 180;
    var dLat = (b.lat - a.lat) * toRad, dLng = (b.lng - a.lng) * toRad;
    var s = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(a.lat * toRad) * Math.cos(b.lat * toRad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
  }

  // ---- GEOFENCES section ----
  function renderGeofences() {
    el("geofences-body").innerHTML =
      '<p class="muted-sm" style="margin-bottom:10px">Click the map to place a geofence centre.</p>' +
      '<div id="map-geofences"></div>' +
      '<div class="toolbar">' +
      '<input id="geo-name" placeholder="Zone name" />' +
      '<input id="geo-radius" type="number" placeholder="Radius (m)" value="200" style="width:140px" />' +
      '<button class="btn btn-primary" id="geo-create">Create geofence</button></div>' +
      '<div id="geo-list" class="grid-cards"></div>';
    if (!geoMap) {
      geoMap = L.map("map-geofences").setView([20.5937, 78.9629], 5);
      refreshTiles(geoMap);
      geoMap.on("click", function (e) {
        geoPending = e.latlng;
        if (geoPendingMarker) geoMap.removeLayer(geoPendingMarker);
        geoPendingMarker = L.marker(e.latlng).addTo(geoMap);
      });
    } else {
      setTimeout(function () { geoMap.invalidateSize(); }, 100);
    }
    el("geo-create").addEventListener("click", function () {
      var name = el("geo-name").value.trim();
      var radius = parseFloat(el("geo-radius").value);
      if (!name || !geoPending || !(radius > 0)) {
        alert("Enter a name, click the map for a centre, and set a radius.");
        return;
      }
      apiSend("POST", "/api/geofences", {
        name: name, lat: geoPending.lat, lng: geoPending.lng, radius_m: radius,
      }).then(function () {
        el("geo-name").value = "";
        geoPending = null;
        if (geoPendingMarker) { geoMap.removeLayer(geoPendingMarker); geoPendingMarker = null; }
        loadGeofences();
      });
    });
    loadGeofences();
  }
  function loadGeofences() {
    api("/api/geofences").then(function (list) {
      if (geoLayer) geoMap.removeLayer(geoLayer);
      geoLayer = L.layerGroup().addTo(geoMap);
      (list || []).forEach(function (g) {
        L.circle([g.lat, g.lng], { radius: g.radius_m, color: "#6750A4", fillOpacity: 0.12 })
          .addTo(geoLayer).bindPopup(esc(g.name));
      });
      el("geo-list").innerHTML = (list || []).map(function (g) {
        return '<div class="m3-card list-card">' +
          '<div style="display:flex;justify-content:space-between;align-items:center">' +
          '<b>' + esc(g.name) + '</b>' +
          '<button class="btn btn-ghost" data-del="' + g.id + '">Delete</button></div>' +
          '<div class="kv"><span>Centre</span><span>' + g.lat.toFixed(4) + ", " + g.lng.toFixed(4) + '</span></div>' +
          '<div class="kv"><span>Radius</span><span>' + Math.round(g.radius_m) + ' m</span></div></div>';
      }).join("") || '<div class="empty">No geofences yet.</div>';
      el("geo-list").querySelectorAll("[data-del]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          apiSend("DELETE", "/api/geofences/" + btn.dataset.del).then(loadGeofences);
        });
      });
    });
  }

  // ---- REPORTS section ----
  function renderReports() {
    var ids = Object.keys(devices);
    el("reports-body").innerHTML =
      '<div class="m3-card list-card">' +
      '<p class="muted-sm" style="margin-bottom:12px">Export a device\'s full location history as CSV.</p>' +
      '<div class="toolbar"><select id="rep-device">' +
      ids.map(function (id) { return '<option value="' + esc(id) + '">' + esc(id) + '</option>'; }).join("") +
      '</select><button class="btn btn-primary" id="rep-download">Download CSV</button></div></div>';
    el("rep-download").addEventListener("click", function () {
      var id = el("rep-device").value;
      if (id) window.open("/api/devices/" + encodeURIComponent(id) + "/export.csv", "_blank");
    });
  }

  // ---- SETTINGS section ----
  function renderSettings() {
    api("/api/settings").then(function (s) {
      el("settings-body").innerHTML =
        '<div class="m3-card list-card">' +
        '<div class="setting-row"><div><b>Default capture interval</b><br>' +
        '<span class="muted-sm">How often devices report location (seconds).</span></div>' +
        '<input id="set-interval" type="number" value="' + s.default_capture_interval_sec + '" style="width:120px" /></div>' +
        '<div class="setting-row"><div><b>Theme</b><br><span class="muted-sm">Light or dark dashboard.</span></div>' +
        '<button class="btn btn-ghost" id="set-theme">Toggle theme</button></div>' +
        '<div style="margin-top:14px"><button class="btn btn-primary" id="set-save">Save settings</button>' +
        ' <span id="set-msg" class="muted-sm"></span></div></div>';
      el("set-theme").addEventListener("click", function () {
        applyTheme(document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark");
      });
      el("set-save").addEventListener("click", function () {
        var v = parseInt(el("set-interval").value, 10);
        if (!(v >= 10)) { el("set-msg").textContent = "Interval must be ≥ 10."; return; }
        apiSend("PUT", "/api/settings", { default_capture_interval_sec: v }).then(function (r) {
          el("set-msg").textContent = r.ok ? "Saved." : "Save failed.";
          refreshData();
        });
      });
    });
  }

  // ---- boot ----
  function init() {
    applyTheme(localStorage.getItem("gps-theme") || "light");

    map = L.map("map", { zoomControl: true }).setView([20.5937, 78.9629], 5);
    refreshTiles(map);

    document.querySelectorAll("[data-section]").forEach(function (n) {
      n.addEventListener("click", function () { showSection(n.dataset.section); });
    });
    el("theme-toggle").addEventListener("click", function () {
      applyTheme(document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark");
    });
    el("bell").addEventListener("click", function () { showSection("alerts"); });
    el("search").addEventListener("input", function () {
      if (section === "devices") renderDevices();
    });
    el("map-expand").addEventListener("click", function () {
      var card = el("map-card");
      var expanded = card.classList.toggle("expanded");
      el("map-expand").innerHTML = expanded ? "&#10005;" : "&#9974;";
      setTimeout(function () { map.invalidateSize(); }, 260);
    });

    connect();
    refreshData();
    setInterval(refreshData, 15000);
  }

  document.addEventListener("DOMContentLoaded", init);
})();
