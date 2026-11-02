// Dedicated app/database only; see README for the browser test environment.
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1600,height:1000}});
    const base = process.env.BASE_URL || 'http://127.0.0.1:18080';
    const create = async (name, world) => {
      const response = await page.request.post(`${base}/characters`,{form:{name,world,basicSettings:`${name}の基本設定`},maxRedirects:0});
      assert.equal(response.status(),302);
      return response.headers().location.match(/characters\/(\d+)/)[1];
    };
    const connect = async (source,target,type) => {
      const response = await page.request.post(`${base}/characters/${source}/relations`,{form:{targetId:target,relationType:type},maxRedirects:0});assert.equal(response.status(),302);
    };
    await page.goto(`${base}/protagonists/new`);
    for(const name of ['name','gender','heightCm','basicSettings']) assert.equal(await page.locator(`[name=${name}]`).isVisible(),true,name);
    for(const name of ['bustCm','personality','affiliation','tagsInput','image']) assert.equal(await page.locator(`[name=${name}]`).count(),0,name);
    await page.locator('#name').fill('ミカド');await page.locator('#gender').selectOption('男');await page.locator('#heightCm').fill('175');await page.locator('#basicSettings').fill('3つの世界を巡る物語の主人公。');
    await page.getByRole('button',{name:'登録して編集を続ける'}).click();await page.waitForURL(/\/characters\/\d+\/edit$/);
    const hero = page.url().match(/characters\/(\d+)/)[1];
    const heroEdit=page.url();
    for(const color of ['red','blue']) {
      const data=await page.evaluate(color=>{const canvas=document.createElement('canvas');canvas.width=4;canvas.height=4;const context=canvas.getContext('2d');context.fillStyle=color;context.fillRect(0,0,4,4);return canvas.toDataURL('image/png').split(',')[1];},color);
      await page.locator('input[name=title]').fill(`主人公画像-${color}`);
      await page.locator('input[name=image]').setInputFiles({name:`${color}.png`,mimeType:'image/png',buffer:Buffer.from(data,'base64')});
      await page.getByRole('button',{name:'画像を追加する'}).click();await page.waitForURL(url=>url.pathname.split(';')[0]===new URL(heroEdit).pathname);
      await page.getByText(`主人公画像-${color}`,{exact:true}).waitFor();
    }
    assert.equal(await page.locator('.gallery-item').count(),2);
    await page.getByRole('button',{name:'代表にする',exact:true}).click();
    await page.locator('.gallery-item').filter({hasText:'主人公画像-blue'}).locator('.cover-badge').waitFor();
    await page.goto(base);
    await page.waitForFunction(()=>document.querySelector('[data-world=PROTAGONIST] .thumbnail img')?.naturalWidth>0);
    await page.goto(`${base}/characters/${hero}`);
    await page.waitForFunction(()=>document.querySelector('.portrait-frame img')?.naturalWidth>0);
    assert.equal(await page.locator('.dossier-gallery img').count(),2);
    await page.goto(heroEdit);
    page.once('dialog',dialog=>dialog.accept());
    await page.locator('.gallery-item').filter({hasText:'主人公画像-red'}).getByRole('button',{name:'画像を削除',exact:true}).click();
    await page.waitForFunction(()=>document.querySelectorAll('.gallery-item').length===1);
    assert.equal(await page.locator('.gallery-item').filter({hasText:'主人公画像-blue'}).locator('.cover-badge').count(),1);

    const names=['キルク','シュネカ','メイ','セイラ','クク','バラム','ライ','ノノハ','ルディア','ライナ','ユラ','ソルバル','ヒカリ','太臣','セネ','シノ','アヤメ','ユミト','メディ','セナ'];
    const ids=[];
    for(let i=0;i<names.length;i++) ids.push(await create(names[i],i<10?'ALTER_PALACE':i<15?'OTHER_WORLD':'MODERN_WORLD'));
    for(let i=0;i<ids.length;i++) if(i%3===0) await connect(hero,ids[i],i%2?'信頼':'仲間');
    await connect(ids[0],ids[1],'姉妹');await connect(ids[2],ids[11],'好敵手');await connect(ids[15],ids[18],'同級生');
    await page.goto(base);
    const worlds = await page.locator('.world-section').evaluateAll(nodes=>nodes.map(n=>n.dataset.world));assert.equal(worlds[0],'PROTAGONIST');
    const heroCard = page.locator('.world-section[data-world=PROTAGONIST] .character-card');
    await heroCard.locator('.favorite-button').click();await page.waitForFunction(()=>document.querySelector('[data-world=PROTAGONIST] .favorite-button').getAttribute('aria-pressed')==='true');
    assert.equal(await heroCard.locator('[role=status]').textContent(),'');
    await page.goto(`${base}/characters/${ids[0]}/edit`);await page.locator('#gender').selectOption('女');assert.equal(await page.locator('label[for=bustCm]').textContent(),'胸囲');
    await page.goto(`${base}/relationships`);
    await page.waitForFunction(()=>document.querySelector('.relationship-diagram img').naturalWidth>0);
    const svgUrl = await page.locator('.relationship-diagram img').getAttribute('src');
    const svg = await (await page.request.get(base+svgUrl)).text();
    assert.ok(svg.includes('url(#night)'));assert.ok(svg.includes('主人公'));for(const name of ['オルタパレス','異世界','現代世界']) assert.ok(svg.includes(name));assert.ok(!/C\d{6}/.test(svg));
    await page.screenshot({path:'/tmp/noctalith-world-map-full.png',fullPage:true});
    await page.locator(`input[name=ids][value="${ids[0]}"]`).check();await page.locator(`input[name=ids][value="${ids[15]}"]`).check();
    await page.getByRole('button',{name:'表示を更新'}).click();await page.waitForURL(/ids=/);
    assert.equal(await page.locator('input[name=ids]:checked').count(),2);
    const selectedSvg = await (await page.request.get(base+await page.locator('.relationship-diagram img').getAttribute('src'))).text();
    for(const name of ['ミカド','キルク','シュネカ','シノ','メディ']) assert.ok(selectedSvg.includes(name),name);
    assert.ok(!selectedSvg.includes('好敵手'));
    const downloadUrl = await page.getByRole('link',{name:'相関図をダウンロード（SVG）'}).getAttribute('href');
    assert.equal(await (await page.request.get(base+downloadUrl)).text(),selectedSvg);
    const zipUrl=await page.getByRole('link',{name:'この相関図を含む共有ZIPを保存'}).getAttribute('href');assert.ok(zipUrl.includes('ids='));
    const downloadEvent=page.waitForEvent('download');await page.getByRole('link',{name:'相関図をダウンロード（SVG）'}).click();const download=await downloadEvent;assert.equal(await download.failure(),null);
    await page.screenshot({path:'/tmp/noctalith-world-map-selected.png',fullPage:true});
    await page.getByRole('link',{name:'全員を表示',exact:true}).click();await page.waitForURL(`${base}/relationships`);assert.equal(await page.locator('input[name=ids]:checked').count(),0);
    await page.setViewportSize({width:390,height:844});assert.ok(await page.locator('.diagram-choices').isVisible());assert.ok(await page.locator('.relationship-diagram').isVisible());
    console.log('PASS: protagonist image upload, cover, list/detail display, delete, protagonist fields and list order, silent favorites, bust label, dark world frames, multi-select, SVG download, selection links, mobile view');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});
