package com.mv.toki.utils

/**
 * 비밀번호 검증 결과를 나타내는 sealed class
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * 비밀번호 유효성 검사 유틸리티
 * 클라이언트에서 미리 검증할 수 있는 규칙을 제공합니다.
 */
object PasswordValidator {
    
    /**
     * 비밀번호를 검증합니다.
     * 
     * @param password 검증할 비밀번호
     * @return ValidationResult.Success 또는 ValidationResult.Error
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.length < 8 -> ValidationResult.Error("비밀번호는 최소 8자 이상이어야 합니다")
            !password.any { it.isLetter() } -> ValidationResult.Error("비밀번호는 영문자를 포함해야 합니다")
            !password.any { it.isDigit() } -> ValidationResult.Error("비밀번호는 숫자를 포함해야 합니다")
            else -> ValidationResult.Success
        }
    }
}
