# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留行号信息，配合每次发版归档的 mapping.txt 还原用户反馈的崩溃堆栈
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 工具栏溢出菜单通过反射读取 PopupMenu.mPopup 调用 setVerticalOffset 调整弹出位置，
# 保留 appcompat 菜单内部类避免 R8 重命名后反射失效（MainActivity.applyPopupMenuOffsetCompat）
-keep class androidx.appcompat.view.menu.** { *; }

# 安全页 Shizuku UserService：服务类由 Shizuku 服务端按类名实例化，AIDL 接口跨进程
-keep class com.violet.box.ui.safety.ShellService { *; }
-keep class com.violet.box.ui.safety.IShellService { *; }
-keep class com.violet.box.ui.safety.IShellService$* { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
