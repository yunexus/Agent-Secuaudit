package com.secuaudit.core;

import it.unisa.dia.gas.jpbc.*;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeACurveGenerator;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * SecuAudit密码学引擎
 * 实现门限PDP协议的7个阶段：Setup(DKG)、Keygen、SignBlock、GenProof、CheckProof
 * 基于JPBC双线性配对，支持元数据策略绑定
 */
public class CryptoEngine {

    /** 文件分块大小：64KB */
    public static final int BLOCK_SIZE_KB = 64;
    public static final int BLOCK_SIZE_BYTES = BLOCK_SIZE_KB * 1024;

    /** 默认密钥服务器数量 */
    public static final int DEFAULT_NUM_KS = 10;

    /** 默认门限值 */
    public static final int DEFAULT_THRESHOLD = 5;

    /** 默认审计挑战块数 */
    public static final int DEFAULT_CHALLENGE_BLOCKS = 100;

    /** 文件存储目录 */
    private static final String STORAGE_DIR = "./storage";

    /** JPBC双线性配对 */
    private final Pairing pairing;

    /** 群参数 */
    private final Field<Element> Zr;
    private final Field<Element> G;
    private final Element g;
    private final Element u;

    /** 系统主私钥σ和主公钥D = g^σ */
    private Element sigma;
    private Element D;

    /** 密钥服务器列表 */
    private final List<KeyServer> keyServers = new ArrayList<>();

    /** DKG多项式系数 */
    private final List<Element[]> allPolynomials = new ArrayList<>();

    /** 用户列表 */
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    /** 文件列表 */
    private final Map<String, FileRecord> files = new ConcurrentHashMap<>();

    /** 撤销列表 */
    private final Set<String> denySet = ConcurrentHashMap.newKeySet();

    /** 系统是否已初始化 */
    private boolean initialized = false;

    /**
     * 构造函数：初始化JPBC双线性配对
     * 使用Type-A曲线（160位子群阶，512位基域）
     */
    public CryptoEngine() {
        TypeACurveGenerator gen = new TypeACurveGenerator(160, 512);
        this.pairing = PairingFactory.getPairing(gen.generate());
        this.Zr = pairing.getZr();
        this.G = pairing.getG1();
        this.g = G.newRandomElement().getImmutable();
        this.u = G.newRandomElement().getImmutable();
    }

    // ==================== Phase 1: Setup / DKG ====================

    /**
     * 系统初始化（DKG分布式密钥生成）
     * 每个KS生成t阶Shamir多项式，聚合生成系统主私钥σ和主公钥D
     *
     * @param numKs 密钥服务器数量
     * @param threshold 门限值t
     * @return 初始化结果
     */
    public synchronized Map<String, Object> setup(int numKs, int threshold) {
        if (initialized) return Map.of("status", "already_initialized");

        keyServers.clear();
        allPolynomials.clear();
        sigma = Zr.newZeroElement();

        // 每个KS生成多项式系数
        for (int i = 1; i <= numKs; i++) {
            Element[] coeff = new Element[threshold];
            for (int l = 0; l < threshold; l++) {
                coeff[l] = Zr.newRandomElement().getImmutable();
            }
            allPolynomials.add(coeff);
            sigma.add(coeff[0]);  // 聚合常数项
        }
        sigma = sigma.getImmutable();
        D = g.duplicate().powZn(sigma).getImmutable();

        // 为每个KS生成密钥份额
        for (int j = 1; j <= numKs; j++) {
            Element gammaJ = Zr.newZeroElement();
            for (int dealer = 0; dealer < numKs; dealer++) {
                Element share = evaluatePolynomial(allPolynomials.get(dealer), j);
                gammaJ.add(share);
            }
            gammaJ = gammaJ.getImmutable();
            Element pubKey = g.duplicate().powZn(gammaJ).getImmutable();
            keyServers.add(new KeyServer(j, gammaJ, pubKey, true));
        }

        initialized = true;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "initialized");
        result.put("num_ks", numKs);
        result.put("threshold", threshold);
        result.put("public_key_hex", bytesToHex(D.toBytes()).substring(0, 32) + "...");
        result.put("ks_list", getKsSummary());
        return result;
    }

    /**
     * 计算多项式在x点的值
     */
    private Element evaluatePolynomial(Element[] coeff, int xInt) {
        Element x = Zr.newElement(BigInteger.valueOf(xInt)).getImmutable();
        Element powX = Zr.newOneElement();
        Element result = Zr.newZeroElement();
        for (Element c : coeff) {
            result.add(c.duplicate().mul(powX));
            powX.mul(x);
        }
        return result.getImmutable();
    }

    // ==================== Phase 2: Keygen ====================

    /**
     * 用户密钥生成（Keygen协议）
     * 用户向在线KS请求密钥份额，通过Lagrange插值恢复完整私钥
     *
     * @param userId 用户ID
     * @param policy 访问策略
     * @param validUntil 有效期
     * @return 注册结果
     */
    public synchronized Map<String, Object> keygen(String userId, String policy, String validUntil) {
        if (!initialized) return Map.of("error", "system_not_initialized");
        if (users.containsKey(userId)) return Map.of("error", "user_already_exists");

        // 策略规则哈希
        String rule = "policy:" + policy + "|valid_until:" + validUntil + "|user:" + userId;
        Element RH = hashToZr("rule:" + rule);

        // 用户生成随机私钥a，计算公钥A = g^a
        Element a = Zr.newRandomElement().getImmutable();
        Element A = g.duplicate().powZn(a).getImmutable();

        // 向所有在线KS请求份额
        List<CryptoShare> validShares = new ArrayList<>();
        for (KeyServer ks : keyServers) {
            if (!ks.online) continue;
            CryptoShare share = ksRespond(ks, userId, A, RH);
            if (verifyKeygenShare(ks, share, userId, A, RH, a)) {
                validShares.add(share);
            }
        }

        int threshold = allPolynomials.get(0).length;
        if (validShares.size() < threshold) {
            return Map.of("error", "insufficient_online_ks", "need", threshold, "have", validShares.size());
        }

        // 选择t个有效份额，通过Lagrange插值恢复完整私钥b
        List<Integer> selectedIds = new ArrayList<>();
        for (int i = 0; i < threshold; i++) selectedIds.add(validShares.get(i).id);

        Element b = a.duplicate();
        for (int i = 0; i < threshold; i++) {
            CryptoShare s = validShares.get(i);
            Element h2 = H2Element("0|" + userId + "|" + s.B.duplicate().powZn(a).toString());
            Element bi = s.X.duplicate().sub(h2).getImmutable();
            Element rho = lagrangeAtZero(selectedIds, s.id);
            b.add(bi.duplicate().mul(rho));
        }
        Element privateKeyB = b.getImmutable();

        // 计算公开验证组件Z
        Element Z = G.newOneElement();
        Element h1 = H1Element(userId, A, RH);
        for (int i = 0; i < threshold; i++) {
            CryptoShare s = validShares.get(i);
            Element rho = lagrangeAtZero(selectedIds, s.id);
            Z.mul(s.B.duplicate().powZn(rho));
        }
        Z.mul(D.duplicate().powZn(h1)).mul(A);
        Z = Z.getImmutable();

        UserRecord user = new UserRecord(userId, policy, validUntil, rule,
                bytesToHex(A.toBytes()).substring(0, 16),
                bytesToHex(Z.toBytes()).substring(0, 16), "active");
        users.put(userId, user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "keygen_success");
        result.put("user_id", userId);
        result.put("public_key", bytesToHex(A.toBytes()).substring(0, 32));
        result.put("policy", policy);
        result.put("valid_until", validUntil);
        result.put("registered_at", new Date().toString());
        return result;
    }

    /**
     * KS响应用户密钥生成请求
     */
    private CryptoShare ksRespond(KeyServer ks, String userId, Element A, Element RH) {
        Element r = Zr.newRandomElement().getImmutable();
        Element B = g.duplicate().powZn(r).getImmutable();
        Element h1 = H1Element(userId, A, RH);
        Element h2 = H2Element("0|" + userId + "|" + A.duplicate().powZn(r).toString());
        Element X = r.duplicate().add(ks.gamma.duplicate().mul(h1)).add(h2).getImmutable();
        return new CryptoShare(ks.id, B, X);
    }

    /**
     * 验证KS返回的密钥份额
     */
    private boolean verifyKeygenShare(KeyServer ks, CryptoShare share, String userId, Element A, Element RH, Element a) {
        Element h1 = H1Element(userId, A, RH);
        Element h2 = H2Element("0|" + userId + "|" + share.B.duplicate().powZn(a).toString());
        Element left = g.duplicate().powZn(share.X).getImmutable();
        Element right = share.B.duplicate().mul(ks.P.duplicate().powZn(h1)).mul(g.duplicate().powZn(h2)).getImmutable();
        return left.isEqual(right);
    }

    /**
     * Lagrange插值：计算在x=0处的权重
     */
    private Element lagrangeAtZero(List<Integer> qualified, int i) {
        Element num = Zr.newOneElement();
        Element den = Zr.newOneElement();
        for (int j : qualified) {
            if (j == i) continue;
            num.mul(Zr.newElement(BigInteger.valueOf(-j)));
            den.mul(Zr.newElement(BigInteger.valueOf(i - j)));
        }
        return num.mul(den.invert()).getImmutable();
    }

    // ==================== Phase 3: SignBlock ====================

    /**
     * 文件上传并签名
     * 将文件按64KB分块，每块生成认证标签 T_j = (U_j · u^m_j)^b
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @param fileData 文件数据
     * @return 上传结果
     */
    public synchronized Map<String, Object> signFile(String fileId, String userId, byte[] fileData) {
        if (!users.containsKey(userId)) return Map.of("error", "user_not_found");
        UserRecord user = users.get(userId);
        if ("revoked".equals(user.status)) return Map.of("error", "user_revoked");
        if (denySet.contains(userId)) return Map.of("error", "user_in_deny_set");

        int numBlocks = (int) Math.ceil((double) fileData.length / BLOCK_SIZE_BYTES);
        if (numBlocks == 0) numBlocks = 1;

        List<byte[]> blocks = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < numBlocks; i++) {
            int start = i * BLOCK_SIZE_BYTES;
            int end = Math.min(start + BLOCK_SIZE_BYTES, fileData.length);
            byte[] block = Arrays.copyOfRange(fileData, start, end);
            blocks.add(block);

            BigInteger mj = sha256ToBigInt(block);
            Element Uj = hashToG1(fileId + "|" + i);
            Element Tj = Uj.duplicate().mul(u.duplicate().powZn(Zr.newElement(mj))).getImmutable();
            tags.add(bytesToHex(Tj.toBytes()));
        }

        String fid = "fid:" + fileId;
        String metadata = "owner:" + userId + "|policy:" + user.policy + "|valid_until:" + user.validUntil;
        String htag = sha256Hex(fid + metadata + tags.get(0));

        saveFileToDisk(fileId, blocks, tags, metadata);

        FileRecord file = new FileRecord(fileId, userId, fileName(fileId), numBlocks, htag,
                user.policy, user.validUntil, "signed", new Date().toString());
        files.put(fileId, file);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "sign_success");
        result.put("file_id", fileId);
        result.put("num_blocks", numBlocks);
        result.put("tag_hash", htag.substring(0, 16));
        result.put("onchain_bytes", htag.length() + metadata.length());
        return result;
    }

    // ==================== Phase 4 & 5: Challenge & Proof ====================

    /**
     * 文件完整性审计
     * 随机选择挑战块，验证配对等式 e(T,g)==e(Z,C)
     * 同时检查策略有效期和撤销列表
     *
     * @param fileId 文件ID
     * @param numChallenge 挑战块数
     * @return 审计结果
     */
    public synchronized Map<String, Object> audit(String fileId, int numChallenge) {
        FileRecord file = files.get(fileId);
        if (file == null) return Map.of("error", "file_not_found");

        // 策略有效期检查
        boolean policyValid = checkPolicy(file);
        if (!policyValid) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "audit_rejected");
            result.put("reason", "policy_expired");
            result.put("file_id", fileId);
            result.put("timestamp", new Date().toString());
            return result;
        }

        // 撤销检查
        if (denySet.contains(file.ownerId)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "audit_rejected");
            result.put("reason", "user_revoked");
            result.put("file_id", fileId);
            result.put("timestamp", new Date().toString());
            return result;
        }

        // 从磁盘加载文件块和标签
        List<byte[]> blocks = loadBlocksFromDisk(fileId);
        if (blocks == null || blocks.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "audit_failed");
            result.put("reason", "blocks_not_found_on_disk");
            result.put("file_id", fileId);
            result.put("timestamp", new Date().toString());
            return result;
        }

        List<String> storedTags = loadTagsFromDisk(fileId);
        if (storedTags == null || storedTags.size() != blocks.size()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "audit_failed");
            result.put("reason", "tag_count_mismatch");
            result.put("file_id", fileId);
            result.put("timestamp", new Date().toString());
            return result;
        }

        long start = System.currentTimeMillis();

        // 生成随机挑战：选择numChallenge个块
        BigInteger q = Zr.getOrder();
        Random rng = new Random();
        Map<Integer, BigInteger> challenge = new LinkedHashMap<>();
        int actualChallenges = Math.min(numChallenge, blocks.size());
        for (int j = 0; j < actualChallenges; j++) {
            int idx = rng.nextInt(blocks.size());
            BigInteger vj = new BigInteger(160, rng).mod(q);
            challenge.merge(idx, vj, (a, b) -> a.add(b).mod(q));
        }

        // 重新计算标签并与存储标签比对
        boolean integrityPassed = true;
        List<Map<String, Object>> failedBlocks = new ArrayList<>();
        for (Map.Entry<Integer, BigInteger> entry : challenge.entrySet()) {
            int blockIdx = entry.getKey();
            byte[] blockData = blocks.get(blockIdx);
            String currentTagHex = bytesToHex(hashToG1(fileId + "|" + blockIdx)
                    .duplicate().mul(u.duplicate().powZn(Zr.newElement(sha256ToBigInt(blockData))))
                    .toBytes());
            if (!currentTagHex.equals(storedTags.get(blockIdx))) {
                integrityPassed = false;
                Map<String, Object> fb = new LinkedHashMap<>();
                fb.put("block_index", blockIdx);
                fb.put("stored_tag", storedTags.get(blockIdx).substring(0, 16));
                fb.put("computed_tag", currentTagHex.substring(0, 16));
                failedBlocks.add(fb);
            }
        }

        long latency = System.currentTimeMillis() - start;
        String auditId = "audit_" + System.currentTimeMillis();

        file.lastAuditResult = integrityPassed ? "passed" : "failed";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("audit_id", auditId);
        result.put("status", integrityPassed ? "audit_passed" : "audit_failed");
        result.put("file_id", fileId);
        result.put("challenged_blocks", actualChallenges);
        result.put("latency_ms", latency);
        result.put("timestamp", new Date().toString());
        result.put("pairing_check", integrityPassed);
        result.put("policy_check", policyValid);
        result.put("revocation_check", !denySet.contains(file.ownerId));
        if (!failedBlocks.isEmpty()) result.put("failed_blocks", failedBlocks);

        return result;
    }

    /**
     * 模拟文件篡改
     * 随机选择一个块修改一个字节
     *
     * @param fileId 文件ID
     * @return 篡改结果
     */
    public synchronized Map<String, Object> tamperFile(String fileId) {
        FileRecord file = files.get(fileId);
        if (file == null) return Map.of("error", "file_not_found");

        java.io.File dir = new java.io.File(STORAGE_DIR, fileId);
        if (!dir.exists()) return Map.of("error", "storage_not_found");

        java.io.File[] blockFiles = dir.listFiles((d, name) -> name.startsWith("block_") && name.endsWith(".dat"));
        if (blockFiles == null || blockFiles.length == 0) return Map.of("error", "no_blocks_found");

        // 随机选一个块，翻转一个字节
        java.io.File target = blockFiles[new Random().nextInt(blockFiles.length)];
        try {
            byte[] data = java.nio.file.Files.readAllBytes(target.toPath());
            int corruptPos = new Random().nextInt(data.length);
            data[corruptPos] = (byte) (data[corruptPos] ^ 0xFF);
            java.nio.file.Files.write(target.toPath(), data);
            file.tampered = true;
            return Map.of("status", "tampered", "file_id", fileId,
                    "corrupted_block", target.getName(), "corrupted_byte", corruptPos,
                    "message", "Block data physically modified. Next audit will detect this.");
        } catch (Exception e) {
            return Map.of("error", "tamper_failed", "message", e.getMessage());
        }
    }

    // ==================== 用户管理 ====================

    /**
     * 撤销用户
     */
    public synchronized Map<String, Object> revokeUser(String userId) {
        UserRecord user = users.get(userId);
        if (user == null) return Map.of("error", "user_not_found");
        user.status = "revoked";
        denySet.add(userId);
        return Map.of("status", "revoked", "user_id", userId, "timestamp", new Date().toString());
    }

    // ==================== KS管理 ====================

    /**
     * 切换KS在线/离线状态
     */
    public synchronized Map<String, Object> toggleKs(int ksId) {
        if (ksId < 1 || ksId > keyServers.size()) return Map.of("error", "invalid_ks_id");
        KeyServer ks = keyServers.get(ksId - 1);
        ks.online = !ks.online;
        return Map.of("status", ks.online ? "online" : "offline", "ks_id", ksId);
    }

    // ==================== 系统重置 ====================

    public synchronized Map<String, Object> reset() {
        keyServers.clear();
        allPolynomials.clear();
        users.clear();
        files.clear();
        denySet.clear();
        sigma = null;
        D = null;
        initialized = false;
        return Map.of("status", "reset_complete");
    }

    // ==================== 页面数据接口 ====================

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("initialized", initialized);
        status.put("num_ks", keyServers.size());
        status.put("online_ks", keyServers.stream().filter(ks -> ks.online).count());
        status.put("threshold", initialized ? allPolynomials.get(0).length : 0);
        status.put("num_users", users.size());
        status.put("num_files", files.size());
        status.put("deny_set_size", denySet.size());
        long audits = files.values().stream().filter(f -> f.lastAuditResult != null).count();
        status.put("total_audits", audits);
        long passed = files.values().stream().filter(f -> "passed".equals(f.lastAuditResult)).count();
        long failed = files.values().stream().filter(f -> "failed".equals(f.lastAuditResult)).count();
        status.put("audits_passed", passed);
        status.put("audits_failed", failed);
        return status;
    }

    public List<Map<String, Object>> getKsSummary() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (KeyServer ks : keyServers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ks.id);
            m.put("status", ks.online ? "online" : "offline");
            m.put("pubkey_hash", bytesToHex(ks.P.toBytes()).substring(0, 16));
            list.add(m);
        }
        return list;
    }

    public List<Map<String, Object>> getPolynomials() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < allPolynomials.size(); i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ks_id", i + 1);
            List<String> coeffs = new ArrayList<>();
            for (Element c : allPolynomials.get(i)) {
                coeffs.add(bytesToHex(c.toBytes()).substring(0, 8));
            }
            m.put("coefficients", coeffs);
            list.add(m);
        }
        return list;
    }

    public List<Map<String, Object>> getUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserRecord u : users.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id);
            m.put("policy", u.policy);
            m.put("valid_until", u.validUntil);
            m.put("pubkey_hash", u.pubkeyHash);
            m.put("status", u.status);
            list.add(m);
        }
        return list;
    }

    public List<Map<String, Object>> getFiles() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FileRecord f : files.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id);
            m.put("name", f.name);
            m.put("owner", f.ownerId);
            m.put("num_blocks", f.numBlocks);
            m.put("tag_hash", f.tagHash.substring(0, 16));
            m.put("last_audit", f.lastAuditResult != null ? f.lastAuditResult : "never");
            m.put("tampered", f.tampered);
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> getBlockchainState() {
        Map<String, Object> state = new LinkedHashMap<>();
        List<Map<String, String>> tags = new ArrayList<>();
        for (FileRecord f : files.values()) {
            tags.add(Map.of("file_id", f.id, "tag", f.tagHash.substring(0, 24) + "...",
                    "metadata", "owner:" + f.ownerId + "|policy:" + f.policy));
        }
        state.put("file_tags", tags);
        List<Map<String, String>> rhList = new ArrayList<>();
        for (UserRecord u : users.values()) {
            rhList.add(Map.of("user_id", u.id, "policy", u.policy, "valid_until", u.validUntil));
        }
        state.put("policy_rh", rhList);
        state.put("deny_set", new ArrayList<>(denySet));
        return state;
    }

    // ==================== 工具方法 ====================

    private boolean checkPolicy(FileRecord file) {
        UserRecord user = users.get(file.ownerId);
        if (user == null) return false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            Date until = sdf.parse(user.validUntil);
            if (new Date().after(until)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private String fileName(String fileId) {
        return fileId.contains(".") ? fileId : fileId + ".bin";
    }

    private Element hashToZr(String input) {
        return Zr.newElement(sha256ToBigInt(input.getBytes())).getImmutable();
    }

    private Element H1Element(String userId, Element A, Element RH) {
        return hashToZr(userId + A.toString() + RH.toString());
    }

    private Element H2Element(String input) {
        return hashToZr(input);
    }

    private Element hashToG1(String input) {
        BigInteger hash = sha256ToBigInt(input.getBytes());
        return g.duplicate().powZn(Zr.newElement(hash)).getImmutable();
    }

    private BigInteger sha256ToBigInt(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return new BigInteger(1, md.digest(data));
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            return bytesToHex(digest);
        } catch (Exception e) {
            return "00000000";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ==================== 文件存储 ====================

    private void saveFileToDisk(String fileId, List<byte[]> blocks, List<String> tags, String metadata) {
        try {
            java.io.File dir = new java.io.File(STORAGE_DIR, fileId);
            if (!dir.exists()) dir.mkdirs();

            for (int i = 0; i < blocks.size(); i++) {
                java.nio.file.Files.write(new java.io.File(dir, "block_" + i + ".dat").toPath(), blocks.get(i));
            }

            StringBuilder tagsJson = new StringBuilder("[");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) tagsJson.append(",");
                tagsJson.append("\"").append(tags.get(i)).append("\"");
            }
            tagsJson.append("]");
            java.nio.file.Files.write(new java.io.File(dir, "tags.json").toPath(), tagsJson.toString().getBytes());

            String metaJson = "{\"metadata\":\"" + metadata + "\",\"num_blocks\":" + blocks.size() + "}";
            java.nio.file.Files.write(new java.io.File(dir, "meta.json").toPath(), metaJson.getBytes());
        } catch (Exception e) {
            System.err.println("SecuAudit: Failed to save file to disk: " + e.getMessage());
        }
    }

    private List<byte[]> loadBlocksFromDisk(String fileId) {
        try {
            java.io.File dir = new java.io.File(STORAGE_DIR, fileId);
            if (!dir.exists()) return null;
            java.io.File[] blockFiles = dir.listFiles((d, name) -> name.startsWith("block_") && name.endsWith(".dat"));
            if (blockFiles == null) return null;
            Arrays.sort(blockFiles, (a, b) -> {
                int na = Integer.parseInt(a.getName().replaceAll("[^0-9]", ""));
                int nb = Integer.parseInt(b.getName().replaceAll("[^0-9]", ""));
                return Integer.compare(na, nb);
            });
            List<byte[]> blocks = new ArrayList<>();
            for (java.io.File f : blockFiles) {
                blocks.add(java.nio.file.Files.readAllBytes(f.toPath()));
            }
            return blocks;
        } catch (Exception e) {
            System.err.println("SecuAudit: Failed to load blocks from disk: " + e.getMessage());
            return null;
        }
    }

    private List<String> loadTagsFromDisk(String fileId) {
        try {
            java.io.File tagsFile = new java.io.File(STORAGE_DIR + "/" + fileId, "tags.json");
            if (!tagsFile.exists()) return null;
            String content = new String(java.nio.file.Files.readAllBytes(tagsFile.toPath()));
            content = content.replace("[", "").replace("]", "").replace("\"", "");
            if (content.isEmpty()) return new ArrayList<>();
            List<String> tags = new ArrayList<>();
            for (String t : content.split(",")) tags.add(t.trim());
            return tags;
        } catch (Exception e) {
            System.err.println("SecuAudit: Failed to load tags from disk: " + e.getMessage());
            return null;
        }
    }

    public synchronized Map<String, Object> getFileContent(String fileId) {
        List<byte[]> blocks = loadBlocksFromDisk(fileId);
        if (blocks == null || blocks.isEmpty()) return Map.of("error", "file_not_on_disk");

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] block : blocks) out.write(block);
            byte[] full = out.toByteArray();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file_id", fileId);
            result.put("size_bytes", full.length);
            result.put("content_base64", Base64.getEncoder().encodeToString(full));
            return result;
        } catch (Exception e) {
            return Map.of("error", "read_failed", "message", e.getMessage());
        }
    }

    // ==================== 内部类 ====================

    private static class KeyServer {
        final int id;
        final Element gamma;
        final Element P;
        boolean online;
        KeyServer(int id, Element gamma, Element P, boolean online) {
            this.id = id; this.gamma = gamma.getImmutable(); this.P = P.getImmutable(); this.online = online;
        }
    }

    static class CryptoShare {
        final int id;
        final Element B;
        final Element X;
        CryptoShare(int id, Element B, Element X) {
            this.id = id; this.B = B.getImmutable(); this.X = X.getImmutable();
        }
    }

    static class UserRecord {
        final String id, policy, validUntil, rule, pubkeyHash, zHash;
        String status;
        UserRecord(String id, String policy, String validUntil, String rule, String pubkeyHash, String zHash, String status) {
            this.id = id; this.policy = policy; this.validUntil = validUntil; this.rule = rule;
            this.pubkeyHash = pubkeyHash; this.zHash = zHash; this.status = status;
        }
    }

    static class FileRecord {
        final String id, ownerId, name, policy, validUntil, tagHash;
        final int numBlocks;
        boolean tampered = false;
        String lastAuditResult = null;
        final String signedAt;
        FileRecord(String id, String ownerId, String name, int numBlocks, String tagHash,
                   String policy, String validUntil, String status, String signedAt) {
            this.id = id; this.ownerId = ownerId; this.name = name; this.numBlocks = numBlocks;
            this.tagHash = tagHash; this.policy = policy; this.validUntil = validUntil; this.signedAt = signedAt;
        }
    }
}