package com.secuaudit.controller;

import com.secuaudit.core.CryptoEngine;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 区块链状态接口
 */
@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private final CryptoEngine engine;

    public BlockchainController(CryptoEngine engine) {
        this.engine = engine;
    }

    /**
     * 获取区块链状态
     */
    @GetMapping
    public Map<String, Object> state() {
        return engine.getBlockchainState();
    }
}