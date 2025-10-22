package com.mv.toki.version

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
// import com.mv.toki.BuildConfig // BuildConfig 문제로 주석 처리
import com.mv.toki.api.ApiClient
import com.mv.toki.api.AppUpdateCheckRequest
import com.mv.toki.api.AppUpdateCheckResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 앱 업데이트 체크 서비스
 * 서버에서 최신 버전 정보를 확인하고 업데이트 필요성을 판단합니다.
 */
class AppUpdateChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "AppUpdateChecker"
    }
    
    /**
     * 앱 업데이트 필요성을 체크합니다.
     * @return AppUpdateCheckResponse? 업데이트 정보 (업데이트가 필요한 경우에만 반환)
     */
    suspend fun checkForUpdates(): AppUpdateCheckResponse? = withContext(Dispatchers.IO) {
        // 변수를 함수 시작 부분에서 정의하여 catch 블록에서도 사용 가능하도록 함
        val packageName = context.packageName
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        val currentVersion = packageInfo.versionName ?: "1.0.0"
        val versionCode = packageInfo.longVersionCode
        
        try {
            Log.d(TAG, "=== 앱 업데이트 체크 시작 ===")
            
            Log.d(TAG, "현재 앱 정보:")
            Log.d(TAG, "  - Package Name: $packageName")
            Log.d(TAG, "  - Current Version: $currentVersion")
            Log.d(TAG, "  - Version Code: $versionCode")
            
            val request = AppUpdateCheckRequest(
                currentVersion = currentVersion,
                platform = "android",
                packageName = packageName
            )
            
            Log.d(TAG, "서버에 업데이트 체크 요청:")
            Log.d(TAG, "  - current_version: ${request.currentVersion}")
            Log.d(TAG, "  - platform: ${request.platform}")
            Log.d(TAG, "  - package_name: ${request.packageName}")
            
            val response = ApiClient.authApi.checkAppUpdate(request)
            
            Log.d(TAG, "서버 응답 상태:")
            Log.d(TAG, "  - Code: ${response.code()}")
            Log.d(TAG, "  - Message: ${response.message()}")
            Log.d(TAG, "  - IsSuccessful: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val updateInfo = response.body()!!
                
            Log.d(TAG, "업데이트 체크 응답:")
            Log.d(TAG, "  - needs_update: ${updateInfo.needsUpdate}")
            Log.d(TAG, "  - force_update: ${updateInfo.forceUpdate}")
            Log.d(TAG, "  - update_available: ${updateInfo.updateAvailable}")
            Log.d(TAG, "  - latest_version: ${updateInfo.latestVersion}")
            Log.d(TAG, "  - current_version: ${updateInfo.currentVersion}")
            Log.d(TAG, "  - update_priority: ${updateInfo.updatePriority}")
            Log.d(TAG, "  - update_type: ${updateInfo.updateType}")
            Log.d(TAG, "  - update_message: ${updateInfo.updateMessage}")
            Log.d(TAG, "  - store_url: ${updateInfo.storeUrl}")
            Log.d(TAG, "  - release_notes: ${updateInfo.releaseNotes}")
            
            // 디버깅을 위한 추가 정보
            Log.d(TAG, "=== 업데이트 판단 과정 ===")
            Log.d(TAG, "1. 서버 needs_update 값: ${updateInfo.needsUpdate}")
            Log.d(TAG, "2. 클라이언트 버전 비교 시작...")
                
                // 서버에서 needs_update를 제공한 경우 서버 판단 우선
                if (updateInfo.needsUpdate) {
                    Log.d(TAG, "✅ 서버 판단: 업데이트 필요함 - 사용자에게 알림")
                    return@withContext updateInfo
                } else {
                    // 서버에서 needs_update가 false인 경우에도 클라이언트 측에서 한번 더 확인
                    val clientNeedsUpdate = compareVersions(currentVersion, updateInfo.latestVersion)
                    if (clientNeedsUpdate) {
                        Log.d(TAG, "⚠️ 서버는 업데이트 불필요라고 했지만, 클라이언트 측에서 업데이트 필요함으로 판단")
                        Log.d(TAG, "   - 현재 버전: $currentVersion")
                        Log.d(TAG, "   - 최신 버전: ${updateInfo.latestVersion}")
                        // 서버 응답을 수정하여 업데이트 필요로 표시
                        val modifiedUpdateInfo = updateInfo.copy(needsUpdate = true)
                        return@withContext modifiedUpdateInfo
                    } else {
                        Log.d(TAG, "✅ 서버 및 클라이언트 모두 최신 버전 사용 중으로 판단")
                        return@withContext null
                    }
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "업데이트 체크 실패:")
                Log.e(TAG, "  - HTTP Code: ${response.code()}")
                Log.e(TAG, "  - Error Body: $errorBody")
                
                // 서버 오류 시에는 업데이트 팝업을 표시하지 않음
                Log.d(TAG, "서버 오류로 인해 업데이트 체크를 건너뜁니다")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "업데이트 체크 중 예외 발생", e)
            Log.e(TAG, "예외 상세: ${e.message}")
            Log.e(TAG, "예외 타입: ${e.javaClass.simpleName}")
            
            // 예외 발생 시에는 업데이트 팝업을 표시하지 않음
            Log.d(TAG, "예외 발생으로 인해 업데이트 체크를 건너뜁니다")
            return@withContext null
        }
    }
    
    /**
     * 스토어로 이동합니다.
     * @param storeUrl 스토어 URL (null인 경우 기본 Play Store URL 사용)
     */
    fun openAppStore(storeUrl: String? = null) {
        try {
            val url = storeUrl ?: "https://play.google.com/store/apps/details?id=${context.packageName}"
            Log.d(TAG, "스토어로 이동 시도: $url")
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "✅ 스토어로 이동 성공")
            
        } catch (e: Exception) {
            Log.e(TAG, "스토어로 이동 실패", e)
        }
    }
    
    /**
     * 클라이언트 측에서 버전을 비교합니다.
     * 서버 응답과 별도로 버전 비교를 수행하여 안전장치 역할을 합니다.
     * @param currentVersion 현재 앱 버전 (예: "1.0.0")
     * @param latestVersion 서버의 최신 버전 (예: "1.2.0")
     * @return true: 업데이트 필요, false: 최신 버전
     */
    private fun compareVersions(currentVersion: String, latestVersion: String): Boolean {
        return try {
            Log.d(TAG, "클라이언트 측 버전 비교:")
            Log.d(TAG, "  - 현재 버전: $currentVersion")
            Log.d(TAG, "  - 최신 버전: $latestVersion")
            
            val currentParts = currentVersion.split(".").map { it.toInt() }
            val latestParts = latestVersion.split(".").map { it.toInt() }
            
            Log.d(TAG, "  - 현재 버전 파트: $currentParts")
            Log.d(TAG, "  - 최신 버전 파트: $latestParts")
            
            // 메이저, 마이너, 패치 버전 순으로 비교
            val maxLength = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrNull(i) ?: 0
                val latestPart = latestParts.getOrNull(i) ?: 0
                
                Log.d(TAG, "  - 비교 [$i]: $currentPart vs $latestPart")
                
                if (latestPart > currentPart) {
                    Log.d(TAG, "  - 결과: 업데이트 필요 (최신 버전이 더 높음)")
                    return true
                }
                if (latestPart < currentPart) {
                    Log.d(TAG, "  - 결과: 업데이트 불필요 (현재 버전이 더 높음)")
                    return false
                }
            }
            
            Log.d(TAG, "  - 결과: 동일한 버전")
            false
        } catch (e: Exception) {
            Log.e(TAG, "버전 비교 실패", e)
            Log.e(TAG, "  - 현재 버전: $currentVersion")
            Log.e(TAG, "  - 최신 버전: $latestVersion")
            Log.e(TAG, "  - 예외: ${e.message}")
            false // 비교 실패 시 업데이트 불필요로 처리
        }
    }
    
    /**
     * 현재 앱의 버전 정보를 가져옵니다.
     */
    fun getCurrentVersionInfo(): VersionInfo {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            VersionInfo(
                versionName = packageInfo.versionName ?: "1.0.0",
                versionCode = packageInfo.longVersionCode,
                packageName = context.packageName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "패키지 정보를 가져올 수 없습니다", e)
            VersionInfo(
                versionName = "1.0.0",
                versionCode = 1L,
                packageName = context.packageName
            )
        }
    }
    
    /**
     * 앱 버전 정보를 저장합니다.
     */
    data class VersionInfo(
        val versionName: String,
        val versionCode: Long,
        val packageName: String
    )
}
