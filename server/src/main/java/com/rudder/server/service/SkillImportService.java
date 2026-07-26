package com.rudder.server.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 URL 拉取 SKILL.md（GitHub / ClawHub / Skills.sh / 直链），防 SSRF。
 */
@Service
public class SkillImportService {

    private static final int MAX_BYTES = 512 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern GITHUB_BLOB = Pattern.compile(
            "^https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "^https?://github\\.com/([^/]+)/([^/#?]+)(?:/?|/tree/([^/#?]+))?/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLAWHUB = Pattern.compile(
            "^https?://(?:www\\.)?clawhub\\.ai/([^/]+)/([^/#?]+)/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILLS_SH = Pattern.compile(
            "^https?://(?:www\\.)?skills\\.sh/([^/]+)/([^/#?]+)/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_RAW_LINK = Pattern.compile(
            "href=[\"'](https?://raw\\.githubusercontent\\.com/[^\"']+/SKILL\\.md)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_GITHUB_BLOB = Pattern.compile(
            "href=[\"'](https?://github\\.com/[^\"']+/blob/[^\"']+/SKILL\\.md)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Imported(String name, String description, String content, String sourceUrl) {}

    public Imported fetch(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        String trimmed = rawUrl.trim();
        List<String> candidates = expandCandidates(trimmed);
        IOException lastIo = null;
        String lastHtml = null;
        for (String candidate : candidates) {
            try {
                URI uri = validatePublicHttpUri(candidate);
                String body = download(uri);
                if (looksLikeMarkdownSkill(body)) {
                    SkillMarkdown.Parsed p = SkillMarkdown.parse(body);
                    String name = StringUtils.hasText(p.name()) ? p.name() : guessName(uri, trimmed);
                    return new Imported(name, p.description(), body, trimmed);
                }
                if (looksLikeHtml(body)) {
                    lastHtml = body;
                    for (String fromHtml : extractLinksFromHtml(body)) {
                        try {
                            URI u = validatePublicHttpUri(normalizeGithubBlob(fromHtml));
                            String md = download(u);
                            if (looksLikeMarkdownSkill(md)) {
                                SkillMarkdown.Parsed p = SkillMarkdown.parse(md);
                                String name = StringUtils.hasText(p.name()) ? p.name() : guessName(u, trimmed);
                                return new Imported(name, p.description(), md, trimmed);
                            }
                        } catch (Exception ignored) {
                            // try next
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (IOException e) {
                lastIo = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalArgumentException("拉取被中断");
            }
        }
        if (lastHtml != null) {
            throw new IllegalArgumentException("未能从页面解析出 SKILL.md，请使用 raw 直链或 GitHub blob 链接");
        }
        if (lastIo != null) {
            throw new IllegalArgumentException("拉取失败: " + lastIo.getMessage());
        }
        throw new IllegalArgumentException("未找到有效的 SKILL.md 内容");
    }

    List<String> expandCandidates(String url) {
        Set<String> out = new LinkedHashSet<>();
        out.add(normalizeGithubBlob(url));

        Matcher claw = CLAWHUB.matcher(url);
        if (claw.matches()) {
            String owner = claw.group(1);
            String slug = claw.group(2);
            out.add("https://clawhub.ai/" + owner + "/" + slug + "/SKILL.md");
            out.add("https://raw.githubusercontent.com/" + owner + "/" + slug + "/main/SKILL.md");
            out.add("https://raw.githubusercontent.com/" + owner + "/" + slug + "/master/SKILL.md");
            out.add("https://github.com/" + owner + "/" + slug);
        }

        Matcher ssh = SKILLS_SH.matcher(url);
        if (ssh.matches()) {
            String owner = ssh.group(1);
            String repo = ssh.group(2);
            out.add("https://github.com/" + owner + "/" + repo);
            out.add("https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/SKILL.md");
            out.add("https://raw.githubusercontent.com/" + owner + "/" + repo + "/master/SKILL.md");
            out.add("https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/skills/SKILL.md");
        }

        Matcher repo = GITHUB_REPO.matcher(normalizeGithubBlob(url));
        if (repo.matches()) {
            String owner = repo.group(1);
            String name = repo.group(2).replaceAll("\\.git$", "");
            String branch = StringUtils.hasText(repo.group(3)) ? repo.group(3) : "main";
            String[] branches = branch.equals("main")
                    ? new String[]{"main", "master"}
                    : new String[]{branch, "main", "master"};
            String[] paths = {
                    "SKILL.md",
                    "skills/SKILL.md",
                    ".agents/skills/" + name + "/SKILL.md",
                    "skills/" + name + "/SKILL.md"
            };
            for (String b : branches) {
                for (String p : paths) {
                    out.add("https://raw.githubusercontent.com/" + owner + "/" + name + "/" + b + "/" + p);
                }
            }
        }
        return new ArrayList<>(out);
    }

    static String normalizeGithubBlob(String url) {
        Matcher m = GITHUB_BLOB.matcher(url.trim());
        if (m.matches()) {
            return "https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2)
                    + "/" + m.group(3) + "/" + m.group(4);
        }
        return url.trim();
    }

    URI validatePublicHttpUri(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效 URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("仅支持 http/https");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("URL 缺少主机名");
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.equals("metadata.google.internal")) {
            throw new IllegalArgumentException("不允许访问内网地址");
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException("不允许访问内网地址");
                }
                byte[] b = addr.getAddress();
                if (b.length == 4) {
                    int a0 = b[0] & 0xff;
                    int a1 = b[1] & 0xff;
                    // 100.64.0.0/10 CGNAT, 169.254.0.0/16 link-local already covered
                    if (a0 == 100 && a1 >= 64 && a1 <= 127) {
                        throw new IllegalArgumentException("不允许访问内网地址");
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析主机: " + host);
        }
        return uri;
    }

    private String download(URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "text/plain,text/markdown,text/html,*/*")
                .header("User-Agent", "RudderSkillImport/0.1")
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + uri);
        }
        byte[] bytes = resp.body();
        if (bytes == null || bytes.length == 0) {
            throw new IOException("空响应");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("内容超过 512KB 限制");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static boolean looksLikeMarkdownSkill(String body) {
        if (!StringUtils.hasText(body)) return false;
        String t = body.stripLeading();
        if (t.startsWith("<!DOCTYPE") || t.startsWith("<html") || t.startsWith("<HTML")) return false;
        return t.startsWith("---") || t.contains("# ") || t.toLowerCase(Locale.ROOT).contains("skill");
    }

    static boolean looksLikeHtml(String body) {
        String t = body.stripLeading().toLowerCase(Locale.ROOT);
        return t.startsWith("<!doctype") || t.startsWith("<html") || t.contains("<head");
    }

    static List<String> extractLinksFromHtml(String html) {
        Set<String> links = new LinkedHashSet<>();
        Matcher m1 = HTML_RAW_LINK.matcher(html);
        while (m1.find()) links.add(m1.group(1));
        Matcher m2 = HTML_GITHUB_BLOB.matcher(html);
        while (m2.find()) links.add(m2.group(1));
        return new ArrayList<>(links);
    }

    static String guessName(URI uri, String original) {
        String path = uri.getPath();
        if (StringUtils.hasText(path)) {
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String p = parts[i];
                if (!StringUtils.hasText(p) || p.equalsIgnoreCase("SKILL.md")) continue;
                return p;
            }
        }
        return "imported-skill";
    }
}
