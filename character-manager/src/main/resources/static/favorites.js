'use strict';
document.querySelectorAll('.favorite-form').forEach(form => {
  const button = form.querySelector('button');
  const message = document.createElement('span');
  message.setAttribute('role', 'status');
  form.append(message);
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (button.disabled) return;
    button.disabled = true;
    message.textContent = '';
    try {
      const response = await fetch(form.action, {
        method: 'PUT', headers: {'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json'},
        body: new URLSearchParams({favorite: String(button.getAttribute('aria-pressed') !== 'true')})
      });
      if (!response.ok) throw new Error('save failed');
      const result = await response.json();
      if (typeof result.favorite !== 'boolean') throw new Error('invalid response');
      button.setAttribute('aria-pressed', String(result.favorite));
      button.classList.toggle('is-favorite', result.favorite);
      button.textContent = result.favorite ? '★ お気に入り解除' : '☆ お気に入り';

    } catch (error) {
      message.textContent = '保存できませんでした。もう一度お試しください。';
    } finally {
      button.disabled = false;
    }
  });
});
