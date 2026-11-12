# Navigation 3 保存返回栈时记录路由类名，恢复时用 Class.forName + kotlinx.serialization
# 反射查找 Companion / INSTANCE 上的 serializer()，路由类不能被重命名或裁剪。
-keep class com.wmdhs.shorea.ui.navigation.** { *; }
