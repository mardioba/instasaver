package com.instasaver.app.data.downloader

/**
 * JavaScript executado dentro da WebView para localizar a mídia da publicação.
 *
 * O endereço da CDN mudou: em vez de `scontent*.cdninstagram.com`, o Instagram
 * serve de `instagram.*.fna.fbcdn.net`. Um filtro que exigia "scontent" descartava
 * a imagem correta — o diagnóstico registrava `imgCount:4` com `imgs:[]`, três
 * avatares de 150px e a foto da publicação em 1248x1248, todas no fbcdn.
 *
 * Por isso aceitamos qualquer host da Meta (fbcdn, cdninstagram, scontent),
 * descartamos avatares (abaixo de 320px) e escolhemos a maior imagem. Quando
 * há `srcset`, preferimos a maior resolução listada — é o que garante a foto
 * em alta qualidade.
 *
 * Devolve "K|url", com K igual a V (vídeo) ou I (imagem). Vazio se ainda não
 * há mídia na página.
 */
internal val PROBE_JS: String = """
(function() {
  var cdn = function(s) {
    if (typeof s !== 'string' || s.indexOf('https') !== 0) return false;
    // Host de JS/CSS e ícones, nunca de mídia.
    if (s.indexOf('static.cdninstagram.com') !== -1) return false;
    return s.indexOf('fbcdn') !== -1 || s.indexOf('cdninstagram') !== -1 || s.indexOf('scontent') !== -1;
  };
  // Vídeo tem prioridade: um post de vídeo é mais específico que uma foto.
  try {
    var vids = document.getElementsByTagName('video');
    for (var v = 0; v < vids.length; v++) {
      var vs = vids[v].src || vids[v].currentSrc || '';
      if (cdn(vs)) return 'V|' + vs;
    }
  } catch (e) {}
  try {
    var imgs = document.getElementsByTagName('img');
    var best = '', bestArea = 0;
    for (var i = 0; i < imgs.length; i++) {
      var el = imgs[i];
      var w = el.naturalWidth || 0, h = el.naturalHeight || 0;
      // Avatar/perfil fica abaixo de 320px; ignoramos para não baixar a foto
      // de perfil no lugar da publicação.
      if (w < 320 && h < 320) continue;
      var src = el.currentSrc || el.src || '';
      // srcset tem a lista de resoluções; pegamos a maior para alta qualidade.
      var ss = el.getAttribute('srcset') || '';
      if (ss) {
        var parts = ss.split(',');
        var bestW = 0, bestU = '';
        for (var p = 0; p < parts.length; p++) {
          var seg = parts[p].trim().split(/\s+/);
          var u = seg[0] || '';
          var sw = parseFloat(String(seg[1] || '0').replace('w', '')) || 0;
          if (cdn(u) && sw > bestW) { bestW = sw; bestU = u; }
        }
        if (bestU) src = bestU;
      }
      if (!cdn(src)) continue;
      var area = w * h;
      if (area > bestArea) { bestArea = area; best = src; }
    }
    if (best) return 'I|' + best;
  } catch (e) {}
  return '';
})();
"""

/**
 * Diagnóstico sem filtro de domínio. Mostra quantos elementos a página montou,
 * de quais hosts vêm as imagens e se há tela de login — é o que permitiu
 * descobrir que a foto estava no fbcdn e não no scontent.
 */
internal val DIAG_JS: String = """
(function() {
  var out = {
    title: '', imgCount: 0, videoCount: 0, biggest: '0x0',
    imgSrcs: [], videoSrcs: [], hosts: {},
    bodyLen: 0, hasLoginForm: false
  };
  try { out.title = (document.title || '').slice(0, 45); } catch (e) {}
  try {
    var imgs = document.getElementsByTagName('img');
    out.imgCount = imgs.length;
    var bw = 0, bh = 0;
    for (var i = 0; i < imgs.length; i++) {
      var el = imgs[i];
      var w = el.naturalWidth || 0, h = el.naturalHeight || 0;
      if (w * h > bw * bh) { bw = w; bh = h; }
      var s = el.src || '';
      try {
        var host = s ? s.split('/')[2] : '(vazio)';
        out.hosts[host] = (out.hosts[host] || 0) + 1;
      } catch (e2) {}
      if (out.imgSrcs.length < 10) out.imgSrcs.push(w + 'x' + h + ' ' + s.slice(0, 85));
    }
    out.biggest = bw + 'x' + bh;
  } catch (e) {}
  try {
    var vids = document.getElementsByTagName('video');
    out.videoCount = vids.length;
    for (var v = 0; v < vids.length && out.videoSrcs.length < 4; v++) {
      out.videoSrcs.push((vids[v].src || vids[v].currentSrc || '').slice(0, 100));
    }
  } catch (e) {}
  try { out.bodyLen = (document.body && document.body.innerText || '').length; } catch (e) {}
  try { out.hasLoginForm = !!document.querySelector('input[name="username"]'); } catch (e) {}
  return JSON.stringify(out);
})();
"""
