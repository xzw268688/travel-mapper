package com.travelmapper.service.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmapper.common.crypto.AesCryptoService;
import com.travelmapper.repository.entity.SysConfig;
import com.travelmapper.repository.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigService {

    private final SysConfigMapper mapper;
    private final AesCryptoService crypto;

    public ConfigService(SysConfigMapper mapper, AesCryptoService crypto) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    public String get(String key) {
        SysConfig row = find(key);
        return row == null ? null : decode(row);
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public Map<String, String> getByPrefix(String prefix) {
        List<SysConfig> rows = mapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .likeRight(SysConfig::getConfigKey, prefix)
                .orderByAsc(SysConfig::getConfigKey));
        Map<String, String> result = new LinkedHashMap<>();
        for (SysConfig row : rows) {
            result.put(row.getConfigKey(), decode(row));
        }
        return result;
    }

    @Transactional
    public void updateGroup(Map<String, ConfigEntry> entries, Long updatedBy) {
        for (Map.Entry<String, ConfigEntry> item : entries.entrySet()) {
            String key = item.getKey();
            ConfigEntry entry = item.getValue();
            if (entry == null) {
                continue;
            }
            String stored = entry.encrypted() ? crypto.encrypt(entry.value()) : entry.value();
            SysConfig existing = find(key);
            if (existing == null) {
                SysConfig row = new SysConfig();
                row.setConfigKey(key);
                row.setConfigValue(stored);
                row.setEncrypted(entry.encrypted());
                row.setMasked(entry.masked());
                row.setRemark(entry.remark());
                row.setUpdatedBy(updatedBy);
                mapper.insert(row);
            } else {
                existing.setConfigValue(stored);
                existing.setEncrypted(entry.encrypted());
                existing.setMasked(entry.masked());
                if (entry.remark() != null) {
                    existing.setRemark(entry.remark());
                }
                existing.setUpdatedBy(updatedBy);
                mapper.updateById(existing);
            }
        }
    }

    public Map<String, ConfigEntry> snapshot(Set<String> keys) {
        Map<String, ConfigEntry> result = new LinkedHashMap<>();
        for (String key : keys) {
            SysConfig row = find(key);
            if (row != null) {
                result.put(key, new ConfigEntry(
                        decode(row),
                        Boolean.TRUE.equals(row.getEncrypted()),
                        Boolean.TRUE.equals(row.getMasked()),
                        row.getRemark()));
            }
        }
        return result;
    }

    public String decode(SysConfig row) {
        if (row == null) {
            return null;
        }
        if (Boolean.TRUE.equals(row.getEncrypted())) {
            return crypto.decrypt(row.getConfigValue());
        }
        return row.getConfigValue();
    }

    private SysConfig find(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
    }
}
