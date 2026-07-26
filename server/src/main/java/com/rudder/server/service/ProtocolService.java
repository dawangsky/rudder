package com.rudder.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudder.server.auth.AuthPrincipal;
import com.rudder.server.domain.WorkspaceEntity;
import com.rudder.server.domain.WorkspaceProtocolEntity;
import com.rudder.server.mapper.WorkspaceMapper;
import com.rudder.server.mapper.WorkspaceProtocolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 工作区运行时协议目录：种子、CRUD、白名单校验。 */
@Service
@RequiredArgsConstructor
public class ProtocolService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WorkspaceProtocolMapper protocolMapper;
    private final WorkspaceMapper workspaceMapper;

    /** workspaceId -> codes sorted by length desc（含未启用，用于解析 custom_） */
    private final ConcurrentHashMap<Long, List<String>> codeCache = new ConcurrentHashMap<>();

    /** 为工作区写入内置协议种子（已存在则跳过）。 */
    @Transactional
    public void seedWorkspace(Long workspaceId) {
        if (workspaceId == null) return;
        Long cnt = protocolMapper.selectCount(new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                .eq(WorkspaceProtocolEntity::getWorkspaceId, workspaceId));
        if (cnt != null && cnt > 0) return;
        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (BuiltinProtocols.Spec spec : BuiltinProtocols.ALL) {
            WorkspaceProtocolEntity e = new WorkspaceProtocolEntity();
            e.setWorkspaceId(workspaceId);
            e.setCode(spec.code());
            e.setLabel(spec.label());
            e.setShortLabel(spec.shortLabel());
            e.setBinsJson(toBinsJson(spec.bins()));
            e.setCommandHint(spec.commandHint());
            e.setRegion(spec.region());
            e.setEnabled(1);
            e.setBuiltin(1);
            e.setSortOrder(order++);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            protocolMapper.insert(e);
        }
        invalidate(workspaceId);
    }

    /** 为所有尚无协议行的工作区补种子（迁移后调用 / 启动时）。 */
    @Transactional
    public void seedAllWorkspacesIfEmpty() {
        List<WorkspaceEntity> all = workspaceMapper.selectList(null);
        for (WorkspaceEntity w : all) {
            seedWorkspace(w.getId());
        }
    }

    public List<Map<String, Object>> listProtocols(AuthPrincipal p, boolean enabledOnly) {
        requireWorkspace(p);
        ensureSeeded(p.workspaceId());
        LambdaQueryWrapper<WorkspaceProtocolEntity> q = new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                .eq(WorkspaceProtocolEntity::getWorkspaceId, p.workspaceId())
                .orderByAsc(WorkspaceProtocolEntity::getSortOrder)
                .orderByAsc(WorkspaceProtocolEntity::getId);
        if (enabledOnly) {
            q.eq(WorkspaceProtocolEntity::getEnabled, 1);
        }
        return protocolMapper.selectList(q).stream().map(this::view).collect(Collectors.toList());
    }

    /** Daemon：返回已启用协议的 code + bins。 */
    public List<Map<String, Object>> listEnabledForDaemon(AuthPrincipal daemon) {
        if (daemon.workspaceId() == null) {
            throw new IllegalArgumentException("Daemon 未绑定工作区");
        }
        ensureSeeded(daemon.workspaceId());
        return protocolMapper.selectList(new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                        .eq(WorkspaceProtocolEntity::getWorkspaceId, daemon.workspaceId())
                        .eq(WorkspaceProtocolEntity::getEnabled, 1)
                        .orderByAsc(WorkspaceProtocolEntity::getSortOrder))
                .stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("code", e.getCode());
                    m.put("bins", parseBins(e.getBinsJson()));
                    m.put("label", e.getLabel());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createProtocol(AuthPrincipal p, Map<String, Object> body) {
        requireWorkspace(p);
        ensureSeeded(p.workspaceId());
        String code = normalizeCode(str(body.get("code")));
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("协议标识须为小写字母开头，仅含 a-z / 0-9 / _，长度 2–63");
        }
        if ("custom".equals(code)) {
            throw new IllegalArgumentException("保留标识不可用: custom");
        }
        WorkspaceProtocolEntity exists = find(p.workspaceId(), code);
        if (exists != null) {
            throw new IllegalArgumentException("协议已存在: " + code);
        }
        String label = require(body.get("label"), "名称不能为空");
        if (label.length() > 128) throw new IllegalArgumentException("名称过长");
        String shortLabel = str(body.get("short"));
        if (!StringUtils.hasText(shortLabel)) shortLabel = label.length() > 16 ? label.substring(0, 16) : label;
        List<String> bins = parseBinsInput(body.get("bins"));
        String hint = str(body.get("commandHint"));
        String region = normalizeRegion(str(body.get("region")));
        LocalDateTime now = LocalDateTime.now();
        WorkspaceProtocolEntity e = new WorkspaceProtocolEntity();
        e.setWorkspaceId(p.workspaceId());
        e.setCode(code);
        e.setLabel(label);
        e.setShortLabel(shortLabel);
        e.setBinsJson(toBinsJson(bins));
        e.setCommandHint(hint);
        e.setRegion(region);
        e.setEnabled(body.get("enabled") == null || truthy(body.get("enabled")) ? 1 : 0);
        e.setBuiltin(0);
        e.setSortOrder(nextSortOrder(p.workspaceId()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        protocolMapper.insert(e);
        invalidate(p.workspaceId());
        return view(e);
    }

    @Transactional
    public Map<String, Object> updateProtocol(AuthPrincipal p, String codeRaw, Map<String, Object> body) {
        requireWorkspace(p);
        ensureSeeded(p.workspaceId());
        String code = normalizeCode(codeRaw);
        WorkspaceProtocolEntity e = find(p.workspaceId(), code);
        if (e == null) throw new IllegalArgumentException("协议不存在: " + code);
        if (body.containsKey("label")) {
            String label = require(body.get("label"), "名称不能为空");
            if (label.length() > 128) throw new IllegalArgumentException("名称过长");
            e.setLabel(label);
        }
        if (body.containsKey("short")) {
            String s = str(body.get("short"));
            e.setShortLabel(StringUtils.hasText(s) ? s : e.getLabel());
        }
        if (body.containsKey("bins")) {
            e.setBinsJson(toBinsJson(parseBinsInput(body.get("bins"))));
        }
        if (body.containsKey("commandHint")) {
            e.setCommandHint(str(body.get("commandHint")));
        }
        if (body.containsKey("region")) {
            e.setRegion(normalizeRegion(str(body.get("region"))));
        }
        if (body.containsKey("enabled")) {
            e.setEnabled(truthy(body.get("enabled")) ? 1 : 0);
        }
        if (body.containsKey("sortOrder") && body.get("sortOrder") instanceof Number n) {
            e.setSortOrder(n.intValue());
        }
        e.setUpdatedAt(LocalDateTime.now());
        protocolMapper.updateById(e);
        invalidate(p.workspaceId());
        return view(e);
    }

    @Transactional
    public void deleteProtocol(AuthPrincipal p, String codeRaw) {
        requireWorkspace(p);
        String code = normalizeCode(codeRaw);
        WorkspaceProtocolEntity e = find(p.workspaceId(), code);
        if (e == null) throw new IllegalArgumentException("协议不存在: " + code);
        if (e.getBuiltin() != null && e.getBuiltin() == 1) {
            throw new IllegalArgumentException("内置协议不可删除，请改用停用");
        }
        protocolMapper.deleteById(e.getId());
        invalidate(p.workspaceId());
    }

    /** 校验 provider（含 custom_<base>_<hash>）是否允许在该工作区使用。 */
    public boolean isAllowedProvider(Long workspaceId, String provider) {
        if (workspaceId == null || provider == null) return false;
        ensureSeeded(workspaceId);
        String p = provider.toLowerCase(Locale.ROOT).trim();
        if (!StringUtils.hasText(p)) return false;
        String base = resolveBase(workspaceId, p);
        if (!StringUtils.hasText(base) || base.startsWith("custom_")) return false;
        WorkspaceProtocolEntity e = find(workspaceId, base);
        return e != null && e.getEnabled() != null && e.getEnabled() == 1;
    }

    /** 解析基础协议 code。 */
    public String baseProvider(Long workspaceId, String provider) {
        if (provider == null) return "";
        String p = provider.toLowerCase(Locale.ROOT).trim();
        if (workspaceId == null) {
            return WorkdirResolver.baseProviderStatic(p);
        }
        ensureSeeded(workspaceId);
        return resolveBase(workspaceId, p);
    }

    private String resolveBase(Long workspaceId, String p) {
        if (!p.startsWith("custom_")) return p;
        String rest = p.substring("custom_".length());
        for (String base : codesByLen(workspaceId)) {
            if (rest.startsWith(base + "_")) return base;
        }
        return p;
    }

    private void ensureSeeded(Long workspaceId) {
        Long cnt = protocolMapper.selectCount(new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                .eq(WorkspaceProtocolEntity::getWorkspaceId, workspaceId));
        if (cnt == null || cnt == 0) {
            seedWorkspace(workspaceId);
        }
    }

    private List<String> codesByLen(Long workspaceId) {
        return codeCache.computeIfAbsent(workspaceId, id -> {
            List<WorkspaceProtocolEntity> rows = protocolMapper.selectList(
                    new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                            .eq(WorkspaceProtocolEntity::getWorkspaceId, id)
                            .select(WorkspaceProtocolEntity::getCode));
            return rows.stream()
                    .map(WorkspaceProtocolEntity::getCode)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(s -> s))
                    .collect(Collectors.toList());
        });
    }

    private void invalidate(Long workspaceId) {
        if (workspaceId != null) codeCache.remove(workspaceId);
    }

    private WorkspaceProtocolEntity find(Long workspaceId, String code) {
        return protocolMapper.selectOne(new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                .eq(WorkspaceProtocolEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceProtocolEntity::getCode, code)
                .last("LIMIT 1"));
    }

    private int nextSortOrder(Long workspaceId) {
        WorkspaceProtocolEntity last = protocolMapper.selectOne(new LambdaQueryWrapper<WorkspaceProtocolEntity>()
                .eq(WorkspaceProtocolEntity::getWorkspaceId, workspaceId)
                .orderByDesc(WorkspaceProtocolEntity::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private Map<String, Object> view(WorkspaceProtocolEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(e.getId()));
        m.put("code", e.getCode());
        m.put("value", e.getCode());
        m.put("label", e.getLabel());
        m.put("short", StringUtils.hasText(e.getShortLabel()) ? e.getShortLabel() : e.getLabel());
        m.put("bins", parseBins(e.getBinsJson()));
        m.put("commandHint", e.getCommandHint());
        m.put("region", e.getRegion());
        m.put("enabled", e.getEnabled() != null && e.getEnabled() == 1);
        m.put("builtin", e.getBuiltin() != null && e.getBuiltin() == 1);
        m.put("sortOrder", e.getSortOrder());
        m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
        return m;
    }

    private static void requireWorkspace(AuthPrincipal p) {
        if (p == null || p.workspaceId() == null) {
            throw new IllegalArgumentException("尚未加入工作区");
        }
    }

    private static String normalizeCode(String raw) {
        return str(raw).toLowerCase(Locale.ROOT);
    }

    private static String normalizeRegion(String raw) {
        String r = str(raw).toLowerCase(Locale.ROOT);
        if ("cn".equals(r) || "test".equals(r) || "intl".equals(r)) return r;
        return "intl";
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseBinsInput(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                String s = str(o);
                if (StringUtils.hasText(s)) out.add(s);
            }
            return out;
        }
        String s = str(raw);
        if (!StringUtils.hasText(s)) return List.of();
        if (s.startsWith("[")) return parseBins(s);
        // 逗号或空格分隔
        String[] parts = s.split("[,\\s]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (StringUtils.hasText(p)) out.add(p.trim());
        }
        return out;
    }

    private static List<String> parseBins(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String toBinsJson(List<String> bins) {
        try {
            return JSON.writeValueAsString(bins == null ? List.of() : bins);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = str(v).toLowerCase(Locale.ROOT);
        return "1".equals(s) || "true".equals(s) || "yes".equals(s);
    }

    private static String require(Object v, String msg) {
        String s = str(v);
        if (!StringUtils.hasText(s)) throw new IllegalArgumentException(msg);
        return s;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
