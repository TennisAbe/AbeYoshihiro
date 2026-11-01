(() => {
  'use strict';
  document.documentElement.classList.add('js');
  const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');
  const toggle = document.querySelector('.motion-toggle');
  const canvas = document.getElementById('palace-particles');
  const context = canvas ? canvas.getContext('2d') : null;
  const glow = document.querySelector('.cursor-glow');
  const cards = [...document.querySelectorAll('.character-card')];
  const animations = new Set();
  let saved = 'on';
  try { saved = localStorage.getItem('noctalith-motion') || 'on'; } catch (_) { /* Storage may be unavailable. */ }
  let enabled = false;
  let frame = 0;
  let lastTime = 0;
  let width = 0;
  let height = 0;
  let particles = [];
  let pointer = null;

  function animate(element, keyframes, options) {
    if (!enabled || !element.animate) return;
    const animation = element.animate(keyframes, options);
    animations.add(animation);
    animation.onfinish = animation.oncancel = () => animations.delete(animation);
  }

  function resize() {
    if (!context) return;
    width = window.innerWidth;
    height = window.innerHeight;
    const ratio = Math.min(window.devicePixelRatio || 1, 1.5);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    particles = Array.from({ length: width < 640 ? 18 : 42 }, () => ({
      x: Math.random() * width, y: Math.random() * height,
      size: Math.random() * 1.2 + .5, speed: Math.random() * .16 + .06,
      phase: Math.random() * Math.PI * 2
    }));
  }

  function draw(time) {
    frame = 0;
    if (!enabled || document.hidden) return;
    if (time - lastTime >= 32) {
      const delta = Math.min((time - lastTime) / 16.67, 3);
      lastTime = time;
      if (context) {
        context.clearRect(0, 0, width, height);
        particles.forEach((p, index) => {
          p.y -= p.speed * delta;
          p.x += Math.sin(time * .0003 + p.phase) * .09 * delta;
          if (p.y < -5) { p.y = height + 5; p.x = Math.random() * width; }
          const alpha = .25 + (Math.sin(time * .0007 + p.phase) + 1) * .2;
          context.fillStyle = index % 3 ? `rgba(216,186,132,${alpha})` : `rgba(192,149,234,${alpha})`;
          context.beginPath();
          context.arc(p.x, p.y, p.size, 0, Math.PI * 2);
          context.fill();
        });
      }
      if (glow && pointer) glow.style.transform = `translate3d(${pointer.x}px, ${pointer.y}px, 0)`;
    }
    if (context || glow) frame = requestAnimationFrame(draw);
  }

  function start() {
    if (!frame && enabled && !document.hidden) {
      lastTime = performance.now();
      frame = requestAnimationFrame(draw);
    }
  }

  function refresh() {
    enabled = saved !== 'off' && !preference.matches;
    document.body.classList.toggle('motion-on', enabled);
    document.body.classList.toggle('motion-off', !enabled);
    if (toggle) {
      toggle.textContent = enabled ? '演出：ON' : '演出：OFF';
      toggle.setAttribute('aria-pressed', String(enabled));
      toggle.disabled = preference.matches;
      toggle.title = preference.matches ? '端末の「視差効果を減らす」設定に合わせて演出を停止しています' : '動きのある演出を切り替える';
    }
    if (!enabled) {
      cancelAnimationFrame(frame); frame = 0;
      animations.forEach(animation => animation.cancel());
      context?.clearRect(0, 0, width, height);
      cards.forEach(card => { card.style.removeProperty('--rx'); card.style.removeProperty('--ry'); });
    } else start();
  }

  toggle?.addEventListener('click', () => {
    saved = enabled ? 'off' : 'on';
    try { localStorage.setItem('noctalith-motion', saved); } catch (_) { /* Keep the current page usable. */ }
    refresh();
  });
  preference.addEventListener('change', refresh);
  document.addEventListener('visibilitychange', () => {
    cancelAnimationFrame(frame); frame = 0;
    if (!document.hidden) start();
  });
  window.addEventListener('resize', resize, { passive: true });
  window.addEventListener('pointermove', event => {
    if (enabled && finePointer.matches) pointer = { x: event.clientX, y: event.clientY };
  }, { passive: true });

  cards.forEach(card => {
    card.addEventListener('pointermove', event => {
      if (!enabled || !finePointer.matches) return;
      const rect = card.getBoundingClientRect();
      const x = (event.clientX - rect.left) / rect.width;
      const y = (event.clientY - rect.top) / rect.height;
      card.style.setProperty('--rx', `${(0.5 - y) * 5}deg`);
      card.style.setProperty('--ry', `${(x - 0.5) * 7}deg`);
      card.style.setProperty('--mx', `${x * 100}%`);
      card.style.setProperty('--my', `${y * 100}%`);
    }, { passive: true });
    card.addEventListener('pointerleave', () => {
      card.style.removeProperty('--rx'); card.style.removeProperty('--ry');
    });
  });

  document.querySelectorAll('.button, button').forEach(button => {
    button.addEventListener('click', event => {
      if (!enabled || !button.animate || button.disabled) return;
      const rect = button.getBoundingClientRect();
      const ripple = document.createElement('span');
      ripple.className = 'ripple';
      ripple.setAttribute('aria-hidden', 'true');
      ripple.style.left = `${event.detail ? event.clientX - rect.left : rect.width / 2}px`;
      ripple.style.top = `${event.detail ? event.clientY - rect.top : rect.height / 2}px`;
      button.append(ripple);
      const animation = ripple.animate([{ transform: 'scale(0)', opacity: .7 }, { transform: `scale(${rect.width / 8})`, opacity: 0 }], { duration: 600, easing: 'ease-out' });
      animations.add(animation);
      animation.onfinish = animation.oncancel = () => { ripple.remove(); animations.delete(animation); };
    });
  });

  resize();
  refresh();
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        const element = entry.target;
        const index = Number(element.style.getPropertyValue('--card-index')) || 0;
        animate(element, [{ opacity: 0, transform: 'translateY(28px)' }, { opacity: 1, transform: 'translateY(0)' }], {
          duration: 750, delay: Math.min(index % 6 * 85, 425), easing: 'cubic-bezier(.2,.7,.3,1)', fill: 'backwards'
        });
        observer.unobserve(element);
      });
    }, { threshold: .06 });
    document.querySelectorAll('[data-reveal]').forEach(element => observer.observe(element));
  }
})();
