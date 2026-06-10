# XhsCommentHelper V0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved V0.1 Android helper for manually screening Xiaohongshu comment users with an overlay, accessibility-assisted comment reading, draft generation, copy, and best-effort input filling.

**Architecture:** Keep the existing Kotlin/XML Android app and add small packages for domain state, DataStore settings, accessibility helpers, overlay service, and utilities. The app remains local-only, conservative, and never performs automatic sending or batch processing.

**Tech Stack:** Kotlin, Android XML views, AppCompat, ConstraintLayout, DataStore Preferences, AccessibilityService, WindowManager overlay, ClipboardManager, JUnit.

---

## File Structure

- Modify `gradle/libs.versions.toml`
  - Add DataStore Preferences dependency coordinates.
- Modify `app/build.gradle.kts`
  - Add DataStore dependency.
- Modify `app/src/main/AndroidManifest.xml`
  - Add overlay permission.
  - Register `XhsAccessibilityService`.
  - Register `FloatingOverlayService`.
- Modify `app/src/main/res/values/strings.xml`
  - Add Chinese UI strings and accessibility service description.
- Modify `app/src/main/res/layout/activity_main.xml`
  - Replace starter layout with permission status, fixed text input, controls, and recent state.
- Create `app/src/main/res/layout/overlay_floating_panel.xml`
  - Expanded floating overlay layout.
- Create `app/src/main/res/layout/overlay_collapsed_button.xml`
  - Collapsed floating overlay layout.
- Create `app/src/main/res/xml/accessibility_service_config.xml`
  - Accessibility service configuration restricted to Xiaohongshu package.
- Modify `app/src/main/java/com/ccy/xhscommenthelper/MainActivity.kt`
  - Wire settings, permission status, overlay start/stop, and recent lead display.
- Create `app/src/main/java/com/ccy/xhscommenthelper/domain/Lead.kt`
  - Current lead data model.
- Create `app/src/main/java/com/ccy/xhscommenthelper/domain/LeadStatus.kt`
  - Workflow status enum.
- Create `app/src/main/java/com/ccy/xhscommenthelper/domain/MessageBuilder.kt`
  - Deterministic message generation.
- Create `app/src/main/java/com/ccy/xhscommenthelper/data/UserSettings.kt`
  - Settings model.
- Create `app/src/main/java/com/ccy/xhscommenthelper/data/SettingsRepository.kt`
  - DataStore fixed-text and target-package persistence.
- Create `app/src/main/java/com/ccy/xhscommenthelper/data/RecentLeadStore.kt`
  - DataStore latest comment and latest message persistence.
- Create `app/src/main/java/com/ccy/xhscommenthelper/util/ClipboardHelper.kt`
  - Clipboard copy/read helper.
- Create `app/src/main/java/com/ccy/xhscommenthelper/util/PermissionHelper.kt`
  - Overlay and accessibility permission checks.
- Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/AccessibilityBridge.kt`
  - Simple service-instance bridge.
- Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/XhsAccessibilityService.kt`
  - Accessibility service root access and lifecycle bridge.
- Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/NodeFinder.kt`
  - Node flattening and clickable-parent lookup.
- Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/CommentReader.kt`
  - Comment candidate filtering with clipboard fallback.
- Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/XhsActionExecutor.kt`
  - Conservative node-based profile opening.
- Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/MessageFiller.kt`
  - Input filling via `ACTION_SET_TEXT` and `ACTION_PASTE`.
- Create `app/src/main/java/com/ccy/xhscommenthelper/overlay/FloatingOverlayService.kt`
  - WindowManager overlay, lead state, and button handlers.
- Modify `app/src/test/java/com/ccy/xhscommenthelper/ExampleUnitTest.kt`
  - Replace generated sample with `MessageBuilder` and comment filtering tests.
- Create `README.md`
  - Feature, permissions, usage, and safety notes.

---

### Task 1: Dependencies And Manifest Wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/xml/accessibility_service_config.xml`

- [ ] **Step 1: Add DataStore version and library**

Edit `gradle/libs.versions.toml` so the versions and libraries sections include:

```toml
[versions]
agp = "9.2.1"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
appcompat = "1.6.1"
material = "1.10.0"
activityKtx = "1.8.0"
constraintlayout = "2.1.4"
datastorePreferences = "1.1.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
```

Keep the existing `[plugins]` section unchanged.

- [ ] **Step 2: Add DataStore dependency**

Edit `app/build.gradle.kts` dependencies:

```kotlin
dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
```

- [ ] **Step 3: Add strings**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">XhsCommentHelper</string>
    <string name="main_title">小红书评论区筛人助手</string>
    <string name="main_description">这是一个手机端提效工具，用于辅助读取当前评论、生成私信草稿并复制或填入输入框。最终发送需要你人工确认。</string>
    <string name="accessibility_service_description">辅助读取小红书当前屏幕评论内容，并帮助填入由用户确认发送的私信草稿。</string>
</resources>
```

- [ ] **Step 4: Create accessibility config**

Create `app/src/main/res/xml/accessibility_service_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewFocused"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="com.xingin.xhs" />
```

- [ ] **Step 5: Register permissions and services**

Edit `app/src/main/AndroidManifest.xml` to include:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.XhsCommentHelper">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".accessibility.XhsAccessibilityService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <service
            android:name=".overlay.FloatingOverlayService"
            android:exported="false" />
    </application>

</manifest>
```

- [ ] **Step 6: Run build to verify manifest/resource wiring**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: build reaches Kotlin compilation and may fail because referenced service classes do not exist yet. Resource and manifest XML parsing should not fail.

- [ ] **Step 7: Commit**

Run:

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/xml/accessibility_service_config.xml
git commit -m "chore: wire v0.1 android services"
```

---

### Task 2: Domain Models And Pure Logic

**Files:**
- Create: `app/src/main/java/com/ccy/xhscommenthelper/domain/Lead.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/domain/LeadStatus.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/domain/MessageBuilder.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/CommentTextFilter.kt`
- Modify: `app/src/test/java/com/ccy/xhscommenthelper/ExampleUnitTest.kt`

- [ ] **Step 1: Write failing unit tests**

Replace `app/src/test/java/com/ccy/xhscommenthelper/ExampleUnitTest.kt` with:

```kotlin
package com.ccy.xhscommenthelper

import com.ccy.xhscommenthelper.accessibility.CommentTextFilter
import com.ccy.xhscommenthelper.domain.MessageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun messageBuilderUsesDocumentedTemplate() {
        val message = MessageBuilder.build(
            comment = "这个多少钱呀",
            fixedText = "方便的话可以了解一下，我们这边可以给你发详细介绍～"
        )

        assertEquals(
            "刚刚看到你评论：“这个多少钱呀”\n方便的话可以了解一下，我们这边可以给你发详细介绍～",
            message
        )
    }

    @Test
    fun commentTextFilterRejectsNoiseAndShortText() {
        assertTrue(CommentTextFilter.isNoiseText("关注"))
        assertTrue(CommentTextFilter.isNoiseText("赞"))
        assertTrue(CommentTextFilter.isNoiseText("说点什么"))
        assertFalse(CommentTextFilter.isNoiseText("这个多少钱呀"))
    }

    @Test
    fun commentTextFilterPicksFirstReasonableCandidate() {
        val candidate = CommentTextFilter.pickBestCommentCandidate(
            listOf("关注", "这个多少钱呀", "回复")
        )

        assertEquals("这个多少钱呀", candidate)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.ccy.xhscommenthelper.ExampleUnitTest"
```

Expected: FAIL because `MessageBuilder` and `CommentTextFilter` do not exist.

- [ ] **Step 3: Create lead status enum**

Create `app/src/main/java/com/ccy/xhscommenthelper/domain/LeadStatus.kt`:

```kotlin
package com.ccy.xhscommenthelper.domain

enum class LeadStatus {
    IDLE,
    COMMENT_READ,
    PROFILE_OPENED,
    MARKED_SUITABLE,
    MARKED_UNSUITABLE,
    MESSAGE_GENERATED,
    MESSAGE_COPIED,
    MESSAGE_FILLED,
    ERROR
}
```

- [ ] **Step 4: Create lead model**

Create `app/src/main/java/com/ccy/xhscommenthelper/domain/Lead.kt`:

```kotlin
package com.ccy.xhscommenthelper.domain

data class Lead(
    val nickname: String? = null,
    val comment: String? = null,
    val profileOpened: Boolean = false,
    val suitable: Boolean? = null,
    val message: String? = null,
    val status: LeadStatus = LeadStatus.IDLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 5: Create message builder**

Create `app/src/main/java/com/ccy/xhscommenthelper/domain/MessageBuilder.kt`:

```kotlin
package com.ccy.xhscommenthelper.domain

object MessageBuilder {
    fun build(comment: String, fixedText: String): String {
        return "刚刚看到你评论：“$comment”\n$fixedText"
    }
}
```

- [ ] **Step 6: Create comment text filter**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/CommentTextFilter.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

object CommentTextFilter {
    private val exactNoiseTexts = setOf(
        "关注",
        "点赞",
        "回复",
        "分享",
        "收藏",
        "说点什么",
        "搜索",
        "首页",
        "购物",
        "消息",
        "我"
    )

    fun isNoiseText(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.length <= 1) return true
        return normalized in exactNoiseTexts
    }

    fun pickBestCommentCandidate(texts: List<String>): String? {
        return texts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isNoiseText(it) }
            .firstOrNull { it.length in 2..120 }
    }
}
```

- [ ] **Step 7: Run tests to verify pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.ccy.xhscommenthelper.ExampleUnitTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```powershell
git add app/src/main/java/com/ccy/xhscommenthelper/domain app/src/main/java/com/ccy/xhscommenthelper/accessibility/CommentTextFilter.kt app/src/test/java/com/ccy/xhscommenthelper/ExampleUnitTest.kt
git commit -m "feat: add lead domain logic"
```

---

### Task 3: DataStore Settings And Utilities

**Files:**
- Create: `app/src/main/java/com/ccy/xhscommenthelper/data/UserSettings.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/data/SettingsRepository.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/data/RecentLeadStore.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/util/ClipboardHelper.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/util/PermissionHelper.kt`

- [ ] **Step 1: Create user settings model**

Create `app/src/main/java/com/ccy/xhscommenthelper/data/UserSettings.kt`:

```kotlin
package com.ccy.xhscommenthelper.data

data class UserSettings(
    val fixedText: String = DEFAULT_FIXED_TEXT,
    val targetPackageName: String = DEFAULT_TARGET_PACKAGE_NAME
) {
    companion object {
        const val DEFAULT_FIXED_TEXT = "方便的话可以了解一下，我们这边可以给你发详细介绍～"
        const val DEFAULT_TARGET_PACKAGE_NAME = "com.xingin.xhs"
    }
}
```

- [ ] **Step 2: Create settings repository**

Create `app/src/main/java/com/ccy/xhscommenthelper/data/SettingsRepository.kt`:

```kotlin
package com.ccy.xhscommenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val fixedTextKey = stringPreferencesKey("fixed_text")
    private val targetPackageKey = stringPreferencesKey("target_package")

    val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            fixedText = prefs[fixedTextKey] ?: UserSettings.DEFAULT_FIXED_TEXT,
            targetPackageName = prefs[targetPackageKey] ?: UserSettings.DEFAULT_TARGET_PACKAGE_NAME
        )
    }

    suspend fun saveFixedText(text: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[fixedTextKey] = text
        }
    }

    suspend fun saveTargetPackageName(packageName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[targetPackageKey] = packageName
        }
    }
}
```

- [ ] **Step 3: Create recent lead store**

Create `app/src/main/java/com/ccy/xhscommenthelper/data/RecentLeadStore.kt`:

```kotlin
package com.ccy.xhscommenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentLeadDataStore by preferencesDataStore(name = "recent_lead")

data class RecentLead(
    val comment: String = "",
    val message: String = ""
)

class RecentLeadStore(private val context: Context) {
    private val recentCommentKey = stringPreferencesKey("recent_comment")
    private val recentMessageKey = stringPreferencesKey("recent_message")

    val recentLeadFlow: Flow<RecentLead> = context.recentLeadDataStore.data.map { prefs ->
        RecentLead(
            comment = prefs[recentCommentKey].orEmpty(),
            message = prefs[recentMessageKey].orEmpty()
        )
    }

    suspend fun saveComment(comment: String) {
        context.recentLeadDataStore.edit { prefs ->
            prefs[recentCommentKey] = comment
        }
    }

    suspend fun saveMessage(message: String) {
        context.recentLeadDataStore.edit { prefs ->
            prefs[recentMessageKey] = message
        }
    }
}
```

- [ ] **Step 4: Create clipboard helper**

Create `app/src/main/java/com/ccy/xhscommenthelper/util/ClipboardHelper.kt`:

```kotlin
package com.ccy.xhscommenthelper.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardHelper(private val context: Context) {
    fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xhs_message", text))
    }

    fun getText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}
```

- [ ] **Step 5: Create permission helper**

Create `app/src/main/java/com/ccy/xhscommenthelper/util/PermissionHelper.kt`:

```kotlin
package com.ccy.xhscommenthelper.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.ccy.xhscommenthelper.accessibility.XhsAccessibilityService

object PermissionHelper {
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/${XhsAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
```

- [ ] **Step 6: Run unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```powershell
git add app/src/main/java/com/ccy/xhscommenthelper/data app/src/main/java/com/ccy/xhscommenthelper/util
git commit -m "feat: add local settings utilities"
```

---

### Task 4: Accessibility Helpers

**Files:**
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/AccessibilityBridge.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/XhsAccessibilityService.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/NodeFinder.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/CommentReader.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/XhsActionExecutor.kt`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/accessibility/MessageFiller.kt`

- [ ] **Step 1: Create accessibility bridge**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/AccessibilityBridge.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

object AccessibilityBridge {
    var service: XhsAccessibilityService? = null
}
```

- [ ] **Step 2: Create accessibility service**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/XhsAccessibilityService.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class XhsAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        AccessibilityBridge.service = this
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (AccessibilityBridge.service === this) {
            AccessibilityBridge.service = null
        }
        super.onDestroy()
    }

    fun getRoot(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
```

- [ ] **Step 3: Create node finder**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/NodeFinder.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo

object NodeFinder {
    fun flatten(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()

        fun dfs(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result.add(node)
            for (index in 0 until node.childCount) {
                dfs(node.getChild(index))
            }
        }

        dfs(root)
        return result
    }

    fun findByText(root: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        return flatten(root).firstOrNull { node ->
            val text = node.text?.toString().orEmpty()
            keywords.any { keyword -> text.contains(keyword) }
        }
    }

    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }
}
```

- [ ] **Step 4: Create comment reader**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/CommentReader.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.util.ClipboardHelper

class CommentReader(private val clipboardHelper: ClipboardHelper) {
    fun readCurrentComment(root: AccessibilityNodeInfo?): String? {
        val texts = NodeFinder.flatten(root)
            .mapNotNull { node -> node.text?.toString()?.trim() }
            .filter { text -> text.isNotBlank() }
            .filterNot { text -> CommentTextFilter.isNoiseText(text) }

        return CommentTextFilter.pickBestCommentCandidate(texts)
            ?: clipboardHelper.getText()?.trim()?.takeIf { it.isNotBlank() }
    }
}
```

- [ ] **Step 5: Create action executor**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/XhsActionExecutor.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

import android.view.accessibility.AccessibilityNodeInfo

class XhsActionExecutor {
    fun openProfile(root: AccessibilityNodeInfo?): Boolean {
        val possibleNicknameNode = NodeFinder.flatten(root).firstOrNull { node ->
            val text = node.text?.toString().orEmpty().trim()
            text.isNotBlank() && text.length in 2..20 && !isSystemText(text)
        }

        val clickable = NodeFinder.findClickableParent(possibleNicknameNode)
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun isSystemText(text: String): Boolean {
        val systemWords = listOf("关注", "回复", "点赞", "分享", "收藏")
        return systemWords.any { word -> text.contains(word) }
    }
}
```

- [ ] **Step 6: Create message filler**

Create `app/src/main/java/com/ccy/xhscommenthelper/accessibility/MessageFiller.kt`:

```kotlin
package com.ccy.xhscommenthelper.accessibility

import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ccy.xhscommenthelper.util.ClipboardHelper

class MessageFiller(private val clipboardHelper: ClipboardHelper) {
    fun fillMessage(root: AccessibilityNodeInfo?, message: String): Boolean {
        clipboardHelper.copyText(message)
        val inputNode = findInputNode(root) ?: return false

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
        }
        if (inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return true
        }

        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun findInputNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return NodeFinder.flatten(root).firstOrNull { node ->
            val className = node.className?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString().orEmpty()
            } else {
                ""
            }

            className.contains("EditText", ignoreCase = true) ||
                text.contains("发消息") ||
                text.contains("发送消息") ||
                text.contains("说点什么") ||
                text.contains("请输入") ||
                hint.contains("发消息") ||
                hint.contains("发送消息") ||
                hint.contains("说点什么") ||
                hint.contains("请输入")
        }
    }
}
```

- [ ] **Step 7: Run build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: build succeeds or only fails later because `FloatingOverlayService` is not created yet. No errors should remain in accessibility helper classes.

- [ ] **Step 8: Commit**

Run:

```powershell
git add app/src/main/java/com/ccy/xhscommenthelper/accessibility
git commit -m "feat: add accessibility helpers"
```

---

### Task 5: Main App UI And Permission Controls

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/ccy/xhscommenthelper/MainActivity.kt`

- [ ] **Step 1: Replace main layout**

Replace `app/src/main/res/layout/activity_main.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    tools:context=".MainActivity">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:id="@+id/titleTextView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/main_title"
            android:textSize="24sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/descriptionTextView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/main_description"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/overlayPermissionTextView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="悬浮窗权限：未开启"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/accessibilityPermissionTextView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="辅助功能权限：未开启"
            android:textSize="16sp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="固定话术："
            android:textSize="16sp" />

        <EditText
            android:id="@+id/fixedTextEditText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:gravity="top"
            android:inputType="textMultiLine"
            android:minLines="3" />

        <Button
            android:id="@+id/saveFixedTextButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="保存话术" />

        <Button
            android:id="@+id/startOverlayButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="开启悬浮窗" />

        <Button
            android:id="@+id/stopOverlayButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="关闭悬浮窗" />

        <Button
            android:id="@+id/openAccessibilitySettingsButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="开启辅助功能权限" />

        <Button
            android:id="@+id/openOverlaySettingsButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="开启悬浮窗权限" />

        <TextView
            android:id="@+id/recentCommentTextView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="最近评论：暂无"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/recentMessageTextView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="最近私信内容：暂无"
            android:textSize="15sp" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 2: Replace MainActivity implementation**

Replace `app/src/main/java/com/ccy/xhscommenthelper/MainActivity.kt` with:

```kotlin
package com.ccy.xhscommenthelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ccy.xhscommenthelper.data.RecentLeadStore
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.overlay.FloatingOverlayService
import com.ccy.xhscommenthelper.util.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var recentLeadStore: RecentLeadStore

    private lateinit var overlayPermissionTextView: TextView
    private lateinit var accessibilityPermissionTextView: TextView
    private lateinit var fixedTextEditText: EditText
    private lateinit var recentCommentTextView: TextView
    private lateinit var recentMessageTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsRepository = SettingsRepository(applicationContext)
        recentLeadStore = RecentLeadStore(applicationContext)
        bindViews()
        bindActions()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun bindViews() {
        overlayPermissionTextView = findViewById(R.id.overlayPermissionTextView)
        accessibilityPermissionTextView = findViewById(R.id.accessibilityPermissionTextView)
        fixedTextEditText = findViewById(R.id.fixedTextEditText)
        recentCommentTextView = findViewById(R.id.recentCommentTextView)
        recentMessageTextView = findViewById(R.id.recentMessageTextView)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.saveFixedTextButton).setOnClickListener {
            lifecycleScope.launch {
                settingsRepository.saveFixedText(fixedTextEditText.text.toString())
                Toast.makeText(this@MainActivity, "话术已保存", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.startOverlayButton).setOnClickListener {
            if (!PermissionHelper.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限，否则无法显示工具面板。", Toast.LENGTH_SHORT).show()
                openOverlaySettings()
                return@setOnClickListener
            }
            startService(Intent(this, FloatingOverlayService::class.java))
        }

        findViewById<Button>(R.id.stopOverlayButton).setOnClickListener {
            stopService(Intent(this, FloatingOverlayService::class.java))
        }

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.openOverlaySettingsButton).setOnClickListener {
            openOverlaySettings()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            fixedTextEditText.setText(settingsRepository.settingsFlow.first().fixedText)
        }

        lifecycleScope.launch {
            recentLeadStore.recentLeadFlow.collect { recent ->
                recentCommentTextView.text = "最近评论：${recent.comment.ifBlank { "暂无" }}"
                recentMessageTextView.text = "最近私信内容：${recent.message.ifBlank { "暂无" }}"
            }
        }
    }

    private fun updatePermissionStatus() {
        val overlayStatus = if (PermissionHelper.canDrawOverlays(this)) "已开启" else "未开启"
        val accessibilityStatus = if (PermissionHelper.isAccessibilityServiceEnabled(this)) "已开启" else "未开启"
        overlayPermissionTextView.text = "悬浮窗权限：$overlayStatus"
        accessibilityPermissionTextView.text = "辅助功能权限：$accessibilityStatus"
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
```

- [ ] **Step 3: Run build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: build may fail because `FloatingOverlayService` has not been created yet. No errors should remain in `MainActivity` or the main layout.

- [ ] **Step 4: Commit**

Run:

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/ccy/xhscommenthelper/MainActivity.kt
git commit -m "feat: add main configuration screen"
```

---

### Task 6: Floating Overlay UI And Workflow

**Files:**
- Create: `app/src/main/res/layout/overlay_collapsed_button.xml`
- Create: `app/src/main/res/layout/overlay_floating_panel.xml`
- Create: `app/src/main/java/com/ccy/xhscommenthelper/overlay/FloatingOverlayService.kt`

- [ ] **Step 1: Create collapsed overlay layout**

Create `app/src/main/res/layout/overlay_collapsed_button.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/collapsedButton"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:background="#CCF44336"
    android:gravity="center"
    android:text="筛"
    android:textColor="#FFFFFFFF"
    android:textSize="20sp"
    android:textStyle="bold" />
```

- [ ] **Step 2: Create expanded overlay layout**

Create `app/src/main/res/layout/overlay_floating_panel.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/overlayPanel"
    android:layout_width="260dp"
    android:layout_height="wrap_content"
    android:background="#EEFFFFFF"
    android:elevation="8dp"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/overlayTitleTextView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="筛人助手"
        android:textColor="#FF222222"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/commentTextView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:maxLines="4"
        android:text="当前评论：暂无"
        android:textColor="#FF333333"
        android:textSize="13sp" />

    <Button
        android:id="@+id/readCommentButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="读取评论" />

    <Button
        android:id="@+id/openProfileButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="打开主页" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <Button
            android:id="@+id/suitableButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="合适" />

        <Button
            android:id="@+id/unsuitableButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="不合适" />
    </LinearLayout>

    <TextView
        android:id="@+id/messageTextView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:maxLines="4"
        android:text="私信：暂无"
        android:textColor="#FF333333"
        android:textSize="13sp" />

    <Button
        android:id="@+id/generateMessageButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="生成私信" />

    <Button
        android:id="@+id/copyMessageButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="复制私信" />

    <Button
        android:id="@+id/fillMessageButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="填入私信框" />

    <Button
        android:id="@+id/collapseButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="收起" />
</LinearLayout>
```

- [ ] **Step 3: Create floating overlay service**

Create `app/src/main/java/com/ccy/xhscommenthelper/overlay/FloatingOverlayService.kt`:

```kotlin
package com.ccy.xhscommenthelper.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.ccy.xhscommenthelper.R
import com.ccy.xhscommenthelper.accessibility.AccessibilityBridge
import com.ccy.xhscommenthelper.accessibility.CommentReader
import com.ccy.xhscommenthelper.accessibility.MessageFiller
import com.ccy.xhscommenthelper.accessibility.XhsActionExecutor
import com.ccy.xhscommenthelper.data.RecentLeadStore
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.domain.Lead
import com.ccy.xhscommenthelper.domain.LeadStatus
import com.ccy.xhscommenthelper.domain.MessageBuilder
import com.ccy.xhscommenthelper.util.ClipboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var layoutInflater: LayoutInflater
    private lateinit var params: WindowManager.LayoutParams
    private var currentView: View? = null
    private var expanded = false

    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var commentReader: CommentReader
    private lateinit var actionExecutor: XhsActionExecutor
    private lateinit var messageFiller: MessageFiller
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var recentLeadStore: RecentLeadStore

    private var currentLead = Lead()
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutInflater = LayoutInflater.from(this)
        clipboardHelper = ClipboardHelper(this)
        commentReader = CommentReader(clipboardHelper)
        actionExecutor = XhsActionExecutor()
        messageFiller = MessageFiller(clipboardHelper)
        settingsRepository = SettingsRepository(applicationContext)
        recentLeadStore = RecentLeadStore(applicationContext)
        showCollapsed()
    }

    override fun onDestroy() {
        removeCurrentView()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createLayoutParams(width: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - width
            y = resources.displayMetrics.heightPixels / 3
        }
    }

    private fun showCollapsed() {
        expanded = false
        removeCurrentView()
        val view = layoutInflater.inflate(R.layout.overlay_collapsed_button, null)
        params = createLayoutParams(dpToPx(48))
        bindDrag(view)
        view.setOnClickListener { showExpanded() }
        currentView = view
        windowManager.addView(view, params)
    }

    private fun showExpanded() {
        expanded = true
        removeCurrentView()
        val view = layoutInflater.inflate(R.layout.overlay_floating_panel, null)
        params = createLayoutParams(dpToPx(260))
        bindDrag(view)
        bindExpandedActions(view)
        currentView = view
        windowManager.addView(view, params)
        updateExpandedView(view)
    }

    private fun removeCurrentView() {
        currentView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        currentView = null
    }

    private fun bindDrag(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun bindExpandedActions(view: View) {
        view.findViewById<Button>(R.id.readCommentButton).setOnClickListener { onReadCommentClicked() }
        view.findViewById<Button>(R.id.openProfileButton).setOnClickListener { onOpenProfileClicked() }
        view.findViewById<Button>(R.id.suitableButton).setOnClickListener { onSuitableClicked() }
        view.findViewById<Button>(R.id.unsuitableButton).setOnClickListener { onUnsuitableClicked() }
        view.findViewById<Button>(R.id.generateMessageButton).setOnClickListener { onGenerateMessageClicked() }
        view.findViewById<Button>(R.id.copyMessageButton).setOnClickListener { onCopyMessageClicked() }
        view.findViewById<Button>(R.id.fillMessageButton).setOnClickListener { onFillMessageClicked() }
        view.findViewById<Button>(R.id.collapseButton).setOnClickListener { showCollapsed() }
    }

    private fun onReadCommentClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val comment = commentReader.readCurrentComment(service.getRoot())
        if (comment.isNullOrBlank()) {
            currentLead = currentLead.copy(status = LeadStatus.ERROR, updatedAt = System.currentTimeMillis())
            showToast("未读取到评论，可手动复制评论后再点击读取。")
            return
        }

        currentLead = Lead(comment = comment, status = LeadStatus.COMMENT_READ)
        serviceScope.launch { recentLeadStore.saveComment(comment) }
        updateExpandedView()
    }

    private fun onOpenProfileClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val ok = actionExecutor.openProfile(service.getRoot())
        if (ok) {
            currentLead = currentLead.copy(
                profileOpened = true,
                status = LeadStatus.PROFILE_OPENED,
                updatedAt = System.currentTimeMillis()
            )
            showToast("已尝试打开主页")
        } else {
            showToast("未能自动打开主页，请手动点击头像或昵称。")
        }
    }

    private fun onSuitableClicked() {
        currentLead = currentLead.copy(
            suitable = true,
            status = LeadStatus.MARKED_SUITABLE,
            updatedAt = System.currentTimeMillis()
        )
        showToast("已标记合适")
    }

    private fun onUnsuitableClicked() {
        currentLead = currentLead.copy(
            suitable = false,
            status = LeadStatus.MARKED_UNSUITABLE,
            updatedAt = System.currentTimeMillis()
        )
        showToast("已标记不合适")
    }

    private fun onGenerateMessageClicked() {
        serviceScope.launch {
            val comment = currentLead.comment
            if (comment.isNullOrBlank()) {
                showToast("请先读取评论")
                return@launch
            }
            if (currentLead.suitable != true) {
                showToast("请先标记为合适。")
                return@launch
            }

            val settings = settingsRepository.settingsFlow.first()
            val message = MessageBuilder.build(comment, settings.fixedText)
            currentLead = currentLead.copy(
                message = message,
                status = LeadStatus.MESSAGE_GENERATED,
                updatedAt = System.currentTimeMillis()
            )
            recentLeadStore.saveMessage(message)
            updateExpandedView()
        }
    }

    private fun onCopyMessageClicked() {
        val message = currentLead.message
        if (message.isNullOrBlank()) {
            showToast("请先生成私信")
            return
        }

        clipboardHelper.copyText(message)
        currentLead = currentLead.copy(
            status = LeadStatus.MESSAGE_COPIED,
            updatedAt = System.currentTimeMillis()
        )
        showToast("已复制私信")
    }

    private fun onFillMessageClicked() {
        val message = currentLead.message
        if (message.isNullOrBlank()) {
            showToast("请先生成私信")
            return
        }

        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val ok = messageFiller.fillMessage(service.getRoot(), message)
        if (ok) {
            currentLead = currentLead.copy(
                status = LeadStatus.MESSAGE_FILLED,
                updatedAt = System.currentTimeMillis()
            )
            showToast("已填入私信框，请人工确认发送。")
        } else {
            clipboardHelper.copyText(message)
            showToast("自动填入失败，已复制私信，请手动粘贴。")
        }
    }

    private fun updateExpandedView(view: View? = currentView) {
        if (!expanded || view == null) return
        view.findViewById<TextView>(R.id.commentTextView).text =
            "当前评论：${currentLead.comment ?: "暂无"}"
        view.findViewById<TextView>(R.id.messageTextView).text =
            "私信：${currentLead.message ?: "暂无"}"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
```

- [ ] **Step 4: Run build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS and produce `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Run unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```powershell
git add app/src/main/res/layout/overlay_collapsed_button.xml app/src/main/res/layout/overlay_floating_panel.xml app/src/main/java/com/ccy/xhscommenthelper/overlay/FloatingOverlayService.kt
git commit -m "feat: add floating screening overlay"
```

---

### Task 7: README And Final Verification

**Files:**
- Create: `README.md`

- [ ] **Step 1: Create README**

Create `README.md`:

```markdown
# XhsCommentHelper

## 功能

- 悬浮窗筛人助手
- 读取当前评论
- 打开用户主页
- 人工标记合适/不合适
- 生成私信草稿
- 复制或填入私信框
- 人工确认发送

## 权限

- 辅助功能权限：用于读取当前屏幕控件文本，并尝试填入私信草稿。
- 悬浮窗权限：用于在小红书 App 上方显示筛人助手工具条。

## 使用步骤

1. 打开 App，设置固定话术。
2. 开启辅助功能权限。
3. 开启悬浮窗权限。
4. 点击开启悬浮窗。
5. 打开小红书 App 评论区。
6. 点击读取评论。
7. 点击打开主页。
8. 人工判断是否合适。
9. 点击生成私信。
10. 点击复制或填入私信框。
11. 人工确认发送。

## 注意

本工具不自动发送私信，不批量处理用户，不自动判断性别/IP，仅作为用户手动操作的提效辅助工具。

如果自动读取评论失败，可以先在小红书中手动复制评论文本，再点击悬浮窗的读取评论按钮。工具会从剪贴板兜底读取。

如果自动填入私信失败，工具会保留私信内容到剪贴板，请手动粘贴并确认后再发送。
```

- [ ] **Step 2: Run final unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Run final debug APK build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS and produce `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Inspect git status**

Run:

```powershell
git status --short
```

Expected: only `README.md` is untracked/modified before commit, plus any pre-existing untracked `.idea/` directory.

- [ ] **Step 5: Commit**

Run:

```powershell
git add README.md
git commit -m "docs: add v0.1 usage guide"
```

---

## Manual Device Verification Checklist

After installing `app/build/outputs/apk/debug/app-debug.apk` on a real Android device:

- [ ] App opens and shows `小红书评论区筛人助手`.
- [ ] Fixed text can be saved and survives app restart.
- [ ] Overlay permission status updates after returning from settings.
- [ ] Accessibility permission status updates after returning from settings.
- [ ] `开启悬浮窗` shows the collapsed `筛` button.
- [ ] Collapsed button expands into the full panel.
- [ ] Floating panel can be dragged.
- [ ] `收起` returns to collapsed state.
- [ ] In Xiaohongshu comment area, `读取评论` displays a visible comment when accessibility nodes expose it.
- [ ] If node reading fails, manually copied comment text is read from clipboard.
- [ ] `打开主页` attempts node-based profile opening or shows the manual tap prompt.
- [ ] `不合适` prevents message generation.
- [ ] `合适` allows message generation.
- [ ] Generated message matches `刚刚看到你评论：“{comment}”\n{fixedText}`.
- [ ] `复制私信` puts the draft on the clipboard.
- [ ] `填入私信框` fills the draft when a supported input node is available.
- [ ] When fill fails, the draft remains copied and the manual paste prompt appears.
- [ ] No workflow clicks a send button automatically.

## Self-Review Notes

- The plan covers every approved spec section: dependency wiring, DataStore settings, main page, accessibility service, overlay, comment reading, conservative profile opening, suitability gating, message generation, copy/fill behavior, README, and verification.
- V0.1 deliberately excludes coordinate fallback tapping, automatic send, batch processing, Excel, backend, Xiaohongshu APIs, packet capture, reverse engineering, and risk-control bypass.
- Pure unit coverage is focused on deterministic message generation and text filtering. Runtime overlay/accessibility behavior requires real-device verification because it depends on Android system permissions and Xiaohongshu's accessibility node exposure.
