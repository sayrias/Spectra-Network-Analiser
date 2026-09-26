package com.spectra.analyzer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 13, 15);
    private static final int PANEL = Color.rgb(23, 24, 27);
    private static final int PANEL_2 = Color.rgb(31, 32, 36);
    private static final int LINE = Color.rgb(47, 48, 52);
    private static final int TEXT = Color.rgb(255, 246, 235);
    private static final int MUTED = Color.rgb(157, 157, 165);
    private static final int ACCENT = Color.rgb(255, 131, 49);
    private static final int TEAL = Color.rgb(255, 172, 91);
    private static final int BLUE = Color.rgb(215, 180, 134);
    private static final int AMBER = Color.rgb(255, 205, 121);
    private static final int CORAL = Color.rgb(255, 103, 80);
    private static final int GREEN = Color.rgb(101, 214, 166);

    private FrameLayout pageHost;
    private final List<View> pages = new ArrayList<>();
    private final List<Button> navButtons = new ArrayList<>();
    private TextView connectionChip, alertChip, calibrationLabel, sourceLabel;
    private TextView averageValue, peakValue, affectedValue, packetValue;
    private TextView mgmtValue, dataValue, ctrlValue, totalValue;
    private LinearLayout journalRows;
    private boolean journalWarnings;
    private DeviceIdentity identities;
    private TextView diagnostics;
    private TextView connectionDetail, discoveredText;
    private SpectrumView homeSpectrum, fullSpectrum;
    private FlowView homeFlow, fullFlow;
    private ListView deviceList;
    private DeviceAdapter deviceAdapter;
    private JSONArray wifiDevices = new JSONArray(), bleDevices = new JSONArray();
    private final List<String> discoveredDevices = new ArrayList<>();
    private boolean showingWifi = true;
    private Button categoryPicker;
    private int deviceCategory;
    private static final String[] DEVICE_CATEGORIES={"Wi-Fi", "BLE", "Trafik", "AirTag / Find My", "iBeacon", "BLE sensörler"};
    private SeekBar thresholdBar, channelsBar, holdBar, cooldownBar, volumeBar, buzzerLevelBar;
    private TextView thresholdOutput, channelsOutput, holdOutput, cooldownOutput, volumeOutput, buzzerLevelOutput;
    private Switch alertsSwitch, vibrateSwitch, buzzerSwitch;
    private final Button[] profileButtons = new Button[3];
    private int selectedQuickProfile = -1;
    private TextView profileSummary;
    private boolean settingsDirty;
    private Button tonePicker, scanPicker, buzzerTonePicker;
    private TextView scanApplyStatus;
    private boolean deviceConnected;
    private int pendingScanSamples;
    private final Runnable scanTimeout=()->{if(pendingScanSamples>0 && scanApplyStatus!=null)scanApplyStatus.setText("Cihaz onayı gelmedi · tekrar seçerek deneyin");};
    private android.app.Dialog expandedDialog;
    private final java.util.Set<android.app.Dialog> sheets=new java.util.HashSet<>();
    private Spinner toneSpinner, buzzerToneSpinner;
    private EditText hotspotSsid, hotspotPassword, manualIp;
    private SharedPreferences preferences;
    private boolean receiverRegistered;
    private String activeTransport = "";
    private RadarView radar;
    private JSONArray trafficDevices = new JSONArray();
    private boolean showingTraffic;
    private TextView performanceText, deviceSubtitle, trafficRate, streamHealth, firmwareHealth;
    private long lastFrameAt;
    private final android.os.Handler uiHandler = new android.os.Handler();
    private final Runnable freshness = new Runnable() {
        @Override public void run() {
            if (streamHealth != null && lastFrameAt > 0 && android.os.SystemClock.elapsedRealtime()-lastFrameAt > 3000 && !streamHealth.getText().toString().startsWith("Akış durdu")) {
                streamHealth.setText("Akış durdu · 3 saniyeden uzun süredir ölçüm yok");
                streamHealth.setTextColor(CORAL);packetValue.setText("—");
            }
            uiHandler.postDelayed(this,1000);
        }
    };
    private Switch watchEnabled, watchSound;
    private EditText watchRules;
    private Spinner scanSpinner;
    private final List<String> toneKeys = new ArrayList<>();
    private final List<String> toneNames = new ArrayList<>();
    private boolean waterfallPaused;
    private boolean pendingWifiRequest;
    private int currentPage;
    private static final int PICK_AUDIO=301, EXPORT_DATA=302;
    private String exportSnapshot="";


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { handleUpdate(intent); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences("spectra_settings", MODE_PRIVATE);
        pendingScanSamples=preferences.getBoolean("scan_pending",false)?preferences.getInt("scan_samples",4):0;
        identities = new DeviceIdentity(this);
        if (getIntent().hasExtra("simulator")) {
            boolean demo = getIntent().getBooleanExtra("simulator", false);
            preferences.edit().putString("transport_mode", demo ? "demo" : "auto")
                    .putBoolean("simulator_mode", demo).apply();
        }
        View content = buildInterface();
        setContentView(content);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(12), dp(7) + top, dp(12), dp(8) + bottom);
            return insets;
        });
        content.requestApplyInsets();
        loadSettings();
        requestRuntimePermissions();
        startForegroundService(new Intent(this, AnalyzerService.class));
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AnalyzerService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        receiverRegistered = true;
        renderJournal();uiHandler.post(freshness);
    }

    @Override protected void onStop() {
        if (receiverRegistered) unregisterReceiver(receiver);
        receiverRegistered = false;uiHandler.removeCallbacks(freshness);
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 24) {
            if (pendingWifiRequest) {
                pendingWifiRequest = false;
                if (hasWifiPermission()) requestFastWifi();
                else Toast.makeText(this, "Wi-Fi bağlantısı için yakındaki cihaz iznini açın.", Toast.LENGTH_LONG).show();
            } else startService(new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_DISCOVER));
        }
    }

    private View buildInterface() {
        LinearLayout root = vertical();
        root.setPadding(dp(12), dp(7), dp(12), dp(8));
        root.setBackgroundColor(BG);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(54)));
        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));
        addPage(buildDashboard());
        addPage(buildSpectrumPage());
        addPage(buildConnectionPage());
        addPage(buildDevicesPage());
        addPage(buildSettingsPage());
        for (View page : pages) normalizeControls(page);
        root.addView(buildNavigation(), new LinearLayout.LayoutParams(-1, dp(60)));
        showPage(0);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = vertical();
        titles.setPadding(dp(5), 0, 0, 0);
        TextView title = text("spectra", 25, TEXT, true);
        title.setLetterSpacing(-.025f);
        TextView subtitle = text("RF FIELD STUDIO  /  24", 8, ACCENT, true);
        subtitle.setLetterSpacing(.18f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        connectionChip = chip("ARANIYOR", AMBER);
        connectionChip.setOnClickListener(v -> showPage(2));
        header.addView(connectionChip);
        return header;
    }

    private View buildDashboard() {
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);

        LinearLayout stateCard = horizontal();
        stateCard.setGravity(Gravity.CENTER_VERTICAL);
        stateCard.setPadding(dp(14), dp(11), dp(14), dp(11));
        stateCard.setBackground(round(PANEL, LINE, 22));
        LinearLayout stateCopy = vertical();
        alertChip = text("ÖLÇÜM BEKLENİYOR", 12, ACCENT, true);
        calibrationLabel = text("Ortam kalibrasyonu bekleniyor", 9, MUTED, false);
        stateCopy.addView(alertChip);
        stateCopy.addView(calibrationLabel);
        stateCard.addView(stateCopy, new LinearLayout.LayoutParams(0, -2, 1));
        sourceLabel = chip("KAYNAK —", MUTED);
        stateCard.addView(sourceLabel);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.setMargins(dp(4), dp(5), dp(4), dp(7));
        body.addView(stateCard, stateParams);

        GridLayout metrics = new GridLayout(this);
        metrics.setColumnCount(2);
        averageValue = metric(metrics, "RF DOLULUĞU", "—", TEAL);
        peakValue = metric(metrics, "TEPE FREKANS", "—", BLUE);
        affectedValue = metric(metrics, "DEĞİŞEN KANAL", "—", AMBER);
        packetValue = metric(metrics, "PAKET / SN", "—", GREEN);
        body.addView(metrics);

        homeSpectrum = new SpectrumView(this);
        body.addView(panel("Waterfall", "2.400–2.525 GHz  /  RPD doluluğu",
                homeSpectrum, dp(245)));
        homeFlow = new FlowView(this);
        LinearLayout flowBlock = vertical();
        flowBlock.addView(homeFlow, new LinearLayout.LayoutParams(-1, dp(135)));
        flowBlock.addView(flowStats());
        body.addView(panel("Havadaki trafik", "İçerik değil, yalnızca anlık çerçeve türü sayacı",
                flowBlock, -2));
        body.addView(buildJournal(), cardParams());
        addMeasurementNotice(body);
        return scroll;
    }

    private View buildSpectrumPage() {
        LinearLayout body = pageBody();
        ScrollView scroll=scroll();scroll.addView(body);
        TextView heading = text("Canlı spektrum", 20, TEXT, true);
        heading.setPadding(dp(5), dp(10), 0, dp(2));
        body.addView(heading);
        TextView sub = text("126 frekans adımı · zaman geçmişi · anlık eğri", 10, MUTED, false);
        sub.setPadding(dp(5), 0, 0, dp(12));
        body.addView(sub);
        LinearLayout controls=horizontal();
        Button pause=iconButton(Icons.PAUSE,"Waterfall duraklat / sürdür");
        pause.setOnClickListener(v->{waterfallPaused=!waterfallPaused;homeSpectrum.setPaused(waterfallPaused);fullSpectrum.setPaused(waterfallPaused);Icons.apply(pause,waterfallPaused?Icons.PLAY:Icons.PAUSE,21);});
        Button clear=iconButton(Icons.REFRESH,"Görünüm geçmişini temizle");
        clear.setOnClickListener(v->{homeSpectrum.clear();fullSpectrum.clear();});
        controls.addView(pause,new LinearLayout.LayoutParams(dp(48),dp(44)));
        controls.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(44)));
        Button hold=actionButton("Tepe izi",false);
        hold.setOnClickListener(v->{boolean enabled=!v.isSelected();v.setSelected(enabled);homeSpectrum.setPeakHold(enabled);fullSpectrum.setPeakHold(enabled);hold.setText(enabled?"Tepe izi açık":"Tepe izi");});
        controls.addView(hold,new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(controls);
        fullSpectrum = new SpectrumView(this);
        body.addView(panel("Spektrum", "Mavi → camgöbeği → sarı → kırmızı · RPD %0–100",
                fullSpectrum, dp(300)));
        fullFlow = new FlowView(this);
        body.addView(panel("Paket akışı", "Yönetim / veri / kontrol · paket/sn", fullFlow, dp(170)));
        return scroll;
    }

    private View buildConnectionPage() {
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);
        titleBlock(body, "BAĞLANTI MERKEZİ", "ESP32’yi Wi-Fi, telefon hotspot’u veya Bluetooth LE ile kullanın");

        LinearLayout status = card();
        status.addView(text("BAĞLANTI DURUMU", 10, ACCENT, true));
        connectionDetail = text("Yakındaki SPECTRA cihazı aranıyor…", 14, TEXT, true);
        connectionDetail.setPadding(0, dp(8), 0, dp(8));
        status.addView(connectionDetail);
        performanceText = text("ESP32 bekleniyor · 126 frekans adımı", 11, MUTED, false);
        status.addView(performanceText);
        streamHealth=text("Akış ölçümü bekleniyor",10,MUTED,false);
        streamHealth.setPadding(0,dp(7),0,0);status.addView(streamHealth);
        diagnostics = text("Donanım sağlığı bekleniyor", 10, MUTED, false);
        diagnostics.setPadding(0,dp(8),0,dp(8)); status.addView(diagnostics);
        firmwareHealth=text("Aktarım tanısı bekleniyor",10,MUTED,false);status.addView(firmwareHealth);
        discoveredText = text("Henüz cihaz bulunmadı.", 10, MUTED, false);
        status.addView(discoveredText);
        LinearLayout quick = horizontal();
        quick.setPadding(0, dp(12), 0, 0);
        Button auto = actionButton("Otomatik bağlan", true);
        Button direct = actionButton("Hızlı Wi-Fi", false);
        Button ble = actionButton("Bluetooth", false);
        auto.setOnClickListener(v -> connectMode("auto", null));
        direct.setOnClickListener(v -> requestFastWifi());
        ble.setOnClickListener(v -> connectMode("ble", null));
        quick.addView(auto, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(0, dp(46), 1);
        qp.setMarginStart(dp(6)); quick.addView(direct, qp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1);
        bp.setMarginStart(dp(6)); quick.addView(ble, bp);
        status.addView(quick);
        TextView pass=text("SPECTRA-24  ·  Şifre: spectrum24\nWi-Fi daha yüksek aktarım kapasitesi sağlar; BLE yedek bağlantıdır.",11,AMBER,false);
        pass.setPadding(0,dp(12),0,0);status.addView(pass);
        Button systemWifi = actionButton("Telefon Wi-Fi ayarlarını aç",false);
        systemWifi.setOnClickListener(v->openWifiSettings());
        LinearLayout systemActions=horizontal();systemWifi.setText("Wi-Fi ayarları");
        systemActions.addView(systemWifi,new LinearLayout.LayoutParams(0,dp(48),1));
        Button appPermissions = actionButton("Uygulama izinlerini aç",false);
        appPermissions.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        appPermissions.setText("Uygulama izinleri");
        systemActions.addView(appPermissions,new LinearLayout.LayoutParams(0,dp(48),1));status.addView(systemActions);
        body.addView(status, cardParams());

        LinearLayout hotspot = card();
        hotspot.addView(text("TELEFON HOTSPOT’UNA BAĞLA", 13, TEXT, true));
        TextView hotspotInfo = text("Telefonunuzda internet paylaşımını açın. Aşağıdaki bilgileri BLE üzerinden ESP32’ye yerel komut olarak gönderin.", 10, MUTED, false);
        hotspotInfo.setPadding(0, dp(4), 0, dp(9));
        hotspot.addView(hotspotInfo);
        hotspotSsid = field("Hotspot adı (SSID)", false);
        hotspotPassword = field("Hotspot parolası", true);
        hotspot.addView(hotspotSsid, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout.LayoutParams passwordParams = new LinearLayout.LayoutParams(-1, dp(50));
        passwordParams.topMargin = dp(7); hotspot.addView(hotspotPassword, passwordParams);
        Button provision = actionButton("BLE İLE ESP32’YE GÖNDER", true);
        provision.setOnClickListener(v -> provisionHotspot());
        LinearLayout.LayoutParams provisionParams = new LinearLayout.LayoutParams(-1, dp(48));
        provisionParams.topMargin = dp(9); hotspot.addView(provision, provisionParams);
        body.addView(hotspot, cardParams());

        LinearLayout manual = card();
        manual.addView(text("LAN / MANUEL IP", 13, TEXT, true));
        TextView manualInfo = text("BLE kullanılamıyorsa hotspot istemci listesindeki ESP32 IP adresini girin.", 10, MUTED, false);
        manualInfo.setPadding(0, dp(3), 0, dp(8)); manual.addView(manualInfo);
        LinearLayout manualRow = horizontal();
        manualIp = field("Örn. 192.168.43.120", false);
        manualIp.setInputType(InputType.TYPE_CLASS_PHONE);
        manualIp.setText(preferences.getString("device_ip", ""));
        manualRow.addView(manualIp, new LinearLayout.LayoutParams(0, dp(49), 1));
        Button manualConnect = actionButton("BAĞLAN", false);
        manualConnect.setOnClickListener(v -> connectMode("wifi", manualIp.getText().toString().trim()));
        LinearLayout.LayoutParams manualButton = new LinearLayout.LayoutParams(dp(100), dp(49));
        manualButton.setMarginStart(dp(7)); manualRow.addView(manualConnect, manualButton);
        manual.addView(manualRow);
        body.addView(manual, cardParams());
        return scroll;
    }

    private View buildDevicesPage() {
        LinearLayout body = pageBody();
        LinearLayout heading = horizontal();
        LinearLayout titles = vertical();
        titles.addView(text("Çevre radarı", 22, TEXT, true));
        deviceSubtitle=text("Sinyal gücü görünümü · yön / mesafe ölçümü değil",10,MUTED,false);
        titles.addView(deviceSubtitle);
        heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = iconButton(Icons.REFRESH,"Cihaz listesini yenile");
        refresh.setOnClickListener(v -> sendDeviceCommand("{\"action\":\"scan\"}"));
        heading.addView(refresh);
        heading.setPadding(dp(5), dp(10), dp(5), dp(13));
        body.addView(heading);

        radar=new RadarView(this);
        radar.setBackground(round(PANEL,LINE,24));
        radar.setOnDeviceSelected(position -> { deviceList.smoothScrollToPosition(position); showDeviceDetails(position); });
        body.addView(radar,new LinearLayout.LayoutParams(-1,dp(220)));
        LinearLayout tools=horizontal();
        categoryPicker=actionButton("Wi-Fi",true);categoryPicker.setContentDescription("Radar kategorisi seç");
        categoryPicker.setOnClickListener(v->showDeviceCategories());
        tools.addView(categoryPicker,new LinearLayout.LayoutParams(0,dp(48),1.35f));
        Button actions=actionButton("İşlemler",false);actions.setContentDescription("Radar işlemleri");
        actions.setOnClickListener(v->showDeviceActions());
        tools.addView(actions,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(tools);
        deviceList = new ListView(this);
        deviceList.setDividerHeight(0);
        deviceList.setSelector(android.R.color.transparent);
        deviceList.setPadding(0, dp(7), 0, dp(7));
        deviceAdapter = new DeviceAdapter();
        deviceList.setAdapter(deviceAdapter);
        deviceList.setOnItemClickListener((p,v,pos,id)->showDeviceDetails(pos));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1);
        listParams.topMargin = dp(7); body.addView(deviceList, listParams);
        return body;
    }

    private View buildSettingsPage() {
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);
        titleBlock(body, "ALGILAMA & UYARILAR", "Adaptif tabana göre hassasiyet ve telefon davranışı");
        TextView build=text("SPECTRA "+appVersion()+" · özel arayüz",11,ACCENT,true);
        build.setPadding(dp(5),0,0,dp(12));body.addView(build);

        LinearLayout presets = card();
        presets.addView(text("HIZLI PROFİL", 10, ACCENT, true));
        LinearLayout presetRow = horizontal();
        presetRow.setPadding(0, dp(8), 0, 0);
        String[] names = {"HASSAS", "DENGELİ", "SAKİN"};
        for (int i = 0; i < names.length; i++) {
            final int profile = i;
            Button button = actionButton(names[i], false);
            profileButtons[i] = button;
            button.setOnClickListener(v -> applyProfile(profile));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(43), 1);
            if (i > 0) params.setMarginStart(dp(7));
            presetRow.addView(button, params);
        }
        presets.addView(presetRow);
        profileSummary=text("",11,ACCENT,false);profileSummary.setPadding(dp(4),dp(8),0,0);presets.addView(profileSummary);
        body.addView(presets, cardParams());

        LinearLayout detection = card();
        detection.addView(text("YANLIŞ ALARM KORUMASI", 10, ACCENT, true));
        alertsSwitch = toggleRow(detection, "Kalibre edilmiş RF uyarıları", true);
        thresholdBar = range(detection, "Taban üzeri değişim", 70, thresholdOutput = output());
        channelsBar = range(detection, "En az anomali kanalı", 92, channelsOutput = output());
        holdBar = range(detection, "Kesintisiz doğrulama", 37, holdOutput = output());
        cooldownBar = range(detection, "Tekrar uyarı aralığı", 58, cooldownOutput = output());
        Button calibrate = actionButton("ORTAMI YENİDEN KALİBRE ET", false);
        calibrate.setOnClickListener(v -> calibrate());
        detection.addView(calibrate, new LinearLayout.LayoutParams(-1, dp(46)));
        body.addView(detection, cardParams());

        LinearLayout phone = card();
        phone.addView(text("TELEFON UYARISI", 10, ACCENT, true));
        volumeBar = range(phone, "Ses düzeyi", 100, volumeOutput = output());
        phone.addView(settingLabel("Uyarı sesi"));
        toneNames.clear(); toneKeys.clear();
        java.util.Collections.addAll(toneNames,"Yumuşak uyarı","Sonar","Hızlı darbe","Dijital siren");
        java.util.Collections.addAll(toneKeys,"soft","sonar","pulse","siren");
        try { JSONArray saved=new JSONArray(preferences.getString("custom_tones","[]"));for(int i=0;i<saved.length();i++){JSONObject t=saved.getJSONObject(i);toneNames.add(t.getString("name"));toneKeys.add(t.getString("uri"));}}catch(Exception ignored){}
        toneSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, toneNames) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT); view.setTextSize(12); view.setPadding(dp(12), 0, dp(8), 0);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(TEXT); view.setBackgroundColor(PANEL_2);
                view.setPadding(dp(14), dp(13), dp(14), dp(13)); return view;
            }
        };
        toneSpinner.setAdapter(adapter);
        toneSpinner.setBackground(round(PANEL_2, LINE, 11));
        tonePicker=choicePicker("Uyarı sesi",toneSpinner);
        phone.addView(tonePicker, new LinearLayout.LayoutParams(-1, dp(48)));
        Button importTone=actionButton("Ses dosyaları ekle",false);
        importTone.setOnClickListener(v->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick,PICK_AUDIO);
        });
        phone.addView(importTone,new LinearLayout.LayoutParams(-1,dp(46)));
        phone.addView(text("MP3 / WAV / OGG · birden çok dosya seçerek kendi ses setini oluştur.",10,MUTED,false));
        vibrateSwitch = toggleRow(phone, "Titreşim", true);
        vibrateSwitch.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean("vibrate", checked).apply());
        LinearLayout actions = horizontal();
        Button test = actionButton("Uyarıyı dene", false);
        Button save = actionButton("KAYDET", true);
        test.setOnClickListener(v -> testAlarm());
        save.setOnClickListener(v -> saveSettings());
        actions.addView(test, new LinearLayout.LayoutParams(0, dp(47), 1));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(47), 1);
        saveParams.setMarginStart(dp(8)); actions.addView(save, saveParams);
        phone.addView(actions);
        body.addView(phone, cardParams());

        LinearLayout deviceBuzzer = card();
        deviceBuzzer.addView(text("ESP32 BUZZER", 10, ACCENT, true));
        deviceBuzzer.addView(text("Telefon bağlı olmasa da kademeli RF uyarısı verir.",10,MUTED,false));
        buzzerSwitch = toggleRow(deviceBuzzer, "Cihaz buzzerı", true);
        buzzerLevelBar = range(deviceBuzzer, "Buzzer şiddeti", 100, buzzerLevelOutput = output());
        buzzerToneSpinner = new Spinner(this);
        ArrayAdapter<String> buzzerTones = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Derin ton", "RWR darbesi", "Keskin alarm"});
        buzzerToneSpinner.setAdapter(buzzerTones);
        buzzerToneSpinner.setBackground(round(PANEL_2, LINE, 11));
        buzzerTonePicker=choicePicker("Buzzer tonu",buzzerToneSpinner);
        deviceBuzzer.addView(buzzerTonePicker,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout buzzerActions=horizontal();
        Button buzzerTest=actionButton("CİHAZDA DENE",false);
        buzzerTest.setOnClickListener(v->{
            saveSettings();
            // Wi-Fi and BLE transports both preserve command order. Queue the
            // test immediately after settings instead of adding UI latency.
            sendDeviceCommand("{\"action\":\"buzzerTest\"}");
        });
        buzzerActions.addView(buzzerTest,new LinearLayout.LayoutParams(0,dp(46),1));
        Button buzzerSave=actionButton("KAYDET",true);
        buzzerSave.setOnClickListener(v->saveSettings());
        LinearLayout.LayoutParams buzzerSaveParams=new LinearLayout.LayoutParams(0,dp(46),1);
        buzzerSaveParams.setMarginStart(dp(8));buzzerActions.addView(buzzerSave,buzzerSaveParams);
        deviceBuzzer.addView(buzzerActions);
        deviceBuzzer.addView(text("Orta, yüksek ve aşırı doluluk seviyeleri aynı profil içinde farklı ritimlerle çalar.",10,MUTED,false));
        body.addView(deviceBuzzer,cardParams());

        LinearLayout advanced=card();
        advanced.addView(text("Tarama performansı",15,TEXT,true));
        scanSpinner=new Spinner(this);
        ArrayAdapter<String> speeds=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Hızlı · 4 örnek/kanal","Dengeli · 8 örnek/kanal","Ayrıntılı · 12 örnek/kanal"});
        scanSpinner.setAdapter(speeds);scanSpinner.setSelection(Math.max(0,preferences.getInt("scan_samples",4)/4-1));
        scanPicker=choicePicker("Tarama ayrıntısı",scanSpinner);
        advanced.addView(scanPicker,new LinearLayout.LayoutParams(-1,dp(48)));
        scanApplyStatus=text("Seçim anında gönderilir · yeniden bağlantı gerekmez",11,MUTED,false);
        scanApplyStatus.setPadding(dp(5),dp(6),dp(5),dp(10));advanced.addView(scanApplyStatus);
        Button chain=actionButton("ESP32 → telefon alarm zincirini test et",false);
        chain.setOnClickListener(v->sendDeviceCommand("{\"action\":\"alertTest\"}"));
        advanced.addView(chain,new LinearLayout.LayoutParams(-1,dp(48)));
        advanced.addView(text("Bu test ses ve bildirim yolunu sınar; RF algılama hassasiyetini ölçmez.",10,MUTED,false));
        body.addView(advanced,cardParams());
        LinearLayout watch=card();
        watch.addView(text("Cihaz imzası uyarıları",15,TEXT,true));
        watchEnabled=toggleRow(watch,"İmza kurallarını izle",preferences.getBoolean("watch_enabled",true));
        watchSound=toggleRow(watch,"Eşleşmede ses çal",preferences.getBoolean("watch_sound",false));
        watchRules=field("Ad, MAC öneki veya BLE servis UUID",false);
        watchRules.setSingleLine(false);watchRules.setMinLines(2);
        watchRules.setText(preferences.getString("watch_rules",""));
        watch.addView(watchRules,new LinearLayout.LayoutParams(-1,dp(80)));
        watch.addView(text("Kuralları virgülle ayır. Flock adları ve araştırma OUI listesi ayrıca kontrol edilir. OUI eşleşmesi düşük güvenlidir ve tek başına ses üretmez.",10,MUTED,false));
        Button saveWatch=actionButton("Kuralları kaydet",true);saveWatch.setOnClickListener(v->saveSettings());
        watch.addView(saveWatch,new LinearLayout.LayoutParams(-1,dp(46)));body.addView(watch,cardParams());
        Button stop = actionButton("Ölçümü durdur ve uygulamadan çık", false);
        stop.setOnClickListener(v -> stopApplication());
        body.addView(stop, cardParams());
        addMeasurementNotice(body);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){settingsDirty=true;selectedQuickProfile=-1;}
                updateSettingOutputs();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        thresholdBar.setOnSeekBarChangeListener(listener);
        channelsBar.setOnSeekBarChangeListener(listener);
        holdBar.setOnSeekBarChangeListener(listener);
        cooldownBar.setOnSeekBarChangeListener(listener);
        volumeBar.setOnSeekBarChangeListener(listener);
        buzzerLevelBar.setOnSeekBarChangeListener(listener);
        return scroll;
    }

    private View buildNavigation() {
        LinearLayout nav=horizontal(); nav.setPadding(dp(2),dp(6),dp(2),dp(6));
        nav.setBackgroundColor(Color.TRANSPARENT);
        String[] icons={Icons.HOME,Icons.SPECTRUM,Icons.LINK,Icons.RADAR,Icons.SETTINGS};
        String[] names={"Genel","Spektrum","Bağlantı","Radar ve cihazlar","Ayarlar"};
        for(int i=0;i<icons.length;i++){
            final int index=i;
            Button button=iconButton(icons[i],names[i]);Icons.apply(button,icons[i],25);button.setMinWidth(0);button.setMinimumWidth(0);
            button.setOnClickListener(v->showPage(index));navButtons.add(button);
            button.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(0, -1, 1);
            item.setMargins(dp(5), 0, dp(5), 0);
            nav.addView(button, item);
        }
        return nav;
    }

    private void handleUpdate(Intent intent) {
        String kind = intent.getStringExtra(AnalyzerService.EXTRA_KIND);
        if (kind == null) return;
        if (kind.equals("watch") || kind.equals("journal")) { renderJournal(); return; }
        if (kind.equals("connection")) {
            boolean connected = intent.getBooleanExtra("connected", false);
            deviceConnected=connected;
            if(!connected && pendingScanSamples>0){uiHandler.removeCallbacks(scanTimeout);scanApplyStatus.setText("Bağlantı kurulunca uygulanacak: "+pendingScanSamples+" örnek/kanal");}
            String transport = intent.getStringExtra("transport");
            if (transport != null) {
                boolean sourceChanged=activeTransport.equals("demo") != transport.equals("demo");
                activeTransport=transport;
                if(sourceChanged){wifiDevices=new JSONArray();bleDevices=new JSONArray();trafficDevices=new JSONArray();homeSpectrum.clear();fullSpectrum.clear();refreshRadar();}
            }
            String label = activeTransport.equals("demo") ? "DEMO"
                    : activeTransport.equals("ble") ? "BLE" : "WI-FI";
            connectionChip.setText(connected ? label : "BAĞLAN");
            connectionChip.setTextColor(connected ? (activeTransport.equals("demo") ? AMBER : GREEN) : CORAL);
            sourceLabel.setText(activeTransport.equals("demo") ? "SENTETİK DEMO"
                    : activeTransport.equals("ble") ? "CANLI BLE" : "CANLI WI-FI");
            sourceLabel.setTextColor(activeTransport.equals("demo") ? AMBER : TEAL);
            if (!connected) {
                sourceLabel.setText("BAĞLANTI YOK");
                alertChip.setText("ÖLÇÜM BEKLENİYOR");
                alertChip.setTextColor(MUTED);
                calibrationLabel.setText("Görünen son ölçümler güncel olmayabilir");
            }
            if (connectionDetail != null) {
                String message = safe(intent.getStringExtra("message"));
                String address = safe(intent.getStringExtra("address"));
                connectionDetail.setText(connected
                        ? (message.isEmpty() ? "Canlı ölçüm" : message)
                        + (address.isEmpty() ? "" : " · " + address)
                        : "ESP32 bağlantısı bekleniyor");
            }
            setDemo(activeTransport.equals("demo"));
            return;
        }
        if (kind.equals("spectrum")) {
            lastFrameAt=android.os.SystemClock.elapsedRealtime();
            if(streamHealth!=null && intent.hasExtra("receivedFrames")) {
                streamHealth.setText(intent.getLongExtra("receivedFrames",0)+" kare alındı · "+intent.getLongExtra("sequenceGaps",0)+" sıra boşluğu · "+intent.getLongExtra("invalidFrames",0)+" bozuk kare");
                streamHealth.setTextColor(GREEN);
            } else if(streamHealth!=null) {
                streamHealth.setText("DEMO · sentetik kareler alınıyor; donanım aktarım tanısı yok");
                streamHealth.setTextColor(AMBER);
            }
            byte[] values = intent.getByteArrayExtra("values");
            homeSpectrum.push(values); fullSpectrum.push(values);
            String transport = intent.getStringExtra("transport");
            if (transport != null) activeTransport = transport;
            connectionChip.setText(activeTransport.equals("demo") ? "DEMO" : activeTransport.equals("ble") ? "BLE" : "WI-FI");
            connectionChip.setTextColor(activeTransport.equals("demo") ? AMBER : GREEN);
            setDemo(activeTransport.equals("demo"));
            sourceLabel.setText(activeTransport.equals("demo")?"DEMO":activeTransport.equals("ble")?"BLE":"WI-FI");
            int average = intent.getIntExtra("average", 0);
            int peak = intent.getIntExtra("peak", 0);
            int peakChannel = intent.getIntExtra("peakChannel", 0);
            int affected = intent.getIntExtra("affected", 0);
            int flags = intent.getIntExtra("flags", 0);
            averageValue.setText(average + "%");
            peakValue.setText((2400 + peakChannel) + " MHz");
            peakValue.setContentDescription("Tepe frekans " + (2400 + peakChannel) + " MHz, doluluk yüzde " + peak);
            affectedValue.setText(String.valueOf(affected));
            boolean calibrating = (flags & 0x08) != 0;
            boolean alarm = (flags & 0x04) != 0;
            boolean candidate = (flags & 0x02) != 0;
            if (activeTransport.equals("demo")) {
                alertChip.setText("DEMO VERİSİ"); alertChip.setTextColor(AMBER);
                calibrationLabel.setText("Bu veri ESP32’den gelmiyor");
            } else if (calibrating) {
                alertChip.setText("ORTAM KALİBRE EDİLİYOR"); alertChip.setTextColor(ACCENT);
                calibrationLabel.setText("Cihazı sabit tutun; bu sırada alarm üretilmez");
            } else if (alarm) {
                alertChip.setText("GENİŞ BANT RF DEĞİŞİMİ"); alertChip.setTextColor(CORAL);
                calibrationLabel.setText("Kalibre edilmiş tabanın üzerinde sürekli anomali");
            } else if (candidate) {
                alertChip.setText("DEĞİŞİM DOĞRULANIYOR"); alertChip.setTextColor(AMBER);
                calibrationLabel.setText("Süre ve bant yayılımı kontrol ediliyor");
            } else {
                alertChip.setText("RF TABANI NORMAL"); alertChip.setTextColor(GREEN);
                calibrationLabel.setText("Adaptif taban etkin · anlık veri akıyor");
            }
            return;
        }
        if (kind.equals("spectra_device")) {
            String entry = intent.getStringExtra("name") + " · " + intent.getStringExtra("transport").toUpperCase(Locale.ROOT)
                    + " · " + intent.getStringExtra("address")
                    + (intent.getIntExtra("rssi", 0) == 0 ? "" : " · " + intent.getIntExtra("rssi", 0) + " dBm");
            if (!discoveredDevices.contains(entry)) discoveredDevices.add(entry);
            updateDiscoveredDevices();
            return;
        }
        if (kind.equals("ble_state") || kind.equals("discovery_state") || kind.equals("provision")
                || kind.equals("connection_hint") || kind.equals("error")) {
            String message = intent.getStringExtra("message");
            if (connectionDetail != null && message != null) connectionDetail.setText(message);
            if (kind.equals("error") && message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }

        String raw = intent.getStringExtra("json");
        if (raw == null) return;
        try {
            JSONObject object = new JSONObject(raw);
            switch (kind) {
                case "hello":
                    applyDeviceSettings(object.optJSONObject("settings"));
                    updateDeviceStatus(object);
                    break;
                case "status": updateDeviceStatus(object); break;
                case "flow":
                    int mgmt = object.optInt("mgmt"), data = object.optInt("data"), ctrl = object.optInt("ctrl");
                    int total = object.optInt("total");
                    long windowMs = Math.max(1, object.optLong("windowMs", 1000));
                    packetValue.setText(Math.round(total * 1000.0 / windowMs) + "/s");
                    float scale=1000f/windowMs;
                    mgmtValue.setText(String.valueOf(Math.round(mgmt*scale))); dataValue.setText(String.valueOf(Math.round(data*scale)));
                    ctrlValue.setText(String.valueOf(Math.round(ctrl*scale))); totalValue.setText(String.valueOf(Math.round(total*scale)));
                    trafficRate.setText(DeviceRules.bytes(Math.round(object.optLong("bytes")*scale))+"/sn gözlendi · CH "+object.optInt("channel")+" · "+windowMs+" ms pencere");
                    homeFlow.push(mgmt*scale, data*scale, ctrl*scale); fullFlow.push(mgmt*scale, data*scale, ctrl*scale);
                    break;
                case "wifi":
                    wifiDevices = object.optJSONArray("networks");
                    if (wifiDevices == null) wifiDevices = new JSONArray();
                    identities.learn(wifiDevices,true,activeTransport.equals("demo"));
                    if (showingWifi && !showingTraffic) deviceAdapter.notifyDataSetChanged();
                    refreshRadar();
                    break;
                case "ble":
                    bleDevices = object.optJSONArray("devices");
                    if (bleDevices == null) bleDevices = new JSONArray();
                    identities.learn(bleDevices,false,activeTransport.equals("demo"));
                    if (!showingWifi && !showingTraffic) deviceAdapter.notifyDataSetChanged();
                    refreshRadar();
                    break;
                case "traffic":
                    trafficDevices=object.optJSONArray("devices");if(trafficDevices==null)trafficDevices=new JSONArray();
                    identities.learn(trafficDevices,true,activeTransport.equals("demo"));
                    if(showingTraffic)deviceAdapter.notifyDataSetChanged();refreshRadar();break;
                case "alert": renderJournal(); break;
                case "saved":
                    JSONObject saved=object.optJSONObject("settings");
                    if(saved!=null && saved.has("samples"))confirmScanSamples(saved.optInt("samples"));
                    if(!activeTransport.equals("demo")) Toast.makeText(this, "Ayarlar ESP32’ye kaydedildi", Toast.LENGTH_SHORT).show(); break;
                case "wifi_config": Toast.makeText(this, "Hotspot bilgisi alındı; ESP32 bağlanıyor", Toast.LENGTH_LONG).show(); break;
            }
        } catch (Exception ignored) {}
    }

    private void updateDeviceStatus(JSONObject object) {
        if(object.has("samples"))confirmScanSamples(object.optInt("samples"));
        if(object.has("skippedSweeps")) {
            String extra="Ölçüm atlama: "+object.optLong("skippedSweeps")+" · UDP hata: "+object.optLong("udpSendErrors")
                +" · Wi-Fi "+(object.optBoolean("wifiPowerSave",true)?"tasarruf":"düşük gecikme");
            firmwareHealth.setText(extra);
        }
        if(diagnostics!=null && object.has("heapFree"))diagnostics.setText("Çalışma: "+object.optLong("uptime")/1000+" sn · Boş RAM: "+DeviceRules.bytes(object.optLong("heapFree"))+"\nEn düşük boş RAM: "+DeviceRules.bytes(object.optLong("heapMin"))+" · Wi-Fi CH "+object.optInt("wifiChannel"));
        if(performanceText!=null) performanceText.setText(activeTransport.equals("demo") ? "DEMO · sentetik test kaynağı"
            : object.has("scanHz") ? String.format(Locale.US,"%.1f tarama/sn  ·  %d örnek/kanal  ·  %s",object.optDouble("scanHz"),object.optInt("samples"),activeTransport.toUpperCase(Locale.ROOT))
            : "Canlı ESP32 · performans bilgisi bekleniyor");
        boolean calibrating = object.optBoolean("calibrating", false);
        int percent = object.optInt("calibration", calibrating ? 0 : 100);
        if (calibrating) {
            alertChip.setText("KALİBRASYON %" + percent); alertChip.setTextColor(ACCENT);
            calibrationLabel.setText("Kısa taban ölçümü boyunca cihazı sabit tutun");
        }
        if (connectionDetail != null) {
            String firmware = object.optString("firmware", "?");
            String network = activeTransport.equals("demo")
                    ? "PC tarafında üretilen sentetik test verisi"
                    : object.optBoolean("staConnected")
                    ? object.optString("staSsid") + " · " + object.optString("staIp")
                    : "Doğrudan ağ: SPECTRA-24 · 192.168.4.1";
            connectionDetail.setText("Firmware " + firmware + " · " + network);
        }
    }

    private void applyDeviceSettings(JSONObject settings) {
        if(settings!=null && settings.has("samples"))confirmScanSamples(settings.optInt("samples"));
        if (settings == null || settingsDirty) return; // A reconnect must not erase an unsaved choice.
        selectedQuickProfile=-1;
        thresholdBar.setProgress(settings.optInt("threshold", 30) - 10);
        channelsBar.setProgress(settings.optInt("channels", 28) - 8);
        holdBar.setProgress((settings.optInt("hold", 4000) - 1500) / 500);
        cooldownBar.setProgress((settings.optInt("cooldown", 30000) - 10000) / 5000);
        alertsSwitch.setChecked(settings.optBoolean("alerts", true));
        buzzerSwitch.setChecked(settings.optBoolean("buzzer", true));
        buzzerToneSpinner.setSelection(Math.max(0,Math.min(2,settings.optInt("buzzerTone",1))));
        buzzerLevelBar.setProgress(settings.optInt("buzzerLevel",65));
        updateSettingOutputs();
    }

    private void connectMode(String mode, String ip) {
        requestRuntimePermissions();
        Intent command = new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_CONNECT)
                .putExtra("mode", mode);
        if (ip != null && !ip.isEmpty()) command.putExtra("ip", ip);
        startService(command);
        startService(new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_DISCOVER));
        Toast.makeText(this, mode.equals("ble") ? "BLE bağlantısı aranıyor"
                : mode.equals("auto") ? "ESP32 otomatik aranıyor" : "Wi-Fi bağlantısı deneniyor", Toast.LENGTH_SHORT).show();
    }

    private void provisionHotspot() {
        requestRuntimePermissions();
        String ssid = hotspotSsid.getText().toString().trim();
        if (ssid.isEmpty()) { hotspotSsid.setError("Hotspot adını girin"); return; }
        preferences.edit().putString("hotspot_ssid", ssid).apply();
        startService(new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_PROVISION)
                .putExtra("ssid", ssid).putExtra("password", hotspotPassword.getText().toString()));
    }

    private void calibrate() {
        startService(new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_CALIBRATE));
        showPage(0);
        Toast.makeText(this, "Kalibrasyon başlatıldı; cihazı sabit tutun", Toast.LENGTH_LONG).show();
    }

    private void applyProfile(int profile) {
        settingsDirty=true;
        selectedQuickProfile=profile;
        if (profile == 0) {
            thresholdBar.setProgress(12); channelsBar.setProgress(12); holdBar.setProgress(1);
        } else if (profile == 1) {
            thresholdBar.setProgress(20); channelsBar.setProgress(20); holdBar.setProgress(5);
        } else {
            thresholdBar.setProgress(32); channelsBar.setProgress(36); holdBar.setProgress(13);
        }
        cooldownBar.setProgress(profile == 0 ? 2 : profile == 1 ? 4 : 10);
        updateSettingOutputs();
        saveSettings();
        Toast.makeText(this, "Profil ESP32’ye gönderildi", Toast.LENGTH_SHORT).show();
    }

    private void loadSettings() {
        selectedQuickProfile=-1;
        thresholdBar.setProgress(preferences.getInt("threshold", 30) - 10);
        channelsBar.setProgress(preferences.getInt("channels", 28) - 8);
        holdBar.setProgress((preferences.getInt("hold", 4000) - 1500) / 500);
        cooldownBar.setProgress((preferences.getInt("cooldown", 30000) - 10000) / 5000);
        volumeBar.setProgress(preferences.getInt("volume", 70));
        alertsSwitch.setChecked(preferences.getBoolean("alerts", true));
        vibrateSwitch.setChecked(preferences.getBoolean("vibrate", true));
        buzzerSwitch.setChecked(preferences.getBoolean("buzzer", true));
        buzzerToneSpinner.setSelection(Math.max(0,Math.min(2,preferences.getInt("buzzer_tone",1))));
        buzzerLevelBar.setProgress(preferences.getInt("buzzer_level",65));
        hotspotSsid.setText(preferences.getString("hotspot_ssid", ""));
        String tone = preferences.getString("tone", "soft");
        for (int i = 0; i < toneKeys.size(); i++) if (toneKeys.get(i).equals(tone)) toneSpinner.setSelection(i);
        boolean demo = "demo".equals(preferences.getString("transport_mode", "auto"));
        activeTransport = demo ? "demo" : "";
        setDemo(demo);
        updateSettingOutputs();
    }

    private void saveSettings() {
        settingsDirty=false;
        int threshold = thresholdBar.getProgress() + 10;
        int channels = channelsBar.getProgress() + 8;
        int hold = 1500 + holdBar.getProgress() * 500;
        int cooldown = 10000 + cooldownBar.getProgress() * 5000;
        String tone = toneKeys.get(toneSpinner.getSelectedItemPosition());
        preferences.edit().putInt("scan_samples",(scanSpinner.getSelectedItemPosition()+1)*4)
            .putBoolean("watch_enabled",watchEnabled.isChecked()).putBoolean("watch_sound",watchSound.isChecked())
            .putString("watch_rules",watchRules.getText().toString()).apply();
        preferences.edit().putInt("threshold", threshold).putInt("channels", channels)
                .putInt("hold", hold).putInt("cooldown", cooldown)
                .putInt("volume", volumeBar.getProgress()).putBoolean("alerts", alertsSwitch.isChecked())
                .putBoolean("vibrate", vibrateSwitch.isChecked()).putString("tone", tone)
                .putBoolean("buzzer",buzzerSwitch.isChecked())
                .putInt("buzzer_tone",buzzerToneSpinner.getSelectedItemPosition())
                .putInt("buzzer_level",buzzerLevelBar.getProgress()).apply();
        startService(new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_SETTINGS)
                .putExtra("threshold", threshold).putExtra("channels", channels)
                .putExtra("hold", hold).putExtra("cooldown", cooldown)
                .putExtra("volume", volumeBar.getProgress()).putExtra("alerts", alertsSwitch.isChecked())
                .putExtra("vibrate", vibrateSwitch.isChecked()).putExtra("tone", tone)
                .putExtra("buzzer",buzzerSwitch.isChecked())
                .putExtra("buzzerTone",buzzerToneSpinner.getSelectedItemPosition())
                .putExtra("buzzerLevel",buzzerLevelBar.getProgress()));
        updateSettingOutputs();
    }

    private void testAlarm() {
        AlertTone.play(this, toneKeys.get(toneSpinner.getSelectedItemPosition()), volumeBar.getProgress());
        boolean haptic = vibrateSwitch.isChecked() && AlertHaptics.play(this);
        Toast.makeText(this, !vibrateSwitch.isChecked() ? "Ses deneniyor · titreşim kapalı"
                : haptic ? "Ses ve titreşim deneniyor" : "Ses deneniyor · titreşim donanımı kullanılamıyor", Toast.LENGTH_SHORT).show();
    }

    private void updateSettingOutputs() {
        thresholdOutput.setText("+%" + (thresholdBar.getProgress() + 10));
        channelsOutput.setText((channelsBar.getProgress() + 8) + " kanal");
        float seconds = (1500 + holdBar.getProgress() * 500) / 1000f;
        holdOutput.setText(String.format(Locale.US, seconds % 1 == 0 ? "%.0f sn" : "%.1f sn", seconds));
        cooldownOutput.setText(((10000 + cooldownBar.getProgress() * 5000) / 1000) + " sn");
        volumeOutput.setText(volumeBar.getProgress() + "%");
        buzzerLevelOutput.setText(buzzerLevelBar.getProgress() + "%");
        refreshPickers();
        int selectedProfile=-1;
        int[][] profiles = {{12,12,1,2},{20,20,5,4},{32,36,13,10}};
        for(int i=0;i<profileButtons.length;i++) {
            Button button=profileButtons[i]; if(button==null)continue;
            boolean selected=selectedQuickProfile==i || (selectedQuickProfile<0
                && thresholdBar.getProgress()==profiles[i][0] && channelsBar.getProgress()==profiles[i][1]
                && holdBar.getProgress()==profiles[i][2] && cooldownBar.getProgress()==profiles[i][3]);
            if(selected)selectedProfile=i;
            button.setSelected(selected);button.setTextColor(selected?BG:TEXT);
            button.setBackground(buttonSurface(selected));
        }
        if(profileSummary!=null)profileSummary.setText((selectedProfile<0?"Özel ayarlar":"Seçili: "+new String[]{"Hassas","Dengeli","Sakin"}[selectedProfile])+(settingsDirty?" · uygulamak için Kaydet":""));
    }

    private void selectDeviceCategory(int category) {
        deviceCategory=category;showingWifi=category==0;showingTraffic=category==2;
        categoryPicker.setText(DEVICE_CATEGORIES[category]);
        deviceSubtitle.setText(category>=3?"Yayın imzası filtresi · kesin cihaz kimliği değil":showingTraffic?"Gözlenen radyo baytları · dosya boyutu değil":"Şematik RSSI radarı · açı ve mesafe ölçülmez");
        deviceAdapter.notifyDataSetChanged();
        deviceList.setSelection(0);
        refreshRadar();
    }

    private void updateDiscoveredDevices() {
        if (discoveredText == null) return;
        StringBuilder value = new StringBuilder();
        for (String item : discoveredDevices) value.append("").append(item).append('\n');
        discoveredText.setText(value.toString().trim());
        discoveredText.setTextColor(TEAL);
    }

    private void setDemo(boolean demo) {
        if (homeSpectrum != null) homeSpectrum.setDemo(demo);
        if (fullSpectrum != null) fullSpectrum.setDemo(demo);
        if (demo && sourceLabel != null) {
            sourceLabel.setText("SENTETİK DEMO"); sourceLabel.setTextColor(AMBER);
        }
    }

    private void stopApplication() {
        AlertTone.stop();
        startService(new Intent(this, AnalyzerService.class).setAction(AnalyzerService.ACTION_STOP));
        finishAndRemoveTask();
    }

    private void requestRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if(Build.VERSION.SDK_INT<=32 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
        {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 24);
    }

    private void openWifiSettings() {
        startActivity(new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS));
    }

    private void addPage(View view) { pages.add(view); pageHost.addView(view, new FrameLayout.LayoutParams(-1, -1)); }
    private void showPage(int index) {
        for (int i = 0; i < pages.size(); i++) pages.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        for (int i = 0; i < navButtons.size(); i++) {
            navButtons.get(i).setTextColor(i==index?ACCENT:MUTED);
            navButtons.get(i).setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ff8331),
                    round(i==index?0x1fff8331:Color.TRANSPARENT, Color.TRANSPARENT, 18),
                    round(Color.WHITE, Color.TRANSPARENT, 18)));
            navButtons.get(i).setSelected(i==index);
        }
        View selected=pages.get(index);selected.animate().cancel();selected.setAlpha(.5f);selected.setTranslationY(dp(5));
        selected.animate().alpha(1).translationY(0).setDuration(180).start();currentPage=index;
        if(radar!=null && index==3)radar.invalidate();
    }

    private void titleBlock(LinearLayout body, String title, String subtitle) {
        TextView heading = text(title, 18, TEXT, true); heading.setPadding(dp(5), dp(10), 0, dp(2)); body.addView(heading);
        TextView sub = text(subtitle, 10, MUTED, false); sub.setPadding(dp(5), 0, dp(5), dp(11)); body.addView(sub);
    }

    private LinearLayout flowStats() {
        LinearLayout row = horizontal();
        mgmtValue = miniStat(row, "YÖNETİM", TEAL); dataValue = miniStat(row, "VERİ", BLUE);
        ctrlValue = miniStat(row, "KONTROL", AMBER); totalValue = miniStat(row, "TOPLAM", TEXT);
        LinearLayout group=vertical();group.addView(row);
        trafficRate=text("Trafik ölçümü bekleniyor · sayılar paket/sn",9,MUTED,false);
        trafficRate.setPadding(dp(8),0,dp(8),dp(8));group.addView(trafficRate);
        return group;
    }

    private TextView miniStat(LinearLayout parent, String label, int color) {
        LinearLayout box = vertical(); box.setPadding(dp(8), dp(8), dp(8), dp(7));
        TextView value = text("0", 16, color, true); box.addView(value); box.addView(text(label, 8, MUTED, true));
        parent.addView(box, new LinearLayout.LayoutParams(0, -2, 1)); return value;
    }

    private TextView metric(GridLayout grid, String label, String initial, int accent) {
        LinearLayout card = vertical(); card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(round(PANEL, LINE, 16));
        TextView bar = new TextView(this); bar.setBackgroundColor(accent);
        card.addView(bar, new LinearLayout.LayoutParams(dp(18), dp(3)));
        TextView caption = text(label, 9, MUTED, true); caption.setLetterSpacing(.07f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(9); card.addView(caption, cp);
        TextView value = text(initial, 21, TEXT, true); value.setGravity(Gravity.BOTTOM);
        card.addView(value, new LinearLayout.LayoutParams(-1, 0, 1));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = dp(86);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(card, params); return value;
    }

    private LinearLayout panel(String title, String subtitle, View content, int height) {
        LinearLayout card = vertical(); card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(PANEL, LINE, 22));
        if(content instanceof SpectrumView) {
            LinearLayout toolbar=horizontal();toolbar.addView(text(title,14,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
            Button info=iconButton(Icons.INFO,title+" açıklaması");
            info.setOnClickListener(v->showSpectrumHelp());toolbar.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));
            Button expand=iconButton(Icons.EXPAND,title+" tam ekran");
            expand.setOnClickListener(v->expandSpectrum((SpectrumView)content,title));toolbar.addView(expand,new LinearLayout.LayoutParams(dp(48),dp(48)));
            card.addView(toolbar);
        } else card.addView(text(title,14,TEXT,true));
        TextView sub = text(subtitle, 9, MUTED, false); sub.setPadding(0, dp(2), 0, dp(9)); card.addView(sub);
        LinearLayout.LayoutParams contentParams = height == 0 ? new LinearLayout.LayoutParams(-1, 0, 1)
                : new LinearLayout.LayoutParams(-1, height);
        content.setBackground(round(BG, Color.TRANSPARENT, 11));
        content.setClipToOutline(true); card.addView(content, contentParams);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, height == 0 ? 0 : -2, height == 0 ? 1 : 0);
        outer.setMargins(dp(4), dp(7), dp(4), dp(7)); card.setLayoutParams(outer); return card;
    }

    private LinearLayout card() {
        LinearLayout card = vertical(); card.setPadding(dp(15), dp(13), dp(15), dp(14));
        card.setBackground(round(PANEL, LINE, 17)); return card;
    }
    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(4), dp(5), dp(4), dp(7)); return params;
    }

    private void addMeasurementNotice(LinearLayout body) {
        TextView note = text("ÖLÇÜM GERÇEĞİ  ·  nRF24 bir SDR değildir ve yaklaşık −64 dBm üzerindeki enerjinin görülme oranını ölçer. Uyarı; kalibrasyon tabanından geniş, sürekli sapmadır ve tek başına jammer kanıtı değildir.", 10, MUTED, false);
        note.setPadding(dp(13), dp(12), dp(13), dp(12)); note.setBackground(round(PANEL_2, LINE, 13));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(4), dp(7), dp(4), dp(14)); body.addView(note, params);
    }

    private SeekBar range(LinearLayout parent, String label, int max, TextView output) {
        LinearLayout line = horizontal(); line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(label, 12, TEXT, true), new LinearLayout.LayoutParams(0, dp(37), 1)); line.addView(output);
        parent.addView(line); SeekBar bar = new SeekBar(this); bar.setMax(max);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(TEAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(36)); params.bottomMargin = dp(4);
        parent.addView(bar, params); return bar;
    }

    private Switch toggleRow(LinearLayout parent, String label, boolean checked) {
        LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption = text(label, 12, TEXT, true); caption.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(caption, new LinearLayout.LayoutParams(0, dp(52), 1));
        Switch toggle = new Switch(this); toggle.setChecked(checked); row.addView(toggle); parent.addView(row); return toggle;
    }

    private EditText field(String hint, boolean password) {
        EditText input = new EditText(this); input.setHint(hint); input.setHintTextColor(MUTED); input.setTextColor(TEXT);
        input.setTextSize(12); input.setSingleLine(true); input.setPadding(dp(13), 0, dp(13), 0);
        input.setBackground(round(PANEL_2, LINE, 11));
        if (password) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private TextView settingLabel(String value) {
        TextView label = text(value, 12, TEXT, true); label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(0, dp(4), 0, 0); label.setHeight(dp(42)); return label;
    }
    private TextView output() { TextView output = text("", 11, TEAL, true); output.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); return output; }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setTextSize(11); button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setTextColor(TEXT); button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(primary?BG:TEXT);button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));button.setMinimumWidth(dp(48));button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);button.setMaxLines(2);
        button.setStateListAnimator(null); button.setElevation(0); button.setTranslationZ(0);
        button.setBackgroundTintList(null);
        button.setBackground(buttonSurface(primary));return button;
    }
    private android.graphics.drawable.Drawable buttonSurface(boolean active) {
        return new InsetDrawable(new RippleDrawable(ColorStateList.valueOf(0x44ffae63),
            round(active?ACCENT:PANEL_2,active?0xffffc07b:0xff46413b,12),round(Color.WHITE,Color.TRANSPARENT,10)),dp(3),dp(4),dp(3),dp(4));
    }
    private void normalizeControls(View view) {
        if(view instanceof Button) {
            ViewGroup.LayoutParams p=view.getLayoutParams();if(p!=null){p.height=dp(48);view.setLayoutParams(p);}
        } else if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)normalizeControls(group.getChildAt(i));
        }
    }
    private Button tabButton(String label, boolean active) { return actionButton(label, active); }
    private TextView chip(String label, int color) {
        TextView chip = text(label, 9, color, true); chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0); chip.setBackground(round(PANEL_2, 0xff41594f, 18));
        chip.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(34))); return chip;
    }

    private LinearLayout pageBody() { LinearLayout body = vertical(); body.setPadding(0, 0, 0, dp(8)); return body; }
    private ScrollView scroll() { ScrollView view = new ScrollView(this); view.setFillViewport(true); view.setVerticalScrollBarEnabled(false); return view; }
    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setBaselineAligned(false);view.setGravity(Gravity.CENTER_VERTICAL);return view; }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL)); view.setIncludeFontPadding(false); return view;
    }
    private GradientDrawable round(int fill, int stroke, float radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke); return drawable;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String safe(String value) { return value == null ? "" : value; }

    private static int bleKind(JSONObject d){return BleSignals.classify(d.optInt("manufacturer",-1),d.optString("mfgPrefix"),d.optInt("mfgLength"),d.optString("services"),d.optLong("signatureAgeMs",Long.MAX_VALUE));}
    private JSONArray visibleDevices(){
        if(deviceCategory<3)return showingTraffic?trafficDevices:showingWifi?wifiDevices:bleDevices;
        JSONArray filtered=new JSONArray();
        for(int i=0;i<bleDevices.length();i++){JSONObject d=bleDevices.optJSONObject(i);if(d!=null && bleKind(d)==deviceCategory-2)filtered.put(d);}
        return filtered;
    }
    private void showDeviceCategories(){
        LinearLayout choices=vertical();final android.app.Dialog[] menu={null};
        for(int i=0;i<DEVICE_CATEGORIES.length;i++){
            final int category=i;Button b=actionButton(DEVICE_CATEGORIES[i],i==deviceCategory);b.setSelected(i==deviceCategory);
            b.setOnClickListener(v->{selectDeviceCategory(category);menu[0].dismiss();});choices.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        menu[0]=showSheet("Gözlem kategorisi",choices,null,null);
    }
    private void showDeviceActions(){
        LinearLayout choices=vertical();final android.app.Dialog[] menu={null};
        String[] labels={"Taramayı yenile","CSV dışa aktar","BLE adlarını sorgula","Etiket algılama hakkında"};
        for(int i=0;i<labels.length;i++){
            final int action=i;Button b=actionButton(labels[i],false);
            b.setOnClickListener(v->{menu[0].dismiss();
                if(action==0)sendDeviceCommand("{\"action\":\"scan\"}");
                else if(action==1)exportDevices();
                else if(action==2)showSheet("Tek seferlik BLE ad sorgulaması",text("Yalnız scan-response içinde yayınlanan adlar okunabilir. Cihazlarla bağlantı veya eşleştirme yapılmaz.",12,MUTED,false),"Sorgula",()->sendDeviceCommand("{\"action\":\"scanNames\"}"));
                else showSheet("Etiket ve beacon gözlemi",text(BleSignals.caveat()+"\n\nAirTag / Find My ve iBeacon filtreleri firmware 1.0.0 ile gelen paket başlığına ihtiyaç duyar. Eski firmware'de Apple şirket kimliği tek başına yeterli değildir. Sensör filtresi 180D veya 181A servis beyanını kullanır. Bu uygulama takip edilme tespiti garantisi vermez.",12,MUTED,false),null,null);
            });choices.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        menu[0]=showSheet("Radar işlemleri",choices,null,null);
    }
    private Button iconButton(String icon,String description){
        Button b=actionButton("",false);Icons.apply(b,icon,20);b.setContentDescription(description);
        b.setTooltipText(description);return b;
    }
    private void requestFastWifi(){
        if (!hasWifiPermission()) {
            pendingWifiRequest = true;
            requestRuntimePermissions();
            return;
        }
        startService(new Intent(this,AnalyzerService.class).setAction(AnalyzerService.ACTION_WIFI));
    }
    private boolean hasWifiPermission() {
        return checkSelfPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void sendDeviceCommand(String json){
        startService(new Intent(this,AnalyzerService.class).setAction(AnalyzerService.ACTION_COMMAND).putExtra("command",json));
    }
    private void showDeviceDetails(int position){
        JSONObject d=visibleDevices().optJSONObject(position);if(d==null)return;
        final boolean wifi = showingWifi || showingTraffic;
        final boolean demo = activeTransport.equals("demo");
        String title=identities.title(d,wifi,demo);
        StringBuilder detail=new StringBuilder("Adres: ").append(DeviceRules.address(d));
        detail.append("\nSinyal: ").append(d.optBoolean("rssiKnown",true)?d.optInt("rssi",-127)+" dBm":"Alıcı adresi; RSSI ölçülmedi");
        detail.append("\nAd kaynağı: ").append(identities.source(d,wifi,demo));
        if(!wifi)detail.append("\nCihaz türü: ").append(DeviceMetadata.appearance(d.optInt("appearance")));
        if(wifi)detail.append("\nAdres açıklaması: ").append(DeviceMetadata.wifiAddress(DeviceRules.address(d)));
        if(d.has("ch"))detail.append("\nKanal: ").append(d.optInt("ch"));
        if(!wifi)detail.append("\nBLE servisleri: ").append(DeviceMetadata.services(d.optString("services")));
        if(!wifi){detail.append("\nYayın sınıfı: ").append(BleSignals.label(bleKind(d)));
            detail.append("\nİmza kanıtı: ").append(d.has("mfgPrefix")?d.optString("mfgPrefix")+" · "+d.optInt("mfgLength")+" bayt · "+d.optLong("signatureAgeMs")/1000+" sn önce":"Firmware imza başlığı göndermiyor; 1.0.0 gerekir");
            if(bleKind(d)!=BleSignals.UNKNOWN)detail.append("\n").append(BleSignals.caveat());}
        if(!wifi)detail.append("\nŞirket kimliği: ").append(DeviceMetadata.manufacturer(d.optInt("manufacturer")))
            .append("\nKimlik sınırı: Şirket kimliği yayıncının beyanıdır; doğrulanmış marka, model veya cihaz sahibi değildir.");
        if(showingTraffic){
            detail.append("\nGözlenen TX: ").append(DeviceRules.bytes(d.optLong("txBytes"))).append(" / ").append(d.optLong("txPackets")).append(" paket");
            detail.append("\nGözlenen RX: ").append(DeviceRules.bytes(d.optLong("rxBytes"))).append(" / ").append(d.optLong("rxPackets")).append(" paket");
            detail.append("\nSon gözlem: ").append(d.optLong("ageMs")/1000).append(" sn önce");
            detail.append("\nBu sayaçlar mevcut kanalda yakalanan 802.11 çerçeveleridir. Tekrarlar ve protokol başlıkları dahildir. Dosya miktarı veya tüm internet trafiği değildir.");
        }
        if(identities.name(d,wifi,demo).startsWith("Adsız cihaz") || identities.source(d,wifi,demo).contains("Şirket kimliği"))
            detail.append("\nNeden ad yok: Wi-Fi SSID veya BLE yerel adı bu yayında yok. BLE ad sorgulaması yalnız scan-response içinde yayınlanan adı bulabilir; yayınlanmayan ad/model çözülemez.");
        String match=DeviceRules.match(d,preferences.getString("watch_rules",""));
        if(!match.isEmpty())detail.append("\n\n").append(match);
        LinearLayout facts=vertical();
        for(String line:detail.toString().split("\\n")) {
            if(line.isEmpty())continue;
            LinearLayout block=vertical();block.setPadding(dp(12),dp(10),dp(12),dp(10));
            block.setBackground(round(PANEL_2,Color.TRANSPARENT,12));
            int colon=line.indexOf(':');
            if(colon>0 && colon<28) {
                block.addView(text(line.substring(0,colon),10,ACCENT,true));
                TextView value=text(line.substring(colon+1).trim(),12,TEXT,false);
                value.setPadding(0,dp(5),0,0);value.setTextIsSelectable(true);block.addView(value);
            } else block.addView(text(line,11,MUTED,false));
            LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2);gap.bottomMargin=dp(8);facts.addView(block,gap);
        }
        showSheet(title,facts,"Takma ad",()->editAlias(d,wifi,demo));
    }
    private void exportDevices(){
        StringBuilder csv=new StringBuilder("source,address,name,rssi,tx_bytes,rx_bytes,tx_packets,rx_packets\n");
        JSONArray[] groups={wifiDevices,bleDevices,trafficDevices};
        String[] types={"wifi","ble","traffic"};
        for(int g=0;g<groups.length;g++)for(int i=0;i<groups[g].length();i++){
            JSONObject d=groups[g].optJSONObject(i);if(d==null)continue;
            csv.append(types[g]).append(',').append(csvCell(DeviceRules.address(d))).append(',')
                .append(csvCell(identities.title(d,g!=1,activeTransport.equals("demo")))).append(',').append(d.optInt("rssi",-127)).append(',')
                .append(d.optLong("txBytes")).append(',').append(d.optLong("rxBytes")).append(',')
                .append(d.optLong("txPackets")).append(',').append(d.optLong("rxPackets")).append('\n');
        }
        exportSnapshot=csv.toString();
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv").putExtra(Intent.EXTRA_TITLE,"spectra-"+System.currentTimeMillis()+".csv"),EXPORT_DATA);
    }
    private String csvCell(String value){
        if(!value.isEmpty() && "=+-@".indexOf(value.charAt(0))>=0)value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null)return;
        if(requestCode==EXPORT_DATA && data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out!=null)out.write(exportSnapshot.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Dosya kaydedildi",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Dışa aktarılamadı",Toast.LENGTH_LONG).show();}
        }else if(requestCode==PICK_AUDIO){
            List<Uri> uris=new ArrayList<>();
            if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());
            else if(data.getData()!=null)uris.add(data.getData());
            try{
                JSONArray library=new JSONArray(preferences.getString("custom_tones","[]"));
                for(Uri uri:uris){
                    if(library.length()>=24)break;
                    getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String key=uri.toString();if(toneKeys.contains(key))continue;
                    String name="Özel ses "+(library.length()+1);
                    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                        if(c!=null&&c.moveToFirst())name=c.getString(0);
                    }
                    library.put(new JSONObject().put("uri",key).put("name",name));toneKeys.add(key);toneNames.add(name);
                }
                preferences.edit().putString("custom_tones",library.toString()).apply();
                ((ArrayAdapter<?>)toneSpinner.getAdapter()).notifyDataSetChanged();toneSpinner.setSelection(toneKeys.size()-1);refreshPickers();
                Toast.makeText(this,"Ses seti eklendi. Sesi dene ve kaydet.",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"Ses dosyası açılamadı",Toast.LENGTH_LONG).show();}
        }
    }

    private void refreshRadar() {
        if(radar!=null && identities!=null)
            radar.update(visibleDevices(),identities,showingWifi||showingTraffic,activeTransport.equals("demo"));
    }
    private void editAlias(JSONObject d, boolean wifi, boolean demo) {
        EditText input=field("Takma ad (boş bırakırsan kaldırılır)",false);
        LinearLayout body=vertical();
        TextView note=text("Yalnızca bu MAC adresine uygulanır. Rastgele adres değişirse otomatik birleştirilmez.",11,MUTED,false);
        note.setPadding(0,0,0,dp(14));body.addView(note);body.addView(input,new LinearLayout.LayoutParams(-1,dp(48)));
        showSheet("Bu adrese takma ad ver",body,"Kaydet",()->{
            identities.setAlias(d,wifi,demo,input.getText().toString());deviceAdapter.notifyDataSetChanged();refreshRadar();
        });
    }
    private View buildJournal() {
        LinearLayout card=card();
        card.addView(text("Olay günlüğü",17,TEXT,true));
        TextView sub=text("Zaman damgalı · son 100 olay · bu telefonda saklanır",10,MUTED,false);
        sub.setPadding(0,dp(6),0,dp(12));card.addView(sub);
        LinearLayout actions=horizontal();
        Button filter=actionButton("Tümü",false);
        filter.setOnClickListener(v->{journalWarnings=!journalWarnings;filter.setText(journalWarnings?"Uyarılar":"Tümü");renderJournal();});
        Button export=actionButton("Dışa aktar",false);
        export.setOnClickListener(v->{
            exportSnapshot=EventJournal.read(this).toString();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE,"spectra-olaylar.json"),EXPORT_DATA);
        });
        Button clear=iconButton(Icons.CLOSE,"Olay günlüğünü temizle");
        clear.setOnClickListener(v->showSheet("Kayıtlar silinsin mi?",
            text("Telefondaki olay geçmişi silinir. Önce dışa aktarabilirsiniz.",12,MUTED,false),
            "Temizle",()->{EventJournal.clear(this);renderJournal();}));
        actions.addView(filter,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,dp(48),1);gap.setMargins(dp(6),0,dp(6),0);
        actions.addView(export,gap);actions.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(48)));
        card.addView(actions);journalRows=vertical();card.addView(journalRows);renderJournal();return card;
    }
    private void renderJournal() {
        if(journalRows==null)return;
        journalRows.removeAllViews();JSONArray events=EventJournal.read(this);int shown=0;
        for(int i=0;i<events.length() && shown<6;i++) {
            JSONObject e=events.optJSONObject(i);if(e==null)continue;
            String level=e.optString("level");
            if(journalWarnings && !(level.equals("alert")||level.equals("warning")))continue;
            LinearLayout row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),dp(9),dp(10),dp(9));row.setBackground(round(PANEL_2,Color.TRANSPARENT,10));
            TextView icon=text("",18,level.equals("alert")?CORAL:ACCENT,false);
            Icons.apply(icon,level.equals("alert")||level.equals("warning")?Icons.SHIELD:Icons.LINK,18);
            row.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
            LinearLayout copy=vertical();copy.addView(text(e.optString("title"),12,TEXT,true));
            String detailValue=e.optString("detail");
            if(!detailValue.isEmpty()){TextView detail=text(detailValue,10,MUTED,false);detail.setPadding(0,dp(3),0,dp(3));copy.addView(detail);}
            copy.addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM,new Locale("tr","TR")).format(new Date(e.optLong("time"))),9,AMBER,false));
            row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);journalRows.addView(row,rp);shown++;
        }
        if(shown==0){TextView empty=text("Bu filtrede henüz olay yok. Bağlantı, hata ve uyarılar burada görünür.",11,MUTED,false);empty.setPadding(0,dp(15),0,dp(8));journalRows.addView(empty);}
        if(events.length()>6) {
            Button all=actionButton("Tüm "+events.length()+" olayı göster",false);
            all.setOnClickListener(v->showFullJournal());journalRows.addView(all,new LinearLayout.LayoutParams(-1,dp(48)));
        }
    }


    private void showFullJournal() {
        LinearLayout body=vertical(), filters=horizontal(), rows=vertical();
        Button all=actionButton("Tüm olaylar",true), warnings=actionButton("Uyarılar",false);
        filters.addView(all,new LinearLayout.LayoutParams(0,dp(48),1));
        filters.addView(warnings,new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(filters);body.addView(rows);
        java.util.function.Consumer<Boolean> render=onlyWarnings->{
            all.setBackground(buttonSurface(!onlyWarnings));all.setTextColor(!onlyWarnings?BG:TEXT);
            warnings.setBackground(buttonSurface(onlyWarnings));warnings.setTextColor(onlyWarnings?BG:TEXT);
            rows.removeAllViews();JSONArray events=EventJournal.read(this);
            for(int i=0;i<events.length();i++) {
                JSONObject e=events.optJSONObject(i);if(e==null)continue;
                boolean warning=e.optString("level").equals("alert")||e.optString("level").equals("warning");
                if(onlyWarnings&&!warning)continue;
                LinearLayout row=vertical();row.setPadding(dp(12),dp(12),dp(12),dp(12));
                row.setBackground(round(PANEL_2,warning?0x66ffb35a:LINE,12));
                TextView time=text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM,new Locale("tr","TR")).format(new Date(e.optLong("time"))),10,warning?AMBER:ACCENT,false);
                row.addView(time);
                TextView title=text(e.optString("title"),13,TEXT,true);title.setPadding(0,dp(6),0,dp(4));row.addView(title);
                if(!e.optString("detail").isEmpty())row.addView(text(e.optString("detail"),11,MUTED,false));
                LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2);gap.topMargin=dp(8);rows.addView(row,gap);
            }
            if(rows.getChildCount()==0)rows.addView(text("Bu filtrede henüz olay yok.",12,MUTED,false));
        };
        all.setOnClickListener(v->render.accept(false));warnings.setOnClickListener(v->render.accept(true));
        render.accept(false);showSheet("Olay geçmişi",body,null,null);
    }

    /** App-owned bottom sheet, including large-font scrolling and keyboard resize. */
    private android.app.Dialog showSheet(String title, View content, String action, Runnable onAction) {
        android.app.Dialog dialog=new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout shell=vertical();shell.setPadding(dp(18),dp(12),dp(18),dp(16));
        GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff252322,PANEL,BG});
        surface.setCornerRadius(dp(24));surface.setStroke(dp(1),0xff63503c);shell.setBackground(surface);
        View handle=new View(this);handle.setBackground(round(ACCENT,Color.TRANSPARENT,3));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(36),dp(3));hp.gravity=Gravity.CENTER;hp.bottomMargin=dp(12);shell.addView(handle,hp);
        LinearLayout header=horizontal();
        TextView heading=text(title,18,TEXT,true);heading.setPadding(0,0,dp(10),0);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button close=iconButton(Icons.CLOSE,"Pencereyi kapat");close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));shell.addView(header);
        ScrollView scroll=new ScrollView(this) {
            @Override protected void onMeasure(int width,int height) {
                int limit=(int)(getResources().getDisplayMetrics().heightPixels*.60f);
                int size=MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED?limit:Math.min(limit,MeasureSpec.getSize(height));
                super.onMeasure(width,MeasureSpec.makeMeasureSpec(size,MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(0,dp(12),0,dp(8));scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout footer=horizontal();
        Button done=actionButton("Kapat",false);done.setOnClickListener(v->dialog.dismiss());
        footer.addView(done,new LinearLayout.LayoutParams(0,dp(48),1));
        if(action!=null) {
            Button primary=actionButton(action,true);primary.setOnClickListener(v->{dialog.dismiss();onAction.run();});
            footer.addView(primary,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        shell.addView(footer);dialog.setContentView(shell);
        android.view.Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.7f);window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        sheets.add(dialog);dialog.setOnDismissListener(d->sheets.remove(dialog));
        if(window!=null){window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(580)),-2);window.setGravity(Gravity.BOTTOM);}
        shell.setAlpha(0);shell.setTranslationY(dp(16));shell.animate().alpha(1).translationY(0).setDuration(160).start();
        return dialog;
    }


    private String appVersion() {
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "sürüm bilinmiyor";}
    }
    private Button choicePicker(String title,Spinner model) {
        Button picker=actionButton(String.valueOf(model.getSelectedItem()),false);
        picker.setContentDescription(title+" seç");
        picker.setOnClickListener(v->{
            LinearLayout choices=vertical();
            for(int i=0;i<model.getCount();i++) {
                final int selected=i;
                Button option=actionButton(String.valueOf(model.getItemAtPosition(i)),i==model.getSelectedItemPosition());
                option.setSelected(i==model.getSelectedItemPosition());
                option.setOnClickListener(button->{
                    model.setSelection(selected);
                    if(model==scanSpinner)applyScanSelection((selected+1)*4);else settingsDirty=true;
                    refreshPickers();
                    if(choiceDialog!=null){choiceDialog.dismiss();choiceDialog=null;}
                });
                choices.addView(option,new LinearLayout.LayoutParams(-1,dp(48)));
            }
            choiceDialog=showSheet(title,choices,null,null);
        });
        return picker;
    }
    private android.app.Dialog choiceDialog;
    private void applyScanSelection(int samples){
        pendingScanSamples=samples;preferences.edit().putInt("scan_samples",samples).putBoolean("scan_pending",true).apply();
        uiHandler.removeCallbacks(scanTimeout);
        startService(new Intent(this,AnalyzerService.class).setAction(AnalyzerService.ACTION_SCAN_SETTINGS).putExtra("samples",samples));
        if(deviceConnected){
            scanApplyStatus.setText(samples+" örnek/kanal gönderildi · cihaz onayı bekleniyor");
            uiHandler.postDelayed(scanTimeout,8000);
        }else scanApplyStatus.setText("Bağlantı kurulunca uygulanacak: "+samples+" örnek/kanal");
    }
    private void confirmScanSamples(int samples){
        if(samples!=4 && samples!=8 && samples!=12)return;
        if(pendingScanSamples>0 && pendingScanSamples!=samples)return;
        pendingScanSamples=0;uiHandler.removeCallbacks(scanTimeout);
        scanSpinner.setSelection(samples/4-1);refreshPickers();
        preferences.edit().putBoolean("scan_pending",false).apply();
        scanApplyStatus.setText("Cihaz onayladı: "+samples+" örnek/kanal");
    }
    private void refreshPickers(){
        if(tonePicker!=null)tonePicker.setText(String.valueOf(toneSpinner.getSelectedItem()));
        if(scanPicker!=null)scanPicker.setText(String.valueOf(scanSpinner.getSelectedItem()));
        if(buzzerTonePicker!=null)buzzerTonePicker.setText(String.valueOf(buzzerToneSpinner.getSelectedItem()));
    }
    private void showSpectrumHelp() {
        LinearLayout body=vertical();
        String[][] sections={
            {"Nokta nokta görünüm neden normal?","nRF24 bir SDR değildir: sürekli I/Q veya dBm ölçmez. 126 frekansı sırayla örnekler. Wi-Fi ve BLE kısa paket patlamaları gönderdiği için bazı örneklerde enerji görülür, bazılarında görülmez. Koyu bir hücre tek başına veri kaybı değildir."},
            {"Yatay eksen · frekans","2400–2525 MHz, 1 MHz adımlar. Her sütun bir ayar frekansıdır; bu, kalibre edilmiş 1 MHz çözünürlüklü SDR demek değildir."},
            {"Dikey eksen · geçmiş","En yeni tamamlanmış tarama üsttedir. Kartta 96 satır görünür. Tam ekran noktaları büyütmez: aynı hücre boyutuyla ekrana sığan daha fazla geçmiş satırını gösterir. Son 512 gerçek ölçüm saklanır; henüz ölçülmemiş geçmiş boş kalır. Üstte görünen satır / ekran kapasitesi ve toplam kayıt sayısı yazılır. Bunlar paket değil RF tarama satırlarıdır. Bağlantı durursa zaman ekseni de durur; eşit piksel aralığı her zaman eşit zaman değildir."},
            {"Renkler · RPD doluluğu","Koyu/mavi düşük, camgöbeği/yeşil orta, sarı/kırmızı yüksek görülme oranıdır (%0–100). Çipte yaklaşık −64 dBm eşiğini aşan enerjinin örneklerde görülme oranı ölçülür. %0, ortamda hiç sinyal olmadığı anlamına gelmez."},
            {"Örnek sayısı ve boşluklar","4 örnek/kanal ham olarak yalnız %0, 25, 50, 75, 100 verir; firmware bunu yumuşatır. Daha ayrıntılı örnekleme için Ayarlar → Tarama ayrıntısı → 8 veya 12 seçip Kaydet kullan; tarama yavaşlar. Boşluklar sahte verilerle doldurulmaz."},
            {"Alttaki çizgiler","Camgöbeği eğri son görünen taramanın frekans profilidir. Tepe izi açıksa sarı çizgi bu görünümde alınan en yüksek değerleri tutar. Eğri alanı waterfall geçmişinden ayrıdır."},
            {"Akış mı kesildi, enerji mi düşük?","Tüm waterfall duruyorsa kare/sn, VERİ AKIŞI KESİLDİ ve Bağlantı ekranındaki sıra boşluğu/bozuk kare sayaçlarını kontrol et. Enerji noktalarının arasındaki koyu hücreler tek başına paket kaybını göstermez."},
            {"Kontroller","Sağa sürükle: gerçek ölçüm geçmişi. Sola sürükle: yeni ölçümler. Çift dokun veya Canlı: en yeni satır. Büyüt simgesi tam ekran açar; Geri/küçült eski yere döndürür. RF görünümü tek başına jammer teşhisi değildir."}
        };
        for(String[] section:sections){
            TextView heading=text(section[0],13,ACCENT,true);heading.setPadding(0,dp(14),0,dp(6));body.addView(heading);
            TextView explanation=text(section[1],12,TEXT,false);explanation.setLineSpacing(dp(3),1);body.addView(explanation);
        }
        showSheet("Waterfall rehberi",body,null,null);
    }
    private void expandSpectrum(SpectrumView view,String title) {
        if(expandedDialog!=null)return;
        ViewGroup original=(ViewGroup)view.getParent();
        int index=original.indexOfChild(view);ViewGroup.LayoutParams originalParams=view.getLayoutParams();
        view.setExpanded(true);
        original.removeView(view);
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Material_NoActionBar);
        expandedDialog=dialog;
        LinearLayout shell=vertical();shell.setBackgroundColor(BG);shell.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout header=horizontal();header.addView(text(title+" · tam ekran",18,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        Button info=iconButton(Icons.INFO,"Waterfall rehberi");info.setOnClickListener(v->showSpectrumHelp());header.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Button close=iconButton(Icons.COMPRESS,"Tam ekrandan çık");close.setOnClickListener(v->dialog.dismiss());header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        shell.addView(header);shell.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=horizontal();
        Button live=actionButton("Canlı",true);
        Button pause=actionButton(view.isPaused()?"Sürdür":"Duraklat",false);
        pause.setOnClickListener(v->{view.setPaused(!view.isPaused());pause.setText(view.isPaused()?"Sürdür":"Duraklat");});
        live.setOnClickListener(v->{view.setPaused(false);view.goLive();pause.setText("Duraklat");});
        controls.addView(live,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(pause,new LinearLayout.LayoutParams(0,dp(48),1));
        shell.addView(controls);dialog.setContentView(shell);
        dialog.setOnDismissListener(d->{shell.removeView(view);view.setExpanded(false);view.setPaused(waterfallPaused);original.addView(view,index,originalParams);expandedDialog=null;});
        android.view.Window window=dialog.getWindow();
        if(window!=null){window.setStatusBarColor(BG);window.setNavigationBarColor(BG);}
        shell.setOnApplyWindowInsetsListener((v,insets)->{
            int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
            if(Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}
            v.setPadding(dp(12),dp(8)+top,dp(12),dp(8)+bottom);return insets;
        });
        dialog.show();if(window!=null)window.setLayout(-1,-1);shell.requestApplyInsets();
    }
    @Override protected void onDestroy(){
        uiHandler.removeCallbacks(scanTimeout);
        if(expandedDialog!=null)expandedDialog.dismiss();
        if(choiceDialog!=null)choiceDialog.dismiss();
        for(android.app.Dialog sheet:new ArrayList<>(sheets))sheet.dismiss();
        super.onDestroy();
    }

    private final class DeviceAdapter extends BaseAdapter {
        @Override public int getCount(){return Math.max(1,visibleDevices().length());}
        @Override public Object getItem(int position){return visibleDevices().optJSONObject(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View convertView,ViewGroup parent){
            JSONObject d=visibleDevices().optJSONObject(position);if(d==null){
                TextView empty=text(deviceCategory>=3?"Bu filtreyle eşleşen yayın yok. Find My / iBeacon için firmware 1.0.0 gerekir. Eşleşme olmaması etiket bulunmadığını kanıtlamaz.":"Henüz gözlem alınmadı. İşlemler menüsünden taramayı yenileyin. Trafik yalnız mevcut kanalı gösterir.",12,MUTED,false);
                empty.setPadding(dp(16),dp(22),dp(16),dp(22));return empty;
            }
            LinearLayout shell=vertical();shell.setPadding(0,dp(4),0,dp(4));
            LinearLayout row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),dp(10),dp(10),dp(10));row.setBackground(round(PANEL,LINE,14));
            TextView icon=text("",21,ACCENT,true);Icons.apply(icon,showingTraffic?Icons.SIGNAL:showingWifi?Icons.WIFI:Icons.RADIO,21);
            icon.setGravity(Gravity.CENTER);row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(40)));
            LinearLayout copy=vertical();copy.setPadding(dp(10),0,dp(7),0);
            copy.addView(text(((position+1)+". "+identities.title(d,showingWifi||showingTraffic,activeTransport.equals("demo"))),14,TEXT,true));
            String meta=showingTraffic?"TX "+DeviceRules.bytes(d.optLong("txBytes"))+" · RX "+DeviceRules.bytes(d.optLong("rxBytes"))
                :showingWifi?"CH "+d.optInt("ch")+" · "+d.optString("security")+" · "+DeviceRules.address(d)
                :DeviceRules.address(d)+" · "+d.optInt("packets")+" reklam";
            TextView sub=text(meta,10,MUTED,false);sub.setPadding(0,dp(5),0,0);copy.addView(sub);
            copy.addView(text(identities.source(d,showingWifi||showingTraffic,activeTransport.equals("demo")),9,MUTED,false));
            if(!showingWifi && !showingTraffic && bleKind(d)!=BleSignals.UNKNOWN)copy.addView(text(BleSignals.label(bleKind(d)),10,AMBER,true));
            String match=DeviceRules.match(d,preferences.getString("watch_rules",""));
            if(!match.isEmpty())copy.addView(text(match,9,AMBER,false));
            row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            TextView signal=text(d.optBoolean("rssiKnown",true)?d.optInt("rssi",-127)+"\ndBm":"—",10,AMBER,true);
            signal.setGravity(Gravity.END);row.addView(signal);
            shell.addView(row,new LinearLayout.LayoutParams(-1,-2));return shell;
        }
    }
}
