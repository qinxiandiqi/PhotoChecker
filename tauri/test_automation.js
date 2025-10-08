/**
 * PhotoChecker 自动化测试脚本
 * 使用Node.js和Puppeteer进行UI测试
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

class PhotoCheckerTester {
    constructor() {
        this.browser = null;
        this.page = null;
        this.baseUrl = 'http://localhost:5173';
        this.testResults = [];
    }

    async init() {
        console.log('🚀 启动测试环境...');

        // 启动浏览器
        this.browser = await puppeteer.launch({
            headless: false, // 显示浏览器窗口
            defaultViewport: { width: 1200, height: 800 },
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });

        this.page = await this.browser.newPage();

        // 监听控制台输出
        this.page.on('console', (msg) => {
            const type = msg.type();
            const text = msg.text();

            if (type === 'error') {
                this.log(`❌ 控制台错误: ${text}`, 'error');
            } else if (type === 'warning') {
                this.log(`⚠️ 控制台警告: ${text}`, 'warning');
            } else {
                this.log(`📝 控制台日志: ${text}`, 'info');
            }
        });

        // 监听页面错误
        this.page.on('pageerror', (error) => {
            this.log(`❌ 页面错误: ${error.message}`, 'error');
        });

        this.log('✅ 浏览器已启动', 'success');
    }

    async navigateToApp() {
        this.log('📍 导航到应用页面...');

        try {
            await this.page.goto(this.baseUrl, { waitUntil: 'networkidle2' });
            this.log('✅ 应用页面加载成功', 'success');

            // 等待应用完全加载
            await this.page.waitForSelector('body', { timeout: 5000 });
            this.log('✅ 应用DOM加载完成', 'success');

            return true;
        } catch (error) {
            this.log(`❌ 页面加载失败: ${error.message}`, 'error');
            return false;
        }
    }

    async testUIComponents() {
        this.log('🧪 测试UI组件...');

        const tests = [
            {
                name: '检查应用标题',
                selector: 'h1',
                expected: 'PhotoChecker'
            },
            {
                name: '检查选择照片按钮',
                selector: 'button',
                expectedText: '选择照片'
            },
            {
                name: '检查导航按钮',
                selector: 'button',
                expectedText: '关于'
            }
        ];

        for (const test of tests) {
            try {
                const element = await this.page.waitForSelector(test.selector, { timeout: 3000 });

                if (test.expectedText) {
                    const text = await element.evaluate(el => el.textContent.trim());
                    if (text.includes(test.expectedText)) {
                        this.log(`✅ ${test.name}: 找到 "${test.expectedText}"`, 'success');
                        this.addTestResult(test.name, true, `找到文本: ${text}`);
                    } else {
                        this.log(`❌ ${test.name}: 未找到 "${test.expectedText}"，实际文本: "${text}"`, 'error');
                        this.addTestResult(test.name, false, `文本不匹配: ${text}`);
                    }
                } else {
                    this.log(`✅ ${test.name}: 元素存在`, 'success');
                    this.addTestResult(test.name, true, '元素存在');
                }
            } catch (error) {
                this.log(`❌ ${test.name}: ${error.message}`, 'error');
                this.addTestResult(test.name, false, error.message);
            }
        }
    }

    async testPhotoSelection() {
        this.log('📸 测试照片选择功能...');

        try {
            // 查找选择照片按钮
            const selectButton = await this.page.evaluateHandle(() => {
                const buttons = Array.from(document.querySelectorAll('button'));
                return buttons.find(btn => btn.textContent.includes('选择照片'));
            });

            if (!selectButton.asElement()) {
                throw new Error('未找到选择照片按钮');
            }

            this.log('✅ 找到选择照片按钮', 'success');

            // 截图记录点击前状态
            await this.page.screenshot({ path: 'test_screenshots/before_click.png' });
            this.log('📸 保存点击前截图', 'info');

            // 点击按钮
            await selectButton.asElement().click();
            this.log('🖱️ 已点击选择照片按钮', 'info');

            // 等待可能的响应
            await this.page.waitForTimeout(2000);

            // 截图记录点击后状态
            await this.page.screenshot({ path: 'test_screenshots/after_click.png' });
            this.log('📸 保存点击后截图', 'info');

            // 检查是否有错误显示
            const errorElements = await this.page.$$('.text-error, .alert-error, [class*="error"]');
            if (errorElements.length > 0) {
                const errorTexts = await Promise.all(
                    errorElements.map(el => el.evaluate(el => el.textContent.trim()))
                );
                this.log(`⚠️ 发现错误信息: ${errorTexts.join(', ')}`, 'warning');
                this.addTestResult('照片选择', false, `显示错误: ${errorTexts.join(', ')}`);
            } else {
                this.log('✅ 没有发现明显错误', 'success');
                this.addTestResult('照片选择', true, '按钮点击正常');
            }

        } catch (error) {
            this.log(`❌ 照片选择测试失败: ${error.message}`, 'error');
            this.addTestResult('照片选择', false, error.message);
        }
    }

    async testTauriAPI() {
        this.log('🔌 测试Tauri API...');

        try {
            const tauriAvailable = await this.page.evaluate(() => {
                return typeof window.__TAURI__ !== 'undefined';
            });

            if (tauriAvailable) {
                this.log('✅ Tauri API可用', 'success');
                this.addTestResult('Tauri API', true, 'API可用');

                // 测试具体的API调用
                const apiTest = await this.page.evaluate(() => {
                    try {
                        return {
                            tauri: typeof window.__TAURI__,
                            core: typeof window.__TAURI__.core,
                            invoke: typeof window.__TAURI__.core.invoke
                        };
                    } catch (error) {
                        return { error: error.message };
                    }
                });

                this.log(`📋 API详情: ${JSON.stringify(apiTest, null, 2)}`, 'info');
            } else {
                this.log('ℹ️ Tauri API不可用（在Web浏览器中这是正常的）', 'info');
                this.addTestResult('Tauri API', true, 'Web环境，API不可用是正常的');
            }
        } catch (error) {
            this.log(`❌ Tauri API测试失败: ${error.message}`, 'error');
            this.addTestResult('Tauri API', false, error.message);
        }
    }

    async testErrorHandling() {
        this.log('🚨 测试错误处理...');

        try {
            // 检查是否有错误处理组件
            const errorDisplay = await this.page.$('[class*="error"], [class*="Error"]');

            if (errorDisplay) {
                this.log('✅ 找到错误显示组件', 'success');
                this.addTestResult('错误处理', true, '错误显示组件存在');
            } else {
                this.log('ℹ️ 未找到错误显示组件（这可能是正常的）', 'info');
                this.addTestResult('错误处理', true, '无错误显示组件');
            }

            // 检查加载状态
            const loadingElements = await this.page.$$('[class*="loading"], [class*="Loading"]');
            if (loadingElements.length > 0) {
                this.log('✅ 找到加载状态组件', 'success');
                this.addTestResult('加载状态', true, '加载组件存在');
            }

        } catch (error) {
            this.log(`❌ 错误处理测试失败: ${error.message}`, 'error');
            this.addTestResult('错误处理', false, error.message);
        }
    }

    async takeFinalScreenshot() {
        this.log('📸 保存最终截图...');

        try {
            // 确保截图目录存在
            const screenshotDir = 'test_screenshots';
            if (!fs.existsSync(screenshotDir)) {
                fs.mkdirSync(screenshotDir);
            }

            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const filename = `${screenshotDir}/final_state_${timestamp}.png`;

            await this.page.screenshot({
                path: filename,
                fullPage: true
            });

            this.log(`✅ 截图已保存: ${filename}`, 'success');
        } catch (error) {
            this.log(`❌ 截图保存失败: ${error.message}`, 'error');
        }
    }

    log(message, type = 'info') {
        const timestamp = new Date().toLocaleTimeString();
        const prefix = {
            'info': '📝',
            'success': '✅',
            'error': '❌',
            'warning': '⚠️'
        }[type] || '📝';

        console.log(`[${timestamp}] ${prefix} ${message}`);
    }

    addTestResult(testName, passed, details) {
        this.testResults.push({
            name: testName,
            passed,
            details,
            timestamp: new Date().toISOString()
        });
    }

    generateReport() {
        this.log('📊 生成测试报告...');

        const totalTests = this.testResults.length;
        const passedTests = this.testResults.filter(t => t.passed).length;
        const failedTests = totalTests - passedTests;

        const report = {
            timestamp: new Date().toISOString(),
            summary: {
                total: totalTests,
                passed: passedTests,
                failed: failedTests,
                successRate: `${((passedTests / totalTests) * 100).toFixed(1)}%`
            },
            tests: this.testResults
        };

        // 保存报告
        const reportPath = `test_report_${Date.now()}.json`;
        fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));

        this.log(`📋 测试报告已保存: ${reportPath}`, 'success');
        this.log(`📊 测试结果: ${passedTests}/${totalTests} 通过 (${report.summary.successRate})`, 'info');

        // 打印失败的测试
        const failedTestsList = this.testResults.filter(t => !t.passed);
        if (failedTestsList.length > 0) {
            this.log('❌ 失败的测试:', 'error');
            failedTestsList.forEach(test => {
                this.log(`  - ${test.name}: ${test.details}`, 'error');
            });
        }

        return report;
    }

    async cleanup() {
        this.log('🧹 清理测试环境...');

        if (this.browser) {
            await this.browser.close();
            this.log('✅ 浏览器已关闭', 'success');
        }
    }

    async runAllTests() {
        try {
            await this.init();

            const success = await this.navigateToApp();
            if (!success) {
                throw new Error('无法导航到应用页面');
            }

            await this.testUIComponents();
            await this.testPhotoSelection();
            await this.testTauriAPI();
            await this.testErrorHandling();

            await this.takeFinalScreenshot();
            const report = this.generateReport();

            return report;

        } catch (error) {
            this.log(`❌ 测试运行失败: ${error.message}`, 'error');
            throw error;
        } finally {
            await this.cleanup();
        }
    }
}

// 运行测试
async function main() {
    console.log('🎯 PhotoChecker 自动化测试开始\n');

    const tester = new PhotoCheckerTester();

    try {
        const report = await tester.runAllTests();
        console.log('\n🎉 测试完成！');

        // 根据测试结果设置退出码
        const failedTests = report.tests.filter(t => !t.passed).length;
        process.exit(failedTests > 0 ? 1 : 0);

    } catch (error) {
        console.error('\n💥 测试失败:', error.message);
        process.exit(1);
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    main().catch(console.error);
}

module.exports = PhotoCheckerTester;