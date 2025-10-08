import { chromium } from 'playwright';

async function testPhotoSelection() {
  console.log('🚀 开始测试 PhotoChecker 选择图片功能...');

  const browser = await chromium.launch({
    headless: false,  // 显示浏览器窗口以便观察
    slowMo: 1000      // 减慢操作速度
  });

  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // 监听控制台消息
    page.on('console', msg => {
      console.log(`[${msg.type()}] ${msg.text()}`);
    });

    // 监听页面错误
    page.on('pageerror', error => {
      console.error('页面错误:', error.message);
    });

    // 导航到应用
    console.log('📍 导航到应用...');
    await page.goto('http://localhost:5173');

    // 等待页面加载
    await page.waitForTimeout(2000);

    // 检查页面标题
    const title = await page.title();
    console.log(`📄 页面标题: ${title}`);

    // 检查Tauri环境
    const tauriExists = await page.evaluate(() => {
      return !!window.__TAURI__;
    });
    console.log(`🔧 Tauri环境: ${tauriExists ? '✅ 可用' : '❌ 不可用'}`);

    // 查找"选择照片"按钮
    console.log('🔍 查找"选择照片"按钮...');
    const selectButton = await page.locator('text="选择照片"').first();

    if (await selectButton.isVisible()) {
      console.log('✅ 找到"选择照片"按钮');

      // 尝试点击按钮
      console.log('🖱️  点击"选择照片"按钮...');
      await selectButton.click();

      // 等待文件对话框打开（这在Tauri环境中会触发）
      await page.waitForTimeout(2000);

      // 检查是否有错误消息
      const errorElement = await page.locator('text="出错了"').first();
      if (await errorElement.isVisible()) {
        console.log('❌ 检测到错误');
        const errorMsg = await page.locator('[class*="error"]').first().textContent();
        console.log('错误信息:', errorMsg);
      } else {
        console.log('✅ 没有检测到错误');
      }

    } else {
      console.log('❌ 未找到"选择照片"按钮');

      // 查找页面内容
      const content = await page.content();
      console.log('页面内容预览:', content.substring(0, 500) + '...');
    }

    // 截图保存
    await page.screenshot({ path: 'test_result.png' });
    console.log('📸 截图已保存为 test_result.png');

  } catch (error) {
    console.error('❌ 测试过程中发生错误:', error);
  } finally {
    await browser.close();
    console.log('🏁 测试完成');
  }
}

// 运行测试
testPhotoSelection().catch(console.error);