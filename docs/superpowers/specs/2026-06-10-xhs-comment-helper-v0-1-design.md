# XhsCommentHelper V0.1 Design

## Context

The project is an Android Kotlin application using XML views under package `com.ccy.xhscommenthelper`. The current codebase is the starter app: one `MainActivity`, one `activity_main.xml`, and standard Android/AppCompat dependencies.

V0.1 implements a phone-side productivity helper for manually screening Xiaohongshu comment users. It helps the user read the current comment, open a profile, mark suitability, generate a private-message draft, copy or fill the draft, and then leaves final sending to the user.

## Product Scope

### In Scope

- Native Android app in Kotlin.
- XML-based main screen and floating overlay UI.
- Overlay permission and accessibility permission guidance.
- Editable fixed message text persisted locally.
- Accessibility service for reading visible screen nodes and interacting with input fields.
- Floating tool window with collapsed and expanded states.
- Current comment reading from accessibility nodes, with clipboard fallback.
- Conservative profile-opening attempt through clickable accessibility nodes.
- Manual suitability marking.
- Private-message draft generation.
- Clipboard copy.
- Best-effort draft fill into a message input field.
- Recent comment and recent generated message displayed in the app and overlay.
- README with function, permission, usage, and safety notes.

### Out of Scope

- Automatic sending.
- Batch processing.
- Automatic gender, IP, or lead quality judgment.
- Excel import/export.
- Backend, account system, CRM, or database admin UI.
- Xiaohongshu APIs, packet capture, reverse engineering, or risk-control bypass.
- Coordinate-based fallback tapping in V0.1.

## Architecture

The implementation will keep responsibilities small and explicit:

- `MainActivity`
  - Shows permission status.
  - Lets the user edit and save fixed text.
  - Starts and stops the floating overlay service.
  - Opens accessibility and overlay permission settings.
  - Displays the latest comment and latest generated message.

- `data`
  - `UserSettings`: fixed text and target package name.
  - `SettingsRepository`: DataStore Preferences wrapper for settings.
  - `RecentLeadStore`: stores the latest comment and latest generated message for display on the main page.

- `domain`
  - `Lead`: current lead state.
  - `LeadStatus`: current workflow status.
  - `MessageBuilder`: deterministic draft generation.

- `accessibility`
  - `XhsAccessibilityService`: exposes active root, global back, node action helpers.
  - `AccessibilityBridge`: simple V0.1 bridge from overlay service to accessibility service instance.
  - `NodeFinder`: accessibility node traversal and clickable-parent lookup.
  - `CommentReader`: filters visible text and falls back to clipboard.
  - `XhsActionExecutor`: tries to open a profile through a clickable nickname/avatar-related node.
  - `MessageFiller`: copies the draft and tries `ACTION_SET_TEXT`, then `ACTION_PASTE`.

- `overlay`
  - `FloatingOverlayService`: owns the overlay window, current lead state, button handlers, and UI updates.
  - XML layout or direct view construction for expanded/collapsed overlay.

- `util`
  - `ClipboardHelper`: copy and read clipboard text.
  - `PermissionHelper`: overlay and accessibility enabled checks.
  - `Logger`: small wrapper or Android log tags if useful.

## Main Screen Design

The main app is a practical configuration and permission page, not a lead-management surface.

Displayed content:

- Title: `小红书评论区筛人助手`
- Short safety description stating final sending requires manual confirmation.
- Permission status:
  - `悬浮窗权限：已开启 / 未开启`
  - `辅助功能权限：已开启 / 未开启`
- Fixed text editor.
- Buttons:
  - `保存话术`
  - `开启悬浮窗`
  - `关闭悬浮窗`
  - `开启辅助功能权限`
  - `开启悬浮窗权限`
- Recent comment.
- Recent generated message.

The screen refreshes permission status when resumed.

## Floating Overlay Design

The overlay defaults to the right middle of the screen and can be dragged. It supports:

- Collapsed state: a small `筛` button.
- Expanded state:
  - Title: `筛人助手`
  - Current comment text, default `暂无`
  - Buttons:
    - `读取评论`
    - `打开主页`
    - `合适`
    - `不合适`
    - `生成私信`
    - `复制私信`
    - `填入私信框`
    - `收起`

The overlay must remain compact enough to avoid covering most of the Xiaohongshu screen.

## Workflow

Primary flow:

1. User opens the app.
2. User grants accessibility and overlay permissions.
3. User edits and saves fixed text.
4. User starts the floating overlay.
5. User opens Xiaohongshu comment area.
6. User taps `读取评论`.
7. The app reads a likely comment from accessibility text; if no candidate exists, it reads clipboard text.
8. User taps `打开主页`.
9. The app attempts node-based profile opening.
10. If node opening fails, the app prompts the user to manually tap avatar or nickname.
11. User manually checks the profile.
12. User taps `合适` or `不合适`.
13. If suitable, user taps `生成私信`.
14. User taps `复制私信` or `填入私信框`.
15. User manually confirms and sends in Xiaohongshu.

Unsuitable flow:

1. After reading a comment, user taps `不合适`.
2. The current lead status becomes `MARKED_UNSUITABLE`.
3. The app does not generate a message for that lead.

## Message Generation

`MessageBuilder` is deterministic:

```text
刚刚看到你评论：“{comment}”
{fixedText}
```

Default fixed text:

```text
方便的话可以了解一下，我们这边可以给你发详细介绍～
```

For comment `这个多少钱呀`, the result is:

```text
刚刚看到你评论：“这个多少钱呀”
方便的话可以了解一下，我们这边可以给你发详细介绍～
```

## Accessibility Behavior

`XhsAccessibilityService` will:

- Declare `BIND_ACCESSIBILITY_SERVICE`.
- Use a service config with `canRetrieveWindowContent=true`.
- Restrict to `com.xingin.xhs` by default.
- Store its instance in `AccessibilityBridge.service` while active.
- Provide access to `rootInActiveWindow`.
- Provide global back support for future use.

`CommentReader` will:

- Flatten visible accessibility nodes.
- Extract non-blank text.
- Remove obvious navigation/action text such as `关注`, `点赞`, `回复`, `分享`, `收藏`, `说点什么`, `搜索`, `首页`, `购物`, `消息`, `我`.
- Prefer candidates with length `2..120`.
- Fall back to clipboard text if no candidate is found.

`XhsActionExecutor.openProfile` will:

- Search plausible nickname/comment-author nodes.
- Find a clickable parent.
- Perform `ACTION_CLICK`.
- Return `false` if no reliable node is found.
- Not dispatch coordinate gestures in V0.1.

`MessageFiller` will:

- Copy the draft to clipboard first.
- Look for an `EditText` or input node with text/hint such as `发消息`, `发送消息`, `说点什么`, `请输入`.
- Try `ACTION_SET_TEXT`.
- If that fails, focus and try `ACTION_PASTE`.
- If that also fails, keep the draft copied and show a manual paste prompt.
- Never click any send button.

## Error Handling

User-facing messages:

- Accessibility service missing: `请先开启辅助功能权限，否则无法读取当前评论。`
- Overlay permission missing: `请先开启悬浮窗权限，否则无法显示工具面板。`
- Comment missing: `未读取到评论，可手动复制评论后再点击读取。`
- Profile opening failed: `未能自动打开主页，请手动点击头像或昵称。`
- Generate before suitable: `请先标记为合适。`
- Fill failed: `自动填入失败，已复制私信，请手动粘贴。`
- Fill succeeded: `已填入私信框，请人工确认发送。`

## Testing And Verification

Automated verification:

- Unit test `MessageBuilder` output for the documented example.
- Unit test pure filtering logic used by `CommentReader` where possible.
- Run Gradle unit tests.
- Run Android build to verify manifest, resources, services, and layouts compile.

Manual verification required on a real Android device:

- Grant accessibility permission.
- Grant overlay permission.
- Start and stop overlay.
- Drag and collapse overlay.
- Open Xiaohongshu comment area.
- Read a comment from visible nodes.
- Read a manually copied comment through clipboard fallback.
- Attempt profile opening.
- Mark suitable and unsuitable.
- Generate and copy a draft.
- Fill a private-message input field.
- Confirm no automatic send action occurs.

## Delivery Artifacts

- Android Studio-openable project.
- Buildable APK through Gradle.
- `README.md` with features, permissions, usage steps, and safety notes.
- Implemented V0.1 app matching the approved scope.
