// Run against an isolated, disposable application/database.
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    const base = process.env.BASE_URL || 'http://127.0.0.1:19080';
    const stamp = Date.now(); const name = `Presentation-${stamp}`;
    await page.goto(`${base}/characters/new`);
    await page.locator('#name').fill(name);
    await page.locator('#gender').selectOption('女');
    assert.equal(await page.locator('#bustCm').getAttribute('type'), 'text');
    await page.locator('#bustCm').fill('Gカップ / 約90cm・未測定');
    await page.getByRole('button', {name:'登録して編集を続ける'}).click();
    await page.waitForURL(/\/characters\/\d+\/edit$/);
    const editUrl = page.url(), id = editUrl.match(/characters\/(\d+)/)[1];
    assert.equal(await page.locator('#bustCm').inputValue(),'Gカップ / 約90cm・未測定');
    await page.goto(`${base}/?q=${name}`);
    const card = page.locator('.character-card'); const form = card.locator('.card-color-form');
    await card.locator('summary').click();
    let navigations = 0; page.on('framenavigated', frame => {if(frame === page.mainFrame()) navigations++;});
    for(const [key,value] of Object.entries({red:'255',green:'64',blue:'128'})) await form.locator(`[name=${key}]`).fill(value);
    assert.equal(await form.locator('.color-picker').inputValue(),'#ff4080');
    let background = await card.evaluate(node => getComputedStyle(node).backgroundImage);
    assert.ok(background.includes('linear-gradient') && background.includes('255, 64, 128'));
    await form.getByRole('button',{name:'色を保存',exact:true}).click();
    await form.getByRole('status').filter({hasText:'背景色を保存しました'}).waitFor(); assert.equal(navigations,0);
    await page.reload(); await card.locator('summary').click();
    assert.equal(await form.locator('.color-picker').inputValue(),'#ff4080');
    await form.locator('.color-picker').evaluate(node => {node.value='#2060d0';node.dispatchEvent(new Event('input',{bubbles:true}));});
    assert.equal(await form.locator('[name=red]').inputValue(),'32');assert.equal(await form.locator('[name=green]').inputValue(),'96');assert.equal(await form.locator('[name=blue]').inputValue(),'208');
    await page.route('**/characters/*/card-color',route => route.fulfill({status:500,body:'error'}));
    await form.getByRole('button',{name:'色を保存',exact:true}).click();
    await form.getByRole('status').filter({hasText:'保存できませんでした'}).waitFor();
    assert.equal(await form.getByRole('button',{name:'色を保存',exact:true}).isDisabled(),false);
    background = await card.evaluate(node => getComputedStyle(node).backgroundImage);assert.ok(background.includes('255, 64, 128'));
    await page.unroute('**/characters/*/card-color');
    await form.getByRole('button',{name:'色を保存',exact:true}).click();await form.getByRole('status').filter({hasText:'背景色を保存しました'}).waitFor();
    await form.getByRole('button',{name:'標準に戻す'}).click();await form.getByRole('status').filter({hasText:'標準の背景色'}).waitFor();
    assert.equal(await card.evaluate(node=>node.classList.contains('has-custom-color')),false);
    await page.reload();assert.equal(await card.evaluate(node=>node.classList.contains('has-custom-color')),false);
    // RGB fallback works without JavaScript, including the reset button.
    const nojs = await browser.newContext({javaScriptEnabled:false}); const fallback = await nojs.newPage();
    await fallback.goto(`${base}/?q=${name}`);await fallback.locator('.card-color-editor summary').click();
    for(const [key,value] of Object.entries({red:'0',green:'160',blue:'80'})) await fallback.locator(`.card-color-form [name=${key}]`).fill(value);
    await fallback.getByRole('button',{name:'色を保存',exact:true}).click();await fallback.waitForURL(/q=/);
    assert.ok((await fallback.locator('.character-card').getAttribute('style')).includes('--card-rgb: 0, 160, 80;'));await nojs.close();
    // A large roster must increase natural dimensions, not shrink names into a fixed canvas.
    await page.goto(`${base}/relationships`);await page.waitForFunction(()=>document.querySelector('.relationship-diagram img').naturalWidth>0);
    const small = await page.locator('.relationship-diagram img').evaluate(img=>({w:img.naturalWidth,h:img.naturalHeight}));
    for(let i=0;i<32;i++) {
      const response = await page.request.post(`${base}/characters`,{form:{name:`Map-${stamp}-${i}`,world:i<25?'ALTER_PALACE':i<29?'OTHER_WORLD':'MODERN_WORLD'},maxRedirects:0});assert.equal(response.status(),302);
      const targetId = response.headers().location.match(/characters\/(\d+)/)[1];
      if(i<8) assert.equal((await page.request.post(`${base}/characters/${id}/relations`,{form:{targetId,relationType:`関係${i}`},maxRedirects:0})).status(),302);
    }
    await page.reload();await page.waitForFunction(()=>document.querySelector('.relationship-diagram img').naturalWidth>0);
    const image = page.locator('.relationship-diagram img');
    const large = await image.evaluate(img=>({w:img.naturalWidth,h:img.naturalHeight}));assert.ok(large.w>small.w && large.h>small.h);
    assert.equal(await image.evaluate(img=>Math.round(img.getBoundingClientRect().width)),large.w);
    const viewport = page.locator('.relationship-diagram'); assert.ok(await viewport.evaluate(node=>node.scrollWidth>node.clientWidth));
    await page.locator('#diagram-spacing').evaluate(node=>{node.value='200';node.dispatchEvent(new Event('input',{bubbles:true}));});await page.getByRole('button',{name:'表示を更新'}).click();await page.waitForURL(/spacing=200/);
    await page.waitForFunction(()=>document.querySelector('.relationship-diagram img').naturalWidth>0);
    assert.ok(await image.evaluate(img=>img.naturalWidth)>large.w);
    assert.ok((await page.getByRole('link',{name:'相関図をダウンロード（SVG）'}).getAttribute('href')).includes('spacing=200'));
    assert.ok((await page.getByRole('link',{name:'この相関図を含む共有ZIPを保存'}).getAttribute('href')).includes('spacing=200'));
    await page.getByRole('button',{name:'全体表示',exact:true}).click();
    assert.ok(await image.evaluate(img=>img.getBoundingClientRect().width) <= await viewport.evaluate(node=>node.clientWidth));
    await page.getByRole('button',{name:'100%',exact:true}).click();assert.equal(await image.evaluate(img=>Math.round(img.getBoundingClientRect().width)),await image.evaluate(img=>img.naturalWidth));
    const downloaded = page.waitForEvent('download');await page.getByRole('link',{name:'相関図をダウンロード（SVG）'}).click();assert.equal(await (await downloaded).failure(),null);
    await page.screenshot({path:'/tmp/noctalith-expanded-map.png',fullPage:true});
    for(const width of [390,320]) {
      await page.setViewportSize({width,height:844});
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'diagram must scroll inside viewport');
      await page.goto(`${base}/?q=${name}`);await page.locator('.card-color-editor summary').click();
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'RGB editor must fit mobile');
      await page.goto(`${base}/relationships?spacing=200`);
    }
    assert.deepEqual(errors,[]);
    console.log('PASS: free-text bust, RGB/picker synchronization, gradient preview, asynchronous save/reload persistence, failed-save recovery, reset, no-JS fallback, adaptive diagram dimensions, spacing/download links, zoom/fit and mobile layouts');
  } finally { await browser.close(); }
})().catch(error => {console.error(error);process.exitCode=1;});
