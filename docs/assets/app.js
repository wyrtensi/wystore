/* Wy Store — the guided deck: a star field for the arrival, scene navigation, no page scroll. */

(function () {
  "use strict";

  var root = document.documentElement;
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)");

  /* ---------------------------------------------------------------- stars */

  var canvas = document.getElementById("warp");
  /* Nothing is drawn behind the field, so the context is opaque: the frame is
     painted over, not cleared and blended. */
  var ctx = canvas && canvas.getContext ? canvas.getContext("2d", { alpha: false }) : null;
  var INK = getComputedStyle(root).getPropertyValue("--ink").trim() || "#0e0e0e";
  var stars = [];
  var W = 0;
  var H = 0;
  var dpr = 1;
  var speed = 0;
  var target = 0;
  /* Streak length follows the speed with a lag of its own, so while the field
     brakes the stars are still drawn long: the stretch outlives the motion. */
  var stretch = 0;
  var DRIFT = 0.22;
  /* Ripples: a ring that spreads from a point and pushes stars outward as it
     passes, the way a drop moves the surface of water. Each is {x, y, t0} in
     canvas pixels and milliseconds. */
  var ripples = [];
  /* Rings drawn around the button itself: {x, y, w, h, t0} in canvas pixels,
     two per press, staggered. Drawn on the canvas so nothing can clip them. */
  var rings = [];
  var RING_LIFE = 1300;
  var RING_GAP = 220;
  var RIPPLE_SPEED = 0.85;
  var RIPPLE_WIDTH = 130;
  var RIPPLE_PUSH = 34;
  var RIPPLE_LIFE = 2300;

  /* Alpha and width follow z alone, so the field is drawn as one path per tier
     rather than one per star: sixteen strokes a frame instead of two hundred.
     Each star keeps its own place and length inside the path. This is not the
     same pixels — a stroke over many subpaths is rasterised a little
     differently from many strokes over one each, whatever the tier count, so
     sixteen is simply the fewest paths, not a balance struck. What holds is
     the field: measured against the old drawing, its total light differs by
     0.07 per cent, and at four times life size the two cannot be told apart. */
  var TIERS = 16;
  var tiers = [];
  for (var t = 0; t < TIERS; t++) tiers.push([]);

  function seedStar(anywhere) {
    var z = Math.random() * 0.85 + 0.15;
    return {
      x: Math.random() * W,
      y: anywhere ? Math.random() * H : H + Math.random() * H * 0.3,
      z: z,
      tier: Math.min(TIERS - 1, Math.floor(((z - 0.15) / 0.85) * TIERS))
    };
  }

  var lastW = 0;
  var lastH = 0;

  function sizeCanvas(reseed) {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    lastW = window.innerWidth;
    lastH = window.innerHeight;
    W = canvas.width = Math.floor(lastW * dpr);
    H = canvas.height = Math.floor(lastH * dpr);
    canvas.style.width = lastW + "px";
    canvas.style.height = lastH + "px";
    /* Resizing the backing store resets the context, so the state it keeps for
       the whole run is set here rather than once per frame. An opaque canvas
       starts black, not clear, so the ground is laid at once: until the next
       frame the page must look exactly as it did with nothing drawn. */
    ctx.lineCap = "round";
    ctx.fillStyle = INK;
    ctx.fillRect(0, 0, W, H);
    if (!reseed) return;
    var count = Math.round(Math.min(260, Math.max(90, (lastW * lastH) / 5200)));
    stars = [];
    for (var i = 0; i < count; i++) stars.push(seedStar(true));
  }

  function paint(now) {
    ctx.fillStyle = INK;
    ctx.fillRect(0, 0, W, H);
    /* A ripple moves and brightens each star by its own distance to the front,
       so while one is alive the field goes back to being drawn star by star. */
    if (ripples.length) paintStars(now);
    else paintTiers();
    for (var q = 0; q < rings.length; q++) paintRing(rings[q], now);
  }

  function paintTiers() {
    for (var t = 0; t < TIERS; t++) tiers[t].length = 0;
    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      tiers[s.tier].push(s);
    }
    for (var k = 0; k < TIERS; k++) {
      var bucket = tiers[k];
      if (!bucket.length) continue;
      /* The middle of the tier stands for all of it. */
      var z = 0.15 + ((k + 0.5) / TIERS) * 0.85;
      ctx.strokeStyle = "rgba(242,239,233," + Math.min(1, 0.1 + z * 0.5) + ")";
      ctx.lineWidth = (0.6 + z * 0.9) * dpr;
      ctx.beginPath();
      for (var j = 0; j < bucket.length; j++) {
        var b = bucket[j];
        ctx.moveTo(b.x, b.y);
        ctx.lineTo(b.x, b.y + (1.2 + stretch * b.z * 9) * dpr);
      }
      ctx.stroke();
    }
  }

  function paintStars(now) {
    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      var len = (1.2 + stretch * s.z * 9) * dpr;
      var alpha = 0.1 + s.z * 0.5;
      var x = s.x;
      var y = s.y;

      for (var r = 0; r < ripples.length; r++) {
        var rp = ripples[r];
        var age = now - rp.t0;
        var dx = x - rp.x;
        var dy = y - rp.y;
        var d = Math.sqrt(dx * dx + dy * dy) || 1;
        var front = age * RIPPLE_SPEED * dpr;
        var k = (d - front) / (RIPPLE_WIDTH * dpr);
        if (k > 2 || k < -2) continue;
        /* Gaussian ring that swells over its first moments, then fades with
           age; nearer stars move more. */
        var rise = Math.min(1, age / 220);
        var g = Math.exp(-k * k * 1.6) * rise * Math.pow(1 - age / RIPPLE_LIFE, 1.4);
        var push = g * RIPPLE_PUSH * dpr * (0.5 + s.z);
        x += (dx / d) * push;
        y += (dy / d) * push;
        alpha += g * 0.45;
      }

      ctx.strokeStyle = "rgba(242,239,233," + Math.min(1, alpha) + ")";
      ctx.lineWidth = (0.6 + s.z * 0.9) * dpr;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(x, y + len);
      ctx.stroke();
    }
  }

  function easeOut(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  function paintRing(rg, now) {
    for (var n = 0; n < 2; n++) {
      var p = (now - rg.t0 - n * RING_GAP) / RING_LIFE;
      if (p <= 0 || p >= 1) continue;
      var e = easeOut(p);
      /* Swells in over the first stretch, then fades as it grows. */
      var a = Math.min(1, p / 0.12) * (1 - p) * 0.75;
      var w = rg.w + rg.w * 0.7 * e;
      var h = rg.h + rg.h * 1.3 * e;
      var x = rg.x - w / 2;
      var y = rg.y - h / 2;
      ctx.strokeStyle = "rgba(138,172,255," + a + ")";
      ctx.lineWidth = 1 * dpr;
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(x, y, w, h, h / 2);
      else ctx.rect(x, y, w, h);
      ctx.stroke();
    }
  }

  /* Integrated against real time, so a throttled tab settles at the same rate. */
  var last = 0;

  function step(now) {
    var dt = last ? Math.min(96, now - last) / 16.67 : 1;
    last = now;
    speed += (target - speed) * (1 - Math.pow(1 - 0.05, dt));
    stretch += (speed - stretch) * (1 - Math.pow(1 - 0.018, dt));
    var move = speed * dpr * dt;
    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      s.y -= move * s.z * 3.2;
      if (s.y < -120) {
        stars[i] = seedStar(false);
        stars[i].y = H + Math.random() * 60;
      }
    }
    for (var r = ripples.length - 1; r >= 0; r--) {
      if (now - ripples[r].t0 > RIPPLE_LIFE) ripples.splice(r, 1);
    }
    for (var q = rings.length - 1; q >= 0; q--) {
      if (now - rings[q].t0 > RING_LIFE + RING_GAP) rings.splice(q, 1);
    }
    paint(now);
    window.requestAnimationFrame(step);
  }

  function burst(v) {
    speed = Math.max(speed, v);
  }

  function ripple(box) {
    var now = performance.now();
    var cx = (box.left + box.width / 2) * dpr;
    var cy = (box.top + box.height / 2) * dpr;
    ripples.push({ x: cx, y: cy, t0: now });
    rings.push({ x: cx, y: cy, w: box.width * dpr, h: box.height * dpr, t0: now });
  }

  if (ctx) {
    sizeCanvas(true);
    /* A page laid out at zero — prerendered, or in a frame with no room yet —
       gets no resize when it finally has some, and the field would never
       appear. Frames only run once there is something to show, so the first
       one measures again. */
    if (!W || !H) {
      window.requestAnimationFrame(function again() {
        if (!window.innerWidth || !window.innerHeight) return window.requestAnimationFrame(again);
        sizeCanvas(true);
      });
    }
    var resizeTimer;
    window.addEventListener("resize", function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function () {
        /* Android shows and hides its address bar as you move through the page,
           and every such move is a resize with nothing but the height changed.
           The buffer follows it; the field is only re-seeded on a real one, so
           the stars do not jump under the reader. */
        sizeCanvas(window.innerWidth !== lastW || Math.abs(window.innerHeight - lastH) > 120);
        if (reduced.matches) paint(performance.now());
      }, 180);
    });

    if (reduced.matches) {
      speed = 0;
      target = 0;
      stretch = 0;
      paint(performance.now());
    } else {
      speed = 38;
      target = 38;
      stretch = 4;
      window.requestAnimationFrame(step);
      setTimeout(function () {
        target = DRIFT;
      }, 560);
    }
  }

  /* -------------------------------------------------------------- scenes */

  var deck = document.getElementById("deck");
  if (!deck) return;

  var scenes = Array.prototype.slice.call(deck.querySelectorAll(".scene"));
  var screens = Array.prototype.slice.call(document.querySelectorAll(".scr"));
  var index = 0;
  var locked = false;

  /* ------------------------------------------------------------- pictures */

  /* Only the first handset shot is in the markup with a source. The rest — and
     the Play Protect dialog on the last scene — carry data-src and are fetched
     one at a time once the page itself is up, so the arrival is not competing
     with six screenshots nobody is looking at yet. A picture whose scene is
     reached before its turn is fetched at once; the queue then skips it. */

  function fetchImage(img, then) {
    var src = img.getAttribute("data-src");
    if (!src) return false;
    img.removeAttribute("data-src");
    if (then) {
      img.addEventListener("load", then, { once: true });
      img.addEventListener("error", then, { once: true });
    }
    img.src = src;
    return true;
  }

  function pump() {
    var waiting = document.querySelector("img[data-src]");
    if (waiting) fetchImage(waiting, pump);
  }

  function fetchNow(el) {
    if (!el) return;
    var waiting = el.querySelectorAll ? el.querySelectorAll("img[data-src]") : [];
    for (var i = 0; i < waiting.length; i++) fetchImage(waiting[i]);
  }

  /* The handset only changes once the new picture is there to change to, so a
     scene reached before its turn in the queue holds the shot it had rather
     than showing an empty screen for as long as the fetch takes. */
  function showScreen(k) {
    var img = screens[k];
    fetchImage(img);
    if (img.complete && img.naturalWidth) {
      for (var i = 0; i < screens.length; i++) screens[i].classList.toggle("is-on", i === k);
      return;
    }
    var at = index;
    /* On an error the swap still happens: an empty screen is honest, a shot
       from another scene is not. */
    var swap = function () {
      if (index !== at) return;
      for (var i = 0; i < screens.length; i++) screens[i].classList.toggle("is-on", i === k);
    };
    img.addEventListener("load", swap, { once: true });
    img.addEventListener("error", swap, { once: true });
  }

  function screenFor(i) {
    var name = scenes[i].getAttribute("data-screen");
    if (!name) return -1;
    for (var k = 0; k < screens.length; k++) {
      if (screens[k].getAttribute("data-name") === name) return k;
    }
    return -1;
  }

  function show(next, silent) {
    next = Math.max(0, Math.min(scenes.length - 1, next));
    if (next === index && silent !== "force") return;

    scenes[index].classList.remove("is-on");
    scenes[index].setAttribute("aria-hidden", "true");
    index = next;
    scenes[index].classList.add("is-on");
    scenes[index].setAttribute("aria-hidden", "false");
    scenes[index].scrollTop = 0;

    var s = screenFor(index);
    if (s > -1) showScreen(s);
    fetchNow(scenes[index]);

    root.setAttribute("data-scene", scenes[index].id);

    /* The scene left behind becomes aria-hidden, so focus must not stay in it. */
    if (!silent && scenes[index].focus) scenes[index].focus({ preventScroll: true });

    if (!reduced.matches && silent !== "force") burst(index === scenes.length - 1 ? 14 : 7);
    if (!silent && history.replaceState) history.replaceState(null, "", "#" + scenes[index].id);

    locked = true;
    setTimeout(function () {
      locked = false;
    }, 420);
  }

  /* A scene tall enough to scroll keeps the gesture until it reaches its edge. */
  function absorbs(down) {
    var s = scenes[index];
    if (s.scrollHeight <= s.clientHeight + 1) return false;
    if (down) return s.scrollTop + s.clientHeight < s.scrollHeight - 1;
    return s.scrollTop > 1;
  }

  function next() {
    show(index + 1);
  }

  function prev() {
    show(index - 1);
  }

  /* Controls */

  deck.addEventListener("click", function (e) {
    var go = e.target.closest("[data-go]");
    if (go) {
      var v = go.getAttribute("data-go");
      if (v === "next") next();
      else if (v === "prev") prev();
      else show(scenes.findIndex(function (s) { return s.id === v; }));
      if (go.tagName !== "A") e.preventDefault();
    }
  });

  document.addEventListener("click", function (e) {
    var dl = e.target.closest("[data-download]");
    if (!dl) return;
    if (!reduced.matches && ctx) ripple(dl.getBoundingClientRect());
    var to = scenes.findIndex(function (s) { return s.id === "install"; });
    if (to > -1) setTimeout(function () { show(to); }, 480);
  });

  document.addEventListener("keydown", function (e) {
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    var tag = (e.target.tagName || "").toLowerCase();
    if (tag === "input" || tag === "textarea") return;
    if (e.key === "ArrowRight" || e.key === "ArrowDown" || e.key === "PageDown") { next(); e.preventDefault(); }
    else if (e.key === "ArrowLeft" || e.key === "ArrowUp" || e.key === "PageUp") { prev(); e.preventDefault(); }
    else if (e.key === " " && tag !== "a" && tag !== "button") { next(); e.preventDefault(); }
    else if (e.key === "Home") { show(0); }
    else if (e.key === "End") { show(scenes.length - 1); }
  });

  var wheelAt = 0;
  window.addEventListener(
    "wheel",
    function (e) {
      if (locked) return;
      var now = Date.now();
      if (now - wheelAt < 620) return;
      if (Math.abs(e.deltaY) < 14) return;
      if (absorbs(e.deltaY > 0)) return;
      wheelAt = now;
      if (e.deltaY > 0) next();
      else prev();
    },
    { passive: true }
  );

  var tx = 0;
  var ty = 0;
  window.addEventListener(
    "touchstart",
    function (e) {
      tx = e.changedTouches[0].clientX;
      ty = e.changedTouches[0].clientY;
    },
    { passive: true }
  );

  window.addEventListener(
    "touchend",
    function (e) {
      var dx = e.changedTouches[0].clientX - tx;
      var dy = e.changedTouches[0].clientY - ty;
      if (Math.abs(dy) > 56 && Math.abs(dy) > Math.abs(dx)) {
        if (absorbs(dy < 0)) return;
        if (dy < 0) next();
        else prev();
      } else if (Math.abs(dx) > 64 && Math.abs(dx) > Math.abs(dy)) {
        if (dx < 0) next();
        else prev();
      }
    },
    { passive: true }
  );

  /* Start */

  var fromHash = scenes.findIndex(function (s) { return "#" + s.id === location.hash; });
  show(fromHash > -1 ? fromHash : 0, "force");

  var pumping = false;

  function startQueue() {
    if (pumping) return;
    pumping = true;
    pump();
  }

  if (document.readyState === "complete") startQueue();
  else {
    window.addEventListener("load", startQueue, { once: true });
    /* The download badge is fetched from a third party and can take seconds,
       and "load" waits for it. The queue does not. */
    setTimeout(startQueue, 2500);
  }

  window.addEventListener("hashchange", function () {
    var n = scenes.findIndex(function (s) { return "#" + s.id === location.hash; });
    if (n > -1) show(n, true);
  });
})();
