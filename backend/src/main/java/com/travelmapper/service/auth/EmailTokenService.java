package com.travelmapper.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.repository.entity.EmailToken;
import com.travelmapper.repository.enums.EmailTokenType;
import com.travelmapper.repository.mapper.EmailTokenMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class EmailTokenService {

    private final EmailTokenMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public EmailTokenService(EmailTokenMapper mapper) {
        this.mapper = mapper;
    }

    public EmailToken create(Long userId, String email, EmailTokenType type, Duration ttl) {
        mapper.update(null, new LambdaUpdateWrapper<EmailToken>()
                .eq(EmailToken::getUserId, userId)
                .eq(EmailToken::getType, type)
                .eq(EmailToken::getUsed, false)
                .set(EmailToken::getUsed, true));

        EmailToken token = new EmailToken();
        token.setUserId(userId);
        token.setEmail(email);
        token.setToken(generate());
        token.setType(type);
        token.setExpireAt(LocalDateTime.now().plus(ttl));
        token.setUsed(false);
        token.setCreatedAt(LocalDateTime.now());
        mapper.insert(token);
        return token;
    }

    public EmailToken consume(String rawToken, EmailTokenType type) {
        EmailToken token = mapper.selectOne(new LambdaQueryWrapper<EmailToken>()
                .eq(EmailToken::getToken, rawToken));
        if (token == null
                || token.getType() != type
                || Boolean.TRUE.equals(token.getUsed())
                || token.getExpireAt() == null
                || token.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        token.setUsed(true);
        mapper.updateById(token);
        return token;
    }

    private String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
