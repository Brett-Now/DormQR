package app.dormqr.qr;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页，两种形态：
 *
 * - 手机：完整表单，按功能分组，低频参数收进「高级」，底部固定保存条。
 * - 手表：同一套参数的紧凑表单，字号与间距收紧，服务连接等低频项默认折叠；
 *   页脚左「立即获取」右「保存」，点击标题返回。
 */
public class SettingsActivity extends Activity {

    private QrSettings settings;
    private DeviceProfile profile;

    // 表单控件：手机与手表两套布局共用同一批字段
    private EditText baseUrl, qrPath, payload;
    private EditText ttl, percent, cache;
    private CheckBox background, demo;

    private LinearLayout advancedBody;
    private TextView testResult;
    private Button testButton;
    private boolean payloadExpanded;
    private boolean advancedExpanded;
    private boolean testing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new QrSettings(this);
        profile = DeviceProfile.of(this);
        if (getIntent() != null && getIntent().getBooleanExtra("watch_layout", false)) {
            profile = DeviceProfile.forced(this, true);
        }

        setContentView(R.layout.activity_settings);

        // 统一走布局全屏 + Insets 补偿
        Insets.immersive(getWindow());
        Insets.lightStatusBar(getWindow(), true);

        // 圆屏：先把标题居中、把页脚抬进弦宽够用的区域，再叠加 Insets
        if (profile.watch) {
            applyRoundChrome();
        }

        // 手机上给标题留出返回键宽度，手表上则整屏居中
        TextView titleView = findViewById(R.id.title);
        android.widget.FrameLayout.LayoutParams tlp =
                (android.widget.FrameLayout.LayoutParams) titleView.getLayoutParams();
        tlp.leftMargin = profile.watch ? 0 : (int) (44 * getResources().getDisplayMetrics().density);
        titleView.setLayoutParams(tlp);

        Insets.applyTopPadding(findViewById(R.id.header));

        View footer = findViewById(R.id.footer);
        Insets.applyBottomPadding(footer);

        ImageButton back = findViewById(R.id.btnBack);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndFinish();
            }
        });

        LinearLayout content = findViewById(R.id.content);
        if (profile.watch) {
            compact(footer);
            Button cancel = findViewById(R.id.btnCancel);
            cancel.setText("立即获取");
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PrefetchService.refreshNow(SettingsActivity.this);
                    toast("已触发获取");
                    finish();
                }
            });
            buildWatchSettings(content);
        } else {
            buildForm(content);
        }
    }

    /**
     * 466×466 圆屏的弦宽计算：
     *   半径 233，弦半宽 = sqrt(233² - (y-233)²)
     * 这里把页脚整体抬离底部 44dp，使按钮下沿落在 y≈370px，
     */
    private void applyRoundChrome() {
        View header = findViewById(R.id.header);
        ImageButton back = findViewById(R.id.btnBack);

        header.setPadding(0, dp(8), 0, dp(2));

        // 标题整屏水平居中；手表不放返回键
        TextView title = findViewById(R.id.title);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(17);

        // 点击标题返回
        back.setVisibility(View.GONE);
        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        title.setClickable(true);
        title.setBackground(null);

        // 抬起页脚并收窄左右留白
        View footer = findViewById(R.id.footer);
        footer.setPadding(dp(28), dp(6), dp(28), dp(44));

        // 内容内缩放在内容容器上；滚动条用自绘的白色短弧
        LinearLayout content = findViewById(R.id.content);
        content.setPadding(dp(28), content.getPaddingTop(), dp(28), dp(8));

        // 滚动指示：白色短弧，锚在中间右侧
        final android.widget.ScrollView scroll =
                (android.widget.ScrollView) findViewById(R.id.scroll);
        final ArcScrollBarView arc = new ArcScrollBarView(this, dp(10));
        android.widget.FrameLayout.LayoutParams alp =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        alp.gravity = Gravity.CENTER;
        addContentView(arc, alp);

        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                updateArc(arc, scroll);
            }
        });
        scroll.post(new Runnable() {
            @Override
            public void run() {
                updateArc(arc, scroll);
            }
        });
    }

    /** 把滚动状态换算成弧上的位置与长度。 */
    private void updateArc(ArcScrollBarView arc, android.widget.ScrollView scroll) {
        View child = scroll.getChildAt(0);
        if (child == null || arc == null) return;

        int contentH = child.getHeight();
        int viewH = scroll.getHeight();
        if (contentH <= 0 || viewH <= 0) return;

        int maxScroll = Math.max(1, contentH - viewH);
        arc.update(scroll.getScrollY() / (float) maxScroll, viewH / (float) contentH);
    }

    private void compact(View footer) {
        View cancel = footer.findViewById(R.id.btnCancel);
        View save = footer.findViewById(R.id.btnSave);
        for (View b : new View[]{cancel, save}) {
            if (b == null) continue;
            ViewGroup.LayoutParams lp = b.getLayoutParams();
            lp.height = dp(38);
            b.setLayoutParams(lp);
            if (b instanceof Button) {
                ((Button) b).setTextSize(12);
            }
        }
    }

    /* ==================== 侧滑返回（仅手表） ==================== */

    private float swipeDownX, swipeDownY;
    private boolean swipingBack;

    /**
     * 从左侧向右滑超过 100dp（233dp 宽屏幕上接近半屏）即退出设置页。
     */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (profile != null && profile.watch) {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    swipeDownX = ev.getX();
                    swipeDownY = ev.getY();
                    swipingBack = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!swipingBack
                            && ev.getX() - swipeDownX > dp(100)
                            && Math.abs(ev.getY() - swipeDownY) < dp(40)) {
                        swipingBack = true;
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (swipingBack) {
                        swipingBack = false;
                        finish();
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /* ==================== 手表 ==================== */

    private void buildWatchSettings(LinearLayout content) {
        QrStore.Entry e = new QrStore(this).load();

        LinearLayout status = section(content, "当前状态");
        if (e == null) {
            TextView na = new TextView(this);
            na.setText("本机还没有二维码");
            na.setTextSize(12);
            na.setTextColor(getColor(R.color.state_warn));
            status.addView(na);
            helper(status, "手表独立取码，不依赖手机。点下面「立即获取」即可。");
        } else {
            QrFreshness.Snapshot snap = QrFreshness.evaluate(
                    e.retrievedAt, System.currentTimeMillis(),
                    settings.ttlSeconds(), settings.refreshPercent());
            keyValue(status, "二维码", QrFreshness.describeShort(snap) + "  ·  "
                    + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date(e.retrievedAt)));
        }
        keyValue(status, "后台保活", settings.backgroundEnabled()
                ? (PrefetchService.RUNNING ? "开 · 运行中" : "开 · 未启动")
                : "关");


        // 常用参数：圆表上最可能调的三个
        LinearLayout common = section(content, "常用");
        ttl = numberField(common, getString(R.string.label_ttl),
                String.valueOf(settings.ttlSeconds()), null, 60, 3600);
        percent = numberField(common, getString(R.string.label_percent),
                String.valueOf(settings.refreshPercent()), null, 10, 100);

        final CheckBox keepAwake = check(common, "翻腕不熄屏",
                settings.keepAwakeOnWrist(), null);
        keepAwake.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                settings.setKeepAwakeOnWrist(on);
            }
        });
        helper(common, "熄屏后自动把二维码页顶回来，需允许「后台弹出界面」");



        background = check(common, getString(R.string.label_background),
                settings.backgroundEnabled(), null);

        // 服务连接：默认收起
        LinearLayout serviceBody = collapsible(content, getString(R.string.section_service), false);
        baseUrl = field(serviceBody, getString(R.string.label_base_url),
                settings.baseUrl(), null, false);
        qrPath = field(serviceBody, getString(R.string.label_qr_path),
                settings.qrPath(), null, false);
        payload = field(serviceBody, getString(R.string.label_payload),
                settings.payload(), null, true);
        payload.setMinLines(2);
        payload.setMaxLines(4);

        // 高级：缓存、诊断、复位
        LinearLayout advancedBody = collapsible(content, getString(R.string.section_advanced), false);
        cache = numberField(advancedBody, getString(R.string.label_cache),
                String.valueOf(settings.cacheSeconds()), null, 0, 3600);
        demo = check(advancedBody, getString(R.string.label_demo),
                settings.demoMode(), null);

        testButton = wideButton(advancedBody, getString(R.string.action_test), false);
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        });

        testResult = new TextView(this);
        testResult.setTextSize(10);
        testResult.setTextColor(getColor(R.color.text_secondary));
        testResult.setPadding(0, dp(4), 0, 0);
        advancedBody.addView(testResult);

        Button clear = wideButton(advancedBody, getString(R.string.action_clear_cache), false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new QrStore(SettingsActivity.this).clear();
                toast("已清空");
            }
        });

        TextView version = new TextView(this);
        version.setText("门禁码 v" + versionName());
        version.setTextSize(profile.watch ? 10 : 11);
        version.setTextColor(getColor(R.color.text_secondary));
        version.setPadding(0, dp(8), 0, 0);
        advancedBody.addView(version);

        Button resetAll = wideButton(advancedBody, "恢复默认设置", false);
        resetAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.resetAll();
                new QrStore(SettingsActivity.this).clear();
                toast("已恢复默认");
                finish();
            }
        });
        aboutSection(content);

    }

    /**
     * 关于卡片：版本、作者、仓库。
     * 仓库链接默认用系统浏览器打开；没有浏览器时退化为复制地址。
     */
    private void aboutSection(LinearLayout content) {
        LinearLayout card = section(content, getString(R.string.section_about));

        keyValue(card, "版本", "门禁码 v" + versionName());
        keyValue(card, "作者", getString(R.string.app_author));

        TextView repo = keyValueClickable(card, "仓库", getString(R.string.app_repo));
        repo.setTextColor(getColor(R.color.state_info));
        repo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(getString(R.string.app_repo_url))));
                } catch (Exception e) {
                    copyToClipboard(getString(R.string.app_repo_url));
                    toast("没有浏览器，已复制仓库地址");
                }
            }
        });

        TextView desc = new TextView(this);
        desc.setText(getString(R.string.about_desc));
        desc.setTextSize(profile.watch ? 10 : 11);
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setPadding(0, dp(6), 0, 0);
        card.addView(desc);
    }

    /** 可点击的键值行，返回右侧文本视图。 */
    private TextView keyValueClickable(LinearLayout parent, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextSize(profile.watch ? 10 : 11);
        k.setTextColor(getColor(R.color.text_secondary));
        k.setMinWidth(dp(56));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(profile.watch ? 11 : 12);
        v.setTextColor(getColor(R.color.text_primary));
        v.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        row.addView(k);
        row.addView(v);
        parent.addView(row);
        return v;
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("dormqr", text));
            toast("已复制");
        }
    }

    /** 可折叠卡片，返回内容容器。 */
    private LinearLayout collapsible(LinearLayout parent, String title, boolean open) {
        LinearLayout card = section(parent, title);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        card.addView(body);

        final TextView toggle = new TextView(this);
        toggle.setText(open ? "收起" : "展开");
        toggle.setTextColor(getColor(R.color.state_info));
        toggle.setTextSize(profile.watch ? 11 : 13);
        toggle.setPadding(0, dp(4), 0, 0);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean vis = body.getVisibility() == View.VISIBLE;
                body.setVisibility(vis ? View.GONE : View.VISIBLE);
                toggle.setText(vis ? "展开" : "收起");
            }
        });
        card.addView(toggle);
        return body;
    }

    /* ==================== 手机 ==================== */

    private void buildForm(LinearLayout content) {
        LinearLayout service = section(content, getString(R.string.section_service));
        baseUrl = field(service, getString(R.string.label_base_url),
                settings.baseUrl(), getString(R.string.hint_base_url), false);
        qrPath = field(service, getString(R.string.label_qr_path),
                settings.qrPath(), getString(R.string.hint_qr_path), false);

        payload = field(service, getString(R.string.label_payload),
                settings.payload(), getString(R.string.hint_payload), true);
        payload.setMinLines(3);
        payload.setMaxLines(3);
        payload.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        final TextView payloadToggle = new TextView(this);
        payloadToggle.setTextColor(getColor(R.color.state_info));
        payloadToggle.setTextSize(13);
        payloadToggle.setPadding(0, dp(6), 0, 0);
        payloadToggle.setText("展开编辑（当前 " + settings.payload().length() + " 字符）");
        payloadToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payloadExpanded = !payloadExpanded;
                payload.setMaxLines(payloadExpanded ? 12 : 3);
                payloadToggle.setText(payloadExpanded ? "收起" : "展开编辑");
            }
        });
        service.addView(payloadToggle);

        LinearLayout refresh = section(content, getString(R.string.section_refresh));
        ttl = numberField(refresh, getString(R.string.label_ttl),
                String.valueOf(settings.ttlSeconds()), getString(R.string.hint_ttl), 60, 3600);
        percent = numberField(refresh, getString(R.string.label_percent),
                String.valueOf(settings.refreshPercent()), getString(R.string.hint_percent), 10, 100);

        LinearLayout runtime = section(content, getString(R.string.section_runtime));
        cache = numberField(runtime, getString(R.string.label_cache),
                String.valueOf(settings.cacheSeconds()), getString(R.string.hint_cache), 0, 3600);
        background = check(runtime, getString(R.string.label_background),
                settings.backgroundEnabled(), getString(R.string.hint_background));
        demo = check(runtime, getString(R.string.label_demo),
                settings.demoMode(), getString(R.string.hint_demo));

        LinearLayout advanced = section(content, getString(R.string.section_advanced));
        advancedBody = new LinearLayout(this);
        advancedBody.setOrientation(LinearLayout.VERTICAL);
        advancedBody.setVisibility(View.GONE);
        advanced.addView(advancedBody);

        final TextView advancedToggle = new TextView(this);
        advancedToggle.setText("展开高级选项");
        advancedToggle.setTextColor(getColor(R.color.state_info));
        advancedToggle.setTextSize(13);
        advancedToggle.setPadding(0, dp(8), 0, 0);
        advancedToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advancedExpanded = !advancedExpanded;
                advancedBody.setVisibility(advancedExpanded ? View.VISIBLE : View.GONE);
                advancedToggle.setText(advancedExpanded ? "收起高级选项" : "展开高级选项");
            }
        });
        advanced.addView(advancedToggle);

        testButton = wideButton(advancedBody, getString(R.string.action_test), true);
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        });

        testResult = new TextView(this);
        testResult.setTextSize(12);
        testResult.setTextColor(getColor(R.color.text_secondary));
        testResult.setPadding(0, dp(8), 0, 0);
        testResult.setText("这里只发一次真实请求，不会改动当前显示的二维码。");
        advancedBody.addView(testResult);


        Button copy = wideButton(advancedBody, getString(R.string.action_copy), false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyDiagnostics();
            }
        });

        TextView version = new TextView(this);
        version.setText("门禁码 v" + versionName());
        version.setTextSize(profile.watch ? 10 : 11);
        version.setTextColor(getColor(R.color.text_secondary));
        version.setPadding(0, dp(8), 0, 0);
        advancedBody.addView(version);


        Button resetAll = wideButton(advancedBody, "恢复默认设置", false);
        resetAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.resetAll();
                new QrStore(SettingsActivity.this).clear();
                toast("已恢复默认值");
                finish();
            }
        });

        Button clear = wideButton(advancedBody, getString(R.string.action_clear_cache), false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new QrStore(SettingsActivity.this).clear();
                toast("已清空，回到主界面会重新获取");
            }
        });
        aboutSection(content);

    }

    /* ==================== 保存与测试 ==================== */

    private boolean validate() {
        boolean ok = true;
        ok &= checkRange(ttl, 60, 3600);
        ok &= checkRange(percent, 10, 100);
        ok &= checkRange(cache, 0, 3600);

        if (text(baseUrl).isEmpty()) {
            baseUrl.setError("不能为空");
            ok = false;
        }
        if (!text(qrPath).startsWith("/")) {
            qrPath.setError("建议以 / 开头");
        }
        return ok;
    }

    private boolean checkRange(EditText field, int lo, int hi) {
        try {
            int v = Integer.parseInt(text(field));
            if (v < lo || v > hi) {
                field.setError("范围 " + lo + " ~ " + hi);
                return false;
            }
            field.setError(null);
            return true;
        } catch (NumberFormatException e) {
            field.setError(getString(R.string.error_number));
            return false;
        }
    }

    private void saveAndFinish() {
        if (!validate()) {
            toast("有项目未填对，请看输入框提示");
            return;
        }

        settings.save(
                text(baseUrl), text(qrPath), text(payload),
                Integer.parseInt(text(ttl)),
                Integer.parseInt(text(percent)),
                Integer.parseInt(text(cache)),
                background.isChecked(),
                demo.isChecked());

        if (settings.backgroundEnabled()) {
            PrefetchService.refreshNow(this);
        } else {
            PrefetchService.stop(this);
        }

        hideKeyboard();
        toast("已保存");
        finish();
    }

    private void runTest() {
        if (testing) return;
        if (ttl != null && !validate()) {
            toast("参数有误，先修正再测试");
            return;
        }
        if (ttl != null) {
            settings.save(
                    text(baseUrl), text(qrPath), text(payload),
                    Integer.parseInt(text(ttl)),
                    Integer.parseInt(text(percent)),
                    Integer.parseInt(text(cache)),
                    background.isChecked(),
                    demo.isChecked());
        }

        testing = true;
        testButton.setEnabled(false);
        testResult.setTextColor(getColor(R.color.text_secondary));
        testResult.setText("正在请求…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final long t0 = System.currentTimeMillis();
                String summary;
                boolean ok;
                try {
                    QrApi.Result r = QrApi.fetch(settings);
                    long cost = System.currentTimeMillis() - t0;
                    summary = "成功 · " + r.content.length() + " 字符 · " + cost + " ms"
                            + "\n前缀 " + r.content.substring(0, Math.min(11, r.content.length()))
                            + "… · "
                            + new java.text.SimpleDateFormat("HH:mm:ss",
                            java.util.Locale.CHINA).format(new java.util.Date(r.retrievedAt));
                    ok = true;
                } catch (Exception ex) {
                    summary = "失败 · " + ex.getMessage();
                    ok = false;
                }

                final String s = summary;
                final boolean success = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        testing = false;
                        testButton.setEnabled(true);
                        testResult.setText(s);
                        testResult.setTextColor(getColor(success
                                ? R.color.state_ok : R.color.state_error));
                    }
                });
            }
        }, "settings-test").start();
    }

    private void copyDiagnostics() {
        String diag = "门禁码诊断\n"
                + "地址: " + text(baseUrl) + "\n"
                + "路径: " + text(qrPath) + "\n"
                + "有效期: " + text(ttl) + "s  触发线: " + text(percent) + "%\n"
                + "缓存上限: " + text(cache) + "s\n"
                + "后台: " + background.isChecked() + "  模拟: " + demo.isChecked() + "\n"
                + "密文长度: " + text(payload).length() + " 字符（原文已省略）\n"
                + "测试: " + testResult.getText();

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("dormqr", diag));
            toast("诊断信息已复制");
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    /* ==================== 控件工厂 ==================== */

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int p = dp(profile.watch ? 10 : 14);
        card.setPadding(p, p, p, p);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(profile.watch ? 8 : 12);
        parent.addView(card, lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(profile.watch ? 13 : 15);
        t.setTextColor(getColor(R.color.text_primary));
        t.setPadding(0, 0, 0, dp(2));
        card.addView(t);

        return card;
    }

    private void keyValue(LinearLayout parent, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextSize(11);
        k.setTextColor(getColor(R.color.text_secondary));
        k.setMinWidth(dp(56));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(12);
        v.setTextColor(getColor(R.color.text_primary));

        row.addView(k);
        row.addView(v);
        parent.addView(row);
    }

    private void label(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(profile.watch ? 11 : 12);
        t.setTextColor(getColor(R.color.text_secondary));
        t.setPadding(0, dp(profile.watch ? 6 : 10), 0, dp(4));
        parent.addView(t);
    }

    private void helper(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(profile.watch ? 10 : 11);
        t.setTextColor(getColor(R.color.text_secondary));
        t.setPadding(dp(2), dp(profile.watch ? 4 : 6), 0, 0);
        parent.addView(t);
    }

    private EditText field(LinearLayout parent, String labelText, String value,
                           String hint, boolean multiline) {
        label(parent, labelText);
        EditText e = new EditText(this);
        e.setText(value);
        // 值已存在时不再重复显示同样的 hint，避免"值和提示一模一样"的冗余
        e.setHint(value == null || value.isEmpty() ? hint : "");
        e.setTextSize(profile.watch ? 12 : 14);
        e.setTextColor(getColor(R.color.text_primary));
        e.setHintTextColor(getColor(R.color.text_secondary));
        e.setBackgroundResource(R.drawable.bg_field);
        int ph = dp(profile.watch ? 6 : 10);
        e.setPadding(dp(12), ph, dp(12), ph);
        e.setSingleLine(!multiline);
        e.setGravity(Gravity.START | Gravity.TOP);
        parent.addView(e, matchWrap());
        if (hint != null && !hint.isEmpty() && !hint.equals(value)) helper(parent, hint);
        return e;
    }

    private EditText numberField(LinearLayout parent, String labelText, String value,
                                 String hint, final int lo, final int hi) {
        final EditText e = field(parent, labelText, value, hint, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String v = s.toString().trim();
                if (v.isEmpty()) {
                    e.setError(null);
                    return;
                }
                try {
                    int n = Integer.parseInt(v);
                    e.setError(n < lo || n > hi ? "范围 " + lo + " ~ " + hi : null);
                } catch (NumberFormatException ex) {
                    e.setError(getString(R.string.error_number));
                }
            }
        });
        return e;
    }

    private CheckBox check(LinearLayout parent, String labelText, boolean checked, String hint) {
        CheckBox c = new CheckBox(this);
        c.setText(labelText);
        c.setTextSize(profile.watch ? 12 : 14);
        c.setTextColor(getColor(R.color.text_primary));
        c.setChecked(checked);
        c.setPadding(0, dp(8), 0, 0);
        parent.addView(c);
        if (hint != null) helper(parent, hint);
        return c;
    }

    private Button wideButton(LinearLayout parent, String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(profile.watch ? 12 : 14);
        b.setTextColor(getColor(primary ? R.color.text_on_primary : R.color.text_primary));
        b.setBackgroundResource(primary
                ? R.drawable.bg_button_primary : R.drawable.bg_button_tonal);
        if (primary) {
            QrTheme.tintPrimary(b, this);
        } else {
            QrTheme.tintTonal(b, this);
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 版本号取构建注入的 versionName。 */
    private String versionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String text(EditText e) {
        return e.getText().toString().trim();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}





































