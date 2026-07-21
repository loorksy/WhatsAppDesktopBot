/**
 * whatsapp-web.js can throw when CDP already registered a page binding
 * but window[name] is not set yet. Ignore duplicate binding errors.
 */
const puppeteerUtil = require('whatsapp-web.js/src/util/Puppeteer');
const originalExpose = puppeteerUtil.exposeFunctionIfAbsent;

puppeteerUtil.exposeFunctionIfAbsent = async function exposeFunctionIfAbsent(page, name, fn) {
  let existsOnWindow = false;
  try {
    existsOnWindow = await page.evaluate((bindingName) => !!window[bindingName], name);
  } catch (err) {
    const message = String(err?.message || err);
    if (!message.includes('Execution context was destroyed') && !message.includes('Target closed')) {
      throw err;
    }
  }
  if (existsOnWindow) {
    return;
  }
  try {
    await page.exposeFunction(name, fn);
  } catch (err) {
    const message = String(err?.message || err);
    if (!message.includes('already exists')) {
      throw err;
    }
  }
};

module.exports = { exposeFunctionIfAbsent: puppeteerUtil.exposeFunctionIfAbsent, originalExpose };
