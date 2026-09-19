package com.spectra.analyzer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class AnalyzerService extends Service implements BleTransport.Callback {
    public static final String ACTION_UPDATE = "com.spectra.analyzer.UPDATE";
    public static final String ACTION_SETTINGS = "com.spectra.analyzer.SETTINGS";
    public static final String ACTION_SCAN_SETTINGS = "com.spectra.analyzer.SCAN_SETTINGS";
    private boolean bleSettingsSent;
    public static final String ACTION_CONNECT = "com.spectra.analyzer.CONNECT";
    public static final String ACTION_DISCOVER = "com.spectra.analyzer.DISCOVER";
    public static final String ACTION_PROVISION = "com.spectra.analyzer.PROVISION";
    public static final String ACTION_CALIBRATE = "com.spectra.analyzer.CALIBRATE";
    public static final String ACTION_STOP = "com.spectra.analyzer.STOP";
    public static final String EXTRA_KIND = "kind";

    private static final String DIRECT_DEVICE_IP = "192.168.4.1";
    private static final String SIMULATOR_IP = "10.0.2.2";
    private static final int CONTROL_PORT = 4211;
    private static final int SPECTRUM_PORT = 4210;
    private static final int DISCOVERY_PORT = 4212;
    private static final String SERVICE_CHANNEL = "spectra_connection_v2";
    private static final String ALERT_CHANNEL = "spectra_alerts_v2";

    public static final String ACTION_WIFI = "com.spectra.analyzer.WIFI";
    public static final String ACTION_COMMAND = "com.spectra.analyzer.COMMAND";
    private volatile Network requestedWifi;
    private volatile ConnectivityManager.NetworkCallback wifiCallback;
    private final Object tcpWakeLock = new Object();
    private boolean tcpWakePending;
    private volatile long lastWifiSpectrumAt;
    private final StreamStats streamStats=new StreamStats();
    private final java.util.Map<String,JSONObject[]> inventoryPages = new java.util.HashMap<>();
    private final java.util.Map<String,Long> inventoryBatches = new java.util.HashMap<>();
    private final java.util.Map<String,Long> watchSeen = new java.util.HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile boolean tcpConnected;
    private volatile Socket controlSocket;
    private volatile PrintWriter controlWriter;
    private final java.util.concurrent.ThreadPoolExecutor commandWriter=new java.util.concurrent.ThreadPoolExecutor(
        1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),
        runnable->new Thread(runnable,"spectra-command-writer"));
    private WifiManager.WifiLock wifiLock;
    private SharedPreferences preferences;
    private BleTransport bleTransport;
    private int lastAlertSequence = -1;
    private long lastAlarmPlayedAt;

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("spectra_settings", MODE_PRIVATE);
        if (preferences.getBoolean("simulator_mode", false)
                && !preferences.contains("transport_mode")) {
            preferences.edit().putString("transport_mode", "demo").apply();
        }
        createNotificationChannels();
        startForeground(1001, connectionNotification(waitingText()));

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "spectra24:stream");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
        bleTransport = new BleTransport(this, this);
        running = true;
        new Thread(this::tcpLoop, "spectra-tcp").start();
        new Thread(this::udpLoop, "spectra-udp").start();
        if (wantsBle()) bleTransport.scan(true, preferences.getString("ble_address", ""));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        } else if (ACTION_WIFI.equals(action)) {
            requestDirectWifi();
        } else if (ACTION_COMMAND.equals(action)) {
            String command = intent.getStringExtra("command");
            if (command != null) sendCommand(command);
        } else if (ACTION_SCAN_SETTINGS.equals(action)) {
            int samples=intent.getIntExtra("samples",4);
            if(samples==4 || samples==8 || samples==12){
                preferences.edit().putInt("scan_samples",samples).putBoolean("scan_pending",true).apply();
                if(tcpConnected || (bleTransport!=null && bleTransport.isConnected()))
                    sendCommand("{\"action\":\"settings\",\"samples\":"+samples+"}");
            }
        } else if (ACTION_SETTINGS.equals(action)) {
            sendSettings(intent);
        } else if (ACTION_DISCOVER.equals(action)) {
            discoverDevices();
        } else if (ACTION_CONNECT.equals(action)) {
            changeConnection(intent);
        } else if (ACTION_PROVISION.equals(action)) {
            provisionWifi(intent.getStringExtra("ssid"), intent.getStringExtra("password"));
        } else if (ACTION_CALIBRATE.equals(action)) {
            sendCommand("{\"action\":\"calibrate\"}");
        } else if (wantsBle() && bleTransport != null && !bleTransport.isConnected()) {
            bleTransport.scan(true, preferences.getString("ble_address", ""));
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void requestDirectWifi() {
        if (Build.VERSION.SDK_INT < 29) {
            broadcast("error",new Intent().putExtra("message","Wi-Fi ayarlarında SPECTRA-24 / spectrum24 ağına bağlanın"));
            return;
        }
        Socket currentSocket=controlSocket;
        if (tcpConnected && currentSocket != null && currentSocket.getInetAddress() != null
                && DIRECT_DEVICE_IP.equals(currentSocket.getInetAddress().getHostAddress())) {
            broadcast("connection_hint",new Intent().putExtra("message","Hızlı Wi-Fi zaten bağlı; canlı akış korunuyor"));
            return;
        }
        if (wifiCallback != null) {
            wakeTcp();
            broadcast("connection_hint",new Intent().putExtra("message",requestedWifi == null
                ? "Wi-Fi isteği sürüyor; Android bağlantı onayını tamamlayın" : "Wi-Fi hazır; veri bağlantısı kuruluyor"));
            return;
        }
        preferences.edit().putString("transport_mode","auto").putBoolean("simulator_mode",false)
                .putString("device_ip",DIRECT_DEVICE_IP).apply();
        closeControlSocket();
        ConnectivityManager manager=getSystemService(ConnectivityManager.class);
        if (bleTransport != null) bleTransport.pauseDiscovery();
        wakeTcp();
        WifiNetworkSpecifier spec=new WifiNetworkSpecifier.Builder()
                .setSsid("SPECTRA-24").setWpa2Passphrase("spectrum24").build();
        NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build();
        wifiCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network) {
                if (wifiCallback != this || !running) return;
                requestedWifi=network; closeControlSocket(); wakeTcp();
                broadcast("connection_hint",new Intent().putExtra("message","SPECTRA Wi-Fi hazır; hızlı akışa geçiliyor"));
            }
            @Override public void onLost(Network network) {
                if (wifiCallback != this || !network.equals(requestedWifi) || !running) return;
                requestedWifi=null;
                closeControlSocket(); wakeTcp();
                mainHandler.post(()->{if(running && wantsBle() && bleTransport!=null)bleTransport.scan(true,preferences.getString("ble_address",""));});
                broadcast("connection_hint",new Intent().putExtra("message","Wi-Fi ayrıldı; BLE yedeği kullanılıyor"));
            }
            @Override public void onUnavailable() {
                if (wifiCallback != this || !running) return;
                mainHandler.post(()->{if(wifiCallback==this){releaseWifiRequest();if(running && wantsBle() && bleTransport!=null)bleTransport.scan(true,preferences.getString("ble_address",""));}});
                broadcast("error",new Intent().putExtra("message","Wi-Fi bağlantısı onaylanmadı. SPECTRA-24 şifresi: spectrum24"));
            }
        };
        try {
            manager.requestNetwork(request,wifiCallback,30000);
            android.util.Log.i("SpectraWifi","WIFI_REQUEST_REGISTERED");
            broadcast("connection_hint",new Intent().putExtra("message","Android Wi-Fi bağlantı onayı bekleniyor. SPECTRA-24 ağını seçin."));
        }
        catch(SecurityException e) {
            releaseWifiRequest();
            android.util.Log.e("SpectraWifi", "Network permission rejected", e);
            broadcast("error",new Intent().putExtra("message","Android bağlantı iznini reddetti. Uygulama izinlerinden Yakındaki cihazlar iznini kontrol edin; Wi-Fi ayarlarından da bağlanabilirsiniz."));
        } catch(RuntimeException e) {
            releaseWifiRequest();
            android.util.Log.e("SpectraWifi", "Network request failed", e);
            broadcast("error",new Intent().putExtra("message","Wi-Fi isteği başlatılamadı ("+e.getClass().getSimpleName()+"). Wi-Fi ayarlarından SPECTRA-24 ağına bağlanın."));
        }
    }
    private void releaseWifiRequest() {
        ConnectivityManager.NetworkCallback old=wifiCallback;
        wifiCallback=null; // Reject queued callbacks from a released request.
        if(old!=null) {
            try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(old); } catch(Exception ignored){}
        }
        requestedWifi=null;
    }

    private void changeConnection(Intent intent) {
        String mode = intent.getStringExtra("mode");
        if (mode == null) mode = "auto";
        String oldMode = transportMode();
        if ("ble".equals(mode) || "demo".equals(mode)) releaseWifiRequest();
        lastWifiSpectrumAt = 0;
        synchronized(streamStats){streamStats.reset();}
        SharedPreferences.Editor editor = preferences.edit().putString("transport_mode", mode)
                .putBoolean("simulator_mode", mode.equals("demo"));
        String ip = intent.getStringExtra("ip");
        String address = intent.getStringExtra("address");
        if (ip != null && !ip.isEmpty()) editor.putString("device_ip", ip);
        if (address != null && !address.isEmpty()) editor.putString("ble_address", address);
        editor.apply();
        closeControlSocket();
        if (bleTransport != null && !wantsBle()) bleTransport.stop();
        if (bleTransport != null && wantsBle()) {
            if (address != null && !address.isEmpty()) bleTransport.connect(address);
            else bleTransport.scan(true, preferences.getString("ble_address", ""));
        }
        broadcast("connection", new Intent().putExtra("connected", false)
                .putExtra("transport", mode).putExtra("message", "Bağlantı modu değiştirildi"));
        if (!oldMode.equals(mode)) updateForeground(waitingText());
    }

    private void discoverDevices() {
        broadcast("discovery_state", new Intent().putExtra("message", "Wi-Fi ve BLE taranıyor"));
        if (bleTransport != null) bleTransport.scan(wantsBle(), preferences.getString("ble_address", ""));
        new Thread(this::udpDiscovery, "spectra-discovery").start();
    }

    private void udpDiscovery() {
        byte[] query = "SP24_DISCOVER".getBytes(StandardCharsets.US_ASCII);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(3200);
            socket.send(new DatagramPacket(query, query.length,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
            socket.send(new DatagramPacket(query, query.length,
                    InetAddress.getByName(DIRECT_DEVICE_IP), DISCOVERY_PORT));
            long deadline = SystemClock.elapsedRealtime() + 3300;
            while (running && SystemClock.elapsedRealtime() < deadline) {
                byte[] response = new byte[512];
                DatagramPacket packet = new DatagramPacket(response, response.length);
                try {
                    socket.receive(packet);
                    String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    JSONObject object = new JSONObject(raw);
                    if (!"discovery".equals(object.optString("type"))) continue;
                    String ip = requestedWifi != null ? DIRECT_DEVICE_IP
                            : packet.getAddress().getHostAddress();
                    preferences.edit().putString("device_ip", ip).apply();
                    broadcast("spectra_device", new Intent().putExtra("name", object.optString("device", "SPECTRA-24"))
                            .putExtra("address", ip).putExtra("transport", "wifi")
                            .putExtra("rssi", 0).putExtra("json", raw));
                    closeControlSocket();
                } catch (SocketTimeoutException timeout) {
                    break;
                }
            }
        } catch (Exception ignored) {
            broadcast("discovery_state", new Intent().putExtra("message", "Wi-Fi keşfi tamamlandı"));
        }
    }

    private void provisionWifi(String ssid, String password) {
        if (ssid == null || ssid.trim().isEmpty()) {
            broadcast("error", new Intent().putExtra("message", "Hotspot adı boş olamaz"));
            return;
        }
        try {
            preferences.edit().putString("transport_mode","auto").putBoolean("simulator_mode",false).apply();
            JSONObject command = new JSONObject();
            command.put("action", "wifi");
            command.put("ssid", ssid.trim());
            command.put("password", password == null ? "" : password);
            if (bleTransport != null) {
                bleTransport.send(command.toString());
                if (!bleTransport.isConnected()) {
                    bleTransport.scan(true, preferences.getString("ble_address", ""));
                }
            }
            if (bleTransport == null || !bleTransport.isConnected()) sendLine(command.toString());
            broadcast("provision", new Intent().putExtra("message",
                    "Hotspot bilgisi ESP32’ye gönderiliyor"));
        } catch (Exception ignored) {}
    }

    private void tcpLoop() {
        while (running) {
            if (!wantsTcp()) { awaitTcpRetry(); continue; }
            String target = targetIp();
            try {
                Socket socket = new Socket();
                controlSocket = socket;
                Network network = networkForTarget(target);
                if (network != null) network.bindSocket(socket);
                if (DIRECT_DEVICE_IP.equals(target) && network == null && !isDemo()) {
                    throw new IllegalStateException("SPECTRA-24 Wi-Fi ağı bağlı değil");
                }
                socket.connect(new InetSocketAddress(target, CONTROL_PORT), 2600);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(5000);
                controlSocket = socket;
                controlWriter = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
                tcpConnected = true;
                sendLine("{\"action\":\"hello\"}");
                sendCurrentSettings();
                String transport = isDemo() ? "demo" : "wifi";
                broadcast("connection", new Intent().putExtra("connected", true)
                        .putExtra("transport", transport).putExtra("address", target)
                        .putExtra("message", isDemo() ? "PC demo verisi" : "Wi-Fi canlı veri"));
                updateForeground(isDemo() ? "DEMO bağlı · sentetik veri" : "Wi-Fi bağlı · canlı ölçüm");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                long lastMessage = SystemClock.elapsedRealtime();
                while (running && wantsTcp() && socket.isConnected() && !socket.isClosed()) {
                    try {
                        String line = reader.readLine();
                        if (line == null) break;
                        lastMessage = SystemClock.elapsedRealtime();
                        handleControlMessage(line, transport);
                    } catch (SocketTimeoutException timeout) {
                        if (SystemClock.elapsedRealtime()-lastMessage>12000) break;
                        sendLine("{\"action\":\"ping\"}");
                    }
                }
            } catch (Exception exception) {
                broadcast("connection_hint", new Intent().putExtra("message",
                        isDemo() ? "PC simülatörü bekleniyor" : "Wi-Fi üzerinden ESP32 aranıyor"));
            } finally {
                closeControlSocket();
                if (bleTransport == null || !bleTransport.isConnected()) {
                    broadcast("connection", new Intent().putExtra("connected", false)
                            .putExtra("transport", transportMode()));
                    updateForeground(waitingText());
                }
            }
            awaitTcpRetry();
        }
    }

    private void udpLoop() {
        byte[] buffer = new byte[256];
        while (running) {
            if (!tcpConnected || isDemo() || !wantsTcp()) { sleep(150); continue; }
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(SPECTRUM_PORT));
                Network network = networkForTarget(targetIp());
                if (network != null) network.bindSocket(socket);
                socket.setSoTimeout(2500);
                while (running && tcpConnected && wantsTcp()) {
                    DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(datagram);
                        if (datagram.getAddress().getHostAddress().equals(targetIp()))
                            decodeSpectrum(datagram.getData(), datagram.getLength(), "wifi");
                    } catch (SocketTimeoutException ignored) {
                        if (!tcpConnected) break;
                    }
                }
            } catch (Exception ignored) {
                sleep(600);
            }
        }
    }

    private void decodeSpectrum(byte[] packet, int length, String transport) {
        if ("demo".equals(transport) != isDemo()) return;
        if (!WireProtocol.validSpectrum(packet, length)) {synchronized(streamStats){streamStats.invalid++;}return;}
        if ("wifi".equals(transport)) lastWifiSpectrumAt = SystemClock.elapsedRealtime();
        long received,gaps,invalid;
        synchronized(streamStats){
            int sequence=(packet[6]&255)|((packet[7]&255)<<8);
            long uptime=(packet[8]&255L)|((packet[9]&255L)<<8)|((packet[10]&255L)<<16)|((packet[11]&255L)<<24);
            if(!streamStats.accept(sequence,uptime,transport))return;
            received=streamStats.received;gaps=streamStats.gaps;invalid=streamStats.invalid;
        }
        byte[] values = Arrays.copyOfRange(packet, 16, 142);
        Intent data = new Intent()
                .putExtra("values", values)
                .putExtra("flags", packet[5] & 0xff)
                .putExtra("sequence", (packet[6] & 0xff) | ((packet[7] & 0xff) << 8))
                .putExtra("average", packet[12] & 0xff)
                .putExtra("peak", packet[13] & 0xff)
                .putExtra("peakChannel", packet[14] & 0xff)
                .putExtra("affected", packet[15] & 0xff)
                .putExtra("transport", transport)
                .putExtra("live", !"demo".equals(transport));
        data.putExtra("receivedFrames",received).putExtra("sequenceGaps",gaps).putExtra("invalidFrames",invalid);
        broadcast("spectrum", data);
    }

    private synchronized void handleControlMessage(String line, String source) {
        if ("demo".equals(source) != isDemo()) return;
        try {
            JSONObject object = new JSONObject(line);
            String type = object.optString("type", "unknown");
            JSONObject savedSettings=object.optJSONObject("settings");
            if(type.equals("saved") && savedSettings!=null && savedSettings.optInt("samples",-1)==preferences.getInt("scan_samples",4))
                preferences.edit().putBoolean("scan_pending",false).apply();
            if (object.has("pages")) {
                int count=object.optInt("pages"),page=object.optInt("page");
                if(count<1||count>16||page<0||page>=count)return;
                String key=source+type;long batch=object.optLong("batch");
                if(!inventoryBatches.containsKey(key)||inventoryBatches.get(key)!=batch){
                    inventoryBatches.put(key,batch);inventoryPages.put(key,new JSONObject[count]);
                }
                JSONObject[] pages=inventoryPages.get(key);
                if(pages.length!=count)return;pages[page]=object;
                for(JSONObject p:pages)if(p==null)return;
                JSONArray combined=new JSONArray();String field=type.equals("wifi")?"networks":"devices";
                for(JSONObject p:pages){JSONArray items=p.optJSONArray(field);if(items!=null)for(int i=0;i<items.length();i++)combined.put(items.get(i));}
                object.put(field,combined);object.remove("pages");object.remove("page");
                line=object.toString();inventoryPages.remove(key);inventoryBatches.remove(key);
            }
            if (type.equals("spectrum")) {
                if ("demo".equals(source) && isDemo()) decodeSimulatorSpectrum(object);
                return;
            }
            if ((type.equals("status") || type.equals("hello"))
                    && "ble".equals(source) && !tcpConnected && requestedWifi == null && object.optBoolean("staConnected", false)) {
                String ip = object.optString("staIp");
                if (!ip.isEmpty() && !ip.equals(preferences.getString("device_ip", ""))) {
                    preferences.edit().putString("device_ip", ip).apply();
                    broadcast("spectra_device", new Intent().putExtra("name", "SPECTRA-24")
                            .putExtra("address", ip).putExtra("transport", "wifi").putExtra("rssi", 0));
                }
            }
            broadcast(type, new Intent().putExtra("json", line).putExtra("transport", source)
                    .putExtra("live", !"demo".equals(source)));
            if ((type.equals("wifi") || type.equals("ble") || type.equals("traffic")) && !"demo".equals(source)) {
                JSONArray items = object.optJSONArray(type.equals("wifi") ? "networks" : "devices");
                if (items != null) checkWatchRules(items);
            }
            if (type.equals("alert") && object.optBoolean("test",false)) {
                AlertTone.play(this,preferences.getString("tone","soft"),preferences.getInt("volume",70));
                if (preferences.getBoolean("vibrate",true)) AlertHaptics.play(this);
                getSystemService(NotificationManager.class).notify(2001,alertNotification("TEST · ESP32 bildirim zinciri çalışıyor"));
                return;
            }
            if (type.equals("alert") && object.optBoolean("active", false)) {
                int sequence = object.optInt("sequence", 0);
                if (sequence != lastAlertSequence) {
                    lastAlertSequence = sequence;
                    triggerAlarm(object, source);
                }
            }
        } catch (Exception ignored) {}
    }

    private void checkWatchRules(JSONArray items) {
        if (!preferences.getBoolean("watch_enabled",true)) return;
        long now=SystemClock.elapsedRealtime();
        String rules=preferences.getString("watch_rules","");
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i); if(item==null)continue;
            String match=DeviceRules.match(item,rules),key=DeviceRules.address(item)+match;
            if(!DeviceRules.shouldNotify(match))continue;
            Long last=watchSeen.get(key); if(last!=null && now-last<120000)continue;
            watchSeen.put(key,now);
            broadcast("watch",new Intent().putExtra("message",match+" · "+DeviceRules.address(item)));
            if(preferences.getBoolean("watch_sound",false)) {
                AlertTone.play(this,preferences.getString("tone","soft"),preferences.getInt("volume",70));
                if(preferences.getBoolean("vibrate",true)) AlertHaptics.play(this);
            }
            getSystemService(NotificationManager.class).notify(2002,alertNotification(match+" · "+DeviceRules.address(item)));
        }
        if(watchSeen.size()>500)watchSeen.entrySet().removeIf(e->now-e.getValue()>120000);
    }

    private void sendSettings(Intent intent) {
        int threshold = intent.getIntExtra("threshold", 30);
        int channels = intent.getIntExtra("channels", 28);
        int hold = intent.getIntExtra("hold", 4000);
        int cooldown = intent.getIntExtra("cooldown", 30000);
        boolean alerts = intent.getBooleanExtra("alerts", true);
        preferences.edit().putInt("threshold", threshold).putInt("channels", channels)
                .putInt("hold", hold).putInt("cooldown", cooldown).putBoolean("alerts", alerts)
                .putInt("volume", intent.getIntExtra("volume", 70))
                .putString("tone", intent.getStringExtra("tone"))
                .putBoolean("vibrate", intent.getBooleanExtra("vibrate", true)).apply();
        sendCurrentSettings();
    }

    private void sendCurrentSettings() {
        try {
            JSONObject command = new JSONObject();
            command.put("action", "settings");
            command.put("threshold", preferences.getInt("threshold", 30));
            command.put("channels", preferences.getInt("channels", 28));
            command.put("hold", preferences.getInt("hold", 4000));
            command.put("cooldown", preferences.getInt("cooldown", 30000));
            command.put("alerts", preferences.getBoolean("alerts", true));
            command.put("samples", preferences.getInt("scan_samples",4));
            sendCommand(command.toString());
        } catch (Exception ignored) {}
    }

    private void sendCommand(String command) {
        if (tcpConnected) sendLine(command);
        else if (bleTransport != null && bleTransport.isConnected()) bleTransport.send(command);
        else broadcast("error",new Intent().putExtra("message","Komut gönderilmedi: önce ESP32’ye bağlanın"));
    }

    private void sendLine(String line) {
        final PrintWriter writer=controlWriter;
        final Socket socket=controlSocket;
        if(writer==null || socket==null)return;
        try{commandWriter.execute(()->{
            // Commands queued for an old session must never reach a new device.
            if(!running || writer!=controlWriter || socket!=controlSocket)return;
            writer.println(line);
            if(writer.checkError()){
                broadcast("error",new Intent().putExtra("message","Wi-Fi komutu gönderilemedi; bağlantı yeniden denenecek"));
                try{socket.close();}catch(Exception ignored){}
            }
        });}catch(java.util.concurrent.RejectedExecutionException e){
            if(running)broadcast("error",new Intent().putExtra("message","Komut kuyruğu dolu; biraz bekleyip tekrar deneyin"));
        }
    }

    private Network networkForTarget(String target) {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (isDemo()) return manager.getActiveNetwork();
        if (requestedWifi != null && DIRECT_DEVICE_IP.equals(target)) return requestedWifi;
        if (!DIRECT_DEVICE_IP.equals(target)) return null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return network;
        }
        return null;
    }

    private String transportMode() {
        return preferences.getString("transport_mode", "auto");
    }
    private boolean isDemo() { return "demo".equals(transportMode()); }
    private boolean wantsTcp() { return !"ble".equals(transportMode()); }
    private boolean wantsBle() { return "auto".equals(transportMode()) || "ble".equals(transportMode()); }
    private String targetIp() {
        if (isDemo()) return SIMULATOR_IP;
        return preferences.getString("device_ip", DIRECT_DEVICE_IP);
    }
    private String waitingText() {
        if (isDemo()) return "DEMO veri kaynağı bekleniyor";
        if ("ble".equals(transportMode())) return "Yakındaki ESP32 BLE ile aranıyor";
        return "ESP32 Wi-Fi/BLE üzerinden aranıyor";
    }

    private void decodeSimulatorSpectrum(JSONObject object) {
        JSONArray samples = object.optJSONArray("values");
        if (samples == null || samples.length() != 126) return;
        byte[] values = new byte[126];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte) Math.max(0, Math.min(100, samples.optInt(i)));
        }
        broadcast("spectrum", new Intent().putExtra("values", values)
                .putExtra("flags", object.optInt("flags") & ~4)
                .putExtra("sequence", object.optInt("sequence"))
                .putExtra("average", object.optInt("average"))
                .putExtra("peak", object.optInt("peak"))
                .putExtra("peakChannel", object.optInt("peakChannel"))
                .putExtra("affected", object.optInt("affected"))
                .putExtra("transport", "demo").putExtra("live", false));
    }

    private void triggerAlarm(JSONObject data, String source) {
        if ("demo".equals(source) || !preferences.getBoolean("alerts", true)) return;
        long now = SystemClock.elapsedRealtime();
        int cooldown = preferences.getInt("cooldown", 30000);
        if (lastAlarmPlayedAt != 0 && now - lastAlarmPlayedAt < cooldown) return;
        lastAlarmPlayedAt = now;
        String tone = preferences.getString("tone", "soft");
        AlertTone.play(this, tone == null ? "soft" : tone, preferences.getInt("volume", 70));
        if (preferences.getBoolean("vibrate", true)) AlertHaptics.play(this);
        String text = "Geniş bant RF değişimi · anomali puanı " + data.optInt("confidence")
                + " · " + data.optInt("affected") + " kanal";
        getSystemService(NotificationManager.class).notify(2001, alertNotification(text));
    }


    @Override public void onBleState(String state, boolean connected) {
        if(!connected)bleSettingsSent=false;
        broadcast("ble_state", new Intent().putExtra("message", state).putExtra("connected", connected));
        if (connected && bleTransport != null && bleTransport.isConnected()) {
            preferences.edit().putBoolean("simulator_mode", false).apply();
            if (!tcpConnected || "ble".equals(transportMode())) {
                if(!bleSettingsSent){bleSettingsSent=true;sendCurrentSettings();}
                broadcast("connection", new Intent().putExtra("connected", true)
                        .putExtra("transport", "ble").putExtra("message", state));
                updateForeground("Bluetooth LE bağlı · canlı ölçüm");
            }
        } else if (!tcpConnected) {
            broadcast("connection", new Intent().putExtra("connected", false)
                    .putExtra("transport", "ble").putExtra("message", state));
            updateForeground(waitingText());
        }
    }

    @Override public void onBleDevice(String name, String address, int rssi) {
        preferences.edit().putString("ble_address", address).apply();
        broadcast("spectra_device", new Intent().putExtra("name", name).putExtra("address", address)
                .putExtra("transport", "ble").putExtra("rssi", rssi));
    }

    @Override public void onBleJson(String json) {
        if (tcpConnected && !"ble".equals(transportMode())) {
            try {
                String type = new JSONObject(json).optString("type");
                if (!type.equals("status") && !type.equals("hello")) return;
            } catch (Exception ignored) { return; }
        }
        handleControlMessage(json, "ble");
    }
    @Override public void onBleSpectrum(byte[] packet) {
        if (!isDemo() && (!tcpConnected || "ble".equals(transportMode()) || SystemClock.elapsedRealtime()-lastWifiSpectrumAt>2500))
            decodeSpectrum(packet, packet.length, "ble");
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel service = new NotificationChannel(
                SERVICE_CHANNEL, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW);
        service.setDescription("ESP32 Wi-Fi/BLE canlı ölçüm bağlantısı");
        service.setSound(null, null);
        manager.createNotificationChannel(service);
        NotificationChannel alerts = new NotificationChannel(
                ALERT_CHANNEL, getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Kalibre edilmiş geniş bant RF değişimi uyarıları");
        alerts.setSound(null, null);
        alerts.enableVibration(false);
        alerts.setLightColor(Color.rgb(255, 107, 122));
        alerts.enableLights(true);
        manager.createNotificationChannel(alerts);
    }

    private Notification connectionNotification(String text) {
        Intent stop = new Intent(this, AnalyzerService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 25, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, SERVICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle("SPECTRA 24")
                .setContentText(text).setOngoing(true).setContentIntent(mainPendingIntent())
                .addAction(new Notification.Action.Builder(null, "Durdur", stopIntent).build()).build();
    }

    private Notification alertNotification(String text) {
        return new Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle("RF alan değişimi")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text
                        + "\nAdaptif tabana göre sınıflandırılmıştır; kesin jammer teşhisi değildir."))
                .setAutoCancel(true).setContentIntent(mainPendingIntent()).build();
    }

    private PendingIntent mainPendingIntent() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateForeground(String text) {
        if (!running) return;
        getSystemService(NotificationManager.class).notify(1001, connectionNotification(text));
    }

    private void broadcast(String kind, Intent extras) {
        if (EventJournal.record(this,kind,extras,isDemo()))
            sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra(EXTRA_KIND,"journal"));
        extras.setAction(ACTION_UPDATE);
        extras.setPackage(getPackageName());
        extras.putExtra(EXTRA_KIND, kind);
        sendBroadcast(extras);
    }

    private synchronized void closeControlSocket() {
        tcpConnected = false;
        controlWriter = null;
        try { if (controlSocket != null) controlSocket.close(); } catch (Exception ignored) {}
        controlSocket = null;
    }

    private void wakeTcp() {
        synchronized(tcpWakeLock){tcpWakePending=true;tcpWakeLock.notifyAll();}
    }
    private void awaitTcpRetry() {
        synchronized(tcpWakeLock) {
            if(!tcpWakePending && running)try{tcpWakeLock.wait(300);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            tcpWakePending=false;
        }
    }

    private void sleep(long milliseconds) {
        try { Thread.sleep(milliseconds); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private void shutdown() {
        running = false;
        wakeTcp();
        releaseWifiRequest();
        AlertTone.stop();
        getSystemService(NotificationManager.class).cancel(2001);
        getSystemService(NotificationManager.class).cancel(2002);
        closeControlSocket();
        commandWriter.shutdownNow();
        if (bleTransport != null) bleTransport.stop();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        shutdown();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        running = false;
        releaseWifiRequest();
        AlertTone.stop();
        closeControlSocket();
        if (bleTransport != null) bleTransport.stop();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        super.onDestroy();
    }
}
