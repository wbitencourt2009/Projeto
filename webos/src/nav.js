// Spatial navigation for the TV remote. Focusable elements carry data-f; handlers
// are attached as el._onclick / el._onlong / el._onfocus (see util.h).

export const KEY = {
  LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13,
  BACK: 461, BACKSPACE: 8, ESC: 27,
  CH_UP: 33, CH_DOWN: 34,
  PLAY: 415, PAUSE: 19, PLAY_PAUSE: 179, STOP: 413, FF: 417, RW: 412,
};

let current = null;
let handlers = { back: () => false, key: () => false };
let enterDownAt = 0;
let longTimer = null;
let longFired = false;

export function setHandlers(h) { handlers = h; }

export function focused() { return current; }

function visible(el) {
  if (!document.body.contains(el)) return false;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0;
}

export function focus(el, opts) {
  if (!el || !visible(el)) return false;
  if (current && current !== el) current.classList.remove('focused');
  current = el;
  el.classList.add('focused');
  if (el.tagName === 'INPUT') { /* keep DOM focus off inputs until OK is pressed */ }
  scrollIntoView(el);
  if (!(opts && opts.silent) && el._onfocus) el._onfocus();
  return true;
}

function scrollIntoView(el) {
  if (el.scrollIntoViewIfNeeded) el.scrollIntoViewIfNeeded(false);
  else el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

/** Focus the first element marked data-autofocus, else the first focusable. */
export function focusFirst(root) {
  const scope = root || document;
  const auto = scope.querySelector('[data-f][data-autofocus]');
  if (auto && focus(auto)) return;
  const all = scope.querySelectorAll('[data-f]');
  for (const el of all) if (focus(el)) return;
}

function candidates() {
  const dialog = document.querySelector('.dialog');
  const scope = dialog || document;
  return Array.prototype.filter.call(scope.querySelectorAll('[data-f]'), visible);
}

function move(dir) {
  const list = candidates();
  if (!current || list.indexOf(current) < 0) {
    if (list.length) focus(list[0]);
    return;
  }
  const a = current.getBoundingClientRect();
  const ax = a.left + a.width / 2;
  const ay = a.top + a.height / 2;
  let best = null;
  let bestScore = Infinity;
  for (const el of list) {
    if (el === current) continue;
    const b = el.getBoundingClientRect();
    const bx = b.left + b.width / 2;
    const by = b.top + b.height / 2;
    let main;
    let cross;
    let overlap;
    if (dir === 'left' || dir === 'right') {
      main = dir === 'right' ? b.left - a.right + 1 : a.left - b.right + 1;
      if (main < -2 || (dir === 'right' ? bx <= ax : bx >= ax)) continue;
      overlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
      cross = Math.abs(by - ay);
    } else {
      main = dir === 'down' ? b.top - a.bottom + 1 : a.top - b.bottom + 1;
      if (main < -2 || (dir === 'down' ? by <= ay : by >= ay)) continue;
      overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
      cross = Math.abs(bx - ax);
    }
    const score = Math.max(main, 0) + (overlap > 0 ? cross * 0.3 : cross * 2 + 400);
    if (score < bestScore) { bestScore = score; best = el; }
  }
  if (best) focus(best);
  else if (current._onedge) current._onedge(dir);
}

function activate() {
  if (!current) return;
  if (current.tagName === 'INPUT') { current.focus(); return; }
  if (current._onclick) current._onclick();
}

function onKeyDown(e) {
  const k = e.keyCode;
  const input = document.activeElement && document.activeElement.tagName === 'INPUT' ? document.activeElement : null;

  if (input) {
    // While typing, only leave the field on up/down/back/enter.
    if (k === KEY.UP || k === KEY.DOWN || k === KEY.ENTER || k === KEY.BACK || k === KEY.ESC) {
      input.blur();
      if (k === KEY.ENTER && input._onsubmit) input._onsubmit();
      if (k === KEY.UP || k === KEY.DOWN) move(k === KEY.UP ? 'up' : 'down');
      e.preventDefault();
    }
    return;
  }

  if (handlers.key(e)) { e.preventDefault(); return; }

  switch (k) {
    case KEY.LEFT: move('left'); break;
    case KEY.RIGHT: move('right'); break;
    case KEY.UP: move('up'); break;
    case KEY.DOWN: move('down'); break;
    case KEY.ENTER:
      if (!enterDownAt) {
        enterDownAt = Date.now();
        longFired = false;
        if (current && current._onlong) {
          longTimer = setTimeout(() => { longFired = true; current && current._onlong && current._onlong(); }, 650);
        }
      }
      break;
    case KEY.BACK: case KEY.BACKSPACE: case KEY.ESC:
      if (!handlers.back()) {
        if (window.webOS && window.webOS.platformBack) window.webOS.platformBack();
        else window.close();
      }
      break;
    default: return;
  }
  e.preventDefault();
}

function onKeyUp(e) {
  if (e.keyCode !== KEY.ENTER || !enterDownAt) return;
  clearTimeout(longTimer);
  enterDownAt = 0;
  if (!longFired) activate();
  e.preventDefault();
}

export function install() {
  document.addEventListener('keydown', onKeyDown, true);
  document.addEventListener('keyup', onKeyUp, true);
  // Pointer / magic remote: hover focuses, click activates.
  document.addEventListener('mouseover', (e) => {
    const el = e.target.closest && e.target.closest('[data-f]');
    if (el && el !== current) focus(el);
  });
  document.addEventListener('click', (e) => {
    const el = e.target.closest && e.target.closest('[data-f]');
    if (!el) return;
    focus(el, { silent: true });
    activate();
  });
  document.addEventListener('contextmenu', (e) => {
    const el = e.target.closest && e.target.closest('[data-f]');
    if (el && el._onlong) { e.preventDefault(); el._onlong(); }
  });
}
