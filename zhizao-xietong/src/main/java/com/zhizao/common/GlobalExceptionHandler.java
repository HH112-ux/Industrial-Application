package com.zhizao.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全局异常处理：统一转 Result。
 *
 * @author Caspian
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 兜底异常对外提示，具体原因只进日志，避免泄露内部细节。 */
    private static final String INTERNAL_ERROR_MESSAGE = "系统繁忙，请稍后重试";

    /**
     * 处理设备鉴权异常，按凭证无效或设备停用返回 401/403。
     *
     * @param e 设备鉴权异常
     * @return 带 HTTP 状态码的错误响应
     */
    @ExceptionHandler(DeviceAuthException.class)
    public ResponseEntity<Result<Void>> handleDeviceAuth(DeviceAuthException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Result.error(e.getStatus(), e.getMessage()));
    }

    /**
     * AI 能力未启用：返回 HTTP 503 与业务码 503，明确区分「未启用」与「系统异常」。
     *
     * @param e 未启用异常
     * @return 带 HTTP 503 状态码的错误响应
     */
    @ExceptionHandler(AiDisabledException.class)
    public ResponseEntity<Result<Void>> handleAiDisabled(AiDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.error(503, e.getMessage()));
    }

    /**
     * 处理请求参数非法，返回 HTTP 400，避免与业务拒绝（200 + code=500）混用。
     *
     * @param e 参数非法异常
     * @return 带 HTTP 400 状态码的错误响应
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Result<Void>> handleBadRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, e.getMessage()));
    }

    /**
     * 处理业务拒绝：保持 HTTP 200，业务码由异常携带。
     *
     * <p>既有异常的默认业务码为 500，行为与改造前一致；新接口可携带 409 等业务码，
     * 遵循接口契约中「请求合法但业务规则不满足」的口径。</p>
     *
     * @param e 业务异常
     * @return 带业务码的统一响应体
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 账号禁用或跨车间越权，返回 HTTP 403。
     *
     * @param e 访问拒绝异常
     * @return 带 HTTP 403 状态码的错误响应
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, e.getMessage()));
    }

    /**
     * 令牌失效或账号不可用，返回 HTTP 401，前端据此清理本地令牌。
     *
     * @param e 未认证异常
     * @return 带 HTTP 401 状态码的错误响应
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.error("请求体格式错误");
    }

    /**
     * 处理查询参数类型或格式错误，返回 HTTP 400，避免客户端错误落到兜底 500。
     *
     * <p>典型场景是时间参数：`from=2026-11-01` 传给需要 {@code yyyy-MM-ddTHH:mm:ss} 的
     * {@link java.time.LocalDateTime} 参数。所有入口共用该处理，不需要逐个控制器捕获。</p>
     *
     * @param e 参数类型不匹配异常
     * @return 带 HTTP 400 状态码的错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "参数 " + e.getName() + " 格式不正确" + expectedFormat(e.getRequiredType())));
    }

    /**
     * 给出时间参数的期望格式，其他类型只提示格式不正确。
     *
     * @param type 目标类型，可能为 null
     * @return 追加到错误文案后的格式说明
     */
    private String expectedFormat(Class<?> type) {
        if (type == LocalDateTime.class) {
            return "，时间格式应为 yyyy-MM-ddTHH:mm:ss";
        }
        if (type == LocalDate.class) {
            return "，日期格式应为 yyyy-MM-dd";
        }
        return "";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "无权限访问");
    }

    /**
     * 未预期异常兜底：返回 HTTP 500 与固定文案，原始异常只落日志并用 traceId 关联。
     *
     * @param e 未预期异常
     * @return 带 HTTP 500 状态码的错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        LOGGER.error("未处理异常 traceId={}", traceId, e);
        String message = StringUtils.hasText(traceId)
                ? INTERNAL_ERROR_MESSAGE + "（追踪号 " + traceId + "）"
                : INTERNAL_ERROR_MESSAGE;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, message));
    }
}
