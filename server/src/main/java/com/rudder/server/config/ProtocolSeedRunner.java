package com.rudder.server.config;

import com.rudder.server.service.ProtocolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时为尚无协议目录的工作区补种子。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProtocolSeedRunner implements ApplicationRunner {

    private final ProtocolService protocolService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            protocolService.seedAllWorkspacesIfEmpty();
        } catch (Exception e) {
            log.warn("协议目录种子失败（表可能尚未创建）: {}", e.getMessage());
        }
    }
}
