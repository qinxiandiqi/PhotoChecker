import { chromium } from 'playwright';

async function testPhotoFixes() {
  console.log('🚀 测试 PhotoChecker 修复后的功能...');

  const browser = await chromium.launch({
    headless: false,
    slowMo: 1000
  });

  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // 监听控制台消息
    page.on('console', msg => {
      if (msg.type() === 'error') {
        console.error(`❌ [错误] ${msg.text()}`);
      } else if (msg.type() === 'warning') {
        console.warn(`⚠️ [警告] ${msg.text()}`);
      } else {
        console.log(`ℹ️ [信息] ${msg.text()}`);
      }
    });

    // 监听页面错误
    page.on('pageerror', error => {
      console.error('❌ 页面错误:', error.message);
    });

    // 导航到应用
    console.log('📍 导航到应用...');
    await page.goto('http://localhost:5173');
    await page.waitForTimeout(2000);

    // 检查Tauri环境
    const tauriExists = await page.evaluate(() => {
      return !!window.__TAURI__;
    });
    console.log(`🔧 Tauri环境: ${tauriExists ? '✅ 可用' : '❌ 浏览器环境'}`);

    // 查找"选择照片"按钮
    console.log('🔍 查找"选择照片"按钮...');
    const selectButton = await page.locator('text="选择照片"').first();

    if (await selectButton.isVisible()) {
      console.log('✅ 找到"选择照片"按钮');

      // 尝试点击按钮
      console.log('🖱️  点击"选择照片"按钮...');
      await selectButton.click();
      await page.waitForTimeout(3000);

      // 检查页面状态
      console.log('🔍 检查页面状态...');

      // 检查是否有加载状态
      const loadingSpinner = await page.locator('.loading').first();
      if (await loadingSpinner.isVisible()) {
        console.log('✅ 检测到加载状态');
      }

      // 检查是否有图片预览区域
      const photoPreview = await page.locator('[class*="PhotoPreview"]').first();
      if (await photoPreview.isVisible()) {
        console.log('✅ 检测到图片预览区域');
      }

      // 检查是否有EXIF信息区域
      const exifInfo = await page.locator('[class*="ExifInfoList"]').first();
      if (await exifInfo.isVisible()) {
        console.log('✅ 检测到EXIF信息区域');

        // 检查是否有"没有找到EXIF信息"的提示
        const noExifMsg = await page.locator('text="没有找到EXIF信息"').first();
        if (await noExifMsg.isVisible()) {
          console.log('✅ 显示了正确的"没有EXIF信息"提示');

          // 检查是否有帮助信息
          const helpInfo = await page.locator('text="为什么没有EXIF信息？"').first();
          if (await helpInfo.isVisible()) {
            console.log('✅ 显示了详细的帮助信息');
          }
        }
      }

      // 检查是否有错误信息
      const errorElement = await page.locator('text="出错了"').first();
      if (await errorElement.isVisible()) {
        console.log('❌ 仍然有错误');
        const errorMsg = await page.locator('[class*="error"]').first().textContent();
        console.log('错误信息:', errorMsg);
      } else {
        console.log('✅ 没有检测到错误');
      }

    } else {
      console.log('❌ 未找到"选择照片"按钮');
    }

    // 截图保存
    await page.screenshot({ path: 'test_fixes_result.png' });
    console.log('📸 截图已保存为 test_fixes_result.png');

  } catch (error) {
    console.error('❌ 测试过程中发生错误:', error);
  } finally {
    await browser.close();
    console.log('🏁 测试完成');
  }
}

// 运行测试
testPhotoFixes().catch(console.error);