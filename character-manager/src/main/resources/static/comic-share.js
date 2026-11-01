(() => {
  'use strict';
  const button = document.querySelector('.share-copy');
  const field = document.getElementById('share-prompt');
  const status = document.getElementById('share-copy-status');
  if (!button || !field || !status) return;
  button.addEventListener('click', async () => {
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(field.value);
      status.textContent = 'コピーしました。ChatGPTへ貼り付けてください。';
    } catch (_) {
      field.focus(); field.select();
      status.textContent = '依頼文を選択しました。Ctrl+C（MacはCommand+C）でコピーしてください。';
    }
  });
})();
