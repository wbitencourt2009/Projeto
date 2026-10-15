// <video> wrapper: native HLS on webOS, hls.js elsewhere (desktop preview).
import Hls from 'hls.js/dist/hls.light.min.js';

export function createVideo() {
  const video = document.createElement('video');
  video.className = 'video';
  video.setAttribute('autoplay', '');
  video.setAttribute('playsinline', '');
  let hls = null;

  function stop() {
    if (hls) { hls.destroy(); hls = null; }
    video.removeAttribute('src');
    try { video.load(); } catch (e) { /* ignore */ }
  }

  function play(url, onError) {
    stop();
    const isHls = /\.m3u8(\?|$)/i.test(url);
    const native = video.canPlayType('application/vnd.apple.mpegurl');
    if (isHls && !native && Hls && Hls.isSupported()) {
      hls = new Hls({ enableWorker: true, lowLatencyMode: false });
      hls.on(Hls.Events.ERROR, (_, data) => { if (data && data.fatal) onError('hls'); });
      hls.loadSource(url);
      hls.attachMedia(video);
    } else {
      video.src = url;
    }
    video.onerror = () => onError('media');
    const p = video.play();
    if (p && p.catch) p.catch(() => { /* autoplay policies on desktop */ });
  }

  return { el: video, play, stop };
}
