package com.zhizao.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhizao.entity.AuditLog;
import com.zhizao.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 写操作审计：对 Controller 的 POST/PUT/DELETE 记录操作人、路径、参数摘要。
 *
 * <p>参数落库前会把密码、令牌等敏感字段替换为 {@code ***}，避免改密接口把明文写进审计表。</p>
 *
 * @author Caspian
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditAspect.class);

    /** 请求体中的敏感字段，落库前统一脱敏。 */
    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "oldPassword", "newPassword", "initialPassword", "token");

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate auditTransaction;

    public AuditAspect(AuditLogMapper auditLogMapper, ObjectMapper objectMapper,
                       PlatformTransactionManager transactionManager) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
        this.auditTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Around("execution(* com.zhizao.controller..*(..)) && !execution(* com.zhizao.controller.AuthController.*(..)) "
            + "&& (@annotation(org.springframework.web.bind.annotation.PostMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.PutMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        AuditLog log = new AuditLog();
        log.setUsername(currentUsername());
        log.setMethod(requestMethod());
        log.setUri(requestUri());
        log.setOperation(pjp.getSignature().getDeclaringType().getSimpleName()
                + "." + pjp.getSignature().getName());
        log.setParams(serializeArgs(pjp.getArgs()));
        log.setIp(clientIp());
        log.setCreatedAt(LocalDateTime.now());
        try {
            Object result = pjp.proceed();
            log.setSuccess(true);
            persist(log);
            return result;
        } catch (Throwable e) {
            log.setSuccess(false);
            log.setErrorMessage(describeError(e));
            persist(log);
            throw e;
        }
    }

    /**
     * 独立事务写入审计：业务事务回滚不影响审计，审计失败也不影响业务。
     *
     * @param log 审计记录
     */
    private void persist(AuditLog log) {
        try {
            auditTransaction.executeWithoutResult(status -> auditLogMapper.insert(log));
        } catch (Exception e) {
            LOGGER.warn("审计写入失败 uri={} operation={}", log.getUri(), log.getOperation(), e);
        }
    }

    private String describeError(Throwable e) {
        String message = e.getMessage();
        String text = message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private HttpServletRequest request() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String requestMethod() {
        HttpServletRequest req = request();
        return req != null ? req.getMethod() : null;
    }

    private String requestUri() {
        HttpServletRequest req = request();
        return req != null ? req.getRequestURI() : null;
    }

    private String clientIp() {
        HttpServletRequest req = request();
        if (req == null) {
            return null;
        }
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }

    /**
     * 序列化请求参数，密码等敏感字段替换为掩码后再截断。
     *
     * @param args 方法参数
     * @return 脱敏后的 JSON，序列化失败返回 null
     */
    String serializeArgs(Object[] args) {
        try {
            JsonNode node = objectMapper.valueToTree(args);
            maskSensitive(node);
            String json = objectMapper.writeValueAsString(node);
            return json.length() > 1000 ? json.substring(0, 1000) : json;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 递归脱敏对象与数组中的敏感字段。
     *
     * @param node JSON 节点
     */
    private void maskSensitive(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (SENSITIVE_FIELDS.contains(name)) {
                    obj.put(name, "***");
                } else {
                    maskSensitive(obj.get(name));
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::maskSensitive);
        }
    }
}
