package com.zhizao.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一转 Result。
 *
 * @author Caspian
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "无权限访问");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.error("系统异常：" + e.getMessage());
    }
}
