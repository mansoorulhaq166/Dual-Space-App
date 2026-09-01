# Add project-specific ProGuard rules here.

# ============================================================================
# Engine keep-rules (plan A1) — required before enabling minify (isMinifyEnabled=true).
#
# The BlackBox engine resolves classes/methods by NAME via reflection and JNI, so R8 must
# NOT rename or strip them. The engine AAR's consumer rules already keep
# com.dualcore.**, com.dualcore.jnihook.**, mirror.**, android.**, com.android.**,
# and Pine ships its own consumer rules. The packages below are the GAPS not covered there.
# ============================================================================

# black-reflection: generated BR* accessors (black.**) + its runtime + annotations.
# These are looked up reflectively from @BClassName/@BMethod, so names must be preserved.
-keep class black.** { *; }
-keep class com.dualcore.reflection.** { *; }

# Pine (ART hooking) + LSPosed HiddenApiBypass — reflection/JNI sensitive (belt-and-suspenders;
# Pine also ships consumer rules).
-keep class top.canyie.pine.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }

# Preserve metadata the reflection layer and engine depend on.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Never strip/rename native method bindings (JNI resolves by exact name).
-keepclasseswithmembernames class * {
    native <methods>;
}

# Our engine adapter + Application/trampoline are referenced by the engine/manifest by name.
-keep class com.example.dual.space.DualSpaceApplication { *; }
-keep class com.example.dual.space.ShortcutLauncherActivity { *; }
-keep class com.example.dual.space.engine.** { *; }

# R8 (debugAndroidTest / minify): JDK annotation-model types are referenced transitively
# but are not on the Android runtime. Suppress missing-class errors.
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.element.**
-dontwarn javax.lang.model.**
