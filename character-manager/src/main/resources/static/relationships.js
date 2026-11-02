'use strict';
(() => {
  const spacing = document.getElementById('diagram-spacing');
  if (spacing) spacing.addEventListener('input', () => {
    document.querySelector('output[for="diagram-spacing"]').textContent = `${spacing.value}%`;
  });
  const viewport = document.querySelector('.relationship-diagram');
  const image = viewport?.querySelector('img');
  const controls = document.querySelector('.diagram-zoom');
  const slider = document.getElementById('diagram-zoom');
  if (!image || !controls || !slider) return;
  const zoom = (percent) => {
    image.style.width = `${image.naturalWidth * percent / 100}px`;
    slider.value = String(Math.max(10, Math.min(150, percent)));
    controls.querySelector('output').textContent = `${Math.round(percent)}%`;
  };
  const loaded = () => { controls.hidden = false; zoom(Number(slider.value)); };
  image.addEventListener('load', loaded);
  if (image.complete && image.naturalWidth) loaded();
  slider.addEventListener('input', () => zoom(Number(slider.value)));
  controls.querySelectorAll('[data-zoom]').forEach(button => button.addEventListener('click', () => {
    zoom(button.dataset.zoom === 'fit' ? Math.min(100, (viewport.clientWidth - 2) / image.naturalWidth * 100) : 100);
    viewport.scrollTo(0, 0);
  }));
})();
