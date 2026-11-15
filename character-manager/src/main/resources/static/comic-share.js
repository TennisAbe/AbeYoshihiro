(() => {
  'use strict';
  document.querySelectorAll('.share-copy').forEach(button => {
    const field = document.getElementById(button.dataset.copyField || 'share-prompt');
    const status = document.getElementById(button.dataset.copyStatus || 'share-copy-status');
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
  });
  const form = document.getElementById('selected-share-form');
  if (!form) return;
  const choices = [...form.querySelectorAll('input[name="characterIds"]')];
  const status = document.getElementById('selected-share-status');
  const update = () => {
    const count = choices.filter(choice => choice.checked).length;
    status.textContent = count ? `${count}人を差分共有します。` : '共有するキャラクターを選択してください。';
    form.querySelector('button[type="submit"]').disabled = count === 0;
  };
  choices.forEach(choice => choice.addEventListener('change', update));
  form.querySelectorAll('[data-share-selection]').forEach(button => {
    button.addEventListener('click', () => {
      choices.forEach(choice => { choice.checked = button.dataset.shareSelection === 'all'; });
      update();
    });
  });
  form.addEventListener('submit', event => {
    if (!choices.some(choice => choice.checked)) { event.preventDefault(); update(); }
  });
  update();
})();
